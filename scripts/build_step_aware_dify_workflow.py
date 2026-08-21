#!/usr/bin/env python3
"""Generate 物小智 rich Dify workflow (vision/text, dual KB, multi-stage LLM like legal agent)."""

from __future__ import annotations

import copy
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / "docs" / "dify" / "wuxiaozhi-correction-guidance-rich.yml"
OUT_ASCII = ROOT / "docs" / "dify" / "wuxiaozhi-correction-guidance-rich.yml"
OUT_ZH = ROOT / "docs" / "dify" / "物理实验智能纠错与指导-丰富节点完整版.yml"

N_START = "1779853028414"
N_PARSE = "1779854194447"
N_HTTP = "1790000000002"
N_CTX = "1790000000001"
N_ROUTER = "1779857861794"
N_CLASSIFIER = "1790000000100"
N_ANS_OFFTOPIC = "1790000000111"
N_QUERY_REWRITE = "1790000000107"
N_CODE_QUERIES = "1790000000108"
N_KB_T_TEXT = "1781000000101"
N_KB_R_TEXT = "1781000000106"
N_TPL_TEXT = "1790000000113"
N_LLM_DRAFT = "1779858308963"
N_LLM_POLISH = "1790000000112"
N_ANS_TEXT = "1779858605374"
N_KB_R_VIS = "1781000000102"
N_KB_T_VIS = "1781000000103"
N_TPL_VIS = "1790000000115"
N_LLM_VIS = "1779858136115"
N_CODE_VIS = "1781000000001"
N_PARAM = "1779960945383"
N_LLM_FB = "1779858712451"
N_LLM_POLISH_VIS = "1790000000114"
N_ANS_VIS = "1779859794336"

INPUT_PARSE_CODE = r'''def _clean(value):
    if value is None:
        return ""
    return str(value).strip()


def main(
    query="",
    image_name="",
    image_url="",
    mime_type="",
    experiment_code="",
    experiment_type="",
    category="",
    step_id="",
    step_title="",
    step_desc="",
    step_guide="",
    step_correction_mode="",
    correction_mode="",
    data_json="",
    teaching_section="",
    rules_section="",
    retrieval_tags="",
    knowledge_type="",
):
    query = _clean(query)
    experiment_code = _clean(experiment_code)
    experiment_type = _clean(experiment_type)
    category = (_clean(category) or "auto").lower()
    correction_mode = (_clean(correction_mode) or "auto").lower()
    step_id = _clean(step_id)
    step_title = _clean(step_title)
    step_desc = _clean(step_desc)
    step_guide = _clean(step_guide)
    step_correction_mode = _clean(step_correction_mode)
    data_json = _clean(data_json)

    has_image = bool(_clean(image_name) or _clean(image_url))
    mode = "vision" if (has_image or correction_mode == "vision" or category == "vision") else "text"

    if not query:
        if mode == "vision":
            query = "请根据当前实验和当前步骤，对上传图片进行视觉纠错。"
        elif data_json:
            query = "请根据当前实验和当前步骤，检查这些实验记录或数据是否合理，并给出指导建议。"
        else:
            query = "请结合当前实验步骤和知识库，回答我的问题并给出操作指导。"

    return {
        "mode": mode,
        "query": query,
        "experiment_code": experiment_code,
        "experiment_type": experiment_type,
        "category": category,
        "step_id": step_id,
        "step_title": step_title,
        "step_desc": step_desc,
        "step_guide": step_guide,
        "step_correction_mode": step_correction_mode,
        "data_json": data_json,
        "teaching_section": _clean(teaching_section),
        "rules_section": _clean(rules_section),
        "retrieval_tags": _clean(retrieval_tags),
        "knowledge_type": _clean(knowledge_type),
        "fetch_url": f"/api/public/experiments/{experiment_code}" if experiment_code else "",
    }
'''

