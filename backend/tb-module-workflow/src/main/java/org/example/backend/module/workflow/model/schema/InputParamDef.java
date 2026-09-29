package org.example.backend.module.workflow.model.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 命名的输入项：{@code {name, input}}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputParamDef {

    /** 变量名（模板中以 {@code {{name}}} 引用） */
    private String name;

    /** 取值槽位 */
    private InputSlotDef input;

}
