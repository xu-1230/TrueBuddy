// narrate 冒烟测试（独立运行）：红 = 后端未启动/未含 narrate 路由（AI 演讲不翻页的最上游断点）。
// 运行：cd frontend && node scripts/smoke-narrate.mjs
const BASE = process.env.TB_BASE || 'http://localhost:48080'
const main = async () => {
  const res = await fetch(BASE + '/ppt/speech/narrate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ index: 1, title: '冒烟页', text: '这一页讲自动推进。', remark: '围绕本页备注现场讲词。', context: { pageCount: 2, elapsedMin: 1 } }),
  }).catch((error) => {
    console.error('RED: narrate 不可达 ——', error.cause?.code || error.message);
    console.error('     根因：后端未运行，或运行中的进程不含 narrate 路由（需重启 tb-server）。');
    process.exit(1);
  });
  if (res.status !== 200) { console.error('RED: HTTP', res.status, await res.text()); process.exit(1); }
  const body = await res.json();
  const speechText = (body && body.data && body.data.speechText) || body.speechText;
  if (!speechText || !speechText.trim()) { console.error('RED: 200 但 speechText 为空', JSON.stringify(body).slice(0, 300)); process.exit(1); }
  console.log('GREEN: speechText', speechText.length, 'chars');
};
main();