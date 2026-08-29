/*
 * Apex Scaffold 管理控制台（系统管理版）
 *
 * 原生 ES Module 实现的轻量管理后台，覆盖：用户、菜单、字典、参数、
 * 定时任务与登录日志的系统管理闭环。所有请求经统一 api() 封装，
 * 携带 Bearer Token，自动处理 401 会话失效与 Token 续期。
 */

// ==================== 常量与状态 ====================

/** 会话 Token 的 sessionStorage 键 */
const SESSION_TOKEN_KEY = "apex.admin.token";
/** 会话角色缓存键 */
const SESSION_ROLES_KEY = "apex.admin.roles";
/** 会话权限缓存键 */
const SESSION_PERMS_KEY = "apex.admin.permissions";

/** 全局状态 */
const state = {
  token: sessionStorage.getItem(SESSION_TOKEN_KEY) || "",
  identity: null,
  roles: JSON.parse(sessionStorage.getItem(SESSION_ROLES_KEY) || "[]"),
  permissions: JSON.parse(sessionStorage.getItem(SESSION_PERMS_KEY) || "[]"),
  captchaId: "",
  deviceId: "admin-h5",
  // 各视图内部状态
  users: { page: 1, size: 20, total: 0 },
  logs: { page: 1, size: 20, total: 0 },
  auditLogs: { page: 1, size: 20, total: 0 },
  apiCallLogs: { page: 1, size: 20, total: 0 },
  selectedDictType: null,
  selectedMenu: null,
  selectedJob: null,
  selectedConfig: null,
};

/** 业务异常类型 */
class ApiError extends Error {
  constructor(message, status, traceId) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.traceId = traceId;
  }
}

// ==================== 工具函数 ====================

/** 按选择器取元素 */
function $(selector) {
  return document.querySelector(selector);
}

/** HTML 转义，防 XSS */
function esc(value) {
  if (value === null || value === undefined) return "";
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** 格式化时间 */
function fmtTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 19);
}

/** 状态徽标 HTML */
function badge(status, successText, failText) {
  const ok = status === 1 || status === 0 && successText === "正常";
  if (status === 0) return `<span class="badge badge-success">${esc(successText)}</span>`;
  if (status === 1) return `<span class="badge badge-danger">${esc(failText)}</span>`;
  return `<span class="badge">${esc(ok ? successText : failText)}</span>`;
}

/** 简单确认 */
function confirmAction(message) {
  return window.confirm(message);
}

// ==================== 会话与权限 ====================

function setSessionToken(token) {
  state.token = token || "";
  if (state.token) sessionStorage.setItem(SESSION_TOKEN_KEY, state.token);
  else sessionStorage.removeItem(SESSION_TOKEN_KEY);
}

function setIdentity(identity) {
  state.identity = identity;
  if (identity?.roles) {
    state.roles = identity.roles;
    sessionStorage.setItem(SESSION_ROLES_KEY, JSON.stringify(state.roles));
  }
  if (identity?.permissions) {
    state.permissions = identity.permissions;
    sessionStorage.setItem(SESSION_PERMS_KEY, JSON.stringify(state.permissions));
  }
}

function clearSession() {
  setSessionToken("");
  state.identity = null;
  state.roles = [];
  state.permissions = [];
  sessionStorage.removeItem(SESSION_ROLES_KEY);
  sessionStorage.removeItem(SESSION_PERMS_KEY);
}

function hasRole(...codes) {
  return state.roles.some((role) => codes.includes(role));
}

// ==================== API 封装 ====================

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set("Accept", "application/json");
  const multipart = options.body instanceof FormData;
  if (options.body !== undefined && !multipart) headers.set("Content-Type", "application/json");
  if (options.auth !== false && state.token) headers.set("Authorization", `Bearer ${state.token}`);

  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : multipart ? options.body : JSON.stringify(options.body),
    credentials: "omit",
    cache: "no-store",
  });

  const renewedToken = response.headers.get("X-New-Access-Token");
  if (renewedToken) setSessionToken(renewedToken);

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    if (!response.ok) throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status, null);
  }

  if (response.status === 401 && options.auth !== false) {
    clearSession();
    showLogin("登录状态已失效，请重新验证身份。");
    throw new ApiError(payload?.msg || "登录状态已失效", 401, payload?.traceId);
  }
  if (!response.ok || (payload && payload.code !== undefined && payload.code !== 0)) {
    const suffix = payload?.traceId ? ` · Trace ${payload.traceId}` : "";
    throw new ApiError(`${payload?.msg || "请求未完成"}${suffix}`, response.status, payload?.traceId);
  }
  return payload?.data;
}

// ==================== 通用弹窗 ====================

/**
 * 打开模态弹窗。
 *
 * @param {string} title 弹窗标题
 * @param {string} bodyHtml 表单 HTML
 * @param {(form: HTMLFormElement) => Promise<void>} onSubmit 提交回调
 */
function openModal(title, bodyHtml, onSubmit) {
  $("#modal-title").textContent = title;
  $("#modal-body").innerHTML = bodyHtml;
  $("#modal-backdrop").hidden = false;
  const form = $("#modal-body").querySelector("form");
  const confirmButton = $("#modal-confirm");

  confirmButton.onclick = async () => {
    if (form && !form.reportValidity()) return;
    confirmButton.disabled = true;
    try {
      await onSubmit(form);
      closeModal();
      showToast("操作成功");
    } catch (error) {
      showToast(error.message || "操作失败", true);
    } finally {
      confirmButton.disabled = false;
    }
  };
  if (form) form.addEventListener("submit", (event) => {
    event.preventDefault();
    confirmButton.click();
  });
  $("#modal-close").onclick = closeModal;
  $("#modal-cancel").onclick = closeModal;
}

