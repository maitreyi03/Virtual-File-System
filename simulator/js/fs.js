/**
 * fs.js — Virtual File System engine
 *
 * Pure data layer: no DOM access, no rendering.
 * All state lives in module-level variables exported via the FS object.
 *
 * Disk layout:
 *   Block 0      : bitmap
 *   Blocks 1–6   : file descriptors
 *   Block 7      : directory
 *   Blocks 8–63  : data
 */

// ── Constants ────────────────────────────────────────────────────────────────

const BLOCK_COUNT    = 64;
const BLOCK_SIZE     = 512;
const MAX_FILE_BLOCKS = 3;
const MAX_FILENAME_LEN = 10;
const MAX_DESCRIPTORS = 192;
const DESCRIPTOR_SIZE = 4;

const NAME_LEN   = 10;   // bytes reserved for filename in a directory entry
const ENTRY_SIZE = 12;   // NAME_LEN + 1 (desc index) + 1 (padding)
const OFT_SIZE   = 4;    // open file table slots (slot 0 = directory)
const DIR_BLOCK  = 7;
const DATA_START = 8;

// ── State ────────────────────────────────────────────────────────────────────

let disk;         // int[BLOCK_COUNT][BLOCK_SIZE]
let bitmap;       // bool[BLOCK_COUNT]  — true = in use
let descriptors;  // { size, blocks[] }[MAX_DESCRIPTORS]
let oft;          // { buffer, pos, size, desc }[OFT_SIZE]
let memory;       // int[BLOCK_SIZE]   — shared I/O buffer M

// ── Helpers ──────────────────────────────────────────────────────────────────

function newBuf() {
  return new Array(BLOCK_SIZE).fill(0);
}

function saveBitmap() {
  const buf = newBuf();
  for (let i = 0; i < BLOCK_COUNT; i++) {
    if (bitmap[i]) buf[Math.floor(i / 8)] |= (1 << (i % 8));
  }
  disk[0] = buf;
}

function saveDescriptors() {
  for (let blk = 1; blk <= 6; blk++) {
    const buf = newBuf();
    const base = (blk - 1) * 128;
    for (let i = 0; i < 128 && (base + i) < MAX_DESCRIPTORS; i++) {
      const d = descriptors[base + i];
      buf[i * DESCRIPTOR_SIZE]     = d.size;
      buf[i * DESCRIPTOR_SIZE + 1] = d.blocks[0];
      buf[i * DESCRIPTOR_SIZE + 2] = d.blocks[1];
      buf[i * DESCRIPTOR_SIZE + 3] = d.blocks[2];
    }
    disk[blk] = buf;
  }
}

function saveDirBlock() {
  disk[DIR_BLOCK] = oft[0].buffer.slice();
}

function findFreeBlock() {
  for (let i = DATA_START; i < BLOCK_COUNT; i++) if (!bitmap[i]) return i;
  return -1;
}

function findFreeDescriptor() {
  for (let i = 1; i < MAX_DESCRIPTORS; i++) if (descriptors[i].size === -1) return i;
  return -1;
}

function findFreeOftSlot() {
  for (let i = 1; i < OFT_SIZE; i++) if (oft[i].pos === -1) return i;
  return -1;
}

function findFile(name) {
  const count = Math.floor(oft[0].size / ENTRY_SIZE);
  for (let i = 0; i < count; i++) {
    const base = i * ENTRY_SIZE;
    let n = '';
    for (let j = 0; j < NAME_LEN; j++) {
      const c = oft[0].buffer[base + j];
      if (c) n += String.fromCharCode(c);
    }
    if (n === name) return oft[0].buffer[base + NAME_LEN];
  }
  return -1;
}

function getFileName(descIdx) {
  const count = Math.floor(oft[0].size / ENTRY_SIZE);
  for (let i = 0; i < count; i++) {
    const base = i * ENTRY_SIZE;
    if (oft[0].buffer[base + NAME_LEN] === descIdx) {
      let n = '';
      for (let j = 0; j < NAME_LEN; j++) {
        const c = oft[0].buffer[base + j];
        if (c) n += String.fromCharCode(c);
      }
      return n;
    }
  }
  return '?';
}

function validName(name) {
  return name && name.length > 0 &&
         name.length <= MAX_FILENAME_LEN &&
         /^[a-zA-Z0-9]+$/.test(name);
}

// ── Public API ────────────────────────────────────────────────────────────────

function initFS() {
  disk        = Array.from({ length: BLOCK_COUNT }, () => new Array(BLOCK_SIZE).fill(0));
  bitmap      = new Array(BLOCK_COUNT).fill(false);
  descriptors = Array.from({ length: MAX_DESCRIPTORS }, () => ({ size: -1, blocks: [0, 0, 0] }));
  oft         = Array.from({ length: OFT_SIZE }, () => ({ buffer: newBuf(), pos: -1, size: 0, desc: -1 }));
  memory      = new Array(BLOCK_SIZE).fill(0);

  for (let i = 0; i < DATA_START; i++) bitmap[i] = true;
  descriptors[0] = { size: 0, blocks: [DIR_BLOCK, 0, 0] };
  oft[0] = { buffer: newBuf(), pos: 0, size: 0, desc: 0 };

  saveBitmap();
  saveDescriptors();
  return 'system initialized';
}

