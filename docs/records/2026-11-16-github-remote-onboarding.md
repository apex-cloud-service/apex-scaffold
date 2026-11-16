# GitHub 远端接入记录（两仓 `origin`）

| 项 | 值 |
| --- | --- |
| 执行日期 | 2026-11-16 |
| 涉及仓 | `apex-scaffold`（后端）、`apex-admin-web`（前端） |
| 远端 | `git@github.com:apex-cloud-service/apex-scaffold.git`、`git@github.com:apex-cloud-service/apex-admin-web.git` |
| 认证身份 | SSH，密钥 `~/.ssh/edu2519`（注释身份 `anqli@outlook.com`），GitHub 账号 `anqli8341` |
| 影响 | 只增远端与合并一条空仓 `Initial commit`；业务代码、文档正文零改动 |

## 1. 关联方式

```bash
# 两仓同一步骤（以配置为载体，不写死到文档正文以外）
git remote add origin git@github.com:apex-cloud-service/<repo>.git
# 22 端口被网络阻断 → 固定走 443
git config core.sshCommand "ssh -o Hostname=ssh.github.com -p 443"
ssh -T -p 443 git@ssh.github.com     # 期望：Hi anqli8341! You've successfully authenticated...
```

踩坑记录：

| 现象 | 根因 | 处置 |
| --- | --- | --- |
| 形如 `anqli@outlook.com:apex-cloud-service/apex-scaffold.git` 的地址连不上 | git 把 `anqli@` 当 SSH 用户名、把 `outlook.com` 当主机，实际连到 `52.96.91.34:22` | 正确写法是 `git@github.com:apex-cloud-service/<repo>.git`；`anqli@outlook.com` 只是密钥注释身份 |
| `Connection closed by ... port 22` | 本机 22 端口被网络拦截 | `core.sshCommand` 固定 `Hostname=ssh.github.com -p 443` |
| `Permission denied (publickey)` | 私钥 `edu2519` 带口令（bcrypt 加密），非交互环境无法解密 | 先 `ssh-add --apple-use-keychain ~/.ssh/edu2519` 载入 agent，再推送 |

## 2. 远端初始提交的合并决策

远端 `main` 原本只有一条 `Initial commit`（`93f249e8`），与本地 336 个提交**无共同祖先**，仅含 2 个文件：

| 文件 | 远端内容 | 本地内容 | 决策 |
| --- | --- | --- | --- |
| `LICENSE` | GitHub 模板 Apache License 2.0（201 行） | 项目既有 MIT（238 行，含自有版权声明） | **保留本地 MIT**（项目既定许可），不采用建仓模板 |
| `README.md` | 单行 `# apex-scaffold` 桩 | 303 行完整说明 | **保留本地完整版** |

合并命令与结果：

```bash
git fetch origin
git merge --allow-unrelated-histories origin/main   # 两个 add/add 冲突
git checkout --ours LICENSE README.md && git add ...  # 保留本地
```

合并提交：`28cfd7cd`。合并后校验：`LICENSE` 哈希 `085dce9a…`（与合并前一致）、`README.md` 303 行未变。

> **待业主确认**：远端建仓时选的 Apache 2.0 与项目既有 MIT 不一致，本轮按「本地优先」保留 MIT。若希望改用 Apache 2.0，需单独一次提交同时改 `LICENSE` 与各仓声明。

## 3. 推送与验证

```bash
cd /Users/work/<repo> && git push -u origin main
git ls-remote origin refs/heads/main          # 应等于本地 HEAD
```

## 4. 与规范的对应

- 规范 §5.3 已同步更新：远端地址、443 固定、口令私钥处理、远端存在另一条历史时的合并口径、`push -f` 仍禁止。
- 推送受阻期间的兜底归档见 `/Users/work/backup/`（`git archive` 快照 + `git bundle` 全历史 + `SHA256SUMS.txt`）。
