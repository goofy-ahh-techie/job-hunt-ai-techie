"""Pydantic request/response models for semantic matching (Phase 4).

Unlike the JD extraction contract, these are *not* wrapped in a
``{success, data, error}`` envelope: semantic similarity either produces a full
result set or fails with a status code, so there is no partial-success shape for
an envelope to carry. The Java ``MatchingIntelligenceClient`` binds directly to
:class:`SemanticSimilarityResponse`.
"""

from typing import List

from pydantic import BaseModel, Field, field_validator

from app.config import settings


class SemanticSimilarityRequest(BaseModel):
    #: JD-side items to look for — required skills, responsibilities.
    source_phrases: List[str] = Field(default_factory=list)
    #: Resume-side text to look in — chunk contents.
    target_texts: List[str] = Field(default_factory=list)
    #: Minimum cosine similarity for a phrase to count as present.
    threshold: float = Field(
        default=settings.semantic_default_threshold, ge=0.0, le=1.0
    )

    @field_validator("source_phrases", "target_texts", mode="before")
    @classmethod
    def _drop_blank_entries(cls, value):
        """Tolerate nulls and drop null / whitespace-only entries.

        A blank phrase has no meaningful embedding, and letting one through
        would drag ``match_percentage`` down with a row that can never match.
        """
        if value is None:
            return []
        if isinstance(value, list):
            return [str(v).strip() for v in value if v is not None and str(v).strip()]
        return value


class PhraseMatchResult(BaseModel):
    phrase: str
    matched: bool
    best_score: float
    #: First 120 chars of the best-matching target text — enough for a caller to
    #: show *why* a phrase was considered present without shipping whole chunks.
    best_match_excerpt: str


class SemanticSimilarityResponse(BaseModel):
    results: List[PhraseMatchResult]
    match_count: int
    match_percentage: float
