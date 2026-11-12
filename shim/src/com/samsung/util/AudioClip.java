package com.samsung.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * Samsung AudioClip API on the MMAPI shim. The format is taken from the data, not the type: the
 * Larry Samsung build passes TYPE_MMF for standard MIDI files. MMF (SMAF) data itself isn't
 * decoded and plays silently.
 */
public class AudioClip {
    public static final int TYPE_MMF = 1;
    public static final int TYPE_MP3 = 2;
    public static final int TYPE_MIDI = 3;

    private Player player;

    public AudioClip(int type, String filename) throws IOException {
        String name = filename.startsWith("/") ? filename.substring(1) : filename;
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException("resource not found: " + filename);
        }
        try (InputStream s = in) {
            create(s.readAllBytes());
        }
    }

    public AudioClip(int type, byte[] audioData, int audioOffset, int audioLength) {
        byte[] d = new byte[audioLength];
        System.arraycopy(audioData, audioOffset, d, 0, audioLength);
        create(d);
    }

    private void create(byte[] data) {
        try {
            player = Manager.createPlayer(new ByteArrayInputStream(data), null);
            player.realize();
        } catch (Exception e) {
            player = null;
        }
    }

    public static boolean isSupported() {
        return true;
    }

    /** loop: times to play, 1..255 (255 is used for "keep playing"); volume: 0..5. */
    public void play(int loop, int volume) {
        if (player == null) {
            return;
        }
        try {
            player.stop();
            player.setMediaTime(0);
            player.setLoopCount(loop <= 0 || loop >= 255 ? -1 : loop);
            VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(Math.max(0, Math.min(5, volume)) * 20);
            }
            player.start();
        } catch (Exception ignored) {
        }
    }

    public void pause() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public void resume() {
        if (player != null) {
            try {
                player.start();
            } catch (Exception ignored) {
            }
        }
    }

    public void stop() {
        if (player != null) {
            try {
                player.stop();
                player.setMediaTime(0);
            } catch (Exception ignored) {
            }
        }
    }
}
