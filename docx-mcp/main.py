import base64
import hashlib
import html
import io
import json
import os
import re
import shutil
import sys
import tempfile
from datetime import timedelta
from pathlib import Path
from typing import Any, Callable, Literal, Optional

import requests
import yaml
from mcp.server.fastmcp import FastMCP
from minio import Minio

# MCP 客户端启动时会把工作目录切到临时目录，此处显式把项目目录加入模块搜索路径，
# 否则同目录下的 docx_html 无法被导入。
_PROJECT_DIR = Path(__file__).resolve().parent
if str(_PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(_PROJECT_DIR))

import docx_edit as edit  # noqa: E402
from docx_html import ImageAsset, render_docx  # noqa: E402
from html_to_docx import apply_html_replacements, render_html_to_docx  # noqa: E402

# 创建 MCP 服务器实例
mcp = FastMCP("docx-mcp")

_CONFIG_PATH = _PROJECT_DIR / "config.yaml"


# --------------------------------------------------------------------------- #
# 资源准备
# --------------------------------------------------------------------------- #


def _materialize_source(source: str) -> tuple[str, Path | None]:
    """把输入统一为本地 docx 路径。

    本地路径原样返回；``http(s)`` 链接则下载到临时文件，并返回该临时路径
    以便调用方在 ``finally`` 中清理。
    """
    if source.startswith(("http://", "https://")):
        response = requests.get(source, timeout=60)
        response.raise_for_status()
        handle, temp_name = tempfile.mkstemp(suffix=".docx")
        os.close(handle)
        temp_path = Path(temp_name)
        temp_path.write_bytes(response.content)
        return str(temp_path), temp_path
    return source, None


# 会话句柄：docx_open 返回 docId，后续工具只传 docId，不必每次重复
# source + template 并重新下载/解析大文档。
_DOCS: dict[str, str] = {}

# HTML 句柄：docx_to_html 可把结果托管在进程内并返回 handle，调用方不必再
# 自行落盘 + 事后清理临时文件。
_HTML_HANDLES: dict[str, str] = {}

# 会话句柄引用到的文档统一放在 MCP 自己的缓存目录，而不是系统临时目录：
# 临时目录里的下载副本会被 _run 的 finally 清掉，句柄就会失效。
_CACHE_DIR = Path(tempfile.gettempdir()) / "docx-mcp-cache"


def _persist(path: str) -> str:
    """把（可能是下载下来的）源文件托管到 MCP 缓存目录，供会话句柄长期引用。"""
    source = Path(path)
    _CACHE_DIR.mkdir(parents=True, exist_ok=True)
    stat = source.stat()
    fingerprint = f"{source.resolve()}|{stat.st_size}|{stat.st_mtime_ns}"
    digest = hashlib.sha1(fingerprint.encode("utf-8")).hexdigest()[:12]
    target = _CACHE_DIR / f"{digest}-{source.name}"
    if not target.exists():
        shutil.copy2(source, target)
    return str(target)


def _register_doc(path: str) -> str:
    """登记一个会话句柄（路径 + 文件指纹），返回 ``doc-xxxx``。"""
    stat = os.stat(path)
    fingerprint = f"{Path(path).resolve()}|{stat.st_size}|{stat.st_mtime_ns}"
    doc_id = "doc-" + hashlib.sha1(fingerprint.encode("utf-8")).hexdigest()[:12]
    _DOCS[doc_id] = path
    return doc_id


def _register_html(text: str) -> str:
    """把 HTML 文本托管在进程内，返回 ``html-xxxx`` 句柄。"""
    handle = "html-" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]
    _HTML_HANDLES[handle] = text
    return handle


def _resolve_source(source: str) -> tuple[str, Path | None]:
    """统一解析 source：``docId`` / 本地路径 / ``http(s)`` 直链。

    返回 ``(本地 docx 路径, 需清理的临时文件或 None)``。
    """
    cached = _DOCS.get(source)
    if cached:
        return cached, None
    return _materialize_source(source)


def _run(source: str, handler: Callable[..., dict[str, Any]]) -> str:
    """打开文档 → 执行 ``handler(document, model, path)`` → 序列化为 JSON。

    临时下载的源文件在这里统一清理；任何异常都转成 ``{"error": ...}``，
    不会让 MCP 调用直接崩掉。
    """
    temp_path: Path | None = None
    try:
        path, temp_path = _resolve_source(source)
        document, model = edit.open_document(path)
        return json.dumps(handler(document, model, path), ensure_ascii=False, indent=2)
    except Exception as exc:  # noqa: BLE001 - 统一转成结构化错误返回
        return json.dumps({"error": str(exc)}, ensure_ascii=False)
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


def _writable_target(source: str, output_path: Optional[str], path: str) -> str:
    """决定写回目标：显式 output_path > 就地写回本地源。"""
    if output_path:
        return output_path
    if source.startswith(("http://", "https://")):
        raise ValueError("源是 http(s) 直链，无法就地写回，请显式指定 output_path")
    return path


