from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.health import router as health_router
from app.api.jd_routes import router as jd_router
from app.api.ping import router as ping_router
from app.schemas.jd_schemas import JdExtractionResponse

app = FastAPI(title="Job Hunt Copilot Intelligence Service")

# The Java backend is the only browser-visible consumer today.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(ping_router)
app.include_router(jd_router, prefix="/extract")


@app.exception_handler(RequestValidationError)
async def handle_request_validation_error(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """Return the service's own error envelope for malformed request bodies.

    Without this, a request that fails schema validation (for example raw_text
    shorter than 50 characters) would come back in FastAPI's default
    ``{"detail": [...]}`` shape, which is not the contract the Java client
    expects from this service.
    """
    detail = "; ".join(
        f"{'.'.join(str(p) for p in err['loc'])}: {err['msg']}" for err in exc.errors()
    )
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content=JdExtractionResponse(
            success=False, error=f"Invalid request: {detail}"
        ).model_dump(),
    )
