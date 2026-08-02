"""Unit tests for GapAnalysisService — the LLM client is faked, no real Ollama call."""

import json

import pytest

from app.exceptions import (
    ExtractionParseError,
    ExtractionValidationError,
    OllamaUnavailableError,
)
from app.schemas.gap_schemas import GapAnalysisRequest, StandaloneGapRequest
from app.services.gap_analysis_service import GapAnalysisService

REQUEST = GapAnalysisRequest(
    job_title="Senior Backend Engineer",
    company_name="Fintech Global",
    missing_skills=["Terraform"],
    missing_must_haves=["Hands-on experience with Kubernetes in production"],
    preferred_missing=["GraphQL"],
    resume_text_summary="Java and Spring Boot services on AWS with Docker.",
    overall_score=74.5,
)

# Deliberately out of priority order: the service must sort, not the model.
VALID_ANALYSIS = {
    "gap_summary": "Strong backend foundation; container orchestration is the main gap.",
    "gaps": [
        {
            "skill": "GraphQL",
            "priority": "MEDIUM",
            "reason": "Listed as preferred for this role.",
            "learning_recommendation": "Build a small GraphQL layer over your existing REST API.",
            "estimated_weeks": 1,
        },
        {
            "skill": "Hands-on experience with Kubernetes in production",
            "priority": "CRITICAL",
            "reason": "Named as a must-have for this Fintech Global role.",
            "learning_recommendation": "Learn pod scheduling and Helm charts; your Docker work transfers.",
            "estimated_weeks": 6,
        },
        {
            "skill": "Terraform",
            "priority": "HIGH",
            "reason": "Required for the platform team's infrastructure work.",
            "learning_recommendation": "Codify your existing AWS setup as Terraform modules.",
            "estimated_weeks": 3,
        },
    ],
    "quick_wins": ["GraphQL"],
    "deal_breakers": ["Hands-on experience with Kubernetes in production"],
}

