#!/usr/bin/env python3
"""Build/query cosine-distance rankings from stored OpenAI embeddings."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
from typing import List

import numpy as np
from tqdm import tqdm

from embedding_store import (
    EMBEDDINGS_FILE,
    METADATA_FILE,
    WORDS_FILE,
    load_words,
    normalize_rows,
    query_neighbors,
    write_embeddings_memmap,
    write_metadata_file,
    write_words_file,
)

MODEL_ID = "text-embedding-3-small"
PROVIDER = "openai"
DEFAULT_WORDS_FILE = "content/lexicon/common_words_20k.txt"
DEFAULT_OUTPUT_DIR = "content/lexicon/embeddings/openai_text_embedding_3_small"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build/query cosine-distance rankings from stored OpenAI embeddings."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build", help="Build and store normalized embeddings.")
    build.add_argument(
        "--words-file",
        default=DEFAULT_WORDS_FILE,
        help="Path to canonical 20k word list.",
    )
    build.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Directory where embedding artifacts are written.",
    )
    build.add_argument(
        "--batch-size",
        type=int,
        default=512,
        help="Embedding API batch size.",
    )
    build.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Optional dev mode to use only first N words.",
    )

    query = subparsers.add_parser("query", help="Query nearest words from stored embeddings.")
    query.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Directory containing embedding artifacts.",
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


def build_embeddings(words: List[str], batch_size: int) -> np.ndarray:
    try:
        from openai import OpenAI
    except ImportError as exc:
        raise SystemExit(
            "Missing dependency: install with `pip install -r tools/lexicon-generator/requirements.txt`"
        ) from exc

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is required for OpenAI embeddings.")

    client = OpenAI(api_key=api_key)
    vectors: List[List[float]] = []

    for start in tqdm(range(0, len(words), batch_size), desc="Fetching embeddings", unit="batch"):
        end = min(start + batch_size, len(words))
        batch = words[start:end]
        response = client.embeddings.create(model=MODEL_ID, input=batch)
        vectors.extend(item.embedding for item in response.data)

    embeddings = np.asarray(vectors, dtype=np.float32)
    return embeddings


def run_build(args: argparse.Namespace) -> int:
    if args.batch_size <= 0:
        raise SystemExit("--batch-size must be > 0")
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

    embeddings_path = output_dir / EMBEDDINGS_FILE
    write_embeddings_memmap(normalized, embeddings_path)

    write_words_file(output_dir / WORDS_FILE, words)
    write_metadata_file(
        output_dir / METADATA_FILE,
        output_dir=output_dir,
        provider=PROVIDER,
        model_id=MODEL_ID,
        n_words=len(words),
        embedding_dim=int(embeddings.shape[1]),
    )

    print(f"Wrote embeddings to {embeddings_path}")
    print(f"Wrote word index to {output_dir / WORDS_FILE}")
    print(f"Wrote metadata to {output_dir / METADATA_FILE}")
    return 0


def run_query(args: argparse.Namespace) -> int:
    rows = query_neighbors(Path(args.output_dir), args.word, args.top_k)
    print("rank\tword\tdistance")
    for rank, (word, distance) in enumerate(rows, start=1):
        print(f"{rank}\t{word}\t{distance:.8f}")
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
