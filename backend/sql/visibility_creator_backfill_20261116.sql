-- =============================================================
-- 2026-11-16 归属地基：kb_space / workflow 增加 visibility 列
-- 依据：docs/adr/0006-知识库与工作流归属与共享模型.md
--   creator 为归属唯一真相源；visibility 0=私有，1=公有
-- 决策「存量全部私有」：visibility 一律刷 0；
--   存量 creator 从未被写入（历史 INSERT 只填 name/description），
--   统一归入 admin 种子账号（system_user.id = 1），保证演示数据仍可见。
-- =============================================================

ALTER TABLE kb_space ADD COLUMN IF NOT EXISTS visibility SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN kb_space.visibility IS '可见性：0=私有，1=公有';

ALTER TABLE workflow ADD COLUMN IF NOT EXISTS visibility SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN workflow.visibility IS '可见性：0=私有，1=公有';

-- 存量归属补齐（幂等：只补 NULL 行）
UPDATE kb_space SET creator = 1 WHERE creator IS NULL;
UPDATE workflow SET creator = 1 WHERE creator IS NULL;
