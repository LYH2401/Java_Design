#!/usr/bin/env python3
"""
AI 回答质量评估脚本
─────────────────
功能：
  1. 加载 test_cases.json 中的 55 条校园场景问答对
  2. 对每个问题分别调用 /api/chat、/api/rag/chat、/api/agent/chat 三个端点
  3. 评估指标：语义相似度、关键信息覆盖率、引用准确率、响应时间
  4. 输出评估报告：终端汇总表 + evaluate_report.md + evaluate_report.html

用法（需先启动 Java 后端）：
  python scripts/evaluate.py
"""

import json
import os
import re
import sys
import time
import statistics
from collections import OrderedDict
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

import numpy as np
import requests
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from tqdm import tqdm

# 引入同目录配置
sys.path.insert(0, os.path.dirname(__file__))

# ============================================================================
# 配置常量
# ============================================================================

BASE_URL = os.environ.get("EVAL_BASE_URL", "http://localhost:8080")
TEST_CASES_FILE = os.path.join(os.path.dirname(__file__), "test_cases.json")
OUTPUT_MD = os.path.join(os.path.dirname(os.path.dirname(__file__)), "evaluate_report.md")
OUTPUT_HTML = os.path.join(os.path.dirname(os.path.dirname(__file__)), "evaluate_report.html")

# 请求超时（秒）
REQUEST_TIMEOUT = 60

# 尝试使用 sentence-transformers 获取更好的语义相似度
try:
    from sentence_transformers import SentenceTransformer
    _ST_MODEL = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")
    _USE_ST = True
except Exception:
    _ST_MODEL = None
    _USE_ST = False


# ============================================================================
# 数据结构
# ============================================================================

@dataclass
class SingleResult:
    """单次 API 调用的评估结果"""
    question: str
    category: str
    api_name: str           # "普通对话" | "RAG增强" | "Agent"
    response: str           # AI 实际回答
    response_time_ms: float
    similarity: float       # 语义相似度 (0~1)
    key_coverage: float     # 关键信息覆盖率 (0~1)
    citation_accuracy: Optional[float] = None  # 引用准确率（仅 RAG）
    expected_answer: str = ""
    matched_keys: list = field(default_factory=list)
    missed_keys: list = field(default_factory=list)


@dataclass
class TestResult:
    """单条测试用例的完整评估（含 3 种 API 结果）"""
    case_id: int
    category: str
    question: str
    expected_answer: str
    key_points: list
    results: list = field(default_factory=list)  # list[SingleResult]


# ============================================================================
# API 调用
# ============================================================================

def call_chat_api(message: str) -> tuple[str, float]:
    """
    调用 POST /api/chat（非流式 JSON）
    返回 (回答文本, 响应时间ms)
    """
    url = f"{BASE_URL}/api/chat"
    start = time.perf_counter()
    try:
        resp = requests.post(
            url,
            json={"message": message},
            timeout=REQUEST_TIMEOUT,
            headers={"Content-Type": "application/json"},
        )
        elapsed = (time.perf_counter() - start) * 1000
        if resp.status_code == 200:
            data = resp.json()
            return data.get("data", ""), elapsed
        else:
            return f"[HTTP {resp.status_code}] {resp.text[:200]}", elapsed
    except requests.exceptions.Timeout:
        elapsed = (time.perf_counter() - start) * 1000
        return "[超时] 请求超过60秒", elapsed
    except Exception as e:
        elapsed = (time.perf_counter() - start) * 1000
        return f"[错误] {e}", elapsed


