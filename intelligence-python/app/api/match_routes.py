"""POST /match/semantic-similarity route.

Mirrors the JD route's discipline: nothing raises out of this module, because
the Java ``MatchingIntelligenceClient`` keys entirely off the status code and an
unhandled exception would leak FastAPI's default error envelope.

The status contract is narrower than extraction's, though — there is no
"the model replied but the reply was unusable" failure mode for embeddings, so
503 (runtime down) is the only expected error.
"""

import logging

from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.exceptions import IntelligenceError, OllamaUnavailableError
from app.schemas.match_schemas import SemanticSimilarityRequest, SemanticSimilarityResponse
from app.services.semantic_similarity_service import SemanticSimilarityService

logger = logging.getLogger(__name__)

router = APIRouter(tags=["matching"])


def get_similarity_service() -> SemanticSimilarityService:
    """Service provider, overridable in tests via ``dependency_overrides``."""
    return SemanticSimilarityService()


@router.post("/semantic-similarity", response_model=SemanticSimilarityResponse)
async def semantic_similarity(
    request: SemanticSimilarityRequest,
    response: Response,
    service: SemanticSimilarityService = Depends(get_similarity_service),
) -> SemanticSimilarityResponse:
    logger.debug(
        "Semantic similarity requested: phrases=%d targets=%d threshold=%.2f",
        len(request.source_phrases),
        len(request.target_texts),
        request.threshold,
    )

    try:
        result = await service.compare(
            request.source_phrases, request.target_texts, request.threshold
        )
    except OllamaUnavailableError as exc:
        logger.error("LLM service unavailable for embeddings: %s", exc)
        # Raised, not returned: the response_model has no error field to carry a
        # message, so the status code is the whole signal. The Java client maps
        # 503 to IntelligenceServiceUnavailableException, and the scorers on the
        # far side fall back to keyword-only matching.
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="LLM service unavailable",
        ) from exc
    except IntelligenceError as exc:
        logger.exception("Unhandled intelligence error during semantic matching: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Semantic matching failed",
        ) from exc
    except Exception as exc:
        logger.exception("Unexpected error during semantic matching")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error",
        ) from exc

    response.status_code = status.HTTP_200_OK
    return result
