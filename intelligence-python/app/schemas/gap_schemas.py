"""Pydantic request/response models for skill gap analysis (Phase 5).

Unlike the Phase 4 matching schemas, these keep the ``{success, data, error}``
envelope: gap analysis is an LLM reasoning task like JD extraction, so it has the
same partial-failure shape (the model answered, but not usefully) that the
envelope exists to carry.
"""

import re
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator

#: The four priorities the Java ``GapPriority`` enum accepts. Anything else the
#: model invents is coerced rather than rejected — see the validator below.
ALLOWED_PRIORITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW")

#: Fallback for an unrecognised priority. MEDIUM rather than LOW or CRITICAL: a
#: gap the model could not classify should neither be dismissed nor treated as
#: disqualifying.
DEFAULT_PRIORITY = "MEDIUM"


def _clean_string_list(value):
    """Tolerate a null list and drop null / blank entries inside one."""
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if item is not None and str(item).strip()]
    return value


# --- match-tied gap analysis ---


class GapAnalysisRequest(BaseModel):
    job_title: str
    company_name: Optional[str] = None
    missing_skills: List[str] = Field(default_factory=list)
    missing_must_haves: List[str] = Field(default_factory=list)
    preferred_missing: List[str] = Field(default_factory=list)
    resume_text_summary: str = ""
    overall_score: float = 0.0

    @field_validator("missing_skills", "missing_must_haves", "preferred_missing", mode="before")
    @classmethod
    def _drop_blank_entries(cls, value):
        return _clean_string_list(value)


class GapItem(BaseModel):
    skill: str
    priority: str = DEFAULT_PRIORITY
    reason: str = ""
    learning_recommendation: str = ""
    estimated_weeks: Optional[int] = None

    @field_validator("priority", mode="before")
    @classmethod
    def _coerce_priority(cls, value):
        """Force the priority onto the Java enum, or fall back to MEDIUM.

        Small models write "Critical", "very high", or "P1" as readily as the
        four values asked for. Uppercasing recovers the common cases; anything
        still unrecognised becomes MEDIUM, because a bad string would otherwise
        fail enum binding at the Java boundary where the context to diagnose it
        is gone. Dropping the gap entirely would be worse — the skill is real
        even when its label is not.
        """
        if value is None:
            return DEFAULT_PRIORITY
        normalised = str(value).strip().upper()
        return normalised if normalised in ALLOWED_PRIORITIES else DEFAULT_PRIORITY

    @field_validator("reason", "learning_recommendation", mode="before")
    @classmethod
    def _null_prose_to_empty(cls, value):
        """A defaulted field only fills an *absent* key; the model emits nulls."""
        return "" if value is None else value

    @field_validator("estimated_weeks", mode="before")
    @classmethod
    def _coerce_weeks(cls, value):
        """Accept "4", "2-3 weeks", or "about 6" — anything else becomes null.

        The model is asked for an integer and frequently answers with a range or
        a phrase. The low end of a range is the useful reading: it is the point
        at which the candidate can start claiming progress.
        """
        if value is None or isinstance(value, int):
            return value
        match = re.search(r"\d+", str(value))
        return int(match.group()) if match else None


class GapAnalysisResult(BaseModel):
    gap_summary: str = ""
    gaps: List[GapItem] = Field(default_factory=list)
    quick_wins: List[str] = Field(default_factory=list)
    deal_breakers: List[str] = Field(default_factory=list)

    @field_validator("gap_summary", mode="before")
    @classmethod
    def _null_summary_to_empty(cls, value):
        return "" if value is None else value

    @field_validator("quick_wins", "deal_breakers", mode="before")
    @classmethod
    def _drop_blank_entries(cls, value):
        return _clean_string_list(value)

    @field_validator("gaps", mode="before")
    @classmethod
    def _drop_null_gaps(cls, value):
        if value is None:
            return []
        if isinstance(value, list):
            return [item for item in value if item is not None]
        return value


class GapAnalysisResponse(BaseModel):
    success: bool
    data: Optional[GapAnalysisResult] = None
    error: Optional[str] = None


# --- standalone (resume-only) gap analysis ---


class StandaloneGapRequest(BaseModel):
    resume_text: str


class StandaloneGapResult(BaseModel):
    strong_domains: List[str] = Field(default_factory=list)
    weak_domains: List[str] = Field(default_factory=list)
    recommended_additions: List[str] = Field(default_factory=list)
    general_assessment: str = ""

    @field_validator(
        "strong_domains", "weak_domains", "recommended_additions", mode="before"
    )
    @classmethod
    def _drop_blank_entries(cls, value):
        return _clean_string_list(value)

    @field_validator("general_assessment", mode="before")
    @classmethod
    def _null_assessment_to_empty(cls, value):
        return "" if value is None else value


class StandaloneGapResponse(BaseModel):
    success: bool
    data: Optional[StandaloneGapResult] = None
    error: Optional[str] = None