function closeModal() {
  $("#modal-backdrop").hidden = true;
  $("#modal-body").innerHTML = "";
}

// ==================== 提示 ====================

let toastTimer = null;

function showToast(message, isError = false) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.hidden = false;
  toast.classList.toggle("toast-error", isError);
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toast.hidden = true;
  }, 3000);
}

// ==================== 登录 ====================

async function loadCaptcha() {
  try {
    const data = await api("/api/v1/auth/captcha", { auth: false });
    state.captchaId = data.captchaId;
    $("#captcha-image").src = `data:image/png;base64,${data.image}`;
  } catch {
    $("#captcha-image").removeAttribute("src");
  }
}

async function sendSmsCode() {
  const phone = $("#login-phone").value.trim();
  const errorBox = $("#login-error");
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    errorBox.textContent = "请先输入有效的 11 位手机号。";
    errorBox.hidden = false;
    return;
  }
  const button = $("#send-code");
  button.disabled = true;
  errorBox.hidden = true;
  try {
    await api("/api/v1/auth/cl/sms/code", { method: "POST", auth: false, body: { phone } });
    showToast("验证码已发送，请留意短信。");
    let seconds = 60;
    button.textContent = `${seconds}s 后重试`;
    const timer = window.setInterval(() => {
      seconds -= 1;
      button.textContent = seconds > 0 ? `${seconds}s 后重试` : "获取验证码";
      if (seconds <= 0) {
        window.clearInterval(timer);
        button.disabled = false;
      }
    }, 1000);
  } catch (error) {
    button.disabled = false;
    errorBox.textContent = error.message || "验证码发送失败。";
    errorBox.hidden = false;
  }
}

async function login() {
  const phone = $("#login-phone").value.trim();
  const smsCode = $("#login-code").value.trim();
  const captchaCode = $("#login-captcha").value.trim();
  const errorBox = $("#login-error");
  if (!/^1[3-9]\d{9}$/.test(phone) || !/^\d{6}$/.test(smsCode)) {
    errorBox.textContent = "请输入有效的手机号和 6 位短信验证码。";
    errorBox.hidden = false;
    return;
  }
  const submit = $("#login-submit");
  submit.disabled = true;
  errorBox.hidden = true;
  try {
    const loginData = await api("/api/v1/auth/login", {
      method: "POST",
      auth: false,
      body: {
        loginType: "CL_SMS",
        phone,
        smsCode,
        captchaId: state.captchaId,
        captchaCode,
        deviceId: state.deviceId,
        platform: "h5",
      },
    });
    if (!loginData?.accessToken) {
      throw new ApiError("该账号尚未完成注册或后台身份验证。", 403, null);
    }
    setSessionToken(loginData.accessToken);
    await loadIdentity();
    showApp();
    showToast("身份验证成功，欢迎进入系统管理工作台。");
  } catch (error) {
    clearSession();
    errorBox.textContent = error.message || "登录失败，请稍后重试。";
    errorBox.hidden = false;
    loadCaptcha();
  } finally {
    submit.disabled = false;
  }
}

async function loadIdentity() {
  const [roles, permissions] = await Promise.all([
    api("/api/v1/auth/roles/my").catch(() => []),
    api("/api/v1/auth/permissions/my").catch(() => []),
  ]);
  setIdentity({ roles, permissions });
}

async function logout() {
  try {
    if (state.token) await api("/api/v1/auth/logout", { method: "POST" });
  } catch {
    // 服务不可用时仍清理本地会话
  } finally {
    clearSession();
    showLogin();
  }
}

// ==================== 视图切换 ====================

function showLogin(message = "") {
  $("#app-shell").hidden = true;
  $("#login-view").hidden = false;
  const error = $("#login-error");
  error.textContent = message;
  error.hidden = !message;
  $("#login-phone").focus();
  loadCaptcha();
}

function showApp() {
  $("#login-view").hidden = true;
  $("#app-shell").hidden = false;
  switchView("dashboard");
}

function switchView(name) {
  document.querySelectorAll(".view").forEach((section) => {
    section.hidden = section.id !== `view-${name}`;
  });
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === name);
  });
  const renderers = {
    dashboard: renderDashboard,
    users: renderUsers,
    menus: renderMenus,
    dicts: renderDicts,
    configs: renderConfigs,
    jobs: renderJobs,
    "login-logs": renderLoginLogs,
    "audit-logs": renderAuditLogs,
    "api-call-logs": renderApiCallLogs,
  };
  const render = renderers[name];
  if (render) render().catch((error) => showToast(error.message || "加载失败", true));
}

// ==================== 仪表盘 ====================

async function renderDashboard() {
  const rolesText = state.roles.length ? state.roles.join("、") : "未分配后台角色";
  const permissionsCount = state.permissions.length;
  $("#dashboard-cards").innerHTML = `
    <div class="card">
      <h3>登录身份</h3>
      <p class="card-value">${esc(rolesText)}</p>
      <p class="card-hint">当前会话已通过短信验证码 + 图形验证码双重校验</p>
    </div>
    <div class="card">
      <h3>权限点</h3>
      <p class="card-value">${permissionsCount}</p>
      <p class="card-hint">已授予 ${permissionsCount} 个接口级权限标识</p>
    </div>
    <div class="card">
      <h3>系统管理能力</h3>
      <p class="card-value">7 类</p>
      <p class="card-hint">用户 / 菜单 / 字典 / 参数 / 定时任务 / 登录日志</p>
    </div>
  `;
}