def _load_html(value: str) -> str:
    """``html`` 参数既可以是 HTML 文本，也可以是本地路径、http(s) 直链或句柄。

    以 ``<`` 开头视为 HTML 文本；``html-xxxx`` 视为 docx_to_html 托管在进程内的
    句柄；``http(s)://`` 视为直链；其余视为文件路径。这样 AI 可以先把 HTML 落盘、
    用编辑工具就地改写，再把路径交给本工具，避免把整篇文档塞进工具参数里往返。
    """
    cached = _HTML_HANDLES.get(value.strip())
    if cached is not None:
        return cached
    text = value.lstrip()
    if text.startswith("<"):
        return value
    source = value.strip()
    if source.startswith(("http://", "https://")):
        response = requests.get(source, timeout=60)
        response.raise_for_status()
        return response.content.decode("utf-8")
    path = Path(source)
    if not path.exists():
        raise FileNotFoundError(f"既不是 HTML 文本，也找不到该文件：{source}")
    return path.read_text(encoding="utf-8")


# --------------------------------------------------------------------------- #
# MinIO 图片外链
# --------------------------------------------------------------------------- #


def _expand_env(value: Any, env_name: str = "") -> Any:
    """展开配置值里的 ``${VAR}`` / ``${VAR:default}`` 占位符。

    敏感凭据不在 ``config.yaml`` 里落明文，改由环境变量注入：yaml 只写占位符
    （如 ``secret-key: ${MINIO_SECRET_KEY}``），真实值从进程环境读取。
    ``${VAR}`` 未设置时解析为空串，``_minio_settings`` 会因缺项而降级为占位图片。
    """
    if not isinstance(value, str):
        return value
    pattern = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}")
    return pattern.sub(
        lambda match: os.getenv(match.group(1), match.group(2) if match.group(2) is not None else ""),
        value,
    )


def _minio_settings() -> dict[str, Any]:
    """MinIO 配置：先读 ``config.yaml`` 的 ``minio`` 段，再被环境变量覆盖。

    yaml 中的 ``access-key`` 与 ``access_key`` 两种写法等价；值为 ``${VAR}`` 时按
    环境变量展开，因此凭据无需写入配置文件。
    """
    settings: dict[str, Any] = {}
    if _CONFIG_PATH.exists():
        data = yaml.safe_load(_CONFIG_PATH.read_text(encoding="utf-8")) or {}
        section = data.get("minio")
        if isinstance(section, dict):
            settings = {
                str(key).replace("-", "_"): _expand_env(value, str(key))
                for key, value in section.items()
            }

    for key, env_name in (
        ("endpoint", "MINIO_ENDPOINT"),
        ("access_key", "MINIO_ACCESS_KEY"),
        ("secret_key", "MINIO_SECRET_KEY"),
        ("bucket", "MINIO_BUCKET"),
        ("public_base_url", "MINIO_PUBLIC_BASE_URL"),
        ("presign_expires", "MINIO_PRESIGN_EXPIRES"),
    ):
        value = os.getenv(env_name)
        if value:
            settings[key] = value
    return settings


# 资产名（内容哈希）-> 可访问 URL，跨调用复用，避免同一图片重复上传
_UPLOADED: dict[str, str] = {}


