package cz.talich.arp

import java.util.Base64

object HtmlReportGenerator {

    fun generate(rootNode: Node, screenshotBytes: ByteArray?): String {
        val screenshotBase64 = screenshotBytes?.let { Base64.getEncoder().encodeToString(it) }

        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Accessibility Report</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', Arial, sans-serif; background: #1e1e1e; color: #d4d4d4; height: 100vh; display: flex; flex-direction: column; }
  h1 { flex-shrink: 0; font-size: 1.1rem; padding: 10px 16px; background: #2d2d2d; border-bottom: 1px solid #3c3c3c; color: #cccccc; display: flex; align-items: center; gap: 8px; }
  h1 svg { width: 24px; height: 24px; flex-shrink: 0; }
  #main { display: flex; flex: 1; overflow: hidden; }
  #left { display: flex; flex-direction: column; width: 50%; min-width: 150px; overflow: hidden; }
  #tree-container { flex: 1; overflow: auto; padding: 8px; }
  #props-container { height: 220px; flex-shrink: 0; border-top: 1px solid #3c3c3c; overflow: auto; }
  #divider { width: 5px; background: #3c3c3c; cursor: col-resize; flex-shrink: 0; transition: background 0.15s; }
  #divider:hover, #divider.dragging { background: #007acc; }
  #right { flex: 1; overflow: auto; display: flex; align-items: flex-start; justify-content: center; background: #252526; position: relative; }
  #screenshot-wrapper { position: relative; display: inline-block; }
  #screenshot { display: block; max-width: 100%; max-height: 100vh; cursor: default; }
  .hover-box { position: absolute; border: 2px solid rgba(255,0,0,0.85); background: rgba(255,0,0,0.31); pointer-events: none; display: none; }
  .selected-box { position: absolute; border: 2px solid rgba(0,120,255,0.9); background: rgba(0,120,255,0.2); pointer-events: none; display: none; }

  /* Tree */
  ul.tree { list-style: none; padding-left: 0; }
  ul.tree ul { list-style: none; padding-left: 18px; }
  .tree-node { cursor: pointer; padding: 2px 4px; border-radius: 3px; white-space: nowrap; user-select: none; display: flex; align-items: center; gap: 4px; }
  .tree-node:hover { background: #2a2d2e; }
  .tree-node.selected { background: #094771; color: #ffffff; }
  .tree-node.hovered:not(.selected) { color: #ff4444; }
  .toggle { display: inline-block; width: 14px; text-align: center; font-size: 0.75rem; color: #888; flex-shrink: 0; cursor: pointer; }
  .node-label { font-size: 0.82rem; }

  /* Properties table */
  table { width: 100%; border-collapse: collapse; font-size: 0.8rem; }
  th { text-align: left; padding: 4px 8px; background: #252526; color: #888; font-weight: normal; border-bottom: 1px solid #3c3c3c; position: sticky; top: 0; }
  td { padding: 3px 8px; border-bottom: 1px solid #2a2a2a; vertical-align: top; word-break: break-all; }
  tr:hover td { background: #2a2d2e; }
  td:first-child { color: #9cdcfe; width: 130px; white-space: nowrap; }

  .header-actions { margin-left: auto; display: flex; gap: 6px; }
  .tree-btn { background: #3c3c3c; color: #cccccc; border: 1px solid #555; border-radius: 4px; padding: 3px 10px; font-size: 0.78rem; cursor: pointer; }
  .tree-btn:hover { background: #505050; }
</style>
</head>
<body>
<div id="main">
<div id="left">
<h1><a href="https://plugins.jetbrains.com/plugin/31810" target="_blank" style="color:inherit;text-decoration:none;display:flex;align-items:center;gap:8px;"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 602 678"><g><path d="M 129.3 15.4 C 129.7 16 133.5 23 137.7 31 C 141.9 39 145.4 45.7 145.6 45.9 C 145.8 46.1 149.2 44.9 153.2 43.2 C 165.1 38.3 174.9 36.5 189 36.6 C 202.8 36.7 210.6 38.2 222.7 43 C 226.8 44.7 230.3 46 230.5 46 C 230.7 46 234.8 38.8 239.5 30.1 C 248.4 13.9 250.2 12 254.1 14.1 C 258 16.2 257.4 18.6 248.6 34.5 C 243.9 42.9 240 50.2 240 50.7 C 240 51.1 242.8 53.3 246.1 55.5 C 253.4 60.3 264.7 72 269.7 79.7 C 279.1 94.5 283.8 109.2 284.7 127.2 L 285.3 139 L 188 139 L 90.7 139 L 91.3 127.7 C 92.7 102.5 102.1 81.8 120.1 64.4 C 124.2 60.5 129.4 56 131.8 54.5 C 135.2 52.3 136 51.2 135.6 49.6 C 135.3 48.4 131.7 41.2 127.6 33.5 C 123.4 25.8 120 18.9 120 18.2 C 120 17.6 120.9 16.1 122.1 14.9 C 124.2 12.8 127.7 13 129.3 15.4 Z M 139.3 83 C 137.1 84.3 135.7 86.2 134.9 89.1 C 133.8 92.8 133.8 93.7 135.6 96.9 C 136.7 99 139 101.2 141.3 102.4 C 144.7 104 145.5 104.1 148.7 102.9 C 153.3 101.1 156.4 96.9 156.4 92.6 C 156.4 83.2 147.3 78.1 139.3 83 Z M 223.4 84.4 C 220.5 87.3 220 88.5 220 92.3 C 220 102.8 230.9 107.1 238.7 99.8 C 243.6 95.3 242.4 86.5 236.6 82.9 C 231.7 79.9 227.4 80.4 223.4 84.4 Z" style="fill: rgb(92, 192, 42);" fill-rule="evenodd" clip-rule="evenodd"/><path d="M 138.7 155.9 C 144.7 158.7 150.2 164.9 153.5 172.7 C 159.1 185.9 175.9 223.9 190.5 256 C 206 290.3 207.3 294.4 205.1 302.7 C 203.4 309 198.3 315.1 192 318.3 C 187.2 320.8 185.7 321.1 180 320.7 C 176.4 320.5 172 319.5 170.2 318.6 C 165.2 316 159.6 309.9 156.7 303.9 C 128.3 243.6 104 189.4 102.8 183.7 C 98.8 164.7 120.7 147.8 138.7 155.9 Z" style="fill: rgb(92, 192, 42);"/><path d="M 255.5 157 C 262 160.2 266.7 164.7 269.7 170.8 L 272.5 176.5 L 272.5 219 L 272.5 261.5 L 295 262 C 317.5 262.5 317.5 262.5 323.3 265.5 C 333.6 270.6 340.1 279 342.9 290.5 C 343.6 293.2 350 314.9 357.1 338.8 C 365.6 367.6 370 383.8 370 386.8 C 370 392.9 366.3 400.8 361.6 404.7 C 350.5 413.8 332.9 410.5 326.5 398 C 325 395.1 317.6 374.7 309 350 C 302.6 331.7 300.5 328 294.3 325.4 C 291.8 324.3 284.6 324 262.7 324 C 247.1 324 228.2 323.7 220.7 323.3 L 207.1 322.7 L 211 318.3 C 217.4 311 220.2 299.2 218 288.7 C 217.4 285.6 212.1 272.3 206.4 259.2 C 200.6 246.2 189.3 220.7 181.4 202.6 C 164.4 164 163 161.3 159.6 158.1 C 158.2 156.7 157 155.4 157 155.1 C 157 154.8 178.3 154.6 204.2 154.8 C 248.4 155.1 251.8 155.2 255.5 157 Z" style="fill: rgb(92, 192, 42);"/><path d="M 94.3 211.3 C 95.8 214.5 98.6 220.6 100.4 224.8 L 103.8 232.6 L 95.9 238.5 C 71.6 256.4 53.9 284.1 48.4 312.8 C 46.5 322.7 46.6 343.1 48.6 352.8 C 53.2 375.5 63.3 394.3 79.5 410.6 C 96.2 427.4 114.6 437.1 137.9 441.5 C 182.7 449.8 231.8 424.6 252.8 382.5 C 258.4 371.1 263 355 264.2 342.5 L 264.5 339.5 L 275.9 339.7 C 282.2 339.9 287.9 340.5 288.5 341 C 289.2 341.6 290.6 345 291.6 348.6 C 293.7 356.4 293 361 287.6 376.9 C 280.1 398.6 270.3 415 255.5 430.7 C 236.8 450.7 213.8 464.3 187 471.2 C 172.1 475.1 148.9 476.2 134.3 473.6 C 106.4 468.8 81 455.8 60.6 436.1 C 43.1 419 30.9 400 23.6 377.9 C 17.5 359.7 16.5 352.8 16.5 331 C 16.5 313.1 16.8 310.6 19.3 300.6 C 27.1 269.7 43.5 242.4 66.8 221.7 C 75.1 214.3 88.8 204.8 90.5 205.3 C 91 205.4 92.8 208.2 94.3 211.3 Z" style="fill: rgb(92, 192, 42);"/><path d="M 519.8 396.4 C 521.3 397.2 524.5 400 527 402.7 C 529.4 405.3 542.5 419 556.1 433 C 569.6 447 583.2 461.3 586.3 464.7 L 592 471 L 592 556.3 C 592 651.2 592.2 647.9 585.3 655.9 C 583.3 658.3 579.3 661.3 576.6 662.6 L 571.5 665 L 488.4 665 L 405.4 665 L 400.1 662.3 C 394.6 659.6 389.6 653.9 387.3 647.7 C 386.5 645.3 386.1 615.2 386.1 530.9 C 386 405 385.6 412.3 392.6 404.2 C 394.5 402 398.3 399 401.2 397.6 L 406.4 395 L 461.7 395 C 507.2 395 517.6 395.2 519.8 396.4 Z M 440 565 L 440 630 L 453 630 L 466 630 L 466 606.5 L 466 583 L 474.9 583 L 483.8 583 L 487 588.7 C 488.8 591.9 494.7 602.5 500.3 612.2 L 510.3 630 L 524.6 630 C 532.5 630 539 629.8 539 629.6 C 539 629.3 532.5 618 524.5 604.3 C 516.5 590.7 510 579.3 510 579 C 510.1 578.7 512.3 577.5 515 576.2 C 521.7 573.1 528 566.6 531.6 559.5 C 534.3 553.9 534.5 552.7 534.5 541.5 C 534.5 530.2 534.3 529.1 531.4 523 C 527.9 515.7 520.8 508 514.6 505.1 C 505.3 500.6 500.2 500 469.3 500 L 440 500 L 440 565 Z M 496.5 524.1 C 507.4 527.1 512.2 542.9 505.2 552.8 C 501 558.6 496.1 560 480.2 560 L 466 560 L 466 541.5 L 466 523 L 479.3 523 C 486.5 523 494.3 523.5 496.5 524.1 Z" style="fill: rgb(92, 192, 42);" fill-rule="evenodd" clip-rule="evenodd"/></g></svg>Android Accessibility Report</a><div class="header-actions"><button class="tree-btn" onclick="expandAll()">Expand All</button><button class="tree-btn" onclick="collapseAll()">Collapse All</button></div></h1>
<div id="tree-container">
  <ul class="tree" id="root-tree"></ul>
</div>
<div id="props-container">
  <table id="props-table">
    <thead><tr><th>Property</th><th>Value</th></tr></thead>
    <tbody id="props-body"></tbody>
  </table>
</div>
</div>
<div id="divider"></div>
<div id="right">
  <div id="screenshot-wrapper">
    ${if (screenshotBase64 != null) """<img id="screenshot" src="data:image/png;base64,$screenshotBase64" alt="Screenshot">""" else """<div style="color:#888;padding:32px;">No screenshot available</div>"""}
    <div id="hover-box" class="hover-box"></div>
    <div id="selected-box" class="selected-box"></div>
  </div>
</div>
</div>

<script>
const nodes = ${buildNodeJson(rootNode)};

let selectedEl = null;
let selectedNode = null;
let hoveredEl = null;

function escHtml(s) {
  return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// Build tree DOM - depth tracked for expand-to-level-2 default
function buildTree(nodeList, parentUl, depth) {
  nodeList.forEach(n => {
    const li = document.createElement('li');
    const div = document.createElement('div');
    div.className = 'tree-node';
    div._node = n;

    const toggle = document.createElement('span');
    toggle.className = 'toggle';

    const label = document.createElement('span');
    label.className = 'node-label';
    // Match tool window: show id (Node.toString()), fall back to className if id is blank
    label.textContent = n.id !== '' ? n.id : n.className;

    div.appendChild(toggle);
    div.appendChild(label);

    li.appendChild(div);

    let childUl = null;
    if (n.children && n.children.length) {
      toggle.textContent = '▶';
      childUl = document.createElement('ul');
      // Expand first 2 levels by default (depth 0 and 1), matching tool window expandTreeToLevel(..., 2)
      const expanded = depth < 2;
      childUl.style.display = expanded ? '' : 'none';
      if (expanded) toggle.textContent = '▼';
      buildTree(n.children, childUl, depth + 1);
      li.appendChild(childUl);

      toggle.addEventListener('click', e => {
        e.stopPropagation();
        const open = childUl.style.display !== 'none';
        childUl.style.display = open ? 'none' : '';
        toggle.textContent = open ? '▶' : '▼';
      });
    } else {
      toggle.textContent = ' ';
    }

    nodeToDivMap.set(n, div);
    let dblClickPending = false;
    div.addEventListener('click', () => { if (!dblClickPending) selectNode(n, div); });
    div.addEventListener('dblclick', e => {
      e.stopPropagation();
      dblClickPending = true;
      if (childUl) {
        const open = childUl.style.display !== 'none';
        childUl.style.display = open ? 'none' : '';
        toggle.textContent = open ? '▶' : '▼';
      }
      if (selectedEl !== div) selectNode(n, div);
      setTimeout(() => { dblClickPending = false; }, 0);
    });

    div.addEventListener('mouseenter', () => {
      if (hoveredEl && hoveredEl !== div) hoveredEl.classList.remove('hovered');
      hoveredEl = div;
      div.classList.add('hovered');
      showHoverBox(n);
    });
    div.addEventListener('mouseleave', () => {
      div.classList.remove('hovered');
      if (hoveredEl === div) hoveredEl = null;
      hideHoverBox();
    });

    parentUl.appendChild(li);
  });
}

function selectNode(n, divEl) {
  if (selectedEl === divEl) {
    divEl.classList.remove('selected');
    selectedEl = null;
    selectedNode = null;
    clearProps();
    hideSelectedBox();
    return;
  }
  if (selectedEl) selectedEl.classList.remove('selected');
  divEl.classList.add('selected');
  selectedEl = divEl;
  selectedNode = n;
  showProps(n);
  showSelectedBox(n);
}

function showProps(n) {
  const tbody = document.getElementById('props-body');
  tbody.innerHTML = '';
  const props = [
    ['ID', n.id],
    ['Class', n.className],
    ['Text', n.text],
    ['Description', n.description],
    ['Bounds', n.bounds],
  ];
  props.forEach(([k, v]) => {
    const tr = document.createElement('tr');
    tr.innerHTML = '<td>' + escHtml(k) + '</td><td>' + escHtml(v ?? 'null') + '</td>';
    tbody.appendChild(tr);
  });
}

function getImgScale() {
  const img = document.getElementById('screenshot');
  if (!img || !img.naturalWidth) return null;
  return { sx: img.clientWidth / img.naturalWidth, sy: img.clientHeight / img.naturalHeight };
}

function showHoverBox(n) {
  const box = document.getElementById('hover-box');
  const scale = getImgScale();
  if (!box || !scale || !n.boundsRaw) return;
  const b = n.boundsRaw;
  box.style.left = (b.left * scale.sx) + 'px';
  box.style.top = (b.top * scale.sy) + 'px';
  box.style.width = ((b.right - b.left) * scale.sx) + 'px';
  box.style.height = ((b.bottom - b.top) * scale.sy) + 'px';
  box.style.display = 'block';
}

function hideHoverBox() {
  const box = document.getElementById('hover-box');
  if (box) box.style.display = 'none';
}

function showSelectedBox(n) {
  const box = document.getElementById('selected-box');
  const scale = getImgScale();
  if (!box || !scale || !n.boundsRaw) { if (box) box.style.display = 'none'; return; }
  const b = n.boundsRaw;
  box.style.left = (b.left * scale.sx) + 'px';
  box.style.top = (b.top * scale.sy) + 'px';
  box.style.width = ((b.right - b.left) * scale.sx) + 'px';
  box.style.height = ((b.bottom - b.top) * scale.sy) + 'px';
  box.style.display = 'block';
}

function hideSelectedBox() {
  const box = document.getElementById('selected-box');
  if (box) box.style.display = 'none';
}

function deselectNode() {
  if (selectedEl) {
    selectedEl.classList.remove('selected');
    selectedEl = null;
    selectedNode = null;
    clearProps();
    hideSelectedBox();
  }
}

function clearProps() {
  document.getElementById('props-body').innerHTML = '';
}

// Map from node object to its tree div element, for reverse lookup
const nodeToDivMap = new Map();

const rootUl = document.getElementById('root-tree');
buildTree(nodes, rootUl, 0);

// Find the deepest node whose bounds contain (x, y) in image coordinates
function findDeepestNode(nodeList, x, y) {
  for (let i = nodeList.length - 1; i >= 0; i--) {
    const n = nodeList[i];
    const b = n.boundsRaw;
    if (!b) continue;
    if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) {
      if (n.children && n.children.length) {
        const found = findDeepestNode(n.children, x, y);
        if (found) return found;
      }
      // Skip root-like nodes that start at 0,0 (matches ToolWindow logic)
      if (b.left === 0 && b.top === 0) continue;
      return n;
    }
  }
  return null;
}

// Scroll a tree div into view and ensure its ancestors are expanded
function revealNodeDiv(div) {
  // Walk up the DOM to expand any collapsed ancestor <ul>
  let el = div.parentElement; // <li>
  while (el) {
    if (el.tagName === 'UL' && el.style.display === 'none') {
      el.style.display = '';
      // Update the toggle of the parent <li>'s div
      const parentLi = el.parentElement;
      if (parentLi) {
        const parentDiv = parentLi.querySelector(':scope > .tree-node');
        if (parentDiv) {
          const toggle = parentDiv.querySelector('.toggle');
          if (toggle) toggle.textContent = '▼';
        }
      }
    }
    el = el.parentElement;
  }
  div.scrollIntoView({ block: 'nearest' });
}

const img = document.getElementById('screenshot');
if (img) {
  window.addEventListener('resize', () => {
    if (selectedNode) showSelectedBox(selectedNode);
  });

  // Convert mouse event position to image coordinates
  function mouseToImgCoords(e) {
    const rect = img.getBoundingClientRect();
    const x = (e.clientX - rect.left) * (img.naturalWidth / img.clientWidth);
    const y = (e.clientY - rect.top) * (img.naturalHeight / img.clientHeight);
    return { x, y };
  }

  let imgHoveredDiv = null;

  img.addEventListener('mousemove', e => {
    const { x, y } = mouseToImgCoords(e);
    const found = findDeepestNode(nodes, x, y);
    if (found) {
      img.style.cursor = 'pointer';
      showHoverBox(found);
      const div = nodeToDivMap.get(found);
      if (div && div !== imgHoveredDiv) {
        if (imgHoveredDiv && imgHoveredDiv !== selectedEl) imgHoveredDiv.classList.remove('hovered');
        imgHoveredDiv = div;
        if (div !== selectedEl) div.classList.add('hovered');
      }
    } else {
      img.style.cursor = 'default';
      hideHoverBox();
      if (imgHoveredDiv && imgHoveredDiv !== selectedEl) imgHoveredDiv.classList.remove('hovered');
      imgHoveredDiv = null;
    }
  });

  img.addEventListener('mouseleave', () => {
    hideHoverBox();
    if (imgHoveredDiv && imgHoveredDiv !== selectedEl) imgHoveredDiv.classList.remove('hovered');
    imgHoveredDiv = null;
  });

  img.addEventListener('click', e => {
    e.stopPropagation();
    const { x, y } = mouseToImgCoords(e);
    const found = findDeepestNode(nodes, x, y);
    if (found) {
      const div = nodeToDivMap.get(found);
      if (div) {
        // Remove hovered highlight from any previously hovered div before selecting
        if (imgHoveredDiv) {
          imgHoveredDiv.classList.remove('hovered');
          imgHoveredDiv = null;
        }
        revealNodeDiv(div);
        selectNode(found, div);
      }
    } else {
      deselectNode();
    }
  });

  document.getElementById('right').addEventListener('click', () => {
    deselectNode();
  });

  document.getElementById('tree-container').addEventListener('click', e => {
    if (!e.target.closest('.tree-node')) {
      deselectNode();
    }
  });
}

function expandAll() {
  document.querySelectorAll('#root-tree ul').forEach(ul => { ul.style.display = ''; });
  document.querySelectorAll('#root-tree .toggle').forEach(t => { if (t.textContent === '▶') t.textContent = '▼'; });
}

function collapseAll() {
  document.querySelectorAll('#root-tree > li > ul').forEach(ul => { ul.style.display = 'none'; });
  document.querySelectorAll('#root-tree ul ul').forEach(ul => { ul.style.display = 'none'; });
  document.querySelectorAll('#root-tree .toggle').forEach(t => { if (t.textContent === '▼') t.textContent = '▶'; });
}

// Resizable divider
(function() {
  const divider = document.getElementById('divider');
  const left = document.getElementById('left');
  const main = document.getElementById('main');
  let dragging = false, startX = 0, startW = 0;
  divider.addEventListener('mousedown', e => {
    dragging = true;
    startX = e.clientX;
    startW = left.getBoundingClientRect().width;
    divider.classList.add('dragging');
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  });
  document.addEventListener('mousemove', e => {
    if (!dragging) return;
    const mainW = main.getBoundingClientRect().width;
    const newW = Math.min(Math.max(startW + (e.clientX - startX), 150), mainW - 150);
    left.style.width = newW + 'px';
    left.style.flex = 'none';
  });
  document.addEventListener('mouseup', () => {
    if (!dragging) return;
    dragging = false;
    divider.classList.remove('dragging');
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  });
})();
</script>
</body>
</html>"""
    }

    private fun buildNodeJson(node: Node): String {
        val sb = StringBuilder()
        appendNodeJson(node, sb)
        return "[$sb]"
    }

    private fun appendNodeJson(node: Node, sb: StringBuilder) {
        sb.append("{")
        sb.append(""""id":${jsonStr(node.id)},""")
        sb.append(""""className":${jsonStr(node.className)},""")
        sb.append(""""text":${jsonStr(node.text ?: "")},""")
        sb.append(""""description":${jsonStr(node.description ?: "")},""")
        sb.append(""""bounds":"[${node.bounds.left},${node.bounds.top}][${node.bounds.right},${node.bounds.bottom}]",""")
        sb.append(""""boundsRaw":{"left":${node.bounds.left},"top":${node.bounds.top},"right":${node.bounds.right},"bottom":${node.bounds.bottom}},""")
        sb.append(""""children":[""")
        node.children.forEachIndexed { i, child ->
            if (i > 0) sb.append(",")
            appendNodeJson(child, sb)
        }
        sb.append("]}")
    }

    private fun jsonStr(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
