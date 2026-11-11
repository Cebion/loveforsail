package javax.microedition.lcdui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import javax.imageio.ImageIO;

public class Image {
    final BufferedImage img;
    final boolean mutable;
    /** Cache of transformed regions for immutable images (key: region + transform). */
    HashMap<Long, BufferedImage> regionCache;

    Image(BufferedImage img, boolean mutable) {
        this.img = img;
        this.mutable = mutable;
    }

    static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Image decode(InputStream in, String what) throws IOException {
        if (in == null) {
            throw new IOException("resource not found: " + what);
        }
        try {
            BufferedImage b = ImageIO.read(in);
            if (b == null) {
                throw new IOException("cannot decode image: " + what);
            }
            return new Image(toArgb(b), false);
        } finally {
            in.close();
        }
    }

    public static Image createImage(String name) throws IOException {
        String path = name.startsWith("/") ? name.substring(1) : name;
        return decode(javax.microedition.midlet.MIDlet.LarryHost.resources().getResourceAsStream(path), name);
    }

    public static Image createImage(InputStream stream) throws IOException {
        return decode(stream, "stream");
    }

    public static Image createImage(byte[] data, int offset, int length) {
        try {
            return decode(new ByteArrayInputStream(data, offset, length), "byte[]");
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        return new Image(b, true);
    }

    public static Image createImage(Image source) {
        if (!source.mutable) {
            return source;
        }
        return new Image(copy(source.img), false);
    }

    public static Image createImage(Image image, int x, int y, int width, int height, int transform) {
        BufferedImage region = Graphics.transformRegion(image.img, x, y, width, height, transform);
        return new Image(region, false);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < width * height; i++) {
            px[i] = processAlpha ? rgb[i] : (rgb[i] | 0xFF000000);
        }
        return new Image(b, false);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException();
        }
        return new Graphics(img);
    }

    public int getWidth() {
        return img.getWidth();
    }

    public int getHeight() {
        return img.getHeight();
    }

    public boolean isMutable() {
        return mutable;
    }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
        img.getRGB(x, y, width, height, rgbData, offset, scanlength);
    }
}
