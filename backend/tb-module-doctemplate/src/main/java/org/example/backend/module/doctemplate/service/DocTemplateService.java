package org.example.backend.module.doctemplate.service;

import org.example.backend.module.doctemplate.model.vo.GenerateReqVO;
import org.example.backend.module.doctemplate.model.vo.GenerationHistoryRespVO;
import org.example.backend.module.doctemplate.model.vo.GenerationRespVO;

import java.util.List;

/**
 * 文档模板生成 Service（模板复刻编排 + 历史查询）
 */
public interface DocTemplateService {

    /**
     * 生成文档：以模板为基底、结合知识库检索与描述说明，产出多份同版式文档。
     *
     * @param reqVO 生成请求（模板文件 id / 知识库空间 / 描述 / 清晰度）
     * @return 生成任务信息 + 文件列表
     */
    GenerationRespVO generate(GenerateReqVO reqVO);

    /**
     * 查询生成历史（按时间倒序）
     *
     * @param limit 最多返回条数
     */
    List<GenerationHistoryRespVO> getHistory(int limit);

    /**
     * 查询生成详情（任务 + 文件列表）
     *
     * @param id 生成任务 id
     */
    GenerationRespVO getDetail(Long id);

}
