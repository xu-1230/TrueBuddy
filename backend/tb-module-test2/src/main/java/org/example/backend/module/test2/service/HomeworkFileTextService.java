package org.example.backend.module.test2.service;

import org.example.backend.module.homework.model.vo.HomeworkFileItemVO;
import org.example.backend.module.homework.model.vo.HomeworkGradeReqVO;
import org.example.backend.module.test2.model.vo.FileTextItemVO;
import org.example.backend.module.test2.model.vo.HomeworkFileTextRespVO;

/**
 * 作业文件文本读取 Service
 *
 * <p>只读能力：把 MinIO 里的作业文件解析为纯文本，供后续 AI 批改（MCP 工具）复用。
 * 不落库、不调用工作流与大模型。
 */
public interface HomeworkFileTextService {

    /**
     * 批量读取：按请求体里的文件清单逐条提取文本（串行）。
     *
     * @param reqVO 批改请求参数，本接口只使用 taskId 与 files
     * @return 逐条结果与成功/失败计数
     */
    HomeworkFileTextRespVO readAll(HomeworkGradeReqVO reqVO);

    /**
     * 读取单个文件的文本（原子方法，后续 MCP 工具直接复用）。
     *
     * <p>任何失败都不抛异常，而是写入返回对象的 {@code error}。
     *
     * @param taskId 批改任务 id，用于 fileId 缺失时回查 homework_file
     * @param item   文件明细
     * @return 单条结果
     */
    FileTextItemVO readOne(Long taskId, HomeworkFileItemVO item);

}
