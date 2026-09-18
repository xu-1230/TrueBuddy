package org.example.backend.module.test.model;

import lombok.Data;

import java.util.Set;

@Data
public class RoleDO {

    private Long id;
    private String name;
    private String code;
    private Integer status;
    private String remark;

}
