#!/usr/bin/env python3
"""Build/query a full cosine-distance matrix using SentenceTransformer embeddings."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
from tqdm import tqdm

WORD_RE = re.compile(r"^[a-z]{2,}$")
MODEL_ID = "google/embeddinggemma-300m"
PROVIDER = "sentence-transformers"
DEFAULT_WORDS_FILE = "content/lexicon/common_words_20k.txt"
DEFAULT_OUTPUT_DIR = "content/lexicon/embeddings/sentence_transformer_embeddinggemma300m"
DISTANCE_FILE = "distances.f32.memmap"
WORDS_FILE = "words.json"
METADATA_FILE = "metadata.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build/query all-pairs cosine-distance matrix using SentenceTransformer."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build", help="Build embeddings and all-pairs distance matrix.")
    build.add_argument(
        "--words-file",
        default=DEFAULT_WORDS_FILE,
        help="Path to canonical 20k word list.",
    )
    build.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where matrix artifacts are written.",
    )
    build.add_argument(
        "--batch-size",
        type=int,
        default=256,
        help="Embedding batch size for SentenceTransformer encoding.",
    )
    build.add_argument(
        "--distance-batch-size",
        type=int,
        default=256,
        help="Row chunk size when computing distance matrix.",
    )
    build.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Optional dev mode to use only first N words.",
    )

    query = subparsers.add_parser("query", help="Query nearest words from built matrix.")
    query.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Directory containing matrix artifacts.",
    )
    query.add_argument(
        "--word",
        required=True,
        help="Query word to rank neighbors for.",
    )
    query.add_argument(
        "--top-k",
        type=int,
        default=None,
        help="Optional number of rows to print. If omitted, prints all rows.",
    )

    return parser.parse_args()


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
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    norms = np.maximum(norms, 1e-12)
    return vectors / norms


def build_embeddings(words: List[str], batch_size: int) -> np.ndarray:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise SystemExit(
            "Missing dependency: install with `pip install -r tools/lexicon-generator/requirements.txt`"
        ) from exc

    model = SentenceTransformer(MODEL_ID)
    embeddings = model.encode(
        words,
        batch_size=batch_size,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=False,
    )
    return np.asarray(embeddings, dtype=np.float32)


def write_words_file(path: Path, words: List[str]) -> None:
    payload = {
        "words": words,
        "wordToIndex": {word: index for index, word in enumerate(words)},
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def write_metadata_file(
    path: Path,
    *,
    output_dir: Path,
    n_words: int,
    embedding_dim: int,
) -> None:
    payload = {
        "createdAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "provider": PROVIDER,
        "model": MODEL_ID,
        "metric": "cosine_distance",
        "dtype": "float32",
        "shape": [n_words, n_words],
        "embeddingDimension": embedding_dim,
        "files": {
            "distances": str(output_dir / DISTANCE_FILE),
            "words": str(output_dir / WORDS_FILE),
            "metadata": str(output_dir / METADATA_FILE),
        },
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def build_distance_matrix(
    normalized_embeddings: np.ndarray,
    out_path: Path,
    distance_batch_size: int,
) -> None:
    n_rows = normalized_embeddings.shape[0]
    mmap = np.memmap(out_path, mode="w+", dtype=np.float32, shape=(n_rows, n_rows))
    all_t = normalized_embeddings.T

    for start in tqdm(
        range(0, n_rows, distance_batch_size),
        desc="Computing distances",
        unit="rows",
    ):
        end = min(start + distance_batch_size, n_rows)
        sims = normalized_embeddings[start:end] @ all_t
        dists = 1.0 - sims
        np.clip(dists, 0.0, 2.0, out=dists)
        mmap[start:end, :] = dists.astype(np.float32, copy=False)

    mmap.flush()


def run_build(args: argparse.Namespace) -> int:
    if args.batch_size <= 0:
        raise SystemExit("--batch-size must be > 0")
    if args.distance_batch_size <= 0:
        raise SystemExit("--distance-batch-size must be > 0")
    if args.limit is not None and args.limit <= 0:
        raise SystemExit("--limit must be > 0 when set")

    words_file = Path(args.words_file)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    words = load_words(words_file=words_file, limit=args.limit)
    print(f"Loaded {len(words)} words from {words_file}")

    embeddings = build_embeddings(words=words, batch_size=args.batch_size)
    if embeddings.shape[0] != len(words):
        raise SystemExit(
            f"Embedding count mismatch: got {embeddings.shape[0]} vectors for {len(words)} words."
        )
    normalized = normalize_rows(embeddings)

    distance_path = output_dir / DISTANCE_FILE
    build_distance_matrix(
        normalized_embeddings=normalized,
        out_path=distance_path,
        distance_batch_size=args.distance_batch_size,
    )

    write_words_file(output_dir / WORDS_FILE, words)
    write_metadata_file(
        output_dir / METADATA_FILE,
        output_dir=output_dir,
        n_words=len(words),
        embedding_dim=int(embeddings.shape[1]),
    )

    print(f"Wrote distance matrix to {distance_path}")
    print(f"Wrote word index to {output_dir / WORDS_FILE}")
    print(f"Wrote metadata to {output_dir / METADATA_FILE}")
    return 0


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


def run_query(args: argparse.Namespace) -> int:
    output_dir = Path(args.output_dir)
    words_payload = load_words_payload(output_dir / WORDS_FILE)
    metadata = load_metadata(output_dir / METADATA_FILE)

    words = words_payload.get("words")
    word_to_index = words_payload.get("wordToIndex")
    shape = metadata.get("shape")
    if not isinstance(words, list) or not isinstance(word_to_index, dict):
        raise SystemExit("words.json is missing required keys: words, wordToIndex")
    if not isinstance(shape, list) or len(shape) != 2:
        raise SystemExit("metadata.json is missing required key: shape")

    query_word = args.word.strip().lower()
    if query_word not in word_to_index:
        raise SystemExit(f"Word '{query_word}' not found in index.")
    row_index = int(word_to_index[query_word])
    n_rows = int(shape[0])
    n_cols = int(shape[1])
    if n_rows != len(words) or n_cols != len(words):
        raise SystemExit("metadata shape does not match words index length.")

    mmap = np.memmap(output_dir / DISTANCE_FILE, mode="r", dtype=np.float32, shape=(n_rows, n_cols))
    row = np.asarray(mmap[row_index], dtype=np.float32)
    order = np.argsort(row, kind="stable")

    top_k = args.top_k if args.top_k is not None else len(words)
    if top_k <= 0:
        raise SystemExit("--top-k must be > 0 when set")
    top_k = min(top_k, len(words))

    print("rank\tword\tdistance")
    for rank, idx in enumerate(order[:top_k], start=1):
        print(f"{rank}\t{words[int(idx)]}\t{float(row[int(idx)]):.8f}")
    return 0


def main() -> int:
    args = parse_args()
    if args.command == "build":
        return run_build(args)
    if args.command == "query":
        return run_query(args)
    raise SystemExit(f"Unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
