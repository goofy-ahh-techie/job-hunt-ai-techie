"""Unit tests for SemanticSimilarityService — embeddings are faked, no real Ollama.

Vectors are hand-built unit-ish vectors in a tiny space so the expected cosine
similarities are obvious by inspection: identical vectors score 1.0, orthogonal
ones score 0.0.
"""

import pytest

from app.exceptions import OllamaUnavailableError
from app.services.semantic_similarity_service import SemanticSimilarityService

# A 3-dimensional "embedding space". Anything mapped to the same axis as a
# target text matches perfectly; anything on a different axis is orthogonal.
X_AXIS = [1.0, 0.0, 0.0]
Y_AXIS = [0.0, 1.0, 0.0]
Z_AXIS = [0.0, 0.0, 1.0]


class FakeOllamaClient:
    """Stands in for OllamaClient: maps text → vector, counting calls."""

    def __init__(self, vectors=None, error=None):
        self._vectors = vectors or {}
        self._error = error
        self.calls = []

    async def embed(self, text: str):
        if self._error is not None:
            raise self._error
        self.calls.append(text)
        # Unknown text is orthogonal to everything mapped, so it never matches.
        return self._vectors.get(text, Z_AXIS)


async def test_partial_match_reports_half():
    client = FakeOllamaClient(
        vectors={
            "Kubernetes": X_AXIS,        # same axis as the resume chunk -> 1.0
            "Rust": Y_AXIS,              # orthogonal to it -> 0.0
            "Deployed services on Kubernetes clusters": X_AXIS,
        }
    )
    service = SemanticSimilarityService(client=client)

    response = await service.compare(
        source_phrases=["Kubernetes", "Rust"],
        target_texts=["Deployed services on Kubernetes clusters"],
        threshold=0.65,
    )

    assert response.match_count == 1
    assert response.match_percentage == 50.0

    matched, unmatched = response.results
    assert matched.phrase == "Kubernetes"
    assert matched.matched is True
    assert matched.best_score == pytest.approx(1.0)
    assert matched.best_match_excerpt == "Deployed services on Kubernetes clusters"

    assert unmatched.phrase == "Rust"
    assert unmatched.matched is False
    assert unmatched.best_score == pytest.approx(0.0)


async def test_all_phrases_match_reports_hundred_percent():
    client = FakeOllamaClient(
        vectors={"Java": X_AXIS, "Spring Boot": X_AXIS, "Built Java Spring services": X_AXIS}
    )
    service = SemanticSimilarityService(client=client)

    response = await service.compare(
        source_phrases=["Java", "Spring Boot"],
        target_texts=["Built Java Spring services"],
        threshold=0.65,
    )

    assert response.match_count == 2
    assert response.match_percentage == 100.0
    assert all(result.matched for result in response.results)


async def test_no_phrases_match_reports_zero():
    client = FakeOllamaClient(
        vectors={"Kubernetes": X_AXIS, "Terraform": X_AXIS, "Wrote frontend React components": Y_AXIS}
    )
    service = SemanticSimilarityService(client=client)

    response = await service.compare(
        source_phrases=["Kubernetes", "Terraform"],
        target_texts=["Wrote frontend React components"],
        threshold=0.65,
    )

    assert response.match_count == 0
    assert response.match_percentage == 0.0
    # A result row is still returned per phrase — the caller needs the misses to
    # report the gap, not just the count.
    assert len(response.results) == 2
    assert all(not result.matched for result in response.results)


async def test_ollama_unavailable_propagates():
    service = SemanticSimilarityService(
        client=FakeOllamaClient(error=OllamaUnavailableError("runtime down"))
    )

    with pytest.raises(OllamaUnavailableError):
        await service.compare(
            source_phrases=["Java"], target_texts=["Built Java services"], threshold=0.65
        )


async def test_target_embeddings_computed_once_per_distinct_text():
    """N phrases x M targets must cost N+M embed calls, not N*M."""
    client = FakeOllamaClient(
        vectors={
            "Java": X_AXIS,
            "Kafka": Y_AXIS,
            "Terraform": Z_AXIS,
            "Built Java services": X_AXIS,
            "Ran Kafka pipelines": Y_AXIS,
        }
    )
    service = SemanticSimilarityService(client=client)

    await service.compare(
        source_phrases=["Java", "Kafka", "Terraform"],
        # The duplicate target must not be embedded twice either.
        target_texts=["Built Java services", "Ran Kafka pipelines", "Built Java services"],
        threshold=0.65,
    )

    # 3 phrases + 2 distinct targets = 5. The naive pairwise version would be 6
    # comparisons' worth of calls and would grow multiplicatively from there.
    assert len(client.calls) == 5
    assert sorted(client.calls) == sorted(set(client.calls))


async def test_empty_phrase_list_does_not_divide_by_zero():
    client = FakeOllamaClient()
    service = SemanticSimilarityService(client=client)

    response = await service.compare(
        source_phrases=[], target_texts=["Built Java services"], threshold=0.65
    )

    assert response.results == []
    assert response.match_count == 0
    assert response.match_percentage == 0.0
    # Nothing to compare means nothing to embed.
    assert client.calls == []
