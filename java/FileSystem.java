package vfs;

import java.util.Arrays;

/**
 * Virtual File System — public API.
 *
 * <p>All methods return a human-readable result string or throw a
 * {@link FileSystemException} sub-class. The {@link Shell} catches exceptions
 * and translates them to "error" for command-file output.
 *
 * <p>Component responsibilities:
 * <ul>
 *   <li>{@link Disk}            – raw block device</li>
 *   <li>{@link Bitmap}          – free-block tracking</li>
 *   <li>{@link DescriptorTable} – file metadata</li>
 *   <li>{@link OpenFileTable}   – in-flight I/O state</li>
 *   <li>{@link Directory}       – name ↔ descriptor mapping</li>
 *   <li>memory[]                – shared I/O buffer (one BLOCK_SIZE array)</li>
 * </ul>
 */
public class FileSystem implements Config {

    // Components
    private final Disk            disk;
    private final Bitmap          bitmap;
    private final DescriptorTable descriptors;
    private final OpenFileTable   oft;
    private final Directory       directory;

    // Shared memory buffer
    private final int[] memory;

    public FileSystem() {
        disk        = new Disk();
        bitmap      = new Bitmap(disk);
        descriptors = new DescriptorTable(disk);
        oft         = new OpenFileTable();
        directory   = new Directory(oft, disk);
        memory      = new int[BLOCK_SIZE];
    }

    // ── System ────────────────────────────────────────────────────────────────

    /** Re-initialise the entire file system (in command: "in"). */
    public String initialize() {
        disk.reset();
        bitmap.init();
        descriptors.init();
        oft.init();
        directory.init();
        Arrays.fill(memory, 0);
        return "system initialized";
    }

    // ── File management ───────────────────────────────────────────────────────

    /** Create a new empty file. */
    public String create(String name) {
        validateName(name);
        if (directory.find(name) != -1)
            throw new FileSystemException.AlreadyExists(name);

        int descIdx = descriptors.allocate();
        try {
            directory.addEntry(name, descIdx);
        } catch (FileSystemException e) {
            descriptors.free(descIdx);   // rollback
            throw e;
        }
        descriptors.flush();
        return name + " created";
    }

    /** Delete a file (must not be open). */
    public String destroy(String name) {
        int descIdx = directory.find(name);
        if (descIdx == -1) throw new FileSystemException.NotFound(name);
        if (oft.findByDescriptor(descIdx) != -1)
            throw new FileSystemException.FileOpen(name);

        // Release data blocks
        Descriptor d = descriptors.get(descIdx);
        for (int blk : d.dataBlocks) {
            if (blk != 0) bitmap.free(blk);
        }
        descriptors.free(descIdx);
        directory.removeEntry(name);
        return name + " destroyed";
    }

    /** Open a file and return its OFT index. */
    public String open(String name) {
        int descIdx = directory.find(name);
        if (descIdx == -1) throw new FileSystemException.NotFound(name);
        if (oft.findByDescriptor(descIdx) != -1)
            throw new FileSystemException.AlreadyExists(name + " (already open)");

        int slot = oft.allocate();
        OftEntry e = oft.get(slot);
        e.descriptorIndex = descIdx;
        e.fileSize        = descriptors.get(descIdx).fileSize;
        e.position        = 0;

        // Load first block into buffer if it exists
        int firstBlock = descriptors.get(descIdx).dataBlocks[0];
        if (firstBlock != 0) disk.readBlock(firstBlock, e.buffer);

        return name + " opened " + slot;
    }

