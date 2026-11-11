import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the original game jar on top of the MIDP shim. Resource reads go through
 * {@link #getResourceAsStream}, which lets the port rewrite selected resources (button prompts
 * in the text tables, minigame key glyphs) in memory. The game jar itself is never modified.
 */
final class GameLoader extends URLClassLoader {
    private final ConcurrentHashMap<String, byte[]> patched = new ConcurrentHashMap<>();

    GameLoader(URL gameJar, ClassLoader parent) {
        super(new URL[] {gameJar}, parent);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        String key = name.startsWith("/") ? name.substring(1) : name;
        byte[] cached = patched.get(key);
        if (cached != null) {
            return new ByteArrayInputStream(cached);
        }
        InputStream in = super.getResourceAsStream(key);
        if (in == null || !ResourcePatches.wants(key)) {
            return in;
        }
        try {
            byte[] original = readAll(in);
            byte[] out = ResourcePatches.apply(key, original);
            patched.put(key, out);
            return new ByteArrayInputStream(out);
        } catch (IOException e) {
            System.out.println("[patch] " + key + ": " + e);
            return super.getResourceAsStream(key);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
