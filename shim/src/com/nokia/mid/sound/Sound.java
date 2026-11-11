package com.nokia.mid.sound;

import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/**
 * Nokia Sound API. WAV and MIDI data are played through the MMAPI shim; Nokia tone
 * sequences (FORMAT_TONE) and plain beeps are silent but report the right state changes.
 */
public class Sound {
    public static final int FORMAT_TONE = 1;
    public static final int FORMAT_WAV = 5;
    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;

    private Player player;
    private SoundListener listener;
    private int state = SOUND_UNINITIALIZED;
    private int gain = 255;

    public Sound(byte[] data, int type) {
        init(data, type);
    }

    public Sound(int freq, long duration) {
        init(freq, duration);
    }

    public static int getConcurrentSoundCount(int type) {
        return 1;
    }

    public static int[] getSupportedFormats() {
        return new int[] {FORMAT_TONE, FORMAT_WAV};
    }

    public void init(byte[] data, int type) {
        release();
        if (type != FORMAT_TONE && data != null) {
            try {
                player = Manager.createPlayer(new ByteArrayInputStream(data), null);
                player.addPlayerListener((p, event, d) -> {
                    if (PlayerListener.END_OF_MEDIA.equals(event)) {
                        changed(SOUND_STOPPED);
                    }
                });
                player.realize();
            } catch (Exception e) {
                player = null;
            }
        }
        state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        release();
        state = SOUND_STOPPED;
    }

    public void play(int loop) {
        if (player != null) {
            try {
                player.setLoopCount(loop <= 0 ? -1 : loop);
                applyGain();
                player.start();
            } catch (Exception ignored) {
            }
        }
        changed(SOUND_PLAYING);
        if (player == null) {
            changed(SOUND_STOPPED);
        }
    }

    public void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
        }
        changed(SOUND_STOPPED);
    }

    public void resume() {
        play(1);
    }

    public void release() {
        if (player != null) {
            player.close();
            player = null;
        }
        state = SOUND_UNINITIALIZED;
    }

    public int getState() {
        return state;
    }

    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(255, gain));
        applyGain();
    }

    public int getGain() {
        return gain;
    }

    public void setSoundListener(SoundListener l) {
        listener = l;
    }

    private void applyGain() {
        if (player != null) {
            VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(gain * 100 / 255);
            }
        }
    }

    private void changed(int s) {
        state = s;
        SoundListener l = listener;
        if (l != null) {
            l.soundStateChanged(this, s);
        }
    }
}
