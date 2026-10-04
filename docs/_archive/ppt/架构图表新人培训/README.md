# 架构图表新人培训 PPT 使用说明

> 生成日期: 2026-09-10 | 版本: v1.0

本目录包含基于 `ONBOARDING_ARCHITECTURE_GUIDE.md` 生成的完整培训PPT资料包。

---

## 📦 文件清单

```
ppt/架构图表新人培训/
├── README.md (本文件)
├── PPT大纲.md (18页完整大纲)
├── 培训课件-Markdown版.md (Marp格式PPT源文件)
├── 架构图表新人培训.html (Waterfall格式PPT,开发中)
└── images/ (架构图表资源文件夹)
```

---

## 🚀 快速开始

### 方案1: 使用 Marp (推荐) ⭐

**Marp** 是一个基于Markdown的演示文稿生成工具,支持导出PDF和PPTX格式。

#### 安装 Marp CLI

```bash
# 使用 npm 安装
npm install -g @marp-team/marp-cli

# 或使用 Homebrew (macOS)
brew install marp-cli
```

#### 生成 PDF

```bash
cd /Users/work/apex-scaffold/docs/_archive/ppt/架构图表新人培训

# 导出为PDF
marp --pdf 培训课件-Markdown版.md

# 导出为PPTX
marp --pptx 培训课件-Markdown版.md

# 导出为HTML
marp --html 培训课件-Markdown版.md
```

#### VS Code 实时预览

1. 安装 **Marp for VS Code** 扩展
2. 打开 `培训课件-Markdown版.md`
3. 点击右上角预览按钮(或按 `Cmd+K V`)
4. 实时编辑,即时预览

---

### 方案2: 使用 reveal.js

**reveal.js** 是一个功能强大的HTML演示框架,支持丰富的交互效果。

#### 在线转换

