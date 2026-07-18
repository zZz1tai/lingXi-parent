import asyncio
import copy
import json
import unittest

from langchain_core.language_models import FakeListChatModel

from app.chains import build_chapter_analysis_chain
from app.services.chapter_analysis import SourceUnit, validate_document


def _source_units() -> list[SourceUnit]:
    return [
        SourceUnit(id="U1", order=1, paragraph_no=1, text="林夏推门而入。"),
        SourceUnit(id="U2", order=2, paragraph_no=1, text="她看见顾川。"),
    ]


def _shot(shot_no: int, source_unit_id: str, character: str) -> dict:
    return {
        "shotNo": shot_no,
        "durationMs": 3000,
        "sourceUnitIds": [source_unit_id],
        "characters": [character],
        "narrativeBeat": "人物进入房间",
        "shotSize": "MEDIUM_SHOT",
        "cameraMovement": "STATIC",
        "composition": "人物位于画面中央",
        "action": "人物缓步向前",
        "emotion": "警觉",
        "dialogues": [],
        "keyframePrompt": "cinematic medium shot in a quiet room",
        "imageNegativePrompt": "text, watermark",
        "videoPrompt": "The character walks forward slowly while identity and room remain consistent.",
        "videoNegativePrompt": "identity drift, background drift, text, watermark",
    }


def _story_bible() -> dict:
    return {
        "summary": "林夏进入房间并看见顾川。",
        "worldSetting": "现代室内",
        "timeline": [],
        "relationships": [],
        "immutableFacts": [],
        "videoPlan": {
            "sourceUnitCount": 2,
            "minimumShotCount": 2,
            "shotCount": 2,
            "estimatedTotalDurationMs": 6000,
            "segmentationRationale": "按进入和反应拆成两镜",
        },
        "characters": [
            {
                "name": "林夏",
                "aliases": ["小夏"],
                "gender": "女",
                "ageRange": "20-25",
                "appearance": "黑色短发",
                "personality": "谨慎",
                "speakingStyle": "简洁",
                "visualPromptBase": "young woman, short black hair, navy coat",
            },
            {
                "name": "顾川",
                "aliases": [],
                "gender": "男",
                "ageRange": "25-30",
                "appearance": "棕色短发",
                "personality": "沉稳",
                "speakingStyle": "平静",
                "visualPromptBase": "young man, short brown hair, gray shirt",
            },
        ],
        "scenes": [
            {
                "sceneNo": 1,
                "title": "安静的房间",
                "time": "夜晚",
                "location": "房间",
                "atmosphere": "紧张",
                "dramaticGoal": "两人相遇",
                "characters": ["林夏", "顾川"],
                "dialogues": [],
                "shots": [
                    _shot(1, "U1", "小夏"),
                    _shot(2, "U2", "顾川"),
                ],
            }
        ],
    }


class ChapterChainIntegrationTests(unittest.TestCase):
    def test_chapter_chain_repairs_invalid_json_once(self) -> None:
        model = FakeListChatModel(
            responses=["not json", json.dumps(_story_bible(), ensure_ascii=False)]
        )
        chain = build_chapter_analysis_chain(model)

        result = asyncio.run(
            chain.ainvoke("chapter contract", _source_units(), request_id="test-request")
        )

        self.assertEqual(1, result.repair_count)
        self.assertEqual(
            ["林夏"],
            result.story_bible["scenes"][0]["shots"][0]["characters"],
        )

    def test_unknown_visible_character_is_rejected_before_java_persistence(self) -> None:
        document = copy.deepcopy(_story_bible())
        document["scenes"][0]["shots"][0]["characters"] = ["陌生人"]

        with self.assertRaisesRegex(ValueError, "未匹配到 characters"):
            validate_document(document, _source_units())


if __name__ == "__main__":
    unittest.main()