function create(name) {
  if (!validName(name))          return 'error';
  if (findFile(name) !== -1)     return 'error';

  const di = findFreeDescriptor();
  if (di === -1) return 'error';
  descriptors[di] = { size: 0, blocks: [0, 0, 0] };

  const pos = oft[0].size;
  if (pos + ENTRY_SIZE > BLOCK_SIZE) {
    descriptors[di].size = -1;
    return 'error';
  }

  for (let i = 0; i < NAME_LEN; i++) {
    oft[0].buffer[pos + i] = (i < name.length) ? name.charCodeAt(i) : 0;
  }
  oft[0].buffer[pos + NAME_LEN] = di;
  oft[0].size += ENTRY_SIZE;

  saveDescriptors();
  saveDirBlock();
  return `${name} created`;
}

function destroy(name) {
  const di = findFile(name);
  if (di === -1) return 'error';

  for (let i = 1; i < OFT_SIZE; i++) {
    if (oft[i].desc === di) return 'error';   // file is open
  }

  for (const b of descriptors[di].blocks) {
    if (b) bitmap[b] = false;
  }
  descriptors[di] = { size: -1, blocks: [0, 0, 0] };

  // Remove directory entry and compact
  const count = Math.floor(oft[0].size / ENTRY_SIZE);
  for (let i = 0; i < count; i++) {
    const base = i * ENTRY_SIZE;
    let n = '';
    for (let j = 0; j < NAME_LEN; j++) {
      const c = oft[0].buffer[base + j];
      if (c) n += String.fromCharCode(c);
    }
    if (n === name) {
      for (let j = base; j < oft[0].size - ENTRY_SIZE; j++) {
        oft[0].buffer[j] = oft[0].buffer[j + ENTRY_SIZE];
      }
      for (let j = oft[0].size - ENTRY_SIZE; j < oft[0].size; j++) {
        oft[0].buffer[j] = 0;
      }
      oft[0].size -= ENTRY_SIZE;
      break;
    }
  }

  saveBitmap();
  saveDescriptors();
  saveDirBlock();
  return `${name} destroyed`;
}

function openFile(name) {
  const di = findFile(name);
  if (di === -1) return 'error';

  // Already open?
  for (let i = 0; i < OFT_SIZE; i++) if (oft[i].desc === di) return 'error';

  const slot = findFreeOftSlot();
  if (slot === -1) return 'error';

  const firstBlock = descriptors[di].blocks[0];
  oft[slot] = {
    buffer: firstBlock ? disk[firstBlock].slice() : newBuf(),
    pos:    0,
    size:   descriptors[di].size,
    desc:   di
  };
  return `${name} opened ${slot}`;
}

function closeFile(idx) {
  if (idx < 1 || idx >= OFT_SIZE || oft[idx].pos === -1) return 'error';

  const e = oft[idx];
  const bi = Math.max(0, Math.floor(Math.max(e.pos - 1, 0) / BLOCK_SIZE));
  if (bi < MAX_FILE_BLOCKS) {
    const blk = descriptors[e.desc].blocks[bi];
    if (blk) disk[blk] = e.buffer.slice();
  }
  descriptors[e.desc].size = e.size;
  saveDescriptors();

  oft[idx] = { buffer: newBuf(), pos: -1, size: 0, desc: -1 };
  return `${idx} closed`;
}

function readFile(idx, mpos, count) {
  if (idx < 1 || idx >= OFT_SIZE || oft[idx].pos === -1) return 'error';
  if (mpos < 0 || count < 0 || mpos + count > BLOCK_SIZE) return 'error';

  const e = oft[idx];
  let n = 0;
  while (n < count && e.pos < e.size) {
    const bi  = Math.floor(e.pos / BLOCK_SIZE);
    const off = e.pos % BLOCK_SIZE;
    if (off === 0 && n > 0) {
      const blk = descriptors[e.desc].blocks[bi];
      if (blk) e.buffer = disk[blk].slice();
    }
    memory[mpos + n] = e.buffer[off];
    n++;
    e.pos++;
  }
  return `${n} bytes read from file ${idx}`;
}

