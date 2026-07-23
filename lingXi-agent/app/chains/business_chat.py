"""Python 拥有的提示链，用于结构化业务聊天工作流。"""

from __future__ import annotations

import json
from typing import Any, AsyncIterator

from langchain_core.language_models import BaseChatModel
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from app.schemas.request import SmartQuestionHistoryItem, SmartQuestionsOutput


CONTEXT_ANALYSIS_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            """你是灵犀智能零售终端管理系统的数据分析助手。
只能基于系统提供的结构化业务数据回答，不得臆造缺失数据，也不要回复问候语。

回答要求：
1. 先概括与问题直接相关的关键数据；
2. 针对用户问题给出明确结论；
3. 发现异常时说明依据并给出可执行建议；
4. 数据不足时明确指出缺少什么。""",
        ),
        (
            "human",
            "系统业务数据（JSON）：\n{context_json}\n\n用户问题：\n{message}",
        ),
    ]
)


SMART_QUESTIONS_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            """你负责根据对话记录生成用户接下来最可能点击的快捷提问。
问题必须从用户视角出发、紧扣已有对话、彼此不重复。只输出符合指定结构的 JSON。""",
        ),
        (
            "human",
            "对话历史（JSON）：\n{history_json}\n\n{format_instructions}",
        ),
    ]
)


def _json_text(value: Any) -> str:
    """将任意值转换为紧凑的 JSON 字符串。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)


def build_context_analysis_chain(model: BaseChatModel):
    """构建 LCEL 上下文分析链。"""

    return CONTEXT_ANALYSIS_PROMPT | model | StrOutputParser()


async def analyze_context(model: BaseChatModel, message: str, context_data: Any) -> str:
    """异步分析上下文并返回分析结果字符串。"""
    chain = build_context_analysis_chain(model)
    return await chain.ainvoke(
        {"message": message, "context_json": _json_text(context_data)}
    )


async def stream_context_analysis(
    model: BaseChatModel,
    message: str,
    context_data: Any,
) -> AsyncIterator[str]:
    """异步流式分析上下文，逐块生成分析结果。"""
    chain = build_context_analysis_chain(model)
    async for chunk in chain.astream(
        {"message": message, "context_json": _json_text(context_data)}
    ):
        if chunk:
            yield chunk


def build_smart_questions_chain(model: BaseChatModel):
    """构建严格的 LCEL 智能问题结构化输出链。"""

    parser = JsonOutputParser(pydantic_object=SmartQuestionsOutput)
    chain = SMART_QUESTIONS_PROMPT | model | StrOutputParser() | parser
    return chain, parser


async def generate_smart_questions(
    model: BaseChatModel,
    history: list[SmartQuestionHistoryItem],
) -> list[str]:
    """异步生成智能问题列表，基于对话历史。"""
    chain, parser = build_smart_questions_chain(model)
    normalized_history = [
        {"role": item.resolved_role(), "content": item.content}
        for item in history
        if item.content.strip()
    ]
    raw = await chain.ainvoke(
        {
            "history_json": _json_text(normalized_history),
            "format_instructions": parser.get_format_instructions(),
        }
    )
    output = SmartQuestionsOutput.model_validate(raw)
    return output.questions
