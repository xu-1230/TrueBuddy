package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

/**
 * 文档模板生成请求 VO（对应前端 DocTemplateView 生成表单）
 */
@Data
public class GenerateReqVO {

    /** 模板文件 id（infra_file.id，前端上传模板后获得） */
    private Long templateFileId;
    /** 知识库空间 id；为空或 "default" 表示不启用检索增强 */
    private String spaceId;
    /** AI 描述说明（<=500 字） */
    private String description;
    /** 清晰度参数：480p / 720p / 1080p（可选） */
    private String quality;

}
