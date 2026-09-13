# PNG 图片显示测试

## 测试说明
本文件用于测试 PNG 图片在当前编辑器中的显示效果。

---

## 方法 1: 相对路径 + Markdown 语法

### 缩略图（1000px）
![01_logical_architecture_thumb](./docs/diagrams/thumb/01_logical_architecture_thumb.png)

### 高清图（2000px）
![01_logical_architecture](./docs/diagrams/01_logical_architecture.png)

---

## 方法 2: HTML img 标签（绝对路径）

### 缩略图
<img src="file:///Users/work/apex-scaffold/docs/diagrams/thumb/01_logical_architecture_thumb.png" alt="缩略图" width="600">

### 高清图
<img src="file:///Users/work/apex-scaffold/docs/diagrams/01_logical_architecture.png" alt="高清图" width="600">

---

## 方法 3: Markdown 图片 + 相对路径（无 ./）

### 缩略图
![thumb](docs/diagrams/thumb/01_logical_architecture_thumb.png)

### 高清图
![full](docs/diagrams/01_logical_architecture.png)

---

## 方法 4: 图片链接列表

1. [缩略图 PNG](./docs/diagrams/thumb/01_logical_architecture_thumb.png)
2. [高清图 PNG](./docs/diagrams/01_logical_architecture.png)
3. [SVG 矢量图](../../../diagrams/01_logical_architecture.svg)

---

## 诊断信息

- **项目路径**: `/Users/work/apex-scaffold`
- **图片目录**: `docs/diagrams/`
- **缩略图目录**: `docs/diagrams/thumb/`

---

## 测试结果

请在您的编辑器中查看上面哪种方法的图片能正常显示：

- [ ] 方法 1（相对路径 `./`）
- [ ] 方法 2（HTML + file:// 绝对路径）
- [ ] 方法 3（相对路径无 `./`）
- [ ] 方法 4（仅链接）

**如果所有方法都不显示**，可能是：
1. 编辑器禁用了本地图片预览
2. 需要在编辑器设置中启用图片渲染
3. 图片文件权限问题

请告诉我哪种方法有效，我将据此修复所有文档！
