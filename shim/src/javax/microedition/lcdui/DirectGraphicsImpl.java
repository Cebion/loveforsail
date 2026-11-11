package javax.microedition.lcdui;

import com.nokia.mid.ui.DirectGraphics;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Nokia DirectGraphics on top of the shim's Graphics. Byte (grayscale/332) formats are not drawn. */
final class DirectGraphicsImpl implements DirectGraphics {
    private final Graphics gr;

    DirectGraphicsImpl(Graphics gr) {
        this.gr = gr;
    }

    // ---- images and pixels ---------------------------------------------------------

    @Override
    public void drawImage(Image img, int x, int y, int anchor, int manipulation) {
        if (img == null) {
            throw new NullPointerException();
        }
        BufferedImage src = img.img;
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getRGB(0, 0, w, h, px, 0, w);
        BufferedImage out = manipulate(px, w, h, manipulation);
        gr.drawImage(new Image(out, false), x, y, anchor == 0 ? Graphics.TOP | Graphics.LEFT : anchor);
    }

    @Override
    public void drawPixels(int[] pixels, boolean transparency, int offset, int scanlength, int x, int y,
            int width, int height, int manipulation, int format) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] px = new int[width * height];
        boolean alpha = transparency && format == TYPE_INT_8888_ARGB;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int v = pixels[offset + r * scanlength + c];
                px[r * width + c] = alpha ? v : (v | 0xFF000000);
            }
        }
        blit(px, width, height, x, y, manipulation);
    }

    @Override
    public void drawPixels(short[] pixels, boolean transparency, int offset, int scanlength, int x, int y,
            int width, int height, int manipulation, int format) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] px = new int[width * height];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int v = toArgb(pixels[offset + r * scanlength + c] & 0xFFFF, format);
                px[r * width + c] = transparency ? v : (v | 0xFF000000);
            }
        }
        blit(px, width, height, x, y, manipulation);
    }

    @Override
    public void drawPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y,
            int width, int height, int manipulation, int format) {
    }

    @Override
    public void getPixels(int[] pixels, int offset, int scanlength, int x, int y, int width, int height,
            int format) {
        BufferedImage t = gr.target();
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                pixels[offset + r * scanlength + c] = pixelAt(t, x + c, y + r);
            }
        }
    }

    @Override
    public void getPixels(short[] pixels, int offset, int scanlength, int x, int y, int width, int height,
            int format) {
        BufferedImage t = gr.target();
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                pixels[offset + r * scanlength + c] = (short) fromArgb(pixelAt(t, x + c, y + r), format);
            }
        }
    }

    @Override
    public void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y,
            int width, int height, int format) {
    }

    // ---- shapes --------------------------------------------------------------------

    @Override
    public void drawPolygon(int[] xs, int xOffset, int[] ys, int yOffset, int n, int argb) {
        withColor(argb, () -> gr.awt().drawPolygon(slice(xs, xOffset, n), slice(ys, yOffset, n), n));
    }

    @Override
    public void fillPolygon(int[] xs, int xOffset, int[] ys, int yOffset, int n, int argb) {
        withColor(argb, () -> gr.awt().fillPolygon(slice(xs, xOffset, n), slice(ys, yOffset, n), n));
    }

    @Override
    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argb) {
        drawPolygon(new int[] {x1, x2, x3}, 0, new int[] {y1, y2, y3}, 0, 3, argb);
    }

    @Override
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argb) {
        fillPolygon(new int[] {x1, x2, x3}, 0, new int[] {y1, y2, y3}, 0, 3, argb);
    }

    @Override
    public int getAlphaComponent() {
        return (gr.argbColor() >>> 24) & 0xFF;
    }

    @Override
    public int getNativePixelFormat() {
        return TYPE_INT_8888_ARGB;
    }

    @Override
    public void setARGBColor(int argb) {
        gr.setArgbColor(argb);
    }

    // ---- helpers -------------------------------------------------------------------

    private void withColor(int argb, Runnable r) {
        java.awt.Graphics2D g = gr.awt();
        Color old = g.getColor();
        g.setColor(new Color(argb, true));
        r.run();
        g.setColor(old);
    }

    private static int[] slice(int[] a, int off, int n) {
        int[] o = new int[n];
        System.arraycopy(a, off, o, 0, n);
        return o;
    }

    private int pixelAt(BufferedImage t, int x, int y) {
        int ax = x + gr.getTranslateX();
        int ay = y + gr.getTranslateY();
        if (ax < 0 || ay < 0 || ax >= t.getWidth() || ay >= t.getHeight()) {
            return 0;
        }
        return t.getRGB(ax, ay);
    }

    private void blit(int[] px, int w, int h, int x, int y, int manipulation) {
        gr.awt().drawImage(manipulate(px, w, h, manipulation), x, y, null);
    }

    /** Flips first, then rotation counter-clockwise, as in the Nokia UI API. */
    static BufferedImage manipulate(int[] px, int w, int h, int manipulation) {
        boolean fh = (manipulation & FLIP_HORIZONTAL) != 0;
        boolean fv = (manipulation & FLIP_VERTICAL) != 0;
        int rot = ((manipulation & 0x0FFF) % 360 + 360) % 360;
        boolean swap = rot == 90 || rot == 270;
        int dw = swap ? h : w;
        int dh = swap ? w : h;
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        int[] o = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int fx = fh ? w - 1 - x : x;
                int fy = fv ? h - 1 - y : y;
                int dx;
                int dy;
                switch (rot) {
                    case 90:  dx = fy;         dy = w - 1 - fx; break;
                    case 180: dx = w - 1 - fx; dy = h - 1 - fy; break;
                    case 270: dx = h - 1 - fy; dy = fx;         break;
                    default:  dx = fx;         dy = fy;         break;
                }
                o[dy * dw + dx] = px[y * w + x];
            }
        }
        return out;
    }

    private static int toArgb(int v, int format) {
        switch (format) {
            case TYPE_USHORT_4444_ARGB:
                return (expand4(v >> 12) << 24) | (expand4(v >> 8) << 16) | (expand4(v >> 4) << 8) | expand4(v);
            case TYPE_USHORT_444_RGB:
                return 0xFF000000 | (expand4(v >> 8) << 16) | (expand4(v >> 4) << 8) | expand4(v);
            case TYPE_USHORT_565_RGB:
                return 0xFF000000 | (expand5(v >> 11) << 16) | (expand6(v >> 5) << 8) | expand5(v);
            case TYPE_USHORT_1555_ARGB:
                return ((v & 0x8000) != 0 ? 0xFF000000 : 0)
                        | (expand5(v >> 10) << 16) | (expand5(v >> 5) << 8) | expand5(v);
            default: // 555
                return 0xFF000000 | (expand5(v >> 10) << 16) | (expand5(v >> 5) << 8) | expand5(v);
        }
    }

    private static int fromArgb(int c, int format) {
        int a = c >>> 24;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        switch (format) {
            case TYPE_USHORT_4444_ARGB:
                return ((a >> 4) << 12) | ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            case TYPE_USHORT_444_RGB:
                return ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            case TYPE_USHORT_565_RGB:
                return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
            case TYPE_USHORT_1555_ARGB:
                return (a >= 128 ? 0x8000 : 0) | ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
            default:
                return ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
        }
    }

    private static int expand4(int v) {
        v &= 0xF;
        return v | (v << 4);
    }

    private static int expand5(int v) {
        v &= 0x1F;
        return (v << 3) | (v >> 2);
    }

    private static int expand6(int v) {
        v &= 0x3F;
        return (v << 2) | (v >> 4);
    }
}
