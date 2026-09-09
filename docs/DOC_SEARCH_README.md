# 📚 文档搜索工具使用指南 (完整版)

## ⚠️ 重要说明

由于浏览器的安全策略限制,**直接双击打开 `doc-search.html` 文件可能无法正常访问文档链接**。

✅ **推荐方式**: 通过 HTTP 服务器访问

---

## 🚀 快速启动 (推荐)

### 方法 1: 使用启动脚本 (最简单)

```bash
cd /Users/work/apex-scaffold
./start-doc-search.sh
```

脚本会自动:
- ✅ 检查 Python 环境
- ✅ 检测可用端口
- ✅ 启动 HTTP 服务器
- ✅ 显示访问地址

**访问地址**:
- 文档搜索工具: http://localhost:8080/docs/doc-search.html
- 链接测试页面: http://localhost:8080/docs/link-test.html

按 `Ctrl+C` 停止服务器。

---

## 🔧 其他启动方式

### 方法 2: 手动启动 Python 服务器

```bash
# 1. 进入项目目录
cd /Users/work/apex-scaffold

# 2. 启动 HTTP 服务器
python3 -m http.server 8080

# 3. 在浏览器中打开
# http://localhost:8080/docs/doc-search.html
```

### 方法 3: 使用项目服务

```bash
# 启动 Spring Boot 项目
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces

# 访问 http://localhost:8080/docs/doc-search.html
```

### 方法 4: VS Code Live Server

如果使用 VS Code 开发:

1. 安装 **Live Server** 扩展
2. 右键点击 `docs/doc-search.html`
3. 选择 **Open with Live Server**

---

## 📖 功能使用说明

### 1. 搜索文档

在搜索框中输入关键词:

```
示例关键词:
- OAuth2
- 前端组件
- Redis 缓存
- 单元测试
- 监控配置
```

结果会实时显示,支持:
- ✅ 标题匹配
- ✅ 描述匹配
- ✅ 标签匹配
- ✅ 路径匹配

### 2. 分类过滤

点击分类按钮快速筛选:

| 分类 | 包含内容 |
|------|---------|
| **全部** | 所有 80+ 个文档 |
| **集成** | OAuth2/PKCE 认证集成 (15个) |
| **前端** | Vue3、组件、状态管理 (7个) |
| **后端** | DDD、缓存、消息队列 (15个) |
| **运维** | Docker、K8s、监控 (11个) |
| **架构** | 系统架构、服务治理 (4个) |
| **安全** | JWT、RBAC、加密 (4个) |
| **测试** | 单元测试、性能测试 (5个) |
| **商业化** | 执行计划、重构方案 (3个) |
| **规范** | 开发规范、API 设计 (5个) |
| **报告** | 项目总结、实施记录 (3个) |

### 3. 打开文档

点击搜索结果中的文档标题:
- ✅ 在新标签页打开
- ✅ 显示 Markdown 源文件
- ✅ 可以下载或复制内容

### 4. 快捷键

按键盘 `/` 键快速聚焦到搜索框

---

## 🧪 测试链接功能

### 使用测试页面

访问测试页面验证链接是否正常:

```
http://localhost:8080/docs/link-test.html
```

测试页面包含:
- 6 个测试链接
- 详细的测试说明
- 问题诊断指导

### 手动测试步骤

1. **启动服务器**
   ```bash
   ./start-doc-search.sh
   ```

2. **打开搜索工具**
   ```
   http://localhost:8080/docs/doc-search.html
   ```

3. **测试搜索**
   - 搜索 "OAuth2"
   - 点击 "OAuth2 集成指南"
   - 验证文档能否打开

4. **测试多个分类**
   - 点击"后端"分类
   - 点击"安全"分类
   - 点击"测试"分类
   - 验证各分类文档都能正常打开

---

## ❓ 常见问题

### Q1: 为什么直接打开 HTML 文件不行?

**A**: 由于浏览器的安全策略:

1. **CORS 限制**: `file://` 协议不能访问其他本地文件
2. **同源策略**: 浏览器阻止本地文件之间的交互
3. **安全沙箱**: 现代浏览器对本地文件有严格的安全限制

