from fastapi import APIRouter

from app.config import settings

router = APIRouter()


@router.get("/health")
def health():
    return {
        "service": "intelligence-python",
        "status": "ok",
        "model": settings.ollama_model,
    }
