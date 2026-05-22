#!/usr/bin/env python3
"""
知识库预处理脚本
─────────────────
功能：
  1. 读取 src/main/resources/knowledge/ 下所有 .txt 文件
  2. 按段落分块（智能断句，保持语义完整）
  3. 去重（检测高度相似的段落）
  4. 生成标准 JSON 格式输出
  5. 统计：文档数 / 总段落数 / 平均长度 / 重复段落数

用法：
  python scripts/prepare_knowledge.py
"""

import json
import os
import re
import sys
from collections import OrderedDict

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from tqdm import tqdm

# 引入同目录配置
sys.path.insert(0, os.path.dirname(__file__))
from config import (
    KNOWLEDGE_DIR,
    OUTPUT_FILE,
    CHUNK_SIZE,
    CHUNK_OVERLAP,
    DEDUP_THRESHOLD,
    MIN_CONTENT_LENGTH,
    CATEGORY_MAP,
    ID_PREFIX,
)

# ============================================================================
# 1. 文件读取
# ============================================================================

def read_knowledge_files(directory: str) -> list[dict]:
    """
    扫描目录下所有 .txt 文件，返回列表，每项包含文件名与全文。
    """
    files = []
    if not os.path.isdir(directory):
        print(f"[警告] 知识库目录不存在: {directory}")
        return files

    for fname in sorted(os.listdir(directory)):
        if fname.endswith(".txt"):
            fpath = os.path.join(directory, fname)
            with open(fpath, "r", encoding="utf-8") as f:
                content = f.read()
            files.append({"filename": fname, "content": content})
    return files


def extract_title(content: str, filename: str) -> str:
    """
    从内容中提取标题：取第一行非空行。
    """
    for line in content.split("\n"):
        stripped = line.strip()
        if stripped:
            return stripped
    # 回退：用文件名
    return os.path.splitext(filename)[0]


def infer_category(filename: str) -> str:
    """
    根据文件名推断分类。
    """
    stem = os.path.splitext(filename)[0]
    return CATEGORY_MAP.get(stem, stem)


# ============================================================================
# 2. 智能分块
# ============================================================================

# 匹配中文序号标题：一、二、… 二十、 / （一）（二）/ 1. 2. / 一) 二)
_SECTION_HEADER_RE = re.compile(
    r"^(?:[一二三四五六七八九十]+[、．.)]|[(（][一二三四五六七八九十]+[)）]|\d{1,2}[.、)])\s*"
)

# 句末标点
_SENTENCE_END_RE = re.compile(r"[。！？!?\n]")


def split_into_sections(text: str) -> list[str]:
    """
    按「一、」「二、」等中文序号标题将文档拆分为段落。
    如果文档没有任何序号标题，则整篇作为一个段落。
    """
    lines = text.split("\n")
    sections: list[str] = []
    current: list[str] = []

    for line in lines:
        stripped = line.strip()
        if not stripped:
            # 空行视为段落边界 — 如果 current 非空，先保存
            if current:
                sections.append("\n".join(current))
                current = []
            continue

        # 检测是否为中文序号标题行
        if _SECTION_HEADER_RE.match(stripped):
            # 保存上一个段落
            if current:
                sections.append("\n".join(current))
                current = []
            # 当前行作为新段落的起始（含标题）
            current.append(stripped)
        else:
            current.append(stripped)

    # 最后一个段落
    if current:
        sections.append("\n".join(current))

    return sections


