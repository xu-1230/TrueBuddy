<template>
  <transition name="fade">
    <div v-if="open" class="studio-overlay" @click="open = false"></div>
  </transition>
  <transition name="slide">
    <aside v-if="open" class="history-drawer">
      <header class="history-drawer__head">
        <div class="history-drawer__title">
          <el-icon><Clock /></el-icon>
          <span>批改历史记录</span>
        </div>
        <button type="button" class="history-drawer__close" @click="open = false">
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div v-if="loading" class="history-drawer__loading">
        <span class="history-item__spinner"></span>
        <span>正在加载历史记录…</span>
      </div>

      <ul class="history-drawer__list">
        <li
          v-for="record in records"
          :key="record.id"
          class="history-item"
          :class="{
            'is-active': activeId === record.id,
            'is-loading': detailLoadingId === record.id,
            'is-no-record': !record.recordCount,
          }"
          role="button"
          tabindex="0"
          :title="record.recordCount ? '点击载入该次批改结果' : '该次批改没有产出记录'"
          @click="handleClick(record)"
          @keydown.enter="handleClick(record)"
          @keydown.space.prevent="handleClick(record)"
        >
          <div class="history-item__main">
            <span class="history-item__title">{{ record.title }}</span>
            <span
              class="history-item__status"
              :class="record.status === '失败' ? 'is-failed' : 'is-success'"
            >
              {{ record.status }}
            </span>
          </div>
          <div class="history-item__meta">
            <span>{{ record.kb }}</span>
            <span class="history-item__dot">·</span>
            <span>{{ record.workflow }}</span>
            <span class="history-item__dot">·</span>
            <span>{{ record.time }}</span>
            <span class="history-item__count">{{ record.recordCount || 0 }} 份</span>
            <span class="history-item__open">
              <span v-if="detailLoadingId === record.id" class="history-item__spinner"></span>
              <el-icon v-else><ArrowRight /></el-icon>
            </span>
          </div>
        </li>
      </ul>

      <el-empty
        v-if="!records.length && !loading"
        :image-size="80"
        description="暂无批改记录"
        class="history-drawer__empty"
      />
    </aside>
  </transition>
</template>

<script setup>
import { ArrowRight, Clock, Close } from '@element-plus/icons-vue'

defineProps({
  /** 历史记录列表 */
  records: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 当前结果来自哪条历史记录 */
  activeId: { type: Number, default: null },
  /** 正在拉取详情的历史记录 id */
  detailLoadingId: { type: Number, default: null },
})

const emit = defineEmits(['select'])

const open = defineModel({ type: Boolean, default: false })

function handleClick(record) {
  emit('select', record)
}
</script>

<style scoped>
.studio-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: rgba(17, 24, 39, 0.32);
  backdrop-filter: blur(2px);
}

.history-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 21;
  width: 372px;
  max-width: 86%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-left: 1px solid #eceef2;
  box-shadow: -18px 0 40px rgba(17, 24, 39, 0.12);
}

.history-drawer__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid #eceef2;
}

.history-drawer__title {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: #111827;
}

.history-drawer__title .el-icon {
  color: #f59e0b;
}

.history-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.history-drawer__close:hover {
  background: #f3f4f6;
  color: #111827;
}

.history-drawer__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1.2rem 0;
  font-size: 0.78rem;
  color: #6b7280;
}

.history-drawer__list {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0.75rem;
  list-style: none;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.history-drawer__list::-webkit-scrollbar {
  width: 6px;
}

.history-drawer__list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #d8dade;
}

.history-item {
  padding: 0.7rem 0.85rem;
  border-radius: 10px;
  background: #fafbfc;
  border: 1px solid #eef0f3;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.history-item:hover {
  border-color: #ffc7b3;
  background: #ffece6;
}

.history-item:focus-visible {
  outline: 2px solid #ff4500;
  outline-offset: 2px;
}

.history-item.is-active {
  border-color: #ff4500;
  background: #ffdacc;
}

.history-item.is-loading {
  border-color: #ffc7b3;
  background: #ffece6;
}

.history-item.is-no-record {
  cursor: default;
  opacity: 0.72;
}

.history-item.is-no-record:hover {
  border-color: #eef0f3;
  background: #fafbfc;
}

.history-item__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.history-item__title {
  min-width: 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item__status {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 600;
}

.history-item__status.is-success {
  color: #047857;
  background: #d1fae5;
}

.history-item__status.is-failed {
  color: #b91c1c;
  background: #fee2e2;
}

.history-item__meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.4rem;
  white-space: nowrap;
  font-size: 0.72rem;
  color: #9ca3af;
}

.history-item__dot {
  color: #d1d5db;
}

.history-item__count {
  margin-left: auto;
  padding: 1px 7px;
  border-radius: 999px;
  background: #ffece6;
  color: #cc3700;
  font-weight: 600;
  font-size: 0.68rem;
}

.history-item__open {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: #ffc7b3;
  font-size: 0.8rem;
  transition: color 0.2s ease, transform 0.2s ease;
}

.history-item:hover .history-item__open {
  color: #ff4500;
  transform: translateX(2px);
}

.history-item__spinner {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2px solid #ffdacc;
  border-top-color: #ff4500;
  animation: history-spin 0.8s linear infinite;
}

@keyframes history-spin {
  to {
    transform: rotate(360deg);
  }
}

.history-drawer__empty {
  margin-top: 4rem;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.26s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
