package org.example.backend.module.infra.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.model.vo.FileUploadRespVO;
import org.example.backend.module.infra.service.FileService;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.infra.enums.ErrorCodeConstants.FILE_EMPTY;
import static org.example.backend.module.infra.enums.ErrorCodeConstants.FILE_SIZE_EXCEED;
import static org.example.backend.module.infra.enums.ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED;

/**
 * 通用文件 Service 实现
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 单文件最大 50MB（与 spring.servlet.multipart 上限保持一致） */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    /** MinIO 通用文件存储目录 */
    private static final String UPLOAD_DIR = "infra/file";
    /** MinIO 程序化生成产物存储目录（如文档模板生成结果） */
    private static final String GENERATED_DIR = "doc-template";
    /** 允许上传的扩展名（覆盖文档模板 / PPT / 表格 / 图片等场景） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "md", "markdown", "txt",
            "ppt", "pptx", "xls", "xlsx",
            "png", "jpg", "jpeg", "gif", "webp");

    private final MinioFileStorageService minioFileStorageService;
    private final InfraFileMapper infraFileMapper;

    public FileServiceImpl(MinioFileStorageService minioFileStorageService,
                           InfraFileMapper infraFileMapper) {
        this.minioFileStorageService = minioFileStorageService;
        this.infraFileMapper = infraFileMapper;
    }

    @Override
    public FileUploadRespVO uploadFile(MultipartFile file) {
        validateFile(file);
        String originalName = sanitizeName(file.getOriginalFilename());
        long fileSize = file.getSize();

        // 1. 上传到 MinIO，并生成预签名访问地址
        String objectName = minioFileStorageService.uploadFile(file, UPLOAD_DIR);
        String fileUrl = minioFileStorageService.getFileUrl(objectName);

        // 2. 记录文件元数据到 infra_file
        InfraFileDO infraFile = new InfraFileDO();
        infraFile.setBucket(minioFileStorageService.getBucket());
        infraFile.setPath(objectName);
        infraFile.setName(originalName);
        infraFile.setUrl(fileUrl);
        infraFile.setSize(fileSize);
        infraFile.setType(file.getContentType());
        infraFileMapper.insert(infraFile);

        log.info("文件上传完成: fileId={}, name={}, size={}, object={}",
                infraFile.getId(), originalName, fileSize, objectName);

        // 3. 组装返回
        FileUploadRespVO respVO = new FileUploadRespVO();
        respVO.setId(infraFile.getId());
        respVO.setName(originalName);
        respVO.setSize(fileSize);
        respVO.setType(infraFile.getType());
        respVO.setUrl(fileUrl);
        return respVO;
    }

    @Override
    public FileUploadRespVO uploadBytes(byte[] content, String originalName, String contentType) {
        validateBytes(content, originalName);
        String name = sanitizeName(originalName);
        long fileSize = content.length;

        // 1. 上传到 MinIO，并生成预签名访问地址
        String objectName = minioFileStorageService.uploadBytes(content, GENERATED_DIR, name, contentType);
        String fileUrl = minioFileStorageService.getFileUrl(objectName);

        // 2. 记录文件元数据到 infra_file
        InfraFileDO infraFile = new InfraFileDO();
        infraFile.setBucket(minioFileStorageService.getBucket());
        infraFile.setPath(objectName);
        infraFile.setName(name);
        infraFile.setUrl(fileUrl);
        infraFile.setSize(fileSize);
        infraFile.setType(contentType);
        infraFileMapper.insert(infraFile);

        log.info("字节流上传完成: fileId={}, name={}, size={}, object={}",
                infraFile.getId(), name, fileSize, objectName);

        // 3. 组装返回
        FileUploadRespVO respVO = new FileUploadRespVO();
        respVO.setId(infraFile.getId());
        respVO.setName(name);
        respVO.setSize(fileSize);
        respVO.setType(contentType);
        respVO.setUrl(fileUrl);
        return respVO;
    }

    // ========== 私有方法 ==========

    /** 字节流产物的非空、大小、扩展名校验 */
    private void validateBytes(byte[] content, String originalName) {
        if (content == null || content.length == 0) {
            throw exception(FILE_EMPTY);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw exception(FILE_SIZE_EXCEED);
        }
        String ext = getExt(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
    }

    /** 非空、大小、扩展名校验 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw exception(FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw exception(FILE_SIZE_EXCEED);
        }
        String ext = getExt(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
    }

    /** 去除文件名中的路径信息，防止非法路径注入 */
    private String sanitizeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown";
        }
        String name = originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    /** 获取小写扩展名（不含点） */
    private String getExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

}
