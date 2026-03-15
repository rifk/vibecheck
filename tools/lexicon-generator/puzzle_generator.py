#!/usr/bin/env python3
"""Generate daily puzzle JSON files from word-of-day entries and embeddings."""

from __future__ import annotations

import argparse
import datetime as dt
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np


DEFAULT_WORD_OF_DAY = "content/lexicon/word_of_day.json"
DEFAULT_MODEL_CATALOG = "content/models/model_info.json"
DEFAULT_OUTPUT_DIR = "content/puzzles"

DEFAULT_EMBEDDING_DIRS: Dict[str, str] = {
    "google-embeddinggemma-300m": "content/lexicon/embeddings/sentence_transformer_embeddinggemma300m",
    "openai-text-embedding-3-large": "content/lexicon/embeddings/openai_text_embedding_3_large",
    "alibaba-gte-qwen2-7b-instruct": "content/lexicon/embeddings/sentence_transformer_alibaba_gte-qwen2-1_5b-instruct",
}


@dataclass
class ModelEmbedding:
    model_id: str
    words: List[str]
    word_to_index: Dict[str, int]
    embeddings: np.memmap


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate daily puzzle JSON files from word-of-day + embeddings."
    )
    parser.add_argument(
        "--word-of-day",
        default=DEFAULT_WORD_OF_DAY,
        help="Path to word_of_day.json file.",
    )
    parser.add_argument(
        "--model-catalog",
        default=DEFAULT_MODEL_CATALOG,
        help="Path to model_info.json file.",
    )
    parser.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_DIR,
        help="Output directory for puzzle JSON files.",
    )
    parser.add_argument(
        "--embedding-dir",
        action="append",
        default=[],
        help="Override embedding dir mapping: modelId=path. Repeatable.",
    )
    parser.add_argument(
        "--overwrite-existing",
        action="store_true",
        help="Overwrite puzzle files that already exist.",
    )
    parser.add_argument(
        "--start-date",
        help="Optional start date (YYYY-MM-DD) to filter entries.",
    )
    parser.add_argument(
        "--days",
        type=int,
        help="Optional number of days to generate from start date.",
    )
    return parser.parse_args()