STEP_CONTEXT_CODE = r'''import json


def _clean(v):
    if v is None:
        return ""
    return str(v).strip()


def _pick_step(steps, step_id):
    if not isinstance(steps, dict) or not step_id:
        return {}
    if step_id in steps:
        return steps.get(step_id) or {}
    for key in (step_id, str(int(step_id)) if step_id.isdigit() else step_id):
        if key in steps:
            return steps.get(key) or {}
    return {}


def _format_tut_guide(tut):
    if not isinstance(tut, dict):
        return ""
    lines = []
    steps = tut.get("steps") or []
    warnings = tut.get("warnings") or []
    if steps:
        lines.append("操作要点:")
        for item in steps:
            lines.append(f"- {item}")
    if warnings:
        lines.append("注意:")
        for item in warnings:
            lines.append(f"- {item}")
    return "\n".join(lines)


def main(
    mode="text",
    query="",
    experiment_code="",
    experiment_type="",
    step_id="",
    step_title="",
    step_desc="",
    step_guide="",
    step_correction_mode="",
    data_json="",
    teaching_section="",
    rules_section="",
    retrieval_tags="",
    kb_context="",
    repeat_hint="",
    http_body="",
):
    mode = _clean(mode) or "text"
    query = _clean(query)
    experiment_code = _clean(experiment_code)
    experiment_type = _clean(experiment_type)
    step_id = _clean(step_id)
    step_title = _clean(step_title)
    step_desc = _clean(step_desc)
    step_guide = _clean(step_guide)
    step_correction_mode = _clean(step_correction_mode)
    data_json = _clean(data_json)
    teaching_section = _clean(teaching_section)
    rules_section = _clean(rules_section)
    retrieval_tags = _clean(retrieval_tags)
    kb_context = _clean(kb_context)
    repeat_hint = _clean(repeat_hint)

    correction_mode_step = step_correction_mode
    body = _clean(http_body)
    if body:
        try:
            cfg = json.loads(body)
            steps = cfg.get("steps") or {}
            step = _pick_step(steps, step_id)
            if not step_title:
                step_title = _clean(step.get("title"))
            if not step_desc:
                step_desc = _clean(step.get("desc"))
            if not step_guide:
                step_guide = _format_tut_guide(step.get("tut") or {})
            if not correction_mode_step:
                correction_mode_step = _clean(step.get("correctionMode"))
        except Exception:
            pass

    step_part = " ".join(x for x in [f"step:{step_id}" if step_id else "", step_title, step_desc] if x)
    tag_part = retrieval_tags or experiment_code
    section_teaching = teaching_section or (f"{experiment_code}.step.{step_id}.teaching" if step_id else experiment_code)
    section_rules = rules_section or (f"{experiment_code}.step.{step_id}.rules" if step_id else experiment_code)

    data_part = f" data:{data_json[:800]}" if data_json else ""
    base = f"{experiment_type} {experiment_code} {step_part} {tag_part} {query}{data_part}"

    if mode == "vision":
        retrieval_query_teaching = f"{base} 步骤目标 仪器要求 操作背景 {section_teaching}"
        retrieval_query_rules = f"{base} 视觉纠错 rule_id severity evidence fix_instruction {section_rules}"
    else:
        retrieval_query_teaching = f"{base} 实验原理 操作步骤 数据处理 仪器使用 {section_teaching}"
        retrieval_query_rules = f"{base} 操作错误 读数方法 记录规范 数据纠错 {section_rules}"

    if experiment_code and step_id and (step_title or step_guide or step_desc):
        context_quality = "full"
    elif experiment_code or experiment_type:
        context_quality = "partial"
    else:
        context_quality = "minimal"

    if context_quality == "minimal":
        fallback_note = (
            "【上下文降级】实验/步骤参数未完整传入。"
            "请从用户问题、检索结果识别实验（牛顿环 newton_rings / 空气劈尖 air_wedge_thickness / 读数显微镜 microscope_length_measurement），"
            "按大学物理实验常规给指导；需要步骤细节时提示学生确认当前步骤或上传照片。"
        )
    elif context_quality == "partial":
        fallback_note = (
            "【上下文部分】已有实验信息但步骤可能不全。"
            f"优先围绕实验 {experiment_type or experiment_code} 回答；"
            "若问题指向具体操作，给该操作指导，并说明「若不在此步骤，请切换到对应步骤后再问」。"
        )
    else:
        fallback_note = ""

    context_block = "\n".join(
        x
        for x in [
            f"上下文完整度: {context_quality}",
            f"实验: {experiment_type} ({experiment_code})".strip() if (experiment_type or experiment_code) else "",
            f"步骤: {step_id} {step_title}".strip() if (step_id or step_title) else "",
            f"步骤说明: {step_desc}" if step_desc else "",
            f"步骤纠错模式: {correction_mode_step}" if correction_mode_step else "",
            f"步骤教程摘要: {step_guide[:1200]}" if step_guide else "",
            f"后端预检索 kb_context:\n{kb_context[:2000]}" if kb_context else "",
            f"重复求助提示: {repeat_hint}" if repeat_hint else "",
            fallback_note,
        ]
        if x
    )

    return {
        "mode": mode,
        "query": query,
        "experiment_code": experiment_code,
        "experiment_type": experiment_type,
        "step_id": step_id,
        "step_title": step_title,
        "step_desc": step_desc,
        "data_json": data_json,
        "retrieval_query_teaching": retrieval_query_teaching,
        "retrieval_query_rules": retrieval_query_rules,
        "step_context": context_block,
        "context_quality": context_quality,
        "metadata_experiment_code": experiment_code or "general",
        "metadata_step_id": step_id or "0",
        "metadata_teaching_section": section_teaching,
        "metadata_rules_section": section_rules,
    }
'''

QUERY_REWRITE_CODE = r'''import json
import re


def _clean(v):
    if v is None:
        return ""
    return str(v).strip()


def _extract_json(text):
    text = _clean(text)
    text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except Exception:
        start, end = text.find("{"), text.rfind("}")
        if start >= 0 and end > start:
            try:
                return json.loads(text[start : end + 1])
            except Exception:
                return {}
    return {}


def main(raw_text="", fallback_teaching="", fallback_rules=""):
    parsed = _extract_json(raw_text)
    teaching = _clean(parsed.get("teaching_query")) or _clean(fallback_teaching)
    rules = _clean(parsed.get("rules_query")) or _clean(fallback_rules)
    intent = _clean(parsed.get("intent")) or "operation"
    return {
        "teaching_query": teaching,
        "rules_query": rules,
        "intent": intent,
    }
'''

