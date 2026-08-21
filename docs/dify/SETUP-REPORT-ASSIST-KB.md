# 报告助手 · 知识库检索配置（Dify）

本文说明：如何把立项 Word 整理的三实验知识导入 Dify，并在 **report-assist（实验报告分析）** 工作流里按 `experiment_code` 检索，使助手只引用**当前实验**的目的、步骤、公式与注意事项。

---

## 1. 方案是否可行？

**可行，且推荐。**

| 数据来源 | 用途 | 是否替代过程数据 |
|---------|------|------------------|
| **知识库（本方案）** | 实验目的、原理、步骤、公式、注意事项、报告写法 | 否 |
| **后端注入 `data_logs_json`** | 学生本次提交的测量值 | — |
| **报告草稿 `purpose`…`discussion`** | 学生已写内容 | — |

知识库解决「这门实验应该写什么」；`data_logs_json` 解决「这次测了什么」。两者叠加，报告助手才能既规范又贴数据。

物小智主问答工作流已用同样思路（教学库 + `experiment_code` 过滤），报告助手可复用 **同一 teaching 数据集**，不必另建三份库。

---

## 2. Excel 已生成

已从立项文档提取并分段：

**`docs/kb/import/三实验教学知识库-Dify导入.xlsx`**

**格式：每个实验 1 行，共 3 行。**

| 列 | 含义 |
|----|------|
| `experiment_code` | `newton_rings` / `air_wedge_thickness` / `microscope_length_measurement` |
| `experiment_name` | 中文实验名 |
| `content` | **整篇**实验文档（目的、原理、步骤、注意事项、附录） |
| `kb_type` | 固定 `teaching` |
| `sectionKey` | 与 experiment_code 相同 |
| `keywords` | 检索关键词 |

重新生成：

```bash
python scripts/extract_three_experiments_to_excel.py
```

源 Word：`d:\yanjing\00 资料\智能体\26立项--物小智\牛顿环与空气劈尖三个实验整理.docx`

---

## 3. 在 Dify 导入知识库

### 3.1 新建或选用数据集

推荐：**合并进现有「物理实验教学知识点通用库」**（与 `DIFY_KB_TEACHING_DATASET_ID` 一致）。

若单独建库：命名如 `物小智-三实验报告教学-2026`，导入后把 UUID 写入 `config/dify.env`。

### 3.2 导入 Excel

1. Dify → **知识库** → 选择 teaching 数据集 → **添加文件**
2. 上传 `三实验教学知识库-Dify导入.xlsx`
3. 分段策略：
   - 每行已是**完整一篇实验**，建议选 **General / 自定义** 且 **不再二次切分**（或按段落切，最大段长设大一些，如 2000+ 字）
   - 避免把一行再切成很多碎片，否则检索会丢上下文
4. **元数据映射** — 将 Excel 列映射为文档 metadata：

   | Excel 列 | Dify 元数据字段 |
   |----------|----------------|
   | experiment_code | experiment_code |
   | kb_type | kb_type |
   | sectionKey | sectionKey |

5. 索引模式：**高质量**（向量 + 关键词）
6. 等待嵌入完成

### 3.3 验证检索

在知识库「召回测试」中：

- Query：`牛顿环 暗环直径 空程差`
- Metadata filter：`experiment_code = newton_rings`

应命中牛顿环相关片段，不应大量出现空气劈尖内容。

---

## 4. 报告助手工作流：加知识库检索节点

在 **`wuxiaozhi-report-assist-branched.yml`**（多分支版）或自建 Chatflow 中插入检索，推荐拓扑：

```mermaid
flowchart LR
  Start --> Parse[代码: 输入解析]
  Parse --> KB[知识库检索]
  KB --> Classifier[问题分类器]
  Classifier --> LLM1[LLM 各分支]
  LLM1 --> Answer[回答]
```

也可放在 **每个 LLM 分支前** 各放一个检索节点（更精细，成本略高）。一般 **Parse 后共用一个检索** 即可。

### 4.1 新增「知识库检索」节点

| 配置项 | 值 |
|--------|-----|
| 查询文本 | `{{#parse.experiment_name#}} {{#parse.query#}}` |
| 检索条数 Top K | 5～8 |
| 相似度阈值 | 0.5～0.6（按效果调） |
| 数据集 | teaching 库 UUID |
| **元数据过滤** | 见下 |

### 4.2 元数据过滤（按实验筛选）

在检索节点 → **Metadata 过滤**：

```
experiment_code == {{#start.experiment_code#}}
```

若 Dify 界面是 JSON 形式，示例：

