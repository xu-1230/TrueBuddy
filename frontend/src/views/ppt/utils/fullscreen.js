// 全屏相关方法（兼容各浏览器前缀）
export const enterFullscreen = () => {
  const docEl = document.documentElement;
  if (docEl.requestFullscreen) docEl.requestFullscreen();
  else if (docEl.webkitRequestFullscreen) docEl.webkitRequestFullscreen();
  else if (docEl.mozRequestFullScreen) docEl.mozRequestFullScreen();
  else if (docEl.msRequestFullscreen) docEl.msRequestFullscreen();
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
  } catch (e) {
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
