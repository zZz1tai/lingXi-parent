"""Strict structured extraction using explicit LangChain v1 strategies."""

from __future__ import annotations

import time
from typing import Any

from fastapi import APIRouter, Depends
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage
from pydantic import BaseModel, ConfigDict, Field, create_model

from app.agents.builder import build_extraction_agent, get_recursion_limit
from app.api.dependencies import get_llm, get_request_id
from app.schemas.request import (
    ExtractRequest,
    ExtractionSchemaName,
    ExtractionStrategy,
)
from app.schemas.response import ExtractData, ExtractResponse
from app.utils.exceptions import AgentError
from app.utils.logger import logger


router = APIRouter(prefix="/api/v1/extract", tags=["extract"])


class ExtractionOutputError(AgentError):
    """The provider completed but did not return the requested schema."""

    def __init__(self, message: str = "Model returned invalid structured output") -> None:
        super().__init__(
            message,
            code="EXTRACTION_INVALID_OUTPUT",
            status_code=502,
        )


class _ExtractionModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class GeneralExtraction(_ExtractionModel):
    summary: str = Field(description="Brief summary of the text")
    key_points: list[str] = Field(description="Main points or findings")
    entities: list[dict[str, str]] = Field(default_factory=list)
    topics: list[str] = Field(default_factory=list)
    sentiment: str = Field(default="neutral")


class PersonExtraction(_ExtractionModel):
    name: str
    title: str | None = None
    organization: str | None = None
    achievements: list[str] = Field(default_factory=list)
    background: str | None = None


class EventExtraction(_ExtractionModel):
    title: str
    date: str | None = None
    location: str | None = None
    description: str
    participants: list[str] = Field(default_factory=list)
    significance: str | None = None


SCHEMA_REGISTRY: dict[ExtractionSchemaName, type[BaseModel]] = {
    ExtractionSchemaName.GENERAL: GeneralExtraction,
    ExtractionSchemaName.PERSON: PersonExtraction,
    ExtractionSchemaName.EVENT: EventExtraction,
}


def _build_dynamic_schema(fields: list[str]) -> type[BaseModel]:
    """Create a bounded schema after request validation has checked names."""

    definitions = {
        field_name: (
            str,
            Field(..., max_length=8_000, description=f"Extracted value for {field_name}"),
        )
        for field_name in fields
    }
    return create_model(
        "DynamicExtraction",
        __base__=_ExtractionModel,
        **definitions,
    )


def _validated_structured_response(
    result: dict[str, Any],
    schema: type[BaseModel],
) -> dict[str, Any]:
    structured = result.get("structured_response")
    if structured is None:
        raise ExtractionOutputError()
    try:
        validated = schema.model_validate(structured)
    except Exception as exc:
        raise ExtractionOutputError() from exc
    return validated.model_dump(mode="json")


async def _extract_with_strategy(
    *,
    text: str,
    schema: type[BaseModel],
    llm: BaseChatModel,
    strategy: ExtractionStrategy,
    request_id: str,
) -> dict[str, Any]:
    agent = build_extraction_agent(
        model=llm,
        response_schema=schema,
        strategy=strategy.value,
        system_prompt=(
            "You are a precise information extraction assistant. Extract only "
            "facts supported by the input and satisfy the required schema exactly."
        ),
    )
    result = await agent.ainvoke(
        {"messages": [HumanMessage(content=text)]},
        config={
            "recursion_limit": get_recursion_limit(3),
            "metadata": {"request_id": request_id},
        },
    )
    return _validated_structured_response(result, schema)


async def _extract_with_tool_strategy(
    text: str,
    schema: type[BaseModel],
    llm: BaseChatModel,
    request_id: str,
) -> dict[str, Any]:
    return await _extract_with_strategy(
        text=text,
        schema=schema,
        llm=llm,
        strategy=ExtractionStrategy.TOOL,
        request_id=request_id,
    )


async def _extract_with_provider_strategy(
    text: str,
    schema: type[BaseModel],
    llm: BaseChatModel,
    request_id: str,
) -> dict[str, Any]:
    return await _extract_with_strategy(
        text=text,
        schema=schema,
        llm=llm,
        strategy=ExtractionStrategy.PROVIDER,
        request_id=request_id,
    )


@router.post("", response_model=ExtractResponse, summary="Extract structured information")
async def extract_structured(
    request: ExtractRequest,
    request_id: str = Depends(get_request_id),
) -> ExtractResponse:
    start_time = time.perf_counter()
    if request.custom_fields:
        extraction_schema = _build_dynamic_schema(request.custom_fields)
        schema_name = "custom"
    else:
        extraction_schema = SCHEMA_REGISTRY[request.schema_name]
        schema_name = request.schema_name.value

    logger.info(
        "Extract request | request_id=%s | schema=%s | strategy=%s | text_length=%d",
        request_id,
        schema_name,
        request.strategy.value,
        len(request.text),
    )

    llm = get_llm(profile=f"extract-{request.strategy.value}")
    try:
        result = await _extract_with_strategy(
            text=request.text,
            schema=extraction_schema,
            llm=llm,
            strategy=request.strategy,
            request_id=request_id,
        )
    except ExtractionOutputError:
        raise
    except Exception as exc:
        logger.error(
            "Extract failed | request_id=%s | elapsed=%.2fs | error_type=%s",
            request_id,
            time.perf_counter() - start_time,
            type(exc).__name__,
        )
        raise ExtractionOutputError("Structured extraction failed") from exc

    logger.info(
        "Extract completed | request_id=%s | strategy=%s | elapsed=%.2fs",
        request_id,
        request.strategy.value,
        time.perf_counter() - start_time,
    )
    return ExtractResponse(
        success=True,
        message="ok",
        data=ExtractData(
            result=result,
            strategy=request.strategy.value,
            schema_name=schema_name,
            request_id=request_id,
        ),
    )
