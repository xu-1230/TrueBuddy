package org.example.backend.module.infra.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.framework.common.exception.ServiceException;
import org.example.backend.module.infra.config.MinioConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储服务（底层对象存储能力，与业务无关）
 */
@Slf4j
@Service
public class MinioFileStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorageService(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.bucket = minioConfig.getBucket();
        ensureBucketExists();
    }

    public String getBucket() {
        return bucket;
    }

    /**
     * 确保存储桶存在；初始化失败仅告警，不影响应用启动
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 存储桶已创建: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO 初始化失败（上传时才会再次报错）: {}", e.getMessage());
        }
    }

    /**
     * 上传文件到 MinIO
     *
     * @param file 上传文件
     * @param dir  存储目录（如 infra/file）
     * @return 对象键 objectKey
     */
    public String uploadFile(MultipartFile file, String dir) {
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = dir + "/" + UUID.randomUUID() + ext;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("文件已上传到 MinIO: bucket={}, object={}", bucket, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败: {}", e.getMessage());
            throw new ServiceException(500, "文件存储失败");
        }
    }

    /**
     * 上传字节流到 MinIO（用于后端程序化生成的产物，如生成的 docx）
     *
     * @param content          文件字节
     * @param dir              存储目录（如 doc-template）
     * @param originalFilename 原始文件名（用于取扩展名）
     * @param contentType      内容类型（可空）
     * @return 对象键 objectKey
     */
    public String uploadBytes(byte[] content, String dir, String originalFilename, String contentType) {
        try {
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = dir + "/" + UUID.randomUUID() + ext;
            try (InputStream inputStream = new ByteArrayInputStream(content)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(inputStream, content.length, -1)
                                .contentType(contentType)
                                .build()
                );
            }
            log.info("字节流已上传到 MinIO: bucket={}, object={}, size={}", bucket, objectName, content.length);
            return objectName;
        } catch (Exception e) {
            log.error("上传字节流到 MinIO 失败: {}", e.getMessage());
            throw new ServiceException(500, "文件存储失败");
        }
    }

    /**
     * 获取文件访问 URL（预签名 URL，默认 7 天有效）
     */
    public String getFileUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成文件访问 URL 失败: {}", e.getMessage());
            throw new ServiceException(500, "获取文件链接失败");
        }
    }

}
