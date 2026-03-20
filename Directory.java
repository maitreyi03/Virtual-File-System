package vfs;

/**
 * Manages directory entries stored in DIRECTORY_BLOCK (block 7).
 * Each entry is ENTRY_SIZE bytes: 4-char padded name + 4-byte descriptor index.
 *
 * <p>The directory is always open in OFT slot 0. Its buffer IS the directory
 * block — all reads and writes go directly to/from that buffer and disk.
 */
public class Directory implements Config {

    private final OpenFileTable oft;
    private final Disk          disk;

    public Directory(OpenFileTable oft, Disk disk) {
        this.oft  = oft;
        this.disk = disk;
    }

    /** Load the directory block from disk into the OFT buffer. */
    public void load() {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        disk.readBlock(DIRECTORY_BLOCK, dir.buffer);
    }

    /** Flush the directory buffer to disk. */
    public void flush() {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        disk.writeBlock(DIRECTORY_BLOCK, dir.buffer);
    }

    /** Reset the directory buffer and flush. */
    public void init() {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        java.util.Arrays.fill(dir.buffer, 0);
        dir.fileSize = 0;
        flush();
    }

    /**
     * Look up a file by name.
     * @return descriptor index, or -1 if not found
     */
    public int find(String name) {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        int count = dir.fileSize / ENTRY_SIZE;
        for (int i = 0; i < count; i++) {
            String n = readName(dir.buffer, i);
            if (n.equals(name)) {
                return readDescIndex(dir.buffer, i);
            }
        }
        return -1;
    }

    /**
     * Add a new directory entry.
     * @throws FileSystemException.NoSpace if directory block is full
     */
    public void addEntry(String name, int descIndex) {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        if (dir.fileSize + ENTRY_SIZE > BLOCK_SIZE)
            throw new FileSystemException.NoSpace("directory");

        int pos = dir.fileSize;
        writeName(dir.buffer, pos, name);
        writeDescIndex(dir.buffer, pos + ENTRY_NAME_LEN, descIndex);
        dir.fileSize += ENTRY_SIZE;
        flush();
    }

    /**
     * Remove a directory entry by name (compact — shift remaining entries up).
     */
    public void removeEntry(String name) {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        int count = dir.fileSize / ENTRY_SIZE;
        for (int i = 0; i < count; i++) {
            if (readName(dir.buffer, i).equals(name)) {
                int startByte = i * ENTRY_SIZE;
                int endByte   = startByte + ENTRY_SIZE;
                // Shift everything after this entry up
                System.arraycopy(dir.buffer, endByte,
                                 dir.buffer, startByte,
                                 dir.fileSize - endByte);
                // Zero the vacated tail
                for (int j = dir.fileSize - ENTRY_SIZE; j < dir.fileSize; j++)
                    dir.buffer[j] = 0;
                dir.fileSize -= ENTRY_SIZE;
                flush();
                return;
            }
        }
    }

    /** Return all entries as "name size" strings (for the dr command). */
    public String listAll(DescriptorTable descriptors) {
        OftEntry dir = oft.get(DIR_OFT_SLOT);
        int count = dir.fileSize / ENTRY_SIZE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String name = readName(dir.buffer, i);
            int descIdx = readDescIndex(dir.buffer, i);
            if (descIdx != Descriptor.FREE_MARKER) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(name).append(' ').append(descriptors.get(descIdx).fileSize);
            }
        }
        return sb.toString();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String readName(int[] buf, int entryIndex) {
        int base = entryIndex * ENTRY_SIZE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ENTRY_NAME_LEN; i++) {
            int c = buf[base + i];
            if (c == 0) break;
            sb.append((char) c);
        }
        return sb.toString();
    }

    private int readDescIndex(int[] buf, int entryIndex) {
        return buf[entryIndex * ENTRY_SIZE + ENTRY_NAME_LEN];
    }

    private void writeName(int[] buf, int byteOffset, String name) {
        for (int i = 0; i < ENTRY_NAME_LEN; i++) {
            buf[byteOffset + i] = (i < name.length()) ? name.charAt(i) : 0;
        }
    }

    private void writeDescIndex(int[] buf, int byteOffset, int descIndex) {
        buf[byteOffset] = descIndex;
    }
}
