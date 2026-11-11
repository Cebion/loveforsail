package javax.microedition.media;

import java.util.Timer;
import java.util.TimerTask;

/** Plays nothing but keeps MIDP state and END_OF_MEDIA timing, so game logic waiting on it works. */
class SilentPlayer extends BasePlayer {
    private static final Timer TIMER = new Timer("silent-player", true);
    private final long durationMs;
    private TimerTask task;
    private long startedAt;
    private long position;

    SilentPlayer(String type, long durationMs) {
        super(type);
        this.durationMs = Math.max(1, durationMs);
    }

    protected void doPrefetch() {
    }

    protected synchronized void doStart() {
        startedAt = System.currentTimeMillis();
        task = new TimerTask() {
            public void run() {
                synchronized (SilentPlayer.this) {
                    position = 0;
                    task = null;
                }
                endOfMedia();
            }
        };
        TIMER.schedule(task, Math.max(1, durationMs - position));
    }

    protected synchronized void doStop() {
        if (task != null) {
            task.cancel();
            task = null;
            position += System.currentTimeMillis() - startedAt;
        }
    }

    protected void doClose() {
        doStop();
    }

    protected void applyVolume() {
    }

    public synchronized long setMediaTime(long now) {
        position = Math.max(0, Math.min(durationMs, now / 1000));
        return position * 1000;
    }

    public synchronized long getMediaTime() {
        long p = position + (task != null ? System.currentTimeMillis() - startedAt : 0);
        return p * 1000;
    }

    public long getDuration() {
        return durationMs * 1000;
    }
}