def split_long_chunk(chunk_text: str, max_size: int, overlap: int) -> list[str]:
    """
    对超长段落按句末标点 + 长度阈值进行二次切分，相邻块有 overlap。
    """
    if len(chunk_text) <= max_size:
        return [chunk_text] if chunk_text.strip() else []

    # 按句子边界切分
    sentences: list[str] = []
    start = 0
    for m in _SENTENCE_END_RE.finditer(chunk_text):
        end = m.end()
        sentences.append(chunk_text[start:end])
        start = end
    # 剩余部分
    if start < len(chunk_text):
        remaining = chunk_text[start:]
        if remaining.strip():
            sentences.append(remaining)

    # 按 max_size 组装，保留 overlap
    sub_chunks: list[str] = []
    current_chunk = ""
    for sent in sentences:
        if len(current_chunk) + len(sent) <= max_size:
            current_chunk += sent
        else:
            if current_chunk.strip():
                sub_chunks.append(current_chunk)
            # 新 chunk 以 overlap 量的旧内容开头
            if overlap > 0 and len(current_chunk) > overlap:
                current_chunk = current_chunk[-overlap:] + sent
            else:
                current_chunk = sent if current_chunk.strip() else current_chunk + sent

    if current_chunk.strip():
        sub_chunks.append(current_chunk)

    return sub_chunks


def chunk_all(files: list[dict]) -> list[dict]:
    """
    对所有文档分块，返回 chunk 字典列表。
    """
    chunks: list[dict] = []
    global_chunk_idx = 0

    for file_info in tqdm(files, desc="分块处理"):
        filename = file_info["filename"]
        content = file_info["content"]
        title = extract_title(content, filename)
        category = infer_category(filename)

        # 去掉标题行后的正文
        body = content
        first_line = content.split("\n")[0].strip()
        if first_line == title and "\n" in content:
            body = content[content.index("\n") + 1:]

        sections = split_into_sections(body)

        for sec in sections:
            sec_clean = sec.strip()
            if len(sec_clean) < MIN_CONTENT_LENGTH:
                continue

            # 对过长段落二次切分
            sub_parts = split_long_chunk(sec_clean, CHUNK_SIZE, CHUNK_OVERLAP)

            for part in sub_parts:
                part_clean = part.strip()
                if len(part_clean) < MIN_CONTENT_LENGTH:
                    continue

                global_chunk_idx += 1
                chunks.append({
                    "id": f"{ID_PREFIX}{global_chunk_idx:03d}",
                    "title": title,
                    "category": category,
                    "content": part_clean,
                    "chunk_index": len(sub_parts),
                    "source_file": filename,
                })

    return chunks


# ============================================================================
# 3. 去重
# ============================================================================

def deduplicate(chunks: list[dict], threshold: float) -> tuple[list[dict], int]:
    """
    使用 TF-IDF + 余弦相似度检测重复段落。
    返回 (去重后的 chunk 列表, 被移除的重复数量)。
    """
    if len(chunks) <= 1:
        return chunks, 0

    contents = [c["content"] for c in chunks]
    removed_count = 0

    try:
        vectorizer = TfidfVectorizer(analyzer="char", ngram_range=(2, 4), min_df=1)
        tfidf = vectorizer.fit_transform(contents)
    except ValueError:
        # 内容太短导致 vectorizer 失败，退回简单 Jaccard
        print("[警告] TF-IDF 向量化失败，回退到简单 Jaccard 去重")
        return _dedup_jaccard(chunks, threshold)

    # 分块计算，避免大矩阵内存溢出
    batch_size = 500
    keep_mask = [True] * len(chunks)

    for i in tqdm(range(0, len(chunks), batch_size), desc="去重处理"):
        end = min(i + batch_size, len(chunks))
        batch_vec = tfidf[i:end]
        sim = cosine_similarity(batch_vec, tfidf)

        for row_idx in range(sim.shape[0]):
            global_idx = i + row_idx
            if not keep_mask[global_idx]:
                continue
            # 检查与所有后续 chunk 的相似度
            for col_idx in range(global_idx + 1, len(chunks)):
                if not keep_mask[col_idx]:
                    continue
                if sim[row_idx, col_idx] >= threshold:
                    keep_mask[col_idx] = False
                    removed_count += 1

    result = [chunks[k] for k in range(len(chunks)) if keep_mask[k]]
    return result, removed_count


