package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
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
        current = d;
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
