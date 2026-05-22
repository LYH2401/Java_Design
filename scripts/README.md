# Python 脚本使用说明

本目录包含校园智能服务小助手的 Python 辅助脚本，用于知识库预处理和 AI 回答质量评估。

> **注意**：Python 脚本不参与 Docker 部署，仅在开发/评估阶段手动运行。

---

## 目录结构

```
scripts/
├── README.md              # 本文件
├── requirements.txt       # Python 依赖清单
├── config.py              # 预处理脚本配置文件
├── prepare_knowledge.py   # 知识库预处理脚本
├── test_cases.json        # 评估测试用例（55 条）
└── evaluate.py            # AI 回答质量评估脚本
```

---

## 环境准备

### 1. 创建 Python 虚拟环境

```bash
cd scripts
python -m venv venv
```

### 2. 激活虚拟环境

**Windows (cmd):**
```bash
venv\Scripts\activate
```

**Windows (PowerShell):**
```powershell
venv\Scripts\Activate.ps1
```

**macOS / Linux:**
```bash
source venv/bin/activate
```

### 3. 安装依赖

```bash
pip install -r requirements.txt
```

依赖包括：
| 包名 | 用途 |
|------|------|
| `scikit-learn` | TF-IDF 向量化 + 余弦相似度 |
| `numpy` | 数值计算 |
| `requests` | HTTP API 调用 |
| `tqdm` | 进度条显示 |
| `sentence-transformers` | （可选）多语言语义相似度增强 |

---

## 一、知识库预处理

### 功能说明

读取 [`src/main/resources/knowledge/`](../src/main/resources/knowledge/) 目录下的 `.txt` 知识库文件，执行：

1. **分块**：按中文序号标题（一、二、三…）智能分段，每块 ≤800 字符，块间重叠 80 字符
2. **去重**：TF-IDF 余弦相似度检测，阈值 0.85 以上的重复段落自动丢弃
3. **输出**：生成标准 JSON 格式文件 [`knowledge_processed.json`](../knowledge_processed.json)

### 运行

```bash
python prepare_knowledge.py
```

### 输出示例

```json
[
  {
    "id": "lib_001",
    "category": "图书馆",
    "title": "图书馆使用须知",
    "content": "图书馆开放时间为周一至周日 7:00-22:30...",
    "char_count": 345,
    "source_file": "图书馆使用须知.txt"
  }
]
```

### 配置参考

编辑 [`config.py`](config.py) 可调整以下参数：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `CHUNK_SIZE` | 800 | 每个分块最大字符数 |
| `CHUNK_OVERLAP` | 80 | 相邻分块重叠字符数 |
| `DEDUP_THRESHOLD` | 0.85 | 重复判定余弦相似度阈值 |
| `MIN_CONTENT_LENGTH` | 20 | 最小有效段落长度 |

### 终端输出统计

运行完成后会打印：
- 处理的文档数
- 总段落数（分块后）
- 平均段落长度
- 检测到的重复段落数
- 输出文件路径

---

## 二、AI 回答质量评估

### 功能说明

基于 55 条校园场景测试用例，对三个 API 端点进行质量评估：

| 端点 | 说明 |
|------|------|
| `POST /api/chat` | 普通对话（非流式 JSON） |
| `POST /api/rag/chat` | RAG 增强对话（SSE 流式） |
| `POST /api/agent/chat` | Agent 智能体对话（SSE 流式） |

### 评估指标

| 指标 | 计算方式 | 范围 |
|------|----------|------|
| **语义相似度** | 回答与期望答案的余弦相似度（优先使用 sentence-transformers，回退 TF-IDF） | 0~1 |
| **关键信息覆盖率** | 关键要点在回答中出现的比例（模糊匹配 + 数值特殊处理） | 0~100% |
| **引用准确率** | RAG 回答中引用内容与知识库源文档的吻合度 | 0~100% |
| **响应时间** | 从发起请求到完整响应的耗时 | ms |

### 前置条件

**必须先启动 Java 后端服务**（默认监听 `http://localhost:8080`），否则脚本无法调用 API。

```bash
# 在项目根目录启动后端
java -jar target/campus-assistant-*.jar
```

### 运行

```bash
python evaluate.py
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `EVAL_BASE_URL` | `http://localhost:8080` | Java 后端地址 |

示例（自定义后端地址）：

```bash
# Windows cmd
set EVAL_BASE_URL=http://192.168.1.100:9090 && python evaluate.py

# PowerShell
$env:EVAL_BASE_URL="http://192.168.1.100:9090"; python evaluate.py

# bash / zsh
EVAL_BASE_URL=http://192.168.1.100:9090 python evaluate.py
```

### 输出产物

| 文件 | 说明 |
|------|------|
| 终端汇总表 | 每个端点在各指标上的平均分 + 排名 |
| `evaluate_report.md` | Markdown 格式详细报告（项目根目录） |
| `evaluate_report.html` | HTML 可视化报告（项目根目录） |

### 终端输出示例

```
═══════════════════════════════════════════════════════
  校园智能服务小助手 — AI 回答质量评估报告
  评估时间：2026-05-22 19:30:00
  测试用例数：55
═══════════════════════════════════════════════════════

API端点         语义相似度  关键覆盖率  引用准确率  平均响应时间
───────────────────────────────────────────────────────────
普通对话(/api/chat)     0.72        68.5%        —         1.2s
RAG增强 (/api/rag/chat) 0.85        82.3%      91.2%       3.8s
Agent  (/api/agent/chat) 0.78        75.1%        —         5.6s
```

---

## 常见问题

### Q: `ImportError: No module named 'sklearn'`

未安装依赖，请执行：
```bash
pip install -r requirements.txt
```

### Q: `Connection refused` 或 `ConnectionError`

Java 后端未启动或端口不正确。请确认：
1. 后端已启动：`java -jar target/campus-assistant-*.jar`
2. 端口正确：默认 8080，可通过 `EVAL_BASE_URL` 环境变量自定义

### Q: `sentence-transformers` 下载模型很慢

首次运行会自动下载 `paraphrase-multilingual-MiniLM-L12-v2` 模型（~500MB）。如果网络受限，脚本会自动回退到 TF-IDF 计算相似度。

### Q: 评估脚本需要多长时间？

55 条用例 × 3 个端点 = 165 次 API 调用。取决于 AI 模型响应速度，通常需要 5-15 分钟。
