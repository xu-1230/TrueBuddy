package org.example.backend.module.test2.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 作业文件文本读取结果（批量）
 */
@Data
public class HomeworkFileTextRespVO {

    /** 请求的文件总数 */
    private Integer total;
    /** 成功提取到文本的数量 */
    private Integer success;
    /** 失败数量（失败原因见各条的 error） */
    private Integer failed;
    /** 逐条结果，顺序与请求体 files 一致 */
    private List<FileTextItemVO> items;
}
