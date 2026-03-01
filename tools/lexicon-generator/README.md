# Lexicon Generator

Builds the canonical 20k word list, lemma map, and prunes puzzle files.

## Install dependencies

```bash
python3 -m pip install -r tools/lexicon-generator/requirements.txt
```

## Generate canonical lexicon

```bash
python3 tools/lexicon-generator/lexicon_generator.py generate \
  --output-dir content/lexicon \
  --target-count 20000 \
  --candidate-count 200000
```

Outputs:

- `content/lexicon/common_words_20k.txt`
- `content/lexicon/lemma_map.json`
- `content/lexicon/lexicon_metadata.json`

Canonicalization behavior includes a past-tense rule: words ending in `ed` are canonicalized to a base verb form when available (for example, `abandoned -> abandon`), so inflected past-tense forms go into `lemma_map.json` instead of the canonical 20k list.

## Prune puzzle content to canonical list

```bash
python3 tools/lexicon-generator/lexicon_generator.py prune \
  --puzzles-dir content/puzzles \
  --canonical-words content/lexicon/common_words_20k.txt \
  --report-out content/lexicon/prune_report.json
```

Prune behavior:

- remove ranked words not in canonical list
- dedupe ranked words per model while preserving order
- remove empty models
- delete day file if no models remain
- if answer is removed, replace with first ranked word from first surviving model
- force `rankedWords[0] == answer` on all surviving models
- keep only models that contain the final answer

The command writes a machine-readable JSON report with removed words, removed models, deleted days, and answer replacements.

## Create or update word-of-day list

This creates/updates a file containing only the #1 word per day (no full puzzle content).

```bash
python3 tools/lexicon-generator/word_of_day_generator.py \
  --words-file content/lexicon/common_words_20k.txt \
  --output content/lexicon/word_of_day.json \
  --start-date 2026-04-01 \
  --days 90 \
  --seed 42
```

- By default, existing dates are preserved.
- Add `--overwrite-existing` to regenerate words for dates already present in the file.
