/**
 * 程序化 SVG 封面生成器（移植自参考件 js/cover-art.js，规格与原站一致）。
 *
 * 六种图案变体（orbits / flow / contour / nodes / spectrum / iso），
 * 图案与配色由卡片标题哈希派生（种子化伪随机，同一卡片恒定同一图案）。
 * 色系不再按「family」自由取，而是按资产类型固定映射（共识 Q2）：
 *   知识库=蓝、工作流=紫、文档生成=橙、会话=绿；
 * 同一类型内以标题哈希做 ±14° 色相抖动，保留「每张卡都略有不同」的观感。
 *
 * 本文件为纯函数集，无副作用，可被 Node 直接跑单测。
 */

/* FNV-1a 字符串哈希 → 32 位无符号整数 */
function hashStr(str) {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x1000193) >>> 0;
  }
  return h >>> 0;
}

/* mulberry32 伪随机数生成器（0..1） */
function mulberry32(seed) {
  let a = seed | 0;
  return function () {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = Math.imul(t ^ (t >>> 7), 61 | t) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v));
}

/* 资产类型 → 封面色系（色相 / 饱和度），与首页标签页的 kind 对应 */
const KIND_FAMILY = {
  knowledge: { h: 216, s: 80 },
  workflow: { h: 266, s: 74 },
  doc: { h: 22, s: 88 },
  chat: { h: 152, s: 70 },
};

/* 卡片主题色 --tc：类型色相 + 标题哈希偏移（±14） */
export function trackColor(title, kind) {
  const fam = KIND_FAMILY[kind] ?? KIND_FAMILY.knowledge;
  const offset = (hashStr(`${title}#h`) % 29) - 14;
  return `hsl(${fam.h + offset} ${fam.s}% 58%)`;
}

/* SVG 内动画元素的公共 transform-origin */
function centerOrigin() {
  return "transform-origin:200px 55px";
}

