from __future__ import annotations

import copy
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS = Path.home() / "Downloads"
SOURCE = next(path for path in DOWNLOADS.glob("*.yml") if "(1)" in path.name)
ASCII_OUT = ROOT / "docs" / "dify" / "wuxiaozhi-correction-guidance-two-branch.yml"
ZH_OUT = ROOT / "docs" / "dify" / "物理实验智能纠错与指导-两分支完整版.yml"
RULE_SRC = ROOT / "docs" / "visual-rules" / "newton_rings.md"
RULE_DST = ROOT / "backend" / "src" / "main" / "resources" / "experiments" / "newton_rings" / "visual-rules.md"


INPUT_PARSE_CODE = """def _clean(value):
    if value is None:
        return ""
    return str(value).strip()


def main(query="", image_name="", image_url="", mime_type="", experiment_code="", experiment_type="", category="", step_id="", step_title="", step_desc="", correction_mode="", data_json=""):
    query = _clean(query)
    experiment_code = _clean(experiment_code) or "newton_rings"
    experiment_type = _clean(experiment_type) or "牛顿环实验"
    category = (_clean(category) or "auto").lower()
    correction_mode = (_clean(correction_mode) or "auto").lower()
    step_id = _clean(step_id)
    step_title = _clean(step_title)
    step_desc = _clean(step_desc)
    data_json = _clean(data_json)

    has_image = bool(_clean(image_name) or _clean(image_url))
    mode = "vision" if (has_image or correction_mode == "vision" or category == "vision") else "text"

    if not query:
        if mode == "vision":
            query = "请根据当前实验和步骤，对上传图片进行视觉纠错。"
        elif data_json:
            query = "请根据当前实验和步骤，检查这些实验记录或数据描述是否合理，并给出指导建议。"
        else:
            query = "请结合实验教学知识库回答我的问题。"

    step_part = " ".join([x for x in [step_id, step_title, step_desc] if x])
    data_part = f" 实验记录 {data_json}" if data_json else ""
    retrieval_query_teaching = f"{experiment_type} {experiment_code} {step_part} {query}{data_part} 实验原理 操作步骤 数据处理 注意事项 JCD3 读数显微镜 MKS-NHY"
    retrieval_query_rules = f"{experiment_type} {experiment_code} {step_part} {query}{data_part} 纠错规则 常见错误 evidence fix_instruction JCD3 读数显微镜 牛顿环 半反镜 单向读数"

    return {
        "mode": mode,
        "query": query,
        "experiment_code": experiment_code,
        "experiment_type": experiment_type,
        "category": category,
        "step_id": step_id,
        "step_title": step_title,
        "step_desc": step_desc,
        "data_json": data_json,
        "retrieval_query_teaching": retrieval_query_teaching,
        "retrieval_query_rules": retrieval_query_rules,
    }
"""


TEXT_PROMPT = """你是大学物理实验教学与纠错指导助手，负责回答学生关于实验原理、操作步骤、仪器使用、读数方法、数据记录、数据处理、报告撰写和注意事项的问题。当前请求没有图片，不能声称你看到了现场画面。

当前实验信息：
- 实验名称：{{#1779854194447.experiment_type#}}
- 实验代码：{{#1779854194447.experiment_code#}}
- 当前步骤编号：{{#1779854194447.step_id#}}
- 当前步骤标题：{{#1779854194447.step_title#}}
- 当前步骤说明：{{#1779854194447.step_desc#}}

用户问题：{{#1779854194447.query#}}

实验记录或数据 JSON（可能为空）：
{{#1779854194447.data_json#}}

教学知识库检索结果：
{{#1781000000101.result#}}

纠错规则库检索结果（用于操作错误、仪器使用、读数/记录/计算问题）：
{{#1781000000106.result#}}

回答要求：
1. 优先依据当前实验、当前步骤和检索结果回答。
2. 如果用户提供数据或记录，只把它当作文本材料进行核对；指出字段、单位、数量级、公式或记录逻辑上的疑点，不要要求 Dify 进入单独数据分支。
3. 牛顿环实验重点关注：JCD3 读数显微镜、45 度半反镜、钠光水平入射、环心居中、十字丝相切、沿直径方向测量、单向读数、D_m 和 D_n、Δm、单位换算、R 或 λ 数量级。
4. 没有图片时不要编造可见证据；如必须看现场状态，明确建议学生上传当前步骤照片。
5. 回答要简洁可执行：先给判断，再给 1-3 条下一步操作。
6. 如果检索结果不足以回答，要说明缺少哪些信息。"""


