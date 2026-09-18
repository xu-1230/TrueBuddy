package org.example.backend.module.test.controller;

import org.example.backend.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试模块：接口功能连通性验证
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/ping")
    public CommonResult<String> ping() {
        return CommonResult.success("pong");
    }


}