package vfs;

/**
 * Manages a bit-per-block allocation bitmap.
 * The bitmap is stored in BITMAP_BLOCK (block 0) on disk.
 * An in-memory boolean array is kept in sync for O(1) queries.
 */
public class Bitmap implements Config {

    private final boolean[] used;   // true = block in use
    private final Disk disk;

    public Bitmap(Disk disk) {
        this.disk = disk;
        this.used = new boolean[BLOCK_COUNT];
    }

    /**
     * Initialise: mark system blocks (0 – DATA_BLOCK_START-1) as used,
     * all data blocks as free, then persist to disk.
     */
    public void init() {
        for (int i = 0; i < BLOCK_COUNT; i++) {
            used[i] = (i < DATA_BLOCK_START);
        }
        flush();
    }

    /** Load bitmap state from disk into the in-memory array. */
    public void load() {
        int[] buf = Disk.newBuffer();
        disk.readBlock(BITMAP_BLOCK, buf);
        for (int i = 0; i < BLOCK_COUNT; i++) {
            used[i] = ((buf[i / 8] >> (i % 8)) & 1) == 1;
        }
    }

    /** Save in-memory bitmap to disk. */
    public void flush() {
        int[] buf = Disk.newBuffer();
        for (int i = 0; i < BLOCK_COUNT; i++) {
            if (used[i]) buf[i / 8] |= (1 << (i % 8));
        }
        disk.writeBlock(BITMAP_BLOCK, buf);
    }

    /**
     * Allocate the first free data block.
     * @return block number, or throws NoSpace
     */
    public int allocate() {
        for (int i = DATA_BLOCK_START; i < BLOCK_COUNT; i++) {
            if (!used[i]) {
                used[i] = true;
                flush();
                return i;
            }
        }
        throw new FileSystemException.NoSpace("data block");
    }

    /** Release a block back to the free pool. */
    public void free(int blockNum) {
        if (blockNum >= DATA_BLOCK_START && blockNum < BLOCK_COUNT) {
            used[blockNum] = false;
            flush();
        }
    }

    /** Returns true if the block is currently allocated. */
    public boolean isUsed(int blockNum) {
        return used[blockNum];
    }
}
