import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what a particular build of a J2ME game expects, so every phone version of the game
 * runs without per-version settings: its screen size and the key codes of its soft keys.
 */
final class GameProfile {
    /** Common J2ME screen sizes, used to recognise full-screen images inside a jar. */
    private static final int[][] SCREENS = {
        {128, 128}, {128, 160}, {130, 130}, {132, 176}, {176, 208}, {176, 220}, {208, 208},
        {240, 320}, {320, 240}, {240, 400}, {352, 416}, {360, 640}, {640, 360},
    };

    /** Manifest/JAD attributes various vendors use to declare the screen size. */
    private static final String[] SIZE_KEYS = {
        "Nokia-MIDlet-Target-Display-Size", "MIDlet-Display-Size", "Display-Size",
        "MIDlet-Screen-Size", "MIDlet-ScreenSize", "Screen-Size", "MIDlet-Canvas-Size",
    };

    int width;
    int height;
    String sizeSource;
    int softLeft = -6;
    int softRight = -7;
    String softSource = "default (Nokia/Sony Ericsson/Samsung)";

    static GameProfile detect(File jar, Attributes manifest) {
        GameProfile p = new GameProfile();
        p.detectSize(jar, manifest);
        p.detectSoftKeys(jar);
        return p;
    }

    // ---- screen size ---------------------------------------------------------------

    private void detectSize(File jar, Attributes manifest) {
        Integer pw = Integer.getInteger("midp.width");
        Integer ph = Integer.getInteger("midp.height");
        if (pw != null && ph != null) {
            set(pw, ph, "midp.width/midp.height");
            return;
        }
        int[] s = parseSize(jar.getName());
        if (s != null) {
            set(s[0], s[1], "file name");
            return;
        }
        if (manifest != null) {
            for (String k : SIZE_KEYS) {
                s = parseSize(manifest.getValue(k));
                if (s != null) {
                    set(s[0], s[1], "manifest " + k);
                    return;
                }
            }
        }
        Map<String, String> jad = readJad(jar);
        for (String k : SIZE_KEYS) {
            s = parseSize(jad.get(k));
            if (s != null) {
                set(s[0], s[1], "JAD " + k);
                return;
            }
        }
        s = sizeFromImages(jar);
        if (s != null) {
            set(s[0], s[1], "full-screen images in the jar");
            return;
        }
        // Last resort: the size the code was written for. Resized builds often keep the old
        // value here (the 240x320 Larry build says 176x220), so it ranks below everything else.
        String orig = manifest == null ? null : manifest.getValue("Nokia-MIDlet-Original-Display-Size");
        s = parseSize(orig != null ? orig : jad.get("Nokia-MIDlet-Original-Display-Size"));
        if (s != null) {
            set(s[0], s[1], "manifest Nokia-MIDlet-Original-Display-Size");
            return;
        }
        // Small Nokia Series 40 builds (1st/2nd edition phones, 128x128) use the Nokia UI or Sound
        // API and stay well under 128 KB; the Larry builds are 62 KB (MIDP 1.0) and 126 KB (MIDP 2.0).
        if (jar.length() <= 128 * 1024 && classesMention(jar, "com/nokia/mid/")) {
            set(128, 128, "Nokia Series 40 build (Nokia API, jar under 128 KB)");
            return;
        }
        // Art for a 128-pixel-wide screen: no image wider than 160 px (larger screens always have
        // wider backgrounds, logos or title screens). Catches the full Series 40 Larry build (MIDP
        // 2.0, no Nokia API, widest 149 px) and the Samsung E250 build (widest 160 px). Samsung's
        // small phones were 128x160, Nokia's 128x128.
        int widest = widestImage(jar);
        if (widest > 0 && widest <= 160) {
            if (classesMention(jar, "com/samsung/")) {
                set(128, 160, "small-screen art (widest image " + widest + " px), Samsung build");
            } else {
                set(128, 128, "small-screen art (widest image " + widest + " px)");
            }
            return;
        }
        set(240, 320, "default, could not detect; set -Dmidp.width/-Dmidp.height");
    }

