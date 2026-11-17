package org.example.backend.module.lecture.ppt.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.module.infra.mapper.InfraFileMapper;
import org.example.backend.module.infra.model.InfraFileDO;
import org.example.backend.module.infra.service.MinioFileStorageService;
import org.example.backend.module.lecture.config.DigitalHumanProperties;
import org.example.backend.module.lecture.digital.tts.VoiceEnrollmentPort;
import org.example.backend.module.lecture.mapper.AvatarVoiceMapper;
import org.example.backend.module.lecture.mapper.LectureAvatarPreferenceMapper;
import org.example.backend.module.lecture.model.AvatarVoiceDO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceListRespVO;
import org.example.backend.module.lecture.ppt.model.vo.AvatarVoiceVO;
import org.example.backend.module.lecture.ppt.model.vo.VoiceCloneCommand;
import org.example.backend.module.lecture.ppt.service.PptVoiceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.example.backend.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_CLONE_FAILED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_CLONE_INPUT_INVALID;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_DELETE_FORBIDDEN;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_LOGIN_REQUIRED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_METADATA_INVALID;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_NAME_DUPLICATED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_NOT_FOUND;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_SAMPLE_INVALID;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_SOURCE_INVALID;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_UPLOAD_DISABLED;
import static org.example.backend.module.lecture.ppt.enums.PptErrorCodeConstants.PPT_VOICE_URL_INVALID;

/**
 * 音色库实现。
 *
 * <p>内置音色来自 `avatar_voice` 的种子数据；复刻音色通过 DashScope 声音复刻创建，
 * 只对创建者可见。复刻样本可以上传（需要公网存储）或直接给公网 URL —— DashScope
 * 只接受公网 URL，不接收本地文件。
 */
@Slf4j
@Service
public class PptVoiceServiceImpl implements PptVoiceService {

    /** 复刻音色的固定分类 */
    private static final String CLONED_CATEGORY = "我的复刻";
    /** 上传样本大小上限：DashScope 声音复刻要求 ≤10MB */
    private static final long MAX_SAMPLE_BYTES = 10L * 1024 * 1024;
    /** MinIO 中存放声音样本的目录 */
    private static final String SAMPLE_DIR = "voice-sample";
    /** 复刻来源 */
    private static final int SOURCE_CLONED = 2;

    private final AvatarVoiceMapper voiceMapper;
    private final LectureAvatarPreferenceMapper preferenceMapper;
    private final VoiceEnrollmentPort enrollmentPort;
    private final DigitalHumanProperties properties;
    private final MinioFileStorageService minioFileStorageService;
    private final InfraFileMapper infraFileMapper;

    public PptVoiceServiceImpl(AvatarVoiceMapper voiceMapper,
                               LectureAvatarPreferenceMapper preferenceMapper,
                               VoiceEnrollmentPort enrollmentPort,
                               DigitalHumanProperties properties,
                               MinioFileStorageService minioFileStorageService,
                               InfraFileMapper infraFileMapper) {
        this.voiceMapper = voiceMapper;
        this.preferenceMapper = preferenceMapper;
        this.enrollmentPort = enrollmentPort;
        this.properties = properties;
        this.minioFileStorageService = minioFileStorageService;
        this.infraFileMapper = infraFileMapper;
    }

    @Override
    public AvatarVoiceListRespVO list(Long userId) {
        Long user = requireUser(userId);
        List<AvatarVoiceVO> items = voiceMapper.selectVisible(user).stream()
                .map(voice -> toVO(voice, user))
                .toList();

        AvatarVoiceListRespVO respVO = new AvatarVoiceListRespVO();
        respVO.setItems(items);
        respVO.setDefaultVoiceCode(properties.getTtsVoice());
        respVO.setCloneEnabled(Boolean.TRUE.equals(properties.getEnabled()) && hasApiKey());
        respVO.setUploadEnabled(trimToNull(properties.getPublicBaseUrl()) != null);
        return respVO;
    }

