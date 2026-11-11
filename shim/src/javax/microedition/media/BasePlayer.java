package javax.microedition.media;

import java.util.ArrayList;
import javax.microedition.media.control.VolumeControl;

abstract class BasePlayer implements Player, VolumeControl {
    protected volatile int state = UNREALIZED;
    protected final String contentType;
    protected int loopCount = 1;
    protected int level = 100;
    protected boolean muted;
    private final ArrayList<PlayerListener> listeners = new ArrayList<>();

    BasePlayer(String contentType) {
        this.contentType = contentType;
    }

    // ---- lifecycle helpers ---------------------------------------------------------

    protected void checkClosed() {
        if (state == CLOSED) {
            throw new IllegalStateException("player closed");
        }
    }

    public void realize() throws MediaException {
        checkClosed();
        if (state == UNREALIZED) {
            state = REALIZED;
        }
    }

    public void prefetch() throws MediaException {
        checkClosed();
        realize();
        if (state == REALIZED) {
            doPrefetch();
            state = PREFETCHED;
        }
    }

    public void start() throws MediaException {
        checkClosed();
        prefetch();
        if (state == STARTED) {
            return;
        }
        state = STARTED;
        doStart();
        fire(PlayerListener.STARTED, Long.valueOf(getMediaTime()));
    }

    public void stop() throws MediaException {
        checkClosed();
        if (state == STARTED) {
            doStop();
            state = PREFETCHED;
            fire(PlayerListener.STOPPED, Long.valueOf(getMediaTime()));
        }
    }

    public void deallocate() {
        checkClosed();
        if (state == STARTED) {
            try {
                stop();
            } catch (MediaException ignored) {
            }
        }
        if (state == PREFETCHED) {
            state = REALIZED;
        }
    }

    public void close() {
        if (state == CLOSED) {
            return;
        }
        if (state == STARTED) {
            doStop();
        }
        doClose();
        state = CLOSED;
        fire(PlayerListener.CLOSED, null);
    }

    /** Called by subclasses when playback reaches its natural end. */
    protected void endOfMedia() {
        if (state != STARTED) {
            return;
        }
        state = PREFETCHED;
        fire(PlayerListener.END_OF_MEDIA, Long.valueOf(getMediaTime()));
    }

    protected abstract void doPrefetch() throws MediaException;

    protected abstract void doStart() throws MediaException;

    protected abstract void doStop();

    protected abstract void doClose();

    protected abstract void applyVolume();

    public int getState() {
        return state;
    }

    public String getContentType() {
        return contentType;
    }

    public void setLoopCount(int count) {
        if (count == 0) {
            throw new IllegalArgumentException();
        }
        loopCount = count;
    }

    // ---- listeners -----------------------------------------------------------------

    public void addPlayerListener(PlayerListener l) {
        if (l != null) {
            synchronized (listeners) {
                listeners.add(l);
            }
        }
    }

    public void removePlayerListener(PlayerListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    protected void fire(String event, Object data) {
        PlayerListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new PlayerListener[0]);
        }
        for (PlayerListener l : ls) {
            try {
                l.playerUpdate(this, event, data);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    // ---- controls ------------------------------------------------------------------

    public Control[] getControls() {
        return new Control[] {this};
    }

    public Control getControl(String type) {
        if (type != null && type.endsWith("VolumeControl")) {
            return this;
        }
        return null;
    }

    public void setMute(boolean mute) {
        muted = mute;
        applyVolume();
    }

    public boolean isMuted() {
        return muted;
    }

    public int setLevel(int l) {
        level = Math.max(0, Math.min(100, l));
        applyVolume();
        return level;
    }

    public int getLevel() {
        return level;
    }

    protected float gain() {
        return muted ? 0f : level / 100f;
    }
}