**解决方案**: 使用 HTTP 服务器,通过 `http://` 协议访问

### Q2: Python 服务器端口被占用怎么办?

**A**: 使用其他端口:

```bash
# 使用 8081 端口
python3 -m http.server 8081

# 访问 http://localhost:8081/docs/doc-search.html
```

或者使用启动脚本,它会自动检测可用端口。

### Q3: 点击文档链接后显示乱码?

**A**: 这是正常的,Markdown 文件的源代码。

**解决方案**:

1. **安装 Markdown 浏览器扩展** (推荐):
   - Chrome: [Markdown Viewer](https://chrome.google.com/webstore/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)
   - Firefox: [Markdown Viewer Webext](https://addons.mozilla.org/zh-CN/firefox/addon/markdown-viewer-webext/)

2. **使用代码编辑器**:
   - 在 VS Code 中打开 Markdown 文件
   - 按 `Cmd+Shift+V` (Mac) 或 `Ctrl+Shift+V` (Windows) 预览

3. **使用在线 Markdown 编辑器**:
   - 复制文档内容
   - 粘贴到 [StackEdit](https://stackedit.io/) 或 [Dillinger](https://dillinger.io/)

### Q4: 某些文档找不到?

**A**: 检查文档是否存在:

```bash
# 检查文档路径
ls -la /Users/work/apex-scaffold/docs/integration/oauth2/integration-guide.md
```

如果文档不存在,可能是路径配置错误,请查看完整的文档索引:
- [ARCHITECTURE_INDEX.md](ARCHITECTURE_INDEX.md)
- [INTEGRATION_SUMMARY.md](INTEGRATION_SUMMARY.md)

### Q5: 如何添加新文档到搜索工具?

**A**: 编辑 `docs/doc-search.html`,在 `documents` 数组中添加:

```javascript
{
    title: "新文档标题",
    path: "./category/new-document.md",
    description: "文档描述",
    category: "backend",  // 分类
    tags: ["标签1", "标签2"],
    icon: "🔧"  // emoji 图标
}
```

---

## 📊 性能说明

### 搜索性能

- ✅ **实时搜索**: 输入即搜索,毫秒级响应
- ✅ **客户端计算**: 所有搜索在浏览器中完成
- ✅ **无服务器依赖**: 不需要后端 API

### 数据规模

| 指标 | 数量 |
|------|------|
| 文档分类 | 11 个 |
| 文档总数 | 80+ 个 |
| 标签关键词 | 200+ 个 |
| HTML 文件大小 | ~150 KB |
| 加载时间 | < 1 秒 |

---

## 🔗 相关文档

| 文档 | 说明 |
|------|------|
| [DOC_SEARCH_GUIDE.md](DOC_SEARCH_GUIDE.md) | 详细使用指南 |
| [DOC_SEARCH_FIX.md](DOC_SEARCH_FIX.md) | 链接修复说明 |
| [DOC_SEARCH_CHANGELOG.md](DOC_SEARCH_CHANGELOG.md) | 更新日志 |
| [link-test.html](link-test.html) | 链接测试页面 |

---

## 📞 技术支持

### 反馈渠道

- **GitHub Issues**: 提交 Bug 或功能建议
- **团队邮箱**: architecture-team@apex.com
- **文档维护**: AI Agent + 架构团队

### 提交问题时请包含

1. 操作系统和浏览器版本
2. 启动方式 (直接打开 / HTTP 服务器)
3. 具体报错信息或截图
4. 复现步骤

---

## 📋 检查清单

开始使用前,请确认:

- [ ] 已安装 Python 3
- [ ] 项目文件完整
- [ ] 使用 HTTP 服务器访问 (不是直接双击 HTML)
- [ ] 浏览器版本较新 (Chrome 90+, Firefox 88+, Safari 14+)
- [ ] 防火墙允许访问 localhost:8080

---

**最后更新**: 2026-09-09  
**文档版本**: v2.0.1  
**维护团队**: AI Agent + 架构团队  
**状态**: ✅ 正常使用
