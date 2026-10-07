package org.example.backend.module.test2.controller;

import jakarta.annotation.Resource;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.test2.model.vo.HomeworkFileTextRespVO;
import org.example.backend.module.test2.service.HomeworkFileTextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.example.backend.framework.common.pojo.CommonResult.success;

/**
 * 作业文件文本读取测试接口（GET + 内置模拟数据，联调用）。
 *
 * <p>不接收请求体：要读哪些文件由 {@link #mockGradeReq()} 里的模拟数据决定，
 * 直接改那个方法中的 {@code taskId} / {@code fileId} / {@code path} 即可切换，无需构造 JSON。
 *
 * <p>模拟数据与 {@code POST /homework/grade} 的入参同构（{@link HomeworkGradeReqVO}），
 * 但只使用 taskId 与 files：逐个文件按 fileId 从 MinIO 取字节并以 Tika 提取文本，
 * 用于验证后续 AI 批改（MCP 工具）的取文本链路。
 *
 * <p>免登录：由 {@code tb.security.permit-all-urls} 放行（该配置不限 HTTP 方法）。
 */
@RestController
@RequestMapping("/test2")
public class HomeworkFileTextController {

    @Resource
    private HomeworkFileTextService homeworkFileTextService;

    /** 读取作业文件文本：逐条返回，单个文件失败不影响整批（HTTP 恒 200） */
    @GetMapping("/file-text")
    public CommonResult<HomeworkFileTextRespVO> fileText() {
        return success(homeworkFileTextService.readAll(mockGradeReq()));
    }

    /**
     * 联调用的模拟批改请求：**需要替换的就是这里**。
     *
     * <p>字段说明：{@code taskId} 仅在 fileId 缺失时用于回查 homework_file；
     * {@code name}（扩展名）决定要不要按图片跳过，{@code path} 用于回查与结果回显；
     * {@code url} 本接口取字节不走预签名 URL，故不填。
     */
    private HomeworkGradeReqVO mockGradeReq() {
        HomeworkFileItemVO first = new HomeworkFileItemVO();
        first.setId(31L);
        first.setFileId(98L);
        first.setName("test1.txt");
        first.setPath("test/test1.txt");
        first.setType("text/plain");

        HomeworkFileItemVO second = new HomeworkFileItemVO();
        second.setId(32L);
        second.setFileId(99L);
        second.setName("test1.txt");
        second.setPath("test/test1.zip/test1.txt");
        second.setType("application/octet-stream");

        HomeworkGradeReqVO reqVO = new HomeworkGradeReqVO();
        reqVO.setTaskId(15L);
        reqVO.setSpaceIds(List.of("default", "2", "1"));
        reqVO.setFiles(List.of(first, second));
        reqVO.setDescription("cs");
        reqVO.setExportFormat("xlsx");
        reqVO.setWorkflowId("1000000000000000001");
        return reqVO;
    }

}
