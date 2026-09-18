import { onMounted, onUnmounted, watch, ref } from "vue";
import { storeToRefs } from "pinia";
import { useSlidesStore } from "../stores/slides";

// 在屏幕中的幻灯片尺寸，根据可视区域大小按比例缩放
export default (wrapRef = null) => {
  const slidesStore = useSlidesStore();
  const { viewportRatio } = storeToRefs(slidesStore);

  const slideWidth = ref(0);
  const slideHeight = ref(0);

  // 计算和更新幻灯片内容的尺寸（按比例自适应屏幕）
  const updateSlideSize = () => {
    const slideWrapRef = wrapRef?.value || document.body;
    const clientWidth = slideWrapRef.clientWidth || slideWrapRef.innerWidth || 0;
    const clientHeight = slideWrapRef.clientHeight || slideWrapRef.innerHeight || 0;
    if (clientWidth <= 0 || clientHeight <= 0) return;

    let width, height;

    // 可视区域高度与宽度的比例大于幻灯片比例时，以宽度为基准
    if (clientHeight / clientWidth > viewportRatio.value) {
      width = clientWidth;
      height = clientWidth * viewportRatio.value;
    }
    // 以高度为基准
    else {
      width = clientHeight / viewportRatio.value;
      height = clientHeight;
    }
    slideWidth.value = width;
    slideHeight.value = height;
  };

  onMounted(() => {
    updateSlideSize();
    window.addEventListener("resize", updateSlideSize);
  });
  onUnmounted(() => {
    window.removeEventListener("resize", updateSlideSize);
  });

  // 幻灯片比例变化时重新计算（导入的 PPT 数据可能带不同的比例）
  watch(viewportRatio, updateSlideSize);

  return {
    slideWidth,
    slideHeight,
  };
};
