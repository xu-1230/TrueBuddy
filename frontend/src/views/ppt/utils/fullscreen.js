// 全屏相关方法（兼容各浏览器前缀）
export const enterFullscreen = () => {
  const docEl = document.documentElement;
  const request = docEl.requestFullscreen
    || docEl.webkitRequestFullscreen
    || docEl.mozRequestFullScreen
    || docEl.msRequestFullscreen;
  if (!request) return;

  // 浏览器要求「用户手势」才允许进入全屏：由 `/index/ppt?screen=1` 这类自动打开的
  // 放映页在首屏没有任何手势，请求会被拒绝并抛出 Promise 拒绝。全屏只是可选增强，
  // 这里吞掉 rejection，让「进入放映状态」本身始终成立（需要真全屏时点放映工具条上的全屏按钮）
  Promise.resolve(request.call(docEl)).catch(() => {});
};

export const exitFullscreen = () => {
  // 文档不处于全屏状态时调用 exitFullscreen 会报错
  if (!isFullscreen()) return;

  const doc = document;
  try {
    if (doc.exitFullscreen) doc.exitFullscreen();
    else if (doc.webkitExitFullscreen) doc.webkitExitFullscreen();
    else if (doc.mozCancelFullScreen) doc.mozCancelFullScreen();
    else if (doc.msExitFullscreen) doc.msExitFullscreen();
  } catch {
    // ignore
  }
};

export const isFullscreen = () => {
  return document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement ||
    document.msFullscreenElement
    ? true
    : false;
};
