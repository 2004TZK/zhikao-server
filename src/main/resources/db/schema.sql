-- =============================================================
-- 知考（Zhikao）数据库建表脚本 schema.sql
-- 契约来源：任务书 v1.4 第五章（5.2 表结构 / 5.3 数据治理 / 5.4 索引 / 5.5 枚举）
-- 数据库：MySQL 8.x；字符集 utf8mb4；引擎 InnoDB
-- 说明：内容数据一律逻辑下架（status=0），不做物理删除
-- =============================================================

CREATE DATABASE IF NOT EXISTS zhikao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhikao;

-- ---------------------------------------------------------------
-- 1. user 用户表（正式用户与游客共用同一 user_id 体系，v1.4 定稿）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_type           TINYINT      NOT NULL DEFAULT 1 COMMENT '用户类型：1 正式用户 2 游客',
    username            VARCHAR(50)  NOT NULL COMMENT '登录账号名（游客为系统生成占位名，不用于登录）',
    guest_uuid          VARCHAR(36)  NULL COMMENT '游客身份 UUID（user_type=2 必填，正式用户 NULL）',
    password_hash       VARCHAR(255) NULL COMMENT 'BCrypt 密码哈希（游客为 NULL，严禁存明文）',
    nickname            VARCHAR(50)  NULL COMMENT '昵称',
    avatar              VARCHAR(255) NULL COMMENT '头像 URL',
    status              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 正常 1 禁用 2 已注销',
    streak_days         INT          NOT NULL DEFAULT 0 COMMENT '连续学习天数',
    last_study_date     DATE         NULL COMMENT '最近学习日期（连续天数计算）',
    total_study_minutes INT          NOT NULL DEFAULT 0 COMMENT '累计学习时长（分钟），冗余缓存，事实来源=study_record 聚合',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_guest_uuid (guest_uuid)
) ENGINE = InnoDB COMMENT ='用户';

-- ---------------------------------------------------------------
-- 2. knowledge_category 常识分类
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_category (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(20)  NOT NULL COMMENT '分类名（政治/法律/经济/历史/地理/科技/文化/生态/生活）',
    icon       VARCHAR(100) NULL COMMENT '图标标识',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 下架 1 上架（分类也走逻辑下架）',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB COMMENT ='常识分类';

-- ---------------------------------------------------------------
-- 3. knowledge 常识知识点
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title              VARCHAR(100) NOT NULL COMMENT '标题',
    category_id        BIGINT       NOT NULL COMMENT '分类 id',
    summary            VARCHAR(255) NULL COMMENT '一句话记忆',
    content            TEXT         NULL COMMENT '核心知识',
    key_points         TEXT         NULL COMMENT '重点内容（JSON 数组）',
    common_mistakes    TEXT         NULL COMMENT '易错点',
    difficulty         TINYINT      NOT NULL DEFAULT 3 COMMENT '难度 1-5',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 下架 1 上架',
    source_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '来源：1 自建 2 公共领域资料 3 合法授权 4 文档导入',
    source_document_id BIGINT       NULL COMMENT '来源 import_document.id（source_type=4 必填）',
    source_title       VARCHAR(200) NULL COMMENT '来源文件名/原始资料标题',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category (category_id, status),
    KEY idx_source_doc (source_document_id)
) ENGINE = InnoDB COMMENT ='常识知识点';

