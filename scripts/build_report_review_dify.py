"""Generate the teacher-side report pre-grade Chatflow DSL for Dify."""
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
OUT_ZH = ROOT / "docs" / "dify" / "物小智-教师报告预评.yml"
OUT_ASCII = ROOT / "docs" / "dify" / "wuxiaozhi-report-review.yml"

MODEL = {
    "provider": "langgenius/tongyi/tongyi",
    "name": "qwen-plus",
    "mode": "chat",
    "completion_params": {"temperature": 0.15},
}
MODEL_COMMENT = {
    "provider": "langgenius/tongyi/tongyi",
    "name": "qwen-plus",
    "mode": "chat",
    "completion_params": {"temperature": 0.25},
}


def node(node_id, title, node_type, x, y, **data):
    payload = {
        "id": str(node_id),
        "type": "custom",
        "width": 244,
        "height": 54,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": title,
            "type": node_type,
            "selected": False,
        },
    }
    payload["data"].update(data)
    return payload


def edge(edge_id, source, target, source_type, target_type, handle="source"):
    return {
        "id": edge_id,
        "source": str(source),
        "sourceHandle": handle,
        "target": str(target),
        "targetHandle": "target",
        "type": "custom",
        "data": {
            "isInIteration": False,
            "sourceType": source_type,
            "targetType": target_type,
        },
        "zIndex": 0,
    }


def llm_prompt(text):
    return [{"id": "system-prompt", "role": "system", "text": text}]


def start_var(label, variable, var_type="paragraph", required=False, max_length=8000):
    item = {
        "label": label,
        "variable": variable,
        "type": var_type,
        "required": required,
        "options": [],
    }
    if var_type in ("paragraph", "text-input"):
        item["max_length"] = max_length
    return item


PARSE_CODE = r'''import json
import re


def _clean(v):
    if v is None:
        return ""
    return str(v).strip()


def _strip_html(text):
    if not text:
        return ""
    s = str(text)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|h[1-6]|li|tr|table)>", "\n", s)
    s = re.sub(r"(?i)<li[^>]*>", "- ", s)
    s = re.sub(r"<[^>]+>", " ", s)
    s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def _sections_to_report(raw, experiment_name):
    obj = raw
    if isinstance(raw, str) and raw.strip():
        try:
            obj = json.loads(raw)
        except Exception:
            return _strip_html(raw)
    if not isinstance(obj, dict):
        return ""
    inner = obj.get("sections") if isinstance(obj.get("sections"), dict) else obj
    labels = [
        ("purpose", "实验目的"),
        ("principle", "实验原理"),
        ("apparatus", "实验仪器"),
        ("procedure", "实验步骤"),
        ("data", "数据与处理"),
        ("results", "实验结果"),
        ("discussion", "分析与讨论"),
    ]
    parts = []
    if experiment_name:
        parts.append(experiment_name + " — 实验报告")
    for key, label in labels:
        body = _strip_html(inner.get(key) or "")
        if body:
            parts.append("## " + label + "\n\n" + body)
    return "\n\n".join(parts).strip()


def main(query="", student_report="", student_report_sections="", experiment_guide="",
         experiment_name="", experiment_code="", student_name="", student_class="",
         data_logs_json="", corrections_json="", step_summaries_json="",
         help_count="", error_point_count=""):
    name = _clean(experiment_name) or "大学物理实验"
    report = _strip_html(_clean(student_report))
    if not report:
        report = _sections_to_report(student_report_sections, name)
    guide = _strip_html(_clean(experiment_guide))[:12000]
    report = report[:12000]
    return {
        "query": _clean(query) or "请作为大学物理实验教师，对学生实验报告做预评（非终裁）。",
        "experiment_name": name,
        "experiment_code": _clean(experiment_code),
        "student_name": _clean(student_name),
        "student_class": _clean(student_class),
        "student_report": report,
        "experiment_guide_text": guide,
        "data_logs_json": _clean(data_logs_json)[:8000],
        "corrections_json": _clean(corrections_json)[:4000],
        "step_summaries_json": _clean(step_summaries_json)[:4000],
        "help_count": _clean(help_count) or "0",
        "error_point_count": _clean(error_point_count) or "0",
    }
'''