    @Override
    public AvatarVoiceVO clone(Long userId, VoiceCloneCommand command) {
        Long user = requireUser(userId);
        if (command == null) {
            throw exception(PPT_VOICE_CLONE_INPUT_INVALID);
        }
        String name = trimToNull(command.name());
        String gender = trimToNull(command.gender());
        if (name == null || gender == null) {
            throw exception(PPT_VOICE_CLONE_INPUT_INVALID);
        }
        if (name.length() > 50) {
            throw exception(PPT_VOICE_METADATA_INVALID);
        }
        gender = gender.toLowerCase(Locale.ROOT);
        if (!"male".equals(gender) && !"female".equals(gender)) {
            throw exception(PPT_VOICE_CLONE_INPUT_INVALID);
        }
        String trait = trimToNull(command.trait());
        String language = trimToNull(command.language());
        if ((trait != null && trait.length() > 100) || (language != null && language.length() > 100)) {
            throw exception(PPT_VOICE_METADATA_INVALID);
        }
        if (voiceMapper.countByVisibleName(user, name) > 0) {
            throw exception(PPT_VOICE_NAME_DUPLICATED, name);
        }

        String sourceType = trimToNull(command.sourceType());
        Long sampleFileId = null;
        String audioUrl;
        if ("upload".equalsIgnoreCase(sourceType)) {
            String publicBaseUrl = trimToNull(properties.getPublicBaseUrl());
            if (publicBaseUrl == null) {
                throw exception(PPT_VOICE_UPLOAD_DISABLED);
            }
            UploadedSample uploaded = uploadSample(command.file(), publicBaseUrl);
            audioUrl = uploaded.audioUrl();
            sampleFileId = uploaded.fileId();
        } else if ("url".equalsIgnoreCase(sourceType)) {
            audioUrl = validSampleUrl(command.url());
        } else {
            throw exception(PPT_VOICE_SOURCE_INVALID);
        }

        String targetModel = defaultModel();
        String voiceId;
        try {
            voiceId = enrollmentPort.createVoice(targetModel, newVoicePrefix(), audioUrl);
        } catch (RuntimeException e) {
            throw exception(PPT_VOICE_CLONE_FAILED, e.getMessage());
        }

        AvatarVoiceDO voice = new AvatarVoiceDO();
        voice.setVoiceCode(voiceId);
        voice.setName(name);
        voice.setTrait(trait);
        voice.setAge(null);
        voice.setLanguage(language);
        voice.setCategory(CLONED_CATEGORY);
        voice.setGender(gender);
        voice.setSource(SOURCE_CLONED);
        voice.setTtsModel(targetModel);
        voice.setAudioPreviewUrl(null);
        voice.setSampleFileId(sampleFileId);
        voice.setSampleAudioUrl(audioUrl);
        voice.setCreator(user);
        voice.setUpdater(user);
        try {
            voiceMapper.insert(voice);
        } catch (RuntimeException e) {
            // 本地入库失败：尽力把云端刚创建的音色删掉，避免留下孤儿占用额度
            try {
                enrollmentPort.deleteVoice(voiceId);
            } catch (RuntimeException cleanupError) {
                log.warn("[voice][入库失败后清理云端音色失败 voiceId={}]: {}", voiceId, cleanupError.getMessage());
            }
            throw e;
        }

        log.info("[voice][复刻成功 id={}, voiceId={}, creator={}, sourceType={}]",
                voice.getId(), voiceId, user, sourceType);
        return toVO(voice, user);
    }

    /** 上传样本的落库结果：infra_file 主键 + DashScope 能拉取的公网地址。 */
    private record UploadedSample(Long fileId, String audioUrl) {
    }

