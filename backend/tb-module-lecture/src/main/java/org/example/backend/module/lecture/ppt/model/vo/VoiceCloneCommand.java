package org.example.backend.module.lecture.ppt.model.vo;

import org.springframework.web.multipart.MultipartFile;

/**
 * 声音复刻的入参（multipart 表单）。
 *
 * @param name       音色名称（必填）
 * @param gender     性别：male / female（必填）
 * @param trait      特征描述（选填）
 * @param language   支持语言（选填）
 * @param sourceType 来源：upload=上传文件，url=音频链接
 * @param url        公网音频链接（sourceType=url 时必填）
 * @param file       上传的音频样本（sourceType=upload 时必填）
 */
public record VoiceCloneCommand(String name, String gender, String trait, String language,
                                String sourceType, String url, MultipartFile file) {
}
