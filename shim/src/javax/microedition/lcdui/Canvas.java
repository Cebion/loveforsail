package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;

    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    /** Nokia-style device keycodes used by the host input mapping. */
    public static final int KEYCODE_UP = -1;
    public static final int KEYCODE_DOWN = -2;
    public static final int KEYCODE_LEFT = -3;
    public static final int KEYCODE_RIGHT = -4;
    public static final int KEYCODE_FIRE = -5;
    public static final int KEYCODE_SOFT_LEFT = -6;
    public static final int KEYCODE_SOFT_RIGHT = -7;

    /** Receives finished frames (ARGB, Display.WIDTH x Display.HEIGHT). Set by the launcher. */
    public interface FrameSink {
        void present(int[] argb, int width, int height);
    }

    public static volatile FrameSink frameSink;

    private static final int EV_PRESS = 1;
    private static final int EV_RELEASE = 2;
    private static final int EV_REPEAT = 3;
    private static final ArrayDeque<int[]> keyQueue = new ArrayDeque<>();
    private static final Object dispatchLock = new Object();
    private static volatile long lastDispatch;

    private BufferedImage screen;

    protected Canvas() {
    }

    protected abstract void paint(Graphics g);

    public void setFullScreenMode(boolean mode) {
    }

    public boolean hasPointerEvents() {
        return false;
    }

    public boolean hasPointerMotionEvents() {
        return false;
    }

    public boolean hasRepeatEvents() {
        return true;
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case KEYCODE_UP:
            case KEY_NUM2:
                return UP;
            case KEYCODE_DOWN:
            case KEY_NUM8:
                return DOWN;
            case KEYCODE_LEFT:
            case KEY_NUM4:
                return LEFT;
            case KEYCODE_RIGHT:
            case KEY_NUM6:
                return RIGHT;
            case KEYCODE_FIRE:
            case KEY_NUM5:
                return FIRE;
            case KEY_NUM7:
                return GAME_A;
            case KEY_NUM9:
                return GAME_B;
            case KEY_STAR:
                return GAME_C;
            case KEY_POUND:
                return GAME_D;
            default:
                return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return KEYCODE_UP;
            case DOWN:
                return KEYCODE_DOWN;
            case LEFT:
                return KEYCODE_LEFT;
            case RIGHT:
                return KEYCODE_RIGHT;
            case FIRE:
                return KEYCODE_FIRE;
            case GAME_A:
                return KEY_NUM7;
            case GAME_B:
                return KEY_NUM9;
            case GAME_C:
                return KEY_STAR;
            case GAME_D:
                return KEY_POUND;
            default:
                throw new IllegalArgumentException();
        }
    }

    public String getKeyName(int keyCode) {
        if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) {
            return String.valueOf((char) keyCode);
        }
        switch (keyCode) {
            case KEY_STAR:
                return "*";
            case KEY_POUND:
                return "#";
            case KEYCODE_UP:
                return "Up";
            case KEYCODE_DOWN:
                return "Down";
            case KEYCODE_LEFT:
                return "Left";
            case KEYCODE_RIGHT:
                return "Right";
            case KEYCODE_FIRE:
                return "Select";
            case KEYCODE_SOFT_LEFT:
                return "Soft1";
            case KEYCODE_SOFT_RIGHT:
                return "Soft2";
            default:
                return "Key" + keyCode;
        }
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    void shimShow() {
        showNotify();
    }

    void shimHide() {
        hideNotify();
    }

    public final void repaint() {
        repaint(0, 0, getWidth(), getHeight());
    }

    public final void repaint(int x, int y, int w, int h) {
        if (Display.current != this) {
            return;
        }
        if (screen == null) {
            screen = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        }
        Graphics g = new Graphics(screen);
        paint(g);
        g.dispose();
        presentImage(screen);
        dispatchKeyEvents();
    }

    public final void serviceRepaints() {
    }

    // ---- shim internals ------------------------------------------------------------

    /** Called from the host thread: queue a key event for delivery on the game thread. */
    public static void postKey(boolean pressed, int keyCode) {
        synchronized (keyQueue) {
            keyQueue.add(new int[] {pressed ? EV_PRESS : EV_RELEASE, keyCode});
        }
    }

    public static void postKeyRepeat(int keyCode) {
        synchronized (keyQueue) {
            keyQueue.add(new int[] {EV_REPEAT, keyCode});
        }
    }

    public static boolean hasPendingKeys() {
        synchronized (keyQueue) {
            return !keyQueue.isEmpty();
        }
    }

    /**
     * Keys are normally delivered on the game thread when it flushes a frame. A game that waits
     * for a key without drawing (common in MIDP 1 style Canvas games) would never see it, so the
     * host also delivers keys that have been waiting longer than maxAgeMs, as a phone would.
     */
    public static void dispatchIfStale(long maxAgeMs) {
        if (hasPendingKeys() && System.currentTimeMillis() - lastDispatch > maxAgeMs) {
            dispatchKeyEvents();
        }
    }

    /** Deliver queued key events to the current canvas. Runs on whichever thread flushes frames. */
    public static void dispatchKeyEvents() {
        synchronized (dispatchLock) {
            lastDispatch = System.currentTimeMillis();
            dispatchLocked();
        }
    }

    private static void dispatchLocked() {
        Displayable d = Display.current;
        if (!(d instanceof Canvas)) {
            return;
        }
        Canvas c = (Canvas) d;
        while (true) {
            int[] ev;
            synchronized (keyQueue) {
                ev = keyQueue.poll();
            }
            if (ev == null) {
                return;
            }
            try {
                if (c instanceof javax.microedition.lcdui.game.GameCanvas && ev[0] != EV_REPEAT) {
                    ((javax.microedition.lcdui.game.GameCanvas) c).shimKeyState(ev[1], ev[0] == EV_PRESS);
                }
                if (ev[0] == EV_PRESS) {
                    c.keyPressed(ev[1]);
                } else if (ev[0] == EV_RELEASE) {
                    c.keyReleased(ev[1]);
                } else {
                    c.keyRepeated(ev[1]);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static void presentImage(BufferedImage img) {
        FrameSink sink = frameSink;
        if (sink != null) {
            int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            sink.present(px, img.getWidth(), img.getHeight());
        }
    }
}
