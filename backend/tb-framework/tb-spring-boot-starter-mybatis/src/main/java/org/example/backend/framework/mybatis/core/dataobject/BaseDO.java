package org.example.backend.framework.mybatis.core.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库实体基类：提供自增主键与审计字段
 *
 * 约定：业务 DO 继承本类，并标注 @TableName 指向数据表。
 * 审计字段对应数据库列：
 *   create_time / update_time : timestamp
 *   creator / updater          : bigint
 *   deleted                    : boolean NOT NULL DEFAULT false
 */
@Data
public abstract class BaseDO implements Serializable {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建者（预留字段，待登录用户工具就绪后再自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    /** 更新者（预留字段） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    /** 逻辑删除标记：false=未删除，true=已删除 */
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
}