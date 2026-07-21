"""Small valid story-bible fixtures shared by offline tests."""

from __future__ import annotations

from copy import deepcopy
from typing import Any

from app.services.chapter_analysis import SourceUnit


def source_units() -> list[SourceUnit]:
    return [
        SourceUnit(id="U1", order=1, paragraph_no=1, text="林夏走进房间。"),
        SourceUnit(id="U2", order=2, paragraph_no=1, text="她向陈默问好。"),
    ]


def shot(source_unit_id: str, characters: list[str] | None = None) -> dict[str, Any]:
    return {
        "shotNo": 1,
        "durationMs": 3000,
        "sourceUnitIds": [source_unit_id],
        "characters": characters if characters is not None else ["林夏"],
        "narrativeBeat": "人物进入空间",
        "shotSize": "medium shot",
        "cameraMovement": "static camera",
        "composition": "balanced composition",
        "action": "林夏走进房间",
        "emotion": "平静",
        "dialogues": [],
        "keyframePrompt": "Lin Xia entering a quiet room, medium shot",
        "imageNegativePrompt": "text, watermark, distorted anatomy",
        "videoPrompt": "Lin Xia walks into the room at a natural pace while the camera remains static.",
        "videoNegativePrompt": "flicker, face change, costume change, subtitles, watermark",
    }


def story_bible() -> dict[str, Any]:
    first_shot = shot("U1")
    second_shot = shot("U2", ["林夏", "陈默"])
    second_shot["shotNo"] = 2
    second_shot["narrativeBeat"] = "林夏向陈默问好"
    second_shot["dialogues"] = [{"dialogueId": "model-dialogue-1"}]
    return {
        "summary": "林夏进入房间并向陈默问好。",
        "worldSetting": "现代室内空间",
        "timeline": ["林夏进入房间", "林夏问好"],
        "relationships": [],
        "immutableFacts": ["林夏与陈默在房间见面"],
        "videoPlan": {
            "sourceUnitCount": 2,
            "minimumShotCount": 2,
            "shotCount": 2,
            "estimatedTotalDurationMs": 6000,
            "segmentationRationale": "按进入动作和对白拆为两个镜头",
        },
        "characters": [
            {
                "name": "林夏",
                "aliases": [],
                "gender": "女",
                "ageRange": "20-30",
                "appearance": "黑色长发",
                "personality": "沉稳",
                "speakingStyle": "简洁",
                "visualPromptBase": "oval face, black long hair, slim build, blue coat",
                "characterReferencePrompt": "Lin Xia identity design with a blue coat",
                "characterReferenceNegativePrompt": "different coat, different face",
            },
            {
                "name": "陈默",
                "aliases": [],
                "gender": "男",
                "ageRange": "20-30",
                "appearance": "短发",
                "personality": "安静",
                "speakingStyle": "温和",
                "visualPromptBase": "square face, short black hair, average build, grey shirt",
            },
        ],
        "scenes": [
            {
                "sceneNo": 1,
                "title": "房间会面",
                "time": "白天",
                "location": "房间",
                "atmosphere": "安静",
                "dramaticGoal": "建立两人会面",
                "characters": ["林夏", "陈默"],
                "sceneImagePrompt": "A quiet modern room in daylight with cool neutral colors",
                "sceneImageNegativePrompt": "crowd, person",
                "dialogues": [
                    {
                        "dialogueId": "model-dialogue-1",
                        "speaker": "林夏",
                        "line": "你好。",
                        "emotion": "友好",
                        "action": "点头",
                    }
                ],
                "shots": [first_shot, second_shot],
            }
        ],
    }


def cloned_story_bible() -> dict[str, Any]:
    return deepcopy(story_bible())


def chapter_plan() -> dict[str, Any]:
    bible = story_bible()
    return {
        "summary": bible["summary"],
        "worldSetting": bible["worldSetting"],
        "timeline": bible["timeline"],
        "relationships": bible["relationships"],
        "immutableFacts": bible["immutableFacts"],
        "segmentationRationale": bible["videoPlan"]["segmentationRationale"],
        "characters": bible["characters"],
        "sceneBreaks": ["U2"],
    }


def generated_scene() -> dict[str, Any]:
    return deepcopy(story_bible()["scenes"][0])