METRICS_CODE = r'''import json
import re


def _safe_json(value, default):
    if not value:
        return default
    try:
        return json.loads(value) if isinstance(value, str) else value
    except Exception:
        return default


def _to_int(v):
    try:
        return int(float(str(v).strip() or "0"))
    except Exception:
        return 0


def main(student_report="", experiment_guide_text="", data_logs_json="",
         corrections_json="", help_count="", error_point_count=""):
    text = student_report or ""
    guide = experiment_guide_text or ""
    data_logs = _safe_json(data_logs_json, [])
    corrections = _safe_json(corrections_json, [])
    has_uncertainty = bool(re.search(r"不确定|误差|标准差|相对|±|\+/-", text))
    has_unit = bool(re.search(r"(mm|cm|m|nm|N|kN|MPa|Pa|kg|g|s|V|A|Ω|ohm|℃)", text, re.I))
    word_count = len(re.sub(r"\s+", "", text))
    section_count = len(re.findall(r"^##\s+", text, re.M))
    filled_keys = []
    for key, label in [
        ("purpose", "实验目的"),
        ("principle", "实验原理"),
        ("apparatus", "实验仪器"),
        ("procedure", "实验步骤"),
        ("data", "数据与处理"),
        ("results", "实验结果"),
        ("discussion", "分析与讨论"),
    ]:
        if label in text:
            filled_keys.append(label)
    risk = []
    if word_count < 180:
        risk.append("正文偏短")
    if not has_uncertainty:
        risk.append("缺少不确定度/误差分析")
    if not has_unit:
        risk.append("单位痕迹不足")
    if not data_logs:
        risk.append("没有结构化过程数据")
    if not guide:
        risk.append("未收到实验指导书")
    if section_count < 4:
        risk.append("章节覆盖不足")
    if _to_int(error_point_count) >= 3:
        risk.append("实验过程纠错次数较多")
    return {
        "word_count": word_count,
        "section_count": section_count,
        "filled_sections": "、".join(filled_keys) if filled_keys else "未识别到标准章节",
        "data_log_count": len(data_logs) if isinstance(data_logs, list) else 0,
        "correction_count": len(corrections) if isinstance(corrections, list) else 0,
        "help_count": _to_int(help_count),
        "error_point_count": _to_int(error_point_count),
        "has_uncertainty": has_uncertainty,
        "has_unit": has_unit,
        "risk_flags": "；".join(risk) if risk else "未发现明显硬性风险",
    }
'''


JSON_ONLY = """
输出约束（必须遵守）：
- 只输出一个 JSON 对象，不要 Markdown，不要代码块，不要解释性前后文。
- score 必须是 0 到 20 的整数。
- comment 用中文，2-5 句，具体指出报告里出现或缺失的内容，禁止空套话。
- issues 为字符串数组，没有问题则给空数组。
- 只根据学生已写内容和过程数据判断，不得替学生补写未出现的测量值，不得编造数据。
- 这是预评，不是终裁。
"""


def dimension_prompt(title, score_hint, extra):
    return f"""你是大学物理实验报告预评教师，只负责「{title}」这一维打分。

实验：{{{{#parse.experiment_name#}}}}
学生：{{{{#parse.student_name#}}}}（{{{{#parse.student_class#}}}}）

## 实验指导书（标准参照）
{{{{#parse.experiment_guide_text#}}}}

## 学生实验报告
{{{{#parse.student_report#}}}}

## 实验过程数据
{{{{#parse.data_logs_json#}}}}

## 过程纠错记录
{{{{#parse.corrections_json#}}}}

客观指标：字数 {{{{#metrics.word_count#}}}}；章节数 {{{{#metrics.section_count#}}}}；已识别章节 {{{{#metrics.filled_sections#}}}}；过程数据条数 {{{{#metrics.data_log_count#}}}}；求助 {{{{#metrics.help_count#}}}}；纠错 {{{{#metrics.error_point_count#}}}}；硬性风险 {{{{#metrics.risk_flags#}}}}

评分要点：
{score_hint}

{extra}

JSON 字段：
{{
  "dimension": "{title}",
  "score": 0,
  "max": 20,
  "comment": "该维评语",
  "issues": []
}}
""" + JSON_ONLY


