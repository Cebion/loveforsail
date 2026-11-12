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
 *   midp.toneprogram           General MIDI instrument for Nokia ringtones (default 0, grand piano)
 *   midp.tonecycle=true        debug: F5/F6 step through candidate ringtone instruments, number shown
 *   midp.tonearrange=false     play Nokia ringtones as the bare melody (no added bass and drums)
 *   midp.mididebug=true        debug: print the synthesizer's program per channel every 2 s
 *   midp.dump.dir / midp.dump.every   debug: write every Nth frame as PNG
 *   midp.script                debug: file of "ms down|up keycode" lines for scripted input
 *   midp.keyfifo               debug: named pipe of "down|up keycode" lines, read live
 *   midp.watch=Cls.f:I,...     debug: print the game's static fields once a second
 *   midp.nopatch=true          show the original phone-keypad prompts
 */
public final class LarryPortmaster {
    private static final Object frameLock = new Object();
    private static int[] pendingFrame;
    private static boolean frameReady;
    private static volatile boolean running = true;

    private static final boolean TONE_CYCLE = Boolean.getBoolean("midp.tonecycle");
    private static int overlayNumber;
    private static long overlayUntil;

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

        // Load the synthesizer and soundfont during the logos, not when the first song starts.
        Thread midiPreload = new Thread(javax.microedition.media.ShimMidi::preload, "midi-preload");
        midiPreload.setDaemon(true);
        midiPreload.setPriority(Thread.MIN_PRIORITY);
        midiPreload.start();

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

        startKeyFifo(System.getProperty("midp.keyfifo"));
        startWatch(System.getProperty("midp.watch"), loader);
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
                } else if (TONE_CYCLE && (sc == 62 || sc == 63)) {   // F5/F6: ringtone instrument
                    if (type == SDL2Bridge.EVENT_KEYDOWN) {
                        overlayNumber = com.nokia.mid.sound.ShimTone.cycle(sc == 63 ? 1 : -1);
                        overlayUntil = System.currentTimeMillis() + 2000;
                        synchronized (frameLock) {
                            frameReady = true;
                        }
                    }
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
                if (System.currentTimeMillis() < overlayUntil) {
                    int[] shown = frame.clone();
                    drawNumber(shown, gw, gh, overlayNumber);
                    SDL2Bridge.updateFrame(shown, gw, gh);
                    synchronized (frameLock) {
                        frameReady = true;   // keep redrawing until the label times out
                    }
                } else {
                    SDL2Bridge.updateFrame(frame, gw, gh);
                }
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

    /** 3x5 digit glyphs, one row per 3 bits. */
    private static final int[] DIGITS = {
        075557, 022222, 071747, 071717, 055711, 074717, 074757, 071111, 075757, 075717,
    };

    /** Debug overlay: a number in the top-left corner, on a black box, drawn at 2x. */
    private static void drawNumber(int[] px, int w, int h, int n) {
        String txt = Integer.toString(n);
        int scale = 2;
        int bw = (txt.length() * 4 + 1) * scale;
        int bh = 7 * scale;
        for (int y = 0; y < bh && y < h; y++) {
            for (int x = 0; x < bw && x < w; x++) {
                px[y * w + x] = 0xFF000000;
            }
        }
        for (int i = 0; i < txt.length(); i++) {
            int g = DIGITS[txt.charAt(i) - '0'];
            for (int row = 0; row < 5; row++) {
                int bits = (g >> (3 * (4 - row))) & 7;
                for (int col = 0; col < 3; col++) {
                    if ((bits & (4 >> col)) == 0) {
                        continue;
                    }
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            int x = (1 + i * 4 + col) * scale + dx;
                            int y = (1 + row) * scale + dy;
                            if (x < w && y < h) {
                                px[y * w + x] = 0xFFFFFF00;
                            }
                        }
                    }
                }
            }
        }
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

    /** Debug: print the game's static fields ("Class.field,...") once a second. */
    private static void startWatch(String spec, ClassLoader loader) {
        if (spec == null) {
            return;
        }
        Thread t = new Thread(() -> {
            while (running) {
                StringBuilder sb = new StringBuilder("[watch]");
                for (String item : spec.split(",")) {
                    int dot = item.lastIndexOf('.');
                    String name = item.substring(dot + 1);
                    String type = null;   // optional ":Z"/":I"/":J" suffix, obfuscated names repeat
                    if (name.contains(":")) {
                        type = name.substring(name.indexOf(':') + 1);
                        name = name.substring(0, name.indexOf(':'));
                    }
                    try {
                        java.lang.reflect.Field f = null;
                        for (java.lang.reflect.Field c : Class.forName(item.substring(0, dot), false, loader)
                                .getDeclaredFields()) {
                            String d = c.getType() == boolean.class ? "Z" : c.getType() == int.class ? "I"
                                    : c.getType() == long.class ? "J" : "L";
                            if (c.getName().equals(name) && (type == null || type.equals(d))) {
                                f = c;
                                break;
                            }
                        }
                        f.setAccessible(true);
                        sb.append(' ').append(item).append('=').append(f.get(null));
                    } catch (Throwable e) {
                        sb.append(' ').append(item).append("=?");
                    }
                }
                System.out.println(sb);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "watch");
        t.setDaemon(true);
        t.start();
    }

    /** Debug: read "down|up keycode" lines from a named pipe while the game runs. */
    private static void startKeyFifo(String path) {
        if (path == null) {
            return;
        }
        Thread t = new Thread(() -> {
            while (running) {
                try (BufferedReader r = new BufferedReader(new FileReader(path))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] p = line.trim().split("\\s+");
                        if (p.length == 2) {
                            Canvas.postKey(p[0].equals("down"), Integer.parseInt(p[1]));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[host] keyfifo error: " + e);
                    return;
                }
            }
        }, "keyfifo");
        t.setDaemon(true);
        t.start();
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
