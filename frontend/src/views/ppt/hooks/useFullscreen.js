import { onMounted, onUnmounted, ref } from "vue";
import useScreening from "./useScreening";
import { exitFullscreen, isFullscreen } from "../utils/fullscreen";

// 全屏状态管理
export default () => {
  // 放映模式下默认处于全屏状态，因此初始值设为 true
  const fullscreenState = ref(true);

  // 是否是直接通过 ESC 键退出全屏的
  // 手动退出全屏时，需要将该值置为 false，防止退出全屏时触发退出放映
  const escExit = ref(true);

  const { exitScreening } = useScreening();

  const handleFullscreenChange = () => {
    fullscreenState.value = isFullscreen();

    // 非全屏状态且是通过 ESC 退出全屏时，退出放映
    if (!fullscreenState.value && escExit.value) exitScreening();

    escExit.value = true;
  };

  onMounted(() => {
    fullscreenState.value = isFullscreen();
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    document.addEventListener("webkitfullscreenchange", handleFullscreenChange);
  });
  onUnmounted(() => {
    document.removeEventListener("fullscreenchange", handleFullscreenChange);
    document.removeEventListener("webkitfullscreenchange", handleFullscreenChange);
  });

  // 手动退出全屏
  const manualExitFullscreen = () => {
    if (!fullscreenState.value) return;
    escExit.value = false;
    exitFullscreen();
  };

  return {
    fullscreenState,
    manualExitFullscreen,
  };
};
