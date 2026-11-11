package javax.microedition.rms;

import java.util.ArrayList;

/** Snapshot enumeration (keepUpdated is accepted but the snapshot is only rebuilt on request). */
class SimpleRecordEnumeration implements RecordEnumeration {
    private final RecordStore store;
    private final RecordFilter filter;
    private final RecordComparator comparator;
    private int[] ids = new int[0];
    private int pos;

    SimpleRecordEnumeration(RecordStore store, RecordFilter filter, RecordComparator comparator) {
        this.store = store;
        this.filter = filter;
        this.comparator = comparator;
        rebuild();
    }

    public void rebuild() {
        ArrayList<int[]> keep = new ArrayList<>();
        ArrayList<byte[]> data = new ArrayList<>();
        for (int id : store.recordIds()) {
            byte[] b;
            try {
                b = store.getRecord(id);
            } catch (RecordStoreException e) {
                continue;
            }
            if (filter == null || filter.matches(b)) {
                keep.add(new int[] {id, data.size()});
                data.add(b);
            }
        }
        if (comparator != null) {
            keep.sort((a, b) -> comparator.compare(data.get(a[1]), data.get(b[1])));
        }
        ids = new int[keep.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = keep.get(i)[0];
        }
        pos = 0;
    }

    public int numRecords() {
        return ids.length;
    }

    public byte[] nextRecord() throws RecordStoreException {
        return store.getRecord(nextRecordId());
    }

    public int nextRecordId() throws InvalidRecordIDException {
        if (pos >= ids.length) {
            throw new InvalidRecordIDException();
        }
        return ids[pos++];
    }

    public byte[] previousRecord() throws RecordStoreException {
        return store.getRecord(previousRecordId());
    }

    public int previousRecordId() throws InvalidRecordIDException {
        if (pos <= 0) {
            throw new InvalidRecordIDException();
        }
        return ids[--pos];
    }

    public boolean hasNextElement() {
        return pos < ids.length;
    }

    public boolean hasPreviousElement() {
        return pos > 0;
    }

    public void reset() {
        pos = 0;
    }

    public void keepUpdated(boolean keepUpdated) {
    }

    public boolean isKeptUpdated() {
        return false;
    }

    public void destroy() {
        ids = new int[0];
    }
}