-- ---------------------------------------------------------------
-- 4. idiom_category 成语分类
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idiom_category (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(20)  NOT NULL COMMENT '分类名（高频成语/易错成语/近义辨析/常见误用/真题语境）',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 下架 1 上架',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB COMMENT ='成语分类';

-- ---------------------------------------------------------------
-- 5. idiom_category_rel 成语-分类关联（多对多）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idiom_category_rel (
    id          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    idiom_id    BIGINT  NOT NULL COMMENT '成语 id',
    category_id BIGINT  NOT NULL COMMENT '分类 id',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rel (idiom_id, category_id)
) ENGINE = InnoDB COMMENT ='成语-分类关联';

-- ---------------------------------------------------------------
-- 6. idiom 成语
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idiom (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    word               VARCHAR(20)  NOT NULL COMMENT '成语',
    pinyin             VARCHAR(100) NULL COMMENT '拼音',
    explanation        TEXT         NULL COMMENT '释义',
    origin             TEXT         NULL COMMENT '出处',
    example            TEXT         NULL COMMENT '例句',
    synonyms           VARCHAR(500) NULL COMMENT '近义词（JSON 数组）',
    antonyms           VARCHAR(500) NULL COMMENT '反义词（JSON 数组）',
    confusing          TEXT         NULL COMMENT '易混成语及辨析（JSON）',
    common_error       TEXT         NULL COMMENT '常见误用',
    difficulty         TINYINT      NOT NULL DEFAULT 3 COMMENT '难度',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 下架 1 上架',
    source_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '来源：1 自建 2 公共领域资料 3 合法授权 4 文档导入',
    source_document_id BIGINT       NULL COMMENT '来源 import_document.id',
    source_title       VARCHAR(200) NULL COMMENT '来源文件名/原始资料标题',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word (word),
    KEY idx_source_doc (source_document_id)
) ENGINE = InnoDB COMMENT ='成语';

-- ---------------------------------------------------------------
-- 7. question 题目
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    type               TINYINT      NOT NULL DEFAULT 1 COMMENT '题型：1 单选（V1.0 只用），2 多选 3 判断 4 辨析（预留）',
    content            TEXT         NOT NULL COMMENT '题干',
    analysis           TEXT         NULL COMMENT '解析',
    difficulty         TINYINT      NOT NULL DEFAULT 3 COMMENT '难度',
    knowledge_id       BIGINT       NULL COMMENT '关联常识知识点（与 idiom_id 至少一个非空）',
    idiom_id           BIGINT       NULL COMMENT '关联成语',
    source_type        TINYINT      NOT NULL DEFAULT 3 COMMENT '来源：1 真题 2 模拟题 3 自编题 4 AI 生成题（V2.0）',
    source_name        VARCHAR(100) NULL COMMENT '来源名称（如 2024 年国考行测）',
    exam_year          SMALLINT     NULL COMMENT '考试年份',
    province           VARCHAR(20)  NULL COMMENT '省份（省考用）',
    question_no        VARCHAR(20)  NULL COMMENT '原题题号',
    source_document_id BIGINT       NULL COMMENT '文档导入来源 import_document.id',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0 下架 1 上架',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_knowledge (knowledge_id),
    KEY idx_idiom (idiom_id),
    KEY idx_source_doc (source_document_id),
    CONSTRAINT chk_question_assoc CHECK (knowledge_id IS NOT NULL OR idiom_id IS NOT NULL)
) ENGINE = InnoDB COMMENT ='题目';

-- ---------------------------------------------------------------
-- 8. question_option 题目选项
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_option (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id BIGINT       NOT NULL COMMENT '题目 id',
    label       CHAR(1)      NOT NULL COMMENT '选项标号 A/B/C/D...',
    content     VARCHAR(500) NOT NULL COMMENT '选项内容',
    is_correct  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否正确答案：0 否 1 是',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question (question_id)
) ENGINE = InnoDB COMMENT ='题目选项';

-- ---------------------------------------------------------------
-- 9. user_knowledge 用户-知识点学习状态（掌握度核心表）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_knowledge (
    id               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT   NOT NULL COMMENT '用户 id',
    knowledge_id     BIGINT   NOT NULL COMMENT '知识点 id',
    mastery          TINYINT  NOT NULL DEFAULT 0 COMMENT '掌握度：0 未学习 1 学习中 2 已学习 3 掌握 4 熟练',
    review_stage     INT      NOT NULL DEFAULT 0 COMMENT '当前复习阶段（间隔梯度索引 0~5）',
    study_count      INT      NOT NULL DEFAULT 0 COMMENT '学习次数',
    correct_count    INT      NOT NULL DEFAULT 0 COMMENT '答对次数',
    wrong_count      INT      NOT NULL DEFAULT 0 COMMENT '答错次数',
    last_study_time  DATETIME NULL COMMENT '最近学习时间',
    next_review_time DATETIME NULL COMMENT '下次复习时间（调度核心字段）',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_knowledge (user_id, knowledge_id),
    KEY idx_review (user_id, next_review_time)
) ENGINE = InnoDB COMMENT ='用户-知识点学习状态';

