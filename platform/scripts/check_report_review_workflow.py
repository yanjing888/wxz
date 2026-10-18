"""Offline validation of the importable report-review Workflow and scoring code."""
import json
import unittest

import yaml

from report_review_workflow import OUTPUTS, build, validate


class ReportReviewWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dsl = yaml.safe_load(OUTPUTS[0].read_text(encoding="utf-8"))
        cls.nodes = {node["id"]: node["data"] for node in cls.dsl["workflow"]["graph"]["nodes"]}

    def run_code(self, node, **inputs):
        namespace = {}
        exec(self.nodes[node]["code"], namespace)
        result = namespace["main"](**inputs)
        self.assertEqual(set(result), set(self.nodes[node]["outputs"]))
        return result

    def scores(self, values):
        return dict(zip(("completeness_json", "data_json", "error_json", "conclusion_json", "think_json"),
                        (json.dumps({"score": value, "comment": "维度评语", "issues": []}) for value in values)))

    def test_only_two_end_outputs_and_no_chat_dependencies(self):
        validate(self.dsl)
        self.assertEqual(self.dsl["app"]["mode"], "workflow")
        ends = [node for node in self.nodes.values() if node["type"] == "end"]
        self.assertEqual(len(ends), 2)
        for node in ends:
            self.assertEqual({output["variable"] for output in node["outputs"]}, {"score", "comment"})
        self.assertEqual(build(self.dsl), self.dsl)
        for path in OUTPUTS:
            self.assertEqual(path.read_bytes(), OUTPUTS[0].read_bytes())

    def test_scores_sum_to_100_without_division(self):
        for scores in ([18, 16, 14, 17, 15], [0] * 5, [20] * 5):
            result = self.run_code("merge", **self.scores(scores))
            self.assertEqual(result["score"], sum(scores))
            self.assertEqual(json.loads(result["merged_json"])["maxScore"], 100)

    def test_invalid_or_missing_dimension_does_not_invent_marks(self):
        for invalid in (None, -1, 21, float("nan"), True, 12.5):
            with self.assertRaises((ValueError, TypeError)):
                self.run_code("merge", **self.scores([invalid, 20, 20, 20, 20]))
        missing = self.scores([20] * 5)
        missing["data_json"] = "not-json"
        with self.assertRaises(ValueError):
            self.run_code("merge", **missing)

    def test_comment_model_cannot_change_aggregated_score(self):
        result = self.run_code("format", score=80, final_text='{"score":100,"comment":"数据处理完整，误差分析需要补充。"}')
        self.assertEqual(result, {"score": 80.0, "comment": "数据处理完整，误差分析需要补充。"})
        for text in ('{"comment":""}', '{"comment":null}', '[]'):
            with self.assertRaises(ValueError):
                self.run_code("format", score=80, final_text=text)

    def test_short_report_has_same_output_contract(self):
        result = self.run_code("empty", experiment_name="牛顿环", student_name="同学", word_count=12)
        self.assertEqual(set(result), {"score", "comment"})
        self.assertEqual(result["score"], 0)
        self.assertIn("教师确认", result["comment"])

    def test_file_text_and_required_inputs(self):
        variables = {field["variable"]: field for field in self.nodes["start"]["variables"]}
        self.assertTrue(variables["student_report_file"]["required"])
        self.assertTrue(variables["experiment_spec"]["required"])
        result = self.run_code("parse", student_report="<p>测量 &lt; 10 mm</p>", experiment_spec="观察干涉环")
        self.assertEqual(result["student_report"], "测量 < 10 mm")
        self.assertEqual(result["experiment_guide_text"], "观察干涉环")


if __name__ == "__main__":
    unittest.main()