def parse_model_catalog(path: Path) -> List[str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    models = payload.get("models")
    if not isinstance(models, list) or not models:
        raise SystemExit(f"Model catalog missing or invalid: {path}")
    model_ids: List[str] = []
    for entry in models:
        if not isinstance(entry, dict):
            continue
        model_id = entry.get("modelId")
        if isinstance(model_id, str) and model_id.strip():
            model_ids.append(model_id.strip())
    if not model_ids:
        raise SystemExit(f"Model catalog has no modelId entries: {path}")
    return model_ids


def parse_embedding_overrides(values: Sequence[str]) -> Dict[str, str]:
    overrides: Dict[str, str] = {}
    for raw in values:
        if "=" not in raw:
            raise SystemExit(f"--embedding-dir must be modelId=path, got: {raw}")
        model_id, path = raw.split("=", 1)
        model_id = model_id.strip()
        path = path.strip()
        if not model_id or not path:
            raise SystemExit(f"--embedding-dir must be modelId=path, got: {raw}")
        overrides[model_id] = path
    return overrides


def resolve_embedding_dirs(model_ids: Sequence[str], overrides: Dict[str, str]) -> Dict[str, Path]:
    resolved: Dict[str, Path] = {}
    missing = []
    for model_id in model_ids:
        path = overrides.get(model_id) or DEFAULT_EMBEDDING_DIRS.get(model_id)
        if not path:
            missing.append(model_id)
            continue
        resolved[model_id] = Path(path)
    if missing:
        raise SystemExit(
            "No embedding directory mapping for modelId(s): " + ", ".join(missing)
        )
    return resolved


def load_word_of_day(path: Path) -> Dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    entries = payload.get("entries")
    if not isinstance(entries, dict) or not entries:
        raise SystemExit(f"word_of_day.json missing entries: {path}")
    cleaned: Dict[str, str] = {}
    for date_key, word in entries.items():
        if isinstance(date_key, str) and isinstance(word, str):
            cleaned[date_key] = word.strip().lower()
    if not cleaned:
        raise SystemExit(f"word_of_day.json has no valid entries: {path}")
    return cleaned


def filter_entries(
    entries: Dict[str, str],
    start_date: Optional[str],
    days: Optional[int],
) -> Dict[str, str]:
    if start_date is None and days is None:
        return dict(sorted(entries.items()))
    if start_date is None or days is None:
        raise SystemExit("--start-date and --days must be provided together.")
    if days <= 0:
        raise SystemExit("--days must be > 0")

    start = dt.date.fromisoformat(start_date)
    target_dates = {
        (start + dt.timedelta(days=offset)).isoformat() for offset in range(days)
    }
    filtered = {date_key: entries[date_key] for date_key in sorted(target_dates) if date_key in entries}
    if not filtered:
        raise SystemExit("No word-of-day entries found in the requested date range.")
    return filtered


def load_words_payload(path: Path) -> Tuple[List[str], Dict[str, int]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    words = payload.get("words")
    word_to_index = payload.get("wordToIndex")
    if not isinstance(words, list) or not isinstance(word_to_index, dict):
        raise SystemExit(f"Invalid words payload: {path}")
    cleaned_words: List[str] = []
    cleaned_word_to_index: Dict[str, int] = {}
    for idx, word in enumerate(words):
        if isinstance(word, str):
            cleaned_words.append(word)
            cleaned_word_to_index[word] = idx
    if not cleaned_words:
        raise SystemExit(f"words.json has no valid words: {path}")
    return cleaned_words, cleaned_word_to_index


def load_embeddings(output_dir: Path, words_len: int) -> np.memmap:
    metadata_path = output_dir / "metadata.json"
    payload = json.loads(metadata_path.read_text(encoding="utf-8"))
    shape = payload.get("shape")
    embedding_dim = payload.get("embeddingDimension")
    if not isinstance(shape, list) or len(shape) != 2:
        raise SystemExit(f"metadata.json invalid shape in {metadata_path}")
    if not isinstance(embedding_dim, int) or embedding_dim <= 0:
        raise SystemExit(f"metadata.json invalid embeddingDimension in {metadata_path}")
    try:
        n_rows = int(shape[0])
        n_cols = int(shape[1])
    except (TypeError, ValueError) as exc:
        raise SystemExit(f"metadata.json invalid shape in {metadata_path}") from exc
    if n_rows != words_len or n_cols != embedding_dim:
        raise SystemExit("metadata.json shape does not match words length.")

    embeddings_path = output_dir / "embeddings.f32.memmap"
    if not embeddings_path.exists():
        raise SystemExit(f"Embeddings file not found: {embeddings_path}")
    return np.memmap(
        embeddings_path,
        mode="r",
        dtype=np.float32,
        shape=(n_rows, n_cols),
    )


def load_model_embeddings(model_id: str, embedding_dir: Path) -> ModelEmbedding:
    words_path = embedding_dir / "words.json"
    if not words_path.exists():
        raise SystemExit(f"Missing words.json for {model_id}: {words_path}")
    words, word_to_index = load_words_payload(words_path)
    embeddings = load_embeddings(embedding_dir, len(words))
    return ModelEmbedding(
        model_id=model_id,
        words=words,
        word_to_index=word_to_index,
        embeddings=embeddings,
    )


def build_ranked_words(
    model: ModelEmbedding,
    answer: str,
) -> List[str]:
    if answer not in model.word_to_index:
        raise SystemExit(
            f"Answer '{answer}' missing from embeddings word list for {model.model_id}."
        )
    row_index = model.word_to_index[answer]
    answer_vector = np.asarray(model.embeddings[row_index], dtype=np.float32)
    similarities = np.asarray(model.embeddings @ answer_vector, dtype=np.float32)
    order = np.argsort(-similarities, kind="stable")
    return [model.words[int(idx)] for idx in order]


def write_puzzle_file(
    output_dir: Path,
    utc_date: str,
    answer: str,
    models: Sequence[Tuple[str, List[str]]],
) -> None:
    payload = {
        "utcDate": utc_date,
        "answer": answer,
        "models": [
            {"modelId": model_id, "rankedWords": ranked_words}
            for model_id, ranked_words in models
        ],
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / f"{utc_date}.json"
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    word_of_day_path = Path(args.word_of_day)
    model_catalog_path = Path(args.model_catalog)
    output_dir = Path(args.output_dir)

    entries = load_word_of_day(word_of_day_path)
    filtered_entries = filter_entries(entries, args.start_date, args.days)

    model_ids = parse_model_catalog(model_catalog_path)
    overrides = parse_embedding_overrides(args.embedding_dir)
    embedding_dirs = resolve_embedding_dirs(model_ids, overrides)

    model_embeddings = {
        model_id: load_model_embeddings(model_id, embedding_dirs[model_id])
        for model_id in model_ids
    }

    targets: List[str] = []
    for utc_date in filtered_entries.keys():
        path = output_dir / f"{utc_date}.json"
        if path.exists() and not args.overwrite_existing:
            continue
        targets.append(utc_date)

    if not targets:
        print("No puzzle files to generate for requested dates.")
        return 0

    for utc_date in targets:
        answer = filtered_entries[utc_date]
        ranked = [
            (model_id, build_ranked_words(model_embeddings[model_id], answer))
            for model_id in model_ids
        ]
        write_puzzle_file(output_dir, utc_date, answer, ranked)
        print(f"Wrote puzzle: {output_dir / f'{utc_date}.json'}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
