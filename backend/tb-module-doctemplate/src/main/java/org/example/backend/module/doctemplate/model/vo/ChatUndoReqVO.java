package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

/**
 * 撤回某次编辑：目标版本及其后代全部失效，当前版本回到目标版本的父版本。
 */
@Data
public class ChatUndoReqVO {

    /** 根文件 id（doc_template_file.id） */
    private Long rootFileId;
    /** 要撤回的版本 id（assistant 消息产生的版本） */
    private Long targetVersionId;

}
