import JSZip from 'jszip'

/**
 * 作业批改 —— zip 压缩包本地解压
 *
 * 学生作业常以压缩包提交，这里在浏览器端直接解压：
 * - 保留包内目录层级，并挂在压缩包所在目录下
 *   （解压文件的 webkitRelativePath 形如「分组A/张三.zip/张三/作业.docx」，
 *    压缩包直接投放时为「张三.zip/张三/作业.docx」）；
 * - 中文文件名兼容：Windows 压缩包通常不置 UTF-8 标志位，按 GBK 兜底解码；
 * - 限额与容错：单包条目数 / 解压总字节上限（防 zip bomb），单包失败不影响同批其他文件。
 */

/** 单包最大条目数 */
export const MAX_ZIP_ENTRIES = 300
/** 单包解压后总字节上限（200MB） */
export const MAX_ZIP_TOTAL_SIZE = 200 * 1024 * 1024
/** 单包内单文件解压上限（50MB，与普通文件上限一致） */
export const MAX_ZIP_FILE_SIZE = 50 * 1024 * 1024

/** 系统 / 临时文件忽略规则（与 FolderUploader 保持一致） */
const IGNORED_NAME_PATTERN = /(^|\/)(\.|__MACOSX|~\$)/

/**
 * 解压一个 zip 文件
 *
 * @param {File} zipFile zip 文件
 * @param {{ maxEntries?: number, maxTotalSize?: number, maxFileSize?: number }} [limits]
 * @returns {Promise<{ files: File[], skipped: string[], warnings: string[] }>}
 *          files 为成功解压的文件（已写入 webkitRelativePath）
 */
export async function extractZip(zipFile, limits = {}) {
  const maxEntries = limits.maxEntries ?? MAX_ZIP_ENTRIES
  const maxTotalSize = limits.maxTotalSize ?? MAX_ZIP_TOTAL_SIZE
  const maxFileSize = limits.maxFileSize ?? MAX_ZIP_FILE_SIZE

  const files = []
  const skipped = []
  const warnings = []

  let zip
  try {
    zip = await JSZip.loadAsync(zipFile, { decodeFileName: decodeZipFileName })
  } catch (e) {
    return { files, skipped, warnings: [`「${zipFile.name}」解析失败：${reason(e)}`] }
  }

  const entries = Object.values(zip.files).filter((entry) => !entry.dir)
  if (!entries.length) {
    warnings.push(`「${zipFile.name}」是空压缩包`)
    return { files, skipped, warnings }
  }
  if (entries.length > maxEntries) {
    warnings.push(`「${zipFile.name}」包含 ${entries.length} 个文件，超过 ${maxEntries} 个上限，仅处理前 ${maxEntries} 个`)
  }

  // 解压内容挂到「压缩包自身所在的位置」：包内路径接在压缩包的相对路径之后，
  // 因此压缩包在子目录里（如「分组A/张三.zip」）时，解压结果仍留在该子目录内，
  // 同时保留一层以压缩包文件名命名的节点，便于辨认来源。
  const rootPath = zipFile.webkitRelativePath || zipFile.name
  let totalSize = 0

  for (const entry of entries.slice(0, maxEntries)) {
    // 部分工具（.NET / Compress-Archive）写出的包内路径用反斜杠，统一规范为 /
    const innerPath = entry.name
      .replace(/\\/g, '/')
      .replace(/^\/+/, '')
      .replace(/^\.\//, '')
    if (!innerPath || IGNORED_NAME_PATTERN.test(innerPath)) continue

    try {
      const blob = await entry.async('blob')
      if (blob.size > maxFileSize) {
        skipped.push(`${innerPath}（超过 ${Math.round(maxFileSize / 1024 / 1024)}MB）`)
        continue
      }
      totalSize += blob.size
      if (totalSize > maxTotalSize) {
        warnings.push(`「${zipFile.name}」解压总量超过 ${Math.round(maxTotalSize / 1024 / 1024)}MB，已停止继续解压`)
        break
      }
      files.push(toFile(blob, innerPath, `${rootPath}/${innerPath}`))
    } catch (e) {
      // 加密条目或压缩方法不支持时 jszip 会抛错，跳过该条目不中断整包
      skipped.push(`${innerPath}（${reason(e)}）`)
    }
  }

  if (!files.length && !warnings.length) {
    warnings.push(`「${zipFile.name}」中没有可读取的文件`)
  }
  return { files, skipped, warnings }
}

/** blob → File，并写入 webkitRelativePath 以保留「zip 名/包内路径」层级 */
function toFile(blob, innerPath, relativePath) {
  const name = innerPath.split('/').pop()
  const file = new File([blob], name, { type: blob.type || '' })
  // webkitRelativePath 为只读原型属性，这里在实例上覆写以复用目录树构建逻辑
  Object.defineProperty(file, 'webkitRelativePath', { value: relativePath })
  return file
}

/**
 * 文件名解码：优先按 UTF-8 严格解码（zip 规范置了 UTF-8 标志位时），
 * 失败则按 GBK 兜底（Windows 资源管理器压缩的中文包）
 */
function decodeZipFileName(bytes) {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    try {
      return new TextDecoder('gbk').decode(bytes)
    } catch {
      return new TextDecoder().decode(bytes)
    }
  }
}

function reason(error) {
  const message = error?.message || String(error)
  if (/encrypted|password/i.test(message)) return '压缩包已加密，无法解压'
  if (/corrupt|invalid|end of central directory/i.test(message)) return '压缩包已损坏或不是有效的 zip'
  return message
}
