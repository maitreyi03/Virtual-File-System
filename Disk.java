package vfs;

import java.util.Arrays;

/**
 * Simulates a raw block-addressable disk.
 * All other components read/write through this class — no direct array access.
 */
public class Disk implements Config {

    private final int[][] blocks;

    public Disk() {
        blocks = new int[BLOCK_COUNT][BLOCK_SIZE];
    }

    /** Read a full block into the provided buffer (must be BLOCK_SIZE). */
    public void readBlock(int blockNum, int[] buffer) {
        checkBlock(blockNum);
        System.arraycopy(blocks[blockNum], 0, buffer, 0, BLOCK_SIZE);
    }

    /** Write the provided buffer into a full block (must be BLOCK_SIZE). */
    public void writeBlock(int blockNum, int[] buffer) {
        checkBlock(blockNum);
        System.arraycopy(buffer, 0, blocks[blockNum], 0, BLOCK_SIZE);
    }

    /** Returns a fresh zeroed buffer of BLOCK_SIZE. */
    public static int[] newBuffer() {
        return new int[BLOCK_SIZE];
    }

    /** Wipe all blocks back to zero (used on system re-initialisation). */
    public void reset() {
        for (int[] block : blocks) Arrays.fill(block, 0);
    }

    private void checkBlock(int n) {
        if (n < 0 || n >= BLOCK_COUNT)
            throw new FileSystemException.OutOfBounds("block " + n);
    }
}
