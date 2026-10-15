# Next.js 迁移记录

目标：单个 Next.js / React / TypeScript 应用承担页面、业务 API、AI 流式接口、数据与设备采集；删除 Vue、Vite、Spring Boot、Java 和 Python 采集服务代码。

## 已完成

- 业务与页面迁移至 `src/app`、`src/components`、`src/server`。
- 运行时数据库为 Node.js 内置 SQLite（`data/wuxiaozhi.sqlite`）。
- 从本机 MySQL 导出（`scripts/export-mysql.ts`），校验表行数写入 `data/migration-verification.json`，并导入为正式 SQLite（替换原先仅含 H2 的旧库；旧库备份在 `data/backups/pre-mysql-era/`）。
- 删除仓库内 `frontend/`（Vue/Vite）及绝大部分 `backend/`（Spring Boot）；部署脚本改为 Next.js（`scripts/DEPLOY.template.txt`、`scripts/pack-deploy.ps1`）。
- 实验与教程内容以 `content/`、`public/` 为准（不再依赖 `backend/src/main/resources/experiments`）。

## 验收

```bash
npm run typecheck
npm test
npm run build
```

本地启动：`npm run dev`，默认端口见 `.env.local` 的 `PORT`（本机常用 5174）。

## 备注

- 若 `backend/` 空目录因进程占用无法删除，先结束 Java/Python 旧后端再手动删除该目录。
- 原始数据在 Git 忽略的 `data/` 下，勿提交用户数据。
