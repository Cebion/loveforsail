package javax.microedition.lcdui;

import java.awt.image.BufferedImage;

/** Bridges package-private shim helpers to javax.microedition.lcdui.game. */
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
}
