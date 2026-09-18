package org.example.backend.module.infra.service;

import org.example.backend.module.infra.model.vo.FileUploadRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件 Service（系统基础能力）
 */
public interface FileService {

    /**
     * 上传文件：校验 -> MinIO 存储 -> infra_file 落库
     *
     * @param file 上传文件（multipart）
     * @return 文件元数据（id / name / size / type / url）
     */
    FileUploadRespVO uploadFile(MultipartFile file);

    /**
     * 上传字节流（用于后端程序化生成的产物）：校验 -> MinIO 存储 -> infra_file 落库
     *
     * @param content     文件字节
     * @param originalName 原始文件名（含扩展名，用于类型校验与存储命名）
     * @param contentType 内容类型（可空）
     * @return 文件元数据（id / name / size / type / url）
     */
    FileUploadRespVO uploadBytes(byte[] content, String originalName, String contentType);

}