    /** 上传样本：校验格式/大小 → MinIO → infra_file → 公网 URL */
    private UploadedSample uploadSample(MultipartFile file, String publicBaseUrl) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SAMPLE_BYTES) {
            throw exception(PPT_VOICE_SAMPLE_INVALID);
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!"wav".equals(ext) && !"mp3".equals(ext)) {
            throw exception(PPT_VOICE_SAMPLE_INVALID);
        }

        String objectName = minioFileStorageService.uploadFile(file, SAMPLE_DIR);
        InfraFileDO row = new InfraFileDO();
        row.setBucket(minioFileStorageService.getBucket());
        row.setPath(objectName);
        row.setName(sanitizeName(file.getOriginalFilename(), ext));
        row.setUrl(minioFileStorageService.getFileUrl(objectName));
        row.setSize(file.getSize());
        row.setType(file.getContentType());
        infraFileMapper.insert(row);

        String audioUrl = joinPublicUrl(publicBaseUrl, row.getBucket(), objectName);
        log.info("[voice][样本已上传 fileId={}, object={}, publicUrl={}]", row.getId(), objectName, audioUrl);
        return new UploadedSample(row.getId(), audioUrl);
    }

    /** 校验粘贴的公网音频地址 */
    private String validSampleUrl(String raw) {
        String url = trimToNull(raw);
        if (url == null || url.length() > 500) {
            throw exception(PPT_VOICE_URL_INVALID);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw exception(PPT_VOICE_URL_INVALID);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw exception(PPT_VOICE_URL_INVALID);
            }
        } catch (IllegalArgumentException e) {
            throw exception(PPT_VOICE_URL_INVALID);
        }
        return url;
    }

    private static String joinPublicUrl(String base, String bucket, String objectName) {
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + "/" + bucket + "/" + objectName;
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeName(String name, String ext) {
        String base = name == null ? "" : name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.isBlank()) {
            base = "voice-sample." + ext;
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
    }

    @Override
    public void delete(Long userId, Long id) {
        Long user = requireUser(userId);
        AvatarVoiceDO voice = id == null ? null : voiceMapper.selectById(id);
        if (voice == null) {
            throw exception(PPT_VOICE_NOT_FOUND);
        }
        if (voice.getSource() == null || voice.getSource() != SOURCE_CLONED
                || voice.getCreator() == null || !voice.getCreator().equals(user)) {
            throw exception(PPT_VOICE_DELETE_FORBIDDEN);
        }
        if (voiceMapper.softDeleteOwned(id, user) == 0) {
            throw exception(PPT_VOICE_DELETE_FORBIDDEN);
        }
        // 引用这条音色的偏好一并回退到默认
        preferenceMapper.clearVoiceByCode(voice.getVoiceCode());
        try {
            enrollmentPort.deleteVoice(voice.getVoiceCode());
        } catch (RuntimeException e) {
            log.warn("[voice][云端删除失败，本地已软删 voiceId={}]: {}", voice.getVoiceCode(), e.getMessage());
        }
        log.info("[voice][删除复刻音色 id={}, voiceId={}, creator={}]", id, voice.getVoiceCode(), user);
    }

    @Override
    public VoiceSelection resolveForSpeak(String requestedVoiceCode, Long userId) {
        String requested = trimToNull(requestedVoiceCode);
        String defaultVoice = defaultVoiceCode();
        String defaultModel = defaultModel();
        if (requested == null) {
            return new VoiceSelection(defaultVoice, defaultModel, false, null);
        }

        AvatarVoiceDO voice = voiceMapper.selectByCode(requested);
        if (voice == null || trimToNull(voice.getVoiceCode()) == null) {
            return new VoiceSelection(defaultVoice, defaultModel, true, "not_found");
        }
        if (Integer.valueOf(SOURCE_CLONED).equals(voice.getSource())
                && (userId == null || voice.getCreator() == null || !voice.getCreator().equals(userId))) {
            return new VoiceSelection(defaultVoice, defaultModel, true, "forbidden");
        }
        String model = trimToNull(voice.getTtsModel());
        return new VoiceSelection(voice.getVoiceCode(), model == null ? defaultModel : model, false, null);
    }

    // ========== 内部方法 ==========

    private AvatarVoiceVO toVO(AvatarVoiceDO voice, Long userId) {
        AvatarVoiceVO vo = new AvatarVoiceVO();
        vo.setId(voice.getId());
        vo.setVoiceCode(voice.getVoiceCode());
        vo.setName(voice.getName());
        vo.setTrait(voice.getTrait());
        vo.setAge(voice.getAge());
        vo.setLanguage(voice.getLanguage());
        vo.setCategory(voice.getCategory());
        vo.setGender(voice.getGender());
        boolean cloned = Integer.valueOf(SOURCE_CLONED).equals(voice.getSource());
        vo.setSource(cloned ? "cloned" : "system");
        vo.setCanDelete(cloned && voice.getCreator() != null && voice.getCreator().equals(userId));
        vo.setPreviewUrl(previewUrl(voice));
        return vo;
    }

    /**
     * 试听地址：内置用官方示例；复刻优先用公网样本 URL，其次用上传文件的预签名地址。
     * 预签名失败不能拖垮整个列表，因此这里吞掉异常并返回 null。
     */
    private String previewUrl(AvatarVoiceDO voice) {
        String official = trimToNull(voice.getAudioPreviewUrl());
        if (official != null) {
            return official;
        }
        String sample = trimToNull(voice.getSampleAudioUrl());
        if (sample != null) {
            return sample;
        }
        if (voice.getSampleFileId() == null) {
            return null;
        }
        InfraFileDO file = infraFileMapper.selectById(voice.getSampleFileId());
        if (file == null || trimToNull(file.getPath()) == null) {
            return null;
        }
        try {
            return minioFileStorageService.getFileUrl(file.getPath());
        } catch (RuntimeException e) {
            log.warn("[voice][生成试听地址失败 fileId={}]: {}", voice.getSampleFileId(), e.getMessage());
            return null;
        }
    }

    private Long requireUser(Long userId) {
        if (userId == null) {
            throw exception(PPT_VOICE_LOGIN_REQUIRED);
        }
        return userId;
    }

    private boolean hasApiKey() {
        String apiKey = properties.getDashscopeApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    private String defaultVoiceCode() {
        String voice = trimToNull(properties.getTtsVoice());
        return voice == null ? "longanyang" : voice;
    }

    private String defaultModel() {
        String model = trimToNull(properties.getTtsModel());
        return model == null ? "cosyvoice-v3-flash" : model;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 复刻音色前缀：小写字母/数字，1~10 位，以字母开头；随机 8 位降低冲突。 */
    private static String newVoicePrefix() {
        return "vb" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

}
