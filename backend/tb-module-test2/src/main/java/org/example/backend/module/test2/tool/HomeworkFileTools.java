package org.example.backend.module.test2.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.test2.model.vo.FileTextItemVO;
import org.example.backend.module.test2.service.HomeworkFileTextService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 作业文件读取工具（提供给大模型调用）。
 *
 * <p>工具只暴露"fileId → 文本"这一层：文件清单（name / path / type）由调用方以提示词形式给出，
 * 模型拿 fileId 逐份读取。取字节只走 {@code fileId → infra_file(bucket, path) → MinIO 对象字节流}，
 * 不依赖预签名 URL（会过期，且 localhost 地址对模型侧不可达）。
 *
 * <p>挂在 {@code HomeworkFileToolConfiguration} 注册为 Spring AI 的 ToolCallback，
 * 与 docx-mcp 的远程工具共用同一套机制，工作流节点可直接使用。
 */
@Slf4j
@Component
public class HomeworkFileTools {

    /** 读取失败时返回文本的前缀，提示模型这段不是作业内容 */
    private static final String FAILURE_PREFIX = "读取失败：";

    private final HomeworkFileTextService homeworkFileTextService;

    public HomeworkFileTools(HomeworkFileTextService homeworkFileTextService) {
        this.homeworkFileTextService = homeworkFileTextService;
    }

    /**
     * 读取一份作业文件的纯文本。
     *
     * <p>失败不抛异常：返回以「读取失败：」开头的说明，让模型能继续处理其它文件。
     */
    @Tool(name = "read_homework_file_text",
            description = "读取一份作业文件的文本内容（支持 PDF / Word / PPT / Excel / 纯文本 / Markdown，图片不支持）。"
                    + "fileId 取自作业文件清单中的 files[].fileId，返回该文件的全文。"
                    + "若返回内容以「读取失败：」开头，表示该文件不可读（原因随附），不要把它当作作业内容。"
                    + "批改多份作业时，请对每个文件各调用一次本工具。")
    public String readHomeworkFileText(
            @ToolParam(required = true, description = "作业文件的 fileId（对应 infra_file.id，取自作业文件清单 files[].fileId）")
            Long fileId,
            @ToolParam(required = false, description = "本次读取的字符上限，缺省用服务端配置（20000）；仅在确实需要更长内容时才调大")
            Integer maxChars) {
        if (fileId == null) {
            return FAILURE_PREFIX + "fileId 不能为空，请传入作业文件清单里的 files[].fileId";
        }

        HomeworkFileItemVO item = new HomeworkFileItemVO();
        item.setFileId(fileId);
        FileTextItemVO result = homeworkFileTextService.readOne(null, item, maxChars);
        if (result.getError() != null) {
            log.info("[readHomeworkFileText] 读取失败: fileId={}, error={}", fileId, result.getError());
            return FAILURE_PREFIX + result.getError();
        }

        String text = result.getText() == null ? "" : result.getText();
        if (text.isBlank()) {
            return "（该文件未提取到文本内容）";
        }
        if (Boolean.TRUE.equals(result.getTruncated())) {
            return text + "\n...[内容已截断，原文共 " + result.getTextLength() + " 字]";
        }
        return text;
    }

}
