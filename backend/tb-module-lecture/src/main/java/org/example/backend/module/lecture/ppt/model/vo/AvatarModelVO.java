package org.example.backend.module.lecture.ppt.model.vo;

import lombok.Data;

/**
 * 数字人形象目录项（设置弹窗「数字人」Tab 用）。
 */
@Data
public class AvatarModelVO {

    /** 形象编码，与前端 configs/avatar.js 的键一致 */
    private String modelCode;
    /** 显示名称 */
    private String name;
    /** 类型：deepseek=社区二创，sample=Live2D 官方示例 */
    private String kind;
    /** 静态缩略图路径 */
    private String previewImage;
    /** 排序 */
    private Integer sort;

}
