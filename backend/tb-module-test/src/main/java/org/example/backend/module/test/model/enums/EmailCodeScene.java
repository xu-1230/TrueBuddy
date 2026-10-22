package org.example.backend.module.test.model.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test.enums.ErrorCodeConstants.EMAIL_CODE_SCENE_INVALID;

/**
 * 邮箱验证码使用场景。
 *
 * <p>验证码按 {@code scene:email} 隔离，注册码不能用于重置密码或登录。
 */
@Getter
public enum EmailCodeScene {

    REGISTER("register"),
    RESET("reset"),
    LOGIN("login");

    private final String value;

    EmailCodeScene(String value) {
        this.value = value;
    }

    public static EmailCodeScene from(String value) {
        String normalized = StrUtil.trim(value);
        for (EmailCodeScene scene : values()) {
            if (scene.value.equalsIgnoreCase(normalized)) {
                return scene;
            }
        }
        throw exception(EMAIL_CODE_SCENE_INVALID);
    }
}