COMPLETENESS_PROMPT = dimension_prompt(
    "完整性",
    "对照指导书检查实验目的、原理、仪器、步骤、数据与处理、结果、分析与讨论是否齐全。缺章、空章、明显复制提纲扣分。",
    "有过程数据但报告数据章空白，必须在 issues 中指出。",
)
DATA_PROMPT = dimension_prompt(
    "数据可信度",
    "核对报告数据是否与过程记录方向一致、是否有单位、是否像估算/编造、表格是否可读。无数据最高 6 分。",
    "不得把指导书例题数据当成学生数据。",
)
ERROR_PROMPT = dimension_prompt(
    "误差分析",
    "看是否写出误差来源、不确定度或相对误差、数据处理思路是否与本实验匹配。空泛套话扣分。",
    "只评价学生已写的误差/不确定度内容，不要替学生补算。",
)
CONCLUSION_PROMPT = dimension_prompt(
    "结论质量",
    "看结论是否回应实验目的、是否有定量结果、是否与前面数据矛盾。只有一句话且无数据支撑则低分。",
    "结论抄原理、不回答测量问题，issues 中明确写出。",
)
THINK_PROMPT = dimension_prompt(
    "思考题与讨论",
    "看分析与讨论、思考题是否结合本次实验，而不是万能模板。有过程纠错却完全不反思，酌情扣分。",
    "没有讨论章可以给低分，但不要虚构学生没写的思考题答案。",
)


MERGE_CODE = r'''import json
import re


def _extract_obj(text):
    if not text:
        return {}
    s = str(text).strip()
    s = re.sub(r"^```(?:json)?", "", s, flags=re.I).strip()
    s = re.sub(r"```$", "", s).strip()
    try:
        obj = json.loads(s)
        return obj if isinstance(obj, dict) else {}
    except Exception:
        pass
    m = re.search(r"\{[\s\S]*\}", s)
    if not m:
        return {}
    try:
        obj = json.loads(m.group(0))
        return obj if isinstance(obj, dict) else {}
    except Exception:
        return {}


def _score(obj, default=0):
    try:
        n = float(obj.get("score", default))
    except Exception:
        n = default
    if n < 0:
        n = 0
    if n > 20:
        n = 20
    return int(round(n))


def _band(score100):
    if score100 >= 90:
        return "优秀"
    if score100 >= 80:
        return "良好"
    if score100 >= 70:
        return "中等"
    if score100 >= 60:
        return "及格"
    return "不及格"


def main(completeness_json="", data_json="", error_json="", conclusion_json="", think_json="", risk_flags=""):
    items = [
        _extract_obj(completeness_json),
        _extract_obj(data_json),
        _extract_obj(error_json),
        _extract_obj(conclusion_json),
        _extract_obj(think_json),
    ]
    names = ["完整性", "数据可信度", "误差分析", "结论质量", "思考题与讨论"]
    dimensions = []
    total = 0
    issues = []
    for i, obj in enumerate(items):
        name = obj.get("dimension") or names[i]
        score = _score(obj, 0 if obj else 10)
        comment = str(obj.get("comment") or "").strip()
        dim_issues = obj.get("issues") if isinstance(obj.get("issues"), list) else []
        dimensions.append({
            "dimension": name,
            "score": score,
            "max": 20,
            "comment": comment,
            "issues": [str(x) for x in dim_issues if str(x).strip()],
        })
        total += score
        issues.extend([str(x) for x in dim_issues if str(x).strip()])
    if risk_flags and risk_flags != "未发现明显硬性风险":
        issues.append(str(risk_flags))
    score10 = round(total / 10.0, 1)
    merged = {
        "score": score10,
        "score100": total,
        "maxScore": 10,
        "gradeBand": _band(total),
        "dimensions": dimensions,
        "risks": list(dict.fromkeys(issues)),
        "needsTeacherConfirm": True,
    }
    return {
        "score10": score10,
        "score100": total,
        "grade_band": merged["gradeBand"],
        "merged_json": json.dumps(merged, ensure_ascii=False),
        "risk_summary": "；".join(merged["risks"]) if merged["risks"] else "未发现明显风险",
    }
'''


