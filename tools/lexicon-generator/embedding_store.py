#!/usr/bin/env python3
"""Shared helpers for storing/querying normalized embedding artifacts."""

from __future__ import annotations

import datetime as dt
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

WORD_RE = re.compile(r"^[a-z]{2,}$")
EMBEDDINGS_FILE = "embeddings.f32.memmap"
WORDS_FILE = "words.json"
METADATA_FILE = "metadata.json"
VECTOR_NORMALIZATION = "unit_length"


def load_words(words_file: Path, limit: Optional[int]) -> List[str]:
    words: List[str] = []
    seen = set()
    for line in words_file.read_text(encoding="utf-8").splitlines():
        token = line.strip().lower()
        if not WORD_RE.fullmatch(token):
            continue
        if token in seen:
            continue
        words.append(token)
        seen.add(token)
        if limit is not None and len(words) >= limit:
            break
    if not words:
        raise SystemExit(f"No valid words loaded from {words_file}")
    return words


def normalize_rows(vectors: np.ndarray) -> np.ndarray:
    vectors = np.asarray(vectors, dtype=np.float32)
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    norms = np.maximum(norms, 1e-12)
    return vectors / norms


def write_embeddings_memmap(normalized_embeddings: np.ndarray, out_path: Path) -> None:
    embeddings = np.asarray(normalized_embeddings, dtype=np.float32)
    if embeddings.ndim != 2:
        raise SystemExit("Embeddings must be a 2D array.")
    mmap = np.memmap(out_path, mode="w+", dtype=np.float32, shape=embeddings.shape)
    mmap[:, :] = embeddings
    mmap.flush()


def write_words_file(path: Path, words: Sequence[str]) -> None:
    payload = {
        "words": list(words),
        "wordToIndex": {word: index for index, word in enumerate(words)},
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def write_metadata_file(
    path: Path,
    *,
    output_dir: Path,
    provider: str,
    model_id: str,
    n_words: int,
    embedding_dim: int,
) -> None:
    payload = {
        "createdAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "provider": provider,
        "model": model_id,
        "metric": "cosine_distance",
        "dtype": "float32",
        "shape": [n_words, embedding_dim],
        "embeddingDimension": embedding_dim,
        "vectorNormalization": VECTOR_NORMALIZATION,
        "storage": {
            "format": "memmap",
            "layout": "row_major",
        },
        "files": {
            "embeddings": str(output_dir / EMBEDDINGS_FILE),
            "words": str(output_dir / WORDS_FILE),
            "metadata": str(output_dir / METADATA_FILE),
        },
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def load_words_payload(path: Path) -> Dict[str, object]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise SystemExit(f"Invalid words payload in {path}")
    return payload


def load_metadata(path: Path) -> Dict[str, object]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise SystemExit(f"Invalid metadata payload in {path}")
    return payload


def _require_metadata_keys(metadata: Dict[str, object], keys: Sequence[str]) -> None:
    missing = [key for key in keys if key not in metadata]
    if missing:
        raise SystemExit(f"metadata.json is missing required keys: {', '.join(missing)}")


def _load_embeddings(output_dir: Path, metadata: Dict[str, object], n_words: int) -> np.memmap:
    _require_metadata_keys(metadata, ("shape", "embeddingDimension", "vectorNormalization"))

    shape = metadata.get("shape")
    embedding_dim = metadata.get("embeddingDimension")
    vector_normalization = metadata.get("vectorNormalization")
    files = metadata.get("files")

    if vector_normalization != VECTOR_NORMALIZATION:
        raise SystemExit("Only unit_length normalized embeddings are supported")
    if not isinstance(shape, list) or len(shape) != 2:
        raise SystemExit("metadata shape must be [n_words, embedding_dim] with embedding_dim > 0")
    if not isinstance(embedding_dim, int) or embedding_dim <= 0:
        raise SystemExit("metadata shape must be [n_words, embedding_dim] with embedding_dim > 0")

    try:
        n_rows = int(shape[0])
        n_cols = int(shape[1])
    except (TypeError, ValueError):
        raise SystemExit("metadata shape must be [n_words, embedding_dim] with embedding_dim > 0") from None

    if n_rows != n_words or n_cols != embedding_dim:
        raise SystemExit("metadata shape does not match words index length")

    embeddings_path_str = None
    if isinstance(files, dict):
        raw_path = files.get("embeddings")
        if isinstance(raw_path, str) and raw_path:
            embeddings_path_str = raw_path
    if embeddings_path_str is None:
        embeddings_path_str = str(output_dir / EMBEDDINGS_FILE)
    embeddings_path = Path(embeddings_path_str)
    if not embeddings_path.exists():
        raise SystemExit(f"Embeddings file not found: {embeddings_path}")

    expected_bytes = n_rows * n_cols * np.dtype(np.float32).itemsize
    actual_bytes = embeddings_path.stat().st_size
    if actual_bytes != expected_bytes:
        raise SystemExit("embeddings.f32.memmap size does not match metadata shape.")

    return np.memmap(embeddings_path, mode="r", dtype=np.float32, shape=(n_rows, n_cols))


def query_neighbors(output_dir: Path, query_word: str, top_k: Optional[int]) -> List[Tuple[str, float]]:
    words_payload = load_words_payload(output_dir / WORDS_FILE)
    metadata = load_metadata(output_dir / METADATA_FILE)

    words = words_payload.get("words")
    word_to_index = words_payload.get("wordToIndex")
    if not isinstance(words, list) or not isinstance(word_to_index, dict):
        raise SystemExit("words.json is missing required keys: words, wordToIndex")

    normalized_query = query_word.strip().lower()
    if normalized_query not in word_to_index:
        raise SystemExit(f"Word '{normalized_query}' not found in index.")

    row_index = int(word_to_index[normalized_query])
    embeddings = _load_embeddings(output_dir, metadata, len(words))

    if top_k is not None and top_k <= 0:
        raise SystemExit("--top-k must be > 0 when set")
    limit = len(words) if top_k is None else min(top_k, len(words))

    query_vector = np.asarray(embeddings[row_index], dtype=np.float32)
    similarities = np.asarray(embeddings @ query_vector, dtype=np.float32)
    distances = np.asarray(1.0 - similarities, dtype=np.float32)
    np.clip(distances, 0.0, 2.0, out=distances)
    order = np.argsort(distances, kind="stable")

    return [(str(words[int(idx)]), float(distances[int(idx)])) for idx in order[:limit]]
