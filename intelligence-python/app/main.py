from fastapi import FastAPI
from app.api.health import router as health_router
from app.api.ping import router as ping_router

app = FastAPI(title="Job Hunt Copilot Intelligence Service")

app.include_router(health_router)
app.include_router(ping_router)