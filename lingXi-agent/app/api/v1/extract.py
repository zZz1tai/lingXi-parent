"""
Structured information extraction endpoint.

Supports two strategies:
- **ToolStrategy**: Uses the agent's tool-calling mechanism to produce
  structured output (via ``response_format`` in ``create_react_agent``)
- **ProviderStrategy**: Uses the LLM's native structured output
  (via ``with_structured_output``)
"""

from __future__ import annotations

import time
from typing import Any, Optional, Type

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.agents.builder import build_extraction_agent, get_recursion_limit
from app.api.dependencies import get_agent, get_llm, get_request_id
from app.schemas.request import ExtractRequest
from app.schemas.response import ExtractData, ExtractResponse
from app.utils.logger import logger

from fastapi import APIRouter, Depends

router = APIRouter(prefix="/api/v1/extract", tags=["extract"])


# ── Predefined Extraction Schemas ───────────────────────────────────────────

class GeneralExtraction(BaseModel):
    """General-purpose structured extraction schema."""

    summary: str = Field(description="Brief summary of the text")
    key_points: list[str] = Field(description="Main points or findings")
    entities: list[dict[str, str]] = Field(
        default_factory=list,
        description="Named entities found (name, type, description)",
    )
    topics: list[str] = Field(
        default_factory=list,
        description="Main topics or categories"
    )
    sentiment: str = Field(
        default="neutral",
        description="Overall sentiment: positive, negative, or neutral",
    )


class PersonExtraction(BaseModel):
    """Schema for extracting person-related information."""

    name: str = Field(description="Person's full name")
    title: Optional[str] = Field(default=None, description="Job title or role")
    organization: Optional[str] = Field(default=None, description="Associated organization")
    achievements: list[str] = Field(
        default_factory=list,
        description="Key achievements or accomplishments",
    )
    background: Optional[str] = Field(default=None, description="Brief background description")


class EventExtraction(BaseModel):
    """Schema for extracting event-related information."""

    title: str = Field(description="Event name or title")
    date: Optional[str] = Field(default=None, description="Event date or time reference")
    location: Optional[str] = Field(default=None, description="Event location")
    description: str = Field(description="Brief description of the event")
    participants: list[str] = Field(
        default_factory=list,
        description="Key participants or organizations involved",
    )
    significance: Optional[str] = Field(
        default=None,
        description="Why this event is significant",
    )


# Schema registry for lookup by name
SCHEMA_REGISTRY: dict[str, Type[BaseModel]] = {
    "general": GeneralExtraction,
    "person": PersonExtraction,
    "event": EventExtraction,
}


def _build_dynamic_schema(fields: list[str]) -> Type[BaseModel]:
    """Dynamically create a Pydantic model from a list of field names.

    Each field is typed as ``str`` with a generic description.
    """
    field_definitions = {}
    for field_name in fields:
        field_definitions[field_name] = (
            str,
            Field(default="", description=f"Extracted value for {field_name}"),
        )

    # Create a dynamic model class
    DynamicModel = BaseModel  # type: ignore
    model = type(
        "DynamicExtraction",
        (DynamicModel,),
        {"__annotations__": {k: v[0] for k, v in field_definitions.items()},
         **{k: v[1] for k, v in field_definitions.items()}},
    )
    return model  # type: ignore


# ── Strategy Implementations ────────────────────────────────────────────────