VISION_PROMPT = """你是大学物理实验视觉纠错专家。你的任务是根据用户上传的实验图片，结合当前实验、当前步骤、教学知识库检索结果和纠错规则库检索结果，识别图片中可见的实验操作问题、仪器使用问题、读数/记录问题，并只输出 JSON 文本。

当前实验信息：
- 实验名称：{{#1779854194447.experiment_type#}}
- 实验代码：{{#1779854194447.experiment_code#}}
- 当前步骤编号：{{#1779854194447.step_id#}}
- 当前步骤标题：{{#1779854194447.step_title#}}
- 当前步骤说明：{{#1779854194447.step_desc#}}

用户问题：{{#1779854194447.query#}}

纠错规则库检索结果（主依据）：
{{#1781000000102.result#}}

教学知识库检索结果（辅助背景）：
{{#1781000000103.result#}}

牛顿环 / MKS-NHY / JCD3 重点检查：
- 45 度半反镜是否位于钠光灯与牛顿环之间，光线是否合理照向牛顿环。
- 牛顿环中心接触点是否在镜筒中央附近，十字丝是否与环心或目标暗环正确对准。
- 环纹、十字丝、标尺、测微鼓轮、示教屏是否清晰可读。
- 测量方向是否沿环的直径方向，是否存在把弦长当直径的风险。
- 读数动作是否有中途返向风险；若只能看到静态照片且无法确认，不要臆测。
- 底座大反光镜是否产生杂散反光；光学表面是否有明显灰尘、污物、手触镜面。

判断规则：
1. 必须优先依据当前步骤判断，不要泛化到无关实验或无关步骤。
2. 只标注图片中确实可见的问题，不要臆测；无法判断的项目不要判错。
3. 如果图片与当前实验或当前步骤明显无关，is_step_related=false，issues 和 regions 返回空数组，并在 summary 说明原因。
4. 如果没有明显问题，issues 和 regions 返回空数组。
5. 每个问题都必须给出 evidence，说明你在图片中看到了什么。
6. severity 只能是“高”“中”“低”。
7. confidence 范围是 0 到 1；静态图片无法确认动作过程时，置信度不要超过 0.65。
8. regions 使用 0-1000 归一化坐标，左上角为 (0,0)，右下角为 (1000,1000)。坐标框要贴近错误元素，不要框整张图。
9. 如果问题无法定位到具体区域，可以写入 issues，但不要生成 region；此时 issue 的 region_ref 设为 null。
10. issue_id 优先使用纠错规则库中的 rule_id；没有匹配规则时使用“实验代码_STEP步骤号_简短英文问题”的格式。
11. 不要输出 Markdown，不要输出解释文字，不要把 JSON 包在代码块中。

输出 JSON 格式：
{
  "experiment": "{{#1779854194447.experiment_type#}}",
  "experiment_code": "{{#1779854194447.experiment_code#}}",
  "step_id": "{{#1779854194447.step_id#}}",
  "step_title": "{{#1779854194447.step_title#}}",
  "is_step_related": true,
  "summary": "一句话概括图片内容和总体判断",
  "issues": [
    {
      "issue_id": "NR_STEP2_CENTER_OFF",
      "error_type": "环心定位",
      "severity": "高",
      "description": "十字丝交点未与牛顿环中心重合",
      "evidence": "图片中环心明显位于十字丝交点右下方",
      "fix_instruction": "先粗调载物台将环心移至视场中央，再微调显微镜使十字丝交点接近环心",
      "confidence": 0.86,
      "region_ref": 1
    }
  ],
  "regions": [
    {
      "topLeftX": 420,
      "topLeftY": 360,
      "bottomRightX": 560,
      "bottomRightY": 500,
      "label": "环心偏离十字丝"
    }
  ],
  "next_action": "建议学生重新调节载物台和显微镜，使环心居中后再记录 D0"
}"""


EDGES = [
    ("1779853028414-source-1779854194447-target", "1779853028414", "source", "1779854194447", "start", "code"),
    ("1779854194447-source-1779857861794-target", "1779854194447", "source", "1779857861794", "code", "if-else"),
    ("1779857861794-false-1781000000101-target", "1779857861794", "false", "1781000000101", "if-else", "knowledge-retrieval"),
    ("1781000000101-source-1781000000106-target", "1781000000101", "source", "1781000000106", "knowledge-retrieval", "knowledge-retrieval"),
    ("1781000000106-source-1779858308963-target", "1781000000106", "source", "1779858308963", "knowledge-retrieval", "llm"),
    ("1779858308963-source-1779858605374-target", "1779858308963", "source", "1779858605374", "llm", "answer"),
    ("1779857861794-vision-1781000000102-target", "1779857861794", "vision", "1781000000102", "if-else", "knowledge-retrieval"),
    ("1781000000102-source-1781000000103-target", "1781000000102", "source", "1781000000103", "knowledge-retrieval", "knowledge-retrieval"),
    ("1781000000103-source-1779858136115-target", "1781000000103", "source", "1779858136115", "knowledge-retrieval", "llm"),
    ("1779858136115-source-1781000000001-target", "1779858136115", "source", "1781000000001", "llm", "code"),
    ("1781000000001-source-1779960945383-target", "1781000000001", "source", "1779960945383", "code", "parameter-extractor"),
    ("1779960945383-source-1779858712451-target", "1779960945383", "source", "1779858712451", "parameter-extractor", "llm"),
    ("1779858712451-source-1779859794336-target", "1779858712451", "source", "1779859794336", "llm", "answer"),
]