QUERY_REWRITE_PROMPT = f"""你是物小智·检索 query 改写器，服务于大学物理实验台（Chatflow 文本分支）。

## 当前上下文
{{{{#{N_CTX}.step_context#}}}}

## 用户输入
- 用户问题：{{{{#{N_CTX}.query#}}}}
- 实验数据 JSON：{{{{#{N_CTX}.data_json#}}}}
- 默认教学检索 query：{{{{#{N_CTX}.retrieval_query_teaching#}}}}
- 默认规则检索 query：{{{{#{N_CTX}.retrieval_query_rules#}}}}

## 平台实验（上下文缺失时可从问题中匹配）
- newton_rings：牛顿环法测定平凸透镜曲率半径（JCD-3 读数显微镜、干涉环、暗环直径、曲率半径）
- air_wedge_thickness：空气劈尖干涉测量薄片厚度（直条纹、L/L′、条纹计数 n′）
- microscope_length_measurement：读数显微镜微小长度直接测量（主尺+鼓轮合读、空程差）

## 任务
输出 JSON（不要 Markdown 代码块），用于双库检索：
{{
  "intent": "principle | operation | data_check | off_topic",
  "teaching_query": "面向教学库的检索句",
  "rules_query": "面向纠错规则库的检索句"
}}

## 改写要求
1. teaching_query / rules_query 各 1 句中文，40–120 字；必须含实验名或 experiment_code、步骤号/步骤名（若有）、问题核心词。
2. intent=principle：原理/公式/报告写法；rules_query 偏「常见误区、单位、公式用错」。
3. intent=operation：操作/仪器/读数/现象；rules_query 偏「操作错误、读数方法、空程差、光路/调焦」。
4. 有 data_json 或用户在核对数据：intent=data_check；rules_query 偏「数据记录、单位换算、公式代入、合理范围」。
5. 上下文完整度为 minimal/partial 时：从用户问题推断最可能实验，在两条 query 中显式写出实验中文名与关键词；禁止输出空字符串。
6. 明显与大学物理实验无关（闲聊、其他学科）：intent=off_topic，两条 query 可简短但仍含「大学物理实验」。
7. 不要输出除 JSON 以外的任何文字。"""

TEXT_DRAFT_PROMPT = f"""你是物小智·大学物理实验助教（初稿生成器）。你的输出会被下一节点润色后给学生，请写**分析草稿**而非最终短答。

## 角色与边界
- 身份：现场实验助教，同时能做**操作指导**与**数据/操作纠错**（文字侧，无图）。
- 服务对象：正在做实验的本科生。
- 禁止：声称看见现场/图片；写「根据知识库/检索结果」；代写完整实验报告；编造未给出的测量数据。

## 当前上下文与检索材料
{{{{#{N_TPL_TEXT}.output#}}}}

## 用户问题
{{{{#{N_CTX}.query#}}}}

## 实验数据（若有）
{{{{#{N_CTX}.data_json#}}}}

## 回答策略（按上下文完整度）
- **full**：严格围绕 step_context 中的实验、步骤、纠错模式（vision/data）回答。
- **partial**：实验已知、步骤不全时，先答用户问题，再提示「请确认当前步骤编号或看左侧步骤条」。
- **minimal**：从问题+检索片段推断实验（牛顿环/空气劈尖/读数显微镜）；给通用但可执行的指导；明确说明「未收到完整实验步骤参数，以下为根据问题推断的建议」。

## 意图分支（结合检索意图 intent）
- principle：讲清原理/公式/符号含义/报告怎么写，联系当前实验。
- operation：分步骤说明怎么操作、仪器怎么调、读数注意什么；可指出常见错误。
- data_check：对照 data_json 查单位、公式、数量级、记录格式；列出疑点和改法。
- 若检索片段为空：仍基于 step_context 与问题给保守指导，不要拒答。

## 草稿结构（Markdown）
必须包含：
1. **【判断】** 一句话：问题类型 + 当前实验/步骤（或推断的实验）
2. **【要点】** 2–4 条，操作指导或纠错点
3. **【下一步】** 一条可立即执行的动作
4. 有 data_json 时增加 **【数据疑点】**
5. 需要看图才能确认时，写「请上传当前步骤照片以便视觉纠错」

## 实验纠错要点（文字侧，无图时）
- 读数：空程差、单向读数、主尺+鼓轮合读、弦长≠直径
- 光学：光路/调焦顺序（先叉丝后样品）、反光镜、条纹/环纹
- 数据：单位（nm/mm/μm）、公式漏因子、平均与重复测量

只输出草稿正文，不必过度润色。"""

TEXT_POLISH_PROMPT = f"""你是物小智·回答质检员。将初稿改为学生**在实验台边 30 秒内能看完并执行**的 Markdown 短答。

## 现场上下文
{{{{#{N_CTX}.step_context#}}}}

## 用户问题
{{{{#{N_CTX}.query#}}}}

## 初稿
{{{{#{N_LLM_DRAFT}.text#}}}}

## 质检要求
1. **准确性**：保留初稿正确内容；删除空话、重复、过度原理推导；不与 step_context 中实验/步骤明显矛盾。
2. **上下文降级**：若上下文完整度为 partial/minimal，保留初稿中的合理推断，但用一句话提醒学生确认实验与步骤；仍要给可执行建议，不要只叫学生「信息不足」。
3. **长度**：150–350 字为主；列表 ≤4 条；公式最多 1 个，必要时用 plain 文字解释符号。
4. **结构**：必须含 **下一步**（加粗或单独一行），一条动作指令。
5. **指导 vs 纠错**：操作问题偏「怎么做」；数据/记录问题偏「哪里可能错了+怎么改」；两者可兼有但别写成报告腔。
6. **无图**：不得写「从图上看」；若需看图，明确说「请上传当前步骤照片」。
7. **语气**：短句、直接、像助教口头提醒；禁止「根据知识库」。

直接输出最终 Markdown，不要解释质检过程。"""

