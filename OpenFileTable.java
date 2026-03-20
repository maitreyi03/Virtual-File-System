package vfs;

/**
 * One slot in the Open File Table.
 * Holds the I/O buffer, current position, file size, and descriptor index.
 */
class OftEntry {
    static final int UNUSED = -1;

    int[] buffer;
    int   position;       // byte offset into file (-1 = slot unused)
    int   fileSize;
    int   descriptorIndex;

    OftEntry() {
        buffer = new int[Config.BLOCK_SIZE];
        clear();
    }

    boolean isUnused() { return position == UNUSED; }

    void clear() {
        java.util.Arrays.fill(buffer, 0);
        position        = UNUSED;
        fileSize        = 0;
        descriptorIndex = UNUSED;
    }
}

/**
 * The Open File Table — a fixed array of OftEntry slots.
 * Slot 0 is permanently occupied by the directory.
 */
public class OpenFileTable implements Config {

    private final OftEntry[] slots;

    public OpenFileTable() {
        slots = new OftEntry[OFT_SIZE];
        for (int i = 0; i < OFT_SIZE; i++) slots[i] = new OftEntry();
    }

    /** Reset all slots (called on system initialisation). */
    public void init() {
        for (OftEntry e : slots) e.clear();
        // Set up directory slot
        OftEntry dir = slots[DIR_OFT_SLOT];
        dir.position        = 0;
        dir.fileSize        = 0;
        dir.descriptorIndex = 0;
    }

    /** Find the first unused slot that is NOT the directory slot. */
    public int allocate() {
        for (int i = 1; i < OFT_SIZE; i++) {
            if (slots[i].isUnused()) return i;
        }
        throw new FileSystemException.NoSpace("OFT slot");
    }

    /** Return the slot for the given index, validating it is in use. */
    public OftEntry getOpen(int index) {
        if (index < 0 || index >= OFT_SIZE || slots[index].isUnused()) {
            throw new FileSystemException.NotOpen(index);
        }
        return slots[index];
    }

    /** Return the slot without checking whether it's in use. */
    public OftEntry get(int index) {
        if (index < 0 || index >= OFT_SIZE)
            throw new FileSystemException.OutOfBounds("OFT index " + index);
        return slots[index];
    }

    /** Release a slot back to free. */
    public void free(int index) {
        slots[index].clear();
    }

    /** Returns the OFT index for a given descriptor, or -1 if not open. */
    public int findByDescriptor(int descIdx) {
        for (int i = 0; i < OFT_SIZE; i++) {
            if (slots[i].descriptorIndex == descIdx) return i;
        }
        return -1;
    }
}
