package org.example.backend.module.test.model.vo;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class UserSaveReqVO {

    private Long id;
    private String username;
    private String nickname;
    private String remark;
    private String email;
    private String avatar;

    // ========== 仅【创建】时，需要传递的字段 ==========

    private String password;

    // ========== 管理端编辑字段（注册路径不传，保持默认行为） ==========

    /** 激活状态：0=正常，1=禁用；空表示不修改 */
    private Integer status;

    /** 角色编码集合；空表示不调整角色绑定，非空时全量重绑 */
    private Set<String> roles = new LinkedHashSet<>();

    public boolean isPasswordValid() {
        return id != null // 修改时，不需要传递
                || (ObjectUtil.isAllNotEmpty(password)); // 新增时，必须都传递 password
    }
}
