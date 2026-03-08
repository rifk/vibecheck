#!/usr/bin/env python3
"""Unit tests for embedding artifact helpers."""

from __future__ import annotations

import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

from embedding_store import (  # noqa: E402
    EMBEDDINGS_FILE,
    METADATA_FILE,
    VECTOR_NORMALIZATION,
    WORDS_FILE,
    load_words,
    normalize_rows,
    query_neighbors,
    write_embeddings_memmap,
    write_metadata_file,
    write_words_file,
)


class EmbeddingStoreTest(unittest.TestCase):
    def test_load_words_normalizes_dedupes_and_limits(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            words_path = Path(tmpdir) / "words.txt"
            words_path.write_text(" Apple \npear\npear\nBANANA\nx1\nA\n", encoding="utf-8")

            self.assertEqual(load_words(words_path, limit=None), ["apple", "pear", "banana"])
            self.assertEqual(load_words(words_path, limit=2), ["apple", "pear"])

    def test_normalize_rows_handles_zero_vectors(self) -> None:
        vectors = np.asarray([[3.0, 4.0], [0.0, 0.0]], dtype=np.float32)
        normalized = normalize_rows(vectors)

        self.assertAlmostEqual(float(np.linalg.norm(normalized[0])), 1.0, places=6)
        np.testing.assert_allclose(normalized[1], np.asarray([0.0, 0.0], dtype=np.float32))

    def test_writes_embeddings_and_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(np.asarray([[1.0, 0.0], [0.0, 1.0]], dtype=np.float32))

            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha", "beta"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=2,
                embedding_dim=2,
            )

            mmap = np.memmap(output_dir / EMBEDDINGS_FILE, mode="r", dtype=np.float32, shape=(2, 2))
            np.testing.assert_allclose(np.asarray(mmap), embeddings)

            metadata = json.loads((output_dir / METADATA_FILE).read_text(encoding="utf-8"))
            self.assertEqual(metadata["shape"], [2, 2])
            self.assertEqual(metadata["embeddingDimension"], 2)
            self.assertEqual(metadata["vectorNormalization"], VECTOR_NORMALIZATION)
            self.assertEqual(metadata["files"]["embeddings"], str(output_dir / EMBEDDINGS_FILE))

    def test_query_neighbors_returns_sorted_distances(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(
                np.asarray(
                    [
                        [1.0, 0.0],
                        [0.0, 1.0],
                        [1.0, 1.0],
                    ],
                    dtype=np.float32,
                )
            )
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha", "beta", "gamma"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=3,
                embedding_dim=2,
            )

            rows = query_neighbors(output_dir, "alpha", top_k=3)

            self.assertEqual([word for word, _ in rows], ["alpha", "gamma", "beta"])
            self.assertAlmostEqual(rows[0][1], 0.0, places=6)
            self.assertAlmostEqual(rows[1][1], 1.0 - (1.0 / math.sqrt(2.0)), places=6)
            self.assertAlmostEqual(rows[2][1], 1.0, places=6)

    def test_query_neighbors_preserves_stable_tie_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(
                np.asarray(
                    [
                        [1.0, 0.0],
                        [0.0, 1.0],
                        [0.0, -1.0],
                    ],
                    dtype=np.float32,
                )
            )
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha", "beta", "gamma"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=3,
                embedding_dim=2,
            )

            rows = query_neighbors(output_dir, "alpha", top_k=3)
            self.assertEqual([word for word, _ in rows], ["alpha", "beta", "gamma"])

    def test_query_neighbors_rejects_missing_word(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(np.asarray([[1.0, 0.0]], dtype=np.float32))
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=1,
                embedding_dim=2,
            )

            with self.assertRaises(SystemExit) as ctx:
                query_neighbors(output_dir, "beta", top_k=1)

            self.assertEqual(str(ctx.exception), "Word 'beta' not found in index.")

    def test_query_neighbors_rejects_shape_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(np.asarray([[1.0, 0.0], [0.0, 1.0]], dtype=np.float32))
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha", "beta", "gamma"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=2,
                embedding_dim=2,
            )

            with self.assertRaises(SystemExit) as ctx:
                query_neighbors(output_dir, "alpha", top_k=1)

            self.assertEqual(str(ctx.exception), "metadata shape does not match words index length")

    def test_query_neighbors_rejects_unsupported_normalization(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(np.asarray([[1.0, 0.0]], dtype=np.float32))
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha"])
            write_metadata_file(
                output_dir / METADATA_FILE,
                output_dir=output_dir,
                provider="test",
                model_id="demo",
                n_words=1,
                embedding_dim=2,
            )
            metadata = json.loads((output_dir / METADATA_FILE).read_text(encoding="utf-8"))
            metadata["vectorNormalization"] = "raw"
            (output_dir / METADATA_FILE).write_text(
                json.dumps(metadata, ensure_ascii=True, indent=2) + "\n",
                encoding="utf-8",
            )

            with self.assertRaises(SystemExit) as ctx:
                query_neighbors(output_dir, "alpha", top_k=1)

            self.assertEqual(str(ctx.exception), "Only unit_length normalized embeddings are supported")

    def test_load_metadata_rejects_malformed_payload(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            output_dir = Path(tmpdir)
            embeddings = normalize_rows(np.asarray([[1.0, 0.0]], dtype=np.float32))
            write_embeddings_memmap(embeddings, output_dir / EMBEDDINGS_FILE)
            write_words_file(output_dir / WORDS_FILE, ["alpha"])
            (output_dir / METADATA_FILE).write_text("[]\n", encoding="utf-8")

            with self.assertRaises(SystemExit) as ctx:
                query_neighbors(output_dir, "alpha", top_k=1)

            self.assertIn("Invalid metadata payload", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
