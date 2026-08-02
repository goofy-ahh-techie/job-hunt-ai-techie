"""POST /extract/jd route.

Every failure is translated here into a :class:`JdExtractionResponse` with an
appropriate status code. Nothing raises out of this module: the Java client
(``JdIntelligenceClient``) keys entirely off the status code, so an unhandled
exception leaking FastAPI's default error envelope would break that contract.
"""

import logging

from fastapi import APIRouter, Depends, Response, status

from app.exceptions import (
    ExtractionParseError,
    ExtractionValidationError,
    IntelligenceError,
    OllamaUnavailableError,
)
from app.schemas.jd_schemas import JdExtractionRequest, JdExtractionResponse
from app.services.jd_extraction_service import JdExtractionService

logger = logging.getLogger(__name__)

router = APIRouter(tags=["extraction"])


def get_extraction_service() -> JdExtractionService:
    """Service provider, overridable in tests via ``dependency_overrides``."""
    return JdExtractionService()


@router.post("/jd", response_model=JdExtractionResponse)
async def extract_jd(
    request: JdExtractionRequest,
    response: Response,
    service: JdExtractionService = Depends(get_extraction_service),
) -> JdExtractionResponse:
    logger.debug("JD extraction requested: raw_text_length=%d", len(request.raw_text))

    try:
        result = await service.extract(request.raw_text)
    except OllamaUnavailableError as exc:
        logger.error("LLM service unavailable: %s", exc)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return JdExtractionResponse(success=False, error="LLM service unavailable")
    except ExtractionParseError as exc:
        logger.error(
            "Failed to parse LLM response: %s | raw=%.500s", exc, exc.raw_response or ""
        )
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return JdExtractionResponse(
            success=False, error="Failed to parse LLM response"
        )
    except ExtractionValidationError as exc:
        logger.error("Extracted data failed validation: %s", exc)
        response.status_code = status.HTTP_422_UNPROCESSABLE_ENTITY
        return JdExtractionResponse(
            success=False, error="Extracted data failed validation"
        )
    except IntelligenceError as exc:
        # Defensive: a new domain error added later must not escape as a 500
        # with FastAPI's own error envelope.
        logger.exception("Unhandled intelligence error: %s", exc)
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return JdExtractionResponse(success=False, error="Extraction failed")
    except Exception:
        logger.exception("Unexpected error during JD extraction")
        response.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR
        return JdExtractionResponse(success=False, error="Internal server error")

    response.status_code = status.HTTP_200_OK
    return JdExtractionResponse(success=True, data=result)
