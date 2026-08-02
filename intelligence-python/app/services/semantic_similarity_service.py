"""Embedding-based semantic similarity between JD phrases and resume text.

The matching engine's keyword pass answers "does this exact string appear?".
This service answers the question keywords cannot: does the resume *say the same
thing* in different words — "Kubernetes" against "container orchestration",
"mentor engineers" against "led and coached a team of five".

Cost shape drives the design. Ollama's embeddings endpoint takes one text per
call, so a naive implementation comparing N phrases against M texts would issue
N×M calls. Embeddings are vectors, though, not pairwise judgements: embedding
each side *once* (N+M calls) and comparing the vectors in numpy gives identical
answers. For 15 skills against 20 chunks that is 35 calls instead of 300.
"""

import asyncio
import logging
from typing import Dict, Iterable, List, Sequence

import numpy as np

from app.clients.ollama_client import OllamaClient
from app.config import settings
from app.schemas.match_schemas import PhraseMatchResult, SemanticSimilarityResponse

logger = logging.getLogger(__name__)

#: Characters of the best-matching target text returned as evidence.
EXCERPT_LENGTH = 120

#: Ollama serialises inference anyway, but a handful of in-flight requests keeps
#: the HTTP round-trips overlapped without burying the runtime in a queue of
#: hundreds when a resume has many chunks.
MAX_CONCURRENT_EMBEDDINGS = 4


class SemanticSimilarityService:
    """Scores how well each source phrase is covered by any target text."""

    def __init__(self, client: OllamaClient | None = None):
        self.client = client or OllamaClient(
            timeout_seconds=settings.ollama_embedding_timeout_seconds
        )

    async def compare(
        self,
        source_phrases: Sequence[str],
        target_texts: Sequence[str],
        threshold: float,
    ) -> SemanticSimilarityResponse:
        """Compare every phrase against every target text.

        Raises:
            OllamaUnavailableError: propagated from the client untouched — the
                caller (and ultimately the Java scorer) degrades to keyword-only
                matching rather than failing the whole match.
        """
        if not source_phrases:
            # Nothing to look for. Zero of zero is not a failure, and dividing by
            # len(source_phrases) below would be.
            return SemanticSimilarityResponse(
                results=[], match_count=0, match_percentage=0.0
            )

        if not target_texts:
            # Nothing to look in: every phrase is genuinely unmatched.
            logger.debug("No target texts supplied; all %d phrases unmatched",
                         len(source_phrases))
            return self._build_response(
                [
                    PhraseMatchResult(
                        phrase=phrase, matched=False, best_score=0.0, best_match_excerpt=""
                    )
                    for phrase in source_phrases
                ]
            )

        # One embedding per *distinct* string across both sides. Identical texts
        # (a skill repeated in two JD sections, say) cost one call, not two.
        embeddings = await self._embed_all({*source_phrases, *target_texts})

        target_matrix, usable_targets = self._stack(target_texts, embeddings)
        if target_matrix is None:
            logger.warning("No usable target embeddings; treating all phrases as unmatched")
            return self._build_response(
                [
                    PhraseMatchResult(
                        phrase=phrase, matched=False, best_score=0.0, best_match_excerpt=""
                    )
                    for phrase in source_phrases
                ]
            )

        results: List[PhraseMatchResult] = []
        for phrase in source_phrases:
            vector = embeddings.get(phrase)
            if vector is None or len(vector) != target_matrix.shape[1]:
                # Dimension mismatch means the vectors came from different
                # models — comparing them would produce a meaningless number,
                # so report "not matched" rather than a fabricated score.
                results.append(
                    PhraseMatchResult(
                        phrase=phrase, matched=False, best_score=0.0, best_match_excerpt=""
                    )
                )
                continue

            scores = target_matrix @ self._normalise(np.asarray(vector, dtype=np.float64))
            best_index = int(np.argmax(scores))
            best_score = float(scores[best_index])
            results.append(
                PhraseMatchResult(
                    phrase=phrase,
                    matched=best_score >= threshold,
                    best_score=round(best_score, 4),
                    best_match_excerpt=usable_targets[best_index][:EXCERPT_LENGTH],
                )
            )

        response = self._build_response(results)
        logger.debug(
            "Semantic comparison complete: phrases=%d targets=%d threshold=%.2f matched=%d",
            len(source_phrases),
            len(target_texts),
            threshold,
            response.match_count,
        )
        return response

    async def _embed_all(self, texts: Iterable[str]) -> Dict[str, List[float]]:
        """Embed each distinct text once, a few calls in flight at a time."""
        distinct = sorted({text for text in texts if text})
        semaphore = asyncio.Semaphore(MAX_CONCURRENT_EMBEDDINGS)

        async def embed_one(text: str) -> List[float]:
            async with semaphore:
                return await self.client.embed(text)

        vectors = await asyncio.gather(*(embed_one(text) for text in distinct))
        return dict(zip(distinct, vectors))

    def _stack(
        self, target_texts: Sequence[str], embeddings: Dict[str, List[float]]
    ) -> tuple[np.ndarray | None, List[str]]:
        """Build the L2-normalised target matrix, dropping unusable vectors.

        Pre-normalising every row turns cosine similarity into a plain dot
        product, so each phrase costs one matrix-vector multiply.
        """
        rows: List[np.ndarray] = []
        kept: List[str] = []
        dimension: int | None = None

        for text in target_texts:
            vector = embeddings.get(text)
            if not vector:
                continue
            if dimension is None:
                dimension = len(vector)
            elif len(vector) != dimension:
                continue
            rows.append(self._normalise(np.asarray(vector, dtype=np.float64)))
            kept.append(text)

        if not rows:
            return None, []
        return np.vstack(rows), kept

    @staticmethod
    def _normalise(vector: np.ndarray) -> np.ndarray:
        """Scale to unit length; a zero vector is returned unchanged.

        Guarding the zero case keeps a degenerate embedding from producing NaN
        similarities, which would silently poison argmax.
        """
        norm = float(np.linalg.norm(vector))
        return vector if norm == 0.0 else vector / norm

    @staticmethod
    def _build_response(results: List[PhraseMatchResult]) -> SemanticSimilarityResponse:
        match_count = sum(1 for result in results if result.matched)
        percentage = (match_count / len(results) * 100) if results else 0.0
        return SemanticSimilarityResponse(
            results=results,
            match_count=match_count,
            match_percentage=round(percentage, 2),
        )
