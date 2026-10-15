# Next.js 迁移代码恢复说明

## 已执行的本地恢复（2026-09-29）

| 来源 | 结果 |
|------|------|
| Cursor 本地历史 `%APPDATA%\Cursor\User\History` | 约 225 个文件（多为 `frontend/`、`backend/`） |
| GitHub `yanjing888/wxz` main | 约 500 个文件（Vue + Spring，**不含** Next.js `src/`） |
| 本机全部 `.cursor/projects/**/agent-transcripts` 中的 Write | **无** `src/`、`package.json`（根目录）等 Next 文件正文 |

## 为何 Next.js 找不回

另一工具完成的 Next.js 全栈（`src/`、`content/`、`public/`、根 `package.json` 等）**未出现在**：

- GitHub 远程仓库
- Cursor 本地 History（这些文件很可能从未在编辑器里打开过）
- 当前机器上可读的 agent-transcripts（该次会话不在本机 jsonl 里，常见于 **Cloud Agent / 其他窗口的独立会话**）

## 请你立刻尝试（可能唯一保留完整代码的地方）

1. **打开「另一个工具」那条对话**（约 48 分钟、MySQL/SQLite 迁移进度那张图）
2. 若界面有 **Restore checkpoint / 恢复到检查点**，对整个项目执行恢复
3. 若无检查点：在该对话里让 AI **重新导出**或 **zip 整个项目** 再解压到本目录
4. **Cursor Timeline**：在文件树对任意曾存在的 Next 文件路径右键 → Open Timeline（若还有条目）

## 本目录可重复运行的脚本

```bash
node scripts/restore-cursor-history.mjs
node scripts/extract-transcript-writes.mjs
```

## 若以上都没有

只能基于现有 `backend/` + `frontend/` + `docs/migration-nextjs.md` **重新生成** Next.js 全栈（与迁移末期的架构一致），并保留旧目录不删。
