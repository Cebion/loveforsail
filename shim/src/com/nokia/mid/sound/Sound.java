package com.nokia.mid.sound;

import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/**
 * Nokia Sound API. WAV and MIDI data are played through the MMAPI shim. Nokia ringing tones
 * (FORMAT_TONE) become MIDI and plain beeps WAV, both made by NokiaTone.
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

    private static final java.util.concurrent.ScheduledExecutorService CALLBACKS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nokia-sound");
                t.setDaemon(true);
                return t;
            });

    /** Bumped by every play()/stop(), so a late end-of-sound from an earlier play is ignored. */
    private int playId;
    /** Ringtone data and the instrument its MIDI was built with (rebuilt if that changed). */
    private byte[] toneData;
    private int toneProgram = -1;

    public void init(byte[] data, int type) {
        release();
        if (data != null) {
            byte[] media = data;
            String mediaType = null;
            if (type == FORMAT_TONE) {
                toneData = data;
                toneProgram = NokiaTone.program;
                media = NokiaTone.toneToMidi(data);
                mediaType = "audio/midi";
                if (media == null) {
                    media = NokiaTone.silenceToWav(200);
                    mediaType = "audio/x-wav";
                }
            }
            createPlayer(media, mediaType);
        }
        state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        release();
        if (freq > 0 && duration > 0) {
            createPlayer(NokiaTone.beepToWav(freq, duration), "audio/x-wav");
        }
        state = SOUND_STOPPED;
    }

    private void createPlayer(byte[] media, String type) {
        try {
            player = Manager.createPlayer(new ByteArrayInputStream(media), type);
            player.addPlayerListener((p, event, d) -> {
                if (PlayerListener.END_OF_MEDIA.equals(event)) {
                    int id;
                    synchronized (this) {
                        id = playId;
                    }
                    CALLBACKS.execute(() -> ended(id));
                }
            });
            player.realize();
        } catch (Exception e) {
            player = null;
        }
    }

    /**
     * Like the phone: the sound is PLAYING when play() returns (games check getState() right
     * after play() and treat anything else as an interruption, e.g. a call), and the listener
     * hears about play()/stop() directly but about the natural end later, from another thread.
     */
    public void play(int loop) {
        int id;
        synchronized (this) {
            id = ++playId;
            state = SOUND_PLAYING;
        }
        if (toneData != null && toneProgram != NokiaTone.program) {
            byte[] midi = NokiaTone.toneToMidi(toneData);
            if (midi != null) {
                if (player != null) {
                    player.close();
                }
                createPlayer(midi, "audio/midi");
                toneProgram = NokiaTone.program;
            }
        }
        if (player != null) {
            try {
                player.setLoopCount(loop <= 0 ? -1 : loop);
                player.setMediaTime(0);   // the phone restarts a sound from the beginning
                applyGain();
                player.start();
            } catch (Exception ignored) {
            }
        } else {
            CALLBACKS.schedule(() -> ended(id), 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        notifyListener(SOUND_PLAYING);
    }

    public void stop() {
        synchronized (this) {
            playId++;
            state = SOUND_STOPPED;
        }
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
        }
        notifyListener(SOUND_STOPPED);
    }

    private void ended(int id) {
        synchronized (this) {
            if (id != playId || state != SOUND_PLAYING) {
                return;
            }
            state = SOUND_STOPPED;
        }
        notifyListener(SOUND_STOPPED);
    }

    public void resume() {
        play(1);
    }

    public void release() {
        toneData = null;
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

    private void notifyListener(int s) {
        SoundListener l = listener;
        if (l != null) {
            l.soundStateChanged(this, s);
        }
    }
}
