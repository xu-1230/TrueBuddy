package org.example.backend.module.knowledgebase.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayoutVO {
    private String markdownContent;
    private Integer index;
    private String text;
    private String type;
    private Integer pageNum;
    private String uniqueId;
}
