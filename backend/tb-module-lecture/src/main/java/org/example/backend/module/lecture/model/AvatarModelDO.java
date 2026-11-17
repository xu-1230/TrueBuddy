package org.example.backend.module.lecture.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字人形象目录 DO（表 avatar_model）。
 *
 * <p>本表只做目录：哪些形象可展示、显示名、缩略图、排序、启停；
 * 模型地址与取景参数仍由前端 {@code configs/avatar.js} 维护。
 */
@Data
public class AvatarModelDO {

    /** 主键（自增） */
    private Long id;
    /** 形象编码，与前端 configs/avatar.js 的键一致 */
    private String modelCode;
    /** 显示名称 */
    private String name;
    /** 类型：deepseek=社区二创，sample=Live2D 官方示例 */
    private String kind;
    /** 静态缩略图路径（public 下的绝对路径） */
    private String previewImage;
    /** 排序（升序） */
    private Integer sort;
    /** 是否在设置弹窗中展示 */
    private Boolean enabled;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 id */
    private Long creator;
    /** 更新人 id */
    private Long updater;
    /** 是否删除：false=否，true=是 */
    private Boolean deleted;

}