VISION_PROMPT = f"""你是物小智·视觉纠错专家（多模态）。根据**上传图片可见内容**、当前实验/步骤上下文与检索规则，输出**纯 JSON**（无 Markdown、无代码块、无解释文字）。

## 检索与步骤上下文
{{{{#{N_TPL_VIS}.output#}}}}

## 用户问题
{{{{#{N_CTX}.query#}}}}

## 角色与硬规则
1. **只判可见**：仅报告图片中能直接看到的操作、仪器状态、读数界面、记录表等问题；看不见的不臆测。
2. **步骤相关**：图片明显与当前实验/大学物理实验无关 → is_step_related=false，issues 为空，regions 为空，summary 说明原因。
3. **上下文降级**：experiment_code/step 为空时，从图片+问题推断实验（牛顿环/空气劈尖/读数显微镜），在 summary 中说明推断；仍只对可见内容判错。
4. **rule_id**：issues[].issue_id 优先使用检索规则中的 rule_id（如 NR_STEP1_xxx）；无匹配时用「实验缩写_STEP步号_VIS_简述」格式自编。
5. **regions**：0–1000 归一化坐标；只框具体错误区域；无法定位则不画框；不得框满整图。
6. **severity**：高/中/低；**confidence**：0–1。

## issues 每项字段
issue_id, error_type, severity, description, evidence, fix_instruction, confidence, region_ref（对应 regions 序号，从 1 开始，无框则省略）

## 输出 JSON  schema
{{
  "experiment": "实验中文名",
  "experiment_code": "{{{{#{N_CTX}.experiment_code#}}}}",
  "step_id": "{{{{#{N_CTX}.step_id#}}}}",
  "step_title": "{{{{#{N_CTX}.step_title#}}}}",
  "is_step_related": true,
  "summary": "一句话概括图内容与总体判断",
  "issues": [],
  "regions": [
    {{"topLeftX": 0, "topLeftY": 0, "bottomRightX": 0, "bottomRightY": 0, "label": "问题简述"}}
  ],
  "next_action": "一条可执行的下一步操作"
}}

无可见问题时：issues=[]，regions=[]，summary 写「未发现明显可见错误（或图片不足以判断）」，next_action 给继续实验的建议。"""

FEEDBACK_PROMPT = f"""你是物小智·视觉纠错反馈撰写员。将视觉 JSON 转为学生可读的 Markdown（不是 JSON）。

## 步骤
{{{{#{N_CTX}.step_id#}}}} {{{{#{N_CTX}.step_title#}}}}

## 步骤上下文
{{{{#{N_CTX}.step_context#}}}}

## 视觉 JSON
{{{{#{N_CODE_VIS}.vision_json#}}}}

## 输出结构
1. **总体判断**（1–2 句，来自 summary；若 is_step_related=false 说明图片与实验关系）
2. **发现的问题**（每个 issue 一小段：错误类型 + 可见证据 + 改法；severity 高的问题放前面）
3. **下一步建议**（来自 next_action，单独成段）

## 要求
- 用第二人称「你」；不要输出 JSON；不要编造 JSON 中没有的问题。
- 无 issues 时写「未发现明显可见错误」，仍给 next_action。
- 上下文 partial/minimal 时，可提示「请确认当前实验步骤是否与图片一致」。"""

POLISH_VIS_PROMPT = f"""你是物小智·视觉反馈质检员。精简视觉纠错 Markdown，供实验台现场阅读。

## 步骤上下文
{{{{#{N_CTX}.step_context#}}}}

## 反馈初稿
{{{{#{N_LLM_FB}.text#}}}}

## 要求
1. 保留：问题描述、可见证据、具体改法、下一步；删除重复和套话。
2. 长度 150–400 字；问题列表 ≤3 条；每条含「怎么改」。
3. 无问题时明确写「未发现明显可见错误」，并保留建设性的下一步。
4. 不要提及 JSON、知识库、模型；语气像现场助教。
5. 上下文不完整时，加一句提醒确认实验/步骤，但不要因此删掉有效纠错内容。

直接输出最终 Markdown。"""


def edge(eid, src, handle, tgt, stype, ttype):
    return {
        "id": eid,
        "source": src,
        "sourceHandle": handle,
        "target": tgt,
        "targetHandle": "target",
        "type": "custom",
        "data": {"isInIteration": False, "sourceType": stype, "targetType": ttype},
        "zIndex": 0,
    }