async def _extract_with_tool_strategy(
    text: str,
    schema: Type[BaseModel],
    llm: BaseChatModel,
    request_id: str,
) -> dict[str, Any]:
    """ToolStrategy: Use agent with response_format for structured output.

    The agent runs a mini ReAct loop where the final response is
    forced into the target schema via tool-calling.
    """
    agent = build_extraction_agent(
        model=llm,
        response_schema=schema,
        system_prompt=(
            "You are a precise information extraction assistant. "
            "Analyze the provided text and extract structured information "
            "according to the required schema. Be accurate and complete."
        ),
    )

    result = await agent.ainvoke(
        {"messages": [HumanMessage(content=text)]},
        config={"recursion_limit": get_recursion_limit(3)},
    )

    # Extract structured response from agent output
    structured_response = result.get("structured_response")
    if structured_response and isinstance(structured_response, BaseModel):
        return structured_response.model_dump()

    # Fallback: try to parse from the last AI message
    messages = result.get("messages", [])
    for msg in reversed(messages):
        if hasattr(msg, "parsed") and msg.parsed:
            if isinstance(msg.parsed, BaseModel):
                return msg.parsed.model_dump()
            return dict(msg.parsed) if isinstance(msg.parsed, dict) else {}

    return {}


async def _extract_with_provider_strategy(
    text: str,
    schema: Type[BaseModel],
    llm: BaseChatModel,
    request_id: str,
) -> dict[str, Any]:
    """ProviderStrategy: Use LLM's native structured output.

    Directly calls ``with_structured_output`` on the LLM, leveraging
    the model's built-in JSON/function-calling mode.
    """
    structured_llm = llm.with_structured_output(schema)

    result = await structured_llm.ainvoke([
        SystemMessage(content=(
            "You are a precise information extraction assistant. "
            "Analyze the provided text and extract structured information "
            "according to the required schema. Be accurate and complete."
        )),
        HumanMessage(content=text),
    ])

    if isinstance(result, BaseModel):
        return result.model_dump()
    if isinstance(result, dict):
        return result
    return {}


# ── Endpoint ────────────────────────────────────────────────────────────────

@router.post("", response_model=ExtractResponse, summary="Extract structured information")
async def extract_structured(
    request: ExtractRequest,
    request_id: str = Depends(get_request_id),
) -> ExtractResponse:
    """Extract structured information from text using the specified strategy.

    **Strategies:**
    - ``tool`` (ToolStrategy): Uses a LangGraph agent with ``response_format``
      to produce structured output via tool-calling. More robust for complex
      extraction tasks that may benefit from reasoning.
    - ``provider`` (ProviderStrategy): Uses the LLM's native structured output
      (``with_structured_output``). Faster and more direct, suitable for
      well-defined extraction schemas.

    **Predefined schemas:** ``general``, ``person``, ``event``
    **Custom schemas:** Provide ``custom_fields`` to dynamically create a schema.
    """
    start_time = time.time()

    logger.info(
        "Extract request | request_id=%s | schema=%s | strategy=%s | text_length=%d",
        request_id,
        request.schema_name,
        request.strategy,
        len(request.text),
    )

    # Resolve extraction schema
    if request.custom_fields:
        extraction_schema = _build_dynamic_schema(request.custom_fields)
        schema_name = "custom"
    else:
        extraction_schema = SCHEMA_REGISTRY.get(request.schema_name, GeneralExtraction)
        schema_name = request.schema_name

    llm = get_llm()

    try:
        # Execute the selected strategy
        if request.strategy == "tool":
            result = await _extract_with_tool_strategy(
                text=request.text,
                schema=extraction_schema,
                llm=llm,
                request_id=request_id,
            )
        else:
            result = await _extract_with_provider_strategy(
                text=request.text,
                schema=extraction_schema,
                llm=llm,
                request_id=request_id,
            )

        elapsed = time.time() - start_time
        logger.info(
            "Extract completed | request_id=%s | strategy=%s | elapsed=%.2fs",
            request_id,
            request.strategy,
            elapsed,
        )

        return ExtractResponse(
            success=True,
            message="ok",
            data=ExtractData(
                result=result,
                strategy=request.strategy,
                schema_name=schema_name,
                request_id=request_id,
            ),
        )

    except Exception as exc:
        elapsed = time.time() - start_time
        logger.error(
            "Extract failed | request_id=%s | elapsed=%.2fs | error=%s",
            request_id,
            elapsed,
            str(exc),
        )
        raise