def _as_bool(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in ("1", "true", "yes", "on")


def _public_read_policy(bucket: str) -> str:
    """允许匿名 GET 桶内对象，否则 img 直链会 403。"""
    return json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": [f"arn:aws:s3:::{bucket}/*"],
                }
            ],
        }
    )


def _build_resolver(image_mode: str) -> Optional[Callable[[ImageAsset], Optional[str]]]:
    """按 ``image_mode`` 构造图片解析器。

    - ``placeholder``：返回 ``None``，渲染器输出占位 ``<img data-embed>``；
    - ``inline``：返回 data URI，自包含但体积大；
    - ``url``：上传 MinIO 后返回直链 / 预签名 URL；凭据缺失或上传失败时，
      由渲染器自动降级为占位。
    """
    if image_mode == "placeholder":
        return None

    if image_mode == "inline":

        def inline(asset: ImageAsset) -> str:
            data = base64.b64encode(asset.blob).decode("ascii")
            return f"data:{asset.mime};base64,{data}"

        return inline

    settings = _minio_settings()
    endpoint = str(settings.get("endpoint") or "").strip()
    access_key = str(settings.get("access_key") or "").strip()
    secret_key = str(settings.get("secret_key") or "").strip()
    bucket = str(settings.get("bucket") or "").strip()
    if not (endpoint and access_key and secret_key and bucket):
        # 凭据改为环境变量注入后，缺失不再静默：显式提示需要设置哪些变量
        missing = [
            name
            for name, value in (
                ("MINIO_ENDPOINT", endpoint),
                ("MINIO_ACCESS_KEY", access_key),
                ("MINIO_SECRET_KEY", secret_key),
                ("MINIO_BUCKET", bucket),
            )
            if not value
        ]
        print(
            f"[docx-mcp] 警告：image_mode=url 但 MinIO 配置不完整，"
            f"缺少 {', '.join(missing)}；本次图片将降级为占位 <img>。",
            file=sys.stderr,
        )
        return None

    base_url = str(settings.get("public_base_url") or "").strip() or endpoint.rstrip("/")
    try:
        expires = int(settings.get("presign_expires") or 0)
    except (TypeError, ValueError):
        expires = 0
    # 需要匿名直链时（img 由浏览器 / html_to_docx 直接拉取）给桶挂只读策略
    public_read = _as_bool(settings.get("public_read"), default=expires <= 0)

    client = Minio(
        endpoint.split("://", 1)[-1],
        access_key=access_key,
        secret_key=secret_key,
        secure=endpoint.startswith("https://"),
    )
    # 进程级缓存：同名（内容寻址）图片只上传一次；一旦失败则本进程内不再重试，
    # 避免每张图都去等一次网络超时。
    state = {"bucket_ready": False, "broken": False}

    def resolve(asset: ImageAsset) -> Optional[str]:
        if state["broken"]:
            return None
        cached = _UPLOADED.get(asset.name)
        if cached:
            return cached
        try:
            if not state["bucket_ready"]:
                if not client.bucket_exists(bucket):
                    client.make_bucket(bucket)
                if public_read:
                    try:
                        client.set_bucket_policy(bucket, _public_read_policy(bucket))
                    except Exception:  # noqa: BLE001 - 无权限改策略时不影响上传
                        pass
                state["bucket_ready"] = True

            object_name = f"docx/{asset.name}"
            client.put_object(
                bucket,
                object_name,
                io.BytesIO(asset.blob),
                length=asset.size,
                content_type=asset.mime,
            )
            if expires > 0:
                url = client.presigned_get_object(
                    bucket, object_name, expires=timedelta(seconds=expires)
                )
            else:
                url = f"{base_url.rstrip('/')}/{bucket}/{object_name}"
        except Exception:  # noqa: BLE001 - 单张图上传失败不影响整篇渲染
            state["broken"] = True
            return None
        _UPLOADED[asset.name] = url
        return url

    return resolve


# --------------------------------------------------------------------------- #
# MCP 工具
# --------------------------------------------------------------------------- #


@mcp.tool()
def docx_to_html(
    source: str,
    image_mode: Literal["url", "inline", "placeholder"] = "url",
    output_path: Optional[str] = None,
    as_handle: bool = False,
) -> str:
    """
    把 docx 文档渲染为与前端 docx-preview 同构的 docx-wrapper HTML。

    功能说明：
    - 输出结构固定为 div.docx-wrapper > section.docx > article，
      内部按文档顺序排列 p（段落）与 table（表格），格式全部以内联 CSS 表达；
    - 支持段落对齐 / 缩进 / 行距 / 段前段后距、行内字体字号加粗下划线颜色、
      表格列宽 / 边框 / 内边距 / 垂直对齐、嵌套表格、colspan 横向合并与
      rowspan 纵向合并（被合并占用的延续格输出 display: none 占位）；
    - 编号段落输出 class="docx-num-{level}-{numId}"，与 docx-preview 约定一致；
    - 内嵌图片按 image_mode 处理：url（上传 MinIO 后输出短链接，默认）、
      inline（base64 data URI，自包含但体积大）、
      placeholder（输出占位 <img data-embed> 并保留尺寸，体积最小）。

    用途：从模板 docx 中提取"结构"，交给 AI 按需求改写后，再用 html_to_docx
    把改写后的 HTML 变回文档（模板 → HTML → 改写 → 文档）。

    参数:
        source: docx 文档来源，支持本地文件绝对路径（例如
            "D:/file/template.docx"），也支持 http/https 直链（例如 MinIO
            返回的可访问 URL）。
        image_mode: 图片输出形式，"url"（默认）| "inline" | "placeholder"。
            url 会先把图片上传 MinIO 再输出短链接，HTML 体积最小、适合回传；
            inline 输出 base64（自包含但体积大）；
            placeholder 只输出 <img data-embed="..." data-width/height> 占位。
            url 模式下若 MinIO 未配置或上传失败，自动降级为占位。
        output_path: 不传则直接返回 HTML 文本；传了则把 HTML 落盘到该路径，
            只返回 JSON（含 html_path / stats），便于后续用编辑工具就地改写、
            再把路径交给 html_to_docx。文档较长时强烈建议使用这一模式。
        as_handle: 传 True 时把 HTML 托管在 MCP 进程内并只返回 ``html_handle``，
            后续直接把该 handle 传给 html_to_docx 的 ``html`` 参数即可。
            **不需要自己落盘、也不需要事后清理临时文件**（output_path 优先）。

    返回:
        str: 未传 output_path / as_handle 时返回 docx-wrapper 的 HTML 文本；
        传了 output_path 时返回 JSON（html_path / image_mode / stats）；
        传了 as_handle 时返回 JSON（html_handle / image_mode / stats）。
        失败时返回带 data-error 属性的空 wrapper，例如
        <div class="docx-wrapper" data-error="..."></div>。

    注意:
        - 页眉页脚中的图形、域代码、批注等不在本轮渲染范围内；
        - MinIO 配置读取顺序：环境变量 > 项目根目录 config.yaml 的 minio 段。
    """
    temp_path: Path | None = None
    try:
        path, temp_path = _resolve_source(source)
        result = render_docx(path, asset_resolver=_build_resolver(image_mode))
        if output_path:
            target = Path(output_path)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(result.html, encoding="utf-8")
            return json.dumps(
                {
                    "html_path": str(target.resolve()),
                    "image_mode": image_mode,
                    "stats": {
                        **result.stats,
                        "html_bytes": len(result.html.encode("utf-8")),
                    },
                },
                ensure_ascii=False,
                indent=2,
            )
        if as_handle:
            return json.dumps(
                {
                    "html_handle": _register_html(result.html),
                    "image_mode": image_mode,
                    "stats": {
                        **result.stats,
                        "html_bytes": len(result.html.encode("utf-8")),
                    },
                    "hint": "把 html_handle 直接传给 html_to_docx 的 html 参数，无需落盘与清理。",
                },
                ensure_ascii=False,
                indent=2,
            )
        return result.html
    except Exception as e:
        return f'<div class="docx-wrapper" data-error="{html.escape(str(e), quote=True)}"></div>'
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


@mcp.tool()
def html_to_docx(
    html: str,
    output_path: str,
    replacements: Optional[list[dict[str, Any]]] = None,
    template: Optional[str] = None,
    replace_order: Literal["longest-first", "as-given"] = "longest-first",
    replace_mode: Literal["single", "loop"] = "single",
) -> str:
    """
    把 docx-wrapper HTML 还原为 .docx 文档（与 docx_to_html 互为逆过程）。

    用途：接收 AI 改写后的 HTML，生成最终文档。多次调用即可生成多个文档
    （每次指定不同的 output_path）。

    映射范围：
    - 页面：section 的 padding → 页边距，width / height → 纸张尺寸；
    - 段落：text-align / margin-top / margin-bottom / line-height /
      text-indent / margin-inline-start；
    - 字符：font-family / font-size / font-weight / font-style /
      text-decoration / color / vertical-align；
    - 表格：colgroup 列宽、tr 行高、td 宽度 / 边框 / 内边距 / 垂直对齐、
      colspan 横向合并、rowspan 纵向合并（延续格可省略，缺格自动补全）；
    - 编号：class="docx-num-{level}-{numId}" → 多级十进制编号；
    - 图片：http(s) 链接与 data: URI 会被下载/解码后内嵌，仅有 data-embed
      的占位图会被跳过。

    参数:
        html: docx-wrapper HTML。四种传法皆可：
            ① 直接传 HTML 文本（以 "<" 开头）；
            ② 传本地文件路径（推荐：先用 docx_to_html 的 output_path 落盘，
               再用编辑工具就地改写，最后把路径交给本工具）；
            ③ 传 http/https 直链；
            ④ 传 docx_to_html 返回的 html_handle（进程内托管，无需落盘与清理）。
        output_path: 目标 .docx 的绝对路径，父目录会自动创建。
        replacements: 可选，批量文本改写规则，形如
            [{"old": "第3章 关系数据库标准语言SQL", "new": "第3章 系统分析"}]。
            在生成文档前对 HTML 做字面替换（只改文本、不动标签与内联样式，
            格式因此完全保留）。文档较长时用它替代"回传整篇 HTML"，可避免
            数十 KB 的内容在工具参数里往返。old 会替换所有出现位置。
            单条规则可用 "mode": "single" | "loop" 覆盖默认语义。
            **若同一文本出现在多个语义不同的位置、需要分别改写，请改用
            docx_replace（带 scope），本参数做不到按位置区分。**
        replace_order: "longest-first"（默认）按 old 长度降序执行，避免短串先
            吞掉长串（如「迁移用户数据库」吃掉「3.3.8 迁移用户数据库」）；
            "as-given" 保持传入顺序。
        replace_mode: "single"（默认）每个匹配位置只替换一次，天然幂等——
            即便 new 包含 old 也不会把文本翻倍；"loop" 反复替换直到不再变化，
            用于 "--" → "-" 这类收敛式改写。
        template: **强烈建议传入**模板 docx（本地路径或 http(s) 直链，通常就是
            docx_to_html 的那个 source）。传入后以模板为基底：正文清空后按 HTML
            重建，而 styles.xml / settings.xml / numbering.xml / fontTable.xml /
            页面设置等全部继承模板。这些设置 Word/WPS 会参与排版（标点压缩、
            兼容模式、默认制表位、东亚换行规则…），HTML 表达不了，只能从模板带。
            不传则退回空白文档，WPS 下版式可能与模板有偏差。

    返回:
        str: JSON 文本，字段包括
        - docx_path: 落盘后的 .docx 绝对路径；
        - bytes: 文件字节数；
        - stats: paragraphs / tables / images 统计；
        - replacements: 结构化变更报告
            {applied: 命中条数, missed: [未命中的 old],
             results: [{old, new, mode, matched, missed, hits:[{kind,
               paragraphIndex, before, hit, after}]}],
             warnings: [顺序/包含关系提示], order, mode}
          —— **务必检查 missed**，非空说明该条改写没生效（原文已变或不连续）；
        - error: 失败时的错误信息（成功时不存在）。

    注意:
        - 只识别 article 内的直接子块 p / table，其余标签忽略；
        - 不支持的 CSS 属性会被静默忽略，不会中断生成。
    """
    temp_path: Path | None = None
    try:
        html_text, replacement_report = apply_html_replacements(
            _load_html(html),
            replacements,
            order=replace_order,
            mode=replace_mode,
        )
        template_path = None
        if template:
            template_path, temp_path = _resolve_source(template)
        result = render_html_to_docx(html_text, output_path, template=template_path)
        result["replacements"] = replacement_report
        return json.dumps(result, ensure_ascii=False, indent=2)
    except Exception as e:
        return json.dumps({"error": str(e)}, ensure_ascii=False)
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


# --------------------------------------------------------------------------- #
# 文档理解：骨架 / 检索 / 定位读取
# --------------------------------------------------------------------------- #


@mcp.tool()
def docx_open(source: str) -> str:
    """
    打开文档，返回会话句柄 docId + 文档骨架（结构统计 / 页面设置 / 标题 / 封面字段 / 表格概览）。

    功能说明：
    - 登记会话句柄后，后续所有 docx_* 工具的 source 都可以只传这个 docId：
      不必重复传路径，http 直链也只下载一次；文件会被托管到 MCP 自己的缓存目录，
      调用方不需要再用 delete_file 清理中间产物；
    - 一次拿到「这篇文档长什么样」：段落/表格/单元格数量、纸张与页边距、
      疑似标题（第X章 / 多级编号）、疑似封面字段（"任课教师：" 这类标签行）、
      每张表的行列数与首行/首列，方便后续定位。

    参数:
        source: docx 来源——本地绝对路径或 http(s) 直链。

    返回:
        str: JSON 文本，含 docId / path / structure / sections / headings /
        labels / tables。

    注意:
        原先「找主题在哪、文档全貌是什么」只能靠 PowerShell 正则 + 手工剥标签，
        这一步是那次任务的第 1 步，现在一次调用即可。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        persisted = _persist(path)
        outline = edit.describe_outline(model)
        return {"docId": _register_doc(persisted), "path": persisted, **outline}

    return _run(source, handler)


@mcp.tool()
def docx_search(
    source: str,
    pattern: str,
    regex: bool = False,
    ignore_case: bool = False,
    scope: Optional[dict[str, Any]] = None,
    context_chars: int = 40,
    limit: int = 0,
) -> str:
    """
    在文档内检索，返回每处命中的**位置 + 上下文**。

    功能说明：
    - 命中自带结构信息：part（body/table）、paragraphIndex、tableIndex、
      row、col、charOffset，以及前后 context_chars 个字符的上下文；
    - 自动跨 run 匹配：Word 常把「第3章 关系数据库标准语言SQL」切成三个 run，
      这里按整段文本匹配，命中仍然有效；
    - scope 可把检索限制到某一段/某个表格/某个单元格。

    参数:
        source: docx 来源——docId（docx_open 返回）/ 本地路径 / http(s) 直链。
        pattern: 要查找的文本；regex=True 时为正则表达式。
        regex: 是否把 pattern 当正则（默认 False，按字面量查找）。
        ignore_case: 是否忽略大小写。
        scope: 可选检索范围，例如 {"paragraph": 151}、{"table": 3}、
            {"table": 3, "row": 2, "cell": 1}、{"part": "body"}、
            {"location": "p:12"}、{"after": "难点："}、{"before": "备注"}。
        context_chars: 上下文前后截取的字符数（默认 40）。
        limit: 最多返回多少条命中，0 表示不限。

    返回:
        str: JSON 文本，含 pattern / count / truncated / hits（每条命中带
        位置与上下文）。

    用途:
        本次任务的第一步本该是这个——原先要写 PowerShell 正则 + IndexOf 打印
        ±400 字上下文才能定位，现在一次调用拿到全部命中位置。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.search(
            model,
            pattern,
            regex=regex,
            ignore_case=ignore_case,
            scope=scope,
            context_chars=context_chars,
            limit=limit,
        )
        result["source"] = path
        return result

    return _run(source, handler)


@mcp.tool()
def docx_get_text(
    source: str,
    page: Optional[int] = None,
    page_size: int = 50,
    start: int = 0,
    end: Optional[int] = None,
    with_index: bool = True,
    with_table_grid: bool = True,
) -> str:
    """
    带稳定索引的纯文本导出（正文段落 + 表格单元格路径），支持分页。

    功能说明：
    - 每个块都带 location：段落是 "p:151"，表格是 "t:3"，单元格是
      "t:3/r:2/c:1"，可以直接喂给 docx_replace 的 scope 或 docx_get_runs；
    - 表格以逻辑网格输出（grid），合并单元格体现在 rowspan/colspan 上；
    - 分页避免一次吐出整篇文档：先 page=1 看前 page_size 块，再翻页。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        page: 页码（1 起）。传了就按 page_size 切片，忽略 start/end。
        page_size: 每页块数（默认 50）。
        start / end: 不传 page 时按块区间切片。
        with_index: 是否返回 blockIndex（默认 True）。
        with_table_grid: 表格是否附带 grid 二维文本（默认 True）。

    返回:
        str: JSON 文本，含 totalBlocks / page / pages / structure / blocks。

    用途:
        取代「整份 HTML 落盘 → 按 </p> 切开 → 剥标签 → 手工编号」的做法：
        既省 token，也不会在中途被截断。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.get_text(
            model,
            start=start,
            end=end,
            page=page,
            page_size=page_size,
            with_index=with_index,
            with_table_grid=with_table_grid,
        )
        result["source"] = path
        return result

    return _run(source, handler)


@mcp.tool()
def docx_get_runs(source: str, location: str) -> str:
    """
    返回某段落的 run 边界：被切成了几段文本、各自的格式、以及跨 run 匹配说明。

    功能说明：
    - Word 常把同一句话按字体/修订切成多个 run，决定「old 串能不能命中」；
    - 这里显式给出 splitAcrossRuns 与每个 run 的 charStart / text / rPr，
      并明确 matchAcrossRuns=true —— 跨 run 匹配是 docx_replace / docx_search
      的默认行为，不再需要事先探测。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        location: 段落路径，如 "p:151"、"t:3/r:2/c:1/p:0"，或直接给段号 "151"。

    返回:
        str: JSON 文本，含 text / runCount / splitAcrossRuns / matchAcrossRuns /
        runs。

    用途:
        原先要写 PowerShell 按 <[^>]*> 切 text node 数 run 数，现在一次调用。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.get_runs(model, location)
        result["source"] = path
        return result

    return _run(source, handler)


@mcp.tool()
def docx_get_tables(source: str) -> str:
    """
    表格结构查询：每张表的行列数、合并单元格、单元格路径 → 文本。

    功能说明：
    - 行列号是**逻辑网格**坐标（合并单元格以左上角参与寻址），
      rowspan / colspan 直接给出；
    - 每个单元格返回可直接用于 docx_set_cell / docx_replace 的 location
      （形如 "t:0/r:2/c:1"）以及单元格内段落的 location。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。

    返回:
        str: JSON 文本，含 count / tables[]（grid、cells[]）。

    用途:
        本次任务里 4 张表（课前预习 / 课中实施 / 课后拓展 / 重点难点）的行列
        对应关系基本靠推理，风险很高；现在可以先查再改。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.get_tables(model)
        result["source"] = path
        return result

    return _run(source, handler)


# --------------------------------------------------------------------------- #
# 定位编辑：替换 / 增删 / 填字段 / 比对 / 校验
# --------------------------------------------------------------------------- #


@mcp.tool()
def docx_replace(
    source: str,
    rules: list[dict[str, Any]],
    output_path: Optional[str] = None,
    order: Literal["longest-first", "as-given"] = "longest-first",
    dry_run: bool = False,
) -> str:
    """
    带作用域的批量替换，直接改 docx 内容（不走 HTML 往返），返回结构化变更报告。

    功能说明：
    - **只改 w:t 文本**，不动 w:pPr / w:rPr，字体字号缩进编号与模板完全一致；
    - **自动跨 run**：old 可以横跨多个 run，命中后新文本写入起始 run 并保留其格式；
    - **scope 按位置限定**（原实现最大的短板）：同一段文本出现在多个语义不同的
      位置时可以分别改写，不必再为「数据库的创建和使用」这类重复文本做统一文案的妥协；
    - **dryRun 先看命中位置**：确认每条规则命中几处、分别在哪，再落盘；
    - **单次字面替换**：每个匹配位置只替换一次，new 包含 old 也不会文本翻倍；
    - **order="longest-first"**：自动把长串排在短串之前，不必人脑排序；
    - 未命中的规则会附带**最相近段落**作为建议位置。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        rules: 替换规则列表，每条形如
            {"old": "旧文本", "new": "新文本", "scope": {...},
             "regex": false, "ignore_case": false, "mode": "single"}
          其中 scope 支持（可组合）：
            {"paragraph": 151}               只在该段号内替换
            {"location": "p:151"}            同上，直接给路径
            {"table": 3}                     只在该表格内替换
            {"table": 3, "row": 2}           限定到行
            {"table": 3, "row": 2, "cell": 1} 限定到单元格
            {"part": "body" | "table"}       只改正文 / 只改表格
            {"after": "难点："}              只改该锚点之后的那一处
            {"before": "备注"}               只改该锚点之前的那一处
            {"occurrence": 2}                只改全局第 2 次出现
          mode: "single"（默认，幂等）| "loop"（反复替换直到收敛）。
        output_path: 目标路径；不传则就地写回 source（源是 http 直链时必填）。
        order: "longest-first"（默认）| "as-given"。
        dry_run: True 只报告命中位置、不写盘。**强烈建议先跑一次 dryRun。**

    返回:
        str: JSON 文本，含 source / dryRun / order / appliedRules /
        missedRules / results（每条规则的 occurrences 带 location、charOffset、
        before/hit/after）/ warnings / outputPath。

    典型用法:
        1) docx_replace(source, rules, dry_run=True) 先看每条规则命中在哪；
        2) 确认无歧义后 docx_replace(source, rules, output_path="...") 落盘。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        report = edit.replace_rules(model, rules, order=order, dry_run=dry_run)
        report["source"] = path
        if dry_run:
            report["outputPath"] = None
            report["hint"] = "这是 dryRun 结果，未写盘；确认命中位置后传 dry_run=false 重跑即可。"
        else:
            target = _writable_target(source, output_path, path)
            edit.save_document(document, target)
            report["outputPath"] = str(Path(target).resolve())
        return report

    return _run(source, handler)


@mcp.tool()
def docx_diff(
    path_a: str,
    path_b: str,
    level: Literal["text", "format"] = "format",
) -> str:
    """
    比对两份文档，用来证明「只改了文字、版式零变化」。

    功能说明：
    - level="text"：只比文本，返回新增 / 删除 / 改写段落（带双侧 location）；
    - level="format"：额外比段落数、表格数、单元格数、页面设置
      （纸张 / 页边距 / 分栏 / 文档网格）与逐段格式签名
      （对齐 / 缩进 / 行距 / 编号 / 字体字号加粗…）；
    - identical=true 表示文本与版式完全一致。

    参数:
        path_a: 参照文档（改前）——路径 / docId / http(s) 直链。
        path_b: 目标文档（改后）——同上。
        level: "text" | "format"（默认 "format"）。

    返回:
        str: JSON 文本，含 identical / summary / changes / structure；
        level="format" 时另有 format（formatChanged、pageSetupEqual、sections）。

    用途:
        原先只能靠人工比对两份文本 + html_bytes 差异来间接确认版式没变。
    """
    temp_a: Path | None = None
    temp_b: Path | None = None
    try:
        real_a, temp_a = _resolve_source(path_a)
        real_b, temp_b = _resolve_source(path_b)
        return json.dumps(
            edit.diff_documents(real_a, real_b, level=level), ensure_ascii=False, indent=2
        )
    except Exception as exc:  # noqa: BLE001 - 统一转成结构化错误
        return json.dumps({"error": str(exc)}, ensure_ascii=False)
    finally:
        for temp in (temp_a, temp_b):
            if temp is not None:
                temp.unlink(missing_ok=True)


@mcp.tool()
def docx_set_cell(
    source: str,
    text: str,
    table: Optional[int] = None,
    row: Optional[int] = None,
    col: Optional[int] = None,
    location: Optional[str] = None,
    append: bool = False,
    output_path: Optional[str] = None,
) -> str:
    """
    写入单元格文本（保留该单元格原有格式），用于填封面空字段之外的表格内容。

    功能说明：
    - 单元格定位二选一：location="t:0/r:2/c:1"，或 table/row/col（都是**逻辑
      网格**坐标，列号可用 col 或 cell）；
    - replace（默认）会清空该单元格原有段落、只保留首段格式后写入；
      text 里的 \\n 会拆成多个段落，并继承段落属性；
    - append=True 则在单元格末尾追加而不清空。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        text: 要写入的文本（支持 \\n 多段）。
        table / row / col: 表格序号与逻辑行列号（0 起）。
        location: 单元格路径，如 "t:0/r:2/c:1"（优先于 table/row/col）。
        append: 追加而非覆盖。
        output_path: 目标路径；不传则就地写回 source。

    返回:
        str: JSON 文本，含 location / mode / lines / text / outputPath。

    提示:
        先调 docx_get_tables 拿到准确的行列号，避免靠推理定位。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        cell = model.cell_at(location) if location else None
        if cell is None and table is not None and row is not None and col is not None:
            cell = model.find_cell(int(table), int(row), int(col))
        if cell is None:
            raise ValueError(
                "找不到目标单元格：请传 location（如 t:0/r:1/c:2）"
                "或同时传 table/row/col（逻辑行列号，0 起）"
            )
        result = edit.set_cell_text(model, cell, text, append=append)
        target = _writable_target(source, output_path, path)
        edit.save_document(document, target)
        return {**result, "outputPath": str(Path(target).resolve())}

    return _run(source, handler)


@mcp.tool()
def docx_insert_paragraph(
    source: str,
    text: str,
    after: Optional[str] = None,
    before: Optional[str] = None,
    style_from: Optional[str] = None,
    output_path: Optional[str] = None,
) -> str:
    """
    插入段落（可继承某个段落的格式），text 里的 \\n 拆成多段。

    功能说明：
    - after / before 指定锚点段落（"p:12" 或段号 "12"），都不传则追加到文末；
    - style_from 指定格式来源段落；不传时以锚点段落自身为模板，因此新段落的
      对齐、缩进、行距、字体字号与上下文一致。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        text: 段落文本（支持 \\n 多段）。
        after / before: 锚点段落路径。
        style_from: 格式来源段落路径。
        output_path: 目标路径；不传则就地写回 source。

    返回:
        str: JSON 文本，含 inserted / position / after / before / styleFrom。

    注意:
        插入会改变文档结构，若你持有旧的段落索引请重新调用 docx_get_text。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.insert_paragraph(
            model, text=text, after=after, before=before, style_from=style_from
        )
        target = _writable_target(source, output_path, path)
        edit.save_document(document, target)
        return {**result, "outputPath": str(Path(target).resolve())}

    return _run(source, handler)


@mcp.tool()
def docx_delete_paragraph(
    source: str,
    location: str,
    output_path: Optional[str] = None,
) -> str:
    """
    删除一个段落（例如去掉重复的「难度」行）。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        location: 段落路径，如 "p:151"、"p:151" 或段号 "151"。
        output_path: 目标路径；不传则就地写回 source。

    返回:
        str: JSON 文本，含 deleted / text / outputPath。

    注意:
        想删的若是表格单元格内的段落，请传完整路径
        （如 "t:0/r:2/c:1/p:0"）。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.delete_paragraph(model, location)
        target = _writable_target(source, output_path, path)
        edit.save_document(document, target)
        return {**result, "outputPath": str(Path(target).resolve())}

    return _run(source, handler)


@mcp.tool()
def docx_fill_fields(
    source: str,
    fields: dict[str, str],
    output_path: Optional[str] = None,
    overwrite: bool = False,
) -> str:
    """
    填充封面 / 表单字段（任课教师、课程名称、章节名称、面向专业…）。

    功能说明（按顺序尝试两种模式）：
    1. **占位符**：命中 ``{{课程名称}}`` / ``${课程名称}`` / ``【课程名称】``
       时直接替换；
    2. **标签**：找到「课程名称：」后冒号起的内容，为空（或 overwrite=True）
       时把新值写进去。

    比「先转 HTML 再全局字符串替换」稳得多：只在冒号之后落字，不会误伤标题、
    正文里同名的词；写回时保留原段落与 run 的格式。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        fields: 字段字典，如 {"任课教师": "张三", "课程名称": "软件工程"}。
        output_path: 目标路径；不传则就地写回 source。
        overwrite: True 时覆盖已有内容；默认 False 只填空字段。

    返回:
        str: JSON 文本，含 filled（每条带 mode 与 location）/ skipped
        （已有内容而跳过）/ missed（既无占位符也无标签）/ outputPath。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.fill_fields(model, fields, overwrite=overwrite)
        target = _writable_target(source, output_path, path)
        edit.save_document(document, target)
        return {**result, "outputPath": str(Path(target).resolve())}

    return _run(source, handler)


@mcp.tool()
def docx_validate(
    source: str,
    required_keywords: Optional[list[str]] = None,
    forbidden_keywords: Optional[list[str]] = None,
) -> str:
    """
    文档体检：占位符残留、空单元格、关键词计数、页面设置完整性。

    功能说明：
    - 扫描 _____ / XXX / {{}} / 【】 / 待填 等占位符残留，带 location；
    - 统计空单元格（合并单元格按左上角坐标报告）；
    - 统计 required_keywords 的出现次数并列出计数为 0 的（缺项），
      forbidden_keywords 仍存在的也一并报告（残留）；
    - 检查纸张 / 页边距 / 分栏 / 文档网格是否完整。

    参数:
        source: docx 来源——docId / 本地路径 / http(s) 直链。
        required_keywords: 必须出现的关键词（例如原稿里的「系统分析」）。
        forbidden_keywords: 必须消失的关键词（例如原稿里的「SQL」）。

    返回:
        str: JSON 文本，含 ok / structure / placeholders / emptyCells /
        keywordCounts / missingKeywords / forbiddenKeywordCounts /
        pageSetupOk / sections。

    用途:
        原先「SQL / 数据库 计数为 0」是自建 PowerShell 脚本算的，现在内置。
    """

    def handler(document: Any, model: Any, path: str) -> dict[str, Any]:
        result = edit.validate_document(
            model,
            required_keywords=required_keywords,
            forbidden_keywords=forbidden_keywords,
        )
        result["source"] = path
        return result

    return _run(source, handler)


@mcp.tool()
def test() -> str:
    """
    这是个mcp测试函数
    """
    try:

        return "success"
    except Exception as e:
        return f"Error: {e}"


if __name__ == "__main__":
    # 运行服务器
    mcp.run()
