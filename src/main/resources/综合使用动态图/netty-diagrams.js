/* Netty 八个知识单元：共用绘图层；步骤播放复用 player.js。 */
(() => {
  'use strict';
  const cfg = window.NETTY_PAGE;
  const { svg, arrow, packet } = Player;
  const root = document.getElementById('svg');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let previous = null, geometry;
  const text = (g, x, y, value, cls = '') => svg('text', { x, y, class: cls }, g, value);
  const box = (g, x, y, w, h, cls = '') => svg('rect', { x, y, width: w, height: h, rx: 8, class: 'box ' + cls }, g);
  function layout() {
    const width = Math.max(260, Math.floor(root.getBoundingClientRect().width || 700));
    const cols = width >= 620 ? 3 : width >= 380 ? 2 : 1;
    const gap = 24, pad = 12, top = 28;
    const cell = (width - 2 * pad - (cols - 1) * gap) / cols;
    const points = cfg.nodes.map((_, i) => ({
      x: pad + (i % cols) * (cell + gap), y: top + Math.floor(i / cols) * 112,
      w: cell, h: 80,
    }));
    const extraY = top + Math.ceil(cfg.nodes.length / cols) * 112 + 12;
    root.setAttribute('viewBox', `0 0 ${width} ${extraY + (cfg.kind ? 150 : 12)}`);
    return { width, cols, points, extraY };
  }
  function connection(g, a, b, cls) {
    const from = geometry.points[a], to = geometry.points[b];
    const right = to.x > from.x, sameRow = to.y === from.y;
    if (sameRow) {
      arrow(g, right ? from.x + from.w : from.x, from.y + 40,
        right ? to.x : to.x + to.w, to.y + 40, cls);
    } else {
      // 跨行边沿节点边缘走折线，避免穿过中间节点文字。
      const x1 = from.x + from.w / 2, x2 = to.x + to.w / 2;
      const down = to.y > from.y;
      const y1 = from.y + (down ? from.h : 0), y2 = to.y + (down ? 0 : to.h);
      const mid = down ? y1 + 16 : y1 - 16;
      svg('path', { d: `M${x1},${y1} V${mid} H${x2} V${y2}`, class: 'wire ' + cls }, g);
      arrow(g, x2, down ? y2 - 10 : y2 + 10, x2, y2, cls);
    }
  }
  function extras(g, st) {
    const { width: w, extraY: y } = geometry;
    if (cfg.kind === 'memory') {
      text(g, 12, y, '共享存储：refCnt = ' + (st.count ?? 1), 'title');
      const n = Math.max(0, Math.min(st.count ?? 1, 6));
      for (let k = 0; k < 6; k++) box(g, 12 + k * 35, y + 16, 26, 30, k < n ? (st.count > 0 ? 'ok' : 'bad') : 'soft');
      text(g, 12, y + 74, st.count === 0 ? '归还池 / 释放存储；所有视图失效' : 'slice / duplicate 共享存储', '');
      text(g, 12, y + 100, 'retain 不会使 ByteBuf 线程安全', 'muted');
    } else if (cfg.kind === 'codec') {
      text(g, 12, y, 'Decoder 累积区（十六进制字节）', 'title');
      const bytes = st.bytes || [];
      const cols = Math.max(1, Math.floor((w - 24) / 34));
      bytes.forEach((b, k) => {
        const x = 12 + (k % cols) * 34, yy = y + 16 + Math.floor(k / cols) * 35;
        box(g, x, yy, 30, 29, k < 4 ? 'accent' : 'ok');
        text(g, x + 5, yy + 19, b, 'mono');
      });
      text(g, 12, y + 111, st.frame || '完整帧到达前不交付业务', '');
    } else if (cfg.kind === 'flow') {
      const max = 96, left = 12, len = w - 24;
      text(g, 12, y, `写缓冲估算：${st.pending || 0} KiB`, 'title');
      svg('rect', { x: left, y: y + 22, width: len, height: 22, fill: 'var(--line)' }, g);
      svg('rect', { x: left, y: y + 22, width: len * Math.min(st.pending || 0, max) / max,
        height: 22, fill: st.writable === false ? 'var(--warn)' : 'var(--ok)' }, g);
      [32, 64].forEach((v, k) => {
        const x = left + len * v / max;
        svg('line', { x1: x, y1: y + 16, x2: x, y2: y + 50, class: 'wire' }, g);
        text(g, x, y + 69, (k ? '高 ' : '低 ') + v, 'small');
      });
      text(g, 12, y + 104, 'isWritable = ' + (st.writable !== false) + ' · autoRead = ' + (st.autoRead !== false), 'mono');
    } else if (cfg.kind === 'future') {
      text(g, 12, y, '结果状态：' + (st.result || 'PENDING'), 'title');
      text(g, 12, y + 32, 'PENDING → SUCCESS / FAILURE / CANCELLED', 'mono');
      text(g, 12, y + 65, '本地写完成与远端业务 ACK 是两个结果', '');
    } else if (cfg.kind === 'lifecycle') {
      text(g, 12, y, '连接状态：' + (st.phase || 'NEW'), 'title');
      text(g, 12, y + 34, '重试：' + (st.retry || '未安排'), '');
      text(g, 12, y + 68, '停止状态下禁止新连接与重试任务', 'muted');
    } else if (cfg.kind === 'transport') {
      text(g, 12, y, '当前路径：' + (st.path || 'NIO'), 'title');
      text(g, 12, y + 36, st.copy || '传输实现与业务 Pipeline 解耦', '');
      text(g, 12, y + 72, '示意路径；实际拷贝由平台与 TLS 决定', 'muted');
    }
  }
  function draw(st, step, i, ctx) {
    geometry = layout();
    ctx.layer.replaceChildren();
    ctx.fx.replaceChildren();
    const g = ctx.layer;
    const edges = step.edges || cfg.edges || [];
    for (const e of edges) connection(g, e[0], e[1], e[2] || '');
    cfg.nodes.forEach((node, k) => {
      const p = geometry.points[k];
      const status = (st.status || [])[k] || '';
      box(g, p.x, p.y, p.w, p.h, status);
      text(g, p.x + 10, p.y + 23, node[0], 'title');
      text(g, p.x + 10, p.y + 45, node[1], 'small muted');
      text(g, p.x + 10, p.y + 66, (st.values || [])[k] || '待命', 'mono');
    });
    if (cfg.kind) extras(g, st);
    for (const m of step.moves || []) {
      const a = geometry.points[m[0]], b = geometry.points[m[1]];
      if (!reduced) packet(ctx.fx, { x: a.x + a.w / 2, y: a.y + 87 },
        { x: b.x + b.w / 2, y: b.y + 87 }, m[2], m[3] || 'accent');
    }
    previous = { st, step, i, ctx };
  }
  const player = Player({
    scenarios: cfg.scenarios, dwell: 3200,
    init() { previous = null; },
    render: draw,
  });
  let width = 0;
  new ResizeObserver(() => {
    const next = Math.floor(root.getBoundingClientRect().width);
    if (next !== width) {
      width = next;
      if (previous) draw(previous.st, { ...previous.step, moves: [] }, previous.i, previous.ctx);
    }
  }).observe(root);
  // 仅公开给手动调试和页面验证；不依赖外部库或请求。
  window.nettyDiagram = { player, config: cfg };
})();
