/**
 * 讲课工作项（Lesson Ticket）P0 的最小播报队列：manual 跟随模式。
 *
 * 职责边界（docs「解决方案-04」§4 的 P0 子集）：
 * - 整页 = 多段串行：先把整页切段全部生成（先入队），再逐段发送（后发送）；
 * - 每段带 `requestId`（`page-{index}-seg-{n}`）：段身份稳定，P1 的重试/恢复以它做幂等键；
 * - 同段失败中止本页后续段（保守：不静默跳句，也不自动重试——重试与 Attention 卡片是 P1 的事）；
 * - 翻页即换内容：旧回合的在途请求与待发段全部作废（abort 由注入方执行）；
 * - 「能停」：`stop()` 作废一切并调注入的 `stopSpeaking()`（掐请求 = 后端合成取消 + 播放器静音）。
 *
 * 推进决策不在这里：P0 是 manual 跟随（翻页事件就是推进 Trigger），阶段表 / 计划 gate 是 P1 的
 * `lesson-gate.js`。本模块只负责「一页讲完且讲得可靠」。
 *
 * 依赖注入使得编排与浏览器解耦：`speakSegment` / `stopSpeaking` / `getPageText` 都由放映页注入，
 * `node --test` 用假件覆盖全部行为。
 */

import { splitIntoSegments } from './lesson-segments.js'

/**
 * 建一个讲课工作项 runner。
 *
 * @param {object} deps 依赖注入
 * @param {(segment: {id: string, page: number, index: number, text: string}) => Promise<void>} deps.speakSegment
 *   播报一段：resolve = 该段的 SSE 流正常结束（音频已交给播放器排期）；reject = 失败或被取消。
 * @param {() => void} [deps.stopSpeaking] 立刻安静：掐在途请求 + 播放器静音（翻页 / stop 时调用）
 * @param {(page: number) => string} [deps.getPageText] 取某页讲稿纯文本（0 基页码）
 * @param {(event: object) => void} [deps.onEvent] 工作项事件（page-start / page-done / page-failed /
 *   page-skipped / page-drained / stopped），供控制台展示与 P1 的台账对账
 * @param {(text: string) => string[]} [deps.segmentText] 切段函数注入点：默认
 *   {@link splitIntoSegments}（上限 1000 字）；测试与 P1 的策略替换都从这里进
 * @returns {{
 *   start: (page: number) => void,
 *   onPageChanged: (page: number) => void,
 *   notifyDrained: () => void,
 *   stop: () => void,
 *   readonly active: boolean,
 * }}
 */
export function createLessonRunner({ speakSegment, stopSpeaking, getPageText, onEvent, segmentText = splitIntoSegments } = {}) {
  /** 工作项是否在运行（「开始演讲」到「停止」之间） */
  let active = false
  /**
   * 回合号：start / 翻页 / stop 都会递增。旧回合的异步回调（段循环）一律以
   * `myRun !== run` 自我作废——不靠清理定时器或手写取消，回合号就是唯一的真相。
   */
  let run = 0

  const emit = (event) => onEvent?.(event)
  /** 回合是否仍然有效（没被翻页/停止作废） */
  const isCurrent = (myRun) => myRun === run && active

  /**
   * 讲一页：取文本 → 切段（先入队）→ 逐段发送（后发送）。
   * 段循环是私有异步回合，翻页/停止靠回合号作废它。
   */
  function speakPage(page) {
    const myRun = ++run
    const raw = (getPageText?.(page) ?? '').trim()
    const texts = segmentText(raw)
    if (!texts.length) {
      emit({ type: 'page-skipped', page })
      return
    }
    // 先入队：整页的段对象在发送前就全部定型（身份 + 顺序），发送只是逐个取出
    const queue = texts.map((text, index) => ({ id: `page-${page}-seg-${index}`, page, index, text }))
    emit({ type: 'page-start', page, segments: queue.length })

    ;(async () => {
      for (const segment of queue) {
        if (!isCurrent(myRun)) return
        try {
          await speakSegment?.(segment)
        } catch (error) {
          // 回合已作废的 reject 是「被停止/翻页掐断」，不是失败，别记 page-failed
          if (!isCurrent(myRun)) return
          emit({ type: 'page-failed', page, segmentId: segment.id, message: error?.message ?? String(error) })
          return
        }
        if (!isCurrent(myRun)) return
      }
      if (!isCurrent(myRun)) return
      emit({ type: 'page-done', page, segments: queue.length })
    })()
  }

  return {
    /** 启动工作项并开始讲指定页（控制台「开始演讲」信号） */
    start(page) {
      active = true
      speakPage(page)
    },
    /** 老师翻页（manual 跟随的唯一推进 Trigger）：旧回合作废、立即安静、从头讲新页 */
    onPageChanged(page) {
      if (!active) return
      stopSpeaking?.()
      speakPage(page)
    },
    /** 播放器「本页音频已全部播完」回调（P1 自动推进的第一前置；P0 只上报） */
    notifyDrained() {
      if (!active) return
      emit({ type: 'page-drained' })
    },
    /** 停止工作项：作废一切 + 立刻安静（老师随时可停） */
    stop() {
      if (!active) return
      active = false
      ++run
      stopSpeaking?.()
      emit({ type: 'stopped' })
    },
    get active() {
      return active
    },
  }
}
