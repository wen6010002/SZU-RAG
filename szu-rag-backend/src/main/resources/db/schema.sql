-- ============================================================
-- SZU-RAG 数据库初始化脚本
-- 适用于 MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS szu_rag DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE szu_rag;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    username        VARCHAR(64)  NOT NULL COMMENT '用户名',
    password        VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    nickname        VARCHAR(64)  DEFAULT '' COMMENT '昵称',
    role            VARCHAR(20)  DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) COMMENT '用户表';

-- 2. 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    name            VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description     VARCHAR(512) DEFAULT '' COMMENT '描述',
    collection_name VARCHAR(128) NOT NULL COMMENT 'Milvus集合名',
    embedding_dim   INT          DEFAULT 1024 COMMENT '向量维度',
    chunk_strategy  VARCHAR(32)  DEFAULT 'STRUCTURE_AWARE' COMMENT '分块策略',
    chunk_size      INT          DEFAULT 500 COMMENT '分块大小',
    chunk_overlap   INT          DEFAULT 50 COMMENT '重叠字符数',
    document_count  INT          DEFAULT 0 COMMENT '文档数量',
    chunk_count     INT          DEFAULT 0 COMMENT '分块数量',
    status          VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态',
    user_id         BIGINT       NOT NULL COMMENT '创建者ID',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_collection (collection_name)
) COMMENT '知识库表';

-- 3. 知识库文档表
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    knowledge_base_id BIGINT     NOT NULL COMMENT '知识库ID',
    title           VARCHAR(512) NOT NULL COMMENT '文档标题',
    file_name       VARCHAR(256) DEFAULT '' COMMENT '原始文件名',
    file_path       VARCHAR(512) DEFAULT '' COMMENT '存储路径',
    file_size       BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
    mime_type       VARCHAR(128) DEFAULT '' COMMENT 'MIME类型',
    source_url      VARCHAR(500) DEFAULT '' COMMENT '来源URL（爬虫推送时有值）',
    source_type     VARCHAR(20)  DEFAULT 'MANUAL' COMMENT '来源: MANUAL/CRAWLER/UPLOAD',
    document_status VARCHAR(20)  DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PARSING/CHUNKING/INDEXING/COMPLETED/FAILED',
    process_mode    VARCHAR(20)  DEFAULT 'PIPELINE' COMMENT '处理模式: PIPELINE/CHUNK',
    error_message   TEXT         COMMENT '错误信息',
    chunk_count     INT          DEFAULT 0 COMMENT '分块数量',
    user_id         BIGINT       NOT NULL COMMENT '上传者ID',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_status (document_status)
) COMMENT '知识库文档表';

-- 4. 文档分块表
CREATE TABLE IF NOT EXISTS t_document_chunk (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    document_id     BIGINT       NOT NULL COMMENT '文档ID',
    knowledge_base_id BIGINT     NOT NULL COMMENT '知识库ID',
    chunk_index     INT          NOT NULL COMMENT '分块序号',
    chunk_text      TEXT         NOT NULL COMMENT '分块文本',
    char_count      INT          DEFAULT 0 COMMENT '字符数',
    chunk_hash      VARCHAR(64)  DEFAULT '' COMMENT '内容哈希',
    source_title    VARCHAR(512) DEFAULT '' COMMENT '来源文档标题',
    source_url      VARCHAR(500) DEFAULT '' COMMENT '来源URL',
    publish_date    DATE         COMMENT '发布日期',
    metadata        JSON         COMMENT '扩展元数据（预留）',
    milvus_id       BIGINT       COMMENT 'Milvus中的ID',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document (document_id),
    INDEX idx_kb (knowledge_base_id),
    INDEX idx_milvus (milvus_id)
) COMMENT '文档分块表';

-- 5. 对话表
CREATE TABLE IF NOT EXISTS t_conversation (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    title           VARCHAR(256) DEFAULT '' COMMENT '对话标题',
    status          VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
    message_count   INT          DEFAULT 0 COMMENT '消息数量',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) COMMENT '对话表';

