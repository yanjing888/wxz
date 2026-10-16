# 物小智知识库维护

实验教学知识和纠错规则只在后端资源目录维护，不再保留多份源文件。

| 内容 | 源文件 | Dify 环境变量 |
|---|---|---|
| 教学知识 | `backend/src/main/resources/experiments/<实验编号>/teaching-knowledge.md` | `DIFY_KB_TEACHING_DATASET_ID` |
| 纠错规则 | 同目录的 `visual-rules.md` | `DIFY_KB_CORRECTION_RULES_DATASET_ID` |

实验与步骤的检索映射见 `backend/src/main/resources/knowledge-map.yml`。`steps/*.md` 和 `report-guide.md` 也由后端直接读取，用于页面操作引导和报告指导。

## 修改和导出

1. 编辑对应实验目录的知识 Markdown。教学知识写原理、操作、公式和报告指导；纠错规则写可见证据、错误判断及修正方法。
2. 保留 frontmatter 中的 `experiment_code`、`kb_type`、`doc`、`sectionKey`，以及各步骤的 `section_key`。
3. 在项目根目录运行：

   ```bash
   python scripts/sync_kb.py
   ```

脚本自动读取所有带 `manifest.json` 的实验目录，生成 `docs/kb/export/teaching/` 和 `docs/kb/export/correction_rules/`。每篇 Markdown 配有同名 `.metadata.json`，包含导入 Dify 所需的元数据。导出过程不会改写源文件；源文件缺失会直接报错。

导出目录是生成文件，已加入 Git 忽略。需要时重新运行脚本，不手工编辑导出副本。也可以指定临时导出位置：

```bash
python scripts/sync_kb.py --output .codex-tmp/kb-export
```

## 更新 Dify

- 将两类导出 Markdown 分别导入教学库和纠错规则库，并按 `.metadata.json` 设置文档元数据。
- 在 Dify 工作流中绑定相应知识库，用 `experiment_code` 过滤当前实验。
- 将知识库 ID 写入 `config/dify.env`，更新配置后重启后端。
- 完整节点绑定方法见 [实验指导工作流](../dify/SETUP-GUIDANCE-WORKFLOW.md)；教师报告预评见 [报告预评工作流](../dify/SETUP-REPORT-REVIEW-WORKFLOW.md)。