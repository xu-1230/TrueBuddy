import { defineStore } from 'pinia'
import { slides } from '../mock/slides'
import { theme } from '../mock/theme'

export const useSlidesStore = defineStore('ppt-slides', {
  // 注意：state 需要函数，theme/slides 是 mock 导入的对象，直接浅拷贝一份避免被意外修改
  state: () => ({
    theme: { ...theme }, // 主题样式
    slides: JSON.parse(JSON.stringify(slides)), // 幻灯片页面数据（深拷贝）
    slideIndex: 0, // 当前页面索引
    viewportRatio: 0.5625, // 可视区域比例，默认16:9
  }),

  getters: {
    currentSlide(state) {
      return state.slides[state.slideIndex]
    },

    // 当前页全部有效的动画（过滤掉不属于当前页元素的动画）
    currentSlideAnimations(state) {
      const currentSlide = state.slides[state.slideIndex]
      if (!currentSlide?.animations) return []

      const els = currentSlide.elements
      const elIds = els.map(el => el.id)
      return currentSlide.animations.filter(animation => elIds.includes(animation.elId))
    },

    // 格式化的当前页动画
    // 将触发条件为“与上一动画同时”的项目向上合并到序列中的同一位置
    // 为触发条件为“上一动画之后”项目的上一项添加自动向下执行标记
    formatedAnimations(state) {
      const currentSlide = state.slides[state.slideIndex]
      if (!currentSlide?.animations) return []

      const els = currentSlide.elements
      const elIds = els.map(el => el.id)
      const animations = currentSlide.animations.filter(animation => elIds.includes(animation.elId))

      const formatedAnimations = []
      for (const animation of animations) {
        if (animation.trigger === 'click' || !formatedAnimations.length) {
          formatedAnimations.push({ animations: [animation], autoNext: false })
        }
        else if (animation.trigger === 'meantime') {
          const last = formatedAnimations[formatedAnimations.length - 1]
          last.animations = last.animations.filter(item => item.elId !== animation.elId)
          last.animations.push(animation)
          formatedAnimations[formatedAnimations.length - 1] = last
        }
        else if (animation.trigger === 'auto') {
          const last = formatedAnimations[formatedAnimations.length - 1]
          last.autoNext = true
          formatedAnimations[formatedAnimations.length - 1] = last
          formatedAnimations.push({ animations: [animation], autoNext: false })
        }
      }
      return formatedAnimations
    },
  },

  actions: {
    setTheme(themeProps) {
      this.theme = { ...this.theme, ...themeProps }
    },

    setViewportRatio(viewportRatio) {
      this.viewportRatio = viewportRatio
    },

    setSlides(slides) {
      this.slides = slides
      this.slideIndex = 0
    },

    addSlide(slide) {
      const _slides = Array.isArray(slide) ? slide : [slide]
      const addIndex = this.slideIndex + 1
      this.slides.splice(addIndex, 0, ..._slides)
      this.slideIndex = addIndex
    },

    updateSlide(props) {
      const slideIndex = this.slideIndex
      this.slides[slideIndex] = { ...this.slides[slideIndex], ...props }
    },

    // 按 id 修改任意一页：AI 讲稿是整篇回填的，而 updateSlide 只改当前页
    updateSlideById(slideId, props) {
      const index = this.slides.findIndex(item => item.id === slideId)
      if (index < 0) return
      this.slides[index] = { ...this.slides[index], ...props }
    },

    deleteSlide(slideId) {
      const slidesId = Array.isArray(slideId) ? slideId : [slideId]

      const deleteSlidesIndex = []
      for (let i = 0; i < slidesId.length; i++) {
        const index = this.slides.findIndex(item => item.id === slidesId[i])
        deleteSlidesIndex.push(index)
      }
      let newIndex = Math.min(...deleteSlidesIndex)

      const maxIndex = this.slides.length - slidesId.length - 1
      if (newIndex > maxIndex) newIndex = maxIndex

      this.slideIndex = newIndex
      this.slides = this.slides.filter(item => !slidesId.includes(item.id))
    },

    updateSlideIndex(index) {
      this.slideIndex = index
    },

    addElement(element) {
      const elements = Array.isArray(element) ? element : [element]
      const currentSlideEls = this.slides[this.slideIndex].elements
      this.slides[this.slideIndex].elements = [...currentSlideEls, ...elements]
    },

    deleteElement(elementId) {
      const elementIdList = Array.isArray(elementId) ? elementId : [elementId]
      this.slides[this.slideIndex].elements = this.slides[this.slideIndex].elements.filter(item => !elementIdList.includes(item.id))
    },

    updateElement(data) {
      const { id, props } = data
      const elIdList = typeof id === 'string' ? [id] : id

      const slideIndex = this.slideIndex
      this.slides[slideIndex].elements = this.slides[slideIndex].elements.map(el => {
        return elIdList.includes(el.id) ? { ...el, ...props } : el
      })
    },
  },
})