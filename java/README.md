# Virtual File System — Java Rewrite
### CS143B Project 1 — Improved Architecture

---

## Project structure

```
vfs/
├── Config.java            Interface — all magic numbers in one place
├── FileSystemException.java Exception hierarchy (NotFound, NoSpace, etc.)
├── Disk.java              Raw block device simulation
├── Bitmap.java            Free-block allocation bitmap
├── Descriptor.java        Single file descriptor (inside DescriptorTable.java)
├── DescriptorTable.java   All descriptors + disk persistence
├── OftEntry.java          One OFT slot (inside OpenFileTable.java)
├── OpenFileTable.java     Open File Table management
├── Directory.java         Name ↔ descriptor mapping on disk block 7
├── FileSystem.java        Public API — create/destroy/open/close/read/write/seek
└── Shell.java             Command parser + main entry point
```

---

## How to compile and run

```bash
# From the directory containing the vfs/ folder:
javac vfs/*.java

# Run (will prompt for an input file path):
java vfs.Shell
# Enter: FS-input-1.txt
```

---

## Command reference

| Command | Arguments | Description |
|---------|-----------|-------------|
| `in`    | —         | Re-initialise the file system |
| `cr`    | name      | Create a new file (max 3-char alphanumeric name) |
| `de`    | name      | Destroy a file (must not be open) |
| `op`    | name      | Open a file → prints `name opened <index>` |
| `cl`    | index     | Close open file at OFT index |
| `rd`    | idx mpos cnt | Read `cnt` bytes from file to memory[mpos] |
| `wr`    | idx mpos cnt | Write `cnt` bytes from memory[mpos] to file |
| `sk`    | idx pos   | Seek file position pointer to `pos` |
| `dr`    | —         | List directory: `name size ...` |
| `wm`    | pos data  | Write string `data` into memory at `pos` |
| `rm`    | pos cnt   | Read `cnt` chars from memory at `pos` |

---

## Improvements over the Python version

### Separation of concerns
The Python code was a single 570-line class mixing disk simulation, memory
management, file I/O, directory handling, and command parsing together.
The Java version splits these into dedicated classes with clear boundaries.

### Typed error handling
Python used bare `try/except → return "error"`, swallowing all errors silently.
Java uses a `FileSystemException` hierarchy so the *kind* of error is
preserved internally and only collapsed to "error" at the Shell boundary.
This makes debugging far easier.

### Typed data objects
Python represented descriptors as `[int, int, int, int]` raw arrays and
OFT entries as plain dicts. Java uses `Descriptor` and `OftEntry` objects
with named fields — no more `entry['descriptor_index']` vs `desc[0]` confusion.

### Centralised configuration
All constants (block counts, sizes, limits) live in the `Config` interface.
Changing `OFT_SIZE` or `MAX_FILE_BLOCKS` now propagates everywhere
automatically.

### Cleaner directory management
The `Directory` class encapsulates all entry read/write/compact logic,
making it straightforward to later extend with subdirectory support.

---

## Disk layout

```
Block 0       : Bitmap (1 bit per block)
Blocks 1–6    : File descriptors (128 descriptors per block × 6 = 768 slots,
                capped at MAX_DESCRIPTORS = 192)
Block 7       : Directory entries (8 bytes each: 4-char name + 4-byte index)
Blocks 8–63   : Data blocks (55 × 512 = 28 160 bytes usable storage)
```

Each file may occupy at most 3 data blocks (1 536 bytes max).
