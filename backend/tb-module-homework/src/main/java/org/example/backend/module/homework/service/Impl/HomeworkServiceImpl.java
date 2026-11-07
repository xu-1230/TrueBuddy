package org.example.backend.module.homework.service.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.homework.mapper.HomeworkFileMapper;
import org.example.backend.module.homework.mapper.HomeworkTaskMapper;
import org.example.backend.module.homework.model.HomeworkFileDO;
import org.example.backend.module.homework.model.HomeworkTaskDO;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.homework.model.vo.HomeworkUploadRespVO;
import org.example.backend.module.homework.service.HomeworkService;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_FILE_COUNT_EXCEED;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_FILE_EMPTY;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_FILE_SIZE_EXCEED;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_FILE_TYPE_NOT_ALLOWED;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_PATH_COUNT_MISMATCH;
import static org.example.backend.module.homework.enums.ErrorCodeConstants.HOMEWORK_PATH_INVALID;

/**
 * 作业批改 Service 实现。
 *
 * <p>上传链路：校验相对路径 → 逐份走 infra 的统一文件存储（MinIO + infra_file）→
 * 任务与各份作业的相对路径落库 → 返回以相对路径表示的文件清单。
 *
 * <p>单文件的大小与扩展名校验由 infra 的 FileService 负责（复用其错误码），
 * 本模块只补充「份数上限」与「相对路径」两类业务校验。
 */
@Slf4j
@Service
public class HomeworkServiceImpl implements HomeworkService {

    /** 单次批改份数上限（与前端 FolderUploader 保持一致） */
    private static final int MAX_FILE_COUNT = 50;
    /** 单文件大小上限（与前端 / infra 保持一致） */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    /** MinIO 存储目录（与 infra 的通用上传保持同一目录） */
    private static final String UPLOAD_DIR = "infra/file";
    /** 文件夹名兜底值 */
    private static final String DEFAULT_FOLDER_NAME = "本次作业";
    /** 允许上传的扩展名（与前端 FolderUploader 保持一致；zip 由前端本地解压，不会到达后端） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "md", "markdown", "txt",
            "ppt", "pptx", "xls", "xlsx",
            "png", "jpg", "jpeg", "gif", "webp");

    @Resource
    private MinioFileStorageService minioFileStorageService;
    @Resource
    private InfraFileMapper infraFileMapper;
    @Resource
    private HomeworkTaskMapper taskMapper;
    @Resource
    private HomeworkFileMapper fileMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 上传整份作业文件夹并建任务。
     *
     * <p>先把所有文件传到 MinIO（外部调用，一次最多 50 份，不能包在事务里持有数据库连接），
     * 再把 infra_file / 任务 / 作业清单收进同一个短事务，落库仍然全成或全败。
     * 换过顺序以后语义也变了：原来第 20 份上传失败会把前 19 份的库记录一起回滚，
     * 而那 19 个对象已经躺在 MinIO 里回滚不掉；现在前 19 份照常落库，只有第 20 份自己失败。
     */
    @Override
    public HomeworkUploadRespVO uploadAndBuildTree(List<MultipartFile> files, List<String> paths, String folderName) {
        long start = System.currentTimeMillis();

        // 1. 参数校验：非空、份数上限、文件与路径一一对应
        if (files == null || files.isEmpty()) {
            throw exception(HOMEWORK_FILE_EMPTY);
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw exception(HOMEWORK_FILE_COUNT_EXCEED);
        }
        if (paths == null || paths.size() != files.size()) {
            throw exception(HOMEWORK_PATH_COUNT_MISMATCH);
        }
        List<String> normalizedPaths = normalizePaths(paths);

        // 2. 逐份校验并存储到 MinIO，收集待落库实体（此时 infra_file 尚未插入，fileId 留空）
        long totalSize = 0L;
        List<InfraFileDO> pendingInfraFiles = new ArrayList<>(files.size());
        List<HomeworkFileDO> entities = new ArrayList<>(files.size());
        Map<String, InfraFileDO> storedByPath = new HashMap<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String relativePath = normalizedPaths.get(i);
            InfraFileDO stored = storeToMinio(file, relativePath);
            long size = stored.getSize() == null ? 0L : stored.getSize();

            HomeworkFileDO entity = new HomeworkFileDO();
            entity.setRelativePath(relativePath);
            entity.setName(fileNameOf(relativePath));
            entity.setSize(size);
            pendingInfraFiles.add(stored);
            entities.add(entity);
            storedByPath.put(relativePath, stored);
            totalSize += size;
        }

