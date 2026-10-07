package org.example.backend.module.test2.model.vo;

import lombok.Data;

/**
 * 单个作业文件的文本提取结果
 *
 * <p>逐条返回：某个文件读不到文本不影响同一批里的其它文件，失败原因写在 {@link #error}。
 */
@Data
public class FileTextItemVO {

    /** 作业文件 id（homework_file.id，来自请求体，仅作回显） */
    private Long id;
    /** 实际使用的文件 id（infra_file.id）；请求体未带时由 taskId + path 回查补齐 */
    private Long fileId;
    /** 文件基础名（含扩展名） */
    private String name;
    /** 相对路径（与上传时的目录树顺序一致） */
    private String path;
    /** 文件类型（MIME） */
    private String type;
    /** 文件大小（字节，来自请求体，可能为 0，不代表文件为空） */
    private Long size;
    /** fileId 来源：param=请求体直接携带；lookup=按 taskId + path 回查 homework_file 补齐 */
    private String source;
    /** 提取到的文本（超过上限时被截断）；失败时为空 */
    private String text;
    /** 提取到的原始字符数（截断前）；失败时为 0 */
    private Integer textLength;
    /** 文本是否被截断 */
    private Boolean truncated;
    /** 失败原因；成功时为 null */
    private String error;
}
