"""Prompt-build / LLM-call / parse / validate pipeline for JD extraction."""

import json
import logging
import re

from pydantic import ValidationError

from app.clients.ollama_client import OllamaClient
from app.exceptions import ExtractionParseError, ExtractionValidationError
from app.prompts.jd_extraction_prompt import build_jd_extraction_prompt
from app.schemas.jd_schemas import JdExtractionResult

logger = logging.getLogger(__name__)

#: Fields an extraction is worthless without. Pydantic enforces these too; the
#: explicit check exists to name *which* ones were missing, which a Pydantic
#: error message buries under type detail.
REQUIRED_FIELDS = ("job_title", "responsibilities", "required_skills", "must_have")

#: ```json ... ``` or ``` ... ``` wrapped around the whole reply.
_FENCE_PATTERN = re.compile(
    r"^\s*```(?:json)?\s*(?P<body>.*?)\s*```\s*$",
    re.DOTALL | re.IGNORECASE,
)


def strip_markdown_fences(text: str) -> str:
    """Return the JSON body of an LLM reply, minus any markdown wrapper.

    Two passes, cheapest first: unwrap a fenced block if the whole reply is one,
    then fall back to slicing between the outermost braces. The fallback is what
    rescues a reply that opens with prose like "Here is the JSON:".
    """
    candidate = text.strip()

    fenced = _FENCE_PATTERN.match(candidate)
    if fenced:
        candidate = fenced.group("body").strip()

    if candidate.startswith("{") and candidate.endswith("}"):
        return candidate

    start = candidate.find("{")
    end = candidate.rfind("}")
    if start != -1 and end > start:
        return candidate[start : end + 1].strip()

    return candidate


class JdExtractionService:
    """Turns raw job-description text into a validated structured extraction."""

    def __init__(self, client: OllamaClient | None = None):
        self.client = client or OllamaClient()

    async def extract(self, raw_text: str) -> JdExtractionResult:
        """Extract structured intelligence from ``raw_text``.

        Raises:
            OllamaUnavailableError: propagated from the client untouched.
            ExtractionParseError: the reply was not parseable as a JSON object.
            ExtractionValidationError: parsed fine, but did not satisfy the schema.
        """
        prompt = build_jd_extraction_prompt(raw_text)
        completion = await self.client.generate(prompt)

        payload = self._parse(completion)
        self._check_required_fields(payload, completion)

        try:
            result = JdExtractionResult.model_validate(payload)
        except ValidationError as exc:
            logger.warning("Extraction failed schema validation: %s", exc)
            raise ExtractionValidationError(
                f"Extracted data failed validation: {exc.error_count()} error(s); "
                f"{self._summarise_validation_errors(exc)}"
            ) from exc

        logger.debug(
            "Extraction succeeded: job_title=%r required_skills=%d responsibilities=%d",
            result.job_title,
            len(result.required_skills),
            len(result.responsibilities),
        )
        return result

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
            # Valid JSON, wrong shape — e.g. the model returned a bare array.
            # Nothing downstream can read it, so it is a parse failure to the
            # caller even though json.loads was happy.
            raise ExtractionParseError(
                f"Expected a JSON object, got {type(payload).__name__}",
                raw_response=completion,
            )
        return payload

    @staticmethod
    def _check_required_fields(payload: dict, completion: str) -> None:
        missing = [field for field in REQUIRED_FIELDS if payload.get(field) is None]
        if missing:
            logger.warning("Extraction missing required fields: %s", missing)
            raise ExtractionValidationError(
                f"Extracted data missing required field(s): {', '.join(missing)}"
            )

    @staticmethod
    def _summarise_validation_errors(exc: ValidationError) -> str:
        return "; ".join(
            f"{'.'.join(str(p) for p in err['loc']) or '<root>'}: {err['msg']}"
            for err in exc.errors()[:5]
        )
