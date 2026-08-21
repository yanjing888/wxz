from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
OUT_ASCII = ROOT / "docs" / "dify" / "wuxiaozhi-report-analysis-rich.yml"
OUT_ZH = ROOT / "docs" / "dify" / "物小智学生实验报告分析-丰富节点完整版.yml"
OUT_BRANCHED = ROOT / "docs" / "dify" / "wuxiaozhi-report-assist-branched.yml"


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


PARSE_CODE = r'''import json


def _clean(v):
    if v is None:
        return ""
    return str(v).strip()


def main(
    query="",
    action="run",
    experiment_code="",
    experiment_name="",
    section="",
    sectionLabel="",
    text="",
    localCheckSummary="",
    purpose="",
    principle="",
    apparatus="",
    procedure="",
    data="",
    results="",
    discussion="",
    report_context="",
    data_logs_json="",
    corrections_json="",
    step_summaries_json="",
    session_summary="",
):
    sections = {
        "purpose": _clean(purpose),
        "principle": _clean(principle),
        "apparatus": _clean(apparatus),
        "procedure": _clean(procedure),
        "data": _clean(data),
        "results": _clean(results),
        "discussion": _clean(discussion),
    }
    action = (_clean(action) or "run").lower()
    if action not in ["polish", "check", "review", "recap", "draft", "run"]:
        action = "run"
    experiment_name = _clean(experiment_name) or _clean(experiment_code) or "大学物理实验"
    section = _clean(section)
    section_label = _clean(sectionLabel) or section
    target_text = _clean(text) or sections.get(section, "")
    full_report = "\n\n".join(
        f"{key}: {value}" for key, value in sections.items() if value
    )
    if not full_report and target_text:
        full_report = f"{section_label}: {target_text}"
    if not _clean(query):
        if action == "polish":
            query = f"请整理并润色《{experiment_name}》报告的{section_label}。"
        elif action == "check":
            query = f"请检查《{experiment_name}》实验报告是否完整规范。"
        elif action == "review":
            query = f"请对《{experiment_name}》实验报告做教师预评。"
        else:
            query = f"请分析《{experiment_name}》实验报告并给出改进建议。"

    return {
        "action": action,
        "query": _clean(query),
        "experiment_code": _clean(experiment_code),
        "experiment_name": experiment_name,
        "section": section,
        "section_label": section_label,
        "target_text": target_text,
        "full_report": full_report,
        "local_check_summary": _clean(localCheckSummary),
        "report_context_text": _clean(report_context)[:6000],
        "data_logs_json": _clean(data_logs_json)[:4000],
        "corrections_json": _clean(corrections_json)[:4000],
        "step_summaries_json": _clean(step_summaries_json)[:4000],
        "session_summary": _clean(session_summary)[:2000],
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


def main(full_report="", data_logs_json="", corrections_json="", local_check_summary="", target_text=""):
    text = full_report or target_text or ""
    sections = [line for line in text.splitlines() if ":" in line[:20]]
    data_logs = _safe_json(data_logs_json, [])
    corrections = _safe_json(corrections_json, [])
    has_uncertainty = bool(re.search(r"不确定|误差|标准差|相对|±|\\+/-", text))
    has_unit = bool(re.search(r"(mm|cm|m|nm|N|kN|MPa|Pa|kg|g|s|V|A|Ω|ohm|℃)", text, re.I))
    word_count = len(re.sub(r"\s+", "", text))
    missing = []
    for name in ["purpose", "principle", "apparatus", "procedure", "data", "results", "discussion"]:
        if name not in text:
            missing.append(name)
    risk = []
    if word_count < 220:
        risk.append("正文偏短")
    if not has_uncertainty:
        risk.append("缺少不确定度/误差分析")
    if not has_unit:
        risk.append("单位痕迹不足")
    if not data_logs:
        risk.append("没有结构化数据记录")
    if corrections:
        risk.append("存在过程纠错记录")
    return {
        "word_count": word_count,
        "section_count": len(sections),
        "data_log_count": len(data_logs) if isinstance(data_logs, list) else 0,
        "correction_count": len(corrections) if isinstance(corrections, list) else 0,
        "has_uncertainty": has_uncertainty,
        "has_unit": has_unit,
        "risk_flags": "；".join(risk) if risk else "未发现明显硬性风险",
        "local_check_summary": local_check_summary,
    }
'''


