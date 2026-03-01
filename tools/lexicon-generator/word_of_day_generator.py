#!/usr/bin/env python3
"""Create or update a word-of-the-day file from the canonical lexicon."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import random
import re
from collections import Counter
from pathlib import Path
from typing import Dict, List

WORD_RE = re.compile(r"^[a-z]{2,}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create/update a date->word file using the canonical lexicon."
    )
    parser.add_argument(
        "--words-file",
        default="content/lexicon/common_words_20k.txt",
        help="Path to canonical word list file.",
    )
    parser.add_argument(
        "--output",
        default="content/lexicon/word_of_day.json",
        help="Path to output JSON file.",
    )
    parser.add_argument(
        "--start-date",
        required=True,
        help="Start date in YYYY-MM-DD.",
    )
    parser.add_argument(
        "--days",
        required=True,
        type=int,
        help="Number of days to generate/update.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Optional RNG seed for deterministic output.",
    )
    parser.add_argument(
        "--overwrite-existing",
        action="store_true",
        help="Replace already assigned words in the target date range.",
    )
    parser.add_argument(
        "--common-bias",
        type=float,
        default=1.5,
        help=(
            "Bias toward earlier (more common) words in the 20k list. "
            "1.0 = uniform, higher values increase common-word preference."
        ),
    )
    return parser.parse_args()


def load_words(words_file: Path) -> List[str]:
    words = []
    seen = set()
    for line in words_file.read_text(encoding="utf-8").splitlines():
        word = line.strip().lower()
        if WORD_RE.fullmatch(word) and word not in seen:
            words.append(word)
            seen.add(word)
    if not words:
        raise SystemExit(f"No valid words found in {words_file}")
    return words


def load_existing_entries(output_file: Path) -> Dict[str, str]:
    if not output_file.exists():
        return {}
    payload = json.loads(output_file.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        return {}
    entries = payload.get("entries", {})
    if not isinstance(entries, dict):
        return {}
    cleaned: Dict[str, str] = {}
    for date_key, word in entries.items():
        if not isinstance(date_key, str) or not isinstance(word, str):
            continue
        cleaned[date_key] = word
    return cleaned


def weighted_pick_unique(
    words_in_rank_order: List[str],
    used_words: set[str],
    rng: random.Random,
    common_bias: float,
) -> str:
    available_words = [word for word in words_in_rank_order if word not in used_words]
    if not available_words:
        raise SystemExit("No available words remain to pick from.")

    if common_bias <= 1.0:
        return rng.choice(available_words)

    # Slightly prefer earlier (more common) words while still allowing tail selection.
    weights = []
    total = len(available_words)
    for index, _word in enumerate(available_words):
        normalized_rank = index / max(1, total - 1)  # 0 = most common, 1 = least common
        weight = 1.0 + (common_bias - 1.0) * (1.0 - normalized_rank)
        weights.append(weight)
    return rng.choices(available_words, weights=weights, k=1)[0]


def main() -> int:
    args = parse_args()
    if args.days <= 0:
        raise SystemExit("--days must be > 0")
    if args.common_bias <= 0:
        raise SystemExit("--common-bias must be > 0")

    words_file = Path(args.words_file)
    output_file = Path(args.output)

    words = load_words(words_file)
    rng = random.Random(args.seed)
    existing_entries = load_existing_entries(output_file)

    start_date = dt.date.fromisoformat(args.start_date)
    target_dates = [
        (start_date + dt.timedelta(days=offset)).isoformat()
        for offset in range(args.days)
    ]

    editable_dates = [
        date_key
        for date_key in target_dates
        if args.overwrite_existing or date_key not in existing_entries
    ]

    locked_entries = {
        date_key: word
        for date_key, word in existing_entries.items()
        if date_key not in set(editable_dates)
    }

    duplicates = [word for word, count in Counter(locked_entries.values()).items() if count > 1]
    if duplicates:
        raise SystemExit(
            "Existing locked entries already contain duplicate words; cannot guarantee uniqueness "
            "without editing those dates. Re-run with --overwrite-existing for a broader range."
        )

    used_words = set(locked_entries.values())
    total_needed = len(editable_dates)
    total_available = len(set(words) - used_words)
    if total_needed > total_available:
        raise SystemExit(
            f"Not enough unique words available: need {total_needed}, but only {total_available} "
            "unused words remain."
        )

    for date_key in editable_dates:
        chosen = weighted_pick_unique(
            words_in_rank_order=words,
            used_words=used_words,
            rng=rng,
            common_bias=args.common_bias,
        )
        existing_entries[date_key] = chosen
        used_words.add(chosen)

    output_file.parent.mkdir(parents=True, exist_ok=True)
    result = {
        "generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "sourceWordsFile": str(words_file),
        "entryCount": len(existing_entries),
        "commonBias": args.common_bias,
        "entries": dict(sorted(existing_entries.items())),
    }
    output_file.write_text(json.dumps(result, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(existing_entries)} word-of-the-day entries to {output_file}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
