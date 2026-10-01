package org.example.textflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 节点在画布上的坐标。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowPosition {

    /** 横坐标 */
    private Double x;

    /** 纵坐标 */
    private Double y;

}
