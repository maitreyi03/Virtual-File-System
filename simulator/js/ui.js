/**
 * ui.js — Shell interface and DOM rendering
 *
 * Depends on FS (from fs.js) being available in the global scope.
 * Reads FS.bitmap, FS.oft, FS.descriptors, FS.memory for rendering.
 * Calls FS.dispatch() for all commands.
 */

// ── Demo sequence ─────────────────────────────────────────────────────────────

const DEMO_SEQUENCE = [
  ['in',             'reinitialize the file system'],
  ['cr myfile',      'create a file called myfile'],
  ['op myfile',      'open it — OFT slot 1 is now in use'],
  ['wm 0 helloworld','write "helloworld" into memory at position 0'],
  ['wr 1 0 10',      'write 10 bytes from memory into the file — watch a disk block turn orange'],
  ['dr',             'list directory — shows myfile with size 10'],
  ['sk 1 0',         'seek back to position 0'],
  ['rd 1 20 10',     'read 10 bytes from file into memory at position 20'],
  ['rm 20 10',       'read from memory at position 20 — should print helloworld'],
  ['cl 1',           'close the file — OFT slot 1 is freed'],
  ['de myfile',      'destroy the file — disk block is freed'],
];

let seqIdx   = 0;
let seqTimer = null;

// ── Logging ───────────────────────────────────────────────────────────────────

function classifyResult(result) {
  if (result === 'error') return 'log-result-err';
  const ok = ['initialized', 'created', 'opened', 'written',
               'read', 'position', 'closed', 'destroyed'];
  if (ok.some(w => result.includes(w))) return 'log-result-ok';
  return 'log-result-info';
}

function addLog(cmd, result) {
  const log   = document.getElementById('log');
  const welcome = log.querySelector('.log-welcome');
  if (welcome) welcome.remove();

  const div = document.createElement('div');
  div.className = 'log-entry';
  div.innerHTML =
    `<span class="log-prompt">$</span>` +
    `<span class="log-cmd">${cmd}</span>` +
    `<span class="${classifyResult(result)}">${result}</span>`;
  log.appendChild(div);
  log.scrollTop = log.scrollHeight;
}

// ── Rendering ─────────────────────────────────────────────────────────────────

function renderAll(changedBlocks = []) {
  renderBitmap(changedBlocks);
  renderOFT();
  renderDirectory();
  renderMemory();
  renderStats();
}

function renderBitmap(changed) {
  const { BLOCK_COUNT, DATA_START, bitmap } = FS;
  const grid = document.getElementById('bitmap');
  grid.innerHTML = '';

  for (let i = 0; i < BLOCK_COUNT; i++) {
    const el = document.createElement('div');
    const base = i < DATA_START ? 'sys' : bitmap[i] ? 'used' : 'free';
    el.className  = 'bblock ' + base + (changed.includes(i) ? ' new-used' : '');
    el.textContent = i;
    el.title = `Block ${i}` + (i < DATA_START ? ' (system)' : bitmap[i] ? ' (used)' : ' (free)');
    grid.appendChild(el);
  }
}

function renderOFT() {
  const { OFT_SIZE, oft, getFileName } = FS;
  const tbody = document.getElementById('oft-body');
  tbody.innerHTML = '';

  for (let i = 0; i < OFT_SIZE; i++) {
    const e  = oft[i];
    const tr = document.createElement('tr');
    let badge, nameCell, pos, size, desc;

    if (i === 0) {
      badge    = `<span class="slot-badge badge-dir">DIR</span>`;
      nameCell = `<span class="slot-dir">[directory]</span>`;
      pos = e.pos; size = e.size; desc = 0;
    } else if (e.pos !== -1) {
      badge    = `<span class="slot-badge badge-open">OPEN</span>`;
      nameCell = `<span class="slot-open">${getFileName(e.desc)}</span>`;
      pos = e.pos; size = e.size; desc = e.desc;
    } else {
      badge    = `<span class="slot-badge badge-free">FREE</span>`;
      nameCell = `<span class="slot-free">—</span>`;
      pos = '—'; size = '—'; desc = '—';
    }

    tr.innerHTML =
      `<td>${i}</td><td>${badge}</td><td>${nameCell}</td>` +
      `<td>${pos}</td><td>${size}</td><td>${desc}</td>`;
    tbody.appendChild(tr);
  }
}