        // 3. 落库：infra_file -> 任务 -> 该任务下每份作业
        HomeworkTaskDO task = new HomeworkTaskDO();
        task.setFolderName(resolveFolderName(folderName, normalizedPaths));
        task.setFileCount(entities.size());
        task.setTotalSize(totalSize);
        task.setStatus(0);
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < pendingInfraFiles.size(); i++) {
                InfraFileDO infraFile = pendingInfraFiles.get(i);
                infraFileMapper.insert(infraFile);
                entities.get(i).setFileId(infraFile.getId());
            }
            taskMapper.insert(task);
            entities.forEach(entity -> entity.setTaskId(task.getId()));
            fileMapper.insertBatch(entities);
        });

        Long taskId = task.getId();

        // 4. 组装返回：按上传顺序输出相对路径与文件明细（自增 id 由落库后回查得到）
        HomeworkUploadRespVO respVO = buildRespVO(taskId, task.getFolderName(), normalizedPaths, storedByPath);

        log.info("作业文件夹上传完成: taskId={}, folderName={}, fileCount={}, totalSize={}B, cost={}ms",
                taskId, task.getFolderName(), respVO.getFileCount(), totalSize,
                System.currentTimeMillis() - start);
        return respVO;
    }

    /**
     * 接收批改请求（第二步）。
     *
     * <p>把批改要求与知识库写进该任务**落库存档**（历史 / 管理端可查）。批改执行所需的数据
     * 不由后端回查，而是前端随 {@code POST /chat} 的 inputs 直传，执行以 inputs 为准。
     */
    @Override
    public void receiveGradeRequest(HomeworkGradeReqVO reqVO) {
        List<HomeworkFileItemVO> fileItems = reqVO.getFiles() == null ? List.of() : reqVO.getFiles();
        List<String> spaceIds = reqVO.getSpaceIds() == null ? List.of() : reqVO.getSpaceIds();
        taskMapper.updateGradeConfig(reqVO.getTaskId(), reqVO.getDescription(), String.join(",", spaceIds));
        int descriptionLength = reqVO.getDescription() == null ? 0 : reqVO.getDescription().length();
        log.info("收到批改请求: taskId={}, spaceIds={}, fileCount={}, exportFormat={}, workflowId={}, descriptionLength={}",
                reqVO.getTaskId(), spaceIds, fileItems.size(),
                reqVO.getExportFormat(), reqVO.getWorkflowId(), descriptionLength);
        // 批改取文件以 fileId 为准（fileId → infra_file 的 bucket/path → MinIO）；
        // 请求里的预签名 URL 7 天过期，只作辅助，不记录以免日志冗长
        if (!fileItems.isEmpty()) {
            log.info("批改请求文件清单: taskId={}, paths={}, fileIds={}", reqVO.getTaskId(),
                    fileItems.stream().map(HomeworkFileItemVO::getPath).toList(),
                    fileItems.stream().map(HomeworkFileItemVO::getFileId).toList());
        }
    }

    /**
     * 单份存储：校验（错误信息带相对路径）→ MinIO，返回尚未落库的 infra_file 实体。
     *
     * <p>刻意不复用 infra 的 {@code FileService.uploadFile}：它会拒绝 0 字节文件，
     * 而作业文件夹里带上空占位文件是常见情况，这里按「照常上传」处理，
     * 空文件同样会写入对象存储并落 infra_file（size = 0）。
     * 校验规则（扩展名 + 单文件 50MB）与 infra 保持一致，差别只在错误信息带上相对路径，
     * 便于批量上传时直接定位到具体是哪一份。
     *
     * <p>写库由调用方在短事务里统一完成，所以这里只填充字段、不插入（id 为空）。
     */
    private InfraFileDO storeToMinio(MultipartFile file, String relativePath) {
        if (file == null) {
            throw exception(HOMEWORK_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw exception(HOMEWORK_FILE_SIZE_EXCEED, relativePath);
        }
        String ext = getExt(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw exception(HOMEWORK_FILE_TYPE_NOT_ALLOWED, relativePath);
        }

        String objectName = minioFileStorageService.uploadFile(file, UPLOAD_DIR);
        String fileUrl = minioFileStorageService.getFileUrl(objectName);

        InfraFileDO infraFile = new InfraFileDO();
        infraFile.setBucket(minioFileStorageService.getBucket());
        infraFile.setPath(objectName);
        infraFile.setName(fileNameOf(relativePath));
        infraFile.setUrl(fileUrl);
        infraFile.setSize(file.getSize());
        infraFile.setType(file.getContentType());
        return infraFile;
    }

    /** 按上传顺序组装响应（homework_file.id 需落库后回查） */
    private HomeworkUploadRespVO buildRespVO(Long taskId, String folderName, List<String> normalizedPaths,
                                             Map<String, InfraFileDO> storedByPath) {
        Map<String, HomeworkFileDO> savedByPath = new HashMap<>(normalizedPaths.size());
        fileMapper.selectListByTaskId(taskId).forEach(file -> savedByPath.put(file.getRelativePath(), file));

        List<String> pathList = new ArrayList<>(normalizedPaths.size());
        List<HomeworkFileItemVO> itemList = new ArrayList<>(normalizedPaths.size());
        long totalSize = 0L;
        for (String path : normalizedPaths) {
            HomeworkFileDO saved = savedByPath.get(path);
            InfraFileDO stored = storedByPath.get(path);
            long size = saved != null && saved.getSize() != null ? saved.getSize() : 0L;

            HomeworkFileItemVO item = new HomeworkFileItemVO();
            item.setId(saved == null ? null : saved.getId());
            item.setFileId(saved == null ? null : saved.getFileId());
            item.setName(saved == null ? fileNameOf(path) : saved.getName());
            item.setPath(path);
            item.setSize(size);
            item.setType(stored == null ? null : stored.getType());
            item.setUrl(stored == null ? null : stored.getUrl());

            pathList.add(path);
            itemList.add(item);
            totalSize += size;
        }

        HomeworkUploadRespVO respVO = new HomeworkUploadRespVO();
        respVO.setTaskId(taskId);
        respVO.setFolderName(folderName);
        respVO.setFileCount(itemList.size());
        respVO.setTotalSize(totalSize);
        respVO.setPaths(pathList);
        respVO.setFiles(itemList);
        return respVO;
    }

    /** 批量规范化相对路径，同时拒绝空路径、`.` / `..` 片段与重复路径 */
    private List<String> normalizePaths(List<String> paths) {
        List<String> normalized = new ArrayList<>(paths.size());
        Set<String> seen = new HashSet<>(paths.size());
        for (String path : paths) {
            String value = normalizePath(path);
            if (!seen.add(value)) {
                throw exception(HOMEWORK_PATH_INVALID);
            }
            normalized.add(value);
        }
        return normalized;
    }

    /**
     * 规范化单个相对路径：反斜杠转正斜杠、去掉前导 `/` 与首尾空白，
     * 并拒绝空路径、空片段、`.` / `..` 片段（防路径穿越与脏数据）。
     */
    private String normalizePath(String path) {
        if (path == null) {
            throw exception(HOMEWORK_PATH_INVALID);
        }
        String value = path.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            throw exception(HOMEWORK_PATH_INVALID);
        }
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw exception(HOMEWORK_PATH_INVALID);
            }
        }
        return value;
    }

    /** 取相对路径的最后一段作为文件基础名 */
    private String fileNameOf(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    /** 取小写扩展名（不含点） */
    private String getExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /** 文件夹名：优先取调用方传入值，为空时用首个相对路径的根目录，仍为空则兜底 */
    private String resolveFolderName(String folderName, List<String> paths) {
        if (StringUtils.hasText(folderName)) {
            return folderName.trim();
        }
        if (!paths.isEmpty()) {
            String first = paths.get(0);
            int slash = first.indexOf('/');
            if (slash > 0) {
                return first.substring(0, slash);
            }
        }
        return DEFAULT_FOLDER_NAME;
    }

}