    /** Width of the widest PNG in the jar, or 0 if it has none. */
    private static int widestImage(File jar) {
        int widest = 0;
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || je.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jf.getInputStream(je)) {
                    byte[] h = in.readNBytes(24);
                    if (h.length == 24 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
                        widest = Math.max(widest, be32(h, 16));
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return widest;
    }

    /** True when any class in the jar references the given internal class name. */
    private static boolean classesMention(File jar, String internalName) {
        byte[] needle = internalName.getBytes(StandardCharsets.UTF_8);
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jf.getInputStream(je)) {
                    if (indexOf(in.readAllBytes(), needle) >= 0) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private void set(int w, int h, String source) {
        width = w;
        height = h;
        sizeSource = source;
    }

    /** Finds "176x208" style sizes (also "176*208", "176,208"). */
    static int[] parseSize(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?<!\\d)(\\d{2,3})\\s*[xX*,]\\s*(\\d{2,3})(?!\\d)").matcher(s);
        while (m.find()) {
            int w = Integer.parseInt(m.group(1));
            int h = Integer.parseInt(m.group(2));
            if (w >= 96 && h >= 64 && w <= 800 && h <= 800) {
                return new int[] {w, h};
            }
        }
        return null;
    }

    private static Map<String, String> readJad(File jar) {
        Map<String, String> out = new HashMap<>();
        String name = jar.getName();
        int dot = name.lastIndexOf('.');
        File jad = new File(jar.getParentFile(), (dot > 0 ? name.substring(0, dot) : name) + ".jad");
        if (!jad.isFile()) {
            return out;
        }
        try (InputStream in = new FileInputStream(jad)) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                int c = line.indexOf(':');
                if (c > 0) {
                    out.put(line.substring(0, c).trim(), line.substring(c + 1).trim());
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    /** The most common known screen size among the jar's PNG images (backgrounds, splash screens). */
    private static int[] sizeFromImages(File jar) {
        int[] votes = new int[SCREENS.length];
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || je.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jf.getInputStream(je)) {
                    byte[] h = in.readNBytes(24);
                    if (h.length < 24 || (h[0] & 0xFF) != 0x89 || h[1] != 'P' || h[2] != 'N' || h[3] != 'G') {
                        continue;
                    }
                    int w = be32(h, 16);
                    int ht = be32(h, 20);
                    for (int i = 0; i < SCREENS.length; i++) {
                        if (SCREENS[i][0] == w && SCREENS[i][1] == ht) {
                            votes[i]++;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        int best = -1;
        for (int i = 0; i < votes.length; i++) {
            if (votes[i] > 0 && (best < 0 || votes[i] > votes[best])) {
                best = i;
            }
        }
        return best < 0 ? null : SCREENS[best];
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    // ---- soft keys -----------------------------------------------------------------

    /**
     * Phone makers disagree on soft key codes: Nokia, Sony Ericsson and Samsung use -6/-7,
     * Motorola -21/-22, Siemens -1/-4 (with arrows at -59..-62). Builds for one maker compare
     * key codes against their own constants, so count which "bipush" constants the classes use.
     */
    private void detectSoftKeys(File jar) {
        String prop = System.getProperty("midp.softkeys");
        if (prop != null) {
            String[] p = prop.split(",");
            softLeft = Integer.parseInt(p[0].trim());
            softRight = Integer.parseInt(p[1].trim());
            softSource = "midp.softkeys";
            return;
        }
        int nokia = 0;
        int moto = 0;
        int siemensArrows = 0;
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class")) {
                    continue;
                }
                byte[] b;
                try (InputStream in = jf.getInputStream(je)) {
                    b = in.readAllBytes();
                }
                for (int i = 0; i + 1 < b.length; i++) {
                    if (b[i] != 0x10) {         // bipush
                        continue;
                    }
                    int v = b[i + 1];
                    if (v == -6 || v == -7) {
                        nokia++;
                    } else if (v == -21 || v == -22) {
                        moto++;
                    } else if (v <= -59 && v >= -62) {
                        siemensArrows++;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (moto >= 2 && moto > nokia * 2) {
            softLeft = -21;
            softRight = -22;
            softSource = "detected Motorola key codes";
        } else if (siemensArrows >= 4 && siemensArrows > nokia * 2) {
            softLeft = -1;
            softRight = -4;
            softSource = "detected Siemens key codes";
        }
    }
}
