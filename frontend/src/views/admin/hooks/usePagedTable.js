import { ref } from 'vue'
import { DEFAULT_PAGE_SIZE } from '@/views/admin/constants'

/**
 * 分页表格状态机
 *
 * 把「加载 / 分页 / 筛选 / 失败兜底」收在一处，页面只声明取数函数与列。
 *
 * fallback 的用途要说清楚：管理端接口是后加的，后端未启动或 `/admin/**` 还没实现时，
 * 页面不应该只剩一个红色报错。fallback 只在请求**完全失败**时启用，并置 usingMock，
 * 由表格角标明确写出「演示数据」——不是静默假成功。
 *
 * @param {{
 *   fetcher: (params: object) => Promise<any>,
 *   fallback?: Array|((params: object) => any),
 *   pageSize?: number,
 *   filters?: () => object,
 * }} options
 */
export function usePagedTable({ fetcher, fallback, pageSize = DEFAULT_PAGE_SIZE, filters = () => ({}) } = {}) {
  const rows = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(pageSize)
  const loading = ref(false)
  const errorMessage = ref('')
  const usingMock = ref(false)

  /** 兼容三种返回体：数组、PageResult{list,total}、MyBatis-Plus{records,total} */
  function normalize(result) {
    if (Array.isArray(result)) return { list: result, total: result.length }
    if (!result || typeof result !== 'object') return { list: [], total: 0 }

    const list = result.list ?? result.records ?? result.rows ?? result.data ?? []
    const count = result.total ?? result.totalCount ?? result.count ?? (Array.isArray(list) ? list.length : 0)
    return {
      list: Array.isArray(list) ? list : [],
      total: Number(count) || 0,
    }
  }

  function apply(result) {
    const { list, total: count } = normalize(result)
    rows.value = list
    total.value = count
  }

  async function load() {
    if (typeof fetcher !== 'function') return

    loading.value = true
    errorMessage.value = ''

    const params = { page: page.value, size: size.value, ...filters() }

    try {
      apply(await fetcher(params))
      usingMock.value = false
    } catch (error) {
      if (fallback) {
        apply(typeof fallback === 'function' ? fallback(params) : fallback)
        usingMock.value = true
        errorMessage.value = ''
      } else {
        rows.value = []
        total.value = 0
        usingMock.value = false
        errorMessage.value = error?.message || '数据加载失败，请稍后重试'
      }
    } finally {
      loading.value = false
    }
  }

  /** 查询：筛选条件变了要回到第一页，否则会停在越界页码上显示空表 */
  function search() {
    page.value = 1
    return load()
  }

  return {
    rows,
    total,
    page,
    size,
    loading,
    errorMessage,
    usingMock,
    load,
    search,
  }
}
