# 05 · cold-chain（冷链客户项目）

> 采集：2026-10-31。与 apex **无代码依赖**，独立交付线。本文只做入口级提炼，业务口径以该仓与其外迁文档为准。
> **细节已下沉**：模块规模、红线 R1–R11 全文提炼、已知代码偏差、文档地图见 `cold-chain/MEMORY-CARD.md` 与 `cold-chain/.workbuddy/knowledge/`（2026-11-01 建立）。

## 1. 项目定位

「中冷在线」——**第四方冷链数智公共服务平台**。核心模型：万仓联盟、销在存前、仓单货权、调拨即交易、两段式/两级清分结算。

| 项 | 值 |
| --- | --- |
| 路径 | `/Users/work/jsd-work-code/cold-chain` |
| 形态 | 一期 Spring Boot **多模块单体（17 模块）**；二期规划微服务拆分 |
| 活跃度 | 10-20 起 **226 次提交**（含合并），是三个仓里迭代最快的 |
| 工作树 | 4 项未跟踪（文档迁出后的残留目录 + `.lithe/`） |

## 2. 模块结构（`cold-chain-service/`）

`common`、`framework`、`system`、`bootstrap`、`admin`、`b2b-mall`、`warehouse`、`supplier`、`receipt`、`transfer`、`logistics`、`member`、`rating`、`park-settle`、`stat`、`file`，另有 `db`（SQL）。

> ⚠️ **`transfer` / `logistics` 仍为零 Java 文件的空壳模块**：调拨逻辑实际散在 `warehouse`（`WarehouseTransferServiceImpl`、`TransferOrderServiceImpl`）、`supplier`（`SupplierTransferServiceImpl`）、`receipt`（`ReceiptServiceImpl`、`ServiceFeeDeciderImpl`）。按模块名找代码会扑空。

## 3. 关键业务口径（v4，2026-10-29 定稿）

| 口径 | 内容 |
| --- | --- |
| 服务费归属 | **跟货权走，不跟单据走**；重复建模会导致重复收费 |
| 调拨收费 | 由仓单 `service_fee_paid` 驱动（O-3 拍板）；"首次免费"（D-04）**已废止** |
| 仓单状态 | 6 态，新增 `OUTBOUND`（在途）；`TRANSFERRED` **只表示货权转让** |
| `has_moved` | 在**调拨出库**置位（设计文档写成货权转移时置位，是错的） |
| 两级锁 | **L1 仓单锁定**（平台侧，货权约束）vs **L2 质押冻结**（资方，金融质押）；资方通知 WMS 解 L2 后恢复为 L1，既不是出库也不是过户 |
| 二级清分 | 一级清分成功 → **单触发**变更货权；国企担保 = 钱到监管账户即认解押；`guarantee_flag` 分支**已取消** |
| 解押数量 | **无算法**。仓单 100t 质押、实物 60t、付款 60 万时全解还是按比例解，平台不自行推算 → 结论写进《通知解押》接口返回值，这条边界要守住 |
| 可售量 | 取 `available`，**不是** `available - locked`（后者是重复扣减） |
| 评级 | 硬实力 ×0.6 + 商业 ×0.4；<60 不评级；5 项一票否决；填报从 W 端移交 A 端（防自评造假） |

## 4. 当前进度（2026-10-31）

- **盘点功能 ST1–ST3 全量落地**：盘点单 + 异步快照 + 状态机 + 锁定拦截 + 差异审核 + 调整 + W 端页面 + H5 脚手架。
- 全国大屏**三级下钻**（全国/省/市 + 范围联动），修复 `all-cities.js` 语法损坏导致的省级下钻失效（34 省 475 市县恢复）。
- 商品质押锁 `NONE/L1/L2` 与整单购买全链路；供应商预约到货与格位库容锁定；A 端侧边栏菜单动态化（后端下发）。
- **文档体系迁出**：10-31 起主文档搬至独立 `cold-chain-docs` 仓，本仓转纯代码仓；`PROJECT-MEMORY.md` 退化为目录/指针页。
- **需求审计**（21 份「02-进行中」）：0 COMPLETE / 4 PARTIAL（骨架在、业务改造未做）/ 6 NOT_STARTED（TMS、logistics 零代码等）/ 11 属文档流水线已移至 01-已完成。

## 5. 风险

| 编号 | 事项 |
| --- | --- |
| C1 | 盘点功能单日大改，**状态机与并发锁**是回归重点，需专门验证锁定拦截与差异审核 |
| C2 | 设计文档与代码长期"两套表、两条时间线"（如两级清分：设计案 `bu_*` 5 表 vs 代码 4 表 + `ClearingSettleJob` 30s 轮询），以代码为准并显式标注 |
| C3 | 迁移脚本存在 `DROP TABLE IF EXISTS` 兜底（如 `20261027-04-settlement-l1-l2.sql`），重跑会清数据，**执行前必须备份** |
| C4 | 文档迁出后本仓残留未跟踪的 `docs/` 子目录（业务学习笔记、项目文档 02/09 子集），需决定归档或删除，避免双份真相 |
| C5 | 批发表 `warehouse_stock_batch` 缺 `enterprise_id` → 多货主同 SKU 批次串账（历史高风险项，未见修复记录） |

## 6. 入口

1. 先读 `PROJECT-MEMORY.md`（目录/指针页，说明分册与红线）。
2. 业务口径读外迁 `cold-chain-docs` 仓的 `项目文档/`；历史提炼见本仓 `docs/业务学习笔记/`（12 篇，v4 口径）。
3. 换机/排障读该仓 `docs/memory/`（`README.md` 常青入口 + `status.md` 当前状态 + `ops/runbook.md`）。