-- ---------------------------------------------------------------
-- 10. user_idiom 用户-成语学习状态
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_idiom (
    id               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT   NOT NULL COMMENT '用户 id',
    idiom_id         BIGINT   NOT NULL COMMENT '成语 id',
    mastery          TINYINT  NOT NULL DEFAULT 0 COMMENT '掌握度：0 未学习 1 学习中 2 已学习 3 掌握 4 熟练',
    review_stage     INT      NOT NULL DEFAULT 0 COMMENT '当前复习阶段（间隔梯度索引 0~5）',
    study_count      INT      NOT NULL DEFAULT 0 COMMENT '学习次数',
    correct_count    INT      NOT NULL DEFAULT 0 COMMENT '答对次数',
    wrong_count      INT      NOT NULL DEFAULT 0 COMMENT '答错次数',
    last_study_time  DATETIME NULL COMMENT '最近学习时间',
    next_review_time DATETIME NULL COMMENT '下次复习时间（调度核心字段）',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_idiom (user_id, idiom_id),
    KEY idx_review (user_id, next_review_time)
) ENGINE = InnoDB COMMENT ='用户-成语学习状态';

-- ---------------------------------------------------------------
-- 11. practice_session 练习场次
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS practice_session (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT   NOT NULL COMMENT '用户 id（游客为 user_type=2 的 user.id）',
    type          TINYINT  NOT NULL COMMENT '类型：1 每日挑战 2 专项训练 3 随机训练 4 复习 5 错题重练',
    ref_type      TINYINT  NOT NULL DEFAULT 6 COMMENT 'ref_id 指向对象类型：1 常识分类 2 成语分类 3 知识点 4 成语 5 错题 6 无',
    ref_id        BIGINT   NULL COMMENT 'ref_type 对应目标 id（ref_type=6 时为 NULL）',
    total_count   INT      NOT NULL DEFAULT 0 COMMENT '题目总数',
    correct_count INT      NOT NULL DEFAULT 0 COMMENT '答对数',
    start_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time      DATETIME NULL COMMENT '结束时间',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, start_time)
) ENGINE = InnoDB COMMENT ='练习场次';

-- ---------------------------------------------------------------
-- 12. user_answer 答题明细
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_answer (
    id                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT   NOT NULL COMMENT '用户 id',
    session_id        BIGINT   NOT NULL COMMENT '练习场次 id',
    question_id       BIGINT   NOT NULL COMMENT '题目 id',
    selected_option_id BIGINT  NULL COMMENT '所选选项 id',
    is_correct        TINYINT  NOT NULL DEFAULT 0 COMMENT '是否正确：0 否 1 是',
    answer_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session (session_id, question_id),
    KEY idx_user_time (user_id, answer_time)
) ENGINE = InnoDB COMMENT ='答题明细';

-- ---------------------------------------------------------------
-- 13. wrong_question 错题本
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wrong_question (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT   NOT NULL COMMENT '用户 id',
    question_id     BIGINT   NOT NULL COMMENT '题目 id',
    wrong_count     INT      NOT NULL DEFAULT 1 COMMENT '累计错误次数',
    last_wrong_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近错误时间',
    is_removed      TINYINT  NOT NULL DEFAULT 0 COMMENT '是否移出：0 在错题本 1 已移出',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_question (user_id, question_id),
    KEY idx_list (user_id, is_removed, last_wrong_time)
) ENGINE = InnoDB COMMENT ='错题本';

-- ---------------------------------------------------------------
-- 14. favorite 收藏
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户 id',
    target_type TINYINT  NOT NULL COMMENT '目标类型：1 知识点 2 成语',
    target_id   BIGINT   NOT NULL COMMENT '目标 id',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (user_id, target_type, target_id)
) ENGINE = InnoDB COMMENT ='收藏';

