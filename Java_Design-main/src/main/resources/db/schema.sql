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

-- --------------------------------------------
-- 8. 课表表（阶段 5-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS course_schedule (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '课程ID',
    student_id  VARCHAR(32)  NOT NULL COMMENT '学号',
    course_name VARCHAR(128) NOT NULL COMMENT '课程名称',
    teacher     VARCHAR(64)  DEFAULT NULL COMMENT '授课教师',
    classroom   VARCHAR(64)  DEFAULT NULL COMMENT '上课教室',
    day_of_week TINYINT      NOT NULL COMMENT '星期几（1-7）',
    time_slot   VARCHAR(32)  NOT NULL COMMENT '节次（如 1-2, 3-4, 5-6）',
    week_range  VARCHAR(32)  DEFAULT '1-18' COMMENT '周次范围（如 1-18）',
    INDEX idx_student_id (student_id),
    INDEX idx_day_of_week (day_of_week),
    INDEX idx_classroom (classroom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- --------------------------------------------
-- 9. 校园地点表（阶段 5-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS campus_location (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '地点ID',
    name         VARCHAR(128) NOT NULL COMMENT '地点名称',
    category     VARCHAR(64)  DEFAULT NULL COMMENT '分类（教学楼/食堂/宿舍/行政/体育/其他）',
    description  TEXT         DEFAULT NULL COMMENT '地点描述',
    coordinate_x DOUBLE       DEFAULT 0 COMMENT 'X坐标（米）',
    coordinate_y DOUBLE       DEFAULT 0 COMMENT 'Y坐标（米）',
    INDEX idx_category (category),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园地点信息';

-- --------------------------------------------
-- 10. 维修员表（阶段 9-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS maintainer (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '维修员ID',
    user_id        BIGINT       DEFAULT NULL COMMENT '关联用户ID（可选）',
    name           VARCHAR(64)  NOT NULL COMMENT '姓名',
    phone          VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    skill_category VARCHAR(64)  DEFAULT NULL COMMENT '技能分类（水电/木工/网络/空调/其他）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态（AVAILABLE/BUSY/REST）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_maintainer_status (status),
    CONSTRAINT fk_maintainer_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修员';

-- --------------------------------------------
-- 11. 报修单主表（阶段 9-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS repair_order (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '报修单ID',
    order_no       VARCHAR(32)  NOT NULL COMMENT '报修单号（REP+年月日时分+4位随机数）',
    user_id        BIGINT       NOT NULL COMMENT '报修用户ID',
    title          VARCHAR(256) NOT NULL COMMENT '报修标题',
    description    TEXT         DEFAULT NULL COMMENT '报修描述',
    location       VARCHAR(256) DEFAULT NULL COMMENT '报修地点',
    urgency_level  VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '紧急程度（URGENT/HIGH/NORMAL/LOW）',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/ASSIGNED/REPAIRING/COMPLETED/CANCELLED）',
    image_urls     JSON         DEFAULT NULL COMMENT '报修图片URL列表',
    created_by     VARCHAR(64)  DEFAULT NULL COMMENT '报修人姓名（冗余）',
    assigned_to    BIGINT       DEFAULT NULL COMMENT '指派维修员ID',
    assigned_time  DATETIME     DEFAULT NULL COMMENT '派单时间',
    completed_time DATETIME     DEFAULT NULL COMMENT '完成时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT NULL COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_order_no (order_no),
    CONSTRAINT fk_repair_order_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_order_maintainer FOREIGN KEY (assigned_to) REFERENCES maintainer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报修单';

-- --------------------------------------------
-- 12. 派单记录表（阶段 9-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS dispatch_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '派单记录ID',
    order_id       BIGINT       NOT NULL COMMENT '报修单ID',
    maintainer_id  BIGINT       NOT NULL COMMENT '维修员ID',
    dispatch_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '派单时间',
    accept_time    DATETIME     DEFAULT NULL COMMENT '接单时间',
    complete_time  DATETIME     DEFAULT NULL COMMENT '完成时间',
    reject_reason  VARCHAR(512) DEFAULT NULL COMMENT '拒单原因',
    status         VARCHAR(32)  NOT NULL DEFAULT 'DISPATCHED' COMMENT '状态（DISPATCHED/ACCEPTED/REJECTED/COMPLETED）',
    INDEX idx_order_id (order_id),
    CONSTRAINT fk_dispatch_log_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    CONSTRAINT fk_dispatch_log_maintainer FOREIGN KEY (maintainer_id) REFERENCES maintainer(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单记录';

-- --------------------------------------------
-- 13. 报修评价表（阶段 9-1 新增）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS repair_review (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '评价ID',
    order_id    BIGINT    NOT NULL COMMENT '报修单ID',
    user_id     BIGINT    NOT NULL COMMENT '评价用户ID',
    rating      TINYINT   NOT NULL COMMENT '评分（1-5星）',
    comment     TEXT      DEFAULT NULL COMMENT '评价内容',
    create_time DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_order_id (order_id),
    CONSTRAINT fk_repair_review_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    CONSTRAINT fk_repair_review_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报修评价';