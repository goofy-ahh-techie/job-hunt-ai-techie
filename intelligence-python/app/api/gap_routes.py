"""POST /gaps/analyze and /gaps/analyze-standalone routes.

Same discipline as the JD routes: nothing raises out of this module, because the
Java ``SkillGapIntelligenceClient`` keys entirely off the status code and an
unhandled exception would leak FastAPI's default error envelope instead of the
``{success, data, error}`` contract.
"""

import logging

from fastapi import APIRouter, Depends, Response, status

from app.exceptions import (
    ExtractionParseError,
    ExtractionValidationError,
    IntelligenceError,
    OllamaUnavailableError,
)
from app.schemas.gap_schemas import (
    GapAnalysisRequest,
    GapAnalysisResponse,
    StandaloneGapRequest,
    StandaloneGapResponse,
)
from app.services.gap_analysis_service import GapAnalysisService

logger = logging.getLogger(__name__)

router = APIRouter(tags=["skill-gaps"])


def get_gap_analysis_service() -> GapAnalysisService:
    """Service provider, overridable in tests via ``dependency_overrides``."""
    return GapAnalysisService()


@router.post("/analyze", response_model=GapAnalysisResponse)
async def analyze_gaps(
    request: GapAnalysisRequest,
    response: Response,
    service: GapAnalysisService = Depends(get_gap_analysis_service),
) -> GapAnalysisResponse:
    logger.debug(
        "Gap analysis requested: job_title=%r must_haves=%d skills=%d preferred=%d",
        request.job_title,
        len(request.missing_must_haves),
        len(request.missing_skills),
        len(request.preferred_missing),
    )

    try:
        result = await service.analyze(request)
    except OllamaUnavailableError as exc:
        logger.error("LLM service unavailable: %s", exc)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return GapAnalysisResponse(success=False, error="LLM service unavailable")
    except ExtractionParseError as exc:
        logger.error(
            "Failed to parse LLM response: %s | raw=%.500s", exc, exc.raw_response or ""
        )
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return GapAnalysisResponse(success=False, error="Failed to parse LLM response")
    except ExtractionValidationError as exc:
        logger.error("Gap analysis failed validation: %s", exc)
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return GapAnalysisResponse(
            success=False, error="Gap analysis failed validation"
        )
    except IntelligenceError as exc:
        # Defensive: a new domain error added later must not escape as a 500
        # carrying FastAPI's own error envelope.
        logger.exception("Unhandled intelligence error: %s", exc)
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return GapAnalysisResponse(success=False, error="Gap analysis failed")
    except Exception:
        logger.exception("Unexpected error during gap analysis")
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return GapAnalysisResponse(success=False, error="Internal server error")

    response.status_code = status.HTTP_200_OK
    return GapAnalysisResponse(success=True, data=result)


@router.post("/analyze-standalone", response_model=StandaloneGapResponse)
async def analyze_standalone(
    request: StandaloneGapRequest,
    response: Response,
    service: GapAnalysisService = Depends(get_gap_analysis_service),
) -> StandaloneGapResponse:
    logger.debug(
        "Standalone gap analysis requested: resume_text_length=%d",
        len(request.resume_text),
    )

    try:
        result = await service.analyze_standalone(request)
    except OllamaUnavailableError as exc:
        logger.error("LLM service unavailable: %s", exc)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return StandaloneGapResponse(success=False, error="LLM service unavailable")
    except ExtractionParseError as exc:
        logger.error(
            "Failed to parse LLM response: %s | raw=%.500s", exc, exc.raw_response or ""
        )
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return StandaloneGapResponse(
            success=False, error="Failed to parse LLM response"
        )
    except ExtractionValidationError as exc:
        logger.error("Standalone gap analysis failed validation: %s", exc)
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return StandaloneGapResponse(
            success=False, error="Standalone gap analysis failed validation"
        )
    except IntelligenceError as exc:
        logger.exception("Unhandled intelligence error: %s", exc)
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return StandaloneGapResponse(success=False, error="Gap analysis failed")
    except Exception:
        logger.exception("Unexpected error during standalone gap analysis")
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return StandaloneGapResponse(success=False, error="Internal server error")

    response.status_code = status.HTTP_200_OK
    return StandaloneGapResponse(success=True, data=result)
