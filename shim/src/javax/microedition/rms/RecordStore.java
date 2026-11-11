package javax.microedition.rms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** File-backed RecordStore: one file per store under -Dmidp.rms.dir (default ./savedata). */
public class RecordStore {
    public static final int AUTHMODE_PRIVATE = 0;
    public static final int AUTHMODE_ANY = 1;

    private static final int MAGIC = 0x524D5331; // "RMS1"
    private static final Map<String, RecordStore> open = new HashMap<>();

    private final String name;
    private final File file;
    private final TreeMap<Integer, byte[]> records = new TreeMap<>();
    private final ArrayList<RecordListener> listeners = new ArrayList<>();
    private int nextId = 1;
    private int version;
    private long lastModified;
    private int openCount;

    private RecordStore(String name, File file) {
        this.name = name;
        this.file = file;
    }

    private static File dir() {
        File d = new File(System.getProperty("midp.rms.dir", "savedata"));
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    private static File fileFor(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : String.format("%%%04x", (int) c));
        }
        return new File(dir(), sb + ".rms");
    }

    public static RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        if (name == null || name.length() == 0 || name.length() > 32) {
            throw new IllegalArgumentException("bad record store name");
        }
        synchronized (open) {
            RecordStore rs = open.get(name);
            if (rs == null) {
                File f = fileFor(name);
                rs = new RecordStore(name, f);
                if (f.isFile()) {
                    rs.load();
                } else if (create) {
                    rs.lastModified = System.currentTimeMillis();
                    rs.save();
                } else {
                    throw new RecordStoreNotFoundException(name);
                }
                open.put(name, rs);
            }
            rs.openCount++;
            return rs;
        }
    }

    public static RecordStore openRecordStore(String name, boolean create, int authmode, boolean writable)
            throws RecordStoreException {
        return openRecordStore(name, create);
    }

    public static RecordStore openRecordStore(String name, String vendor, String suite) throws RecordStoreException {
        return openRecordStore(name, false);
    }

    public static void deleteRecordStore(String name) throws RecordStoreException {
        synchronized (open) {
            RecordStore rs = open.get(name);
            if (rs != null && rs.openCount > 0) {
                throw new RecordStoreException("record store is open: " + name);
            }
            File f = fileFor(name);
            if (!f.isFile()) {
                throw new RecordStoreNotFoundException(name);
            }
            if (!f.delete()) {
                throw new RecordStoreException("cannot delete " + f);
            }
            open.remove(name);
        }
    }

    public static String[] listRecordStores() {
        File[] files = dir().listFiles((d, n) -> n.endsWith(".rms"));
        if (files == null || files.length == 0) {
            return null;
        }
        ArrayList<String> names = new ArrayList<>();
        for (File f : files) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                if (in.readInt() == MAGIC) {
                    names.add(in.readUTF());
                }
            } catch (IOException ignored) {
            }
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    private void load() throws RecordStoreException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != MAGIC) {
                throw new RecordStoreException("corrupt record store " + file);
            }
            in.readUTF();
            nextId = in.readInt();
            version = in.readInt();
            lastModified = in.readLong();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] b = new byte[len];
                in.readFully(b);
                records.put(id, b);
            }
        } catch (IOException e) {
            throw new RecordStoreException("cannot read " + file + ": " + e);
        }
    }

    private void save() throws RecordStoreException {
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp);
                DataOutputStream out = new DataOutputStream(fos)) {
            out.writeInt(MAGIC);
            out.writeUTF(name);
            out.writeInt(nextId);
            out.writeInt(version);
            out.writeLong(lastModified);
            out.writeInt(records.size());
            for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue().length);
                out.write(e.getValue());
            }
            out.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            throw new RecordStoreFullException("cannot write " + file + ": " + e);
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RecordStoreFullException("cannot replace " + file + ": " + e);
        }
    }

    private void checkOpen() throws RecordStoreNotOpenException {
        if (openCount <= 0) {
            throw new RecordStoreNotOpenException(name);
        }
    }

    private void touched() throws RecordStoreException {
        version++;
        lastModified = System.currentTimeMillis();
        save();
    }

    public void closeRecordStore() throws RecordStoreException {
        synchronized (open) {
            checkOpen();
            openCount--;
            if (openCount == 0) {
                listeners.clear();
                open.remove(name);
            }
        }
    }

    public String getName() throws RecordStoreNotOpenException {
        checkOpen();
        return name;
    }

    public synchronized int getVersion() throws RecordStoreNotOpenException {
        checkOpen();
        return version;
    }

    public synchronized long getLastModified() throws RecordStoreNotOpenException {
        checkOpen();
        return lastModified;
    }

    public synchronized int getNumRecords() throws RecordStoreNotOpenException {
        checkOpen();
        return records.size();
    }

    public synchronized int getSize() throws RecordStoreNotOpenException {
        checkOpen();
        int s = 64;
        for (byte[] b : records.values()) {
            s += b.length + 8;
        }
        return s;
    }

    public int getSizeAvailable() throws RecordStoreNotOpenException {
        checkOpen();
        return 1024 * 1024;
    }

    public synchronized int getNextRecordID() throws RecordStoreNotOpenException {
        checkOpen();
        return nextId;
    }

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException {
        int id;
        synchronized (this) {
            checkOpen();
            byte[] b = new byte[numBytes];
            if (numBytes > 0) {
                System.arraycopy(data, offset, b, 0, numBytes);
            }
            id = nextId++;
            records.put(id, b);
            touched();
        }
        for (RecordListener l : listenerSnapshot()) {
            l.recordAdded(this, id);
        }
        return id;
    }

    public void deleteRecord(int recordId) throws RecordStoreException {
        synchronized (this) {
            checkOpen();
            if (records.remove(recordId) == null) {
                throw new InvalidRecordIDException(String.valueOf(recordId));
            }
            touched();
        }
        for (RecordListener l : listenerSnapshot()) {
            l.recordDeleted(this, recordId);
        }
    }

    public void setRecord(int recordId, byte[] data, int offset, int numBytes) throws RecordStoreException {
        synchronized (this) {
            checkOpen();
            if (!records.containsKey(recordId)) {
                throw new InvalidRecordIDException(String.valueOf(recordId));
            }
            byte[] b = new byte[numBytes];
            if (numBytes > 0) {
                System.arraycopy(data, offset, b, 0, numBytes);
            }
            records.put(recordId, b);
            touched();
        }
        for (RecordListener l : listenerSnapshot()) {
            l.recordChanged(this, recordId);
        }
    }

    public synchronized byte[] getRecord(int recordId) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(recordId);
        if (b == null) {
            throw new InvalidRecordIDException(String.valueOf(recordId));
        }
        return b.length == 0 ? null : b.clone();
    }

    public synchronized int getRecord(int recordId, byte[] buffer, int offset) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(recordId);
        if (b == null) {
            throw new InvalidRecordIDException(String.valueOf(recordId));
        }
        System.arraycopy(b, 0, buffer, offset, b.length);
        return b.length;
    }

    public synchronized int getRecordSize(int recordId) throws RecordStoreException {
        checkOpen();
        byte[] b = records.get(recordId);
        if (b == null) {
            throw new InvalidRecordIDException(String.valueOf(recordId));
        }
        return b.length;
    }

    synchronized int[] recordIds() {
        int[] ids = new int[records.size()];
        int i = 0;
        for (Integer id : records.keySet()) {
            ids[i++] = id;
        }
        return ids;
    }

    public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator, boolean keepUpdated)
            throws RecordStoreNotOpenException {
        checkOpen();
        return new SimpleRecordEnumeration(this, filter, comparator);
    }

    public void addRecordListener(RecordListener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    public void removeRecordListener(RecordListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    private RecordListener[] listenerSnapshot() {
        synchronized (listeners) {
            return listeners.toArray(new RecordListener[0]);
        }
    }

    public void setMode(int authmode, boolean writable) {
    }
}
