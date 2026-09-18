-- =============================================================
-- TrueBuddy - 身份测试补充数据（种子数据）
-- 普通用户 / 会员 / 管理员 三种身份
-- 密码列当前不参与校验（AdminUserServiceImpl.isPasswordMatch 恒 true），占位即可
-- =============================================================

-- 角色
INSERT INTO system_role (id, name, code, status, remark) VALUES
    (1, '普通用户', 'normal', 0, '普通用户身份'),
    (2, '会员',     'member', 0, '会员身份'),
    (3, '管理员',   'admin', 0, '管理员身份')
ON CONFLICT (id) DO NOTHING;

-- 用户
INSERT INTO system_user (id, username, password, nickname, status) VALUES
    (2, 'normal', '123456', '普通用户', 0),
    (3, 'member', '123456', '会员', 0)
ON CONFLICT (id) DO NOTHING;

-- 用户-角色关联
INSERT INTO system_user_role (user_id, role_id) VALUES
    (2, 1),  -- normal -> 普通用户
    (3, 2),  -- member -> 会员
    (1, 3)   -- admin  -> 管理员
ON CONFLICT DO NOTHING;

-- 修正自增序列
SELECT setval('system_role_id_seq', 3, true);
SELECT setval('system_user_id_seq', 3, true);
SELECT setval('system_user_role_id_seq', 3, true);