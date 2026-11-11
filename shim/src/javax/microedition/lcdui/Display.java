package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    /** Canvas size; the launcher sets midp.width/midp.height from the game version before this loads. */
    public static final int WIDTH = Integer.getInteger("midp.width", 240);
    public static final int HEIGHT = Integer.getInteger("midp.height", 320);

    static volatile Displayable current;
    private static Display instance;

    private Display() {
    }

    public static synchronized Display getDisplay(MIDlet m) {
        if (instance == null) {
            instance = new Display();
        }
        return instance;
    }

    public void setCurrent(Displayable d) {
        Displayable old = current;
        if (old == d) {
            return;
        }
        if (old instanceof Canvas) {
            try {
                ((Canvas) old).shimHide();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        current = d;
        if (d instanceof Canvas) {
            try {
                ((Canvas) d).shimShow();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (!(d instanceof javax.microedition.lcdui.game.GameCanvas)) {
                ((Canvas) d).repaint();
            }
        }
    }

    public Displayable getCurrent() {
        return current;
    }

    public static Displayable currentDisplayable() {
        return current;
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 1 << 24;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public boolean vibrate(int duration) {
        return false;
    }

    public boolean flashBacklight(int duration) {
        return false;
    }

    public void callSerially(Runnable r) {
        new Thread(r).start();
    }
}
