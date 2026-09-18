package org.example.backend.framework.common.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页返回结果
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> list;
    /** 总记录数 */
    private Long total;

    public PageResult() {
        this.list = new ArrayList<>();
        this.total = 0L;
    }

    public PageResult(List<T> list, Long total) {
        this.list = list;
        this.total = total;
    }
}