-- ---------------------------------------------------------------
-- 15. study_record 学习记录
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_record (
    id               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT   NOT NULL COMMENT '用户 id',
    record_type      TINYINT  NOT NULL COMMENT '类型：1 学知识点 2 学成语 3 练习 4 复习',
    target_id        BIGINT   NULL COMMENT '目标 id',
    duration_seconds INT      NOT NULL DEFAULT 0 COMMENT '本次时长（秒），服务端校验 0~1800',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE = InnoDB COMMENT ='学习记录';

-- ---------------------------------------------------------------
-- 16. review_record 复习记录
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS review_record (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户 id',
    target_type TINYINT  NOT NULL COMMENT '目标类型：1 知识点 2 成语',
    target_id   BIGINT   NOT NULL COMMENT '目标 id',
    review_type TINYINT  NOT NULL DEFAULT 2 COMMENT '复习类型：1 即将遗忘 2 普通 3 错题强化',
    is_correct  TINYINT  NOT NULL DEFAULT 0 COMMENT '复习自测结果：0 错 1 对',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE = InnoDB COMMENT ='复习记录';

-- ---------------------------------------------------------------
-- 17. daily_task 每日任务
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_task (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户 id',
    task_date   DATE     NOT NULL COMMENT '任务日期',
    task_type   TINYINT  NOT NULL COMMENT '类型：1 常识积累 2 成语训练 3 今日复习',
    target_count INT     NOT NULL DEFAULT 0 COMMENT '目标数量（复习类=min(到期数,20)）',
    done_count  INT      NOT NULL DEFAULT 0 COMMENT '已完成数量',
    status      TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0 未完成 1 已完成',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_task (user_id, task_date, task_type)
) ENGINE = InnoDB COMMENT ='每日任务';

-- ---------------------------------------------------------------
-- 18. import_document 知识库导入：文件任务
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS import_document (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name      VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path      VARCHAR(500) NOT NULL COMMENT '存储路径（FileStorage 相对 key）',
    file_type      VARCHAR(20)  NOT NULL COMMENT '文件类型：TEXT_PDF/SCANNED_PDF/DOCX/DOC/TXT',
    file_size      BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    import_type    TINYINT      NOT NULL DEFAULT 4 COMMENT '导入类型：1 常识知识库 2 成语知识库 3 题库 4 自动识别',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 上传成功 1 解析中 2 解析完成 3 审核中 4 已完成 5 解析失败',
    total_sections INT          NOT NULL DEFAULT 0 COMMENT '识别出的内容块总数',
    success_count  INT          NOT NULL DEFAULT 0 COMMENT '审核通过入库条数',
    failed_count   INT          NOT NULL DEFAULT 0 COMMENT '驳回/解析失败条数',
    created_by     BIGINT       NOT NULL COMMENT '管理员 id',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='知识库导入：文件任务';

-- ---------------------------------------------------------------
-- 19. import_record 知识库导入：解析草稿与审核记录
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS import_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id    BIGINT       NOT NULL COMMENT '所属 import_document id',
    content_type   TINYINT      NOT NULL COMMENT '内容类型：1 知识点 2 成语 3 题目',
    source_title   VARCHAR(200) NULL COMMENT '解析出的块标题',
    parsed_content TEXT         NULL COMMENT '结构化解结果（JSON）',
    raw_excerpt    TEXT         NULL COMMENT '对应原文摘录（审核对照用）',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待审核 1 已通过入库 2 已驳回 3 已失效',
    error_message  VARCHAR(500) NULL COMMENT '驳回原因/解析错误信息',
    target_id      BIGINT       NULL COMMENT '入库后对应的 knowledge/idiom/question id',
    reviewed_by    BIGINT       NULL COMMENT '审核人 id',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_doc_status (document_id, status)
) ENGINE = InnoDB COMMENT ='知识库导入：解析草稿与审核记录';

-- =============================================================
-- 完成：共 19 张表（user, knowledge_category, knowledge, idiom_category,
-- idiom_category_rel, idiom, question, question_option, user_knowledge,
-- user_idiom, practice_session, user_answer, wrong_question, favorite,
-- study_record, review_record, daily_task, import_document, import_record）
-- 索引：5.4 全部唯一/普通索引均已落地
-- =============================================================
