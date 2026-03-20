package vfs;

/**
 * Central configuration constants for the virtual file system.
 * Changing values here affects the entire system consistently.
 */
public interface Config {
    int BLOCK_COUNT       = 64;
    int BLOCK_SIZE        = 512;
    int MAX_FILE_BLOCKS   = 3;      // max data blocks per file
    int MAX_FILENAME_LEN  = 3;
    int MAX_DESCRIPTORS   = 192;

    // Block layout on disk
    int BITMAP_BLOCK      = 0;
    int DESCRIPTOR_BLOCKS_START = 1;
    int DESCRIPTOR_BLOCKS_END   = 6;   // inclusive
    int DIRECTORY_BLOCK   = 7;
    int DATA_BLOCK_START  = 8;

    // Descriptor: [size, block0, block1, block2]
    int DESCRIPTOR_SIZE   = 4;
    int DESCRIPTORS_PER_BLOCK = BLOCK_SIZE / DESCRIPTOR_SIZE; // 128

    // Directory entry: 4-char name + 4-byte descriptor index = 8 bytes
    int ENTRY_NAME_LEN    = 4;
    int ENTRY_IDX_LEN     = 4;
    int ENTRY_SIZE        = ENTRY_NAME_LEN + ENTRY_IDX_LEN;

    // Open File Table — slot 0 is reserved for the directory
    int OFT_SIZE          = 4;
    int DIR_OFT_SLOT      = 0;
}
