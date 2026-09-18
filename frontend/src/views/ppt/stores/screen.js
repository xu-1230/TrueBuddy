import { defineStore } from 'pinia'

// 放映状态
export const useScreenStore = defineStore('ppt-screen', {
  state: () => ({
    screening: false,
  }),

  actions: {
    setScreening(status) {
      this.screening = status
    },
  },
})