FINAL_PROMPT = """你是大学物理实验课程的阅卷教师，正在写「AI 预评」总评语。分数已经由前序节点按五维加总得出，你不要改分数，只写评语并整理风险。

实验：{{#parse.experiment_name#}}
学生：{{#parse.student_name#}}（{{#parse.student_class#}}）
建议得分（10 分制）：{{#merge.score10#}} / 10
百分制参考：{{#merge.score100#}} / 100
分档：{{#merge.grade_band#}}
硬性风险：{{#merge.risk_summary#}}

## 五维评分 JSON
{{#merge.merged_json#}}

## 学生实验报告（摘要依据）
{{#parse.student_report#}}

写一段 120-220 字的中文总评，面向教师，可直接贴到批改栏：
- 先一句总体印象，再点出 1-2 个优点和 1-2 个必须修改处。
- 明确这是建议分，需教师确认后才算正式成绩。
- 不要出现「作为 AI」「根据指令」「JSON」等元话语。

只输出一个 JSON 对象，不要 Markdown，不要代码块：
{
  "score": <必须等于上面的 10 分制分数，保留一位小数>,
  "score100": <必须等于上面的百分制整数>,
  "maxScore": 10,
  "gradeBand": "<分档>",
  "comment": "<总评语>",
  "dimensions": <原样保留五维数组>,
  "risks": <字符串数组>,
  "needsTeacherConfirm": true
}
"""


FORMAT_CODE = r'''import json
import re


def _extract_obj(text):
    if not text:
        return {}
    s = str(text).strip()
    s = re.sub(r"^```(?:json)?", "", s, flags=re.I).strip()
    s = re.sub(r"```$", "", s).strip()
    try:
        obj = json.loads(s)
        return obj if isinstance(obj, dict) else {}
    except Exception:
        pass
    m = re.search(r"\{[\s\S]*\}", s)
    if not m:
        return {}
    try:
        obj = json.loads(m.group(0))
        return obj if isinstance(obj, dict) else {}
    except Exception:
        return {}


def main(final_text="", merged_json="", score10=0, score100=0, grade_band=""):
    merged = _extract_obj(merged_json)
    final = _extract_obj(final_text)
    out = dict(merged)
    out.update({k: v for k, v in final.items() if v is not None and v != ""})
    try:
        score = float(score10)
    except Exception:
        score = float(out.get("score") or 0)
    try:
        s100 = int(float(score100))
    except Exception:
        s100 = int(out.get("score100") or round(score * 10))
    if score <= 0 and s100 > 0:
        score = round(s100 / 10.0, 1)
    comment = str(out.get("comment") or "").strip()
    if not comment:
        comment = str(final_text or "").strip() or "预评已完成，请教师核验分数与评语后确认。"
    result = {
        "score": round(float(score), 1),
        "score100": s100,
        "maxScore": 10,
        "gradeBand": str(out.get("gradeBand") or grade_band or ""),
        "comment": comment,
        "dimensions": out.get("dimensions") if isinstance(out.get("dimensions"), list) else [],
        "risks": out.get("risks") if isinstance(out.get("risks"), list) else [],
        "needsTeacherConfirm": True,
    }
    return {"result_json": json.dumps(result, ensure_ascii=False)}
'''