function renderDirectory() {
  const { oft, descriptors, ENTRY_SIZE, NAME_LEN } = FS;
  const tbody = document.getElementById('dir-body');
  tbody.innerHTML = '';

  const count = Math.floor(oft[0].size / ENTRY_SIZE);
  if (!count) {
    tbody.innerHTML =
      `<tr><td colspan="4" style="padding:10px 8px">` +
      `<span class="dir-empty">no files</span></td></tr>`;
    return;
  }

  for (let i = 0; i < count; i++) {
    const base = i * ENTRY_SIZE;
    let name = '';
    for (let j = 0; j < NAME_LEN; j++) {
      const c = oft[0].buffer[base + j];
      if (c) name += String.fromCharCode(c);
    }
    const di     = oft[0].buffer[base + NAME_LEN];
    const d      = descriptors[di];
    const blocks = d.blocks.filter(b => b > 0).join(', ') || '—';
    const tr = document.createElement('tr');
    tr.innerHTML =
      `<td class="filename">${name}</td>` +
      `<td class="desc-idx">${di}</td>` +
      `<td class="file-size">${d.size}</td>` +
      `<td class="desc-idx">${blocks}</td>`;
    tbody.appendChild(tr);
  }
}

function renderMemory() {
  const { memory, BLOCK_SIZE } = FS;
  const grid = document.getElementById('mem-grid');
  grid.innerHTML = '';

  for (let i = 0; i < 64; i++) {
    const el = document.createElement('div');
    el.className  = 'mbyte' + (memory[i] ? ' active' : '');
    el.textContent = memory[i] ? String.fromCharCode(memory[i]) : '·';
    el.title = `M[${i}] = ${memory[i]}`;
    grid.appendChild(el);
  }

  let s = '';
  for (let i = 0; i < BLOCK_SIZE; i++) {
    if (memory[i]) s += String.fromCharCode(memory[i]);
  }
  document.getElementById('mem-str').textContent = s ? `"${s}"` : '(empty)';
}

function renderStats() {
  const { BLOCK_COUNT, DATA_START, OFT_SIZE, bitmap, oft, ENTRY_SIZE } = FS;
  let free = 0;
  for (let i = DATA_START; i < BLOCK_COUNT; i++) if (!bitmap[i]) free++;
  const files = Math.floor(oft[0].size / ENTRY_SIZE);
  let open = 0;
  for (let i = 1; i < OFT_SIZE; i++) if (oft[i].pos !== -1) open++;

  document.getElementById('s-free').textContent  = free;
  document.getElementById('s-files').textContent = files;
  document.getElementById('s-open').textContent  = open;
}

// ── Shell actions ─────────────────────────────────────────────────────────────

function run() {
  const inp = document.getElementById('cmd');
  const val = inp.value.trim();
  if (!val) return;

  const before  = FS.bitmap.slice();
  const result  = FS.dispatch(val);
  const changed = [];
  for (let i = FS.DATA_START; i < FS.BLOCK_COUNT; i++) {
    if (!before[i] && FS.bitmap[i]) changed.push(i);
  }

  addLog(val, result);
  renderAll(changed);
  inp.value = '';
  inp.focus();
}

function resetFS() {
  if (seqTimer) { clearTimeout(seqTimer); seqTimer = null; }
  document.getElementById('log').innerHTML =
    '<div class="log-welcome">System reset. Ready.</div>';
  FS.initFS();
  renderAll([]);
}

function hint(cmd) {
  const inp = document.getElementById('cmd');
  inp.value = cmd;
  inp.focus();
}

function runSequence() {
  if (seqTimer) clearTimeout(seqTimer);
  resetFS();
  seqIdx = 0;

  function step() {
    if (seqIdx >= DEMO_SEQUENCE.length) return;
    const [cmd, note] = DEMO_SEQUENCE[seqIdx++];
    const inp    = document.getElementById('cmd');
    const before = FS.bitmap.slice();
    const result = FS.dispatch(cmd);
    const changed = [];
    for (let i = FS.DATA_START; i < FS.BLOCK_COUNT; i++) {
      if (!before[i] && FS.bitmap[i]) changed.push(i);
    }
    addLog(`${cmd} \u2014 ${note}`, result);
    renderAll(changed);
    inp.value = '';
    seqTimer = setTimeout(step, 1100);
  }
  step();
}

// ── Boot ──────────────────────────────────────────────────────────────────────

FS.initFS();
renderAll([]);
document.getElementById('cmd').focus();
