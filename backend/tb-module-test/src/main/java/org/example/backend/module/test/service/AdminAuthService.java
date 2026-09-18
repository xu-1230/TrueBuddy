package org.example.backend.module.test.service;

import org.example.backend.module.test.model.AdminUserDO;
import org.example.backend.module.test.model.vo.AuthLoginReqVO;
import org.example.backend.module.test.model.vo.AuthLoginRespVO;

public interface AdminAuthService {

    AdminUserDO authenticate(String username, String password);

    AuthLoginRespVO login(AuthLoginReqVO reqVO);


}
