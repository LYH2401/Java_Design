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

-- 6. 课表表（阶段 5-1 新增）
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

-- 7. 校园地点表（阶段 5-1 新增）
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

-- 8. RAG评估表
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

-- 9. 维修员表（阶段 9-1 新增）
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

-- 10. 报修单主表（阶段 9-1 新增）
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

-- 11. 派单记录表（阶段 9-1 新增）
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

-- 12. 报修评价表（阶段 9-1 新增）
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

-- 课表示例数据（学号 2024001 的示例课表）
INSERT IGNORE INTO course_schedule (id, student_id, course_name, teacher, classroom, day_of_week, time_slot, week_range) VALUES
(1,  '2024001', '高等数学A(上)', '张明远', '教学楼B-301', 1, '1-2', '1-18'),
(2,  '2024001', '大学英语(三)', '李清华', '教学楼A-205', 1, '3-4', '1-18'),
(3,  '2024001', '大学物理B',   '王力学', '教学楼B-402', 2, '1-2', '1-18'),
(4,  '2024001', '线性代数',     '赵数论', '教学楼B-210', 2, '5-6', '1-18'),
(5,  '2024001', '程序设计基础', '陈代码', '实验楼C-101', 3, '1-2', '1-18'),
(6,  '2024001', '体育(三)',     '刘运动', '体育馆',     3, '3-4', '1-18'),
(7,  '2024001', '高等数学A(上)', '张明远', '教学楼B-301', 4, '1-2', '1-18'),
(8,  '2024001', '大学英语(三)', '李清华', '教学楼A-205', 4, '5-6', '1-18'),
(9,  '2024001', '大学物理实验',  '周实验', '实验楼B-305', 5, '1-4', '2-17'),
(10, '2024001', '形势与政策',    '马思修', '教学楼A-101', 5, '7-8', '5-12'),
(11, '2024002', '高等数学A(上)', '张明远', '教学楼B-302', 1, '3-4', '1-18'),
(12, '2024002', '大学英语(一)', '刘外语', '教学楼A-208', 2, '1-2', '1-18');

-- 校园地点数据（坐标以图书馆为原点(0,0)，东为+X，北为+Y）
INSERT IGNORE INTO campus_location (id, name, category, description, coordinate_x, coordinate_y) VALUES
(1,  '图书馆',        '图书馆', '校园中心标志性建筑，共五层，含自习区/社科图书/自然科学/电子阅览室/学术报告厅', 0,      0),
(2,  '教学楼A区',     '教学楼', '位于校园东侧，主要为文科类课程教室，共6层',                                   120,    30),
(3,  '教学楼B区',     '教学楼', '位于校园西侧，主要为理工科课程教室，共8层，含阶梯教室',                       -80,    50),
(4,  '实验楼',        '实验室', '位于校园北侧，化学/物理/计算机实验课程教室',                                 20,     180),
(5,  '行政楼',        '行政',   '校园南门入口处，校领导办公室/财务处/教务处所在地',                            0,     -150),
(6,  '第一食堂',      '食堂',   '位于东区，共三层，一层为大众餐厅，二层为风味小吃，三层为教工餐厅',           100,    80),
(7,  '第二食堂',      '食堂',   '位于西区，共两层，以地方特色美食为主',                                     -120,   70),
(8,  '第三食堂',      '食堂',   '位于北区，靠近研究生宿舍，简约风格',                                       30,     140),
(9,  '体育馆',        '体育',   '校园西北角，含篮球场/羽毛球场/游泳池/健身房',                             -100,   160),
(10, '校医院',        '医疗',   '行政楼东侧，24小时急诊电话：027-12345678，门诊时间8:00-17:30',              60,    -100),
(11, '学生宿舍1号楼', '宿舍',   '东区本科生宿舍，4人间，独立卫浴',                                          150,    100),
(12, '学生宿舍3号楼', '宿舍',   '西区研究生宿舍，2人间',                                                 -150,    90),
(13, '学术报告厅',    '学术',   '图书馆五楼，可容纳500人，举办大型讲座和学术会议',                            0,       5),
(14, '快递驿站',      '生活',   '位于第一食堂南侧，支持中通/圆通/韵达/顺丰等快递收发',                      80,     50),
(15, '校园超市',      '生活',   '位于第一食堂一楼，经营日用百货/文具/零食/饮品',                             90,     60);

-- 维修员数据（阶段 9-1 新增）
INSERT IGNORE INTO maintainer (id, name, phone, skill_category, status) VALUES
(1, '张师傅', '13800138001', '水电', 'AVAILABLE'),
(2, '李师傅', '13800138002', '网络', 'AVAILABLE'),
(3, '王师傅', '13800138003', '木工', 'BUSY');

-- 报修单示例数据（阶段 9-1 新增）
INSERT IGNORE INTO repair_order (id, order_no, user_id, title, description, location, urgency_level, status, created_by, assigned_to, assigned_time, completed_time) VALUES
(1, 'REP2026061510455284', 1, '图书馆三楼空调不制冷',
 '图书馆三楼自习区中央空调出风口无冷风，室温超过32度，影响学生自习。',
 '图书馆三楼自习区', 'HIGH', 'COMPLETED',
 'admin', 1, '2026-06-10 09:30:00', '2026-06-10 14:00:00'),
(2, 'REP2026061510451937', 1, '宿舍1号楼502室水龙头漏水',
 '宿舍1号楼502室卫生间洗手台水龙头持续漏水，地面积水严重，存在安全隐患。',
 '学生宿舍1号楼502室', 'URGENT', 'PENDING',
 'admin', NULL, NULL, NULL);

-- 报修评价数据（阶段 9-1 新增）
INSERT IGNORE INTO repair_review (id, order_id, user_id, rating, comment) VALUES
(1, 1, 1, 5, '张师傅响应迅速，维修技术专业，半小时就修好了空调，制冷效果很好！');

-- 派单记录数据（阶段 9-1 新增）
INSERT IGNORE INTO dispatch_log (id, order_id, maintainer_id, dispatch_time, accept_time, complete_time, status) VALUES
(1, 1, 1, '2026-06-10 09:30:00', '2026-06-10 09:45:00', '2026-06-10 14:00:00', 'COMPLETED');