// ==================== 用户管理 ====================

async function renderUsers() {
  const keyword = $("#user-keyword").value.trim();
  const status = $("#user-status").value;
  const page = state.users.page;
  const size = state.users.size;
  const data = await api(`/admin-api/v1/users?page=${page}&size=${size}`
      + `&keyword=${encodeURIComponent(keyword)}`
      + (status ? `&status=${status}` : ""));
  state.users.total = data.total || 0;

  const rows = (data.list || []).map((user) => `
    <tr>
      <td>${esc(user.id)}</td>
      <td>${esc(user.nickName ?? user.nickname ?? "-")}</td>
      <td>${esc(user.phone ?? "-")}</td>
      <td>${badge(user.status === undefined ? 1 : user.status, "正常", "禁用")}</td>
      <td class="actions">
        <button class="text-button" data-action="detail" data-id="${user.id}">详情</button>
        <button class="text-button" data-action="roles" data-id="${user.id}">角色</button>
        <button class="text-button" data-action="toggle" data-id="${user.id}" data-enabled="${user.status === 1 ? "false" : "true"}">
          ${user.status === 1 ? "禁用" : "启用"}
        </button>
      </td>
    </tr>`).join("");

  $("#user-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>昵称</th><th>手机号</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${rows || '<tr><td colspan="5" class="empty">暂无数据</td></tr>'}</tbody>
    </table>`;

  renderPagination($("#user-pagination"), state.users, () => {
    renderUsers();
  });

  $("#user-table").querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = () => handleUserAction(button.dataset.action, button.dataset.id, button.dataset.enabled);
  });
}

async function handleUserAction(action, id, enabled) {
  try {
    if (action === "detail") {
      const user = await api(`/admin-api/v1/users/${id}`);
      openModal("用户详情", `
        <dl class="detail-list">
          <dt>用户 ID</dt><dd>${esc(user.id)}</dd>
          <dt>昵称</dt><dd>${esc(user.nickName ?? user.nickname ?? "-")}</dd>
          <dt>手机号</dt><dd>${esc(user.phone ?? "-")}</dd>
          <dt>状态</dt><dd>${user.status === 1 ? "正常" : "禁用"}</dd>
        </dl>` , () => Promise.resolve());
      $("#modal-confirm").hidden = true;
      $("#modal-cancel").textContent = "关闭";
    } else if (action === "roles") {
      const [user, roles] = await Promise.all([
        api(`/admin-api/v1/users/${id}`),
        api("/admin-api/v1/staff-roles"),
      ]);
      const current = user.roleIds || user.roles || [];
      openModal(`配置角色 · 用户 ${user.id}`, `
        <form>
          ${(roles || []).map((role) => `
            <label class="check-row">
              <input type="checkbox" name="roleIds" value="${role.id}" ${current.includes(role.id) ? "checked" : ""}>
              ${esc(role.name)}（${esc(role.code)}）
            </label>`).join("") || '<p class="empty">无可用后台角色</p>'}
        </form>`, async (form) => {
        const roleIds = Array.from(form.querySelectorAll('input[name="roleIds"]:checked')).map((input) => Number(input.value));
        await api(`/admin-api/v1/users/${id}/roles`, { method: "PUT", body: { roleIds } });
        renderUsers();
      });
    } else if (action === "toggle") {
      const enabled = enabled === "true";
      if (!confirmAction(enabled ? "确认启用该用户？" : "确认禁用该用户？")) return;
      await api(`/admin-api/v1/users/${id}/status`, { method: "PUT", body: { enabled } });
      showToast("状态已更新");
      renderUsers();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

// ==================== 菜单管理 ====================

const MENU_TYPE_LABELS = { 0: "目录", 1: "菜单", 2: "按钮" };

async function renderMenus() {
  const menus = await api("/admin-api/v1/menus");
  const rows = [];
  const walk = (nodes, depth) => {
    nodes.forEach((menu) => {
      rows.push(`
        <tr>
          <td style="padding-left:${depth * 24 + 8}px">${depth > 0 ? "└ " : ""}${esc(menu.menuName)}</td>
          <td>${MENU_TYPE_LABELS[menu.menuType] ?? menu.menuType}</td>
          <td>${esc(menu.path ?? "-")}</td>
          <td>${esc(menu.perms ?? "-")}</td>
          <td>${badge(menu.status ?? 1, "正常", "停用")}</td>
          <td class="actions">
            <button class="text-button" data-action="create-child" data-id="${menu.id}">新增子项</button>
            <button class="text-button" data-action="edit" data-id="${menu.id}">编辑</button>
            <button class="text-button" data-action="toggle" data-id="${menu.id}" data-enabled="${menu.status === 1 ? "false" : "true"}">${menu.status === 1 ? "停用" : "启用"}</button>
            <button class="text-button" data-action="delete" data-id="${menu.id}">删除</button>
          </td>
        </tr>`);
      if (menu.children?.length) walk(menu.children, depth + 1);
    });
  };
  walk(menus || [], 0);

  $("#menu-table").innerHTML = `
    <table class="table">
      <thead><tr><th>菜单名称</th><th>类型</th><th>路由</th><th>权限标识</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${rows.join("") || '<tr><td colspan="6" class="empty">暂无菜单，点击右上角"新增菜单"创建</td></tr>'}</tbody>
    </table>`;

  $("#menu-table").querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = () => handleMenuAction(button.dataset.action, button.dataset.id, button.dataset.enabled);
  });
  $("#menu-create").onclick = () => openMenuForm(null);
}

async function handleMenuAction(action, id, enabled) {
  try {
    if (action === "edit") {
      const menu = await findMenu(id);
      openMenuForm(menu);
    } else if (action === "create-child") {
      const menu = await findMenu(id);
      openMenuForm({ parentId: menu.id });
    } else if (action === "toggle") {
      const targetEnabled = enabled === "true";
      await api(`/admin-api/v1/menus/${id}/status`, { method: "PUT", body: { enabled: targetEnabled } });
      showToast("状态已更新");
      renderMenus();
    } else if (action === "delete") {
      if (!confirmAction("确认删除该菜单？含子菜单的节点将被拒绝。")) return;
      await api(`/admin-api/v1/menus/${id}`, { method: "DELETE" });
      showToast("菜单已删除");
      renderMenus();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

async function findMenu(id) {
  const menus = await api("/admin-api/v1/menus");
  let found = null;
  const walk = (nodes) => {
    nodes.forEach((menu) => {
      if (String(menu.id) === String(id)) found = menu;
      if (menu.children?.length) walk(menu.children);
    });
  };
  walk(menus || []);
  return found;
}

function openMenuForm(menu) {
  const isEdit = Boolean(menu?.id);
  openModal(isEdit ? "编辑菜单" : "新增菜单", `
    <form class="form-grid">
      <input type="hidden" name="id" value="${menu?.id ?? ""}">
      <label>父菜单 ID
        <input class="input" name="parentId" type="number" min="0" value="${menu?.parentId ?? 0}" required>
      </label>
      <label>菜单名称
        <input class="input" name="menuName" value="${esc(menu?.menuName ?? "")}" maxlength="50" required>
      </label>
      <label>菜单类型
        <select class="input" name="menuType" required>
          <option value="0" ${menu?.menuType === 0 ? "selected" : ""}>目录</option>
          <option value="1" ${menu?.menuType === 1 || !menu ? "selected" : ""}>菜单</option>
          <option value="2" ${menu?.menuType === 2 ? "selected" : ""}>按钮</option>
        </select>
      </label>
      <label>路由地址
        <input class="input" name="path" value="${esc(menu?.path ?? "")}" maxlength="200">
      </label>
      <label>组件路径
        <input class="input" name="component" value="${esc(menu?.component ?? "")}" maxlength="255">
      </label>
      <label>权限标识
        <input class="input" name="perms" value="${esc(menu?.perms ?? "")}" maxlength="100" placeholder="如 system:user:add">
      </label>
      <label>图标
        <input class="input" name="icon" value="${esc(menu?.icon ?? "")}" maxlength="100">
      </label>
      <label>排序
        <input class="input" name="sort" type="number" min="0" value="${menu?.sort ?? 0}">
      </label>
      <label>状态
        <select class="input" name="status">
          <option value="1" ${menu?.status !== 0 ? "selected" : ""}>正常</option>
          <option value="0" ${menu?.status === 0 ? "selected" : ""}>停用</option>
        </select>
      </label>
    </form>`, async (form) => {
    const body = Object.fromEntries(new FormData(form).entries());
    body.parentId = Number(body.parentId || 0);
    body.menuType = Number(body.menuType);
    body.sort = Number(body.sort || 0);
    body.status = Number(body.status);
    if (isEdit) {
      await api(`/admin-api/v1/menus/${menu.id}`, { method: "PUT", body });
    } else {
      await api("/admin-api/v1/menus", { method: "POST", body });
    }
    renderMenus();
  });
}

// ==================== 字典管理 ====================

async function renderDicts() {
  const types = await api("/admin-api/v1/dict/types");
  $("#dict-type-list").innerHTML = (types || []).map((type) => `
    <div class="list-item ${state.selectedDictType === type.dictType ? "active" : ""}" data-type="${esc(type.dictType)}">
      <div class="list-item-main">
        <strong>${esc(type.dictName)}</strong>
        <span class="list-item-sub">${esc(type.dictType)} · ${badge(type.status ?? 1, "正常", "停用")}</span>
      </div>
      <div class="list-item-actions">
        <button class="text-button" data-action="type-edit" data-id="${type.id}">编辑</button>
        <button class="text-button" data-action="type-toggle" data-id="${type.id}" data-enabled="${type.status === 1 ? "false" : "true"}">${type.status === 1 ? "停用" : "启用"}</button>
        <button class="text-button" data-action="type-delete" data-id="${type.id}">删除</button>
      </div>
    </div>`).join("") || '<p class="empty">暂无字典类型</p>';

  $("#dict-type-list").querySelectorAll(".list-item").forEach((item) => {
    item.onclick = () => {
      state.selectedDictType = item.dataset.type;
      renderDicts();
    };
  });
  $("#dict-type-list").querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = (event) => {
      event.stopPropagation();
      handleDictTypeAction(button.dataset.action, button.dataset.id, button.dataset.enabled);
    };
  });

  if (state.selectedDictType) {
    $("#dict-data-title").textContent = `字典数据 · ${state.selectedDictType}`;
    $("#dict-data-create").disabled = false;
    const data = await api(`/admin-api/v1/dict/data?dictType=${encodeURIComponent(state.selectedDictType)}`);
    $("#dict-data-list").innerHTML = `
      <table class="table">
        <thead><tr><th>排序</th><th>标签</th><th>键值</th><th>默认</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>${(data || []).map((item) => `
          <tr>
            <td>${item.dictSort}</td>
            <td>${esc(item.dictLabel)}</td>
            <td>${esc(item.dictValue)}</td>
            <td>${item.isDefault === 1 ? "✔" : "-"}</td>
            <td>${badge(item.status ?? 1, "正常", "停用")}</td>
            <td class="actions">
              <button class="text-button" data-action="data-edit" data-id="${item.id}">编辑</button>
              <button class="text-button" data-action="data-default" data-id="${item.id}">设默认</button>
              <button class="text-button" data-action="data-delete" data-id="${item.id}">删除</button>
            </td>
          </tr>`).join("") || '<tr><td colspan="6" class="empty">暂无数据项</td></tr>'}
        </tbody>
      </table>`;
    $("#dict-data-list").querySelectorAll("[data-action]").forEach((button) => {
      button.onclick = () => handleDictDataAction(button.dataset.action, button.dataset.id);
    });
  } else {
    $("#dict-data-title").textContent = "字典数据（请选择类型）";
    $("#dict-data-create").disabled = true;
    $("#dict-data-list").innerHTML = '<p class="empty">从左侧选择字典类型查看数据项</p>';
  }
}

async function handleDictTypeAction(action, id, enabled) {
  try {
    if (action === "type-edit") {
      const types = await api("/admin-api/v1/dict/types");
      const type = types.find((item) => String(item.id) === String(id));
      if (!type) return;
      openModal("编辑字典类型", `
        <form class="form-grid">
          <label>字典名称
            <input class="input" name="dictName" value="${esc(type.dictName)}" maxlength="100" required>
          </label>
          <label>字典类型编码
            <input class="input" name="dictType" value="${esc(type.dictType)}" maxlength="100" required>
          </label>
          <label>备注
            <input class="input" name="remark" value="${esc(type.remark ?? "")}" maxlength="500">
          </label>
        </form>`, async (form) => {
        const body = Object.fromEntries(new FormData(form).entries());
        await api(`/admin-api/v1/dict/types/${id}`, { method: "PUT", body });
        renderDicts();
      });
    } else if (action === "type-toggle") {
      await api(`/admin-api/v1/dict/types/${id}/status`, { method: "PUT", body: { enabled: enabled === "true" } });
      showToast("状态已更新");
      renderDicts();
    } else if (action === "type-delete") {
      if (!confirmAction("确认删除该字典类型？含数据项的类型将被拒绝。")) return;
      await api(`/admin-api/v1/dict/types/${id}`, { method: "DELETE" });
      if (state.selectedDictType) state.selectedDictType = null;
      showToast("字典类型已删除");
      renderDicts();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

async function handleDictDataAction(action, id) {
  try {
    const dictType = state.selectedDictType;
    if (!dictType) return;
    if (action === "data-edit") {
      const items = await api(`/admin-api/v1/dict/data?dictType=${encodeURIComponent(dictType)}`);
      const item = items.find((entry) => String(entry.id) === String(id));
      if (!item) return;
      openModal("编辑字典数据", `
        <form class="form-grid">
          <label>字典标签
            <input class="input" name="dictLabel" value="${esc(item.dictLabel)}" maxlength="100" required>
          </label>
          <label>字典键值
            <input class="input" name="dictValue" value="${esc(item.dictValue)}" maxlength="100" required>
          </label>
          <label>排序
            <input class="input" name="dictSort" type="number" min="0" value="${item.dictSort ?? 0}">
          </label>
          <label>设为默认
            <select class="input" name="isDefault">
              <option value="0" ${item.isDefault !== 1 ? "selected" : ""}>否</option>
              <option value="1" ${item.isDefault === 1 ? "selected" : ""}>是</option>
            </select>
          </label>
        </form>`, async (form) => {
        const body = Object.fromEntries(new FormData(form).entries());
        body.dictSort = Number(body.dictSort || 0);
        body.isDefault = Number(body.isDefault);
        body.dictType = dictType;
        await api(`/admin-api/v1/dict/data/${id}`, { method: "PUT", body });
        renderDicts();
      });
    } else if (action === "data-default") {
      const items = await api(`/admin-api/v1/dict/data?dictType=${encodeURIComponent(dictType)}`);
      const item = items.find((entry) => String(entry.id) === String(id));
      if (!item) return;
      const body = {
        dictLabel: item.dictLabel,
        dictValue: item.dictValue,
        dictSort: item.dictSort,
        dictType,
        isDefault: 1,
      };
      await api(`/admin-api/v1/dict/data/${id}`, { method: "PUT", body });
      showToast("已设为默认项");
      renderDicts();
    } else if (action === "data-delete") {
      if (!confirmAction("确认删除该字典数据？")) return;
      await api(`/admin-api/v1/dict/data/${id}`, { method: "DELETE" });
      showToast("字典数据已删除");
      renderDicts();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

function initDictCreators() {
  $("#dict-type-create").onclick = () => {
    openModal("新增字典类型", `
      <form class="form-grid">
        <label>字典名称
          <input class="input" name="dictName" maxlength="100" required>
        </label>
        <label>字典类型编码
          <input class="input" name="dictType" maxlength="100" placeholder="如 sys_user_sex" required>
        </label>
        <label>备注
          <input class="input" name="remark" maxlength="500">
        </label>
      </form>`, async (form) => {
      const body = Object.fromEntries(new FormData(form).entries());
      await api("/admin-api/v1/dict/types", { method: "POST", body });
      renderDicts();
    });
  };
  $("#dict-data-create").onclick = () => {
    const dictType = state.selectedDictType;
    if (!dictType) return;
    openModal("新增字典数据", `
      <form class="form-grid">
        <label>字典标签
          <input class="input" name="dictLabel" maxlength="100" required>
        </label>
        <label>字典键值
          <input class="input" name="dictValue" maxlength="100" required>
        </label>
        <label>排序
          <input class="input" name="dictSort" type="number" min="0" value="0">
        </label>
        <label>设为默认
          <select class="input" name="isDefault">
            <option value="0">否</option>
            <option value="1">是</option>
          </select>
        </label>
      </form>`, async (form) => {
      const body = Object.fromEntries(new FormData(form).entries());
      body.dictSort = Number(body.dictSort || 0);
      body.isDefault = Number(body.isDefault);
      body.dictType = dictType;
      await api("/admin-api/v1/dict/data", { method: "POST", body });
      renderDicts();
    });
  };
}

// ==================== 参数配置 ====================

async function renderConfigs() {
  const configs = await api("/admin-api/v1/configs");
  $("#config-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>参数名称</th><th>参数键名</th><th>参数值</th><th>类型</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${(configs || []).map((config) => `
        <tr>
          <td>${config.id}</td>
          <td>${esc(config.configName)}</td>
          <td><code>${esc(config.configKey)}</code></td>
          <td>${esc(config.configValue)}</td>
          <td>${config.configType === 0 ? "内置" : "用户"}</td>
          <td>${badge(config.status ?? 1, "正常", "停用")}</td>
          <td class="actions">
            <button class="text-button" data-action="config-edit" data-id="${config.id}">编辑</button>
            <button class="text-button" data-action="config-toggle" data-id="${config.id}" data-enabled="${config.status === 1 ? "false" : "true"}">${config.status === 1 ? "停用" : "启用"}</button>
            <button class="text-button" data-action="config-delete" data-id="${config.id}" ${config.configType === 0 ? "disabled" : ""}>删除</button>
          </td>
        </tr>`).join("") || '<tr><td colspan="7" class="empty">暂无参数配置</td></tr>'}
      </tbody>
    </table>`;
  $("#config-table").querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = () => handleConfigAction(button.dataset.action, button.dataset.id, button.dataset.enabled);
  });
  $("#config-create").onclick = () => openConfigForm(null);
}

