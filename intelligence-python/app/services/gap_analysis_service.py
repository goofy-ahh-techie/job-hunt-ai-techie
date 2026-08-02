"""Prompt-build / LLM-call / parse / validate pipeline for skill gap analysis.

Shares the parse-and-clean machinery with JD extraction — ``strip_markdown_fences``
is imported rather than reimplemented, since "the model wrapped its JSON in a
fence again" is the same problem regardless of which prompt produced it.

What differs is what counts as a failure. Extraction failed when a required fact
was absent. Gap analysis fails when the model was *given* gaps and returned none:
that is a refusal to do the task, not a legitimately empty answer, and it must not
be persisted as "no gaps found" — which would tell a candidate they are a perfect
match for a role they are not.
"""

import json
import logging

from pydantic import ValidationError

from app.clients.ollama_client import OllamaClient
from app.exceptions import ExtractionParseError, ExtractionValidationError
from app.prompts.gap_analysis_prompt import (
    build_gap_analysis_prompt,
    build_standalone_gap_prompt,
)
from app.schemas.gap_schemas import (
    GapAnalysisRequest,
    GapAnalysisResult,
    GapItem,
    StandaloneGapRequest,
    StandaloneGapResult,
)
from app.services.jd_extraction_service import strip_markdown_fences

logger = logging.getLogger(__name__)

#: Ranking used to order gaps most-urgent-first. The API contract is that the
#: first gap in the list is the one to work on next, so the sort happens here
#: rather than being left to every consumer.
PRIORITY_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}

#: A gap is a "quick win" at or below this many weeks.
QUICK_WIN_MAX_WEEKS = 1

#: Which input list maps to which priority. This mapping is the *whole* of the
#: priority rule — it is mechanical, so the model is not trusted with it (see
#: _enforce_priorities). Measured: llama3.2:3b promoted a missing_skills item to
#: CRITICAL on the first live run.
PRIORITY_BY_SOURCE = (
    ("missing_must_haves", "CRITICAL"),
    ("missing_skills", "HIGH"),
    ("preferred_missing", "MEDIUM"),
)