def call_rag_api(query: str) -> tuple[str, float]:
    """
    调用 POST /api/rag/chat?query=...（SSE 流式）
    返回 (累积回答文本, 响应时间ms)
    """
    url = f"{BASE_URL}/api/rag/chat"
    start = time.perf_counter()
    try:
        resp = requests.post(
            url,
            params={"query": query},
            timeout=REQUEST_TIMEOUT,
            stream=True,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        elapsed = (time.perf_counter() - start) * 1000
        if resp.status_code != 200:
            return f"[HTTP {resp.status_code}] {resp.text[:200]}", elapsed

        chunks = []
        for line in resp.iter_lines(decode_unicode=True):
            if line and line.startswith("data:"):
                chunk = line[5:].strip()
                if chunk == "[DONE]":
                    break
                chunks.append(chunk)
        elapsed = (time.perf_counter() - start) * 1000
        return "".join(chunks), elapsed
    except requests.exceptions.Timeout:
        elapsed = (time.perf_counter() - start) * 1000
        return "[超时] 请求超过60秒", elapsed
    except Exception as e:
        elapsed = (time.perf_counter() - start) * 1000
        return f"[错误] {e}", elapsed


def call_agent_api(message: str) -> tuple[str, float]:
    """
    调用 POST /api/agent/chat（SSE 流式，JSON body）
    返回 (累积回答文本, 响应时间ms)
    """
    url = f"{BASE_URL}/api/agent/chat"
    start = time.perf_counter()
    try:
        resp = requests.post(
            url,
            json={"message": message},
            timeout=REQUEST_TIMEOUT,
            stream=True,
            headers={"Content-Type": "application/json"},
        )
        elapsed = (time.perf_counter() - start) * 1000
        if resp.status_code != 200:
            return f"[HTTP {resp.status_code}] {resp.text[:200]}", elapsed

        chunks = []
        for line in resp.iter_lines(decode_unicode=True):
            if line:
                # Agent SSE 直接输出每行作为 chunk（无 data: 前缀）
                chunks.append(line)
        elapsed = (time.perf_counter() - start) * 1000
        return "".join(chunks), elapsed
    except requests.exceptions.Timeout:
        elapsed = (time.perf_counter() - start) * 1000
        return "[超时] 请求超过60秒", elapsed
    except Exception as e:
        elapsed = (time.perf_counter() - start) * 1000
        return f"[错误] {e}", elapsed


# ============================================================================
# 评估指标计算
# ============================================================================

def compute_similarity(text_a: str, text_b: str) -> float:
    """
    计算两段文本的语义相似度。
    优先使用 sentence-transformers，回退到 TF-IDF。
    """
    if not text_a.strip() or not text_b.strip():
        return 0.0

    if _USE_ST and _ST_MODEL is not None:
        try:
            emb = _ST_MODEL.encode([text_a, text_b])
            sim = cosine_similarity([emb[0]], [emb[1]])[0][0]
            return max(0.0, float(sim))
        except Exception:
            pass

    # TF-IDF 回退
    try:
        vectorizer = TfidfVectorizer(analyzer="char", ngram_range=(2, 4))
        tfidf = vectorizer.fit_transform([text_a, text_b])
        sim = cosine_similarity(tfidf[0:1], tfidf[1:2])[0][0]
        return max(0.0, float(sim))
    except Exception:
        return 0.0


def compute_key_coverage(response: str, key_points: list) -> tuple[float, list, list]:
    """
    计算关键信息覆盖率：标准答案中的关键点在被测回答中出现的比例。
    返回 (覆盖率, 匹配到的关键点列表, 缺失的关键点列表)
    """
    if not key_points:
        return 1.0, [], []

    response_lower = response.lower()
    matched = []
    missed = []

    for kp in key_points:
        # 数字类关键词做宽松匹配
        kp_lower = kp.lower()
        if _fuzzy_match(kp_lower, response_lower):
            matched.append(kp)
        else:
            missed.append(kp)

    return len(matched) / len(key_points), matched, missed


def _fuzzy_match(keyword: str, text: str) -> bool:
    """宽松匹配：数字精确匹配，中文关键词子串匹配。"""
    # 纯数字/英文关键词：精确包含
    if re.match(r'^[\d./\-:：a-zA-Z]+$', keyword):
        return keyword in text
    # 中英文混合：子串匹配
    return keyword in text


def compute_citation_accuracy(response: str, relevant_doc: str) -> float:
    """
    计算引用准确率：RAG 回答中引用的事实是否与知识库原文一致。
    简化方案：检查回答中是否包含知识库原文中的关键事实片段。
    """
    if not relevant_doc or not response.strip():
        return None

    # 加载知识库原文
    knowledge_dir = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        "src", "main", "resources", "knowledge"
    )
    doc_path = os.path.join(knowledge_dir, relevant_doc)
    if not os.path.exists(doc_path):
        return None

    with open(doc_path, "r", encoding="utf-8") as f:
        doc_content = f.read()

    # 提取知识库原文中长度 ≥ 8 字的句子作为事实基准
    facts = _extract_facts(doc_content)
    if not facts:
        return None

    # 检查 AI 回答中包含多少原文事实
    matched = sum(1 for f in facts if f in response)
    return matched / len(facts) if facts else None