async function handleConfigAction(action, id, enabled) {
  try {
    const configs = await api("/admin-api/v1/configs");
    const config = configs.find((item) => String(item.id) === String(id));
    if (!config) return;
    if (action === "config-edit") {
      openConfigForm(config);
    } else if (action === "config-toggle") {
      await api(`/admin-api/v1/configs/${id}/status`, { method: "PUT", body: { enabled: enabled === "true" } });
      showToast("状态已更新");
      renderConfigs();
    } else if (action === "config-delete") {
      if (!confirmAction("确认删除该参数？内置参数禁止删除。")) return;
      await api(`/admin-api/v1/configs/${id}`, { method: "DELETE" });
      showToast("参数已删除");
      renderConfigs();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

function openConfigForm(config) {
  const isEdit = Boolean(config?.id);
  openModal(isEdit ? "编辑参数" : "新增参数", `
    <form class="form-grid">
      <label>参数名称
        <input class="input" name="configName" value="${esc(config?.configName ?? "")}" maxlength="100" required>
      </label>
      <label>参数键名
        <input class="input" name="configKey" value="${esc(config?.configKey ?? "")}" maxlength="100" placeholder="如 sys.site.name" ${isEdit ? "readonly" : "required"}>
      </label>
      <label>参数值
        <textarea class="input" name="configValue" maxlength="500" required>${esc(config?.configValue ?? "")}</textarea>
      </label>
      <label>备注
        <input class="input" name="remark" value="${esc(config?.remark ?? "")}" maxlength="500">
      </label>
    </form>`, async (form) => {
    const body = Object.fromEntries(new FormData(form).entries());
    if (isEdit) {
      await api(`/admin-api/v1/configs/${config.id}`, { method: "PUT", body });
    } else {
      await api("/admin-api/v1/configs", { method: "POST", body });
    }
    renderConfigs();
  });
}

// ==================== 定时任务 ====================

async function renderJobs() {
  const jobs = await api("/admin-api/v1/jobs");
  $("#job-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>任务名称</th><th>分组</th><th>调用目标</th><th>Cron</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>${(jobs || []).map((job) => `
        <tr>
          <td>${job.id}</td>
          <td>${esc(job.jobName)}</td>
          <td>${esc(job.jobGroup)}</td>
          <td><code>${esc(job.invokeTarget)}</code></td>
          <td><code>${esc(job.cronExpression)}</code></td>
          <td>${badge(job.status ?? 1, "正常", "停用")}</td>
          <td class="actions">
            <button class="text-button" data-action="job-edit" data-id="${job.id}">编辑</button>
            <button class="text-button" data-action="job-run" data-id="${job.id}">立即执行</button>
            <button class="text-button" data-action="job-logs" data-id="${job.id}">日志</button>
            <button class="text-button" data-action="job-toggle" data-id="${job.id}" data-enabled="${job.status === 1 ? "false" : "true"}">${job.status === 1 ? "停用" : "启用"}</button>
            <button class="text-button" data-action="job-delete" data-id="${job.id}">删除</button>
          </td>
        </tr>`).join("") || '<tr><td colspan="7" class="empty">暂无定时任务</td></tr>'}
      </tbody>
    </table>`;
  $("#job-table").querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = () => handleJobAction(button.dataset.action, button.dataset.id, button.dataset.enabled);
  });
  $("#job-create").onclick = () => openJobForm(null);
}

async function handleJobAction(action, id, enabled) {
  try {
    const jobs = await api("/admin-api/v1/jobs");
    const job = jobs.find((item) => String(item.id) === String(id));
    if (!job) return;
    if (action === "job-edit") {
      openJobForm(job);
    } else if (action === "job-run") {
      if (!confirmAction(`确认立即执行任务「${job.jobName}」？`)) return;
      await api(`/admin-api/v1/jobs/${id}/run`, { method: "POST" });
      showToast("任务已触发，执行结果见日志");
    } else if (action === "job-logs") {
      const logs = await api(`/admin-api/v1/jobs/${id}/logs?limit=20`);
      openModal(`执行日志 · ${job.jobName}`, `
        <table class="table">
          <thead><tr><th>状态</th><th>消息</th><th>耗时(ms)</th><th>开始时间</th></tr></thead>
          <tbody>${(logs || []).map((log) => `
            <tr>
              <td>${log.status === 0 ? "✔ 成功" : log.status === 1 ? "✘ 失败" : "运行中"}</td>
              <td>${esc(log.jobMessage ?? "-")}</td>
              <td>${log.duration ?? "-"}</td>
              <td>${fmtTime(log.startTime)}</td>
            </tr>`).join("") || '<tr><td colspan="4" class="empty">暂无执行日志</td></tr>'}
          </tbody>
        </table>`, () => Promise.resolve());
      $("#modal-confirm").hidden = true;
      $("#modal-cancel").textContent = "关闭";
    } else if (action === "job-toggle") {
      await api(`/admin-api/v1/jobs/${id}/status`, { method: "PUT", body: { enabled: enabled === "true" } });
      showToast("状态已更新");
      renderJobs();
    } else if (action === "job-delete") {
      if (!confirmAction("确认删除该定时任务？")) return;
      await api(`/admin-api/v1/jobs/${id}`, { method: "DELETE" });
      showToast("任务已删除");
      renderJobs();
    }
  } catch (error) {
    showToast(error.message || "操作失败", true);
  }
}

function openJobForm(job) {
  const isEdit = Boolean(job?.id);
  openModal(isEdit ? "编辑定时任务" : "新增定时任务", `
    <form class="form-grid">
      <label>任务名称
        <input class="input" name="jobName" value="${esc(job?.jobName ?? "")}" maxlength="64" required>
      </label>
      <label>任务分组
        <input class="input" name="jobGroup" value="${esc(job?.jobGroup ?? "DEFAULT")}" maxlength="64">
      </label>
      <label>调用目标
        <input class="input" name="invokeTarget" value="${esc(job?.invokeTarget ?? "")}" maxlength="500"
               placeholder="beanName.methodName() 或 beanName.methodName(&quot;arg&quot;, 1)" required>
      </label>
      <label>Cron 表达式
        <input class="input" name="cronExpression" value="${esc(job?.cronExpression ?? "")}" maxlength="255"
               placeholder="如 0 0 2 * * ?" required>
      </label>
      <label>允许并发
        <select class="input" name="concurrent">
          <option value="1" ${job?.concurrent !== 0 ? "selected" : ""}>允许</option>
          <option value="0" ${job?.concurrent === 0 ? "selected" : ""}>禁止</option>
        </select>
      </label>
      <label>备注
        <input class="input" name="remark" value="${esc(job?.remark ?? "")}" maxlength="500">
      </label>
    </form>`, async (form) => {
    const body = Object.fromEntries(new FormData(form).entries());
    body.concurrent = Number(body.concurrent);
    if (isEdit) {
      await api(`/admin-api/v1/jobs/${job.id}`, { method: "PUT", body });
    } else {
      await api("/admin-api/v1/jobs", { method: "POST", body });
    }
    renderJobs();
  });
}

// ==================== 登录日志 ====================

async function renderLoginLogs() {
  const keyword = $("#log-keyword").value.trim();
  const status = $("#log-status").value;
  const page = state.logs.page;
  const size = state.logs.size;
  const data = await api(`/admin-api/v1/login-logs?page=${page}&size=${size}`
      + `&username=${encodeURIComponent(keyword)}`
      + (status ? `&status=${status}` : ""));
  state.logs.total = data.total || 0;

  $("#log-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>账号</th><th>IP</th><th>状态</th><th>消息</th><th>登录时间</th></tr></thead>
      <tbody>${(data.list || []).map((log) => `
        <tr>
          <td>${log.id}</td>
          <td>${esc(log.username ?? "-")}</td>
          <td>${esc(log.ip ?? "-")}</td>
          <td>${log.status === 0 ? "✔ 成功" : "✘ 失败"}</td>
          <td>${esc(log.message ?? "-")}</td>
          <td>${fmtTime(log.loginTime)}</td>
        </tr>`).join("") || '<tr><td colspan="6" class="empty">暂无登录日志</td></tr>'}
      </tbody>
    </table>`;

  renderPagination($("#log-pagination"), state.logs, () => {
    renderLoginLogs();
  });
}

// ==================== 审计日志 ====================

/** 操作类型中文名映射 */
const AUDIT_TYPE_NAMES = {
  INSERT: "新增",
  UPDATE: "修改",
  DELETE: "删除",
  SELECT: "查询",
  LOGIN: "登录",
  LOGOUT: "登出",
  UPLOAD: "上传",
  DOWNLOAD: "下载",
  EXPORT: "导出",
  IMPORT: "导入",
  OTHER: "其他",
};

async function renderAuditLogs() {
  const module = $("#audit-keyword").value.trim();
  const operatorName = $("#audit-operator").value.trim();
  const page = state.auditLogs.page;
  const size = state.auditLogs.size;
  const data = await api(`/admin-api/v1/audit-logs?page=${page}&size=${size}`
      + (module ? `&module=${encodeURIComponent(module)}` : "")
      + (operatorName ? `&operatorName=${encodeURIComponent(operatorName)}` : ""));
  state.auditLogs.total = data.total || 0;

  $("#audit-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>操作人</th><th>模块</th><th>类型</th><th>描述</th><th>方法</th><th>URL</th><th>状态</th><th>耗时</th><th>时间</th></tr></thead>
      <tbody>${(data.list || []).map((log) => `
        <tr>
          <td>${log.id}</td>
          <td>${esc(log.operatorName ?? log.operatorId ?? "-")}</td>
          <td>${esc(log.module ?? "-")}</td>
          <td>${esc(AUDIT_TYPE_NAMES[log.operationType] ?? log.operationType ?? "-")}</td>
          <td>${esc(log.description ?? "-")}</td>
          <td>${esc(log.method ?? "-")}</td>
          <td title="${esc(log.requestUrl ?? "")}">${esc(log.requestUrl ?? "-")}</td>
          <td>${log.status === "SUCCESS" ? "✔ 成功" : log.status === "FAILED" ? "✘ 失败" : "-"}</td>
          <td>${log.duration != null ? `${log.duration}ms` : "-"}</td>
          <td>${fmtTime(log.createTime)}</td>
        </tr>`).join("") || '<tr><td colspan="10" class="empty">暂无审计日志</td></tr>'}
      </tbody>
    </table>`;

  renderPagination($("#audit-pagination"), state.auditLogs, () => {
    renderAuditLogs();
  });
}

// ==================== API调用日志 ====================

/** 调用状态中文名映射（0-成功 1-客户端错误 2-服务端错误 3-网络异常） */
const API_CALL_STATUS_NAMES = {
  0: "成功",
  1: "客户端错误",
  2: "服务端错误",
  3: "网络异常",
};

async function renderApiCallLogs() {
  const serviceName = $("#apicall-service").value.trim();
  const status = $("#apicall-status").value.trim();
  const traceId = $("#apicall-traceid").value.trim();
  const path = $("#apicall-path").value.trim();
  const page = state.apiCallLogs.page;
  const size = state.apiCallLogs.size;
  const params = new URLSearchParams({ page, size });
  if (serviceName) params.set("serviceName", serviceName);
  if (status) params.set("status", status);
  if (traceId) params.set("traceId", traceId);
  if (path) params.set("path", path);
  const data = await api(`/admin-api/v1/api-call-logs?${params.toString()}`);
  state.apiCallLogs.total = data.total || 0;

  $("#apicall-table").innerHTML = `
    <table class="table">
      <thead><tr><th>ID</th><th>服务</th><th>方法</th><th>路径</th><th>响应码</th><th>状态</th><th>耗时</th><th>错误</th><th>TraceId</th><th>调用时间</th></tr></thead>
      <tbody>${(data.list || []).map((log) => `
        <tr>
          <td>${log.id}</td>
          <td>${esc(log.serviceName ?? "-")}</td>
          <td>${esc(log.method ?? "-")}</td>
          <td title="${esc(log.url ?? "")}">${esc(log.path ?? "-")}</td>
          <td>${esc(log.responseStatus ?? "-")}</td>
          <td>${esc(API_CALL_STATUS_NAMES[log.status] ?? "-")}</td>
          <td>${log.executionTime != null ? `${log.executionTime}ms` : "-"}</td>
          <td title="${esc(log.errorMsg ?? "")}">${esc((log.errorMsg ?? "-").slice(0, 40))}</td>
          <td>${esc(log.traceId ?? "-")}</td>
          <td>${fmtTime(log.callTime ?? log.createTime)}</td>
        </tr>`).join("") || '<tr><td colspan="10" class="empty">暂无API调用日志</td></tr>'}
      </tbody>
    </table>`;

  renderPagination($("#apicall-pagination"), state.apiCallLogs, () => {
    renderApiCallLogs();
  });
}

// ==================== 分页 ====================

function renderPagination(container, pager, onPageChange) {
  const pages = Math.max(Math.ceil((pager.total || 0) / pager.size), 1);
  const current = Math.min(pager.page, pages);
  let html = `<button class="page-button" data-page="${current - 1}" ${current <= 1 ? "disabled" : ""}>上一页</button>`;
  html += `<span class="page-info">第 ${current} / ${pages} 页 · 共 ${pager.total} 条</span>`;
  html += `<button class="page-button" data-page="${current + 1}" ${current >= pages ? "disabled" : ""}>下一页</button>`;
  container.innerHTML = html;
  container.querySelectorAll("[data-page]").forEach((button) => {
    button.onclick = () => {
      const target = Number(button.dataset.page);
      if (target < 1 || target > pages) return;
      pager.page = target;
      onPageChange();
    };
  });
}

// ==================== 初始化 ====================

function init() {
  // 登录表单
  $("#login-form").addEventListener("submit", (event) => {
    event.preventDefault();
    login();
  });
  $("#send-code").addEventListener("click", sendSmsCode);
  $("#captcha-image").addEventListener("click", loadCaptcha);
  $("#logout-button").addEventListener("click", logout);

  // 导航
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.addEventListener("click", () => switchView(button.dataset.view));
  });

  // 查询按钮
  $("#user-search").addEventListener("click", () => {
    state.users.page = 1;
    renderUsers();
  });
  $("#log-search").addEventListener("click", () => {
    state.logs.page = 1;
    renderLoginLogs();
  });
  $("#audit-search").addEventListener("click", () => {
    state.auditLogs.page = 1;
    renderAuditLogs();
  });
  $("#apicall-search").addEventListener("click", () => {
    state.apiCallLogs.page = 1;
    renderApiCallLogs();
  });

  initDictCreators();

  if (state.token) {
    showApp();
  } else {
    showLogin();
  }
}

init();
