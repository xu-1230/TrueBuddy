package org.example.backend.module.test2.service.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.homework.mapper.HomeworkFileMapper;
import org.example.backend.module.homework.model.HomeworkFileDO;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.test2.model.vo.FileTextItemVO;
import org.example.backend.module.test2.model.vo.HomeworkFileTextRespVO;
import org.example.backend.module.test2.service.HomeworkFileTextService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.test2.enums.ErrorCodeConstants.TEST2_FILE_LIST_EMPTY;

/**
 * 作业文件文本读取 Service 实现
 *
 * <p>取字节只走 {@code fileId → infra_file(bucket, path) → MinIO 对象字节流}：
 * 预签名 URL 会过期、且 localhost 地址对模型侧/其它进程不可达，不作读取通道。
 */
@Slf4j
@Service
public class HomeworkFileTextServiceImpl implements HomeworkFileTextService {

    /** 图片类型不做文本提取（Tika 只能给出元数据，返回空文本会误导模型） */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg");

    /** 单文件提取文本的字符上限（超出截断，避免响应体过大） */
    @Value("${tb.test2.max-text-chars:20000}")
    private int maxTextChars;

    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private HomeworkFileMapper homeworkFileMapper;
    @Resource
    private MinioFileStorageService minioFileStorageService;

    @Override
    public HomeworkFileTextRespVO readAll(HomeworkGradeReqVO reqVO) {
        List<HomeworkFileItemVO> files = reqVO == null ? null : reqVO.getFiles();
        if (files == null || files.isEmpty()) {
            throw exception(TEST2_FILE_LIST_EMPTY);
        }

        Long taskId = reqVO.getTaskId();
        List<FileTextItemVO> items = new ArrayList<>(files.size());
        // 串行逐条：日志顺序与返回顺序一致，单条失败不影响其余文件
        for (HomeworkFileItemVO file : files) {
            items.add(readOne(taskId, file));
        }

        int success = 0;
        for (FileTextItemVO item : items) {
            if (item.getError() == null) {
                success++;
            }
        }

        HomeworkFileTextRespVO respVO = new HomeworkFileTextRespVO();
        respVO.setTotal(items.size());
        respVO.setSuccess(success);
        respVO.setFailed(items.size() - success);
        respVO.setItems(items);
        return respVO;
    }

    @Override
    public FileTextItemVO readOne(Long taskId, HomeworkFileItemVO item) {
        return readOne(taskId, item, null);
    }

    @Override
    public FileTextItemVO readOne(Long taskId, HomeworkFileItemVO item, Integer maxTextChars) {
        // null / 非正数表示沿用配置值（tb.test2.max-text-chars）
        int limit = maxTextChars != null && maxTextChars > 0 ? maxTextChars : this.maxTextChars;
        FileTextItemVO result = new FileTextItemVO();
        if (item == null) {
            result.setError("文件明细为空");
            return result;
        }
        result.setId(item.getId());
        result.setName(item.getName());
        result.setPath(item.getPath());
        result.setType(item.getType());
        result.setSize(item.getSize());

        try {
            // 1. 确定 fileId：请求体优先，缺失时按 taskId + path 回查 homework_file
            Long fileId = item.getFileId();
            String source = "param";
            if (fileId == null) {
                HomeworkFileDO stored = lookupHomeworkFile(taskId, item.getPath());
                if (stored != null) {
                    fileId = stored.getFileId();
                    // 用库里的权威值补全请求体可能缺失的展示字段
                    result.setName(result.getName() != null ? result.getName() : stored.getName());
                    result.setSize(result.getSize() != null ? result.getSize() : stored.getSize());
                }
                source = "lookup";
            }
            result.setSource(source);
            if (fileId == null) {
                result.setError("未找到对应的文件记录（taskId + path 未命中 homework_file）");
                return result;
            }
            result.setFileId(fileId);

            // 2. 图片类：不做文本提取
            if (isImage(item)) {
                result.setError("图片类型暂不提取文本");
                return result;
            }

            // 3. infra_file → MinIO 对象字节
            InfraFileDO infraFile = infraFileMapper.selectById(fileId);
            if (infraFile == null) {
                result.setError("文件记录不存在或已删除：" + fileId);
                return result;
            }
            byte[] bytes = minioFileStorageService.getObject(infraFile.getBucket(), infraFile.getPath());
            if (bytes.length == 0) {
                result.setError("文件内容为空（0 字节），无法提取文本");
                result.setTextLength(0);
                result.setTruncated(false);
                return result;
            }

            // 4. Tika 统一提取文本（含 txt，编码由 Tika 识别）
            String fileName = fileNameOf(item, infraFile);
            String text = extractText(bytes, fileName);

            // 5. 截断（limit 为 0 时不截断）
            int originLength = text.length();
            boolean truncated = limit > 0 && originLength > limit;
            if (truncated) {
                text = text.substring(0, limit);
            }
            result.setText(text);
            result.setTextLength(originLength);
            result.setTruncated(truncated);
            return result;
        } catch (Exception e) {
            log.warn("读取作业文件文本失败: path={}, fileId={}, error={}",
                    item.getPath(), result.getFileId(), e.getMessage());
            result.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return result;
        }
    }

    /**
     * 按 taskId + 相对路径回查作业文件（请求体的 fileId 可空，库里一定有）
     */
    private HomeworkFileDO lookupHomeworkFile(Long taskId, String relativePath) {
        if (taskId == null || relativePath == null) {
            return null;
        }
        List<HomeworkFileDO> files = homeworkFileMapper.selectListByTaskId(taskId);
        if (files == null) {
            return null;
        }
        return files.stream()
                .filter(file -> relativePath.equals(file.getRelativePath()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 用 Tika 把文件字节解析为纯文本（pdf/doc/docx/ppt/pptx/xls/xlsx/txt/md/html 等）
     *
     * <p>资源匿名子类携带文件名：Tika 的 AutoDetectParser 在魔术字节嗅探之外
     * 还能拿文件名做类型提示，避免无扩展名信息时判错格式。
     */
    private String extractText(byte[] bytes, String fileName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        List<Document> documents = new TikaDocumentReader(resource).get();
        StringBuilder text = new StringBuilder();
        for (Document document : documents) {
            if (document.getText() == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(document.getText());
        }
        return text.toString().trim();
    }

    /**
     * 是否图片：扩展名优先（请求体的 MIME 不可信，如 .txt 被标为 application/octet-stream）
     */
    private boolean isImage(HomeworkFileItemVO item) {
        if (IMAGE_EXTENSIONS.contains(extensionOf(item))) {
            return true;
        }
        String type = item.getType();
        return type != null && type.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    /**
     * 取扩展名（小写，不含点）：文件名优先，其次取相对路径的最后一段
     */
    private String extensionOf(HomeworkFileItemVO item) {
        String name = item.getName();
        if (name == null || name.isBlank()) {
            String path = item.getPath();
            name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        }
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 交给 Tika 的文件名：请求体优先，其次 infra_file.name
     */
    private String fileNameOf(HomeworkFileItemVO item, InfraFileDO infraFile) {
        if (item.getName() != null && !item.getName().isBlank()) {
            return item.getName();
        }
        return infraFile.getName() == null ? "" : infraFile.getName();
    }

}