def _dedup_jaccard(chunks: list[dict], threshold: float) -> tuple[list[dict], int]:
    """
    基于字符 3-gram Jaccard 的简单去重回退方案。
    """

    def jaccard(a: str, b: str) -> float:
        set_a = {a[i:i + 3] for i in range(len(a) - 2)}
        set_b = {b[i:i + 3] for i in range(len(b) - 2)}
        if not set_a or not set_b:
            return 0.0
        return len(set_a & set_b) / len(set_a | set_b)

    keep = [True] * len(chunks)
    removed = 0

    for i in tqdm(range(len(chunks)), desc="去重处理(Jaccard)"):
        if not keep[i]:
            continue
        for j in range(i + 1, len(chunks)):
            if not keep[j]:
                continue
            if jaccard(chunks[i]["content"], chunks[j]["content"]) >= threshold:
                keep[j] = False
                removed += 1

    return [chunks[k] for k in range(len(chunks)) if keep[k]], removed


# ============================================================================
# 4. 输出与统计
# ============================================================================

def compute_stats(original_count: int, chunks: list[dict], removed: int) -> dict:
    """
    计算统计信息。
    """
    lengths = [len(c["content"]) for c in chunks]
    titles = set(c["source_file"] for c in chunks)

    return OrderedDict({
        "文档数": len(titles),
        "原始段落数": original_count,
        "去重后段落数": len(chunks),
        "重复移除数": removed,
        "平均段落长度": round(np.mean(lengths), 1) if lengths else 0,
        "最短段落长度": min(lengths) if lengths else 0,
        "最长段落长度": max(lengths) if lengths else 0,
        "分类分布": _category_distribution(chunks),
    })


def _category_distribution(chunks: list[dict]) -> dict:
    """统计各分类的段落数量分布。"""
    dist: dict[str, int] = {}
    for c in chunks:
        cat = c["category"]
        dist[cat] = dist.get(cat, 0) + 1
    return dict(sorted(dist.items()))


def main():
    print("=" * 60)
    print("  知识库预处理脚本")
    print("=" * 60)

    # ---- Step 1: 读取 ----
    print(f"\n[1/4] 读取知识库文件: {KNOWLEDGE_DIR}")
    files = read_knowledge_files(KNOWLEDGE_DIR)
    if not files:
        print("[错误] 未找到任何 .txt 文件，退出。")
        sys.exit(1)
    print(f"      找到 {len(files)} 个文件:")
    for f in files:
        print(f"        - {f['filename']} ({len(f['content'])} 字符)")

    # ---- Step 2: 分块 ----
    print(f"\n[2/4] 分块处理 (max_size={CHUNK_SIZE}, overlap={CHUNK_OVERLAP})")
    chunks = chunk_all(files)
    original_count = len(chunks)
    print(f"      生成 {original_count} 个段落块")

    # ---- Step 3: 去重 ----
    print(f"\n[3/4] 去重处理 (threshold={DEDUP_THRESHOLD})")
    chunks, removed = deduplicate(chunks, DEDUP_THRESHOLD)
    print(f"      移除 {removed} 个重复段落")

    # ---- Step 4: 输出 ----
    print(f"\n[4/4] 写入输出文件: {OUTPUT_FILE}")

    # 移除内部字段后排序输出
    output = []
    for i, c in enumerate(chunks):
        output.append(OrderedDict([
            ("id", c["id"]),
            ("title", c["title"]),
            ("category", c["category"]),
            ("content", c["content"]),
            ("chunk_index", c["chunk_index"]),
            ("source_file", c["source_file"]),
        ]))

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    # ---- 统计 ----
    stats = compute_stats(original_count, chunks, removed)
    print(f"\n{'=' * 60}")
    print("  预处理统计")
    print(f"{'=' * 60}")
    for key, value in stats.items():
        if key == "分类分布":
            print(f"  {key}:")
            for cat, count in value.items():
                print(f"    - {cat}: {count} 段")
        else:
            print(f"  {key}: {value}")
    print(f"\n  输出文件: {OUTPUT_FILE}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
