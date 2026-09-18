import { useSlidesStore } from "../stores/slides";
import { useScreenStore } from "../stores/screen";
import { enterFullscreen, exitFullscreen, isFullscreen } from "../utils/fullscreen";

// 放映控制
export default () => {
  const slidesStore = useSlidesStore();
  const screenStore = useScreenStore();

  // 进入放映（从当前页开始）
  const enterScreening = () => {
    enterFullscreen();
    screenStore.setScreening(true);
  };

  // 进入放映（从第一页开始）
  const enterScreeningFromStart = () => {
    slidesStore.updateSlideIndex(0);
    enterScreening();
  };

  // 退出放映
  const exitScreening = () => {
    screenStore.setScreening(false);
    if (isFullscreen()) exitFullscreen();
  };

  return {
    enterScreening,
    enterScreeningFromStart,
    exitScreening,
  };
};