function writeFile(idx, mpos, count) {
  if (idx < 1 || idx >= OFT_SIZE || oft[idx].pos === -1) return 'error';
  if (mpos < 0 || count < 0 || mpos + count > BLOCK_SIZE) return 'error';

  const e = oft[idx];
  let n = 0;
  while (n < count) {
    const bi  = Math.floor(e.pos / BLOCK_SIZE);
    if (bi >= MAX_FILE_BLOCKS) break;
    const off = e.pos % BLOCK_SIZE;

    if (off === 0 && n > 0) {
      const pb   = Math.floor((e.pos - 1) / BLOCK_SIZE);
      const pblk = descriptors[e.desc].blocks[pb];
      if (pblk) disk[pblk] = e.buffer.slice();
    }
    if (off === 0 && descriptors[e.desc].blocks[bi] === 0) {
      const nb = findFreeBlock();
      if (nb === -1) break;
      bitmap[nb] = true;
      descriptors[e.desc].blocks[bi] = nb;
      e.buffer = newBuf();
    }

    e.buffer[off] = memory[mpos + n];
    n++;
    e.pos++;
    if (e.pos > e.size) e.size = e.pos;
  }

  if (n > 0) {
    const lb   = Math.floor((e.pos - 1) / BLOCK_SIZE);
    const lblk = descriptors[e.desc].blocks[lb];
    if (lblk) disk[lblk] = e.buffer.slice();
  }
  descriptors[e.desc].size = e.size;
  saveBitmap();
  saveDescriptors();
  return `${n} bytes written to file ${idx}`;
}

function seek(idx, pos) {
  if (idx < 1 || idx >= OFT_SIZE || oft[idx].pos === -1) return 'error';
  const e = oft[idx];
  if (pos < 0 || pos > e.size) return 'error';

  const oldBlock = Math.floor(e.pos / BLOCK_SIZE);
  const newBlock = Math.floor(pos  / BLOCK_SIZE);
  if (oldBlock !== newBlock) {
    const oblk = descriptors[e.desc].blocks[oldBlock];
    if (oblk) disk[oblk] = e.buffer.slice();
    const nblk = (newBlock < MAX_FILE_BLOCKS) ? descriptors[e.desc].blocks[newBlock] : 0;
    e.buffer = nblk ? disk[nblk].slice() : newBuf();
  }
  e.pos = pos;
  return `position is ${pos}`;
}

function directory() {
  const count = Math.floor(oft[0].size / ENTRY_SIZE);
  if (!count) return '(empty)';
  return Array.from({ length: count }, (_, i) => {
    const base = i * ENTRY_SIZE;
    let n = '';
    for (let j = 0; j < NAME_LEN; j++) {
      const c = oft[0].buffer[base + j];
      if (c) n += String.fromCharCode(c);
    }
    const di = oft[0].buffer[base + NAME_LEN];
    return `${n} ${descriptors[di].size}`;
  }).join('  ');
}

function writeMemory(pos, data) {
  if (pos < 0 || pos + data.length > BLOCK_SIZE) return 'error';
  for (let i = 0; i < data.length; i++) memory[pos + i] = data.charCodeAt(i);
  return `${data.length} bytes written to M`;
}

function readMemory(pos, count) {
  if (pos < 0 || count < 0 || pos + count > BLOCK_SIZE) return 'error';
  let s = '';
  for (let i = 0; i < count; i++) {
    const b = memory[pos + i];
    if (b) s += String.fromCharCode(b);
  }
  return s || '(empty)';
}

// ── Command dispatcher ────────────────────────────────────────────────────────

function dispatch(line) {
  const parts = line.trim().split(/\s+/);
  const cmd   = parts[0];
  try {
    switch (cmd) {
      case 'in': return initFS();
      case 'cr': return parts.length === 2 ? create(parts[1])                                    : 'error';
      case 'de': return parts.length === 2 ? destroy(parts[1])                                   : 'error';
      case 'op': return parts.length === 2 ? openFile(parts[1])                                  : 'error';
      case 'cl': return parts.length === 2 ? closeFile(+parts[1])                                : 'error';
      case 'rd': return parts.length === 4 ? readFile(+parts[1], +parts[2], +parts[3])           : 'error';
      case 'wr': return parts.length === 4 ? writeFile(+parts[1], +parts[2], +parts[3])          : 'error';
      case 'sk': return parts.length === 3 ? seek(+parts[1], +parts[2])                          : 'error';
      case 'dr': return parts.length === 1 ? directory()                                          : 'error';
      case 'wm': return parts.length >= 3  ? writeMemory(+parts[1], parts.slice(2).join(' '))    : 'error';
      case 'rm': return parts.length === 3 ? readMemory(+parts[1], +parts[2])                    : 'error';
      default:   return 'error';
    }
  } catch {
    return 'error';
  }
}

// ── Expose state for the UI layer ─────────────────────────────────────────────

const FS = {
  // State (read-only references — UI reads these directly)
  get disk()        { return disk; },
  get bitmap()      { return bitmap; },
  get descriptors() { return descriptors; },
  get oft()         { return oft; },
  get memory()      { return memory; },

  // Methods
  dispatch,
  initFS,
  getFileName,

  // Constants the UI needs
  BLOCK_COUNT,
  BLOCK_SIZE,
  DATA_START,
  OFT_SIZE,
  NAME_LEN,
  ENTRY_SIZE,
  MAX_FILE_BLOCKS,
};