def _extract_facts(text: str, min_len: int = 8) -> list[str]:
    """从文本中提取长度 ≥ min_len 的事实句子片段。"""
    # 按标点分割
    sentences = re.split(r'[。！？\n；;]', text)
    facts = []
    for s in sentences:
        s = s.strip()
        # 去掉序号前缀
        s = re.sub(r'^[（(]?[一二三四五六七八九十\d]+[)）]?\s*', '', s)
        s = re.sub(r'^[（(]?\d+[)）]?\s*', '', s)
        if len(s) >= min_len and not s.startswith("●"):
            facts.append(s)
    return facts


# ============================================================================
# 主评估流程
# ============================================================================

def evaluate_single(case: dict) -> TestResult:
    """对单条测试用例进行三种 API 的完整评估。"""
    result = TestResult(
        case_id=case["id"],
        category=case["category"],
        question=case["question"],
        expected_answer=case.get("expected_answer", ""),
        key_points=case.get("key_points", []),
    )

    # 1. 普通对话
    resp, rt = call_chat_api(case["question"])
    sim = compute_similarity(resp, case.get("expected_answer", ""))
    kc, matched, missed = compute_key_coverage(resp, case.get("key_points", []))
    result.results.append(SingleResult(
        question=case["question"],
        category=case["category"],
        api_name="普通对话",
        response=resp,
        response_time_ms=rt,
        similarity=sim,
        key_coverage=kc,
        expected_answer=case.get("expected_answer", ""),
        matched_keys=matched,
        missed_keys=missed,
    ))

    # 2. RAG 增强
    resp, rt = call_rag_api(case["question"])
    sim = compute_similarity(resp, case.get("expected_answer", ""))
    kc, matched, missed = compute_key_coverage(resp, case.get("key_points", []))
    cit = compute_citation_accuracy(resp, case.get("relevant_doc", ""))
    result.results.append(SingleResult(
        question=case["question"],
        category=case["category"],
        api_name="RAG增强",
        response=resp,
        response_time_ms=rt,
        similarity=sim,
        key_coverage=kc,
        citation_accuracy=cit,
        expected_answer=case.get("expected_answer", ""),
        matched_keys=matched,
        missed_keys=missed,
    ))

    # 3. Agent
    resp, rt = call_agent_api(case["question"])
    sim = compute_similarity(resp, case.get("expected_answer", ""))
    kc, matched, missed = compute_key_coverage(resp, case.get("key_points", []))
    result.results.append(SingleResult(
        question=case["question"],
        category=case["category"],
        api_name="Agent",
        response=resp,
        response_time_ms=rt,
        similarity=sim,
        key_coverage=kc,
        expected_answer=case.get("expected_answer", ""),
        matched_keys=matched,
        missed_keys=missed,
    ))

    return result


