-- =============================================================
-- TrueBuddy - 作业批改：评分区间 + 自定义导出列（2026-11-17）
--
-- 目标：
--   1. homework_task 增加 score_min / score_max / pass_score 与 custom_fields；
--   2. homework_grading_result 增加 custom（自定义列的值），并把旧 comment / deductions
--      一次性迁入 custom（旧列保留为 legacy，停止写入与读取）；
--   3. 存量任务回填 0/100/60 与两列预置定义（扣分明细 / AI 评语）。
--
-- 可重复执行：所有新增列用 IF NOT EXISTS，回填只处理 null / 尚未迁移的值。
-- 注意：本脚本只负责存量库；新环境直接由 homework_task.sql / homework_grading_result.sql 建出。
-- =============================================================

BEGIN;

-- ---------- homework_task：评分区间 ----------
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS score_min NUMERIC(6,2);
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS score_max NUMERIC(6,2);
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS pass_score NUMERIC(6,2);

UPDATE homework_task
SET score_min  = COALESCE(score_min, 0),
    score_max  = COALESCE(score_max, 100),
    pass_score = COALESCE(pass_score, 60)
WHERE score_min IS NULL OR score_max IS NULL OR pass_score IS NULL;

ALTER TABLE homework_task ALTER COLUMN score_min  SET DEFAULT 0;
ALTER TABLE homework_task ALTER COLUMN score_max  SET DEFAULT 100;
ALTER TABLE homework_task ALTER COLUMN pass_score SET DEFAULT 60;
ALTER TABLE homework_task ALTER COLUMN score_min  SET NOT NULL;
ALTER TABLE homework_task ALTER COLUMN score_max  SET NOT NULL;
ALTER TABLE homework_task ALTER COLUMN pass_score SET NOT NULL;

COMMENT ON COLUMN homework_task.score_min  IS '评分区间下限；默认 0';
COMMENT ON COLUMN homework_task.score_max  IS '评分区间上限（满分）；默认 100';
COMMENT ON COLUMN homework_task.pass_score IS '及格线，必须落在 [score_min, score_max]；默认 60';

-- 存量任务的区间从 description 里的「0-200 / 60-100」恢复（旧版没有结构化字段）：
--   - 有下限且下限 > 0 → 下限即及格线（60-100 记 60）；
--   - 下限为 0 → 及格线取满分的 60%（0-200 记 120，0-100 记 60）。
WITH parsed AS (
    SELECT id,
           (regexp_match(description, '(\d+(?:\.\d+)?)\s*[-~—–至到]\s*(\d+(?:\.\d+)?)')) AS m
    FROM homework_task
    WHERE description ~ '\d+(?:\.\d+)?\s*[-~—–至到]\s*\d+(?:\.\d+)?'
)
UPDATE homework_task t
SET score_min = p.m[1]::numeric,
    score_max = p.m[2]::numeric,
    pass_score = CASE
                   WHEN p.m[1]::numeric > 0 THEN p.m[1]::numeric
                   ELSE round(p.m[2]::numeric * 0.6, 2)
                 END
FROM parsed p
WHERE t.id = p.id
  AND p.m[1]::numeric < p.m[2]::numeric;

-- ---------- homework_task：自定义导出列定义 ----------
ALTER TABLE homework_task ADD COLUMN IF NOT EXISTS custom_fields JSONB;

-- 两列预置定义：扣分明细在前、AI 评语在后；每列都由模型逐学生产出。
UPDATE homework_task
SET custom_fields = $json$[
  {
    "key": "deductions",
    "description": "扣分明细：逐项列出失分项与扣分，每行一条：失分项 −扣分：依据（尽量引用原文片段）。",
    "type": "string"
  },
  {
    "key": "comment",
    "description": "AI 评语：面向学生本人的评语：先肯定具体优点，再指出具体问题与改进方向，与扣分明细一一对应。",
    "type": "string"
  }
]$json$::jsonb
WHERE custom_fields IS NULL
   OR (custom_fields = '[]'::jsonb AND description IS NOT NULL);

-- 2026-11-17 早期的定义里带过已废弃的 default（静态列）；统一剥掉，避免前端读到旧键。
UPDATE homework_task
SET custom_fields = (
  SELECT COALESCE(
    jsonb_agg(
      CASE
        WHEN jsonb_typeof(elem) = 'object' THEN elem - 'default'
        ELSE elem
      END
      ORDER BY ord
    ),
    '[]'::jsonb
  )
  FROM jsonb_array_elements(custom_fields) WITH ORDINALITY AS t(elem, ord)
)
WHERE jsonb_typeof(custom_fields) = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements(custom_fields) AS e
    WHERE jsonb_typeof(e) = 'object' AND e ? 'default'
  );

