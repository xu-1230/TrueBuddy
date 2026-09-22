/**
 * 成绩汇总表生成与下载（纯前端，无第三方依赖）
 *
 * - CSV：UTF-8 BOM + 双引号转义，Excel / WPS 双击可直接打开
 * - Excel：SpreadsheetML 2003（.xls），Excel / WPS 原生支持，无兼容性弹窗
 */

const HEADERS = ['序号', '学生姓名', '作业文件名', '得分', '等级', '批改状态', 'AI 评语']
const EXCEL_MIME = 'application/vnd.ms-excel'
const CSV_MIME = 'text/csv;charset=utf-8'

/** 导出文件名：作业批改成绩表_<任务号>_<时间戳>.<ext> */
export function exportFileName(taskId, ext) {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
    + `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `作业批改成绩表_${taskId ?? 0}_${stamp}.${ext}`
}

/**
 * 按导出格式生成文件并触发浏览器下载
 * @param {'xlsx'|'csv'} format 导出格式（xlsx 实际产出 .xls，Excel 可直接打开）
 * @param {{taskId?: number, records: Array}} payload
 * @returns {string} 实际生成的文件名
 */
export function exportScores(format, payload) {
  const records = payload?.records ?? []
  const csv = format === 'csv'
  const name = exportFileName(payload?.taskId, csv ? 'csv' : 'xls')
  const content = csv ? buildCsv(records) : buildExcelXml(records)
  download(content, name, csv ? CSV_MIME : EXCEL_MIME)
  return name
}

/** 生成 CSV 文本 */
function buildCsv(records) {
  const rows = [joinCsvRow(HEADERS)]
  records.forEach((record, index) => {
    rows.push(joinCsvRow([
      String(index + 1),
      nullToEmpty(record.studentName),
      nullToEmpty(record.fileName),
      Number.isFinite(record.score) ? String(record.score) : '',
      nullToEmpty(record.grade),
      record.status === 1 ? '成功' : '失败',
      collapse(record.comment),
    ]))
  })
  // BOM：避免 Excel 打开中文乱码
  return `\ufeff${rows.join('\r\n')}\r\n`
}

function joinCsvRow(cells) {
  return cells.map(escapeCsv).join(',')
}

/** 统一加引号；以 = + - @ 开头的内容前置单引号，防止被 Excel 当公式执行 */
function escapeCsv(value) {
  let text = nullToEmpty(value)
  if (text && '=+-@'.includes(text.charAt(0))) {
    text = `'${text}`
  }
  return `"${text.replace(/"/g, '""')}"`
}

/** 生成 Excel 2003 XML（SpreadsheetML）文本 */
function buildExcelXml(records) {
  const rows = [`    ${headerRow()}`]
  records.forEach((record, index) => {
    rows.push(`    <Row>
      <Cell ss:StyleID="body"><Data ss:Type="Number">${index + 1}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="String">${escapeXml(record.studentName)}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="String">${escapeXml(record.fileName)}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="Number">${Number.isFinite(record.score) ? record.score : ''}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="String">${escapeXml(record.grade)}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="String">${record.status === 1 ? '成功' : '失败'}</Data></Cell>
      <Cell ss:StyleID="body"><Data ss:Type="String">${escapeXml(collapse(record.comment))}</Data></Cell>
    </Row>`)
  })
  return `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
  <Styles>
    <Style ss:ID="head">
      <Font ss:Bold="1"/>
      <Interior ss:Color="#FFECE6" ss:Pattern="Solid"/>
      <Alignment ss:Vertical="Center"/>
    </Style>
    <Style ss:ID="body">
      <Alignment ss:Vertical="Top" ss:WrapText="1"/>
    </Style>
  </Styles>
  <Worksheet ss:Name="成绩汇总">
    <Table>
      <Column ss:Width="42"/>
      <Column ss:Width="80"/>
      <Column ss:Width="220"/>
      <Column ss:Width="48"/>
      <Column ss:Width="60"/>
      <Column ss:Width="60"/>
      <Column ss:Width="520"/>
${rows.join('\n')}
    </Table>
  </Worksheet>
</Workbook>
`
}

function headerRow() {
  const cells = HEADERS
    .map((title) => `<Cell ss:StyleID="head"><Data ss:Type="String">${escapeXml(title)}</Data></Cell>`)
    .join('')
  return `<Row>${cells}</Row>`
}

function escapeXml(value) {
  return nullToEmpty(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function collapse(text) {
  return nullToEmpty(text).replace(/\s+/g, ' ').trim()
}

function nullToEmpty(text) {
  return text === null || text === undefined ? '' : String(text)
}

/** Blob 下载 */
function download(content, filename, mime) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 释放对象 URL，避免内存泄漏
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