ROUTER_CODE = r'''def main(action="run"):
    labels = {
        "polish": "段落润色",
        "check": "完整性检查",
        "review": "教师预评",
        "recap": "个性复盘",
        "draft": "报告起草",
        "run": "综合分析",
    }
    action = (action or "run").strip().lower()
    return {
        "action_label": labels.get(action, "综合分析"),
        "mode_hint": "只输出润色正文" if action == "polish" else "输出结构化改进建议",
    }
'''


COMPLETENESS_PROMPT = """你是大学物理实验报告结构审阅教师。请只依据输入文本检查完整性，不编造学生未写的内容。

实验：{{#parse.experiment_name#}}
本地检查：{{#metrics.local_check_summary#}}
字数：{{#metrics.word_count#}}；章节数：{{#metrics.section_count#}}
报告正文：
{{#parse.full_report#}}

输出要求：
1. 用“完整性结论 / 必补项目 / 可优化项目”三段。
2. 必补项目要指出缺的是哪个章节、缺什么证据或数据。
3. 不要给最终成绩。"""


DATA_PROMPT = """你是大学物理实验数据审查助手。请结合实验过程数据判断报告数据段是否可信。

实验：{{#parse.experiment_name#}}
数据记录 JSON：
{{#parse.data_logs_json#}}

报告正文：
{{#parse.full_report#}}

风险标记：{{#metrics.risk_flags#}}

输出要求：
1. 指出数据是否与过程记录相互支撑。
2. 检查单位、有效数字、不确定度、结论表达是否缺失。
3. 如没有过程数据，明确要求回到实验台补交或手工补录，不要虚构测量值。"""


ERROR_PROMPT = """你是大学物理实验误差分析导师。请根据纠错记录和报告讨论段，判断误差分析是否贴合本次实验。

实验：{{#parse.experiment_name#}}
纠错记录 JSON：
{{#parse.corrections_json#}}

报告正文：
{{#parse.full_report#}}

输出要求：
1. 找出最应该写入“分析与讨论”的 2-3 个误差来源。
2. 每个误差来源必须说明来自哪条过程证据、可能影响方向、如何改进。
3. 如果没有过程证据，只给通用误差分析框架并说明证据不足。"""


POLISH_PROMPT = """你是实验报告写作教练。请润色学生当前段落，保持原意，不代写新结论。

实验：{{#parse.experiment_name#}}
段落：{{#parse.section_label#}}
原文：
{{#parse.target_text#}}

要求：
1. 只输出润色后的段落正文。
2. 保留学生已有数据、单位、公式和结论，不凭空新增测量值。
3. 语言更规范、逻辑更顺，但不要变成模板腔。"""


RECAP_PROMPT = """你是学生实验复盘教练。请基于真实过程记录输出个性化复盘。

实验：{{#parse.experiment_name#}}
会话摘要：{{#parse.session_summary#}}
数据记录：{{#parse.data_logs_json#}}
纠错记录：{{#parse.corrections_json#}}
风险标记：{{#metrics.risk_flags#}}

输出：三条个人薄弱点、一条最可能误差来源假设、一条下次实验行动建议。每条都要尽量引用过程证据。"""


FINAL_PROMPT = """你是物小智学生实验报告分析总控。请把前面多个节点的结果合并成学生可执行的最终回复。

用户问题：{{#parse.query#}}
动作：{{#parse.action#}}
结构检查：
{{#completeness.text#}}

数据可信度：
{{#data_quality.text#}}

误差分析：
{{#error_analysis.text#}}

个性复盘：
{{#recap.text#}}

输出规则：
1. 如果动作是 polish，只输出润色节点内容：{{#polish.text#}}
2. 如果动作是 recap，以复盘内容为主，再补一条报告修改建议。
3. 其他动作输出“总体判断 / 必改清单 / 可直接修改的建议 / 下一步”。
4. 不输出最终成绩，不替学生写思考题完整答案。"""


TABLE_PROMPT = """你是大学物理实验报告数据整理助手。

实验：{{#parse.experiment_name#}}
数据记录 JSON：
{{#parse.data_logs_json#}}

用户问题：{{#parse.query#}}

请把测量数据整理成 **Markdown 表格**（可直接粘贴进报告）：
1. 按实验步骤分组，每组一个表
2. 表头用中文物理量名与单位
3. 若 JSON 中缺字段，用「—」占位并说明
4. 表格后附 1–2 句还可补充的计算/不确定度列建议
禁止虚构测量值。"""