-- 6. 消息表
CREATE TABLE IF NOT EXISTS t_message (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    conversation_id BIGINT       NOT NULL COMMENT '对话ID',
    role            VARCHAR(20)  NOT NULL COMMENT '角色: USER/ASSISTANT/SYSTEM',
    content         MEDIUMTEXT   COMMENT '消息内容',
    sources         JSON         COMMENT '引用来源（Assistant消息）',
    token_count     INT          DEFAULT 0 COMMENT 'Token数量',
    model_name      VARCHAR(64)  DEFAULT '' COMMENT '使用的模型',
    duration_ms     INT          DEFAULT 0 COMMENT '响应耗时(ms)',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id)
) COMMENT '消息表';

-- 7. 校园日历表

-- 文档元数据扩展列
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS source_department VARCHAR(128) DEFAULT '' COMMENT '来源部门';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS document_type VARCHAR(32) DEFAULT '' COMMENT '文档类型: notice/policy/procedure/schedule';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS category VARCHAR(64) DEFAULT '' COMMENT '分类: enrollment/exam/teaching...';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS academic_year VARCHAR(20) DEFAULT '' COMMENT '学年';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS semester VARCHAR(20) DEFAULT '' COMMENT '学期';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS target_audience VARCHAR(32) DEFAULT '' COMMENT '目标受众: undergraduate/postgraduate/staff/all';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS source_site VARCHAR(32) DEFAULT '' COMMENT '来源站点: jwb/www/hqb/xsb/gwt';

-- 分块元数据扩展列
ALTER TABLE t_document_chunk ADD COLUMN IF NOT EXISTS source_department VARCHAR(128) DEFAULT '' COMMENT '来源部门';
ALTER TABLE t_document_chunk ADD COLUMN IF NOT EXISTS document_type VARCHAR(32) DEFAULT '' COMMENT '文档类型';
ALTER TABLE t_document_chunk ADD COLUMN IF NOT EXISTS category VARCHAR(64) DEFAULT '' COMMENT '分类';
ALTER TABLE t_document_chunk ADD COLUMN IF NOT EXISTS source_site VARCHAR(32) DEFAULT '' COMMENT '来源站点';

-- 全文检索索引（用于混合检索）
ALTER TABLE t_document_chunk ADD FULLTEXT INDEX IF NOT EXISTS ft_chunk_text (chunk_text) WITH PARSER ngram;

-- 7. 校园日历表
CREATE TABLE IF NOT EXISTS t_campus_calendar (
    id              BIGINT PRIMARY KEY COMMENT '雪花ID',
    academic_year   VARCHAR(20)   NOT NULL COMMENT '学年，如 2025-2026',
    semester        VARCHAR(20)   NOT NULL COMMENT '学期：第一学期/第二学期/暑期',
    start_date      DATE          NOT NULL COMMENT '学期开始日期',
    end_date        DATE          NOT NULL COMMENT '学期结束日期',
    week_count      INT           NOT NULL COMMENT '总教学周数',
    event_name      VARCHAR(100)  NOT NULL COMMENT '事件名称',
    event_type      VARCHAR(50)   NOT NULL COMMENT '事件类型：teaching/exam/enrollment/holiday/registration',
    event_start     DATE          NOT NULL COMMENT '事件开始日期',
    event_end       DATE          NULL     COMMENT '事件结束日期',
    description     VARCHAR(500)  NULL     COMMENT '补充说明',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_event_dates (event_start, event_end)
) COMMENT '校园日历表';

-- 初始管理员（密码: admin123，BCrypt加密 - Hutool BCrypt 兼容）
INSERT INTO t_user (id, username, password, nickname, role)
VALUES (1, 'admin', '$2a$10$aX4KEdZp2w7Qz4XeB0tZ2.ieAM28ME0LJnors/UD6504TuLJ0aouC', '管理员', 'ADMIN')
ON DUPLICATE KEY UPDATE password=VALUES(password), nickname=VALUES(nickname);
