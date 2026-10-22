package org.example.backend.framework.common.util.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 摘要工具。
 *
 * <p>仅用于本项目演示阶段的密码摘要；MD5 无盐且可被彩虹表攻击，
 * 生产环境应替换为 bcrypt / argon2。
 */
public final class MD5Utils {

    private MD5Utils() {
    }

    /**
     * 计算 32 位小写十六进制 MD5。
     *
     * @param text 原文，不能为 null
     * @return 32 位小写 hex
     */
    public static String encrypt(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 摘要算法不可用", e);
        }
    }
}