    /** Flush and close an open file. */
    public String close(int index) {
        OftEntry e = oft.getOpen(index);
        flushOftBuffer(index, e);
        // Persist updated file size to descriptor
        descriptors.get(e.descriptorIndex).fileSize = e.fileSize;
        descriptors.flush();
        oft.free(index);
        return index + " closed";
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    /** Read up to count bytes from file[index] into memory[memPos..]. */
    public String read(int index, int memPos, int count) {
        validateMemory(memPos, count);
        OftEntry e = oft.getOpen(index);

        int bytesRead = 0;
        while (bytesRead < count && e.position < e.fileSize) {
            int blockIdx = e.position / BLOCK_SIZE;
            int offset   = e.position % BLOCK_SIZE;

            // Load block into buffer if crossing a block boundary
            if (offset == 0 && bytesRead > 0) {
                int blk = descriptors.get(e.descriptorIndex).dataBlocks[blockIdx];
                if (blk != 0) disk.readBlock(blk, e.buffer);
            }

            memory[memPos + bytesRead] = e.buffer[offset];
            bytesRead++;
            e.position++;
        }
        return bytesRead + " bytes read from file " + index;
    }

    /** Write count bytes from memory[memPos..] into file[index]. */
    public String write(int index, int memPos, int count) {
        validateMemory(memPos, count);
        OftEntry e   = oft.getOpen(index);
        int descIdx  = e.descriptorIndex;
        int written  = 0;

        while (written < count) {
            int blockIdx = e.position / BLOCK_SIZE;
            if (blockIdx >= MAX_FILE_BLOCKS) break;   // file size limit

            int offset = e.position % BLOCK_SIZE;

            // If entering a new block after writing, flush previous block first
            if (offset == 0 && written > 0) {
                int prevBlockIdx = (e.position - 1) / BLOCK_SIZE;
                int prevBlk = descriptors.get(descIdx).dataBlocks[prevBlockIdx];
                if (prevBlk != 0) disk.writeBlock(prevBlk, e.buffer);
            }

            // Allocate new data block if needed
            if (offset == 0 && descriptors.get(descIdx).dataBlocks[blockIdx] == 0) {
                int newBlk = bitmap.allocate();
                descriptors.get(descIdx).dataBlocks[blockIdx] = newBlk;
                Arrays.fill(e.buffer, 0);
            }

            e.buffer[offset] = memory[memPos + written];
            written++;
            e.position++;
            if (e.position > e.fileSize) e.fileSize = e.position;
        }

        // Flush the final partial block
        if (written > 0) {
            int lastBlockIdx = (e.position - 1) / BLOCK_SIZE;
            int lastBlk = descriptors.get(descIdx).dataBlocks[lastBlockIdx];
            if (lastBlk != 0) disk.writeBlock(lastBlk, e.buffer);
        }

        descriptors.get(descIdx).fileSize = e.fileSize;
        descriptors.flush();
        return written + " bytes written to file " + index;
    }

    /** Move the file position pointer. */
    public String seek(int index, int pos) {
        OftEntry e = oft.getOpen(index);
        if (pos < 0 || pos > e.fileSize)
            throw new FileSystemException.OutOfBounds("seek to " + pos);

        int oldBlock = e.position / BLOCK_SIZE;
        int newBlock = pos / BLOCK_SIZE;

        if (oldBlock != newBlock) {
            // Flush old block
            int oldBlk = descriptors.get(e.descriptorIndex).dataBlocks[oldBlock];
            if (oldBlk != 0) disk.writeBlock(oldBlk, e.buffer);
            // Load new block
            int newBlk = (newBlock < MAX_FILE_BLOCKS)
                    ? descriptors.get(e.descriptorIndex).dataBlocks[newBlock]
                    : 0;
            if (newBlk != 0) disk.readBlock(newBlk, e.buffer);
            else Arrays.fill(e.buffer, 0);
        }

        e.position = pos;
        return "position is " + pos;
    }

    /** List directory contents as "name size ..." */
    public String directoryListing() {
        return directory.listAll(descriptors);
    }

    // ── Memory I/O ────────────────────────────────────────────────────────────

    /** Write a string into the shared memory buffer at pos. */
    public String writeMemory(int pos, String data) {
        validateMemory(pos, data.length());
        for (int i = 0; i < data.length(); i++)
            memory[pos + i] = data.charAt(i);
        return data.length() + " bytes written to M";
    }

    /** Read count characters from memory starting at pos. */
    public String readMemory(int pos, int count) {
        validateMemory(pos, count);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int b = memory[pos + i];
            if (b != 0) sb.append((char) b);
        }
        return sb.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void flushOftBuffer(int slot, OftEntry e) {
        if (e.position < 0) return;
        int blockIdx = (e.position == 0) ? 0 : (e.position - 1) / BLOCK_SIZE;
        if (blockIdx < MAX_FILE_BLOCKS) {
            int blk = descriptors.get(e.descriptorIndex).dataBlocks[blockIdx];
            if (blk != 0) disk.writeBlock(blk, e.buffer);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_FILENAME_LEN)
            throw new FileSystemException.InvalidName(name);
        for (char c : name.toCharArray())
            if (!Character.isLetterOrDigit(c))
                throw new FileSystemException.InvalidName(name);
    }

    private void validateMemory(int pos, int count) {
        if (pos < 0 || count < 0 || pos + count > BLOCK_SIZE)
            throw new FileSystemException.OutOfBounds("memory[" + pos + ".." + (pos + count) + "]");
    }
}
