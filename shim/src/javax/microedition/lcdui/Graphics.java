package javax.microedition.lcdui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;
    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    // Sprite transform constants (javax.microedition.lcdui.game.Sprite)
    static final int TRANS_NONE = 0;
    static final int TRANS_MIRROR_ROT180 = 1;
    static final int TRANS_MIRROR = 2;
    static final int TRANS_ROT180 = 3;
    static final int TRANS_MIRROR_ROT270 = 4;
    static final int TRANS_ROT90 = 5;
    static final int TRANS_ROT270 = 6;
    static final int TRANS_MIRROR_ROT90 = 7;

    private final BufferedImage target;
    private final Graphics2D g;
    private int tx;
    private int ty;
    private int color = 0xFF000000;
    private int strokeStyle = SOLID;
    private Font font = Font.getDefaultFont();

    Graphics(BufferedImage target) {
        this.target = target;
        this.g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setClip(0, 0, target.getWidth(), target.getHeight());
        g.setColor(Color.BLACK);
    }

    void dispose() {
        g.dispose();
    }

    // ---- shim internals (Nokia DirectGraphics) -------------------------------------

    BufferedImage target() {
        return target;
    }

    Graphics2D awt() {
        return g;
    }

    int argbColor() {
        return color;
    }

    void setArgbColor(int argb) {
        color = argb;
        g.setColor(new Color(argb, true));
    }

    // ---- state ---------------------------------------------------------------------

    public void translate(int x, int y) {
        tx += x;
        ty += y;
        g.translate(x, y);
    }

    public int getTranslateX() {
        return tx;
    }

    public int getTranslateY() {
        return ty;
    }

    public void setColor(int rgb) {
        color = 0xFF000000 | rgb;
        g.setColor(new Color(color, true));
    }

    public void setColor(int r, int gr, int b) {
        setColor(((r & 0xFF) << 16) | ((gr & 0xFF) << 8) | (b & 0xFF));
    }

    public int getColor() {
        return color & 0xFFFFFF;
    }

    public int getRedComponent() {
        return (color >> 16) & 0xFF;
    }

    public int getGreenComponent() {
        return (color >> 8) & 0xFF;
    }

    public int getBlueComponent() {
        return color & 0xFF;
    }

    public void setGrayScale(int v) {
        setColor(v, v, v);
    }

    public int getGrayScale() {
        return (getRedComponent() + getGreenComponent() + getBlueComponent()) / 3;
    }

    public int getDisplayColor(int c) {
        return c & 0xFFFFFF;
    }

    public void setStrokeStyle(int style) {
        strokeStyle = style;
    }

    public int getStrokeStyle() {
        return strokeStyle;
    }

    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
    }

    public Font getFont() {
        return font;
    }

    public void setClip(int x, int y, int w, int h) {
        g.setClip(x, y, Math.max(0, w), Math.max(0, h));
    }

    public void clipRect(int x, int y, int w, int h) {
        g.clipRect(x, y, Math.max(0, w), Math.max(0, h));
    }

    private Rectangle clip() {
        Rectangle r = g.getClipBounds();
        return r == null ? new Rectangle(-tx, -ty, target.getWidth(), target.getHeight()) : r;
    }

    public int getClipX() {
        return clip().x;
    }

    public int getClipY() {
        return clip().y;
    }

    public int getClipWidth() {
        return clip().width;
    }

    public int getClipHeight() {
        return clip().height;
    }

    // ---- primitives ----------------------------------------------------------------

    public void drawLine(int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            g.fillRect(x, y, w, h);
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w >= 0 && h >= 0) {
            g.drawRect(x, y, w, h);
        }
    }

    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        g.fillRoundRect(x, y, w, h, aw, ah);
    }

    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        g.drawRoundRect(x, y, w, h, aw, ah);
    }

    public void fillArc(int x, int y, int w, int h, int start, int arc) {
        g.fillArc(x, y, w, h, start, arc);
    }

    public void drawArc(int x, int y, int w, int h, int start, int arc) {
        g.drawArc(x, y, w, h, start, arc);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        g.fillPolygon(new int[] {x1, x2, x3}, new int[] {y1, y2, y3}, 3);
    }

    // ---- text ----------------------------------------------------------------------

    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) {
            throw new NullPointerException();
        }
        try {
            java.awt.Font af = font.awt();
            g.setFont(af);
            java.awt.FontMetrics fm = g.getFontMetrics(af);
            int w = fm.stringWidth(s);
            if ((anchor & HCENTER) != 0) {
                x -= w / 2;
            } else if ((anchor & RIGHT) != 0) {
                x -= w;
            }
            if ((anchor & BOTTOM) != 0) {
                y -= fm.getDescent();
            } else if ((anchor & BASELINE) == 0) {
                y += fm.getAscent();
            }
            g.drawString(s, x, y);
        } catch (Throwable t) {
            // Headless font rendering may be unavailable on the device; text is optional here.
        }
    }

    public void drawSubstring(String s, int offset, int len, int x, int y, int anchor) {
        drawString(s.substring(offset, offset + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) {
        drawString(String.valueOf(c), x, y, anchor);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        drawString(new String(data, offset, length), x, y, anchor);
    }

    // ---- images --------------------------------------------------------------------

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) {
            throw new NullPointerException();
        }
        int w = img.img.getWidth();
        int h = img.img.getHeight();
        x = anchorX(x, w, anchor);
        y = anchorY(y, h, anchor);
        g.drawImage(img.img, x, y, null);
    }

    public void drawRegion(Image src, int sx, int sy, int w, int h, int transform, int x, int y, int anchor) {
        if (src == null) {
            throw new NullPointerException();
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean swap = transform == TRANS_ROT90 || transform == TRANS_ROT270
                || transform == TRANS_MIRROR_ROT90 || transform == TRANS_MIRROR_ROT270;
        int dw = swap ? h : w;
        int dh = swap ? w : h;
        x = anchorX(x, dw, anchor);
        y = anchorY(y, dh, anchor);
        if (transform == TRANS_NONE) {
            g.drawImage(src.img, x, y, x + w, y + h, sx, sy, sx + w, sy + h, null);
            return;
        }
        BufferedImage region;
        if (src.mutable) {
            region = transformRegion(src.img, sx, sy, w, h, transform);
        } else {
            long key = ((long) (sx & 0xFFF) << 52) | ((long) (sy & 0xFFF) << 40)
                    | ((long) (w & 0xFFF) << 28) | ((long) (h & 0xFFF) << 16) | (transform & 0xF);
            if (src.regionCache == null) {
                src.regionCache = new HashMap<>();
            }
            region = src.regionCache.get(key);
            if (region == null) {
                region = transformRegion(src.img, sx, sy, w, h, transform);
                src.regionCache.put(key, region);
            }
        }
        g.drawImage(region, x, y, null);
    }

    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h, boolean processAlpha) {
        if (w <= 0 || h <= 0) {
            return;
        }
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int v = rgb[offset + row * scanlength + col];
                px[row * w + col] = processAlpha ? v : (v | 0xFF000000);
            }
        }
        g.drawImage(b, x, y, null);
    }

    public void copyArea(int sx, int sy, int w, int h, int x, int y, int anchor) {
        x = anchorX(x, w, anchor);
        y = anchorY(y, h, anchor);
        g.copyArea(sx, sy, w, h, x - sx, y - sy);
    }

    private static int anchorX(int x, int w, int anchor) {
        if ((anchor & HCENTER) != 0) {
            return x - w / 2;
        }
        if ((anchor & RIGHT) != 0) {
            return x - w;
        }
        return x;
    }

    private static int anchorY(int y, int h, int anchor) {
        if ((anchor & VCENTER) != 0) {
            return y - h / 2;
        }
        if ((anchor & BOTTOM) != 0) {
            return y - h;
        }
        return y;
    }

    /** Copy a source region applying a MIDP Sprite transform. */
    static BufferedImage transformRegion(BufferedImage src, int sx, int sy, int w, int h, int transform) {
        int[] in = new int[w * h];
        src.getRGB(sx, sy, w, h, in, 0, w);
        boolean swap = transform == TRANS_ROT90 || transform == TRANS_ROT270
                || transform == TRANS_MIRROR_ROT90 || transform == TRANS_MIRROR_ROT270;
        int dw = swap ? h : w;
        int dh = swap ? w : h;
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        int[] o = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        for (int v = 0; v < h; v++) {
            for (int u = 0; u < w; u++) {
                int dx;
                int dy;
                switch (transform) {
                    case TRANS_MIRROR:
                        dx = w - 1 - u;
                        dy = v;
                        break;
                    case TRANS_MIRROR_ROT180:
                        dx = u;
                        dy = h - 1 - v;
                        break;
                    case TRANS_ROT180:
                        dx = w - 1 - u;
                        dy = h - 1 - v;
                        break;
                    case TRANS_ROT90:
                        dx = h - 1 - v;
                        dy = u;
                        break;
                    case TRANS_ROT270:
                        dx = v;
                        dy = w - 1 - u;
                        break;
                    case TRANS_MIRROR_ROT90:
                        dx = h - 1 - v;
                        dy = w - 1 - u;
                        break;
                    case TRANS_MIRROR_ROT270:
                        dx = v;
                        dy = u;
                        break;
                    default:
                        dx = u;
                        dy = v;
                        break;
                }
                o[dy * dw + dx] = in[v * w + u];
            }
        }
        return out;
    }
}
