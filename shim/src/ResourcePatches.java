import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * In-memory resource patches that turn phone-keypad prompts into gamepad button names.
 *
 * Text: string tables (*.dat) are replaced by index from /patches/text.txt in the port jar.
 * File format: "@file.dat COUNT" starts a section (only applied when the table really has COUNT
 * strings), then "INDEX=replacement" lines; "\n" in a replacement is a newline.
 *
 * Image: the Spank-a-Monkey minigame draws the digit to press from biminig_button.png; each
 * digit glyph is redrawn as the gamepad button that now sends that digit.
 */
final class ResourcePatches {
    private static Map<String, Section> text;

    private static final class Section {
        int count;
        TreeMap<Integer, String> lines = new TreeMap<>();
    }

    private ResourcePatches() {
    }

    static boolean wants(String name) {
        return name.equals(MINIGAME_SHEET) || textSections().containsKey(name);
    }

    static byte[] apply(String name, byte[] data) throws IOException {
        if (name.equals(MINIGAME_SHEET)) {
            return patchMinigameSheet(data);
        }
        Section s = textSections().get(name);
        return s == null ? data : patchTextTable(name, data, s);
    }

    // ---- text tables ---------------------------------------------------------------

    private static synchronized Map<String, Section> textSections() {
        if (text != null) {
            return text;
        }
        text = new HashMap<>();
        if (Boolean.getBoolean("midp.nopatch")) {
            return text;
        }
        InputStream in = ResourcePatches.class.getResourceAsStream("/patches/text.txt");
        if (in == null) {
            return text;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Section cur = null;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("@")) {
                    String[] p = line.substring(1).trim().split("\\s+");
                    cur = new Section();
                    cur.count = Integer.parseInt(p[1]);
                    text.put(p[0], cur);
                    continue;
                }
                int eq = line.indexOf('=');
                if (cur != null && eq > 0) {
                    cur.lines.put(Integer.parseInt(line.substring(0, eq).trim()),
                            line.substring(eq + 1).replace("\\n", "\n"));
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[patch] cannot read text patches: " + e);
        }
        return text;
    }