```json
{
  "logical_operator": "and",
  "conditions": [
    {
      "name": "experiment_code",
      "comparison_operator": "is",
      "value": "{{#start.experiment_code#}}"
    }
  ]
}
```

**开始节点**必须声明变量 `experiment_code`（物小智后端 `buildToolInputs` 已注入，与 manifest 一致）。

可选：报告类问题再加 `section_type` 条件，例如完整性检查时优先 `purpose_principle`、`step`、`caution`：

```
kb_type == teaching
experiment_code == {{#start.experiment_code#}}
```

### 4.3 各 LLM 提示词引用检索结果

在完整性 / 误差 / 表格 / 综合 四个 LLM 的 system 提示词**顶部**增加：

```text
## 本实验教学知识（知识库召回，仅作写作规范参考）
{{#kb_teaching.result#}}

规则：
1. 优先依据上述知识与本次 data_logs_json，不要编造测量值。
2. 若召回为空，明确说明「知识库未命中」，仍可根据 report_knowledge 与学生草稿回答。
3. 不要写「根据知识库检索」等元话语。
```

节点变量名以 Dify 实际输出为准（常见为 `知识库检索.result` 或自定义节点 id 如 `kb_teaching.result`）。

---

## 5. 与物小智后端对齐

### 5.1 环境变量

`config/dify.env`：

```env
DIFY_KB_TEACHING_DATASET_ID=你的-teaching-数据集-UUID
DIFY_WF_REPORT_ASSIST=app-报告助手-Key
```

### 5.2 后端已注入、工作流「开始」需声明的变量

| 变量 | 来源 |
|------|------|
| `experiment_code` | 当前实验 code |
| `experiment_name` | 实验中文名 |
| `query` | 用户问题（Chatflow 系统变量） |
| `data_logs_json` | 本次 session 测量数据 |
| `purpose` … `discussion` | 报告草稿 |
| `report_knowledge` / `report_path` | report-guide.md |

报告页小智辅助已传 `sessionId` + 草稿 JSON；**无需** HTTP 再拉知识库，Dify 内检索即可。

### 5.3 与现有 teaching Markdown 的关系

项目内 `docs/kb/export/teaching/*.md` 与 Excel 内容同源（立项 Word / teaching-knowledge）。建议：

- **只维护一份**：要么继续用 `sync_kb.py` 同步 Markdown 导入，要么用 Excel 增量导入
- 避免同一实验重复导入导致召回冗余

---

## 6. 按意图使用不同检索 Query（可选增强）

在 **Parse 代码节点** 增加 `kb_query` 输出：

```python
def main(query="", experiment_name="", **kwargs):
    q = (query or "").strip()
    base = experiment_name or "大学物理实验"
    if any(k in q for k in ["缺", "完整", "检查"]):
        kb_query = f"{base} 实验目的 实验步骤 报告结构 注意事项"
    elif any(k in q for k in ["误差", "讨论"]):
        kb_query = f"{base} 注意事项 误差 空程差 读数"
    elif any(k in q for k in ["表格", "数据"]):
        kb_query = f"{base} 数据处理 公式 测量"
    else:
        kb_query = f"{base} {q}"
    return {"kb_query": kb_query}
```

检索节点 Query 改为 `{{#parse.kb_query#}}`，召回更准。

---

## 7. 检查清单

- [ ] Excel 已导入 teaching 数据集，且 **experiment_code 元数据** 可过滤
- [ ] 召回测试：牛顿环 query 不会召回劈尖为主的内容
- [ ] 报告助手工作流「开始」含 `experiment_code`
- [ ] Parse → 知识库检索 → 分类器 → LLM 链路通
- [ ] LLM 提示词引用检索结果 + `data_logs_json`
- [ ] `DIFY_WF_REPORT_ASSIST` 与 `DIFY_KB_TEACHING_DATASET_ID` 已配置并重启后端
- [ ] 学生端：实验台提交过数据 + 报告页有 sessionId 后再测小智

---

## 8. 常见问题

**Q：只导入 Excel，不写 data_logs，助手能写数据表吗？**  
A：只能给**格式与公式**建议，不能填真实数字；数字必须来自 `data_logs_json` 或学生草稿。

**Q：能否一个工作流绑两个库（teaching + correction_rules）？**  
A：可以两个检索节点并行，报告助手通常 **只要 teaching**；纠错规则库留给实验台主问答/视觉纠错。

**Q：general 附录段（CCD、通用警示）every 实验都要召回？**  
A：导入时 `experiment_code=general`；检索过滤改为：

```
experiment_code == {{#start.experiment_code#}} OR experiment_code == general
```

（Dify 若不支持 OR，则 general 片段复制三份各带对应 experiment_code，或单独第二次检索 general Top2 拼进提示词。）
