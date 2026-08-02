"""Domain exceptions for the intelligence service.

These live in one module rather than beside the layer that raises them: the JD
route has to catch all three to map them onto HTTP status codes, and a shared
module keeps the route from importing exception types out of the client and the
service packages just to write its ``except`` clauses.
"""


class IntelligenceError(Exception):
    """Base class for every error this service raises deliberately."""


class OllamaUnavailableError(IntelligenceError):
    """The LLM runtime could not be reached, timed out, or returned non-2xx.

    Maps to HTTP 503 — the request is retryable once the runtime is healthy.
    """


class ExtractionParseError(IntelligenceError):
    """The LLM replied, but the reply was not parseable as JSON.

    The unparseable text is kept on ``raw_response`` so callers can log the
    model's actual output; a parse failure is almost never debuggable without it.
    """

    def __init__(self, message: str, raw_response: str | None = None):
        super().__init__(message)
        self.raw_response = raw_response


class ExtractionValidationError(IntelligenceError):
    """The LLM returned valid JSON that does not satisfy the extraction schema."""
