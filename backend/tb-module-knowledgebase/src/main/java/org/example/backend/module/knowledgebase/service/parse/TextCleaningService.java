package org.example.backend.module.knowledgebase.service.parse;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextCleaningService {

    // ========== 预编译正则表达式（性能优化）==========

    /**
     * 图片文件名行：image123.png
     * 整行匹配，防止误删正文中的文件名字符串
     */
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?m)^image\\d+\\.(png|jpe?g|gif|bmp|webp)\\s*$");

    /**
     * HTTP/HTTPS 图片链接
     * 支持 URL 查询参数，大小写不敏感
     */
    private static final Pattern IMAGE_URL =
            Pattern.compile("https?://\\S+?\\.(png|jpe?g|gif|bmp|webp)(\\?\\S*)?", Pattern.CASE_INSENSITIVE);

    /**
     * 文件协议 URL（Tika PDF 临时文件路径等）
     */
    private static final Pattern FILE_URL =
            Pattern.compile("file:(//)?\\S+", Pattern.CASE_INSENSITIVE);

    /**
     * 分隔线：---, ___, ***, ===
     * 整行匹配，至少 3 个连续符号
     */
    private static final Pattern SEPARATOR_LINE =
            Pattern.compile("(?m)^\\s*[-_*=]{3,}\\s*$");

    /**
     * 控制字符（不可见字符）
     * 保留换行符 \n (0x0A) 和制表符 \t (0x09)
     */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    /**
     * HTML 标签（排除表格相关标签）
     * 保留：table, tr, td, th, tbody, thead, tfoot, caption
     */
    private static final Pattern HTML_TAGS_EXCEPT_TABLE =
            Pattern.compile(
                    "<(?!\\/?(?:table|tbody|tr|th|td|thead|tfoot|caption|h1|h2|h3|p)\\b)[^>]*>",
                    Pattern.CASE_INSENSITIVE
            );



    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String t = text;

        // ========== 第一层：语义去噪 ==========
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = IMAGE_FILENAME_LINE.matcher(t).replaceAll("");
        t = IMAGE_URL.matcher(t).replaceAll("");
        t = FILE_URL.matcher(t).replaceAll("");
        t = SEPARATOR_LINE.matcher(t).replaceAll("");

        // ========== 第二层：格式规范化 ==========
        // 统一换行符
        t = t.replace("\r\n", "\n").replace("\r", "\n");

        // 去掉行尾空格和制表符，保留空行（保持段落结构）
        t = t.replaceAll("(?m)[ \t]+$", "");

        // 压缩连续空行：最多保留 2 个换行符（即一个空行）
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.strip();
    }

    public String cleanHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text;
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = IMAGE_FILENAME_LINE.matcher(t).replaceAll("");
        t = IMAGE_URL.matcher(t).replaceAll("");
        t = FILE_URL.matcher(t).replaceAll("");
        t = SEPARATOR_LINE.matcher(t).replaceAll("");
        t = t.replace("\r\n", "\n").replace("\r", "\n");

        // 去掉行尾空格和制表符，保留空行（保持段落结构）
        t = t.replaceAll("(?m)[ \t]+$", "");

        // 压缩连续空行：最多保留 2 个换行符（即一个空行）
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.strip();
    }

    public String stripHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text;
        t = HTML_TAGS_EXCEPT_TABLE.matcher(t).replaceAll(" ");
        return t;
    }

    public static List<String> splitHtmlByTag(String htmlContent) {
        List<String> result = new ArrayList<>();

        // 去除首尾空白
        htmlContent = htmlContent.trim();

        // 如果内容为空，返回空列表
        if (htmlContent.isEmpty()) {
            return result;
        }

        // 先处理表格：提取完整的table标签及其内容
        Pattern tablePattern = Pattern.compile("<table[^>]*>.*?</table>", Pattern.DOTALL);
        Matcher tableMatcher = tablePattern.matcher(htmlContent);

        // 记录已处理的位置
        int lastEnd = 0;
        Map<Integer, String> tableMap = new LinkedHashMap<>();

        while (tableMatcher.find()) {
            String tableTag = tableMatcher.group();
            int start = tableMatcher.start();
            int end = tableMatcher.end();
            tableMap.put(start, tableTag);

            // 将表格标记替换为占位符，方便后续处理
            // 这里不直接替换，而是用标记位置来分割
        }

        // 使用栈来手动解析，处理嵌套标签
        // 由于表格已处理，剩余内容主要是独立的标签和文本
        // 使用简单的扫描方式，按标签开始和结束来分割

        // 方案：使用正则匹配每个完整的标签（包括自闭合标签）
        Pattern tagPattern = Pattern.compile(
                "<[a-zA-Z][^>]*>.*?</[a-zA-Z][^>]*>|<[a-zA-Z][^>]*/>|</[a-zA-Z][^>]*>",
                Pattern.DOTALL
        );

        // 由于上面的正则可能无法完美匹配复杂嵌套，改用更稳健的方式：
        // 遍历HTML，使用栈来识别标签边界

        result = parseHtmlToTagList(htmlContent);

        return result;
    }

    /**
     * 使用栈解析HTML，将每个标签及其内容作为一个元素
     */
    private static List<String> parseHtmlToTagList(String html) {
        List<String> result = new ArrayList<>();
        int length = html.length();
        int i = 0;

        while (i < length) {
            char c = html.charAt(i);

            // 跳过空白字符（但保留标签内的空白）
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }

            // 找到下一个标签的开始
            int nextTagStart = html.indexOf('<', i);

            // 如果没有更多标签，跳出
            if (nextTagStart == -1) {
                break;
            }

            // 如果当前位置不是标签开始，说明有文本内容，跳过
            if (nextTagStart > i) {
                // 这里不处理纯文本，因为格式要求每个标签为一个元素
                i = nextTagStart;
                continue;
            }

            // 解析完整的标签（从<到对应的>）
            int tagEnd = findTagEnd(html, nextTagStart);
            if (tagEnd == -1) {
                // 如果找不到对应的结束，跳过
                i++;
                continue;
            }

            String tagContent = html.substring(nextTagStart, tagEnd + 1);

            // 检查是否以<table开头
            if (tagContent.toLowerCase().startsWith("<table")) {
                // 找对应的</table>
                int tableEnd = findMatchingClosingTag(html, nextTagStart, "table");
                if (tableEnd != -1) {
                    String tableFull = html.substring(nextTagStart, tableEnd + 1);
                    result.add(tableFull);
                    i = tableEnd + 1;
                    continue;
                }
            }

            // 检查是否以<h1>等标题标签开头
            Pattern openTagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)[^>]*>");
            Matcher matcher = openTagPattern.matcher(tagContent);

            if (matcher.find()) {
                String tagName = matcher.group(1).toLowerCase();

                // 如果是自闭合标签
                if (tagContent.endsWith("/>")) {
                    result.add(tagContent);
                    i = tagEnd + 1;
                    continue;
                }

                // 对于h1, h2, h3, p等标签，提取完整的标签内容
                if (isContainerTag(tagName)) {
                    int closeTagStart = html.indexOf("</" + tagName + ">", nextTagStart + 1);
                    if (closeTagStart != -1) {
                        int closeTagEnd = html.indexOf(">", closeTagStart);
                        if (closeTagEnd != -1) {
                            String fullTag = html.substring(nextTagStart, closeTagEnd + 1);
                            result.add(fullTag);
                            i = closeTagEnd + 1;
                            continue;
                        }
                    }
                }
            }

            result.add(tagContent);
            i = tagEnd + 1;
        }

        return result;
    }

    /**
     * 查找匹配的结束标签位置
     */
    private static int findMatchingClosingTag(String html, int start, String tagName) {
        Pattern openPattern = Pattern.compile("<" + tagName + "[^>]*>", Pattern.CASE_INSENSITIVE);
        Pattern closePattern = Pattern.compile("</" + tagName + "\\s*>", Pattern.CASE_INSENSITIVE);

        int depth = 0;
        int i = start;

        while (i < html.length()) {
            // 查找下一个开标签或闭标签
            int nextOpen = findNextTag(html, i, "<" + tagName, true);
            int nextClose = findNextTag(html, i, "</" + tagName, false);

            if (nextOpen == -1 && nextClose == -1) {
                return -1;
            }

            // 优先处理位置靠前的
            if (nextOpen != -1 && (nextClose == -1 || nextOpen < nextClose)) {
                depth++;
                i = nextOpen + tagName.length() + 1;
            } else if (nextClose != -1) {
                if (depth == 1) {
                    // 找到匹配的结束标签
                    int endTagEnd = html.indexOf('>', nextClose);
                    return endTagEnd;
                }
                depth--;
                i = nextClose + tagName.length() + 2;
            } else {
                i++;
            }
        }

        return -1;
    }

    /**
     * 查找下一个指定标签的位置
     */
    private static int findNextTag(String html, int start, String tagPattern, boolean isOpen) {
        String searchStr = isOpen ? tagPattern + " " : tagPattern;
        String searchStr2 = isOpen ? tagPattern + ">" : tagPattern + ">";

        int pos = html.indexOf(searchStr, start);
        if (pos != -1) {
            // 检查是否确实是指定的标签
            char nextChar = html.charAt(pos + searchStr.length());
            if (nextChar == ' ' || nextChar == '>' || nextChar == '/') {
                return pos;
            }
        }

        // 尝试不带空格的版本
        if (!tagPattern.equals(searchStr2)) {
            pos = html.indexOf(searchStr2, start);
            if (pos != -1) {
                return pos;
            }
        }

        return -1;
    }

    /**
     * 查找标签结束位置
     */
    private static int findTagEnd(String html, int start) {
        if (start >= html.length() || html.charAt(start) != '<') {
            return -1;
        }

        int i = start + 1;
        boolean inQuotes = false;
        char quoteChar = '"';

        while (i < html.length()) {
            char c = html.charAt(i);

            if (c == '"' || c == '\'') {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                }
            } else if (c == '>' && !inQuotes) {
                return i;
            }

            i++;
        }

        return -1;
    }

    /**
     * 判断是否为容器标签（可以有内容）
     */
    private static boolean isContainerTag(String tagName) {
        return tagName.equals("h1") || tagName.equals("h2") || tagName.equals("h3") ||
                tagName.equals("h4") || tagName.equals("h5") || tagName.equals("h6") ||
                tagName.equals("p")|| tagName.equals("table") || tagName.equals("tbody") ||
                tagName.equals("tr") || tagName.equals("td") || tagName.equals("th");
    }



    public List<Document> chunkByTagType(Integer kbId, List<String> tagList) {
        List<Document> documents = new ArrayList<>();
        List<String> currentParts = new ArrayList<>();
        String primaryType = "p"; // 默认类型为段落

        for (String html : tagList) {
            if (html == null || html.isBlank()) continue;

            // <p /> 空段落作为分片边界：保存当前分片，跳过空标签
            if (isEmptyParagraph(html)) {
                if (!currentParts.isEmpty()) {
                    documents.add(buildChunkDocument(currentParts, primaryType, kbId));
                    currentParts = new ArrayList<>();
                    primaryType = "p";
                }
                continue;
            }

            String tagType = detectTagType(html);

            if ("h".equals(tagType)) {
                if ("h".equals(primaryType)) {
                    // 连续 h 标签：追加到当前分片，不分割
                    currentParts.add(html);
                } else {
                    // 非 h 之后的 h 标签：保存上一个分片，开启新分片
                    if (!currentParts.isEmpty()) {
                        documents.add(buildChunkDocument(currentParts, primaryType, kbId));
                    }
                    currentParts = new ArrayList<>();
                    currentParts.add(html);
                    primaryType = "h";
                }
            } else {
                // p 或 table 内容追加到当前分片
                currentParts.add(html);
                // 标记为非 h，确保后续 h 标签会开启新分片
                primaryType = tagType;
            }
        }

        // 保存最后一个分片
        if (!currentParts.isEmpty()) {
            documents.add(buildChunkDocument(currentParts, primaryType, kbId));
        }

        // 设置 chunk_index 和 total_chunks
        int total = documents.size();
        for (int i = 0; i < total; i++) {
            documents.get(i).getMetadata().put("chunk_index", i);
            documents.get(i).getMetadata().put("total_chunks", total);
        }

        return documents;
    }

    private String detectTagType(String html) {
        String lower = html.strip().toLowerCase();
        if (lower.matches("^<h[1-6][\\s>].*")) return "h";
        if (lower.startsWith("<table")) return "table";
        return "p";
    }

    /** 检测是否为空的 p 标签：&lt;p /&gt; 或 &lt;p&gt;&lt;/p&gt;（去除标签后无内容） */
    private boolean isEmptyParagraph(String html) {
        String lower = html.strip().toLowerCase();
        // <p /> 自闭合
        if (lower.matches("<p\\s*/>")) return true;
        // <p></p> 无内容
        if (lower.matches("<p[^>]*></p>")) return true;
        return false;
    }

    private Document buildChunkDocument(List<String> parts, String type, Integer kbId) {
        // 合并 HTML：每部分之间用 \n 分隔
        String combined = String.join("\n", parts);

        // 只去除非表格标签（保留 table/tr/td/th/tbody/thead/tfoot/caption）
        String text = combined
                .replaceAll("<(?!\\/?(?:table|tr|td|th|tbody|thead|tfoot|caption)\\b)[^>]*>", " ")
                .replaceAll("&[a-z]+;", " ")
                // 清除 Tika 产生的编号前缀：1. 2. 3. / 1、 2、 3、 / 1） 2） / · 等
                .replaceAll("(^|\\n)\\s*\\d+[.、）)]\\s*", "$1")
                .replaceAll("(^|\\n)\\s*·\\s*", "$1")
                // 只压缩水平空白（空格、制表符），保留换行符
                .replaceAll("[ \\t]+", " ")
                // 多个连续换行压缩为两个（保留段落间隔）
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        Document doc = new Document(text);
        doc.getMetadata().put("tag_type", type);
        doc.getMetadata().put("kb_id", kbId.toString());
        return doc;
    }

}