def _start_variables():
    return [
        start_var("学生实验报告", "student_report", max_length=12000),
        start_var("各章 JSON", "student_report_sections", max_length=12000),
        start_var("实验指导书", "experiment_guide", max_length=12000),
        start_var("实验名称", "experiment_name", "text-input", max_length=200),
        start_var("实验代码", "experiment_code", "text-input", max_length=80),
        start_var("学生姓名", "student_name", "text-input", max_length=80),
        start_var("班级", "student_class", "text-input", max_length=80),
        start_var("过程数据 JSON", "data_logs_json", max_length=8000),
        start_var("纠错记录 JSON", "corrections_json", max_length=4000),
        start_var("步骤摘要 JSON", "step_summaries_json", max_length=4000),
        start_var("求助次数", "help_count", "text-input", max_length=20),
        start_var("纠错次数", "error_point_count", "text-input", max_length=20),
    ]


def _llm(node_id, title, x, prompt, model=None):
    return node(
        node_id,
        title,
        "llm",
        x,
        300,
        model=model or MODEL,
        prompt_template=llm_prompt(prompt),
        context={"enabled": False, "variable_selector": []},
        vision={"enabled": False},
    )


def build():
    start = node("start", "开始：预评输入", "start", 80, 300, variables=_start_variables())
    parse = node(
        "parse",
        "节点01 输入清洗与报告拼装",
        "code",
        360,
        300,
        code_language="python3",
        code=PARSE_CODE,
        variables=[{"variable": name, "value_selector": ["start", name]} for name in [
            "student_report", "student_report_sections", "experiment_guide",
            "experiment_name", "experiment_code", "student_name", "student_class",
            "data_logs_json", "corrections_json", "step_summaries_json",
            "help_count", "error_point_count",
        ]] + [{"variable": "query", "value_selector": ["sys", "query"]}],
        outputs={key: {"type": "string", "children": None} for key in [
            "query", "experiment_name", "experiment_code", "student_name", "student_class",
            "student_report", "experiment_guide_text", "data_logs_json",
            "corrections_json", "step_summaries_json", "help_count", "error_point_count",
        ]},
    )
    metrics = node(
        "metrics",
        "节点02 硬性指标与风险标记",
        "code",
        640,
        300,
        code_language="python3",
        code=METRICS_CODE,
        variables=[
            {"variable": "student_report", "value_selector": ["parse", "student_report"]},
            {"variable": "experiment_guide_text", "value_selector": ["parse", "experiment_guide_text"]},
            {"variable": "data_logs_json", "value_selector": ["parse", "data_logs_json"]},
            {"variable": "corrections_json", "value_selector": ["parse", "corrections_json"]},
            {"variable": "help_count", "value_selector": ["parse", "help_count"]},
            {"variable": "error_point_count", "value_selector": ["parse", "error_point_count"]},
        ],
        outputs={
            "word_count": {"type": "number", "children": None},
            "section_count": {"type": "number", "children": None},
            "filled_sections": {"type": "string", "children": None},
            "data_log_count": {"type": "number", "children": None},
            "correction_count": {"type": "number", "children": None},
            "help_count": {"type": "number", "children": None},
            "error_point_count": {"type": "number", "children": None},
            "has_uncertainty": {"type": "boolean", "children": None},
            "has_unit": {"type": "boolean", "children": None},
            "risk_flags": {"type": "string", "children": None},
        },
    )
    completeness = _llm("completeness", "节点03 完整性评分", 920, COMPLETENESS_PROMPT)
    data_quality = _llm("data_quality", "节点04 数据可信度评分", 1200, DATA_PROMPT)
    error_analysis = _llm("error_analysis", "节点05 误差分析评分", 1480, ERROR_PROMPT)
    conclusion = _llm("conclusion", "节点06 结论质量评分", 1760, CONCLUSION_PROMPT)
    think = _llm("think", "节点07 思考题与讨论评分", 2040, THINK_PROMPT)
    merge = node(
        "merge",
        "节点08 五维分数汇总",
        "code",
        2320,
        300,
        code_language="python3",
        code=MERGE_CODE,
        variables=[
            {"variable": "completeness_json", "value_selector": ["completeness", "text"]},
            {"variable": "data_json", "value_selector": ["data_quality", "text"]},
            {"variable": "error_json", "value_selector": ["error_analysis", "text"]},
            {"variable": "conclusion_json", "value_selector": ["conclusion", "text"]},
            {"variable": "think_json", "value_selector": ["think", "text"]},
            {"variable": "risk_flags", "value_selector": ["metrics", "risk_flags"]},
        ],
        outputs={
            "score10": {"type": "number", "children": None},
            "score100": {"type": "number", "children": None},
            "grade_band": {"type": "string", "children": None},
            "merged_json": {"type": "string", "children": None},
            "risk_summary": {"type": "string", "children": None},
        },
    )
    final = _llm("final", "节点09 总评语生成", 2600, FINAL_PROMPT, MODEL_COMMENT)
    fmt = node(
        "format",
        "节点10 结构化输出校验",
        "code",
        2880,
        300,
        code_language="python3",
        code=FORMAT_CODE,
        variables=[
            {"variable": "final_text", "value_selector": ["final", "text"]},
            {"variable": "merged_json", "value_selector": ["merge", "merged_json"]},
            {"variable": "score10", "value_selector": ["merge", "score10"]},
            {"variable": "score100", "value_selector": ["merge", "score100"]},
            {"variable": "grade_band", "value_selector": ["merge", "grade_band"]},
        ],
        outputs={"result_json": {"type": "string", "children": None}},
    )
    answer = node("answer", "回答：分数与评语 JSON", "answer", 3160, 300, answer="{{#format.result_json#}}", variables=[])

    nodes = [start, parse, metrics, completeness, data_quality, error_analysis, conclusion, think, merge, final, fmt, answer]
    edges = [
        edge("e01", "start", "parse", "start", "code"),
        edge("e02", "parse", "metrics", "code", "code"),
        edge("e03", "metrics", "completeness", "code", "llm"),
        edge("e04", "completeness", "data_quality", "llm", "llm"),
        edge("e05", "data_quality", "error_analysis", "llm", "llm"),
        edge("e06", "error_analysis", "conclusion", "llm", "llm"),
        edge("e07", "conclusion", "think", "llm", "llm"),
        edge("e08", "think", "merge", "llm", "code"),
        edge("e09", "merge", "final", "code", "llm"),
        edge("e10", "final", "format", "llm", "code"),
        edge("e11", "format", "answer", "code", "answer"),
    ]
    return {
        "app": {
            "name": "物小智-教师报告预评",
            "description": "教师端 report-review：清洗报告与过程数据后，按完整性、数据可信度、误差分析、结论、思考题五维分别评分，再汇总建议分与总评语。输出结构化 JSON，供教师确认后落档。",
            "mode": "advanced-chat",
            "icon_type": "emoji",
            "icon": "📝",
            "icon_background": "#EEF2FF",
            "use_icon_as_answer_icon": False,
        },
        "kind": "app",
        "version": "0.6.0",
        "workflow": {
            "conversation_variables": [],
            "environment_variables": [],
            "features": {
                "opening_statement": "我是物小智教师预评助手。请提交学生报告后，我会给出建议分和评语，需教师确认才作为正式成绩。",
                "suggested_questions": [
                    "请对本份实验报告做预评",
                    "给出建议分和可编辑评语",
                ],
                "suggested_questions_after_answer": {"enabled": False},
                "speech_to_text": {"enabled": False},
                "text_to_speech": {"enabled": False, "language": "", "voice": ""},
                "retriever_resource": {"enabled": True},
                "sensitive_word_avoidance": {"enabled": False},
                "file_upload": {
                    "enabled": False,
                    "allowed_file_types": [],
                    "allowed_file_extensions": [],
                    "allowed_file_upload_methods": [],
                    "number_limits": 0,
                },
            },
            "graph": {"edges": edges, "nodes": nodes},
        },
    }


def main():
    dsl = build()
    dumped = yaml.safe_dump(dsl, allow_unicode=True, sort_keys=False, width=120)
    for path in (OUT_ZH, OUT_ASCII):
        path.write_text(dumped, encoding="utf-8")
        parsed = yaml.safe_load(path.read_text(encoding="utf-8"))
        assert parsed["app"]["name"] == dsl["app"]["name"]
        assert len(parsed["workflow"]["graph"]["nodes"]) == 12
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
