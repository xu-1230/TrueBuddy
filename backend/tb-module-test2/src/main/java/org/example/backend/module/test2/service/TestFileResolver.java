package org.example.backend.module.test2.service;

import org.example.backend.framework.common.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试文件定位工具：绝对路径直接使用，相对路径从工作目录逐级向上查找。
 */
public final class TestFileResolver {

    /** 默认测试文件（教学大纲） */
    private static final String DEFAULT_FILE_PATH = "d:/c桌面/毕业设计/testfile/《软件工程学》教学大纲.docx";

    /** 默认测试文件名（用于相对路径向上查找） */
    private static final String DEFAULT_FILE_NAME = "《软件工程学》教学大纲.docx";

    /** 相对路径向上查找层数 */
    private static final int MAX_UPWARD_LEVELS = 4;

    private TestFileResolver() {
    }

    /**
     * 定位测试文件。
     *
     * @param filePath 文件路径（可为空，为空时使用默认教学大纲文件）
     * @return 存在的文件绝对路径
     */
    public static Path resolve(String filePath) {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(filePath)) {
            candidates.add(filePath);
        } else {
            candidates.add(DEFAULT_FILE_PATH);
            candidates.add("testfile/" + DEFAULT_FILE_NAME);
        }
        for (String candidate : candidates) {
            Path found = lookup(candidate);
            if (found != null) {
                return found;
            }
        }
        throw new ServiceException(500, "测试文件不存在: "
                + (StringUtils.hasText(filePath) ? filePath : DEFAULT_FILE_PATH));
    }

    private static Path lookup(String candidate) {
        Path path = Path.of(candidate);
        if (path.isAbsolute()) {
            return Files.isRegularFile(path) ? path : null;
        }
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < MAX_UPWARD_LEVELS && base != null; i++) {
            Path resolved = base.resolve(candidate);
            if (Files.isRegularFile(resolved)) {
                return resolved;
            }
            base = base.getParent();
        }
        return null;
    }
}
