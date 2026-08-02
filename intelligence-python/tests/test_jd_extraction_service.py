"""Unit tests for JdExtractionService — the LLM client is faked, no real Ollama call."""

import json

import pytest

from app.exceptions import (
    ExtractionParseError,
    ExtractionValidationError,
    OllamaUnavailableError,
)
from app.schemas.jd_schemas import JdExtractionResult
from app.services.jd_extraction_service import JdExtractionService

VALID_EXTRACTION = {
    "job_title": "Senior Java Engineer",
    "company_name": "Acme",
    "company_description": None,
    "location": "Remote",
    "employment_type": "FULL_TIME",
    "experience_years_min": 5,
    "experience_years_max": 9,
    "responsibilities": ["Build APIs", "Mentor engineers"],
    "required_skills": ["Java", "Spring Boot"],
    "preferred_skills": ["Kafka"],
    "qualifications": ["BSc Computer Science"],
    "must_have": ["5+ years Java"],
    "nice_to_have": ["Open source contributions"],
    "benefits": ["Health insurance"],
    "raw_summary": "A senior backend role building distributed services.",
}


class FakeOllamaClient:
    """Stands in for OllamaClient: returns a canned reply or raises."""

    def __init__(self, reply=None, error=None):
        self._reply = reply
        self._error = error

    async def generate(self, prompt: str) -> str:
        if self._error is not None:
            raise self._error
        return self._reply


async def test_valid_response_returns_mapped_result():
    service = JdExtractionService(client=FakeOllamaClient(reply=json.dumps(VALID_EXTRACTION)))

    result = await service.extract("a" * 80)

    assert isinstance(result, JdExtractionResult)
    assert result.job_title == "Senior Java Engineer"
    assert result.employment_type == "FULL_TIME"
    assert result.experience_years_min == 5
    assert result.experience_years_max == 9
    assert result.required_skills == ["Java", "Spring Boot"]
    assert result.must_have == ["5+ years Java"]
    assert result.responsibilities == ["Build APIs", "Mentor engineers"]


async def test_malformed_json_raises_parse_error():
    service = JdExtractionService(client=FakeOllamaClient(reply="{ this is not valid json "))

    with pytest.raises(ExtractionParseError) as exc_info:
        await service.extract("a" * 80)

    # The raw reply is attached for debugging.
    assert exc_info.value.raw_response == "{ this is not valid json "


async def test_missing_required_field_raises_validation_error():
    incomplete = dict(VALID_EXTRACTION)
    incomplete.pop("must_have")
    service = JdExtractionService(client=FakeOllamaClient(reply=json.dumps(incomplete)))

    with pytest.raises(ExtractionValidationError):
        await service.extract("a" * 80)


async def test_ollama_unavailable_propagates():
    service = JdExtractionService(
        client=FakeOllamaClient(error=OllamaUnavailableError("runtime down"))
    )

    with pytest.raises(OllamaUnavailableError):
        await service.extract("a" * 80)
