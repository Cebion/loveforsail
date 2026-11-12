package javax.microedition.media;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/** Sampled-audio player on a Java Sound Clip; silent (but correctly timed) if no audio device. */
class WavPlayer extends BasePlayer {
    private final byte[] data;
    private Clip clip;
    private SilentPlayer fallback;
    private volatile boolean stopping;

    WavPlayer(byte[] data, String type) {
        super(type);
        this.data = data;
    }

    protected void doPrefetch() throws MediaException {
        long durationMs = 1000;
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            durationMs = (long) (ais.getFrameLength() * 1000.0 / ais.getFormat().getFrameRate());
            clip = AudioSystem.getClip();
            clip.open(ais);
            clip.addLineListener(e -> {
                // Only a clip that played to its end; a STOP from an earlier stop() can arrive
                // after the next start() has already cleared "stopping".
                if (e.getType() == LineEvent.Type.STOP && !stopping && !clip.isRunning()
                        && clip.getFramePosition() >= clip.getFrameLength() - 1) {
                    clip.setFramePosition(0);
                    endOfMedia();
                }
            });
        } catch (Throwable t) {
            System.out.println("[media] WAV unavailable, using silent player: " + t);
            clip = null;
            fallback = new SilentPlayer(contentType, durationMs);
            fallback.addPlayerListener((pl, ev, d) -> {
                if (PlayerListener.END_OF_MEDIA.equals(ev)) {
                    endOfMedia();
                }
            });
        }
    }

    protected void doStart() throws MediaException {
        if (fallback != null) {
            fallback.start();
            return;
        }
        applyVolume();
        stopping = false;
        if (loopCount < 0) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else if (loopCount > 1) {
            clip.loop(loopCount - 1);
        } else {
            clip.start();
        }
    }

    protected void doStop() {
        if (fallback != null) {
            try {
                fallback.stop();
            } catch (MediaException ignored) {
            }
            return;
        }
        stopping = true;
        clip.stop();
    }

    protected void doClose() {
        if (clip != null) {
            stopping = true;
            clip.close();
        }
    }

    protected void applyVolume() {
        if (clip == null) {
            return;
        }
        try {
            FloatControl fc = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float g = gain();
            float db = g <= 0.0001f ? fc.getMinimum() : (float) (20.0 * Math.log10(g));
            fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), db)));
        } catch (Exception ignored) {
        }
    }

    public long setMediaTime(long now) throws MediaException {
        if (fallback != null) {
            return fallback.setMediaTime(now);
        }
        clip.setMicrosecondPosition(Math.max(0, now));
        return clip.getMicrosecondPosition();
    }

    public long getMediaTime() {
        if (fallback != null) {
            return fallback.getMediaTime();
        }
        return clip == null ? 0 : clip.getMicrosecondPosition();
    }

    public long getDuration() {
        if (fallback != null) {
            return fallback.getDuration();
        }
        return clip == null ? TIME_UNKNOWN : clip.getMicrosecondLength();
    }
}
