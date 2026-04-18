from fastapi import APIRouter

router = APIRouter()

@router.get("/health")
def health():
    return {
        "service": "intelligence-python",
        "status": "UP"
    }