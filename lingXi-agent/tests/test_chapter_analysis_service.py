from __future__ import annotations

import unittest

from app.services.chapter_analysis import validate_document
from app.services.chapter_analysis import PROMPT_VERSION
from tests.chapter_fixtures import cloned_story_bible, source_units


class ChapterAnalysisContractTests(unittest.TestCase):
    @staticmethod
    def _add_characters(document: dict, names: list[str]) -> None:
        for name in names:
            document["characters"].append(
                {
                    "name": name,
                    "aliases": [],
                    "gender": "未知",
                    "ageRange": "20-30",
                    "appearance": f"{name}的稳定外观",
                    "personality": "",
                    "speakingStyle": "",
                    "visualPromptBase": f"consistent identity design for character {name}",
                }
            )

    def test_missing_dialogue_id_is_inferred_by_unique_speaker_and_line(self) -> None:
        document = cloned_story_bible()
        document["scenes"][0]["shots"][1]["dialogues"] = [
            {"speaker": "林夏", "line": "你好。"}
        ]

        result = validate_document(document, source_units())

        dialogue = result["scenes"][0]["shots"][1]["dialogues"][0]
        self.assertEqual("S1D1", dialogue["dialogueId"])
        self.assertTrue(result["scenes"][0]["shots"][1]["dialogueReferenceInferred"])

    def test_only_remaining_dialogue_is_bound_when_model_omits_usable_reference(self) -> None:
        document = cloned_story_bible()
        shot = document["scenes"][0]["shots"][1]
        shot["dialogues"] = [{"line": "模型输出了无法精确匹配的略写对白"}]

        result = validate_document(document, source_units())

        normalized_shot = result["scenes"][0]["shots"][1]
        self.assertEqual("S1D1", normalized_shot["dialogues"][0]["dialogueId"])
        self.assertTrue(normalized_shot["dialogueReferenceInferred"])
        self.assertTrue(normalized_shot["dialogueReferenceInferredFromOnlyRemaining"])
        self.assertEqual(
            "模型输出了无法精确匹配的略写对白",
            normalized_shot["modelDeclaredDialogue"]["line"],
        )

    def test_multiple_remaining_dialogues_fall_back_to_narrative_order(self) -> None:
        document = cloned_story_bible()
        document["scenes"][0]["dialogues"].append(
            {
                "dialogueId": "model-dialogue-2",
                "speaker": "陈默",
                "line": "早上好。",
                "emotion": "平静",
                "action": "回应",
            }
        )
        document["scenes"][0]["shots"][0]["dialogues"] = [
            {"line": "无法匹配任何候选对白"}
        ]
        document["scenes"][0]["shots"][1]["dialogues"] = [
            {"dialogueId": "model-dialogue-2"}
        ]

        result = validate_document(document, source_units())

        first_shot = result["scenes"][0]["shots"][0]
        second_shot = result["scenes"][0]["shots"][1]
        self.assertEqual("S1D1", first_shot["dialogues"][0]["dialogueId"])
        self.assertEqual("S1D2", second_shot["dialogues"][0]["dialogueId"])
        self.assertTrue(first_shot["dialogueReferenceInferredByOrder"])

    def test_explicit_unknown_dialogue_id_fails(self) -> None:
        document = cloned_story_bible()
        document["scenes"][0]["shots"][1]["dialogues"] = [
            {"dialogueId": "does-not-exist", "speaker": "林夏", "line": "你好。"}
        ]

        with self.assertRaisesRegex(ValueError, "不属于当前场景"):
            validate_document(document, source_units())

    def test_ambiguous_line_without_id_fails(self) -> None:
        document = cloned_story_bible()
        document["scenes"][0]["dialogues"].append(
            {
                "dialogueId": "model-dialogue-2",
                "speaker": "陈默",
                "line": "你好。",
                "emotion": "平静",
                "action": "回应",
            }
        )
        document["scenes"][0]["shots"][1]["dialogues"] = [{"line": "你好。"}]

        with self.assertRaisesRegex(ValueError, "line 匹配到多句"):
            validate_document(document, source_units())

    def test_complete_shot_dialogue_repairs_missing_scene_dialogue(self) -> None:
        document = cloned_story_bible()
        scene = document["scenes"][0]
        scene["dialogues"] = []
        scene["shots"][1]["dialogues"] = [
            {
                "speaker": "林夏",
                "line": "你好。",
                "emotion": "友好",
                "action": "点头",
            }
        ]

        result = validate_document(document, source_units())

        inferred = result["scenes"][0]["dialogues"][0]
        self.assertEqual("S1D1", inferred["dialogueId"])
        self.assertTrue(inferred["inferredFromShot"])

    def test_inferred_scene_dialogue_requires_emotion_and_action(self) -> None:
        document = cloned_story_bible()
        scene = document["scenes"][0]
        scene["dialogues"] = []
        scene["shots"][1]["dialogues"] = [
            {"speaker": "林夏", "line": "你好。", "emotion": "", "action": ""}
        ]

        with self.assertRaisesRegex(ValueError, "缺少 emotion"):
            validate_document(document, source_units())

    def test_unassigned_scene_dialogue_fails(self) -> None:
        document = cloned_story_bible()
        document["scenes"][0]["shots"][1]["dialogues"] = []

        with self.assertRaisesRegex(ValueError, "每句对白必须恰好分配"):
            validate_document(document, source_units())

    def test_multiple_dialogues_in_one_shot_are_preserved(self) -> None:
        document = cloned_story_bible()
        scene = document["scenes"][0]
        scene["dialogues"].append(
            {
                "dialogueId": "model-dialogue-2",
                "speaker": "陈默",
                "line": "欢迎。",
                "emotion": "友好",
                "action": "回应",
            }
        )
        scene["shots"][1]["dialogues"] = [
            {"dialogueId": "model-dialogue-1"},
            {"dialogueId": "model-dialogue-2"},
        ]

        result = validate_document(document, source_units())

        normalized_shot = result["scenes"][0]["shots"][1]
        self.assertEqual(
            ["S1D1", "S1D2"],
            [dialogue["dialogueId"] for dialogue in normalized_shot["dialogues"]],
        )
        self.assertEqual(["林夏", "陈默"], normalized_shot["characterReferenceOrder"])

    def test_dialogue_duration_is_advisory(self) -> None:
        document = cloned_story_bible()
        long_line = "这句对白即使按照粗略语速估算超过镜头时长也不应导致整章分析失败"
        document["scenes"][0]["dialogues"][0]["line"] = long_line

        result = validate_document(document, source_units())

        self.assertEqual(long_line, result["scenes"][0]["shots"][1]["dialogues"][0]["line"])

    def test_shot_duration_is_normalized_for_configured_video_model(self) -> None:
        document = cloned_story_bible()

        result = validate_document(
            document,
            source_units(),
            video_model="wanx2.2-i2v-plus",
        )

        shots = result["scenes"][0]["shots"]
        self.assertEqual([5000, 5000], [shot["durationMs"] for shot in shots])
        self.assertEqual([3000, 3000], [shot["modelDeclaredDurationMs"] for shot in shots])
        self.assertEqual(10000, result["videoPlan"]["estimatedTotalDurationMs"])

    def test_four_visible_characters_are_allowed_but_five_fail(self) -> None:
        document = cloned_story_bible()
        four_names = ["甲", "乙", "丙", "丁"]
        self._add_characters(document, four_names)
        document["scenes"][0]["shots"][0]["characters"] = four_names
        validate_document(document, source_units())

        document = cloned_story_bible()
        five_names = ["甲", "乙", "丙", "丁", "戊"]
        self._add_characters(document, five_names)
        document["scenes"][0]["shots"][0]["characters"] = five_names
        with self.assertRaisesRegex(ValueError, "超过4人"):
            validate_document(document, source_units())

    def test_asset_prompts_are_finalized_and_model_values_are_audited(self) -> None:
        result = validate_document(cloned_story_bible(), source_units())

        self.assertEqual(PROMPT_VERSION, result["promptVersion"])

        character = result["characters"][0]
        self.assertIn("Three-view full-body turnaround", character["characterReferencePrompt"])
        self.assertIn("front view", character["characterReferencePrompt"])
        self.assertIn("side view", character["characterReferencePrompt"])
        self.assertIn("back view", character["characterReferencePrompt"])
        self.assertEqual(
            "Lin Xia identity design with a blue coat",
            character["modelDeclaredCharacterReferencePrompt"],
        )

        scene = result["scenes"][0]
        self.assertIn("no people", scene["sceneImagePrompt"])
        self.assertIn("people", scene["sceneImageNegativePrompt"])
        self.assertEqual(
            "A quiet modern room in daylight with cool neutral colors",
            scene["modelDeclaredSceneImagePrompt"],
        )

        keyframe = scene["shots"][1]
        self.assertEqual(PROMPT_VERSION, keyframe["promptContractVersion"])
        self.assertIn("Reference image 1: identity reference for 林夏", keyframe["keyframePrompt"])
        self.assertIn("Reference image 2: identity reference for 陈默", keyframe["keyframePrompt"])
        self.assertIn("Reference image 3: scene environment", keyframe["keyframePrompt"])
        self.assertIn("not a character turnaround sheet", keyframe["keyframePrompt"])
        self.assertIn("multiple panels", keyframe["imageNegativePrompt"])
        self.assertIn("modelDeclaredKeyframePrompt", keyframe)

    def test_reference_order_includes_dialogue_speaker_after_visible_characters(self) -> None:
        document = cloned_story_bible()
        shot = document["scenes"][0]["shots"][1]
        shot["characters"] = ["陈默"]

        result = validate_document(document, source_units())
        normalized_shot = result["scenes"][0]["shots"][1]

        self.assertEqual(["陈默", "林夏"], normalized_shot["characterReferenceOrder"])
        self.assertIn("Reference image 1: identity reference for 陈默", normalized_shot["keyframePrompt"])
        self.assertIn("Reference image 2: identity reference for 林夏", normalized_shot["keyframePrompt"])
        self.assertIn("Reference image 3: scene environment", normalized_shot["keyframePrompt"])
        self.assertIn("Visible characters: 陈默.", normalized_shot["keyframePrompt"])

    def test_named_visible_character_missing_from_plan_is_inferred(self) -> None:
        document = cloned_story_bible()
        shot = document["scenes"][0]["shots"][0]
        shot["characters"] = ["林夏", "周元"]

        result = validate_document(document, source_units())

        inferred = next(
            character
            for character in result["characters"]
            if character["name"] == "周元"
        )
        normalized_shot = result["scenes"][0]["shots"][0]
        self.assertTrue(inferred["inferredFromSceneReferences"])
        self.assertTrue(inferred["visualPromptBase"])
        self.assertIn("Professional character reference sheet", inferred["characterReferencePrompt"])
        self.assertEqual(["林夏", "周元"], normalized_shot["characters"])
        self.assertEqual(["林夏", "周元"], normalized_shot["characterReferenceOrder"])

    def test_missing_character_and_scene_prompts_use_deterministic_fallbacks(self) -> None:
        document = cloned_story_bible()
        document["characters"][0].pop("characterReferencePrompt", None)
        document["characters"][0].pop("characterReferenceNegativePrompt", None)
        scene = document["scenes"][0]
        scene.pop("sceneImagePrompt", None)
        scene.pop("sceneImageNegativePrompt", None)

        result = validate_document(document, source_units())

        self.assertIn("oval face", result["characters"][0]["characterReferencePrompt"])
        self.assertIn("location: 房间", result["scenes"][0]["sceneImagePrompt"])
        self.assertEqual("", result["scenes"][0]["modelDeclaredSceneImagePrompt"])

    def test_asset_prompt_finalizer_is_idempotent(self) -> None:
        result = validate_document(cloned_story_bible(), source_units())
        first_character_prompt = result["characters"][0]["characterReferencePrompt"]
        first_scene_prompt = result["scenes"][0]["sceneImagePrompt"]
        first_keyframe_prompt = result["scenes"][0]["shots"][1]["keyframePrompt"]

        validate_document(result, source_units())

        self.assertEqual(first_character_prompt, result["characters"][0]["characterReferencePrompt"])
        self.assertEqual(first_scene_prompt, result["scenes"][0]["sceneImagePrompt"])
        self.assertEqual(first_keyframe_prompt, result["scenes"][0]["shots"][1]["keyframePrompt"])


if __name__ == "__main__":
    unittest.main()
