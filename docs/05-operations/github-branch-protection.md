# GitHub 分支保护规则清单（master / test / dev）

> **采样日**：2026-11-16 · 覆盖 `apex-cloud-service/apex-scaffold`、`apex-cloud-service/apex-admin-web`
> **规则依据**：`docs/specs/project-governance.md` §5.3（分支模型：`dev` 默认工作 → `master` 生产 → `test` 验收）
> **唯一正文在本仓**；前端仓 `docs/guides/github-branch-protection.md` 为指向副本。

---

## 0. 现状与前提

| 项 | 现状（2026-11-16 实测） |
| --- | --- |
| 远程 | `origin` = `git@github.com:apex-cloud-service/<repo>.git`，SSH 走 443 |
| 分支 | `dev`（默认工作）/ `master`（生产，远端默认分支）/ `test`（验收）；三支同点，无 `main` |
| 已开启的保护 | **无**（`GET /repos/.../branches/master/protection` 匿名访问返回 403，无法核实；`gh auth status` 未登录） |
| 可用手段 | ① GitHub 网页 Settings（推荐，无需凭据）；② PAT + `gh api`（需 `repo` 与 `admin:repo_hook` 权限）；③ 无 token 时**不能**用 API |

> **重要前提**：本仓当前**没有可靠的 CI 状态检查**可挂。若在保护规则里启用 "Require status checks"，PR 会因「检查项永远不出现」而永久阻塞。**先按下方「阶段一」只开人工与历史约束，等 CI 就绪再进「阶段二」。**

---

## 1. 目标规则（推荐配置）

| 规则项 | `master` | `test` | `dev` |
| --- | --- | --- | --- |
| Require a pull request before merging | **开** | **开** | 关（允许直推） |
| Required approvals | **1**（单人仓可设 0，靠 PR 留痕） | 1 | — |
| Dismiss stale approvals | 开 | 开 | — |
| Require review from Code Owners | 有 `CODEOWNERS` 时开 | 同 | — |
| Require status checks to pass | 阶段二开（绑定具体 job 名） | 阶段二开 | 关 |
| Require conversation resolution | 开 | 开 | 关 |
| Require linear history | **开**（配「Rebase and merge」保持线性） | 开 | 关 |
| Require signed commits | 可选（团队有 GPG 再开） | 可选 | — |
| Require deployments to succeed | 有环境时开 | — | — |
| Lock branch（只读） | **关**（否则 `dev` 无法同步） | 关 | 关 |
| Allow force pushes | **关** | **关** | **关** |
| Allow deletions | **关** | **关** | **关** |
| 允许绕过（bypass） | 仅仓库管理员，且需在 `docs/records/` 留痕 | 同 | — |

**为什么这样配**：
- `master` / `test` 禁直推 + 线性历史 → 保证「生产与验收只能来自 `dev`」，与治理规范里的 `--ff-only` 合入口径一致；
- `dev` 允许直推（团队当前是小规模、单人主力），但禁 force push / 禁删除，防止误伤；
- `Allow deletions` 全关，是因为本仓曾出现「远端 `main` 因是默认分支删不掉」的折腾，保护开着更安全。

---

## 2. 阶段一：网页操作（不需要任何凭据，最省事）

对**两个仓**分别执行：

1. 打开 `https://github.com/apex-cloud-service/apex-scaffold/settings/branches`
2. 点 **Add branch protection rule**（或 Add ruleset）
3. **Branch name pattern**：`master`
4. 勾选：
   - ☑ Require a pull request before merging（Required approvals = 1）
   - ☑ Dismiss stale pull request approvals when new commits are pushed
   - ☑ Require conversation resolution before merging
   - ☑ Require linear history
   - ☐ Require status checks（阶段二再勾）
   - ☐ Allow force pushes / ☐ Allow deletions（**保持不勾**）
5. Create / Save
6. 重复上面的步骤，pattern 填 `test`（配置相同）
7. 再建一条 pattern `dev`：**只关** Allow force pushes 与 Allow deletions，其余全不勾
8. 前端仓 `https://github.com/apex-cloud-service/apex-admin-web/settings/branches` 同样做一遍

> 新版 GitHub 用 **Rulesets**（Settings → Rules → Rulesets）时，勾选项名称略有差异，含义一致：目标分支 + Require PR + Require linear history + Block force pushes。

