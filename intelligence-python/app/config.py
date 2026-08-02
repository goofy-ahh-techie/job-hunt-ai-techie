"""Application configuration.

Single source of truth for environment-driven settings. Values come from the
process environment (or a local ``.env`` file); every field has a local-dev
default so the service starts with no configuration at all.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "job-hunt-copilot-intelligence"
    app_env: str = "local"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "llama3.2:3b"
    # 30s (the original figure) is not survivable: a 3B model on CPU emits the
    # ~400-token extraction JSON at roughly 13 tok/s, so a single call takes
    # ~32s before any headroom. Measured on a 2.2KB job description.
    ollama_timeout_seconds: float = 120.0
    # Extraction is a deterministic task: the same JD must yield the same
    # structured output. temperature=0 removes the run-to-run variance that
    # otherwise left raw_summary intermittently null and reshuffled skill lists.
    ollama_temperature: float = 0.0
    # Default Ollama context is 2048 tokens. The prompt alone is ~750 tokens and
    # a long JD pushes the total past that, at which point Ollama silently drops
    # the *front* of the context — losing instructions, not erroring. 8192 keeps
    # a full prompt + a large JD inside the window.
    ollama_num_ctx: int = 8192
    # Model used for /api/embeddings (Phase 4 semantic matching). This must be a
    # dedicated embedding model, not ollama_model: a generative model answers
    # /api/embeddings with "This server does not support embeddings" (verified
    # against llama3.2:3b on Ollama 0.32.3), because llama.cpp only exposes the
    # embedding head when the model was loaded for it. nomic-embed-text is 274MB
    # against the generative model's 2GB and returns a vector in well under a
    # second. Empty falls back to ollama_model, which is only useful if that is
    # itself an embedding model.
    ollama_embedding_model: str = "nomic-embed-text"
    # A semantic-similarity request embeds M target texts + N unmatched phrases,
    # one Ollama call each. Individually each is far quicker than a completion,
    # but the same 120s ceiling covers a slow cold start on the first call.
    ollama_embedding_timeout_seconds: float = 120.0
    # Cosine-similarity floor for "this phrase is present in the resume".
    # Overridable per request by the Java caller; this is the fallback.
    semantic_default_threshold: float = 0.65

    intelligence_port: int = 8000

    model_config = SettingsConfigDict(
        env_file=".env",
        extra="ignore",
    )


settings = Settings()
