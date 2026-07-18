from langchain_core.messages import HumanMessage, SystemMessage

from app.agents.prompts import get_system_prompt


def test_dynamic_prompt_keeps_user_messages() -> None:
    user_message = HumanMessage(content="只回复：服务正常")

    messages = get_system_prompt(
        {
            "messages": [user_message],
            "style": "professional",
            "business_tag": "",
        }
    )

    assert isinstance(messages[0], SystemMessage)
    assert messages[1] is user_message
    assert messages[1].content == "只回复：服务正常"
