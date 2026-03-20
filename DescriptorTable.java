package vfs;

import java.util.Arrays;

/**
 * A single file descriptor: [fileSize, block0, block1, block2].
 * Index 0 is reserved for the directory descriptor.
 */
class Descriptor {
    static final int FREE_MARKER = -1;

    int fileSize;
    int[] dataBlocks;   // length == Config.MAX_FILE_BLOCKS

    Descriptor() {
        fileSize = FREE_MARKER;
        dataBlocks = new int[Config.MAX_FILE_BLOCKS];
    }

    boolean isFree() { return fileSize == FREE_MARKER; }

    void markFree() {
        fileSize = FREE_MARKER;
        Arrays.fill(dataBlocks, 0);
    }

    void init() {
        fileSize = 0;
        Arrays.fill(dataBlocks, 0);
    }
}

/**
 * Manages all file descriptors stored in disk blocks 1–6.
 * Keeps an in-memory cache and flushes to disk on every mutation.
 *
 * <p>Layout on disk: each descriptor occupies DESCRIPTOR_SIZE bytes.
 * [0] = fileSize (or FREE_MARKER = -1), [1..3] = data block numbers.
 */
public class DescriptorTable implements Config {

    private final Descriptor[] table;
    private final Disk disk;

    public DescriptorTable(Disk disk) {
        this.disk = disk;
        table = new Descriptor[MAX_DESCRIPTORS];
        for (int i = 0; i < MAX_DESCRIPTORS; i++) table[i] = new Descriptor();
    }

    /**
     * Initialise the table in memory and on disk.
     * Descriptor 0 is the directory (size=0, block=DIRECTORY_BLOCK).
     */
    public void init() {
        for (Descriptor d : table) d.markFree();
        // Directory descriptor
        table[0].fileSize = 0;
        table[0].dataBlocks[0] = DIRECTORY_BLOCK;
        flush();
    }

    /** Load all descriptors from disk into the cache. */
    public void load() {
        int[] buf = Disk.newBuffer();
        int descPerBlock = BLOCK_SIZE / DESCRIPTOR_SIZE;
        for (int blk = DESCRIPTOR_BLOCKS_START; blk <= DESCRIPTOR_BLOCKS_END; blk++) {
            disk.readBlock(blk, buf);
            int baseDesc = (blk - DESCRIPTOR_BLOCKS_START) * descPerBlock;
            for (int i = 0; i < descPerBlock && (baseDesc + i) < MAX_DESCRIPTORS; i++) {
                int base = i * DESCRIPTOR_SIZE;
                Descriptor d = table[baseDesc + i];
                d.fileSize = buf[base];
                for (int j = 0; j < MAX_FILE_BLOCKS; j++) {
                    d.dataBlocks[j] = buf[base + 1 + j];
                }
            }
        }
    }

    /** Flush all descriptors to disk. */
    public void flush() {
        int[] buf = Disk.newBuffer();
        int descPerBlock = BLOCK_SIZE / DESCRIPTOR_SIZE;
        for (int blk = DESCRIPTOR_BLOCKS_START; blk <= DESCRIPTOR_BLOCKS_END; blk++) {
            Arrays.fill(buf, 0);
            int baseDesc = (blk - DESCRIPTOR_BLOCKS_START) * descPerBlock;
            for (int i = 0; i < descPerBlock && (baseDesc + i) < MAX_DESCRIPTORS; i++) {
                int base = i * DESCRIPTOR_SIZE;
                Descriptor d = table[baseDesc + i];
                buf[base] = d.fileSize;
                for (int j = 0; j < MAX_FILE_BLOCKS; j++) {
                    buf[base + 1 + j] = d.dataBlocks[j];
                }
            }
            disk.writeBlock(blk, buf);
        }
    }

    /** Find a free descriptor slot (skipping slot 0 = directory). */
    public int allocate() {
        for (int i = 1; i < MAX_DESCRIPTORS; i++) {
            if (table[i].isFree()) {
                table[i].init();
                return i;
            }
        }
        throw new FileSystemException.NoSpace("descriptor");
    }

    /** Free a descriptor and flush. */
    public void free(int index) {
        table[index].markFree();
        flush();
    }

    public Descriptor get(int index) { return table[index]; }
}
