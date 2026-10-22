package org.example.backend.module.test.model;

import lombok.Data;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Set;


@Data
public class AdminUserDO{

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String remark;
    private String email;
    private String avatar;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginDate;

}