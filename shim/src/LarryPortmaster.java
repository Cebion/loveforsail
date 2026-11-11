import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 * PortMaster launcher for J2ME MIDlets: runs the original MIDlet on the javax.microedition shim
 * and drives an SDL2 window through SDL2Bridge.
 *
 * Usage: java -cp lovesail-portmaster.jar LarryPortmaster game.jar
 *
 * Any phone version of the game works: the screen size is taken from the file name, the
 * manifest/JAD or the jar's full-screen images, and the soft key codes from the game's code
 * (see GameProfile).
 *
 * The game jar is loaded through GameLoader (not the classpath) so its resources can be patched.
 *
 * System properties:
 *   midp.width / midp.height   canvas size (default: detected)
 *   midp.softkeys=L,R          soft key codes (default: detected, usually -6,-7)
 *   midp.window=WxH or N       windowed mode for desktop testing, N = scale (default: fullscreen)
 *   midp.rms.dir               save directory (default ./savedata)
 *   midp.soundfont             optional .sf2 for MIDI
 *   midp.dump.dir / midp.dump.every   debug: write every Nth frame as PNG
 *   midp.script                debug: file of "ms down|up keycode" lines for scripted input
 *   midp.nopatch=true          show the original phone-keypad prompts
 */
public final class LarryPortmaster {
    private static final Object frameLock = new Object();
    private static int[] pendingFrame;
    private static boolean frameReady;
    private static volatile boolean running = true;