访问 [slides.com](https://slides.com/) 或 [reveal-md](https://github.com/webpro/reveal-md)

#### 本地运行

```bash
# 安装 reveal-md
npm install -g reveal-md

# 启动演示服务器
cd /Users/work/apex-scaffold/docs/_archive/ppt/架构图表新人培训
reveal-md 培训课件-Markdown版.md

# 浏览器访问 http://localhost:1948
```

---

### 方案3: 使用 Google Slides / PowerPoint

#### 导入步骤

1. 使用 Marp 导出为 PPTX:
   ```bash
   marp --pptx 培训课件-Markdown版.md
   ```

2. 在 PowerPoint 或 Google Slides 中打开 PPTX 文件

3. 根据需要调整样式和布局

---

## 📐 PPT 结构说明

### 内容结构 (共26页)

| 页码 | 部分 | 内容 |
|------|------|------|
| 1 | 封面 | 课程标题与基本信息 |
| 2-3 | 开场 | 培训目标 + 学习路径总览 |
| 4-8 | 第1阶段 | 架构基础(DDD四层、技术栈、部署) |
| 9-14 | 第2阶段 | 核心业务流程(JWT、RBAC、工作流、文件) |
| 15-20 | 第3阶段 | 基础设施(数据库、缓存、限流、安全) |
| 21-24 | 实践任务 | 环境搭建、创建接口、查看监控 |
| 25 | 考核 | 培训考核清单 |
| 26 | 结束 | 学习资源与获取帮助 |

### 视觉风格

- **主题色**: 紫色渐变 (#667eea → #764ba2)
- **字体**: PingFang SC / Microsoft YaHei / Inter
- **尺寸**: 16:9 (1600×900px)
- **图表**: 嵌入SVG架构图(来自 `docs/diagrams/`)

---

## 🎯 使用场景

### 场景1: 新人入职培训

**讲师**: 架构团队成员
**学员**: 新加入的开发人员
**时长**: 2小时集中培训
**形式**: 线下讲解 + 实践操作

**流程**:
1. 讲师播放PPT,逐页讲解(60分钟)
2. 学员完成3个实践任务(30分钟)
3. 讲师答疑 + 考核(30分钟)

---

### 场景2: 自学模式

**学员**: 自学的开发人员
**时长**: 灵活安排,建议1-2天
**形式**: 阅读PPT + 对照架构图 + 动手实践

**流程**:
1. 阅读PPT,理解架构概念(1小时)
2. 打开 `docs/diagrams/` 查看详细架构图(1小时)
3. 完成实践任务(2小时)
4. 阅读配套文档深化理解(按需)

---

### 场景3: 团队技术分享

**分享人**: 资深开发人员
**受众**: 整个开发团队
**时长**: 45-60分钟
**形式**: 技术分享会

**流程**:
1. 挑选关键幻灯片(如DDD架构、技术栈、核心流程)
2. 深入讲解特定技术点
3. 互动讨论与Q&A

---

## 🛠️ 自定义修改

### 修改内容

直接编辑 `培训课件-Markdown版.md` 文件:

```markdown
<!-- 修改标题 -->
# Apex Scaffold
## 架构图表新人培训

<!-- 修改配色 -->
backgroundImage: linear-gradient(135deg, #667eea 0%, #764ba2 100%)

<!-- 添加幻灯片 -->
---
<!-- _class: content-slide -->
## 新增章节标题
内容...
```

### 更新架构图

如果架构图有更新,替换 `docs/diagrams/` 中的SVG文件即可,Markdown会自动引用最新版本。

---

## 📊 PPT大纲详解

`PPT大纲.md` 包含18页幻灯片的完整设计方案,包括:

- **幻灯片职责**: 每页的核心目标
- **内容/数据模式**: 具体展示什么信息
- **布局方案**: 如何排版
- **视觉/证据**: 使用什么图表
- **交互设计**: 如何与学员互动

**用途**:
1. 了解PPT完整结构
2. 作为制作更精美PPT的蓝图
3. 指导后续扩展和优化

---

## 🎨 进阶美化建议

### 使用专业工具

1. **Canva**: 在线设计工具,提供丰富模板
   - 导入Marp导出的PDF
   - 应用专业模板美化
   - 导出高质量PDF

2. **Keynote** (macOS): 苹果官方演示软件
   - 导入PPTX文件
   - 使用内置主题和动画
   - 导出视频格式

3. **PowerPoint**: 微软官方演示软件
   - 导入PPTX文件
   - 应用企业模板
   - 添加动画效果

### 添加动画

在 Marp 中添加简单动画:

```markdown
<!-- 渐入效果 -->
<style scoped>
section { animation: fadeIn 1s; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>
```

---

## 📤 导出与分享

### 导出格式对比

| 格式 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| **PDF** | 跨平台兼容,文件小 | 无动画,不可编辑 | 归档、打印、分享 |
| **PPTX** | 可编辑,支持动画 | 文件大,兼容性问题 | 二次编辑、演示 |
| **HTML** | 交互性强,可在线访问 | 需要浏览器 | 在线培训、自学 |
| **MP4** | 可录制讲解,易传播 | 文件大,不可交互 | 视频教程 |

### 分享方式

1. **内部Wiki**: 上传PDF到企业知识库
2. **企业微信**: 发送PPTX到开发群
3. **Git仓库**: 提交Markdown源文件到项目
4. **在线平台**: 上传到 SlideShare / Speaker Deck

---

## 🔄 维护更新

### 更新频率

- **季度更新**: 技术栈版本号变更时更新
- **半年更新**: 架构调整时全面修订
- **即时更新**: 发现错误立即修正

### 更新流程

1. 修改 `培训课件-Markdown版.md` 源文件
2. 同步更新 `PPT大纲.md` (如结构有变化)
3. 重新导出 PDF/PPTX
4. 更新本 README 的修订记录

### 修订记录

| 日期 | 版本 | 更新内容 | 更新人 |
|------|------|----------|--------|
| 2026-09-10 | v1.0 | 初始版本,基于ONBOARDING_ARCHITECTURE_GUIDE.md生成 | 架构团队 |

---

## ❓ 常见问题

### Q1: 为什么选择Markdown格式?

**A**: Markdown格式的优势:
- ✅ 纯文本,易于版本控制(Git)
- ✅ 支持多种导出格式(PDF/PPTX/HTML)
- ✅ 可以直接嵌入代码和图表
- ✅ 团队协作修改方便

### Q2: 如何添加备注?

**A**: 在Marp中添加演讲者备注:

```markdown
---
## 幻灯片标题

内容...

<!--
这是演讲者备注,不会显示在幻灯片上,
只会显示在演讲者视图中。
-->
```

### Q3: 如何调整图表大小?

**A**: 使用Markdown图片语法:

```markdown
![height:500px](../../../diagrams/01_logical_architecture.svg)
![width:800px](../../../diagrams/02_physical_architecture.svg)
```

### Q4: 能否导出视频格式?

**A**: 可以通过以下方式:

1. **Keynote**: 导出为MOV/M4V
2. **PowerPoint**: 导出为MP4
3. **屏幕录制**: QuickTime/OBS录制演示过程

---

## 📞 获取帮助

- **技术支持**: 企业微信 Apex 开发群
- **问题反馈**: 提交Issue到项目仓库
- **改进建议**: @架构团队

---

**文档维护者**: 架构团队
**版本**: v1.0
**生成日期**: 2026-09-10
**基于文档**: ONBOARDING_ARCHITECTURE_GUIDE.md

---

© 2026 Apex Team · 帮助新人快速融入团队,理解项目架构
