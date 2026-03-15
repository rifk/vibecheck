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
  --seed 42 \
  --common-bias 1.5
```

- By default, existing dates are preserved.
- Add `--overwrite-existing` to regenerate words for dates already present in the file.
- `--common-bias` slightly favors earlier (more common) words from `common_words_20k.txt` while still being random.
  - `1.0` means uniform random.
  - Values above `1.0` increase common-word preference.

## Build embedding artifacts

Three scripts generate normalized embedding artifacts for the canonical lexicon:

- `embedding_matrix_sentence_transformer.py` using `google/embeddinggemma-300m` (20k words)
- `embedding_matrix_sentence_transformer_alibaba.py` using `Alibaba-NLP/gte-Qwen2-1.5B-instruct` (50k words)
- `embedding_matrix_openai.py` using `text-embedding-3-large` (20k words)

Install dependencies:

```bash
python3 -m pip install -r tools/lexicon-generator/requirements.txt
```

### 1) SentenceTransformer embeddings (EmbeddingGemma 300M, 20k)

Build:

```bash
python3 tools/lexicon-generator/embedding_matrix_sentence_transformer.py build \
  --words-file content/lexicon/common_words_20k.txt \
  --output-dir content/lexicon/embeddings/sentence_transformer_embeddinggemma300m
```

Query:

```bash
python3 tools/lexicon-generator/embedding_matrix_sentence_transformer.py query \
  --output-dir content/lexicon/embeddings/sentence_transformer_embeddinggemma300m \
  --word apple \
  --top-k 25
```

### 2) SentenceTransformer embeddings (Alibaba GTE Qwen2 1.5B, 50k)

Build:

```bash
python3 tools/lexicon-generator/embedding_matrix_sentence_transformer_alibaba.py build \
  --words-file content/lexicon/common_words_50k.txt \
  --output-dir content/lexicon/embeddings/sentence_transformer_alibaba_gte-qwen2-1_5b-instruct
```

Query:

```bash
python3 tools/lexicon-generator/embedding_matrix_sentence_transformer_alibaba.py query \
  --output-dir content/lexicon/embeddings/sentence_transformer_alibaba_gte-qwen2-1_5b-instruct \
  --word apple \
  --top-k 25
```

### 3) OpenAI embeddings

Build:

```bash
export OPENAI_API_KEY="your-key"
python3 tools/lexicon-generator/embedding_matrix_openai.py build \
  --words-file content/lexicon/common_words_20k.txt \
  --output-dir content/lexicon/embeddings/openai_text_embedding_3_large
```

Query:

```bash
python3 tools/lexicon-generator/embedding_matrix_openai.py query \
  --output-dir content/lexicon/embeddings/openai_text_embedding_3_large \
  --word apple \
  --top-k 25
```

### Artifact layout (same schema for all scripts)

Each output directory contains:

- `embeddings.f32.memmap`: float32 row-major normalized embeddings (`N x D`)
- `words.json`: ordered `words` list and `wordToIndex` map
- `metadata.json`: provider/model/metric/dtype/shape/embedding dimension, normalization, storage info, and file paths

Stored vectors are normalized to unit length. Query distance is computed on demand as cosine distance:

- `distance = 1 - cosine_similarity`
- query output is sorted ascending by distance (includes the query word itself at rank 1)

### Useful dev options

All embedding build commands support:

- `--limit N` for tiny test runs
- `--batch-size` for embedding batch size
