/** JNI bridge to libsdl2bridge.so: window, frame upload and keyboard events. */
public final class SDL2Bridge {
    public static final int EVENT_NONE = 0;
    public static final int EVENT_QUIT = 1;
    public static final int EVENT_KEYDOWN = 2;
    public static final int EVENT_KEYUP = 3;

    static {
        System.loadLibrary("sdl2bridge");
    }

    private SDL2Bridge() {
    }

    /** winW/winH &lt;= 0 means fullscreen at the desktop resolution. */
    public static native boolean init(int gameW, int gameH, int winW, int winH);

    public static native void updateFrame(int[] argb, int w, int h);

    /** Returns (type &lt;&lt; 16) | SDL scancode, or 0 when the queue is empty. */
    public static native int pollEvent();

    public static native void destroy();
}
