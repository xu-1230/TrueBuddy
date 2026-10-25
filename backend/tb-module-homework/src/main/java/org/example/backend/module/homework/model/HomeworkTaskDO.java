package org.example.backend.module.homework.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业批改任务 DO（表 homework_task）
 * <p>一次「作业文件夹上传」= 一个任务；任务下的每份作业见 {@link HomeworkFileDO}。
 */
@Data
public class HomeworkTaskDO {

    /** 主键（自增） */
    private Long id;
    /** 作业文件夹名 */
    private String folderName;
    /** 作业份数 */
    private Integer fileCount;
    /** 文件总大小（字节） */
    private Long totalSize;
    /** 任务状态：0=已上传待批改，1=批改中，2=成功，3=部分成功，4=失败 */
    private Integer status;
    /** AI 描述说明（批改要求），由批改请求写入 */
    private String description;
    /** 知识库 id 列表（逗号分隔），由批改请求写入 */
    private String spaceIds;
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