/* ---------- 六种图案生成器 ---------- */
const GENERATORS = {
  /* 同心轨道 + 卫星点云 + 进度弧 */
  orbits: function (e) {
    const { rand, C, ring, prog, dots } = e;
    let rings = "";
    for (let k = 1; k <= 4; k++) {
      const r = 0.5 * ring + 9 * k;
      rings += `<circle cx="200" cy="55" r="${r.toFixed(1)}" fill="none" stroke="${C.line}" stroke-opacity="${(0.66 - 0.1 * k).toFixed(2)}" stroke-width="1.7"${k % 2 ? ' stroke-dasharray="6 11"' : ""}/>`;
    }
    let sat = "";
    for (let k = 0; k < dots; k++) {
      const dist = ring + 58 * rand();
      const angle = rand() * Math.PI * 2;
      sat += `<circle cx="${(200 + Math.cos(angle) * dist).toFixed(1)}" cy="${(55 + Math.sin(angle) * dist * 0.62).toFixed(1)}" r="${(1.2 + 2.4 * rand()).toFixed(1)}" fill="${C.pale}" opacity="${(0.45 + 0.5 * rand()).toFixed(2)}"/>`;
    }
    const outer = ring + 9;
    return (
      rings +
      `<g class="cover-spin" style="${centerOrigin()}">${sat}</g>` +
      `<circle cx="200" cy="55" r="${ring.toFixed(1)}" fill="none" stroke="${C.glow}" stroke-width="2.2" opacity=".9"/>` +
      `<circle cx="200" cy="55" r="3.6" fill="${C.pale}"/>` +
      `<circle cx="200" cy="55" r="${outer.toFixed(1)}" fill="none" stroke="${C.pale}" stroke-width="3" stroke-linecap="round" stroke-dasharray="${(2 * Math.PI * outer * prog).toFixed(1)} 9999" transform="rotate(-90 200 55)" opacity=".95"/>`
    );
  },

  /* 六条波浪流线 + 星点闪烁 */
  flow: function (e) {
    const { rand, C, dots } = e;
    let waves = "";
    for (let k = 0; k < 6; k++) {
      const base = 11 + 17 * k + 4 * rand();
      const n = 7 + 12 * rand();
      waves += `<path class="cover-flow" style="animation-delay:${(-1.2 * k).toFixed(1)}s" d="M-20 ${base.toFixed(0)} C 80 ${(base - n).toFixed(0)}, 160 ${(base + n).toFixed(0)}, 240 ${(base - 0.55 * n).toFixed(0)} S 340 ${(base + 0.7 * n).toFixed(0)}, 420 ${(base - 0.3 * n).toFixed(0)}" fill="none" stroke="${k % 2 ? C.line : C.glow}" stroke-width="${k % 3 ? 1.7 : 2.8}" stroke-opacity="${(0.48 + 0.42 * rand()).toFixed(2)}" stroke-linecap="round"/>`;
    }
    let stars = "";
    for (let k = 0; k < dots; k++) {
      stars += `<circle cx="${(400 * rand()).toFixed(0)}" cy="${(9 + 94 * rand()).toFixed(0)}" r="${(1.1 + 2 * rand()).toFixed(1)}" fill="${C.pale}" opacity="${(0.4 + 0.5 * rand()).toFixed(2)}"/>`;
    }
    return waves + `<g class="cover-twinkle">${stars}</g>`;
  },

  /* 五层呼吸椭圆 + 中心光斑 */
  contour: function (e) {
    const { rand, C, ring } = e;
    let ellipses = "";
    for (let k = 5; k > 0; k--) {
      const rx = 15 * k;
      const ry = 0.52 * rx;
      const cx = 200 + (rand() - 0.5) * 22;
      const cy = 55 + (rand() - 0.5) * 12;
      ellipses += `<ellipse class="cover-breathe" style="animation-delay:${(-0.26 * k).toFixed(2)}s;${centerOrigin()}" cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" rx="${rx}" ry="${ry.toFixed(1)}" fill="none" stroke="${k < 3 ? C.glow : C.line}" stroke-width="${k < 3 ? 2.4 : 1.5}" stroke-opacity="${(0.2 + 0.13 * k).toFixed(2)}"/>`;
    }
    return ellipses + `<circle cx="200" cy="55" r="${(0.62 * ring).toFixed(1)}" fill="${C.glow}" opacity=".92"/>`;
  },

  /* 随机节点网络（近距连线）+ 旋转虚线环 */
  nodes: function (e) {
    const { rand, C, ring, dots } = e;
    const count = 6 + Math.round(dots / 3);
    const pts = [];
    for (let k = 0; k < count; k++) {
      pts.push([30 + 340 * rand(), 11 + 90 * rand(), rand()]);
    }
    let lines = "";
    for (let a = 0; a < count; a++) {
      for (let b = a + 1; b < count; b++) {
        if (Math.hypot(pts[a][0] - pts[b][0], pts[a][1] - pts[b][1]) < 92) {
          lines += `<line x1="${pts[a][0].toFixed(1)}" y1="${pts[a][1].toFixed(1)}" x2="${pts[b][0].toFixed(1)}" y2="${pts[b][1].toFixed(1)}" stroke="${C.line}" stroke-width="1.4" stroke-opacity=".52"/>`;
        }
      }
    }
    let nodes = "";
    pts.forEach((p, i) => {
      nodes += `<circle class="cover-node" style="animation-delay:${(0.22 * i).toFixed(2)}s" cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="${(2.8 + 3.8 * p[2]).toFixed(1)}" fill="${p[2] > 0.7 ? C.pale : C.glow}" opacity=".95"/>`;
    });
    return (
      lines +
      nodes +
      `<circle class="cover-spin-slow" style="${centerOrigin()}" cx="200" cy="55" r="${ring.toFixed(1)}" fill="none" stroke="${C.pale}" stroke-width="1.5" stroke-dasharray="6 8" opacity=".7"/>`
    );
  },

  /* 频谱柱状图（按进度点亮）+ 基准虚线 */
  spectrum: function (e) {
    const { rand, C, prog, pop } = e;
    const n = Math.round(20 + 8 * Math.log10(pop));
    const step = 356 / n;
    let bars = "";
    for (let k = 0; k < n; k++) {
      const h = Math.min(82, (16 + 88 * rand()) * (0.42 + 0.58 * prog));
      const lit = k / n < prog;
      bars += `<rect class="cover-bar" style="animation-delay:${(0.032 * k).toFixed(3)}s" x="${(22 + k * step).toFixed(1)}" y="${(100 - h).toFixed(1)}" width="${(0.58 * step).toFixed(1)}" height="${h.toFixed(1)}" rx="${(0.29 * step).toFixed(1)}" fill="${lit ? C.glow : C.line}" opacity="${lit ? 1 : 0.5}"/>`;
    }
    const baseY = 100 - 82 * prog;
    return bars + `<line x1="22" y1="${baseY.toFixed(1)}" x2="378" y2="${baseY.toFixed(1)}" stroke="${C.pale}" stroke-width="1.3" stroke-dasharray="7 7" opacity=".8"/>`;
  },

  /* 等距网格砖（按进度点亮） */
  iso: function (e) {
    const { C, prog } = e;
    let out = "";
    for (let row = 0; row < 4; row++) {
      for (let col = 0; col < 13; col++) {
        const x = 30 * col + (row % 2 ? 15 : 0) - 20;
        const y = 26 * row + 8;
        const lit = (13 * row + col) / 52 < prog;
        out += `<rect x="${x}" y="${y}" width="27" height="21" rx="4" fill="none" stroke="${C.line}" stroke-opacity="${lit ? 0.78 : 0.22}" stroke-width="1.4"/>`;
        if (lit) {
          out += `<rect x="${x}" y="${y}" width="27" height="21" rx="4" fill="${C.glow}" opacity=".26"/>`;
        }
      }
    }
    return out;
  },
};

