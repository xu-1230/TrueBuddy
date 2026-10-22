package org.example.backend.module.test.service;

import org.example.backend.module.test.model.email.EmailVerificationData;
import org.example.backend.module.test.model.enums.EmailCodeScene;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码内存存储。
 *
 * <p>单实例、重启即失效；没有过期线程，发送与读取时顺带清理过期项。
 * 验证码按 {@code scene:email} 隔离，成功使用后由调用方删除。
 */
@Component
public class EmailCodeStore {

    /** 验证码有效期（分钟） */
    private static final int EXPIRE_MINUTES = 5;

    private final Map<String, EmailVerificationData> verificationDataMap = new ConcurrentHashMap<>();

    public void save(EmailCodeScene scene, String email, String code) {
        removeExpired();
        verificationDataMap.put(key(scene, email),
                new EmailVerificationData(code, scene, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
    }

    public EmailVerificationData get(EmailCodeScene scene, String email) {
        return verificationDataMap.get(key(scene, email));
    }

    public void remove(EmailCodeScene scene, String email) {
        verificationDataMap.remove(key(scene, email));
    }

    private String key(EmailCodeScene scene, String email) {
        return scene.getValue() + ":" + email;
    }

    private void removeExpired() {
        LocalDateTime now = LocalDateTime.now();
        verificationDataMap.entrySet().removeIf(entry -> entry.getValue().getExpiresTime().isBefore(now));
    }
}
