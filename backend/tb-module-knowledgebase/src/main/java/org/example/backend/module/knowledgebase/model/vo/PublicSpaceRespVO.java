package org.example.backend.module.knowledgebase.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 他人公有空间 Response VO（「公有库」浏览区，只读；fork 入口在 006 打通）
 */
@Data
public class PublicSpaceRespVO {

    /** 空间 id */
    private Long id;
    /** 空间名称 */
    private String name;
    /** 空间描述 */
    private String description;
    /** 文档数量 */
    private Long documentCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 创建者显示名（昵称缺失降级账号名） */
    private String creatorName;

}
