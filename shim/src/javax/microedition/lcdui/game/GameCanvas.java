package javax.microedition.lcdui.game;

import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.ShimAccess;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << Canvas.UP;
    public static final int DOWN_PRESSED = 1 << Canvas.DOWN;
    public static final int LEFT_PRESSED = 1 << Canvas.LEFT;
    public static final int RIGHT_PRESSED = 1 << Canvas.RIGHT;
    public static final int FIRE_PRESSED = 1 << Canvas.FIRE;
    public static final int GAME_A_PRESSED = 1 << Canvas.GAME_A;
    public static final int GAME_B_PRESSED = 1 << Canvas.GAME_B;
    public static final int GAME_C_PRESSED = 1 << Canvas.GAME_C;
    public static final int GAME_D_PRESSED = 1 << Canvas.GAME_D;

    private final BufferedImage buffer;
    private final boolean suppressKeyEvents;
    private int keyStates;
    private int latchedStates;

    protected GameCanvas(boolean suppressKeyEvents) {
        this.suppressKeyEvents = suppressKeyEvents;
        buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = buffer.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    protected Graphics getGraphics() {
        return ShimAccess.graphics(buffer);
    }

    public int getKeyStates() {
        int s = keyStates | latchedStates;
        latchedStates = 0;
        return s;
    }

    public void paint(Graphics g) {
        g.drawImage(ShimAccess.wrap(buffer), 0, 0, Graphics.TOP | Graphics.LEFT);
    }

    public void flushGraphics() {
        ShimAccess.present(buffer);
        Canvas.dispatchKeyEvents();
    }

    public void flushGraphics(int x, int y, int w, int h) {
        flushGraphics();
    }

    /** Track game-action key state; called by the shim before the game's own handlers. */
    public void shimKeyState(int keyCode, boolean pressed) {
        int action;
        try {
            action = getGameAction(keyCode);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (action == 0) {
            return;
        }
        int bit = 1 << action;
        if (pressed) {
            keyStates |= bit;
            latchedStates |= bit;
        } else {
            keyStates &= ~bit;
        }
    }

    public boolean shimSuppressesKeyEvents() {
        return suppressKeyEvents;
    }
}