def llm_node(nid, title, prompt, x, y, model="qwen3.6-plus", vision=False, memory=False, temp=0.2):
    data = {
        "title": title,
        "type": "llm",
        "model": {"provider": "langgenius/tongyi/tongyi", "name": model, "mode": "chat", "completion_params": {}},
        "completion_params": {"temperature": temp, "max_tokens": 4096 if vision else 2048},
        "prompt_template": [{"id": f"prompt-{nid}", "role": "system", "text": prompt}],
        "context": {"enabled": False, "variable_selector": []},
        "vision": {"enabled": vision, "configs": {"detail": "high", "variable_selector": [N_START, "image"]}} if vision else {"enabled": False},
        "selected": False,
    }
    if memory:
        data["memory"] = {
            "role_prefix": {"user": "", "assistant": ""},
            "window": {"enabled": True, "size": 12},
            "query_prompt_template": "{{#sys.query#}}",
        }
    return {
        "id": nid,
        "type": "custom",
        "width": 242,
        "height": 88,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": data,
    }


def answer_node(nid, title, answer, x, y, height=103):
    return {
        "id": nid,
        "type": "custom",
        "width": 242,
        "height": height,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {"title": title, "type": "answer", "answer": answer, "variables": []},
    }


def classifier_node(nid, x, y):
    return {
        "id": nid,
        "type": "custom",
        "width": 242,
        "height": 220,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": "问题分类器",
            "type": "question-classifier",
            "instruction": (
                "你是物小智问题路由器。若 query 无实验线索，可结合用户表述推断是否为大学物理实验。"
                "1=与实验无关（闲聊、其他学科、且无法推断为实验问题）；"
                "2=原理/概念/公式/报告写法；"
                "3=操作/仪器/读数/数据/现象/纠错。"
                "问题中出现牛顿环、劈尖、读数显微镜、干涉、空程差等，优先选 2 或 3 而非 1。"
            ),
            "instructions": "",
            "query_variable_selector": [N_CTX, "query"],
            "classes": [
                {"id": "1", "name": "与当前大学物理实验无关（闲聊、其他学科、无实验上下文）"},
                {"id": "2", "name": "实验原理、概念、公式推导、报告写法类问题"},
                {"id": "3", "name": "操作步骤、仪器使用、读数方法、数据核对、实验现象类问题"},
            ],
            "model": {
                "provider": "langgenius/tongyi/tongyi",
                "name": "qwen3.6-plus",
                "mode": "chat",
                "completion_params": {"temperature": 0.1},
            },
            "vision": {"enabled": False},
            "selected": False,
        },
    }


def template_node(nid, title, template, variables, x, y):
    return {
        "id": nid,
        "type": "custom",
        "width": 242,
        "height": 54,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": title,
            "type": "template-transform",
            "template": template,
            "variables": variables,
            "selected": False,
        },
    }


def kb_node(nid, title, desc, x, y, query_node, query_field, kb_type: str = "", section_field: str = ""):
    conditions = [
        {
            "name": "experiment_code",
            "comparison_operator": "is",
            "value": f"{{{{#{N_CTX}.metadata_experiment_code#}}}}",
        }
    ]
    # kb_type / sectionKey 过滤已省略：teaching 与 correction_rules 分库即可；步骤靠 query + 正文
    return {
        "id": nid,
        "type": "custom",
        "width": 242,
        "height": 112,
        "position": {"x": x, "y": y},
        "positionAbsolute": {"x": x, "y": y},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": title,
            "type": "knowledge-retrieval",
            "desc": desc,
            "dataset_ids": [],
            "retrieval_mode": "multiple",
            "query_variable_selector": [query_node, query_field],
            "multiple_retrieval_config": {
                "top_k": 6 if "纠错" in title else 5,
                "score_threshold": 0.3 if "纠错" in title else 0.35,
                "reranking_enable": True,
                "reranking_mode": "reranking_model",
                "reranking_model": {"provider": "langgenius/tongyi/tongyi", "model": "qwen3-rerank"},
            },
            "metadata_filtering_mode": "manual",
            "metadata_filtering_conditions": {
                "logical_operator": "and",
                "conditions": conditions,
            },
            "selected": False,
        },
    }