class GapAnalysisService:
    """Turns detected gaps into prioritised, actionable learning guidance."""

    def __init__(self, client: OllamaClient | None = None):
        self.client = client or OllamaClient()

    async def analyze(self, request: GapAnalysisRequest) -> GapAnalysisResult:
        """Rank and explain the gaps in ``request``.

        Raises:
            OllamaUnavailableError: propagated from the client untouched.
            ExtractionParseError: the reply was not parseable as a JSON object.
            ExtractionValidationError: parsed, but did not satisfy the schema, or
                returned no gaps for an input that had them.
        """
        prompt = build_gap_analysis_prompt(
            job_title=request.job_title,
            company_name=request.company_name,
            missing_skills=request.missing_skills,
            missing_must_haves=request.missing_must_haves,
            preferred_missing=request.preferred_missing,
            resume_text_summary=request.resume_text_summary,
            overall_score=request.overall_score,
        )
        completion = await self.client.generate(prompt)
        payload = self._parse(completion)

        try:
            result = GapAnalysisResult.model_validate(payload)
        except ValidationError as exc:
            logger.warning("Gap analysis failed schema validation: %s", exc)
            raise ExtractionValidationError(
                f"Gap analysis failed validation: {exc.error_count()} error(s); "
                f"{self._summarise_validation_errors(exc)}"
            ) from exc

        self._require_gaps_when_input_had_them(result, request)

        # Everything below is deterministic post-processing. The model is used for
        # what only it can do — judging why a gap matters and how to close it — and
        # is overruled on everything that is a rule rather than a judgement.
        self._enforce_priorities(result, request)
        self._restore_dropped_gaps(result, request)
        self._derive_quick_wins(result)
        self._constrain_deal_breakers(result)

        result.gaps = sorted(
            result.gaps, key=lambda gap: PRIORITY_ORDER.get(gap.priority, 2)
        )

        logger.debug(
            "Gap analysis succeeded: gaps=%d critical=%d quick_wins=%d deal_breakers=%d",
            len(result.gaps),
            sum(1 for gap in result.gaps if gap.priority == "CRITICAL"),
            len(result.quick_wins),
            len(result.deal_breakers),
        )
        return result

    async def analyze_standalone(
        self, request: StandaloneGapRequest
    ) -> StandaloneGapResult:
        """Assess a resume on its own, with no target role."""
        prompt = build_standalone_gap_prompt(request.resume_text)
        completion = await self.client.generate(prompt)
        payload = self._parse(completion)

        try:
            result = StandaloneGapResult.model_validate(payload)
        except ValidationError as exc:
            logger.warning("Standalone gap analysis failed schema validation: %s", exc)
            raise ExtractionValidationError(
                f"Standalone gap analysis failed validation: {exc.error_count()} error(s); "
                f"{self._summarise_validation_errors(exc)}"
            ) from exc

        # Both halves of the judgement are the point of the endpoint: strengths
        # without weaknesses is flattery, weaknesses without strengths is not
        # actionable. An empty either way means the model did not do the task.
        if not result.strong_domains or not result.weak_domains:
            raise ExtractionValidationError(
                "Standalone gap analysis returned no "
                f"{'strong_domains' if not result.strong_domains else 'weak_domains'}"
            )

        logger.debug(
            "Standalone gap analysis succeeded: strong=%d weak=%d additions=%d",
            len(result.strong_domains),
            len(result.weak_domains),
            len(result.recommended_additions),
        )
        return result

    # --- deterministic post-processing ---

    @staticmethod
    def _source_lists(request: GapAnalysisRequest):
        """Input list name → its items, in priority order."""
        return [
            (field, getattr(request, field), priority)
            for field, priority in PRIORITY_BY_SOURCE
        ]

    @classmethod
    def _match_source(cls, skill: str, request: GapAnalysisRequest):
        """Find the input list a returned gap came from, or None if inferred.

        Matching is lenient in one direction only: the model routinely shortens
        "Hands-on experience with Kubernetes in production" to "Kubernetes", so a
        returned skill contained in an input item (or vice versa) counts as the
        same gap. Anything unmatched is treated as a genuinely inferred gap.
        """
        needle = skill.strip().lower()
        if not needle:
            return None
        for _, items, priority in cls._source_lists(request):
            for item in items:
                haystack = item.strip().lower()
                if needle == haystack or needle in haystack or haystack in needle:
                    return item, priority
        return None

    @classmethod
    def _enforce_priorities(
        cls, result: GapAnalysisResult, request: GapAnalysisRequest
    ) -> None:
        """Overwrite each gap's priority from the list it actually came from.

        Priority is defined entirely by provenance — a must-have is CRITICAL
        because it is a must-have, not because the model judged it severe. Gaps
        that match no input list are the model's own inferences and are pinned to
        LOW, which is the only priority the prompt allows for them.
        """
        for gap in result.gaps:
            source = cls._match_source(gap.skill, request)
            corrected = source[1] if source else "LOW"
            if gap.priority != corrected:
                logger.debug(
                    "Correcting priority for %r: %s -> %s", gap.skill, gap.priority, corrected
                )
                gap.priority = corrected

    @classmethod
    def _restore_dropped_gaps(
        cls, result: GapAnalysisResult, request: GapAnalysisRequest
    ) -> None:
        """Re-add input gaps the model silently omitted.

        A dropped gap is worse than a poorly-explained one: it disappears from
        the candidate's list of things to fix. The restored entry carries no
        invented rationale — it says plainly that guidance was unavailable, so a
        thin recommendation is never mistaken for a considered one.
        """
        for _, items, priority in cls._source_lists(request):
            for item in items:
                if any(cls._same_gap(item, gap.skill) for gap in result.gaps):
                    continue
                logger.warning("Model dropped gap %r; restoring it", item)
                result.gaps.append(
                    GapItem(
                        skill=item,
                        priority=priority,
                        reason="Identified as a gap for this role.",
                        learning_recommendation=(
                            "No detailed guidance was generated for this gap. "
                            "Re-run the analysis for a recommendation."
                        ),
                        estimated_weeks=None,
                    )
                )

    @staticmethod
    def _same_gap(left: str, right: str) -> bool:
        left_clean, right_clean = left.strip().lower(), right.strip().lower()
        if not left_clean or not right_clean:
            return False
        return (
            left_clean == right_clean
            or left_clean in right_clean
            or right_clean in left_clean
        )

    @staticmethod
    def _derive_quick_wins(result: GapAnalysisResult) -> None:
        """Recompute quick_wins from the estimates rather than trusting the list.

        "Closeable in a week" is arithmetic on a number the model already gave.
        Measured: the model left quick_wins empty while estimating a gap at one
        week, so the two halves of its own answer disagreed.
        """
        result.quick_wins = [
            gap.skill
            for gap in result.gaps
            if gap.estimated_weeks is not None
            and gap.estimated_weeks <= QUICK_WIN_MAX_WEEKS
        ]

    @staticmethod
    def _constrain_deal_breakers(result: GapAnalysisResult) -> None:
        """Keep only deal_breakers that correspond to an actual CRITICAL gap.

        Whether the resume shows adjacent experience is a judgement, so the
        model's selection stands. What it does not get to do is nominate a
        preferred skill as disqualifying.
        """
        critical = [gap.skill for gap in result.gaps if gap.priority == "CRITICAL"]
        result.deal_breakers = [
            breaker
            for breaker in result.deal_breakers
            if any(
                GapAnalysisService._same_gap(breaker, skill) for skill in critical
            )
        ]

    @staticmethod
    def _require_gaps_when_input_had_them(
        result: GapAnalysisResult, request: GapAnalysisRequest
    ) -> None:
        """Reject an empty gap list when gaps were supplied.

        The caller short-circuits a genuine perfect match before ever reaching
        this service, so an empty ``gaps`` here can only mean the model ignored
        its input.
        """
        had_input = bool(
            request.missing_skills
            or request.missing_must_haves
            or request.preferred_missing
        )
        if had_input and not result.gaps:
            logger.warning(
                "Model returned no gaps for %d supplied gap(s)",
                len(request.missing_skills)
                + len(request.missing_must_haves)
                + len(request.preferred_missing),
            )
            raise ExtractionValidationError(
                "Gap analysis returned no gaps despite gaps being supplied"
            )

    @staticmethod
    def _parse(completion: str) -> dict:
        cleaned = strip_markdown_fences(completion)
        try:
            payload = json.loads(cleaned)
        except json.JSONDecodeError as exc:
            logger.warning("LLM reply was not valid JSON: %s", exc)
            raise ExtractionParseError(
                f"Failed to parse LLM response as JSON: {exc}",
                raw_response=completion,
            ) from exc

        if not isinstance(payload, dict):
            raise ExtractionParseError(
                f"Expected a JSON object, got {type(payload).__name__}",
                raw_response=completion,
            )
        return payload

    @staticmethod
    def _summarise_validation_errors(exc: ValidationError) -> str:
        return "; ".join(
            f"{'.'.join(str(p) for p in err['loc']) or '<root>'}: {err['msg']}"
            for err in exc.errors()[:5]
        )
