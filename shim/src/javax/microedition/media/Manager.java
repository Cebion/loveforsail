package javax.microedition.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";
    public static final String MIDI_DEVICE_LOCATOR = "device://midi";

    private Manager() {
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[] {"audio/midi", "audio/mid", "audio/x-midi", "audio/sp-midi", "audio/x-wav", "audio/wav"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[] {"file", "resource"};
    }

    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException {
        if (stream == null) {
            throw new IllegalArgumentException("stream is null");
        }
        byte[] data = readAll(stream);
        String t = type == null ? "" : type.toLowerCase();
        if (t.contains("mid") || isMidi(data)) {
            return new MidiPlayer(data, type);
        }
        if (t.contains("wav") || isWav(data)) {
            return new WavPlayer(data, type);
        }
        System.out.println("[media] unsupported content type " + type + ", using silent player");
        return new SilentPlayer(type, 1000L);
    }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        throw new MediaException("locator players not supported: " + locator);
    }

    public static void playTone(int note, int duration, int volume) throws MediaException {
    }

    private static boolean isMidi(byte[] d) {
        return d.length > 4 && d[0] == 'M' && d[1] == 'T' && d[2] == 'h' && d[3] == 'd';
    }

    private static boolean isWav(byte[] d) {
        return d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F';
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