GENERAL_PROMPT = """你是大学物理实验报告写作助手。

实验：{{#parse.experiment_name#}}
用户问题：{{#parse.query#}}

报告草稿：
{{#parse.full_report#}}

过程数据 JSON：{{#parse.data_logs_json#}}
纠错 JSON：{{#parse.corrections_json#}}
风险标记：{{#metrics.risk_flags#}}

请用中文分点回答，结合学生草稿与过程数据，不编造未出现的测量值。"""


def _start_variables():
    return [
        {"label": "动作", "variable": "action", "type": "text-input", "required": False, "max_length": 32, "options": []},
        {"label": "实验代码", "variable": "experiment_code", "type": "text-input", "required": False, "max_length": 64, "options": []},
        {"label": "实验名称", "variable": "experiment_name", "type": "text-input", "required": False, "max_length": 128, "options": []},
        {"label": "章节 key", "variable": "section", "type": "text-input", "required": False, "max_length": 64, "options": []},
        {"label": "章节名", "variable": "sectionLabel", "type": "text-input", "required": False, "max_length": 128, "options": []},
        {"label": "当前段正文", "variable": "text", "type": "paragraph", "required": False, "max_length": 6000, "options": []},
        {"label": "本地检查摘要", "variable": "localCheckSummary", "type": "paragraph", "required": False, "max_length": 3000, "options": []},
        *[
            {"label": label, "variable": key, "type": "paragraph", "required": False, "max_length": 6000, "options": []}
            for key, label in [
                ("purpose", "实验目的"),
                ("principle", "实验原理"),
                ("apparatus", "实验仪器"),
                ("procedure", "实验步骤"),
                ("data", "数据与处理"),
                ("results", "实验结果"),
                ("discussion", "分析与讨论"),
            ]
        ],
        {"label": "报告上下文", "variable": "report_context", "type": "paragraph", "required": False, "max_length": 12000, "options": []},
        {"label": "数据记录 JSON", "variable": "data_logs_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
        {"label": "纠错记录 JSON", "variable": "corrections_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
        {"label": "步骤 JSON", "variable": "step_summaries_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
        {"label": "会话摘要", "variable": "session_summary", "type": "paragraph", "required": False, "max_length": 3000, "options": []},
        {"label": "已填章节", "variable": "filled_section_keys", "type": "text-input", "required": False, "max_length": 256, "options": []},
        {"label": "对话历史", "variable": "chat_history", "type": "paragraph", "required": False, "max_length": 4000, "options": []},
    ]


def _parse_node():
    return node(
        "parse",
        "输入解析",
        "code",
        360,
        300,
        code_language="python3",
        code=PARSE_CODE,
        variables=[{"variable": name, "value_selector": ["start", name]} for name in [
            "action", "experiment_code", "experiment_name", "section", "sectionLabel", "text",
            "localCheckSummary", "purpose", "principle", "apparatus", "procedure", "data",
            "results", "discussion", "report_context", "data_logs_json", "corrections_json",
            "step_summaries_json", "session_summary",
        ]] + [{"variable": "query", "value_selector": ["sys", "query"]}],
        outputs={key: {"type": "string", "children": None} for key in [
            "action", "query", "experiment_code", "experiment_name", "section", "section_label",
            "target_text", "full_report", "local_check_summary", "report_context_text",
            "data_logs_json", "corrections_json", "step_summaries_json", "session_summary",
        ]},
    )


def _metrics_node():
    return node(
        "metrics",
        "报告指标",
        "code",
        640,
        300,
        code_language="python3",
        code=METRICS_CODE,
        variables=[
            {"variable": "full_report", "value_selector": ["parse", "full_report"]},
            {"variable": "data_logs_json", "value_selector": ["parse", "data_logs_json"]},
            {"variable": "corrections_json", "value_selector": ["parse", "corrections_json"]},
            {"variable": "local_check_summary", "value_selector": ["parse", "local_check_summary"]},
            {"variable": "target_text", "value_selector": ["parse", "target_text"]},
        ],
        outputs={
            "word_count": {"type": "number", "children": None},
            "section_count": {"type": "number", "children": None},
            "data_log_count": {"type": "number", "children": None},
            "correction_count": {"type": "number", "children": None},
            "has_uncertainty": {"type": "boolean", "children": None},
            "has_unit": {"type": "boolean", "children": None},
            "risk_flags": {"type": "string", "children": None},
            "local_check_summary": {"type": "string", "children": None},
        },
    )


def _llm_node(node_id, title, y, prompt):
    return node(
        node_id,
        title,
        "llm",
        1500,
        y,
        model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.25}},
        prompt_template=llm_prompt(prompt),
        context={"enabled": False, "variable_selector": []},
        vision={"enabled": False},
    )


def build_branched():
    """多分支版：问题分类器 → 4 条互斥 LLM 支路 → 汇总回答。每次只跑一条支路。"""
    start = node("start", "开始：报告输入", "start", 80, 300, variables=_start_variables())
    parse = _parse_node()
    metrics = _metrics_node()
    classifier = node(
        "classifier",
        "报告意图分类",
        "question-classifier",
        920,
        280,
        instruction=(
            "你是物小智报告助手路由器。根据用户问题选择最匹配的分支："
            "1=检查报告完整性、还缺什么章节；"
            "2=误差分析、不确定度、分析与讨论；"
            "3=把测量数据整理成表格；"
            "4=段落润色、写法建议或其他综合问题。"
            "若同时涉及多项，选最主要的一项。"
        ),
        instructions="",
        query_variable_selector=["parse", "query"],
        classes=[
            {"id": "1", "name": "检查报告完整性、还缺什么、遗漏章节"},
            {"id": "2", "name": "误差分析、不确定度、分析与讨论"},
            {"id": "3", "name": "整理测量数据为 Markdown 表格"},
            {"id": "4", "name": "段落润色、写法建议或其他综合问题"},
        ],
        model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.1}},
        vision={"enabled": False},
    )
    llm_check = _llm_node("llm_check", "分支：完整性检查", 80, COMPLETENESS_PROMPT)
    llm_error = _llm_node("llm_error", "分支：误差分析", 240, ERROR_PROMPT)
    llm_table = _llm_node("llm_table", "分支：数据表格", 400, TABLE_PROMPT)
    llm_general = _llm_node("llm_general", "分支：综合/润色", 560, GENERAL_PROMPT)
    answer = node(
        "answer",
        "回答",
        "answer",
        1780,
        300,
        answer="{{#llm_check.text#}}{{#llm_error.text#}}{{#llm_table.text#}}{{#llm_general.text#}}",
        variables=[],
    )

    nodes = [start, parse, metrics, classifier, llm_check, llm_error, llm_table, llm_general, answer]
    edges = [
        edge("start-parse", "start", "parse", "start", "code"),
        edge("parse-metrics", "parse", "metrics", "code", "code"),
        edge("metrics-classifier", "metrics", "classifier", "code", "question-classifier"),
        edge("cls-check", "classifier", "llm_check", "question-classifier", "llm", handle="1"),
        edge("cls-error", "classifier", "llm_error", "question-classifier", "llm", handle="2"),
        edge("cls-table", "classifier", "llm_table", "question-classifier", "llm", handle="3"),
        edge("cls-general", "classifier", "llm_general", "question-classifier", "llm", handle="4"),
        edge("check-answer", "llm_check", "answer", "llm", "answer"),
        edge("error-answer", "llm_error", "answer", "llm", "answer"),
        edge("table-answer", "llm_table", "answer", "llm", "answer"),
        edge("general-answer", "llm_general", "answer", "llm", "answer"),
    ]

    return {
        "app": {
            "name": "物小智学生实验报告助手-多分支版",
            "description": "学生端 report-assist：问题分类器路由到完整性/误差/表格/综合四条互斥支路，每次只执行一条 LLM。",
            "mode": "advanced-chat",
            "icon_type": "emoji",
            "icon": "📝",
            "icon_background": "#E0F2FE",
            "use_icon_as_answer_icon": False,
        },
        "kind": "app",
        "version": "0.6.0",
        "workflow": {
            "conversation_variables": [],
            "environment_variables": [],
            "features": {
                "opening_statement": "你好，我是物小智报告助手。我可以检查报告缺项、整理误差分析、生成数据表格，或润色段落。",
                "suggested_questions": [
                    "帮我检查报告还缺什么",
                    "根据本次实验记录，帮我整理误差分析思路",
                    "帮我把测量数据整理成表格",
                ],
                "suggested_questions_after_answer": {"enabled": True},
                "speech_to_text": {"enabled": False},
                "text_to_speech": {"enabled": False, "language": "", "voice": ""},
                "retriever_resource": {"enabled": True},
                "sensitive_word_avoidance": {"enabled": False},
                "file_upload": {"enabled": False, "allowed_file_types": [], "allowed_file_extensions": [], "allowed_file_upload_methods": [], "number_limits": 0},
            },
            "graph": {"edges": edges, "nodes": nodes},
        },
    }


