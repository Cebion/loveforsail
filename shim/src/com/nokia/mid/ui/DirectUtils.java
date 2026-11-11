package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ShimAccess;

public final class DirectUtils {
    private DirectUtils() {
    }

    public static DirectGraphics getDirectGraphics(Graphics g) {
        return ShimAccess.directGraphics(g);
    }

    /** Mutable image filled with an ARGB colour (may be transparent, unlike Image.createImage). */
    public static Image createImage(int width, int height, int argb) {
        return ShimAccess.createFilledImage(width, height, argb);
    }

    public static Image createImage(byte[] data, int offset, int length) {
        Image im = Image.createImage(data, offset, length);
        return ShimAccess.mutableCopy(im);
    }
}