    /** Layout (from the game's loader): int, int n*4, n flag ints, int, n UTF strings. */
    private static byte[] patchTextTable(String name, byte[] data, Section s) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int header = in.readInt();
        int sizeField = in.readInt();
        int n = sizeField / 4;
        if (n != s.count) {
            System.out.println("[patch] " + name + " has " + n + " strings, expected " + s.count + "; left unchanged");
            return data;
        }
        int[] flags = new int[n];
        for (int i = 0; i < n; i++) {
            flags[i] = in.readInt();
        }
        int extra = in.readInt();
        String[] strings = new String[n];
        for (int i = 0; i < n; i++) {
            strings[i] = in.readUTF();
        }
        for (Map.Entry<Integer, String> e : s.lines.entrySet()) {
            if (e.getKey() >= 0 && e.getKey() < n) {
                strings[e.getKey()] = e.getValue();
            }
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length + 1024);
        DataOutputStream out = new DataOutputStream(bout);
        out.writeInt(header);
        out.writeInt(sizeField);
        for (int f : flags) {
            out.writeInt(f);
        }
        out.writeInt(extra);
        for (String str : strings) {
            out.writeUTF(str);
        }
        out.flush();
        System.out.println("[patch] " + name + ": " + s.lines.size() + " button prompts");
        return bout.toByteArray();
    }

    // ---- minigame glyphs -----------------------------------------------------------

    private static final String MINIGAME_SHEET = "biminig_button.png";

    /** Digit glyph boxes in biminig_button.png (70x96): {digit, x, y, w, h}. */
    private static final int[][] GLYPHS = {
        {3, 23, 54, 10, 13}, {4, 35, 53, 11, 15}, {5, 48, 55, 10, 12}, {1, 59, 55, 9, 13},
        {9, 23, 69, 10, 12}, {0, 35, 69, 10, 12},
        {6, 1, 83, 11, 12}, {7, 13, 82, 13, 14}, {8, 27, 82, 12, 13}, {2, 57, 83, 10, 12},
    };

    /** Button label per digit, matching the port's keypad layout. */
    private static final String[] LABELS = {"B", "L1", "^", "R1", "<", "A", ">", "L2", "v", "R2"};

    /** 3x5 pixel font for the labels ('^' 'v' '<' '>' are arrows). */
    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {
        FONT.put('A', new String[] {"010", "101", "111", "101", "101"});
        FONT.put('B', new String[] {"110", "101", "110", "101", "110"});
        FONT.put('L', new String[] {"100", "100", "100", "100", "111"});
        FONT.put('R', new String[] {"110", "101", "110", "101", "101"});
        FONT.put('1', new String[] {"010", "110", "010", "010", "111"});
        FONT.put('2', new String[] {"110", "001", "010", "100", "111"});
    }

    private static byte[] patchMinigameSheet(byte[] data) throws IOException {
        if (Boolean.getBoolean("midp.nopatch")) {
            return data;
        }
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null || src.getWidth() != 70 || src.getHeight() != 96) {
            System.out.println("[patch] " + MINIGAME_SHEET + " has an unexpected size; left unchanged");
            return data;
        }
        BufferedImage img = new BufferedImage(70, 96, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(src, 0, 0, null);
        for (int[] g : GLYPHS) {
            drawLabel(img, LABELS[g[0]], g[1], g[2], g[3], g[4]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        System.out.println("[patch] " + MINIGAME_SHEET + ": minigame keys shown as buttons");
        return out.toByteArray();
    }

    private static void drawLabel(BufferedImage img, String label, int x, int y, int w, int h) {
        // Take the glyph's own ink colour (darkest opaque pixel) and outline colour (lightest).
        int ink = 0xFF737DB5;
        int light = 0xFFBFD2FF;
        int dark = Integer.MAX_VALUE;
        int bright = -1;
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                int p = img.getRGB(xx, yy);
                if ((p >>> 24) == 0) {
                    continue;
                }
                int lum = ((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF);
                if (lum < dark) {
                    dark = lum;
                    ink = p;
                }
                if (lum > bright) {
                    bright = lum;
                    light = p;
                }
            }
        }
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                img.setRGB(xx, yy, 0);
            }
        }
        boolean[][] mask = new boolean[h][w];
        if (label.length() == 1 && "^v<>".indexOf(label.charAt(0)) >= 0) {
            arrow(mask, label.charAt(0), w, h);
        } else if (label.length() == 1) {
            // Single letter: 3x5 font scaled 2x, centred.
            blit(mask, FONT.get(label.charAt(0)), (w - 6) / 2, (h - 10) / 2, 2, 2);
        } else {
            // Two characters ("L1"): 3x5 font, 1x wide and 2x tall, 1px gap.
            int x0 = (w - 7) / 2;
            int y0 = (h - 10) / 2;
            blit(mask, FONT.get(label.charAt(0)), x0, y0, 1, 2);
            blit(mask, FONT.get(label.charAt(1)), x0 + 4, y0, 1, 2);
        }
        // Light 1px halo like the original glyphs, then the ink.
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (!mask[yy][xx] && near(mask, xx, yy)) {
                    img.setRGB(x + xx, y + yy, light);
                }
            }
        }
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (mask[yy][xx]) {
                    img.setRGB(x + xx, y + yy, ink);
                }
            }
        }
    }

    private static boolean near(boolean[][] m, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int yy = y + dy;
                int xx = x + dx;
                if (yy >= 0 && yy < m.length && xx >= 0 && xx < m[0].length && m[yy][xx]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void blit(boolean[][] m, String[] glyph, int x0, int y0, int sx, int sy) {
        for (int gy = 0; gy < glyph.length; gy++) {
            for (int gx = 0; gx < glyph[gy].length(); gx++) {
                if (glyph[gy].charAt(gx) != '1') {
                    continue;
                }
                for (int yy = 0; yy < sy; yy++) {
                    for (int xx = 0; xx < sx; xx++) {
                        int px = x0 + gx * sx + xx;
                        int py = y0 + gy * sy + yy;
                        if (py >= 0 && py < m.length && px >= 0 && px < m[0].length) {
                            m[py][px] = true;
                        }
                    }
                }
            }
        }
    }

    /** A chunky 2px-stroke arrow filling most of the box. */
    private static void arrow(boolean[][] m, char dir, int w, int h) {
        int s = Math.min(w, h) - 1;
        int ox = (w - s) / 2;
        int oy = (h - s) / 2;
        int c = s / 2;
        for (int v = 0; v < s; v++) {        // v: distance from the tip
            for (int u = 0; u < s; u++) {    // u: across the arrow
                int d = Math.abs(u - c);
                boolean shaft = d <= 1 && v >= 1;
                boolean head = v <= c + 1 && d <= v && d >= v - 2;
                if (!shaft && !head) {
                    continue;
                }
                int px;
                int py;
                switch (dir) {
                    case '^': px = u; py = v; break;
                    case 'v': px = u; py = s - 1 - v; break;
                    case '<': px = v; py = u; break;
                    default:  px = s - 1 - v; py = u; break;
                }
                m[oy + py][ox + px] = true;
            }
        }
    }
}
