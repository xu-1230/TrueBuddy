package org.example.textflow.service;

import org.example.textflow.entity.WorkflowProcessLoader;
import org.example.textflow.entity.WorkflowProcessRespVO;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    /** 工作流定义加载后缓存，避免每次请求都读文件 */
    private volatile WorkflowProcessRespVO processCache;

    @Override
    public WorkflowProcessRespVO getProcess(String workflowId) {
        return loadProcess();
    }

    private WorkflowProcessRespVO loadProcess() {
        WorkflowProcessRespVO process = processCache;
        if (process == null) {
            synchronized (this) {
                process = processCache;
                if (process == null) {
                    process = readProcess();
                    processCache = process;
                }
            }
        }
        return process;
    }

    private WorkflowProcessRespVO readProcess() {
        try {
            return WorkflowProcessLoader.loadDefault();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "加载工作流定义失败: " + WorkflowProcessLoader.DEFAULT_LOCATION, e);
        }
    }

}