def edge(edge_id: str, source: str, handle: str, target: str, source_type: str, target_type: str) -> dict:
    return {
        "id": edge_id,
        "source": source,
        "sourceHandle": handle,
        "target": target,
        "targetHandle": "target",
        "type": "custom",
        "data": {
            "isInIteration": False,
            "sourceType": source_type,
            "targetType": target_type,
        },
        "zIndex": 0,
    }


def main() -> None:
    RULE_DST.write_text(RULE_SRC.read_text(encoding="utf-8"), encoding="utf-8")

    data = yaml.safe_load(SOURCE.read_text(encoding="utf-8"))
    data["app"]["name"] = "物理实验智能纠错与指导-两分支完整版"
    data["app"]["description"] = "按物小智后端契约整理：有图片走视觉纠错，无图片统一走文本指导；含教学知识库与纠错规则库检索节点。导入后请绑定知识库。"

    features = data["workflow"]["features"]
    features["opening_statement"] = "你好，我是物小智实验台助教。你可以直接提问实验原理、操作步骤、数据记录和报告问题；也可以上传当前步骤图片，我会结合规则库做视觉纠错并返回标注区域。"
    features["suggested_questions"] = [
        "牛顿环实验里为什么要保持同一方向读数？",
        "我上传一张牛顿环视场照片，请帮我判断环心和十字丝是否对准。",
        "暗环直径测量时怎样避免把弦长当成直径？",
    ]
    file_upload = features["file_upload"]
    file_upload["allowed_file_extensions"] = [".JPG", ".JPEG", ".PNG", ".WEBP"]
    file_upload["allowed_file_types"] = ["image"]
    file_upload["allowed_file_upload_methods"] = ["local_file", "remote_url"]
    file_upload["enabled"] = True
    file_upload["image"] = {"enabled": True, "number_limits": 3, "transfer_methods": ["local_file", "remote_url"]}
    file_upload["number_limits"] = 3

    graph = data["workflow"]["graph"]
    nodes = graph["nodes"]
    node_by_id = {node["id"]: node for node in nodes}

    start_vars = node_by_id["1779853028414"]["data"]["variables"]
    node_by_id["1779853028414"]["data"]["variables"] = [
        v for v in start_vars if v.get("variable") != "query"
    ]
    parse_vars = []
    for item in node_by_id["1779854194447"]["data"]["variables"]:
        if item.get("variable") == "query":
            parse_vars.append({"variable": "query", "value_selector": ["sys", "query"]})
        else:
            parse_vars.append(item)
    node_by_id["1779854194447"]["data"]["variables"] = parse_vars

    for variable in node_by_id["1779853028414"]["data"]["variables"]:
        if variable.get("variable") == "category":
            variable["options"] = ["auto", "teaching", "vision"]
        if variable.get("variable") == "correction_mode":
            variable["options"] = ["auto", "vision"]

    node_by_id["1779854194447"]["data"]["code"] = INPUT_PARSE_CODE
    node_by_id["1779857861794"]["data"]["cases"] = [
        {
            "id": "vision",
            "case_id": "vision",
            "logical_operator": "and",
            "conditions": [
                {
                    "id": "vision-mode",
                    "varType": "string",
                    "variable_selector": ["1779854194447", "mode"],
                    "comparison_operator": "is",
                    "value": "vision",
                }
            ],
        }
    ]

    text_rules_id = "1781000000106"
    if text_rules_id not in node_by_id:
        text_rules = copy.deepcopy(node_by_id["1781000000102"])
        text_rules["id"] = text_rules_id
        text_rules["data"]["title"] = "知识检索：纠错规则库（文本辅助）"
        text_rules["data"]["desc"] = "用于无图文本指导时召回操作错误、仪器使用、读数方法和数据记录相关纠错规则。导入后请绑定纠错规则库。"
        text_rules["position"] = {"x": 820, "y": 460}
        text_rules["positionAbsolute"] = {"x": 820, "y": 460}
        nodes.append(text_rules)
        node_by_id[text_rules_id] = text_rules

    node_by_id["1779858136115"]["data"]["prompt_template"][0]["text"] = VISION_PROMPT
    node_by_id["1779858308963"]["data"]["prompt_template"][0]["text"] = TEXT_PROMPT
    node_by_id["1779858308963"]["data"]["context"] = {"enabled": False, "variable_selector": []}
    node_by_id["1779859794336"]["data"]["answer"] = "{{#1779858712451.text#}}"

    data_node_ids = {"1781000000002", "1781000000003", "1781000000104", "1781000000105"}
    graph["nodes"] = [node for node in nodes if node["id"] not in data_node_ids]
    graph["edges"] = [edge(*item) for item in EDGES]

    dumped = yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=120)
    if "???" in dumped:
        raise RuntimeError("Generated YAML still contains replacement question marks.")
    for output in (ASCII_OUT, ZH_OUT):
        output.write_text(dumped, encoding="utf-8")
        parsed = yaml.safe_load(output.read_text(encoding="utf-8"))
        assert parsed["app"]["name"] == "物理实验智能纠错与指导-两分支完整版"
    print(f"wrote {ASCII_OUT}")
    print(f"wrote {ZH_OUT}")


if __name__ == "__main__":
    main()