-- 变量名就是 key：剥掉早期定义的 label；已有 label + description 的，把 label 并进描述前缀。
UPDATE homework_task
SET custom_fields = (
  SELECT jsonb_agg(
    CASE
      WHEN jsonb_typeof(elem) = 'object' AND elem ? 'label' THEN
        jsonb_set(
          elem,
          '{description}',
          to_jsonb(
            COALESCE(NULLIF(elem->>'label', '') || '：', '')
            || COALESCE(elem->>'description', '')
          )
        ) - 'label'
      ELSE elem
    END
    ORDER BY ord
  )
  FROM jsonb_array_elements(custom_fields) WITH ORDINALITY AS t(elem, ord)
)
WHERE jsonb_typeof(custom_fields) = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements(custom_fields) AS e
    WHERE jsonb_typeof(e) = 'object' AND e ? 'label'
  );

-- 类型归一：早期用 text，工作流变量类型用 string；统一成 string（评分列本来就是 number）。
UPDATE homework_task
SET custom_fields = (
  SELECT jsonb_agg(
    CASE
      WHEN elem->>'type' = 'text' THEN jsonb_set(elem, '{type}', '"string"', false)
      ELSE elem
    END
    ORDER BY ord
  )
  FROM jsonb_array_elements(custom_fields) WITH ORDINALITY AS t(elem, ord)
)
WHERE jsonb_typeof(custom_fields) = 'array'
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements(custom_fields) AS e
    WHERE e->>'type' = 'text'
  );

ALTER TABLE homework_task ALTER COLUMN custom_fields SET DEFAULT '[]'::jsonb;
ALTER TABLE homework_task ALTER COLUMN custom_fields SET NOT NULL;

COMMENT ON COLUMN homework_task.custom_fields IS
  '自定义导出列定义 JSON 数组：{key,description,type,schema}；key 即变量名 / 导出表头；每列由模型逐学生产出';

-- ---------- homework_grading_result：自定义列的值 ----------
ALTER TABLE homework_grading_result ADD COLUMN IF NOT EXISTS custom JSONB;

-- 旧 comment + deductions（结构化列表）→ custom 的两个文本键。
-- 扣分列表格式化成「失分项 −N：依据」，多行用换行分隔；字符串元素原样保留。
UPDATE homework_grading_result r
SET custom = jsonb_build_object(
    'deductions',
    COALESCE(
      CASE
        WHEN jsonb_typeof(r.deductions) = 'array' THEN (
          SELECT string_agg(
            CASE
              WHEN jsonb_typeof(e) = 'object' THEN
                concat_ws(' −', NULLIF(e->>'item', ''), NULLIF(e->>'diff', ''))
                || CASE
                     WHEN NULLIF(e->>'reason', '') IS NOT NULL THEN '：' || (e->>'reason')
                     ELSE ''
                   END
              ELSE trim(both '"' from e::text)
            END,
            E'\n' ORDER BY ord
          )
          FROM jsonb_array_elements(r.deductions) WITH ORDINALITY AS t(e, ord)
        )
        WHEN jsonb_typeof(r.deductions) = 'string' THEN trim(both '"' from r.deductions::text)
        ELSE ''
      END,
      ''
    ),
    'comment', COALESCE(r.comment, '')
  )
WHERE r.custom IS NULL
   OR (
     r.custom = '{}'::jsonb
     AND (
       COALESCE(r.comment, '') <> ''
       OR (r.deductions IS NOT NULL AND r.deductions <> '[]'::jsonb)
     )
   );

-- 旧回写链路曾把扣分对象 `String()` 成 "[object Object]"（结构在那时就丢了）。
-- 迁移无法还原，给一条可读说明，避免成绩卡直接显示一堆 [object Object]。
UPDATE homework_grading_result
SET custom = custom || jsonb_build_object(
    'deductions',
    '旧数据未保存结构化的扣分明细（迁移前已在结果回写中丢失，可查看评语中的扣分说明）'
  )
WHERE custom->>'deductions' LIKE '%[object Object]%';

ALTER TABLE homework_grading_result ALTER COLUMN custom SET DEFAULT '{}'::jsonb;
ALTER TABLE homework_grading_result ALTER COLUMN custom SET NOT NULL;

COMMENT ON COLUMN homework_grading_result.custom IS
  '自定义导出列的值（JSONB 对象，key → 文本/数字）';

COMMIT;