def build():
    start = node(
        "start",
        "开始：报告输入",
        "start",
        80,
        300,
        variables=[
            {"label": "动作", "variable": "action", "type": "text-input", "required": False, "max_length": 32, "options": []},
            {"label": "实验代码", "variable": "experiment_code", "type": "text-input", "required": False, "max_length": 64, "options": []},
            {"label": "实验名称", "variable": "experiment_name", "type": "text-input", "required": False, "max_length": 128, "options": []},
            {"label": "章节 key", "variable": "section", "type": "text-input", "required": False, "max_length": 64, "options": []},
            {"label": "章节名", "variable": "sectionLabel", "type": "text-input", "required": False, "max_length": 128, "options": []},
            {"label": "当前段正文", "variable": "text", "type": "paragraph", "required": False, "max_length": 6000, "options": []},
            {"label": "本地检查摘要", "variable": "localCheckSummary", "type": "paragraph", "required": False, "max_length": 3000, "options": []},
            *[
                {"label": label, "variable": key, "type": "paragraph", "required": False, "max_length": 6000, "options": []}
                for key, label in [
                    ("purpose", "实验目的"),
                    ("principle", "实验原理"),
                    ("apparatus", "实验仪器"),
                    ("procedure", "实验步骤"),
                    ("data", "数据与处理"),
                    ("results", "实验结果"),
                    ("discussion", "分析与讨论"),
                ]
            ],
            {"label": "报告上下文", "variable": "report_context", "type": "paragraph", "required": False, "max_length": 12000, "options": []},
            {"label": "数据记录 JSON", "variable": "data_logs_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
            {"label": "纠错记录 JSON", "variable": "corrections_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
            {"label": "步骤 JSON", "variable": "step_summaries_json", "type": "paragraph", "required": False, "max_length": 8000, "options": []},
            {"label": "会话摘要", "variable": "session_summary", "type": "paragraph", "required": False, "max_length": 3000, "options": []},
        ],
    )

    parse = node(
        "parse",
        "节点01 输入解析与动作归一",
        "code",
        360,
        300,
        code_language="python3",
        code=PARSE_CODE,
        variables=[{"variable": name, "value_selector": ["start", name]} for name in [
            "action", "experiment_code", "experiment_name", "section", "sectionLabel", "text",
            "localCheckSummary", "purpose", "principle", "apparatus", "procedure", "data",
            "results", "discussion", "report_context", "data_logs_json", "corrections_json",
            "step_summaries_json", "session_summary",
        ]] + [{"variable": "query", "value_selector": ["sys", "query"]}],
        outputs={key: {"type": "string", "children": None} for key in [
            "action", "query", "experiment_code", "experiment_name", "section", "section_label",
            "target_text", "full_report", "local_check_summary", "report_context_text",
            "data_logs_json", "corrections_json", "step_summaries_json", "session_summary",
        ]},
    )

    metrics = node(
        "metrics",
        "节点02 报告指标计算",
        "code",
        640,
        300,
        code_language="python3",
        code=METRICS_CODE,
        variables=[
            {"variable": "full_report", "value_selector": ["parse", "full_report"]},
            {"variable": "data_logs_json", "value_selector": ["parse", "data_logs_json"]},
            {"variable": "corrections_json", "value_selector": ["parse", "corrections_json"]},
            {"variable": "local_check_summary", "value_selector": ["parse", "local_check_summary"]},
            {"variable": "target_text", "value_selector": ["parse", "target_text"]},
        ],
        outputs={
            "word_count": {"type": "number", "children": None},
            "section_count": {"type": "number", "children": None},
            "data_log_count": {"type": "number", "children": None},
            "correction_count": {"type": "number", "children": None},
            "has_uncertainty": {"type": "boolean", "children": None},
            "has_unit": {"type": "boolean", "children": None},
            "risk_flags": {"type": "string", "children": None},
            "local_check_summary": {"type": "string", "children": None},
        },
    )

    nodes = [
        start,
        parse,
        metrics,
        node("router", "节点03 动作识别标签", "code", 920, 300, code_language="python3", code=ROUTER_CODE,
             variables=[{"variable": "action", "value_selector": ["parse", "action"]}],
             outputs={
                 "action_label": {"type": "string", "children": None},
                 "mode_hint": {"type": "string", "children": None},
             }),
        node("completeness", "节点04 结构完整性审阅", "llm", 1200, 120, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.2}}, prompt_template=llm_prompt(COMPLETENESS_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("data_quality", "节点05 数据可信度审查", "llm", 1200, 300, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.2}}, prompt_template=llm_prompt(DATA_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("error_analysis", "节点06 误差分析匹配", "llm", 1200, 480, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.25}}, prompt_template=llm_prompt(ERROR_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("polish", "节点07 当前段落润色", "llm", 1200, 660, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.35}}, prompt_template=llm_prompt(POLISH_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("recap", "节点08 个性复盘生成", "llm", 1200, 840, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.25}}, prompt_template=llm_prompt(RECAP_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("summary_template", "节点09 多路结果编排", "template-transform", 1500, 300, template="{{#completeness.text#}}\n\n---DATA---\n{{#data_quality.text#}}\n\n---ERROR---\n{{#error_analysis.text#}}\n\n---POLISH---\n{{#polish.text#}}\n\n---RECAP---\n{{#recap.text#}}", variables=[]),
        node("final_guard", "节点10 总控质检与输出约束", "llm", 1780, 300, model={"provider": "langgenius/tongyi/tongyi", "name": "qwen-plus", "mode": "chat", "completion_params": {"temperature": 0.15}}, prompt_template=llm_prompt(FINAL_PROMPT), context={"enabled": False, "variable_selector": []}, vision={"enabled": False}),
        node("answer", "回答", "answer", 2060, 300, answer="{{#final_guard.text#}}", variables=[]),
    ]

    edges = [
        edge("start-parse", "start", "parse", "start", "code"),
        edge("parse-metrics", "parse", "metrics", "code", "code"),
        edge("metrics-router", "metrics", "router", "code", "code"),
        edge("router-completeness", "router", "completeness", "code", "llm"),
        edge("completeness-data", "completeness", "data_quality", "llm", "llm"),
        edge("data-error", "data_quality", "error_analysis", "llm", "llm"),
        edge("error-polish", "error_analysis", "polish", "llm", "llm"),
        edge("polish-recap", "polish", "recap", "llm", "llm"),
        edge("recap-summary", "recap", "summary_template", "llm", "template-transform"),
        edge("summary-final", "summary_template", "final_guard", "template-transform", "llm"),
        edge("final-answer", "final_guard", "answer", "llm", "answer"),
    ]

    return {
        "app": {
            "name": "物小智学生实验报告分析-丰富节点完整版",
            "description": "学生端报告助手 Dify Chatflow：解析报告输入、计算质量指标、分动作路由，并通过结构完整性、数据可信度、误差分析、段落润色、个性复盘和总控质检等多节点生成可执行建议。",
            "mode": "advanced-chat",
            "icon_type": "emoji",
            "icon": "🧪",
            "icon_background": "#E0F2FE",
            "use_icon_as_answer_icon": False,
        },
        "kind": "app",
        "version": "0.6.0",
        "workflow": {
            "conversation_variables": [],
            "environment_variables": [],
            "features": {
                "opening_statement": "你好，我是物小智报告助手。我会结合实验过程数据检查报告完整性、数据可信度和误差分析，也可以润色当前段落。",
                "suggested_questions": ["检查我的报告还缺什么", "帮我润色当前段落", "根据本次记录生成复盘建议"],
                "suggested_questions_after_answer": {"enabled": True},
                "speech_to_text": {"enabled": False},
                "text_to_speech": {"enabled": False, "language": "", "voice": ""},
                "retriever_resource": {"enabled": True},
                "sensitive_word_avoidance": {"enabled": False},
                "file_upload": {"enabled": False, "allowed_file_types": [], "allowed_file_extensions": [], "allowed_file_upload_methods": [], "number_limits": 0},
            },
            "graph": {"edges": edges, "nodes": nodes},
        },
    }


def main():
    rich = build()
    dumped = yaml.safe_dump(rich, allow_unicode=True, sort_keys=False, width=120)
    for path in (OUT_ASCII, OUT_ZH):
        path.write_text(dumped, encoding="utf-8")
        parsed = yaml.safe_load(path.read_text(encoding="utf-8"))
        assert parsed["app"]["name"] == rich["app"]["name"]
    print(f"wrote {OUT_ASCII}")
    print(f"wrote {OUT_ZH}")

    branched = build_branched()
    branched_dump = yaml.safe_dump(branched, allow_unicode=True, sort_keys=False, width=120)
    OUT_BRANCHED.write_text(branched_dump, encoding="utf-8")
    print(f"wrote {OUT_BRANCHED}")


if __name__ == "__main__":
    main()
