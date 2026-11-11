package javax.microedition.lcdui;

public abstract class Displayable {
    public int getWidth() {
        return Display.WIDTH;
    }

    public int getHeight() {
        return Display.HEIGHT;
    }

    public boolean isShown() {
        return Display.current == this;
    }

    protected void sizeChanged(int w, int h) {
    }
}
