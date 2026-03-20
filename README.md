# Virtual File System Simulator

### CS143B · Project 1 — Maitreyi Pareek (maitreyp · 76592576)

---

## Project overview

This project implements a virtual file system that simulates a toy disk using flat arrays. It supports creating, opening, reading, writing, seeking, closing, and destroying files through a simple command interface. The project is available in three forms:

| Deliverable           | Location             | How to run                    |
| --------------------- | -------------------- | ----------------------------- |
| Python implementation | `main.py`            | `python3 main.py`             |
| Java implementation   | `java/vfs/*.java`    | see Java section below        |
| Web visualizer        | `vfs-web/index.html` | open in browser               |
| Standalone visualizer | `vfs-simulator.html` | open in browser (single file) |

---

## Disk layout

```
Block 0       Bitmap            1 bit per block, packed into bytes
Blocks 1–6    File descriptors  128 descriptors × 6 blocks = 768 slots (capped at 192)
Block 7       Directory         12 bytes per entry: 10-char name + descriptor index
Blocks 8–63   Data blocks       55 × 512 = 28,160 bytes of usable storage
```

Each file may occupy at most **3 data blocks** (1,536 bytes max).

---

## Command reference

| Command | Syntax                      | Description                                   |
| ------- | --------------------------- | --------------------------------------------- |
| `in`    | `in`                        | Re-initialise the file system                 |
| `cr`    | `cr <name>`                 | Create a new file                             |
| `de`    | `de <name>`                 | Destroy a file — must be closed first         |
| `op`    | `op <name>`                 | Open a file → prints `name opened <slot#>`    |
| `cl`    | `cl <slot#>`                | Close file at OFT slot (**number**, not name) |
| `wm`    | `wm <pos> <string>`         | Write string into memory buffer M at byte pos |
| `rm`    | `rm <pos> <count>`          | Read count bytes from memory at pos           |
| `wr`    | `wr <slot#> <mpos> <count>` | Write count bytes from M into open file       |
| `rd`    | `rd <slot#> <mpos> <count>` | Read count bytes from open file into M        |
| `sk`    | `sk <slot#> <pos>`          | Seek file position pointer to byte pos        |
| `dr`    | `dr`                        | List all files and their sizes                |

**Common mistakes**

- `cl` takes an OFT slot number — `cl 1` not `cl myfile`
- `wr`, `rd`, `sk` first argument is always the OFT slot index
- `de` will return `error` if the file is still open — run `cl` first

---

## Python

### How to run

```bash
python3 main.py
# Enter the file path when prompted, e.g.: FS-input-1.txt
```

Output is printed to stdout and appended to `output.txt`.

---

## Java

### File structure

```
java/
└── vfs/
    ├── Config.java              Constants (block sizes, limits)
    ├── FileSystemException.java Typed exception hierarchy
    ├── Disk.java                Raw block device simulation
    ├── Bitmap.java              Free-block allocation bitmap
    ├── DescriptorTable.java     File metadata + disk persistence
    ├── OpenFileTable.java       OFT slot management
    ├── Directory.java           Name ↔ descriptor mapping (block 7)
    ├── FileSystem.java          Public API
    └── Shell.java               Command parser + main entry point
```

### How to compile and run

Make sure you are in the `java/` folder (the folder that **contains** the `vfs/` subfolder), then:

```bash
# Compile — run from inside java/
cd java
javac vfs/*.java

# Run
java vfs.Shell
# Enter input file path when prompted, e.g.: ../FS-input-1.txt
```

> **Note:** The glob `vfs/*.java` only works when your current directory is `java/`.  
> If you are already inside `java/vfs/`, go up one level first: `cd ..`

Output is printed to stdout and written to `output.txt` in the working directory.

---

## Web visualizer

An interactive browser-based simulator that shows the disk bitmap, open file table, directory, and memory buffer updating in real time as you type commands.

### Two versions

**`vfs-web/`** — organised source (recommended for development)

```
vfs-web/
├── index.html        HTML structure only
├── css/
│   └── styles.css    All visual styles
└── js/
    ├── fs.js         File system engine (no DOM access)
    └── ui.js         Rendering + shell interaction
```

> To open `vfs-web/index.html` locally, use a simple local server — browsers block relative file paths under the `file://` protocol:
>
> ```bash
> # Python (run from inside vfs-web/)
> cd vfs-web
> python3 -m http.server 8080
> # Then open http://localhost:8080 in your browser
> ```

**`vfs-simulator.html`** — single self-contained file, works by double-click with no server needed. Same code as `vfs-web/`, just inlined. Use this for submitting or sharing.

### Running the demo

Click **▶ run full sequence** in the browser to watch a complete create → open → write → read → close → destroy cycle play out step by step with annotations.

---

## Input file format

Commands are listed one per line. Blank lines are ignored. The `in` command resets the system and starts a new session (output is separated by blank lines).

```
in
cr abc
op abc
wm 0 helloworld
wr 1 0 10
sk 1 0
rd 1 20 10
rm 20 10
dr
cl 1
de abc
```
