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

## 3. 推送结果（实测）

| 仓 | 远端原 HEAD | 推送后 HEAD | 结果 |
| --- | --- | --- | --- |
| `apex-scaffold` | `93f249e8`（Initial commit） | **`1a5c8525`** | `93f249e8..1a5c8525  main -> main`（当时分支名 `main`，后重命名为 `master`，见 §5） |
| `apex-admin-web` | `b613564`（Initial commit） | **`dce7aa0`** | `b613564..dce7aa0  main -> main`（同上，后重命名为 `master`） |

校验（两仓一致）：

```bash
git rev-parse main            # 1a5c8525…（后端） / dce7aa0…（前端）
git ls-remote origin refs/heads/main   # 与本地一致
```

**许可差异（待业主决策）**：前端仓本地原本没有 `LICENSE`，远端 `Initial commit` 带的是 GitHub 模板 Apache 2.0，合并时**保留为该仓唯一许可文件**；后端仓本地有既有 MIT，合并时保留 MIT。两仓当前许可不一致（前端 Apache 2.0 / 后端 MIT），需统一时单独一次提交改两仓 `LICENSE` 与声明。

## 4. 与规范的对应

- 规范 §5.3 已同步更新：远端地址、443 固定、口令私钥处理、远端存在另一条历史时的合并口径、`push -f` 仍禁止。
- 推送受阻期间的兜底归档见 `/Users/work/backup/`（`git archive` 快照 + `git bundle` 全历史 + `SHA256SUMS.txt`）。

## 5. 分支重命名：`main` → `master`（2026-11-16）

| 仓 | 本地命令 | 结果 |
| --- | --- | --- |
| `apex-scaffold` | `git branch -m main master` → `git push -u origin master` | `* [new branch] master -> master`，已建立 `master` 跟踪 |
| `apex-admin-web` | 同上 | `* [new branch] master -> master`，已建立 `master` 跟踪 |

注意事项：

1. 远端**旧分支 `main` 仍然存在**：GitHub 拒绝删除默认分支（`refusing to delete the current branch: refs/heads/main`）。要把默认分支改成 `master` 需在 **Settings → General → Default branch** 切换（或提供 PAT 后用 `gh api -X PATCH repos/{owner}/{repo} -f default_branch=master`），之后 `git push origin --delete main` 才能成功。
2. 重命名只改分支指针，**提交历史零变化**（`master` 与重命名前的 `main` 指向同一提交）。
3. 本地各仓 `origin/HEAD` 可能仍指向旧名，切换默认分支后执行 `git remote set-head origin -a` 修正。

## 6. 许可统一：两仓均为 MIT（2026-11-16）

| 仓 | 原状态 | 处理 |
| --- | --- | --- |
| `apex-scaffold` | MIT + `LICENSE-COMMERCIAL.md`（基础版 MIT、标准版/企业版商业授权） | **不改**，这是既有双轨设计（见 `docs/07-commercial/COMMERCIAL_VERSIONS.md`） |
| `apex-admin-web` | 合并远端时带入 GitHub 模板 Apache 2.0，且本地 `package.json` 无 `license` 字段 | 替换为 **MIT**（`Copyright (c) 2026 Apex Admin Web Contributors`），`package.json` 增 `"license": "MIT"` |

选择 MIT 的理由（对比 Apache 2.0）：

| 维度 | MIT | Apache 2.0 |
| --- | --- | --- |
| 长度/可读性 | 约 170 词，一段读完 | 约 1 万字符，含术语定义、贡献条款、免责细则 |
| 专利授权 | **无明示专利许可** | **有明示专利授权**（贡献者授予使用者专利许可，并含专利诉讼反制条款） |
| 商标 | 不涉及商标条款 | 明确不授予商标/品牌使用权，需另议 |
| 分发义务 | 保留版权与许可声明即可 | 保留声明 + 若含 `NOTICE` 文件须一并分发 + 标注修改 |
| 与 GPLv2 兼容 | 是 | 否（与 GPLv2 不兼容，与 GPLv3 兼容） |
| 适用场景 | 内部项目、个人/小团队、希望最低合规成本 | 有专利风险顾虑、需明确专利授权的对外开源项目、基金会/大企业托管项目 |

本项目选择 MIT 的依据：① 后端已是 MIT 且与商业授权双轨配套，改动会牵动 `COMMERCIAL_VERSIONS.md`/`LICENSE-COMMERCIAL.md` 与既有商业文案；② 两仓同属一个内部产品线，统一到 MIT 可避免「前端 Apache + 后端 MIT」的合规歧义；③ 项目暂无对外专利防御需求。若后续需要专利条款保护，应一次性把两仓改为 Apache 2.0 并同步 `NOTICE`、`package.json`/`pom.xml` 元数据与商业文档，不要只改一处。
