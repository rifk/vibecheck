# Vibe Check Puzzle Schema

Each puzzle day is stored as one JSON file in `content/puzzles` named `{utcDate}.json`.

## Shape

```json
{
  "utcDate": "2026-01-01",
  "answer": "serenity",
  "models": [
    {
      "modelId": "model_1",
      "displayName": "Model 1",
      "rankedWords": ["serenity", "calm", "peace"]
    }
  ]
}
```

## Required rules

- `models` must exist and contain at least 1 model.
- `modelId` and `displayName` must be non-empty.
- `rankedWords` must be non-empty.
- `rankedWords[0]` must equal `answer`.
- words must be canonical lowercase alphabetic tokens (`^[a-z]{2,}$`).
- `answer` and all `rankedWords` entries must exist in `content/lexicon/common_words_20k.txt`.
- duplicate words in one model list are not allowed.
- v1 bundle must contain exactly 90 JSON files covering a continuous UTC window.
- current v1 window is `2026-01-01` through `2026-03-31`.

## Runtime semantics

- the app renders any number of models per day dynamically.
- solving one model solves the day and locks all other models for that day.
- user guess canonicalization order:
  - check exact day-list word first;
  - if not present, check `lemma_map.json` for a canonical form;
  - reject if neither path yields a valid ranked word.