def build_summary_table(all_results: list[TestResult]) -> str:
    """构建终端汇总表格。"""
    lines = []
    sep = "+" + "-" * 22 + "+" + "-" * 10 + "+" + "-" * 10 + "+" + "-" * 10 + "+" + "-" * 10 + "+"

    lines.append("")
    lines.append("  " + "=" * 70)
    lines.append("    AI 回答质量评估 — 汇总表")
    lines.append("  " + "=" * 70)
    lines.append(sep)
    lines.append(f"| {'指标':<20s} | {'普通对话':>8s} | {'RAG增强':>8s} | {'Agent':>8s} | {'平均':>8s} |")
    lines.append(sep)

    metrics = ["similarity", "key_coverage", "citation_accuracy", "response_time_ms"]
    labels = ["语义相似度", "关键信息覆盖率", "引用准确率", "响应时间(ms)"]
    formats = [".3f", ".1%", ".1%", ".0f"]

    for metric, label, fmt in zip(metrics, labels, formats):
        values = {}
        for api in ["普通对话", "RAG增强", "Agent"]:
            vals = [
                getattr(r, metric)
                for result in all_results
                for r in result.results
                if r.api_name == api and getattr(r, metric) is not None
            ]
            values[api] = statistics.mean(vals) if vals else 0.0

        avg_val = statistics.mean(list(values.values()))

        if "rate" in metric or "coverage" in metric or "accuracy" in metric:
            line = f"| {label:<20s} | {values['普通对话']:>7.1%} | {values['RAG增强']:>7.1%} | {values['Agent']:>7.1%} | {avg_val:>7.1%} |"
        elif "time" in metric:
            line = f"| {label:<20s} | {values['普通对话']:>7.0f}ms | {values['RAG增强']:>7.0f}ms | {values['Agent']:>7.0f}ms | {avg_val:>7.0f}ms |"
        else:
            line = f"| {label:<20s} | {values['普通对话']:>7.3f} | {values['RAG增强']:>7.3f} | {values['Agent']:>7.3f} | {avg_val:>7.3f} |"
        lines.append(line)

    lines.append(sep)

    # 分类汇总
    lines.append("")
    lines.append("  --- 分类汇总 ---")
    categories = sorted(set(r.category for r in all_results))
    for cat in categories:
        cat_results = [r for r in all_results if r.category == cat]
        avg_sim = statistics.mean(
            getattr(rr, "similarity")
            for cr in cat_results for rr in cr.results if rr.similarity is not None
        )
        avg_kc = statistics.mean(
            getattr(rr, "key_coverage")
            for cr in cat_results for rr in cr.results if rr.key_coverage is not None
        )
        lines.append(f"  {cat:<8s} | 相似度: {avg_sim:.3f} | 关键覆盖率: {avg_kc:.1%} | 用例数: {len(cat_results)}")

    return "\n".join(lines)


