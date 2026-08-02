"""HTTP client for the local Ollama runtime.

Wraps Ollama's ``POST /api/generate`` endpoint. Every failure mode the runtime
can present — connection refused, read timeout, non-2xx, malformed envelope —
is normalised into :class:`OllamaUnavailableError`, so callers never have to
know that httpx is the transport.
"""

import logging

import httpx

from app.config import settings
from app.exceptions import OllamaUnavailableError

logger = logging.getLogger(__name__)

GENERATE_PATH = "/api/generate"


class OllamaClient:
    """Thin async wrapper over Ollama's generate API."""

    def __init__(
        self,
        base_url: str | None = None,
        model: str | None = None,
        timeout_seconds: float | None = None,
        temperature: float | None = None,
        num_ctx: int | None = None,
    ):
        self.base_url = (base_url or settings.ollama_base_url).rstrip("/")
        self.model = model or settings.ollama_model
        self.timeout_seconds = (
            timeout_seconds
            if timeout_seconds is not None
            else settings.ollama_timeout_seconds
        )
        self.temperature = (
            temperature if temperature is not None else settings.ollama_temperature
        )
        self.num_ctx = num_ctx if num_ctx is not None else settings.ollama_num_ctx

    async def generate(self, prompt: str) -> str:
        """Send ``prompt`` to the model and return the raw completion text.

        Raises:
            OllamaUnavailableError: the runtime was unreachable, timed out,
                returned a non-2xx status, or replied with an envelope that
                did not contain a ``response`` field.
        """
        url = f"{self.base_url}{GENERATE_PATH}"
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": self.temperature,
                "num_ctx": self.num_ctx,
            },
        }

        logger.debug(
            "Calling Ollama: url=%s model=%s prompt_length=%d timeout=%.1fs",
            url,
            self.model,
            len(prompt),
            self.timeout_seconds,
        )

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(url, json=payload)
                response.raise_for_status()
                envelope = response.json()
        except httpx.TimeoutException as exc:
            raise OllamaUnavailableError(
                f"Ollama request timed out after {self.timeout_seconds}s "
                f"(model={self.model})"
            ) from exc
        except httpx.HTTPStatusError as exc:
            raise OllamaUnavailableError(
                f"Ollama returned HTTP {exc.response.status_code} "
                f"(model={self.model})"
            ) from exc
        except httpx.HTTPError as exc:
            raise OllamaUnavailableError(
                f"Could not reach Ollama at {url}: {exc}"
            ) from exc
        except ValueError as exc:
            # response.json() on a non-JSON body. The runtime answered, but not
            # in its own protocol — treat it as unavailable, not as a parse
            # failure of the *model's* output.
            raise OllamaUnavailableError(
                f"Ollama returned a non-JSON envelope from {url}"
            ) from exc

        completion = envelope.get("response")
        if completion is None:
            raise OllamaUnavailableError(
                "Ollama envelope did not contain a 'response' field "
                f"(keys={sorted(envelope)})"
            )

        logger.debug(
            "Ollama responded: model=%s response_length=%d",
            self.model,
            len(completion),
        )
        return completion
