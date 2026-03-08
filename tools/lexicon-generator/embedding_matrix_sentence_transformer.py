#!/usr/bin/env python3
"""Build/query cosine-distance rankings from stored SentenceTransformer embeddings."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import List

import numpy as np

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

MODEL_ID = "google/embeddinggemma-300m"
PROVIDER = "sentence-transformers"
DEFAULT_WORDS_FILE = "content/lexicon/common_words_20k.txt"
DEFAULT_OUTPUT_DIR = "content/lexicon/embeddings/sentence_transformer_embeddinggemma300m"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build/query cosine-distance rankings from stored SentenceTransformer embeddings."
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
        default=256,
        help="Embedding batch size for SentenceTransformer encoding.",
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
