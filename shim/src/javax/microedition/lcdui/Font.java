package javax.microedition.lcdui;

public final class Font {
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;

    private static final Font DEFAULT = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);

    private final int face;
    private final int style;
    private final int size;
    private java.awt.Font awtFont;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
    }

    public static Font getDefaultFont() {
        return DEFAULT;
    }

    public static Font getFont(int specifier) {
        return DEFAULT;
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(face, style, size);
    }

    synchronized java.awt.Font awt() {
        if (awtFont == null) {
            int px = size == SIZE_SMALL ? 10 : size == SIZE_LARGE ? 16 : 12;
            int st = ((style & STYLE_BOLD) != 0 ? java.awt.Font.BOLD : 0)
                    | ((style & STYLE_ITALIC) != 0 ? java.awt.Font.ITALIC : 0);
            awtFont = new java.awt.Font(face == FACE_MONOSPACE ? java.awt.Font.MONOSPACED : java.awt.Font.SANS_SERIF, st, px);
        }
        return awtFont;
    }

    private java.awt.FontMetrics metrics() {
        java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = tmp.createGraphics();
        try {
            return g.getFontMetrics(awt());
        } finally {
            g.dispose();
        }
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public boolean isPlain() {
        return style == STYLE_PLAIN;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int getHeight() {
        try {
            return metrics().getHeight();
        } catch (Throwable t) {
            return size == SIZE_SMALL ? 12 : size == SIZE_LARGE ? 18 : 14;
        }
    }

    public int getBaselinePosition() {
        try {
            return metrics().getAscent();
        } catch (Throwable t) {
            return getHeight() - 3;
        }
    }

    public int stringWidth(String s) {
        try {
            return metrics().stringWidth(s);
        } catch (Throwable t) {
            return s.length() * 6;
        }
    }

    public int substringWidth(String s, int offset, int len) {
        return stringWidth(s.substring(offset, offset + len));
    }

    public int charWidth(char c) {
        return stringWidth(String.valueOf(c));
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return stringWidth(new String(ch, offset, length));
    }
}
