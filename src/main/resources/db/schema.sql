-- ============================================
-- 校园智能服务小助手 - 数据库建表脚本
-- MySQL 8.0 / InnoDB / utf8mb4
-- ============================================

CREATE DATABASE IF NOT EXISTS campus_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_assistant;

-- --------------------------------------------
-- 1. 用户表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(64)     NOT NULL COMMENT '用户名',
    password    VARCHAR(256)    NOT NULL COMMENT '密码（BCrypt加密）',
    role        ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT' COMMENT '角色',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

-- --------------------------------------------
-- 2. 对话会话表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id     BIGINT          NOT NULL COMMENT '用户ID',
    thread_id   VARCHAR(64)     DEFAULT NULL COMMENT 'AI对话线程ID',
    title       VARCHAR(256)    DEFAULT NULL COMMENT '会话标题',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_thread_id (thread_id),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

-- --------------------------------------------
-- 3. 消息表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    conversation_id BIGINT      NOT NULL COMMENT '会话ID',
    role            ENUM('USER','ASSISTANT','SYSTEM') NOT NULL COMMENT '消息角色',
    content         TEXT        DEFAULT NULL COMMENT '消息内容',
    metadata        JSON        DEFAULT NULL COMMENT '扩展元数据',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_create_time (create_time),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录';

-- --------------------------------------------
-- 4. 校园知识文档表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    title       VARCHAR(256)    NOT NULL COMMENT '文档标题',
    category    VARCHAR(64)     DEFAULT NULL COMMENT '分类',
    content     TEXT            NOT NULL COMMENT '文档内容',
    vector_id   VARCHAR(128)    DEFAULT NULL COMMENT '向量存储ID',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_category (category),
    INDEX idx_vector_id (vector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园知识文档';

-- --------------------------------------------
-- 5. Agent执行日志表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS agent_execution_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    conversation_id   BIGINT       DEFAULT NULL COMMENT '会话ID',
    user_message      TEXT         DEFAULT NULL COMMENT '用户消息',
    agent_intent      VARCHAR(64)  DEFAULT NULL COMMENT 'Agent识别意图',
    tool_called       VARCHAR(128) DEFAULT NULL COMMENT '调用的工具名称',
    tool_params       JSON         DEFAULT NULL COMMENT '工具参数',
    tool_result       TEXT         DEFAULT NULL COMMENT '工具返回结果',
    final_response    TEXT         DEFAULT NULL COMMENT '最终回复',
    execution_time_ms INT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_agent_intent (agent_intent),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent执行日志';

-- --------------------------------------------
-- 6. RAG评估表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS rag_evaluation (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '评估ID',
    message_id      BIGINT       DEFAULT NULL COMMENT '消息ID',
    rag_enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT 'RAG是否启用',
    retrieved_docs  JSON         DEFAULT NULL COMMENT '检索到的文档列表',
    top_similarity  DECIMAL(10,6) DEFAULT NULL COMMENT '最高相似度',
    response_content TEXT        DEFAULT NULL COMMENT '回复内容',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_message_id (message_id),
    INDEX idx_rag_enabled (rag_enabled),
    CONSTRAINT fk_rag_eval_message FOREIGN KEY (message_id) REFERENCES message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG评估记录';

-- --------------------------------------------
-- 7. 告警日志表
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS alert_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '告警ID',
    alert_type  VARCHAR(64)  NOT NULL COMMENT '告警类型',
    message     TEXT         DEFAULT NULL COMMENT '告警消息',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_alert_type (alert_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警日志';
