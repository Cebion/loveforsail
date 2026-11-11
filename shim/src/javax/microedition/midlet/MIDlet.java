package javax.microedition.midlet;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class MIDlet {
    private static Attributes appProperties;

    protected MIDlet() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() {
        LarryHost.exit();
    }

    public final void notifyPaused() {
    }

    public final void resumeRequest() {
    }

    public final String getAppProperty(String key) {
        Attributes attrs = getAppProperties();
        return attrs == null ? null : attrs.getValue(key);
    }

    public final boolean platformRequest(String url) {
        System.out.println("[midlet] platformRequest ignored: " + url);
        return false;
    }

    public final int checkPermission(String permission) {
        return 1;
    }

    public static synchronized Attributes getAppProperties() {
        if (appProperties != null) {
            return appProperties;
        }
        try {
            Enumeration<URL> urls = LarryHost.resources().getResources("META-INF/MANIFEST.MF");
            while (urls.hasMoreElements()) {
                try (InputStream in = urls.nextElement().openStream()) {
                    Attributes attrs = new Manifest(in).getMainAttributes();
                    if (attrs.getValue("MIDlet-1") != null) {
                        appProperties = attrs;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[midlet] manifest read failed: " + e);
        }
        return appProperties;
    }

    /** Hook implemented by the launcher so the shim can end the process cleanly. */
    public static final class LarryHost {
        private static Runnable exitHandler;
        private static volatile ClassLoader gameLoader;

        /** The loader holding the game jar; resources (images, data) are read through it. */
        public static void setGameLoader(ClassLoader cl) {
            gameLoader = cl;
        }

        public static ClassLoader resources() {
            ClassLoader cl = gameLoader;
            return cl != null ? cl : MIDlet.class.getClassLoader();
        }

        public static void setExitHandler(Runnable r) {
            exitHandler = r;
        }

        public static void start(MIDlet m) throws MIDletStateChangeException {
            m.startApp();
        }

        public static void pause(MIDlet m) {
            m.pauseApp();
        }

        public static void destroy(MIDlet m) throws MIDletStateChangeException {
            m.destroyApp(true);
        }

        static void exit() {
            if (exitHandler != null) {
                exitHandler.run();
            } else {
                System.exit(0);
            }
        }
    }
}