---

## 3. 阶段二：PAT + `gh api`（可脚本化、可复核）

```bash
export GITHUB_TOKEN=<PAT，权限 repo>
gh auth status                      # 期望显示已登录

# master：禁直推 + 线性历史 + 禁强推/禁删除
for repo in apex-scaffold apex-admin-web; do
  gh api -X PUT "repos/apex-cloud-service/${repo}/branches/master/protection" \
    -f "required_status_checks=null" \
    -f "enforce_admins=true" \
    -f "required_pull_request_reviews[required_approving_review_count]=1" \
    -f "required_pull_request_reviews[dismiss_stale_reviews]=true" \
    -f "restrictions=null" \
    -F "allow_force_pushes=false" \
    -F "allow_deletions=false" \
    -F "required_conversation_resolution=true" \
    -F "required_linear_history=true"
done

# test：同上（把 master 换成 test）
# dev：只禁强推/禁删除
for repo in apex-scaffold apex-admin-web; do
  gh api -X PUT "repos/apex-cloud-service/${repo}/branches/dev/protection" \
    -f "required_status_checks=null" -f "required_pull_request_reviews=null" \
    -f "enforce_admins=false" -f "restrictions=null" \
    -F "allow_force_pushes=false" -F "allow_deletions=false" \
    -F "required_conversation_resolution=false" -F "required_linear_history=false"
done
```

**复核**（不开保护就等于没配）：

```bash
gh api repos/apex-cloud-service/apex-scaffold/branches/master/protection | jq '.required_pull_request_reviews, .allow_force_pushes, .allow_deletions'
# 期望：required_pull_request_reviews 非 null；allow_force_pushes.enabled=false；allow_deletions.enabled=false
```

---

## 4. 开启保护后，流程必须这样改

保护一开，原来「本地 `git merge --ff-only dev && git push origin master`」会被**拒绝**（`protected branch hook declined`）。改为 PR 流程：

```bash
# 1) 本地把 dev 推到远端（dev 允许直推）
git checkout dev && git push

# 2) 开 PR：dev → master
gh pr create --base master --head dev --title "release: dev → master ($(date +%F))" --body "见 docs/reference/ai-knowledge/07-change-memory.md 最近条目"

# 3) 用 Rebase and merge（保持线性历史，等价于 ff）
gh pr merge --rebase --delete-branch=false

# 4) test 同样：开 PR dev → test，Rebase and merge
```

没有 `gh` 时，网页点 **Rebase and merge**（不要用 "Create a merge commit"，否则线性历史规则会拒绝）。

**说明**：`Rebase and merge` 会在 `master` 上生成新提交哈希，与 `dev` 不再「同点」，但内容一致；这是保护换来的代价，属预期。

---

## 5. 验收清单（复制即用）

| # | 检查 | 判据 |
| --- | --- | --- |
| 1 | 两个仓 `master` 都有保护 | Settings 里能看到规则，或 `gh api .../protection` 返回非 404 |
| 2 | `master` 禁止直推 | 本地 `git push origin master:master` 被拒（可先用测试提交验证，随后删掉） |
| 3 | `master` 禁止强推 | `git push --force origin master` 被拒 |
| 4 | `master` 禁止删除 | `git push origin --delete master` 被拒 |
| 5 | 线性历史生效 | 直接 push 一个 merge commit 到 `master` 被拒 |
| 6 | `test` 同 2–4 | 同上 |
| 7 | `dev` 可直推但不可强推/删除 | 普通 push 成功，`--force` 与 `--delete` 被拒 |
| 8 | 管理员例外有留痕 | 任何 bypass 操作都在 `docs/records/` 有一条记录 |

---

## 6. 与治理规范的衔接

- 规范 §5.3 的分支模型不变（`dev` 工作 → `master` 生产 → `test` 验收），只是**合并通道**从「本地 ff + push」变为「PR + Rebase and merge」；
- 若暂时不想改变现有工作流，可**只对 `master`/`test` 开「禁强推 + 禁删除」，暂不开 Require PR**（阶段 1.5）——先挡住最危险的两种操作，等团队成型再收紧；
- 任何保护规则变更都要在 `docs/reference/ai-knowledge/07-change-memory.md` 追加一条（类型=治理）。
