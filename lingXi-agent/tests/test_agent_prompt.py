from app.agents.prompts import compose_system_prompt
from app.agents.state import AgentContext


def test_dynamic_prompt_uses_context_without_rewriting_messages() -> None:
    prompt = compose_system_prompt(
        AgentContext(style="professional", business_tag=""),
        search_available=False,
    )

    assert "灵犀智能零售终端管理系统" in prompt
    assert "当前未配置联网搜索工具" in prompt
