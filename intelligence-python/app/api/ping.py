from fastapi import APIRouter

router = APIRouter()

@router.get("/ping")
def ping():
    return {
        "service": "intelligence-python",
        "status": "UP",
        "message": "ping pong"
    }