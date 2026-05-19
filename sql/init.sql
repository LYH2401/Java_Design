-- ============================================
-- 校园智能服务小助手 - Docker 初始化脚本
-- 供 docker-entrypoint-initdb.d 使用
-- 合并 schema.sql + data.sql
-- ============================================

CREATE DATABASE IF NOT EXISTS campus_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE campus_assistant;

-- ============================================
-- 一、建表（schema）
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(64)     NOT NULL COMMENT '用户名',
    password    VARCHAR(256)    NOT NULL COMMENT '密码（BCrypt加密）',
    role        ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT' COMMENT '角色',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

-- 2. 对话会话表
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

-- 3. 消息表
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

-- 4. 校园知识文档表
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

-- 5. Agent执行日志表
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

-- 6. RAG评估表
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

-- 7. 告警日志表
CREATE TABLE IF NOT EXISTS alert_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '告警ID',
    alert_type  VARCHAR(64)  NOT NULL COMMENT '告警类型',
    message     TEXT         DEFAULT NULL COMMENT '告警消息',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_alert_type (alert_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警日志';

-- ============================================
-- 二、初始数据（data）
-- ============================================

-- 管理员用户（admin / admin123）
INSERT IGNORE INTO sys_user (id, username, password, role) VALUES
(1, 'admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPQga4X8y', 'ADMIN');

-- 校园知识文档
INSERT IGNORE INTO knowledge_doc (id, title, category, content) VALUES
(1, '图书馆开放时间',
 '图书馆',
 '学校图书馆开放时间：周一至周五 8:00-22:00，周六周日 9:00-21:00。法定节假日开放时间另行通知。'
 || '图书馆共有五层，一层为自习区，二层为社科图书区，三层为自然科学图书区，四层为电子阅览室，五层为学术报告厅。'
 || '借阅规则：本科生每次最多借阅 5 本，期限 30 天；研究生每次最多借阅 10 本，期限 60 天。可通过校园一卡通自助借还。'),

(2, '校园卡充值指南',
 '生活服务',
 '校园一卡通充值方式：\n'
 || '1. 微信充值：关注"智慧校园"公众号 → 校园服务 → 一卡通充值\n'
 || '2. 支付宝充值：搜索"校园一卡通"小程序 → 选择学校 → 输入金额\n'
 || '3. 现金充值：食堂一楼自助充值机（支持 50/100 元纸币）\n'
 || '4. 人工充值：行政楼一楼财务处窗口（工作日 9:00-16:30）\n'
 || '挂失与补办：如遗失校园卡，请立即通过公众号挂失，补办需携带身份证到行政楼一楼，工本费 20 元。'),

(3, '校园建筑与导航',
 '校园导航',
 '校园主要建筑分布：\n'
 || '● 教学楼 A 区：位于校园东侧，主要为文科类课程教室\n'
 || '● 教学楼 B 区：位于校园西侧，主要为理工科课程教室\n'
 || '● 实验楼：位于校园北侧，化学、物理、计算机等实验课程\n'
 || '● 图书馆：校园中心位置，标志性建筑\n'
 || '● 行政楼：校园南门入口处\n'
 || '● 学生食堂：共有三个食堂，分别位于东区（第一食堂）、西区（第二食堂）、北区（第三食堂）\n'
 || '● 体育馆：校园西北角，含篮球场、羽毛球场、游泳池\n'
 || '● 校医院：行政楼东侧，24 小时急诊电话：027-12345678');