def main() -> None:
    data = yaml.safe_load(TEMPLATE.read_text(encoding="utf-8"))
    data["app"]["name"] = "物理实验智能纠错与指导-丰富节点完整版"
    data["app"]["description"] = (
        "物小智实验台 enriched 工作流：HTTP 拉 manifest → 步骤上下文 → vision/text 分流；"
        "文本侧含问题分类器、检索 query 改写、双库检索、模板聚合、初稿+质检；"
        "视觉侧含双库检索、JSON 纠错、regions 提取、反馈+质检。"
        "导入后绑定教学库与纠错规则库，配置 WXZ_BACKEND_BASE_URL。"
    )

    wf = data["workflow"]
    wf["environment_variables"] = [
        {
            "id": "wxz-backend-base-url",
            "name": "WXZ_BACKEND_BASE_URL",
            "value": "http://127.0.0.1:8082",
            "value_type": "string",
            "description": "物小智后端地址，HTTP 节点拉取 /api/public/experiments/{code}",
        }
    ]
    features = wf["features"]
    features["opening_statement"] = (
        "你好，我是物小智实验台助教。我会结合你当前的实验与步骤回答问题；"
        "上传图片会做视觉纠错并在图上标注问题区域。"
    )
    features["suggested_questions"] = [
        "牛顿环实验里为什么要保持同一方向读数？",
        "空气劈尖测量时，L 和 L′ 分别代表什么？",
        "读数显微镜主尺和鼓轮小数怎么合读？",
        "我上传一张当前步骤照片，请帮我看看操作有没有问题。",
    ]

    graph = wf["graph"]
    old_nodes = {n["id"]: n for n in graph["nodes"]}

    start = old_nodes[N_START]
    # 用户问题走 Dify 内置 sys.query，不在开始节点重复定义 query；按 variable 去重
    deduped = []
    seen = set()
    for v in start["data"]["variables"]:
        var = v.get("variable")
        if var == "query" or var in seen:
            continue
        deduped.append(v)
        seen.add(var)
    start["data"]["variables"] = deduped
    for var, vtype, label, max_len in [
        ("step_guide", "paragraph", "当前步骤教程", 4000),
        ("step_correction_mode", "text-input", "步骤纠错模式", 16),
        ("teaching_doc", "text-input", "教学文档标识", 128),
        ("rules_doc", "text-input", "纠错文档标识", 128),
        ("teaching_section", "text-input", "教学章节标识", 128),
        ("rules_section", "text-input", "纠错章节标识", 128),
        ("retrieval_tags", "text-input", "检索标签", 256),
        ("kb_context", "paragraph", "后端预检索上下文", 8000),
        ("repeat_hint", "paragraph", "重复求助提示", 500),
        ("knowledge_type", "text-input", "知识类型", 48),
    ]:
        if var in seen:
            continue
        start["data"]["variables"].append(
            {"label": label, "variable": var, "type": vtype, "required": False, "max_length": max_len, "options": []}
        )
        seen.add(var)

    parse_node = old_nodes[N_PARSE]
    parse_node["data"]["code"] = INPUT_PARSE_CODE
    for out in ("fetch_url", "teaching_section", "rules_section", "retrieval_tags", "knowledge_type", "step_guide", "step_correction_mode"):
        parse_node["data"]["outputs"][out] = {"type": "string", "children": None}
    # 检索 query 由「步骤上下文组装」产出，不应挂在输入解析输出上
    for stale in ("retrieval_query_teaching", "retrieval_query_rules"):
        parse_node["data"]["outputs"].pop(stale, None)
    parse_vars = []
    for item in parse_node["data"]["variables"]:
        var_name = item.get("variable")
        if var_name == "query":
            parse_vars.append({"variable": "query", "value_selector": ["sys", "query"]})
        elif var_name in ("teaching_section", "rules_section", "retrieval_tags", "knowledge_type", "step_guide", "step_correction_mode"):
            continue
        else:
            parse_vars.append(item)
    for var in ("teaching_section", "rules_section", "retrieval_tags", "knowledge_type", "step_guide", "step_correction_mode"):
        parse_vars.append({"variable": var, "value_selector": [N_START, var]})
    parse_node["data"]["variables"] = parse_vars

    http_node = {
        "id": N_HTTP,
        "type": "custom",
        "width": 242,
        "height": 54,
        "position": {"x": 660, "y": 280},
        "positionAbsolute": {"x": 660, "y": 280},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": "HTTP：拉取实验 manifest",
            "type": "http-request",
            "method": "get",
            "url": "{{#env.WXZ_BACKEND_BASE_URL#}}{{#1779854194447.fetch_url#}}",
            "authorization": {"type": "no-auth", "config": None},
            "headers": "Accept: application/json",
            "params": "",
            "body": {"type": "none", "data": ""},
            "timeout": {"max_connect_timeout": 10, "max_read_timeout": 30, "max_write_timeout": 10},
            "selected": False,
        },
    }

    ctx_node = {
        "id": N_CTX,
        "type": "custom",
        "width": 242,
        "height": 52,
        "position": {"x": 960, "y": 280},
        "positionAbsolute": {"x": 960, "y": 280},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": "步骤上下文组装",
            "type": "code",
            "code_language": "python3",
            "code": STEP_CONTEXT_CODE,
            "outputs": {
                k: {"type": t, "children": None}
                for k, t in [
                    ("mode", "string"),
                    ("query", "string"),
                    ("experiment_code", "string"),
                    ("experiment_type", "string"),
                    ("step_id", "string"),
                    ("step_title", "string"),
                    ("step_desc", "string"),
                    ("data_json", "string"),
                    ("retrieval_query_teaching", "string"),
                    ("retrieval_query_rules", "string"),
                    ("step_context", "string"),
                    ("context_quality", "string"),
                    ("metadata_experiment_code", "string"),
                    ("metadata_step_id", "string"),
                    ("metadata_teaching_section", "string"),
                    ("metadata_rules_section", "string"),
                ]
            },
            "variables": [
                {"variable": v, "value_selector": sel}
                for v, sel in [
                    ("mode", [N_PARSE, "mode"]),
                    ("query", [N_PARSE, "query"]),
                    ("experiment_code", [N_PARSE, "experiment_code"]),
                    ("experiment_type", [N_PARSE, "experiment_type"]),
                    ("step_id", [N_PARSE, "step_id"]),
                    ("step_title", [N_PARSE, "step_title"]),
                    ("step_desc", [N_PARSE, "step_desc"]),
                    ("step_guide", [N_START, "step_guide"]),
                    ("step_correction_mode", [N_START, "step_correction_mode"]),
                    ("data_json", [N_PARSE, "data_json"]),
                    ("teaching_section", [N_START, "teaching_section"]),
                    ("rules_section", [N_START, "rules_section"]),
                    ("retrieval_tags", [N_START, "retrieval_tags"]),
                    ("kb_context", [N_START, "kb_context"]),
                    ("repeat_hint", [N_START, "repeat_hint"]),
                    ("http_body", [N_HTTP, "body"]),
                ]
            ],
        },
    }

    router = old_nodes[N_ROUTER]
    router["position"] = router["positionAbsolute"] = {"x": 1260, "y": 280}
    router["data"]["cases"] = [
        {
            "id": "vision",
            "case_id": "vision",
            "logical_operator": "and",
            "conditions": [
                {
                    "id": "mode-vision",
                    "varType": "string",
                    "variable_selector": [N_CTX, "mode"],
                    "comparison_operator": "is",
                    "value": "vision",
                }
            ],
        }
    ]

    code_queries = {
        "id": N_CODE_QUERIES,
        "type": "custom",
        "width": 242,
        "height": 52,
        "position": {"x": 2160, "y": 120},
        "positionAbsolute": {"x": 2160, "y": 120},
        "selected": False,
        "sourcePosition": "right",
        "targetPosition": "left",
        "data": {
            "title": "Code：解析检索 query",
            "type": "code",
            "code_language": "python3",
            "code": QUERY_REWRITE_CODE,
            "outputs": {
                "teaching_query": {"type": "string", "children": None},
                "rules_query": {"type": "string", "children": None},
                "intent": {"type": "string", "children": None},
            },
            "variables": [
                {"variable": "raw_text", "value_selector": [N_QUERY_REWRITE, "text"]},
                {"variable": "fallback_teaching", "value_selector": [N_CTX, "retrieval_query_teaching"]},
                {"variable": "fallback_rules", "value_selector": [N_CTX, "retrieval_query_rules"]},
            ],
        },
    }

    tpl_text = template_node(
        N_TPL_TEXT,
        "模板：聚合文本检索上下文",
        (
            "## 步骤上下文\n{{ step_context }}\n\n"
            "## 教学知识库\n{{ teaching_kb }}\n\n"
            "## 纠错规则库\n{{ rules_kb }}\n\n"
            "## 检索意图\n{{ intent }}"
        ),
        [
            {"variable": "step_context", "value_selector": [N_CTX, "step_context"]},
            {"variable": "teaching_kb", "value_selector": [N_KB_T_TEXT, "result"]},
            {"variable": "rules_kb", "value_selector": [N_KB_R_TEXT, "result"]},
            {"variable": "intent", "value_selector": [N_CODE_QUERIES, "intent"]},
        ],
        3060,
        120,
    )

    tpl_vis = template_node(
        N_TPL_VIS,
        "模板：聚合视觉检索上下文",
        (
            "## 步骤上下文\n{{ step_context }}\n\n"
            "## 纠错规则库（主）\n{{ rules_kb }}\n\n"
            "## 教学知识库（辅）\n{{ teaching_kb }}"
        ),
        [
            {"variable": "step_context", "value_selector": [N_CTX, "step_context"]},
            {"variable": "rules_kb", "value_selector": [N_KB_R_VIS, "result"]},
            {"variable": "teaching_kb", "value_selector": [N_KB_T_VIS, "result"]},
        ],
        2160,
        480,
    )

    code_vis = old_nodes[N_CODE_VIS]
    for v in code_vis["data"]["variables"]:
        if v["variable"] in ("experiment_type", "experiment_code", "step_id", "step_title"):
            v["value_selector"] = [N_CTX, v["variable"]]

    nodes = [
        start,
        parse_node,
        http_node,
        ctx_node,
        router,
        classifier_node(N_CLASSIFIER, 1560, 80),
        answer_node(
            N_ANS_OFFTOPIC,
            "回复：非实验问题",
            "我主要负责**大学物理实验**现场指导与纠错（牛顿环、空气劈尖、读数显微镜等）。\n\n请围绕当前实验的**操作、读数、数据或现象**提问；需要检查操作是否正确，可**上传当前步骤照片**。\n\n若你在做其中某个实验，也可以直接说实验名和你的问题。",
            1860,
            20,
        ),
        llm_node(N_QUERY_REWRITE, "LLM：检索 query 改写", QUERY_REWRITE_PROMPT, 1860, 120, temp=0.1),
        code_queries,
        kb_node(N_KB_T_TEXT, "知识检索：教学库（文本）", "改写后的 teaching_query", 2460, 80, N_CODE_QUERIES, "teaching_query", "teaching", "metadata_teaching_section"),
        kb_node(N_KB_R_TEXT, "知识检索：纠错规则库（文本）", "改写后的 rules_query", 2460, 220, N_CODE_QUERIES, "rules_query", "correction_rule", "metadata_rules_section"),
        tpl_text,
        llm_node(N_LLM_DRAFT, "LLM：文本初稿", TEXT_DRAFT_PROMPT, 3360, 120, memory=True),
        llm_node(N_LLM_POLISH, "LLM：文本质检精简", TEXT_POLISH_PROMPT, 3660, 120, temp=0.15),
        answer_node(N_ANS_TEXT, "回复：文本指导", f"{{{{#{N_LLM_POLISH}.text#}}}}", 3960, 120),
        kb_node(N_KB_R_VIS, "知识检索：纠错规则库（视觉）", "步骤视觉规则", 1560, 420, N_CTX, "retrieval_query_rules", "correction_rule", "metadata_rules_section"),
        kb_node(N_KB_T_VIS, "知识检索：教学库（视觉辅助）", "步骤背景", 1560, 560, N_CTX, "retrieval_query_teaching", "teaching", "metadata_teaching_section"),
        tpl_vis,
        llm_node(N_LLM_VIS, "LLM：多模态视觉识别", VISION_PROMPT, 2460, 480, model="qwen3-vl-plus", vision=True, temp=0.1),
        code_vis,
        llm_node(N_LLM_FB, "LLM：视觉反馈初稿", FEEDBACK_PROMPT, 3060, 480),
        llm_node(N_LLM_POLISH_VIS, "LLM：视觉反馈质检", POLISH_VIS_PROMPT, 3360, 480, temp=0.15),
        answer_node(
            N_ANS_VIS,
            "回复：视觉纠错",
            f"{{{{#{N_LLM_POLISH_VIS}.text#}}}}\n\n---\n\n可视化标注 regions：\n{{{{#{N_CODE_VIS}.regions#}}}}",
            3660,
            480,
            height=137,
        ),
    ]

    graph["nodes"] = nodes
    graph["edges"] = [
        edge(f"{N_START}-parse", N_START, "source", N_PARSE, "start", "code"),
        edge(f"{N_PARSE}-http", N_PARSE, "source", N_HTTP, "code", "http-request"),
        edge(f"{N_HTTP}-ctx", N_HTTP, "source", N_CTX, "http-request", "code"),
        edge(f"{N_CTX}-router", N_CTX, "source", N_ROUTER, "code", "if-else"),
        # text
        edge(f"{N_ROUTER}-cls", N_ROUTER, "false", N_CLASSIFIER, "if-else", "question-classifier"),
        edge(f"{N_CLASSIFIER}-off", N_CLASSIFIER, "1", N_ANS_OFFTOPIC, "question-classifier", "answer"),
        edge(f"{N_CLASSIFIER}-rw2", N_CLASSIFIER, "2", N_QUERY_REWRITE, "question-classifier", "llm"),
        edge(f"{N_CLASSIFIER}-rw3", N_CLASSIFIER, "3", N_QUERY_REWRITE, "question-classifier", "llm"),
        edge(f"{N_QUERY_REWRITE}-cq", N_QUERY_REWRITE, "source", N_CODE_QUERIES, "llm", "code"),
        edge(f"{N_CODE_QUERIES}-kbt", N_CODE_QUERIES, "source", N_KB_T_TEXT, "code", "knowledge-retrieval"),
        edge(f"{N_KB_T_TEXT}-kbr", N_KB_T_TEXT, "source", N_KB_R_TEXT, "knowledge-retrieval", "knowledge-retrieval"),
        edge(f"{N_KB_R_TEXT}-tpl", N_KB_R_TEXT, "source", N_TPL_TEXT, "knowledge-retrieval", "template-transform"),
        edge(f"{N_TPL_TEXT}-draft", N_TPL_TEXT, "source", N_LLM_DRAFT, "template-transform", "llm"),
        edge(f"{N_LLM_DRAFT}-polish", N_LLM_DRAFT, "source", N_LLM_POLISH, "llm", "llm"),
        edge(f"{N_LLM_POLISH}-ans", N_LLM_POLISH, "source", N_ANS_TEXT, "llm", "answer"),
        # vision
        edge(f"{N_ROUTER}-vis-r", N_ROUTER, "vision", N_KB_R_VIS, "if-else", "knowledge-retrieval"),
        edge(f"{N_KB_R_VIS}-vis-t", N_KB_R_VIS, "source", N_KB_T_VIS, "knowledge-retrieval", "knowledge-retrieval"),
        edge(f"{N_KB_T_VIS}-tplv", N_KB_T_VIS, "source", N_TPL_VIS, "knowledge-retrieval", "template-transform"),
        edge(f"{N_TPL_VIS}-vllm", N_TPL_VIS, "source", N_LLM_VIS, "template-transform", "llm"),
        edge(f"{N_LLM_VIS}-code", N_LLM_VIS, "source", N_CODE_VIS, "llm", "code"),
        edge(f"{N_CODE_VIS}-fb", N_CODE_VIS, "source", N_LLM_FB, "code", "llm"),
        edge(f"{N_LLM_FB}-pv", N_LLM_FB, "source", N_LLM_POLISH_VIS, "llm", "llm"),
        edge(f"{N_LLM_POLISH_VIS}-vans", N_LLM_POLISH_VIS, "source", N_ANS_VIS, "llm", "answer"),
    ]

    dumped = yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=120)
    for out in (OUT_ASCII, OUT_ZH):
        out.write_text(dumped, encoding="utf-8")
        print(f"wrote {out}")

    # keep step-aware alias in sync for importers expecting previous filename
    step_aware = ROOT / "docs" / "dify" / "wuxiaozhi-correction-guidance-step-aware.yml"
    step_aware_zh = ROOT / "docs" / "dify" / "物理实验智能纠错与指导-步骤感知完整版.yml"
    step_aware.write_text(dumped, encoding="utf-8")
    step_aware_zh.write_text(dumped, encoding="utf-8")
    print(f"wrote {step_aware}")


if __name__ == "__main__":
    main()
