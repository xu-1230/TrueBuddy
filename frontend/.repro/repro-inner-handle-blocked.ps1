<#
最小复现：迭代节点「迭代开始 / 迭代结束」两个内侧 handle 被内部连线的命中区遮挡。

现象
----
只要那条内部连线还在，从这两个连接点拖动就**无法发起任何连线**（拖到子块后既没有新边、
也没有任何提示），因此「删除内部连线后再连接」这类操作直接做不成。

判定标准
--------
断言 1（几何）：对每个 handle 的中心点做 elementFromPoint。
  期望 —— 顶层元素是该 handle 自身（reachable=true）；
  实际 —— `132996:iterate-in` / `132996:iterate-out` 的顶层元素是
           `path.vue-flow__edge-interaction`（内部连线的命中区），reachable=false。
  打印 `blockedCount`：> 0 即复现。
断言 2（行为）：从 `132996:iterate-in` 拖到 `173594:in`（子块输入）。
  期望 —— store 边数 +1；实际 —— 边数不变、页面无提示 = 复现。

对照（同一脚本内人工确认）
------------------------
先删掉那条内部连线（点击其可见中点 → Delete），再重复断言 1/2：
  elementFromPoint 会返回 handle 自身，拖拽能成功建立新边
  —— 说明 handle 本身没坏，是连线的命中区把它盖住了。

前置：dev server 已在运行（cd frontend && npm run dev；5173 被占用时会自动落到 5174）
用法：powershell -File .repro\repro-inner-handle-blocked.ps1 [-Port 5173]
#>

param([int]$Port = 5174)

$ErrorActionPreference = 'Continue'
Set-Location (Split-Path -Parent (Split-Path -Parent $PSCommandPath))

function Get-EvalText([string]$script) {
  $raw = playwright-cli eval $script | Out-String
  $line = ($raw -split "`n" | Where-Object { $_ -match '^\s*"' } | Select-Object -First 1)
  if (-not $line) { return '' }
  return $line.Trim().Trim('"')
}

Write-Host '=== 1. 打开画布并复位到默认定义 ===' -ForegroundColor Cyan
playwright-cli open "http://localhost:$Port/index/workflow" | Out-Null
Start-Sleep -Seconds 7
playwright-cli resize 1680 1000 | Out-Null
Start-Sleep -Seconds 1
playwright-cli reload | Out-Null
Start-Sleep -Seconds 5
playwright-cli resize 1680 1000 | Out-Null
Start-Sleep -Seconds 2

Write-Host '=== 2. 断言 1：每个 handle 的可命中性 ===' -ForegroundColor Cyan
$hitScript = @'
(() => {
  const report = []
  for (const h of document.querySelectorAll('.vue-flow__handle')) {
    const node = h.closest('.vue-flow__node')
    const nodeId = node ? node.getAttribute('data-id') : '?'
    const hid = h.getAttribute('data-handleid')
    const r = h.getBoundingClientRect()
    const cx = Math.round(r.x + r.width / 2)
    const cy = Math.round(r.y + r.height / 2)
    const top = document.elementFromPoint(cx, cy)
    const cls = top ? String(top.getAttribute('class') || '') : ''
    report.push(nodeId + ':' + hid + ' -> ' + (cls.includes('vue-flow__handle') ? 'HANDLE' : (top ? top.tagName + '.' + cls.split(' ')[0] : 'NULL')))
  }
  const blocked = report.filter((line) => line.indexOf(' -> HANDLE') < 0)
  return 'blockedCount=' + blocked.length + ' | ' + report.join(' ## ')
})()
'@
$hit = Get-EvalText $hitScript
Write-Host $hit
if ($hit -match 'blockedCount=(\d+)') {
  if ([int]$Matches[1] -gt 0) {
    Write-Host ('判定：复现（有 {0} 个 handle 被连线的命中区盖住）' -f $Matches[1]) -ForegroundColor Yellow
  } else {
    Write-Host '判定：未复现（所有 handle 都能直接命中）' -ForegroundColor Green
  }
}

Write-Host ''
Write-Host '=== 3. 断言 2：从被遮挡的 handle 拖到子块输入 handle ===' -ForegroundColor Cyan
$countScript = @'
(() => {
  const root = document.querySelector('.vue-flow')
  let inst = root && root.__vueParentComponent
  let st = null
  while (inst) { if (inst.setupState && inst.setupState.getEdges) { st = inst.setupState; break } inst = inst.parent }
  const ge = st ? st.getEdges : null
  const arr = Array.isArray(ge) ? ge : (ge && ge.value) || []
  return String(arr.length)
})()
'@
$coordScript = @'
(() => {
  const pick = (nodeId, hid) => {
    for (const h of document.querySelectorAll('.vue-flow__handle')) {
      const node = h.closest('.vue-flow__node')
      if (!node || node.getAttribute('data-id') !== nodeId) continue
      if (h.getAttribute('data-handleid') !== hid) continue
      const r = h.getBoundingClientRect()
      return Math.round(r.x + r.width / 2) + ',' + Math.round(r.y + r.height / 2)
    }
    return '-1,-1'
  }
  return pick('132996', 'iterate-in') + '|' + pick('173594', 'in')
})()
'@

$before = Get-EvalText $countScript
$coords = Get-EvalText $coordScript
Write-Host ("拖拽前 store 边数 = {0}；坐标 {1}" -f $before, $coords)

$parts = $coords -split '\|'
$from = ($parts[0] -split ',')
$to = ($parts[1] -split ',')
if ($from.Count -eq 2 -and $to.Count -eq 2) {
  playwright-cli mousemove ([int]$from[0]) ([int]$from[1]) | Out-Null
  Start-Sleep -Milliseconds 200
  playwright-cli mousedown | Out-Null
  Start-Sleep -Milliseconds 150
  playwright-cli mousemove ([int](([int]$from[0] + [int]$to[0]) / 2)) ([int](([int]$from[1] + [int]$to[1]) / 2)) | Out-Null
  Start-Sleep -Milliseconds 150
  playwright-cli mousemove ([int]$to[0]) ([int]$to[1]) | Out-Null
  Start-Sleep -Milliseconds 200
  playwright-cli mouseup | Out-Null
  Start-Sleep -Milliseconds 800
}

$after = Get-EvalText $countScript
$msgs = Get-EvalText "(() => JSON.stringify(Array.from(document.querySelectorAll('.el-message')).map((m) => m.innerText)))()"
Write-Host ("拖拽后 store 边数 = {0}；页面提示 = {1}" -f $after, $msgs)

# 缺陷特征：拖拽**完全没有反应** —— 边数不变，并且页面一条提示都没有。
# 正常时有两种结果：建立了新边（+1）；或因为这一对节点已有连线而给出提示（边数不变，但有 .el-message）。
$noReaction = ($before -ne '') -and ($before -eq $after) -and (($msgs -eq '[]') -or ($msgs -eq ''))
if ($noReaction) {
  Write-Host '判定：复现（拖拽完全没有反应：边数不变且无任何提示）' -ForegroundColor Yellow
} else {
  Write-Host '判定：正常（连接动作已触发：建了新边，或给出了「已有连线」提示）' -ForegroundColor Green
}

Write-Host ''
Write-Host '=== 4. 对照（人工确认，可选） ===' -ForegroundColor Cyan
Write-Host '点击那条内部连线的可见中点 → 按 Delete 删掉它 → 重跑本脚本：'
Write-Host '  断言 1 应变为 blockedCount=0，断言 2 应建立新边。'