def generate_markdown_report(all_results: list[TestResult], summary_text: str) -> str:
    """生成 Markdown 评估报告。"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    total = len(all_results)
    total_questions = total * 3  # 3 APIs

    # 收集各指标
    all_sim = [r.similarity for cr in all_results for r in cr.results if r.similarity is not None]
    all_kc = [r.key_coverage for cr in all_results for r in cr.results if r.key_coverage is not None]
    all_rt = [r.response_time_ms for cr in all_results for r in cr.results if r.response_time_ms is not None]
    rag_cit = [r.citation_accuracy for cr in all_results for r in cr.results
               if r.api_name == "RAG增强" and r.citation_accuracy is not None]

    lines = [
        f"# 校园智能服务小助手 — AI 回答质量评估报告",
        f"",
        f"**评估时间**: {timestamp}",
        f"**测试用例数**: {total}",
        f"**API 端点**: /api/chat, /api/rag/chat, /api/agent/chat",
        f"**模型嵌入**: {'sentence-transformers' if _USE_ST else 'TF-IDF (回退)'}",
        f"",
        f"---",
        f"",
        f"## 一、总体指标汇总",
        f"",
    ]

    # 按 API 分组统计
    for api_name in ["普通对话", "RAG增强", "Agent"]:
        api_results = [r for cr in all_results for r in cr.results if r.api_name == api_name]
        sims = [r.similarity for r in api_results if r.similarity is not None]
        kcs = [r.key_coverage for r in api_results if r.key_coverage is not None]
        rts = [r.response_time_ms for r in api_results if r.response_time_ms is not None]

        lines.append(f"### {api_name} (POST {'/api/chat' if api_name == '普通对话' else '/api/rag/chat' if api_name == 'RAG增强' else '/api/agent/chat'})")
        lines.append(f"")
        lines.append(f"| 指标 | 平均值 | 最小值 | 最大值 | 中位数 |")
        lines.append(f"|------|--------|--------|--------|--------|")
        if sims:
            lines.append(f"| 语义相似度 | {statistics.mean(sims):.3f} | {min(sims):.3f} | {max(sims):.3f} | {statistics.median(sims):.3f} |")
        if kcs:
            lines.append(f"| 关键信息覆盖率 | {statistics.mean(kcs):.1%} | {min(kcs):.1%} | {max(kcs):.1%} | {statistics.median(kcs):.1%} |")
        if rts:
            lines.append(f"| 响应时间 (ms) | {statistics.mean(rts):.0f} | {min(rts):.0f} | {max(rts):.0f} | {statistics.median(rts):.0f} |")

        if api_name == "RAG增强" and rag_cit:
            lines.append(f"| 引用准确率 | {statistics.mean(rag_cit):.3f} | {min(rag_cit):.3f} | {max(rag_cit):.3f} | {statistics.median(rag_cit):.3f} |")

        lines.append(f"")

    # 分类汇总
    lines.append(f"---")
    lines.append(f"")
    lines.append(f"## 二、分类评估汇总")
    lines.append(f"")
    lines.append(f"| 分类 | 用例数 | 平均相似度 | 平均关键覆盖率 | 平均响应时间 |")
    lines.append(f"|------|--------|-----------|---------------|-------------|")

    categories = sorted(set(r.category for r in all_results))
    for cat in categories:
        cat_results = [r for cr in all_results if cr.category == cat for r in cr.results]
        sims = [r.similarity for r in cat_results if r.similarity is not None]
        kcs = [r.key_coverage for r in cat_results if r.key_coverage is not None]
        rts = [r.response_time_ms for r in cat_results if r.response_time_ms is not None]
        lines.append(
            f"| {cat} | {len([cr for cr in all_results if cr.category == cat])} "
            f"| {statistics.mean(sims):.3f} | {statistics.mean(kcs):.1%} | {statistics.mean(rts):.0f}ms |"
        )

    lines.append(f"")
    lines.append(f"---")
    lines.append(f"")
    lines.append(f"## 三、详细评测结果")
    lines.append(f"")

    for cr in all_results:
        lines.append(f"### #{cr.case_id} {cr.question}")
        lines.append(f"")
        lines.append(f"- **分类**: {cr.category}")
        lines.append(f"- **标准答案**: {cr.expected_answer}")
        lines.append(f"- **关键点**: {'、'.join(cr.key_points)}")
        lines.append(f"")

        for r in cr.results:
            lines.append(f"#### {r.api_name}")
            lines.append(f"")
            lines.append(f"| 指标 | 值 |")
            lines.append(f"|------|----|")
            lines.append(f"| 语义相似度 | {r.similarity:.3f} |")
            lines.append(f"| 关键信息覆盖率 | {r.key_coverage:.1%} |")
            lines.append(f"| 响应时间 | {r.response_time_ms:.0f} ms |")
            if r.citation_accuracy is not None:
                lines.append(f"| 引用准确率 | {r.citation_accuracy:.3f} |")
            if r.matched_keys:
                lines.append(f"| 匹配关键点 | {'、'.join(r.matched_keys)} |")
            if r.missed_keys:
                lines.append(f"| 缺失关键点 | {'、'.join(r.missed_keys)} |")
            lines.append(f"")
            lines.append(f"**AI 回答**:")
            lines.append(f"")
            # 截断过长回答
            truncated = r.response[:600]
            if len(r.response) > 600:
                truncated += f"\n\n...（共 {len(r.response)} 字符，已截断）"
            lines.append(f"> {truncated}")
            lines.append(f"")

        lines.append(f"---")
        lines.append(f"")

    return "\n".join(lines)


def generate_html_report(all_results: list[TestResult]) -> str:
    """生成 HTML 可视化评估报告。"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 计算汇总数据
    apis = ["普通对话", "RAG增强", "Agent"]
    cat_set = sorted(set(r.category for r in all_results))

    # API 对比数据
    api_data = {}
    for api in apis:
        api_results = [r for cr in all_results for r in cr.results if r.api_name == api]
        sims = [r.similarity for r in api_results if r.similarity is not None]
        kcs = [r.key_coverage for r in api_results if r.key_coverage is not None]
        rts = [r.response_time_ms for r in api_results if r.response_time_ms is not None]
        api_data[api] = {
            "sim": statistics.mean(sims) if sims else 0,
            "kc": statistics.mean(kcs) if kcs else 0,
            "rt": statistics.mean(rts) if rts else 0,
        }

    # 构建表格行
    table_rows = ""
    for cr in all_results:
        cells = f"<td>{cr.case_id}</td><td>{cr.question}</td><td>{cr.category}</td>"
        for api in apis:
            r = next((rr for rr in cr.results if rr.api_name == api), None)
            if r:
                color = "#27ae60" if r.key_coverage >= 0.5 else "#e67e22" if r.key_coverage >= 0.3 else "#e74c3c"
                cells += f'<td>{r.key_coverage:.0%}</td><td style="color:{color}">{r.similarity:.2f}</td>'
            else:
                cells += "<td>-</td><td>-</td>"
        table_rows += f"<tr>{cells}</tr>"

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AI 回答质量评估报告</title>
<style>
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f0f2f5; color: #333; padding: 20px; }}
  .container {{ max-width: 1200px; margin: 0 auto; }}
  h1 {{ text-align: center; margin-bottom: 8px; color: #1a1a2e; }}
  .timestamp {{ text-align: center; color: #888; margin-bottom: 24px; }}
  .cards {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 24px; }}
  .card {{ background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }}
  .card h3 {{ margin-bottom: 12px; color: #2c3e50; }}
  .metric {{ display: flex; justify-content: space-between; margin: 8px 0; padding: 6px 0; border-bottom: 1px solid #eee; }}
  .metric:last-child {{ border-bottom: none; }}
  .metric .label {{ color: #666; }}
  .metric .value {{ font-weight: 600; }}
  .bar-container {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 24px; }}
  .bar-card {{ background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }}
  .bar-card h4 {{ text-align: center; margin-bottom: 12px; }}
  .bar {{ height: 20px; border-radius: 10px; margin: 6px 0; min-width: 4px; display: flex; align-items: center; padding-left: 8px; color: #fff; font-size: 12px; font-weight: 600; }}
  .bar.normal {{ background: linear-gradient(90deg, #3498db, #2980b9); }}
  .bar.rag {{ background: linear-gradient(90deg, #27ae60, #1e8449); }}
  .bar.agent {{ background: linear-gradient(90deg, #9b59b6, #7d3c98); }}
  table {{ width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.08); margin-bottom: 24px; }}
  th {{ background: #2c3e50; color: #fff; padding: 12px 10px; font-size: 13px; text-align: left; }}
  td {{ padding: 10px; border-bottom: 1px solid #eee; font-size: 13px; }}
  tr:hover {{ background: #f8f9fa; }}
  .summary {{ background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); margin-bottom: 24px; }}
  .summary h3 {{ margin-bottom: 16px; }}
  .embed-info {{ background: #e8f4fd; padding: 12px 16px; border-radius: 8px; margin-bottom: 16px; font-size: 14px; }}
</style>
</head>
<body>
<div class="container">
  <h1>🤖 AI 回答质量评估报告</h1>
  <p class="timestamp">评估时间: {timestamp} | 测试用例: {len(all_results)} 条 | 嵌入模型: {"sentence-transformers" if _USE_ST else "TF-IDF"}</p>

  <div class="summary">
    <h3>📊 总体对比</h3>
    <div class="cards">
      {"".join(f'''<div class="card">
        <h3>{"🔵 " + name if name == "普通对话" else "🟢 " + name if name == "RAG增强" else "🟣 " + name}</h3>
        <div class="metric"><span class="label">语义相似度</span><span class="value">{api_data[name]["sim"]:.3f}</span></div>
        <div class="metric"><span class="label">关键信息覆盖率</span><span class="value">{api_data[name]["kc"]:.1%}</span></div>
        <div class="metric"><span class="label">平均响应时间</span><span class="value">{api_data[name]["rt"]:.0f} ms</span></div>
      </div>''' for name in apis)}
    </div>
  </div>

  <div class="bar-container">
    <div class="bar-card">
      <h4>语义相似度对比</h4>
      {"".join(f'<div class="bar {"normal" if name == "普通对话" else "rag" if name == "RAG增强" else "agent"}" style="width:{min(api_data[name]["sim"]*100, 100):.0f}%">{name} {api_data[name]["sim"]:.2f}</div>' for name in apis)}
    </div>
    <div class="bar-card">
      <h4>关键信息覆盖率对比</h4>
      {"".join(f'<div class="bar {"normal" if name == "普通对话" else "rag" if name == "RAG增强" else "agent"}" style="width:{min(api_data[name]["kc"]*100, 100):.0f}%">{name} {api_data[name]["kc"]:.0%}</div>' for name in apis)}
    </div>
    <div class="bar-card">
      <h4>响应时间对比 (ms)</h4>
      {"".join(f'<div class="bar {"normal" if name == "普通对话" else "rag" if name == "RAG增强" else "agent"}" style="width:{min(api_data[name]["rt"]/50, 100):.0f}%">{name} {api_data[name]["rt"]:.0f}</div>' for name in apis)}
    </div>
  </div>

  <h3>📋 详细评测结果</h3>
  <div style="overflow-x: auto;">
    <table>
      <thead>
        <tr>
          <th>#</th><th>问题</th><th>分类</th>
          <th>普通对话 KC</th><th>普通对话 Sim</th>
          <th>RAG增强 KC</th><th>RAG增强 Sim</th>
          <th>Agent KC</th><th>Agent Sim</th>
        </tr>
      </thead>
      <tbody>
        {table_rows}
      </tbody>
    </table>
  </div>

  <p style="text-align:center;color:#999;font-size:12px;margin-top:24px;">
    KC = 关键信息覆盖率 | Sim = 语义相似度 | 由 evaluate.py 自动生成
  </p>
</div>
</body>
</html>"""
    return html


# ============================================================================
# 入口
# ============================================================================

def main():
    print("=" * 60)
    print("  AI 回答质量评估脚本")
    print(f"  目标服务: {BASE_URL}")
    print(f"  嵌入模型: {'sentence-transformers' if _USE_ST else 'TF-IDF (回退)'}")
    print("=" * 60)

    # ---- Step 1: 加载测试用例 ----
    print(f"\n[1/4] 加载测试用例: {TEST_CASES_FILE}")
    with open(TEST_CASES_FILE, "r", encoding="utf-8") as f:
        test_cases = json.load(f)
    print(f"      加载 {len(test_cases)} 条测试用例")

    # ---- Step 2: 逐条评估 ----
    print(f"\n[2/4] 开始评估 (每条调用 3 个 API 端点)...")
    all_results: list[TestResult] = []

    for case in tqdm(test_cases, desc="评估进度", unit="case"):
        try:
            result = evaluate_single(case)
            all_results.append(result)
        except Exception as e:
            print(f"\n      [警告] 用例 #{case['id']} 评估异常: {e}")

    # ---- Step 3: 生成报告 ----
    print(f"\n[3/4] 生成评估报告...")

    # 终端汇总
    summary = build_summary_table(all_results)
    print(summary)

    # Markdown 报告
    md_content = generate_markdown_report(all_results, summary)
    with open(OUTPUT_MD, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"\n      已生成 Markdown 报告: {OUTPUT_MD}")

    # HTML 报告
    html_content = generate_html_report(all_results)
    with open(OUTPUT_HTML, "w", encoding="utf-8") as f:
        f.write(html_content)
    print(f"      已生成 HTML 报告: {OUTPUT_HTML}")

    # ---- Step 4: 完成 ----
    print(f"\n[4/4] 评估完成!")
    print(f"      共评估 {len(all_results)} 条用例，{len(all_results) * 3} 次 API 调用")
    print("=" * 60)


if __name__ == "__main__":
    main()
