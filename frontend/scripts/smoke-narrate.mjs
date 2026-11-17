// narrate 冒烟测试（独立运行）：红 = 后端未启动/未含 narrate 路由（AI 演讲不翻页的最上游断点）。
// 运行：cd frontend && node scripts/smoke-narrate.mjs
const proc = globalThis.process
const BASE = proc?.env?.TB_BASE || 'http://localhost:8086/api'
// /ppt/** 自 2026-11-18 起除 speak 外都要求登录；用浏览器里复制出来的访问令牌跑冒烟
const TOKEN = proc?.env?.TB_TOKEN || ''
const main = async () => {
  const res = await fetch(BASE + '/ppt/speech/narrate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {}),
    },
    body: JSON.stringify({ index: 1, title: '冒烟页', text: '这一页讲自动推进。', remark: '围绕本页备注现场讲词。', context: { pageCount: 2, elapsedMin: 1 } }),
  }).catch((error) => {
    console.error('RED: narrate 不可达 ——', error.cause?.code || error.message);
    console.error('     根因：后端未运行，或运行中的进程不含 narrate 路由（需重启 tb-server）。');
    proc?.exit(1);
  });
  if (res.status === 401) {
    console.error('RED: HTTP 401 —— /ppt/speech/narrate 需要登录，请设置 TB_TOKEN=<浏览器访问令牌> 后重跑。');
    proc?.exit(1);
  }
  if (res.status !== 200) { console.error('RED: HTTP', res.status, await res.text()); proc?.exit(1); }
  const body = await res.json();
  const speechText = (body && body.data && body.data.speechText) || body.speechText;
  if (!speechText || !speechText.trim()) { console.error('RED: 200 但 speechText 为空', JSON.stringify(body).slice(0, 300)); proc?.exit(1); }
  console.log('GREEN: speechText', speechText.length, 'chars');
};
main();