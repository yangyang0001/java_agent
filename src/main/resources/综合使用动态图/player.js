/* 面试部分动态图 - 步骤播放器
 *
 * 页面需要提供的元素 id:
 *   controls  控制条容器 (按钮由本文件生成)
 *   svg       动画画布 <svg viewBox="...">
 *   nowNo / nowTitle / nowDesc   当前步骤的编号、标题、说明
 *   code      <pre class="code"> 代码面板 (可选, 没有代码的场景会隐藏其所在 .panel)
 *   stepList  <ol class="steps"> 步骤列表
 *
 * 用法:
 *   Player({
 *     scenarios: [{ label: '场景名', code: ['line1', ...], steps: [...] }, ...],  // 或直接传 steps + code (单场景)
 *     init(svg, ctx),                // 可选: 切换场景时调用, 用来搭建静态画面
 *     render(state, step, i, ctx),   // 每一步调用: 根据 state 更新画面, ctx.fx 是每步自动清空的特效层
 *     dwell: 2600,                   // 每步默认停留毫秒, 单步可用 step.dwell 覆盖
 *   });
 *
 * step 字段:
 *   title  步骤标题 (同时显示在步骤列表)
 *   desc   说明, 可以写 HTML
 *   line   高亮代码行, 从 1 开始, 数字或数组; badLine 用红色高亮
 *   set    相对上一步要修改的状态 (深合并, 数组整体替换); 或 state 给出完整状态
 *   其他字段 (如 hot / moves) 原样传给 render, 适合只在这一步出现的高亮和动画
 *
 * 地址加 #s=2&n=5 可以直接停在第 2 个场景的第 5 步。
 */
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const $ = id => document.getElementById(id);
  const isObj = v => v && typeof v === 'object' && !Array.isArray(v);

  function merge(base, patch) {
    const out = isObj(base) ? { ...base } : {};
    for (const k of Object.keys(patch || {})) {
      out[k] = isObj(patch[k]) && isObj(out[k]) ? merge(out[k], patch[k]) : patch[k];
    }
    return out;
  }

  function svg(tag, attrs, parent, text) {
    const el = document.createElementNS(NS, tag);
    for (const k in attrs || {}) if (attrs[k] !== undefined && attrs[k] !== null) el.setAttribute(k, attrs[k]);
    if (text !== undefined) el.textContent = text;
    if (parent) parent.appendChild(el);
    return el;
  }

  // 把元素平移到 (x, y); 元素需带 class="anim" 才有过渡动画
  function move(el, x, y) { el.style.transform = `translate(${x}px, ${y}px)`; }

  // 在 fx 层创建一个从 from 飞到 to 的小包裹 (圆点 + 文字)
  function packet(fx, from, to, label, cls) {
    const g = svg('g', { class: 'anim' }, fx);
    move(g, from.x, from.y);
    svg('circle', { r: 7, class: 'dot ' + (cls || '') }, g);
    if (label) {
      const t = svg('text', { x: 11, y: 4, class: 'mono small' }, g, label);
      if (cls) t.classList.add('c-' + cls);
    }
    g.getBoundingClientRect();
    requestAnimationFrame(() => requestAnimationFrame(() => move(g, to.x, to.y)));
    return g;
  }

  // 画一条带箭头的线, 箭头 marker 按颜色自动创建
  function arrow(parent, x1, y1, x2, y2, cls) {
    const root = parent.ownerSVGElement || parent;
    const color = (cls || '').split(' ').find(c => ['accent', 'ok', 'warn', 'bad'].includes(c)) || 'line';
    const id = 'arrow-' + color;
    if (!root.querySelector('#' + id)) {
      let defs = root.querySelector('defs') || svg('defs', {}, root);
      const m = svg('marker', { id, viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' }, defs);
      svg('path', { d: 'M0,0 L10,5 L0,10 z', style: `fill: var(--${color})` }, m);
    }
    return svg('line', { x1, y1, x2, y2, class: 'wire ' + (cls || ''), 'marker-end': `url(#${id})` }, parent);
  }

  function Player(cfg) {
    const scenarios = cfg.scenarios || [{ label: '', steps: cfg.steps, code: cfg.code }];
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const root = $('svg');
    let sc = 0, steps, states, i = 0, playing = !reduce, speed = 1, timer = null, ctx;

    $('controls').innerHTML = `
      <div class="btns">
        <button id="pPlay" class="primary" type="button"></button>
        <button id="pPrev" type="button">上一步</button>
        <button id="pNext" type="button">下一步</button>
        <button id="pReset" type="button">重新开始</button>
      </div>
      ${scenarios.length > 1 ? `<label class="ctl">场景 <select id="pScene">${scenarios.map((s, k) => `<option value="${k}">${s.label}</option>`).join('')}</select></label>` : ''}
      <label class="ctl">速度 <select id="pSpeed"><option value="0.5">0.5×</option><option value="1" selected>1×</option><option value="2">2×</option></select></label>
      <div class="progress" aria-hidden="true"><i id="pBar"></i></div>
      <span class="stepno" id="pNo"></span>`;

    function load(k) {
      sc = k;
      steps = scenarios[k].steps;
      states = [];
      let st = scenarios[k].state || cfg.state || {};
      for (const s of steps) { st = s.state ? s.state : merge(st, s.set); states.push(st); }
      const code = $('code');
      if (code) {
        const lines = scenarios[k].code || cfg.code;
        const panel = code.closest('.panel');
        if (panel) panel.hidden = !lines;
        code.innerHTML = (lines || []).map(l => `<span>${l.replace(/&/g, '&amp;').replace(/</g, '&lt;') || ' '}</span>`).join('');
      }
      $('stepList').innerHTML = steps.map((s, n) => `<li><button type="button" data-n="${n}">${s.title}</button></li>`).join('');
      while (root.firstChild) root.removeChild(root.firstChild);
      ctx = { svg: root, scenario: k, layer: svg('g', {}, root) };
      if (cfg.init) cfg.init(ctx.layer, ctx);
      ctx.fx = svg('g', {}, root);
      go(0);
    }

    function go(n) {
      i = (n + steps.length) % steps.length;
      const s = steps[i];
      while (ctx.fx.firstChild) ctx.fx.removeChild(ctx.fx.firstChild);
      cfg.render(states[i], s, i, ctx);
      $('nowNo').textContent = `第 ${i + 1} / ${steps.length} 步`;
      $('nowTitle').textContent = s.title;
      $('nowDesc').innerHTML = s.desc || '';
      const code = $('code');
      if (code) {
        const on = [].concat(s.line || []), bad = [].concat(s.badLine || []);
        [...code.children].forEach((el, k) => {
          el.classList.toggle('on', on.includes(k + 1));
          el.classList.toggle('bad-on', bad.includes(k + 1));
        });
      }
      [...$('stepList').querySelectorAll('button')].forEach((b, k) => {
        b.classList.toggle('cur', k === i);
        b.classList.toggle('done', k < i);
      });
      $('pBar').style.width = ((i + 1) / steps.length * 100) + '%';
      $('pNo').textContent = `${i + 1} / ${steps.length}`;
      schedule();
    }

    function schedule() {
      clearTimeout(timer);
      $('pPlay').textContent = playing ? '暂停' : '播放';
      if (!playing) return;
      const last = i === steps.length - 1;
      const ms = (steps[i].dwell || cfg.dwell || 2600) * (last ? 1.8 : 1) / speed;
      timer = setTimeout(() => go(i + 1), ms);
    }

    $('pPlay').onclick = () => { playing = !playing; schedule(); };
    $('pPrev').onclick = () => { playing = false; go(i - 1); };
    $('pNext').onclick = () => { playing = false; go(i + 1); };
    $('pReset').onclick = () => { playing = !reduce; go(0); };
    $('pSpeed').onchange = e => { speed = +e.target.value; schedule(); };
    if ($('pScene')) $('pScene').onchange = e => load(+e.target.value);
    $('stepList').onclick = e => {
      const b = e.target.closest('button');
      if (b) { playing = false; go(+b.dataset.n); }
    };
    document.addEventListener('keydown', e => {
      if (e.target.closest('select, input, textarea')) return;
      if (e.key === 'ArrowRight') { playing = false; go(i + 1); }
      else if (e.key === 'ArrowLeft') { playing = false; go(i - 1); }
      else if (e.key === ' ' && !e.target.closest('button')) { e.preventDefault(); playing = !playing; schedule(); }
    });

    // 地址带 #s=场景&n=步骤 (都从 1 开始) 时直接停在那一步, 方便截图核对
    const h = new URLSearchParams(location.hash.slice(1));
    if (h.has('s') || h.has('n')) {
      playing = false;
      const s = Math.min(Math.max(+h.get('s') || 1, 1), scenarios.length) - 1;
      if ($('pScene')) $('pScene').value = s;
      load(s);
      go((+h.get('n') || 1) - 1);
    } else {
      load(0);
    }
    return { go: n => go(n), scene: k => load(k) };
  }

  Player.svg = svg;
  Player.move = move;
  Player.packet = packet;
  Player.arrow = arrow;
  window.Player = Player;
})();
