package org.example.backend.module.doctemplate.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 撤回结果：当前版本文件地址 + 失效的版本 id。
 */
@Data
public class ChatUndoRespVO {

    /** 根文件 id（工作区） */
    private Long rootFileId;
    /** 撤回后当前版本的文件地址（MinIO 预签名 URL） */
    private String src;
    /** 被置为 superseded 的版本 id（目标版本及其后代） */
    private List<Long> discardedVersionIds;
    /** 落库的系统撤回提示（刷新后仍可见） */
    private ChatMessageRespVO notice;

}
