package org.example.backend.module.test.controller;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.example.backend.framework.common.pojo.CommonResult;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;
import org.example.backend.module.test.service.AdminAuthService;
import org.springframework.web.bind.annotation.*;

import static org.example.backend.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/system/auth")
public class AuthController {

    @Resource
    private AdminAuthService authService;

    @PostMapping("/login")
    @PermitAll
    public CommonResult<AuthLoginRespVO> login(@RequestBody AuthLoginReqVO reqVO) {
//        AuthLoginReqVO reqVO   = new AuthLoginReqVO();
//        reqVO.setUsername("admin");
//        reqVO.setPassword("admin12344");
        return success( authService.login(reqVO));
    }


}
