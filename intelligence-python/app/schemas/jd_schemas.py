"""Pydantic request/response models for JD extraction."""

from typing import List, Optional

from pydantic import BaseModel, Field, field_validator

#: The employment types the Java backend's ``EmploymentType`` enum accepts.
#: Anything the model invents outside this set is nulled rather than passed on —
#: an unmapped string would fail enum binding downstream.
ALLOWED_EMPLOYMENT_TYPES = {"FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"}


class JdExtractionRequest(BaseModel):
    raw_text: str = Field(min_length=50)


class JdExtractionResult(BaseModel):
    # Required. These four are the contract: an extraction missing any of them
    # is not usable, so Pydantic rejects it and the service reports a
    # validation failure rather than persisting a hollow record.
    job_title: str
    responsibilities: List[str]
    required_skills: List[str]
    must_have: List[str]

    # Optional. A job description legitimately may not state these, so a missing
    # key is tolerated and normalised to null / empty rather than failing the
    # whole extraction.
    company_name: Optional[str] = None
    company_description: Optional[str] = None
    location: Optional[str] = None
    employment_type: Optional[str] = None
    experience_years_min: Optional[int] = None
    experience_years_max: Optional[int] = None
    preferred_skills: List[str] = Field(default_factory=list)
    qualifications: List[str] = Field(default_factory=list)
    nice_to_have: List[str] = Field(default_factory=list)
    benefits: List[str] = Field(default_factory=list)
    raw_summary: str = ""

    @field_validator("raw_summary", mode="before")
    @classmethod
    def _null_summary_to_empty(cls, value):
        """Coerce an explicit ``"raw_summary": null`` to an empty string.

        ``raw_summary`` is a defaulted field, but a default only fills an
        *absent* key. The model frequently emits the key with a null value for
        a terse JD, which would otherwise fail the ``str`` type. This is prose
        we can live without, so an empty string is the right degradation rather
        than failing the whole extraction.
        """
        return "" if value is None else value

    @field_validator("employment_type", mode="before")
    @classmethod
    def _normalise_employment_type(cls, value):
        """Coerce model phrasing onto the enum, or null it out.

        Small models write "Full-time" or "full time" as readily as "FULL_TIME".
        Uppercasing and swapping separators recovers those; anything still
        unrecognised becomes None, because a bad string would only fail later
        at the Java enum boundary where the context to diagnose it is gone.
        """
        if value is None:
            return None
        normalised = str(value).strip().upper().replace("-", "_").replace(" ", "_")
        return normalised if normalised in ALLOWED_EMPLOYMENT_TYPES else None

    @field_validator(
        "responsibilities",
        "required_skills",
        "must_have",
        "preferred_skills",
        "qualifications",
        "nice_to_have",
        "benefits",
        mode="before",
    )
    @classmethod
    def _drop_null_list_entries(cls, value):
        """Tolerate a null list and drop null / blank entries inside one."""
        if value is None:
            return []
        if isinstance(value, list):
            return [item for item in value if item is not None and str(item).strip()]
        return value


class JdExtractionResponse(BaseModel):
    success: bool
    data: Optional[JdExtractionResult] = None
    error: Optional[str] = None