const COVER_KINDS = ["orbits", "flow", "contour", "nodes", "spectrum", "iso"];

/**
 * 生成卡片封面 SVG 字符串。
 * 原站的「报名人数 / 奖金 / 进度」三个装饰参数改由真实资产数据派生：
 * 规模计数决定点云密度与封面环半径，进度弧用标题哈希出装饰档位（不外显为数字）。
 *
 * @param {{id:string|number, title:string, kind:string, metric:number|null}} card
 * @returns {string} SVG 字符串（经 v-html 渲染；标题只参与哈希，不进入 DOM 文本）
 */
export function coverArt(card) {
  const fam = KIND_FAMILY[card.kind] ?? KIND_FAMILY.knowledge;
  const rand = mulberry32(hashStr(card.title));
  const kind = COVER_KINDS[Math.floor(rand() * COVER_KINDS.length)];
  const flip = rand() < 0.5;
  const uid = "c" + String(card.id).replace(/[^a-zA-Z0-9]/g, "");

  const hue = fam.h + ((hashStr(`${card.title}#h`) % 29) - 14);
  const people = clamp((card.metric || 3) * 30, 40, 6000);
  const ring = clamp(14 + Math.sqrt(card.metric || 1), 14, 42);
  const prog = 0.35 + (0.5 * (hashStr(`${card.title}#p`) % 100)) / 100;
  const dots = Math.round(8 + 6 * Math.log10(people));
  const sat = fam.s;

  const palette = {
    d1: `hsl(${hue} ${sat}% 20%)`,
    d2: `hsl(${hue + 24} ${Math.round(0.5 * sat)}% 5%)`,
    glow: `hsl(${hue} 100% 58%)`,
    line: `hsl(${hue} 100% 76%)`,
    pale: `hsl(${hue} 100% 93%)`,
  };

  const art = GENERATORS[kind]({
    rand,
    C: palette,
    ring,
    prog,
    dots,
    pop: people,
  });

  return (
    `<svg viewBox="0 0 400 112" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">` +
    `<defs>` +
    `<linearGradient id="bg${uid}" x1="${+!!flip}" y1="0" x2="${+!flip}" y2="1">` +
    `<stop offset="0" stop-color="${palette.d1}"/>` +
    `<stop offset="1" stop-color="${palette.d2}"/>` +
    `</linearGradient>` +
    `<radialGradient id="gl${uid}" cx="${flip ? 0.82 : 0.18}" cy="0.18" r="0.8">` +
    `<stop offset="0" stop-color="${palette.glow}" stop-opacity=".26"/>` +
    `<stop offset="1" stop-color="${palette.glow}" stop-opacity="0"/>` +
    `</radialGradient>` +
    `</defs>` +
    `<rect width="400" height="112" fill="url(#bg${uid})"/>` +
    `<rect width="400" height="112" fill="url(#gl${uid})"/>` +
    `<g class="cover-art-layer"><g class="cover-drift">${art}</g></g>` +
    `</svg>`
  );
}

/* 光带 sheen 的起始延迟（0-6s，由标题哈希派生） */
export function sheenDelay(title) {
  const e = (hashStr(title) % 89) / 89;
  return `${(6 * e).toFixed(2)}s`;
}

/* 5 个白色粒子的内联布局（位置/尺寸/延迟由标题哈希派生） */
export function sparkLayouts(title) {
  const layouts = [];
  for (let i = 0; i < 5; i++) {
    layouts.push({
      left: `${hashStr(`${title}sx${i}`) % 94}%`,
      top: `${12 + (hashStr(`${title}sy${i}`) % 62)}%`,
      size: `${(1.5 + (hashStr(`${title}sz${i}`) % 15) / 10).toFixed(1)}px`,
      delay: `${((hashStr(`${title}sd${i}`) % 38) / 10).toFixed(1)}s`,
    });
  }
  return layouts;
}
