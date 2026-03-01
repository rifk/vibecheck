#!/usr/bin/env python3
"""Build canonical lexicon + lemma map and prune puzzle files.

Commands:
  generate  -> writes common_words_20k.txt, lemma_map.json, lexicon_metadata.json
  prune     -> removes puzzle words not in canonical list and rewrites puzzle files
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

TOKEN_RE = re.compile(r"^[a-z]{2,}$")
WORDNET_POS = ("n", "v", "a", "r")


def _stable_json(data: object) -> str:
    return json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def normalize_token(word: str) -> str:
    return word.strip().lower()


def is_valid_token(word: str) -> bool:
    return bool(TOKEN_RE.fullmatch(word))


def load_canonical_words(path: Path) -> List[str]:
    words = [normalize_token(line) for line in path.read_text(encoding="utf-8").splitlines()]
    words = [word for word in words if is_valid_token(word)]
    return words


def require_modules() -> Tuple[object, object, object]:
    try:
        import nltk
        from nltk.stem import WordNetLemmatizer
        from wordfreq import top_n_list, zipf_frequency
    except ImportError as exc:  # pragma: no cover - runtime dependency guard
        raise SystemExit(
            "Missing dependency: install with `pip install -r tools/lexicon-generator/requirements.txt`"
        ) from exc

    try:
        nltk.data.find("corpora/wordnet")
    except LookupError:
        nltk.download("wordnet", quiet=True)
        try:
            nltk.data.find("corpora/wordnet")
        except LookupError as exc:
            raise SystemExit(
                "WordNet corpus is missing. Run `python3 -m nltk.downloader wordnet` and retry."
            ) from exc

    return WordNetLemmatizer, top_n_list, zipf_frequency


def canonical_candidate(
    token: str,
    lemmatizer: object,
    zipf_frequency,
) -> str:
    lemmas = {token}
    for pos in WORDNET_POS:
        lemma = normalize_token(lemmatizer.lemmatize(token, pos=pos))
        if is_valid_token(lemma):
            lemmas.add(lemma)

    if len(lemmas) == 1:
        return token

    scored: List[Tuple[float, str]] = []
    for lemma in lemmas:
        score = zipf_frequency(lemma, "en")
        scored.append((score, lemma))

    scored.sort(key=lambda item: (-item[0], item[1]))
    return scored[0][1]


def generate_lexicon(args: argparse.Namespace) -> int:
    WordNetLemmatizer, top_n_list, zipf_frequency = require_modules()
    lemmatizer = WordNetLemmatizer()

    candidates = [
        normalize_token(word)
        for word in top_n_list("en", args.candidate_count)
    ]
    candidates = [word for word in candidates if is_valid_token(word)]

    canonical_score: Dict[str, float] = defaultdict(float)
    form_to_canonical: Dict[str, str] = {}

    for token in candidates:
        canonical = canonical_candidate(token, lemmatizer, zipf_frequency)
        form_to_canonical[token] = canonical
        canonical_score[canonical] += zipf_frequency(token, "en")

    ranked_canonicals = sorted(
        canonical_score.items(),
        key=lambda item: (-item[1], item[0]),
    )
    canonical_words = [word for word, _ in ranked_canonicals[: args.target_count]]

    if len(canonical_words) != args.target_count:
        raise SystemExit(
            f"Only produced {len(canonical_words)} canonical words; increase --candidate-count."
        )

    canonical_set = set(canonical_words)

    lemma_map = {
        form: canonical
        for form, canonical in sorted(form_to_canonical.items())
        if form != canonical and canonical in canonical_set
    }

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    words_path = output_dir / "common_words_20k.txt"
    lemma_path = output_dir / "lemma_map.json"
    metadata_path = output_dir / "lexicon_metadata.json"

    words_path.write_text("\n".join(canonical_words) + "\n", encoding="utf-8")
    lemma_path.write_text(
        json.dumps(lemma_map, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    metadata = {
        "generatedAtUtc": dt.datetime.now(dt.UTC).isoformat(),
        "targetCanonicalCount": args.target_count,
        "candidateCount": args.candidate_count,
        "tokenRegex": TOKEN_RE.pattern,
        "source": {
            "frequency": "wordfreq",
            "lemmatizer": "nltk.wordnet",
        },
        "files": {
            "common_words_20k": str(words_path),
            "lemma_map": str(lemma_path),
        },
        "hashes": {
            "canonicalWordsSha256": hashlib.sha256(words_path.read_bytes()).hexdigest(),
            "lemmaMapSha256": hashlib.sha256(_stable_json(lemma_map).encode("utf-8")).hexdigest(),
        },
    }
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")

    print(f"Wrote {len(canonical_words)} canonical words to {words_path}")
    print(f"Wrote {len(lemma_map)} lemma mappings to {lemma_path}")
    print(f"Wrote metadata to {metadata_path}")
    return 0


def dedupe_preserve_order(words: Iterable[str]) -> List[str]:
    seen = set()
    result = []
    for word in words:
        if word in seen:
            continue
        seen.add(word)
        result.append(word)
    return result


def prune_puzzles(args: argparse.Namespace) -> int:
    puzzles_dir = Path(args.puzzles_dir)
    canonical_words = set(load_canonical_words(Path(args.canonical_words)))
    if not canonical_words:
        raise SystemExit("Canonical list is empty. Generate lexicon first.")

    report = {
        "timestampUtc": dt.datetime.now(dt.UTC).isoformat(),
        "canonicalWordList": str(Path(args.canonical_words)),
        "processedFiles": [],
        "deletedFiles": [],
        "summary": {
            "filesProcessed": 0,
            "filesUpdated": 0,
            "filesDeleted": 0,
            "modelsRemoved": 0,
            "wordsRemoved": 0,
            "answersReplaced": 0,
        },
    }

    for path in sorted(puzzles_dir.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        models = data.get("models") or []

        file_changes = {
            "file": str(path),
            "removedWords": {},
            "removedModels": [],
            "answerReplaced": None,
            "updated": False,
        }

        kept_models = []
        words_removed_count = 0

        for model in models:
            model_id = model.get("modelId", "unknown")
            old_words = [normalize_token(word) for word in model.get("rankedWords", [])]
            new_words = dedupe_preserve_order(word for word in old_words if word in canonical_words)

            removed_words = [word for word in old_words if word not in canonical_words]
            if removed_words:
                file_changes["removedWords"][model_id] = removed_words
                words_removed_count += len(removed_words)

            if not new_words:
                file_changes["removedModels"].append(model_id)
                report["summary"]["modelsRemoved"] += 1
                continue

            kept_models.append(
                {
                    "modelId": model.get("modelId", "").strip(),
                    "displayName": model.get("displayName", "").strip(),
                    "rankedWords": new_words,
                }
            )

        report["summary"]["wordsRemoved"] += words_removed_count

        if not kept_models:
            path.unlink()
            report["deletedFiles"].append(str(path))
            report["summary"]["filesDeleted"] += 1
            report["summary"]["filesProcessed"] += 1
            continue

        original_answer = normalize_token(data.get("answer", ""))
        answer_candidates = [model["rankedWords"][0] for model in kept_models if model["rankedWords"]]
        answer = original_answer if original_answer in canonical_words else ""
        if not answer:
            answer = answer_candidates[0]
            file_changes["answerReplaced"] = {
                "from": original_answer,
                "to": answer,
            }
            report["summary"]["answersReplaced"] += 1

        final_models = []
        for model in kept_models:
            words = model["rankedWords"]
            if answer not in words:
                file_changes["removedModels"].append(model["modelId"])
                report["summary"]["modelsRemoved"] += 1
                continue
            reordered = [answer] + [word for word in words if word != answer]
            final_models.append({**model, "rankedWords": reordered})

        if not final_models:
            path.unlink()
            report["deletedFiles"].append(str(path))
            report["summary"]["filesDeleted"] += 1
            report["summary"]["filesProcessed"] += 1
            continue

        updated_data = {
            "utcDate": data.get("utcDate", "").strip(),
            "answer": answer,
            "models": final_models,
        }

        old_payload = json.dumps(data, sort_keys=True, separators=(",", ":"))
        new_payload = json.dumps(updated_data, sort_keys=True, separators=(",", ":"))

        if old_payload != new_payload:
            path.write_text(json.dumps(updated_data, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
            file_changes["updated"] = True
            report["summary"]["filesUpdated"] += 1

        report["summary"]["filesProcessed"] += 1
        report["processedFiles"].append(file_changes)

    report_path = Path(args.report_out)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(f"Prune report: {report_path}")
    return 0


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Lexicon generation and puzzle pruning")
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate = subparsers.add_parser("generate", help="Generate canonical list and lemma map")
    generate.add_argument("--output-dir", default="content/lexicon")
    generate.add_argument("--target-count", type=int, default=20_000)
    generate.add_argument("--candidate-count", type=int, default=200_000)
    generate.set_defaults(func=generate_lexicon)

    prune = subparsers.add_parser("prune", help="Prune puzzle files against canonical word list")
    prune.add_argument("--puzzles-dir", default="content/puzzles")
    prune.add_argument("--canonical-words", default="content/lexicon/common_words_20k.txt")
    prune.add_argument("--report-out", default="content/lexicon/prune_report.json")
    prune.set_defaults(func=prune_puzzles)

    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
