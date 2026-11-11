package javax.microedition.lcdui;

import java.awt.image.BufferedImage;

/** Bridges package-private shim helpers to javax.microedition.lcdui.game and com.nokia.mid.ui. */
public final class ShimAccess {
    private ShimAccess() {
    }

    public static Image wrap(BufferedImage img) {
        return new Image(img, true);
    }

    public static Graphics graphics(BufferedImage img) {
        return new Graphics(img);
    }

    public static void present(BufferedImage img) {
        Canvas.presentImage(img);
    }

    public static com.nokia.mid.ui.DirectGraphics directGraphics(Graphics g) {
        return new DirectGraphicsImpl(g);
    }

    public static Image createFilledImage(int w, int h, int argb) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Arrays.fill(((java.awt.image.DataBufferInt) b.getRaster().getDataBuffer()).getData(), argb);
        return new Image(b, true);
    }

    public static Image mutableCopy(Image im) {
        BufferedImage b = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.drawImage(im.img, 0, 0, null);
        g.dispose();
        return new Image(b, true);
    }
}
