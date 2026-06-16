"""
知识库预处理脚本 — 配置文件
"""

import os

# ==================== 路径配置 ====================

# 项目根目录（scripts/ 的上级目录）
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# 知识库原始 .txt 文件目录
KNOWLEDGE_DIR = os.path.join(PROJECT_ROOT, "src", "main", "resources", "knowledge")

# 预处理后的输出 JSON 文件
OUTPUT_FILE = os.path.join(PROJECT_ROOT, "knowledge_processed.json")

# ==================== 分块配置 ====================

# 每个 chunk 的最大字符数
CHUNK_SIZE = 800

# 相邻 chunk 之间的重叠字符数（保持语义连续性）
CHUNK_OVERLAP = 80

# ==================== 去重配置 ====================

# 余弦相似度阈值：超过此值视为重复段落，保留第一个
DEDUP_THRESHOLD = 0.85

# 最小内容长度（字符数）：短于此值的段落将被丢弃
MIN_CONTENT_LENGTH = 20

# ==================== 文件名 → 分类映射 ====================

# 根据源文件名自动确定分类，未匹配的使用文件名本身（去掉 .txt）
CATEGORY_MAP = {
    "图书馆使用须知":     "图书馆",
    "奖学金申请指南":     "奖学金",
    "校园卡办理与挂失":   "生活服务",
    "校园交通与导航":     "校园导航",
    "选课与学分管理":     "教务",
}

# ==================== ID 前缀 ====================

# 生成 ID 的前缀，如 "lib_" → "lib_001"
ID_PREFIX = "lib_"