VALID_STANDALONE = {
    "strong_domains": ["backend API development", "cloud infrastructure"],
    "weak_domains": ["data engineering", "frontend development"],
    "recommended_additions": ["Kafka Streams", "Terraform", "GraphQL", "Spark", "React"],
    "general_assessment": "A solid backend engineer with production cloud exposure.",
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


def service_returning(payload) -> GapAnalysisService:
    reply = payload if isinstance(payload, str) else json.dumps(payload)
    return GapAnalysisService(client=FakeOllamaClient(reply=reply))


async def test_gaps_are_sorted_critical_first():
    result = await service_returning(VALID_ANALYSIS).analyze(REQUEST)

    assert [gap.priority for gap in result.gaps] == ["CRITICAL", "HIGH", "MEDIUM"]
    assert result.gaps[0].skill == "Hands-on experience with Kubernetes in production"
    assert result.deal_breakers == ["Hands-on experience with Kubernetes in production"]
    assert result.quick_wins == ["GraphQL"]
    assert result.gap_summary.startswith("Strong backend foundation")


async def test_learning_recommendations_survive_intact():
    result = await service_returning(VALID_ANALYSIS).analyze(REQUEST)

    critical = result.gaps[0]
    assert "Helm" in critical.learning_recommendation
    assert critical.estimated_weeks == 6
    assert critical.reason


async def test_priority_is_corrected_from_the_source_list_not_trusted():
    # Measured on the first live run: the model promoted Terraform (a missing
    # *skill*) to CRITICAL. Priority is provenance, so the code overrules it.
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    for gap in payload["gaps"]:
        gap["priority"] = "CRITICAL"

    result = await service_returning(payload).analyze(REQUEST)

    by_skill = {gap.skill: gap.priority for gap in result.gaps}
    assert by_skill["Hands-on experience with Kubernetes in production"] == "CRITICAL"
    assert by_skill["Terraform"] == "HIGH"
    assert by_skill["GraphQL"] == "MEDIUM"


async def test_abbreviated_skill_name_still_maps_to_its_source_list():
    # The model routinely shortens a sentence-shaped must-have to its noun.
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["gaps"][1]["skill"] = "Kubernetes"

    result = await service_returning(payload).analyze(REQUEST)

    kubernetes = next(gap for gap in result.gaps if gap.skill == "Kubernetes")
    assert kubernetes.priority == "CRITICAL"


async def test_inferred_gap_not_in_any_input_list_is_pinned_to_low():
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["gaps"].append({
        "skill": "Technical writing",
        "priority": "HIGH",
        "reason": "Inferred from the role's collaboration expectations.",
        "learning_recommendation": "Write design docs for your current services.",
        "estimated_weeks": 2,
    })

    result = await service_returning(payload).analyze(REQUEST)

    inferred = next(gap for gap in result.gaps if gap.skill == "Technical writing")
    assert inferred.priority == "LOW"


async def test_dropped_input_gap_is_restored():
    # A gap the model omits would silently vanish from the candidate's to-do list.
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["gaps"] = [g for g in payload["gaps"] if g["skill"] != "Terraform"]

    result = await service_returning(payload).analyze(REQUEST)

    restored = next(gap for gap in result.gaps if gap.skill == "Terraform")
    assert restored.priority == "HIGH"
    assert restored.estimated_weeks is None
    # No invented rationale — it says plainly that guidance was not generated.
    assert "No detailed guidance" in restored.learning_recommendation


async def test_quick_wins_are_derived_from_estimates_not_the_model():
    # Measured live: the model returned quick_wins=[] while estimating a gap at
    # one week, so its own two answers disagreed.
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["quick_wins"] = []
    payload["gaps"][0]["estimated_weeks"] = 1

    result = await service_returning(payload).analyze(REQUEST)

    assert result.quick_wins == ["GraphQL"]


async def test_quick_wins_exclude_anything_over_a_week():
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["quick_wins"] = ["Terraform", "Hands-on experience with Kubernetes in production"]

    result = await service_returning(payload).analyze(REQUEST)

    # Only the 1-week GraphQL gap qualifies; the 3- and 6-week ones are dropped.
    assert result.quick_wins == ["GraphQL"]


async def test_deal_breakers_cannot_include_a_non_critical_gap():
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["deal_breakers"] = ["GraphQL", "Hands-on experience with Kubernetes in production"]

    result = await service_returning(payload).analyze(REQUEST)

    # GraphQL is a preferred skill — it cannot be disqualifying by definition.
    assert result.deal_breakers == ["Hands-on experience with Kubernetes in production"]


async def test_unexpected_priority_is_coerced_to_medium():
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["gaps"][0]["priority"] = "SUPER URGENT"

    result = await service_returning(payload).analyze(REQUEST)

    # The gap is kept — the skill is real even when the model's label is not.
    coerced = next(gap for gap in result.gaps if gap.skill == "GraphQL")
    assert coerced.priority == "MEDIUM"
    assert len(result.gaps) == 3


async def test_estimated_weeks_accepts_a_range_and_takes_the_low_end():
    payload = json.loads(json.dumps(VALID_ANALYSIS))
    payload["gaps"][2]["estimated_weeks"] = "3-5 weeks"

    result = await service_returning(payload).analyze(REQUEST)

    terraform = next(gap for gap in result.gaps if gap.skill == "Terraform")
    assert terraform.estimated_weeks == 3


async def test_empty_gaps_for_non_empty_input_raises_validation_error():
    # The model was handed three gaps and returned none — a refusal to do the
    # task, which must never be persisted as "no gaps found".
    payload = {"gap_summary": "Looks great!", "gaps": [], "quick_wins": [], "deal_breakers": []}

    with pytest.raises(ExtractionValidationError):
        await service_returning(payload).analyze(REQUEST)


async def test_empty_gaps_for_empty_input_is_allowed():
    payload = {"gap_summary": "No gaps.", "gaps": [], "quick_wins": [], "deal_breakers": []}
    empty_request = GapAnalysisRequest(
        job_title="Senior Backend Engineer",
        missing_skills=[],
        missing_must_haves=[],
        preferred_missing=[],
    )

    result = await service_returning(payload).analyze(empty_request)

    assert result.gaps == []


async def test_malformed_json_raises_parse_error():
    service = service_returning("{ not valid json ")

    with pytest.raises(ExtractionParseError) as exc_info:
        await service.analyze(REQUEST)

    assert exc_info.value.raw_response == "{ not valid json "


async def test_markdown_fenced_reply_is_still_parsed():
    fenced = "```json\n" + json.dumps(VALID_ANALYSIS) + "\n```"

    result = await service_returning(fenced).analyze(REQUEST)

    assert len(result.gaps) == 3


async def test_ollama_unavailable_propagates():
    service = GapAnalysisService(
        client=FakeOllamaClient(error=OllamaUnavailableError("runtime down"))
    )

    with pytest.raises(OllamaUnavailableError):
        await service.analyze(REQUEST)


async def test_standalone_happy_path():
    service = service_returning(VALID_STANDALONE)

    result = await service.analyze_standalone(StandaloneGapRequest(resume_text="Java engineer..."))

    assert result.strong_domains == ["backend API development", "cloud infrastructure"]
    assert result.weak_domains == ["data engineering", "frontend development"]
    assert len(result.recommended_additions) == 5
    assert result.general_assessment


async def test_standalone_without_weak_domains_raises_validation_error():
    payload = dict(VALID_STANDALONE, weak_domains=[])

    with pytest.raises(ExtractionValidationError):
        await service_returning(payload).analyze_standalone(
            StandaloneGapRequest(resume_text="Java engineer...")
        )


async def test_standalone_ollama_unavailable_propagates():
    service = GapAnalysisService(
        client=FakeOllamaClient(error=OllamaUnavailableError("runtime down"))
    )

    with pytest.raises(OllamaUnavailableError):
        await service.analyze_standalone(StandaloneGapRequest(resume_text="Java engineer..."))