    private static int softLeft = Canvas.KEYCODE_SOFT_LEFT;
    private static int softRight = Canvas.KEYCODE_SOFT_RIGHT;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || !new File(args[0]).isFile()) {
            System.err.println("[host] usage: LarryPortmaster <game.jar>");
            System.exit(2);
        }
        File jar = new File(args[0]);
        GameLoader loader = new GameLoader(jar.toURI().toURL(), LarryPortmaster.class.getClassLoader());
        MIDlet.LarryHost.setGameLoader(loader);
        Thread.currentThread().setContextClassLoader(loader);

        // Each phone build of a game has its own screen size and soft key codes. This has to be
        // set before Display is first touched, because Display reads midp.width/midp.height.
        GameProfile profile = GameProfile.detect(jar, MIDlet.getAppProperties());
        System.setProperty("midp.width", Integer.toString(profile.width));
        System.setProperty("midp.height", Integer.toString(profile.height));
        softLeft = profile.softLeft;
        softRight = profile.softRight;
        System.out.println("[host] " + jar.getName() + ": screen " + profile.width + "x" + profile.height
                + " (" + profile.sizeSource + "), soft keys " + softLeft + "/" + softRight
                + " (" + profile.softSource + ")");

        final int gw = Display.WIDTH;
        final int gh = Display.HEIGHT;
        int winW = 0;
        int winH = 0;
        String win = System.getProperty("midp.window");
        if (win != null && win.contains("x")) {
            winW = Integer.parseInt(win.substring(0, win.indexOf('x')));
            winH = Integer.parseInt(win.substring(win.indexOf('x') + 1));
        } else if (win != null && !win.isEmpty()) {
            int scale = Math.max(1, Integer.parseInt(win.trim()));   // "2": window at 2x the game size
            winW = gw * scale;
            winH = gh * scale;
        }
        if (!SDL2Bridge.init(gw, gh, winW, winH)) {
            System.err.println("[host] SDL2 init failed");
            System.exit(1);
        }

        pendingFrame = new int[gw * gh];
        final String dumpDir = System.getProperty("midp.dump.dir");
        final int dumpEvery = Integer.getInteger("midp.dump.every", 25);
        final int[] frameNo = {0};
        Canvas.frameSink = (px, w, h) -> {
            if (w != gw || h != gh) {
                return;
            }
            synchronized (frameLock) {
                System.arraycopy(px, 0, pendingFrame, 0, w * h);
                frameReady = true;
            }
            if (dumpDir != null && (frameNo[0]++ % dumpEvery) == 0) {
                dumpFrame(px, w, h, new File(dumpDir, String.format("f%05d.png", frameNo[0] - 1)));
            }
        };
        MIDlet.LarryHost.setExitHandler(() -> running = false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            reportMissingApi(e);
        });

        String midletClass = midletClassName();
        System.out.println("[host] starting MIDlet " + midletClass + " at " + gw + "x" + gh);
        MIDlet midlet;
        try {
            midlet = (MIDlet) Class.forName(midletClass, true, loader).getDeclaredConstructor().newInstance();
            MIDlet.LarryHost.start(midlet);
        } catch (Throwable e) {
            e.printStackTrace();
            reportMissingApi(e);
            SDL2Bridge.destroy();
            System.exit(3);
            return;
        }

        ArrayList<long[]> script = loadScript(System.getProperty("midp.script"));
        long t0 = System.currentTimeMillis();
        int scriptPos = 0;

        int[] frame = new int[gw * gh];
        while (running) {
            int ev;
            while ((ev = SDL2Bridge.pollEvent()) != 0) {
                int type = ev >>> 16;
                int sc = ev & 0xFFFF;
                if (type == SDL2Bridge.EVENT_QUIT) {
                    running = false;
                } else if (type == SDL2Bridge.EVENT_KEYDOWN || type == SDL2Bridge.EVENT_KEYUP) {
                    int key = mapScancode(sc);
                    if (key != 0) {
                        Canvas.postKey(type == SDL2Bridge.EVENT_KEYDOWN, key);
                    }
                }
            }
            long now = System.currentTimeMillis() - t0;
            while (scriptPos < script.size() && script.get(scriptPos)[0] <= now) {
                long[] s = script.get(scriptPos++);
                if (s[1] < 0) {
                    running = false;
                } else {
                    Canvas.postKey(s[1] == 1, (int) s[2]);
                }
            }
            boolean have;
            synchronized (frameLock) {
                have = frameReady;
                if (have) {
                    System.arraycopy(pendingFrame, 0, frame, 0, frame.length);
                    frameReady = false;
                }
            }
            Canvas.dispatchIfStale(100);
            if (have) {
                SDL2Bridge.updateFrame(frame, gw, gh);
            } else {
                try {
                    Thread.sleep(4);
                } catch (InterruptedException ignored) {
                }
            }
        }

        try {
            MIDlet.LarryHost.destroy(midlet);
        } catch (Throwable ignored) {
        }
        SDL2Bridge.destroy();
        System.out.println("[host] exit");
        System.exit(0);
    }

    /** Point at the shim when a game version uses a phone API it does not provide yet. */
    private static void reportMissingApi(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException
                    || t instanceof NoSuchMethodError || t instanceof NoSuchFieldError
                    || t instanceof AbstractMethodError) {
                System.err.println("[host] this game version uses an API the shim does not provide: "
                        + t.getMessage());
                return;
            }
        }
    }

    private static String midletClassName() {
        java.util.jar.Attributes a = MIDlet.getAppProperties();
        String m1 = a == null ? null : a.getValue("MIDlet-1");
        if (m1 == null) {
            throw new IllegalStateException("no MIDlet-1 entry in any manifest on the classpath");
        }
        String[] parts = m1.split(",");
        return parts[parts.length - 1].trim();
    }

    /**
     * SDL scancodes (gptokeyb keyboard output) to MIDP key codes. The pad works as the phone's
     * keypad: arrows are 2/4/6/8 and Enter is 5, because the game treats the digits and the
     * directions the same everywhere except the minigame, which wants the exact digit.
     */
    static int mapScancode(int sc) {
        switch (sc) {
            case 82: return Canvas.KEY_NUM2;            // Up
            case 81: return Canvas.KEY_NUM8;            // Down
            case 80: return Canvas.KEY_NUM4;            // Left
            case 79: return Canvas.KEY_NUM6;            // Right
            case 40:                                    // Return
            case 88:                                    // Keypad Enter
            case 44: return Canvas.KEY_NUM5;            // Space
            case 42: return Canvas.KEY_NUM0;            // Backspace
            case 41:                                    // Escape (Start): pause menu
            case 59: return softRight;                  // F2
            case 43:                                    // Tab (Select)
            case 58: return softLeft;                   // F1
            case 75:                                    // PageUp
            case 85: return Canvas.KEY_STAR;            // Keypad *
            case 78: return Canvas.KEY_POUND;           // PageDown
            default:
                break;
        }
        if (sc >= 30 && sc <= 38) {
            return Canvas.KEY_NUM1 + (sc - 30);         // 1..9
        }
        if (sc == 39) {
            return Canvas.KEY_NUM0;                     // 0
        }
        if (sc >= 89 && sc <= 97) {
            return Canvas.KEY_NUM1 + (sc - 89);         // Keypad 1..9
        }
        if (sc == 98) {
            return Canvas.KEY_NUM0;                     // Keypad 0
        }
        return 0;
    }

    private static ArrayList<long[]> loadScript(String path) {
        ArrayList<long[]> out = new ArrayList<>();
        if (path == null) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\s+");
                long t = Long.parseLong(p[0]);
                if (p[1].equals("quit")) {
                    out.add(new long[] {t, -1, 0});
                } else {
                    out.add(new long[] {t, p[1].equals("down") ? 1 : 0, Long.parseLong(p[2])});
                }
            }
        } catch (Exception e) {
            System.err.println("[host] script error: " + e);
        }
        return out;
    }

    private static void dumpFrame(int[] px, int w, int h, File f) {
        try {
            f.getParentFile().mkdirs();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            ImageIO.write(img, "png", f);
        } catch (Exception e) {
            System.err.println("[host] dump failed: " + e);
        }
    }
}
