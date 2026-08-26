const SESSION_TOKEN_KEY = "apex.admin.access-token";
const SESSION_DEVICE_KEY = "apex.admin.device-id";
const PAGE_SIZE = 30;

const state = {
  token: sessionStorage.getItem(SESSION_TOKEN_KEY) || "",
  deviceId: sessionStorage.getItem(SESSION_DEVICE_KEY) || crypto.randomUUID(),
  roles: [],
  permissions: [],
  identity: null,
  view: "dashboard",
  photoItems: [],
  photoTotal: 0,
  photoOffset: 0,
  selectedPhoto: null,
  interestItems: [],
  interestTotal: 0,
  interestOffset: 0,
  selectedInterest: null,
  userItems: [],
  userPage: 1,
  userTotal: 0,
  selectedUser: null,
  riskItems: [],
  riskTotal: 0,
  riskTarget: null,
  riskAction: null,
  manageableRoles: [],
  reviewReasons: { photo: [], interest: [] },
  policyItems: [],
  appealItems: [],
  decisionTarget: null,
  dashboardLoading: false,
  editingUserId: null,
};
sessionStorage.setItem(SESSION_DEVICE_KEY, state.deviceId);

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

class ApiError extends Error {
  constructor(message, status, traceId) {
    super(message);
    this.status = status;
    this.traceId = traceId;
  }
}

function node(tag, options = {}, children = []) {
  const element = document.createElement(tag);
  if (options.className) element.className = options.className;
  if (options.text !== undefined) element.textContent = String(options.text);
  if (options.type) element.type = options.type;
  if (options.title) element.title = options.title;
  for (const [name, value] of Object.entries(options.dataset || {})) element.dataset[name] = value;
  for (const [name, value] of Object.entries(options.attributes || {})) element.setAttribute(name, value);
  for (const child of children.flat()) {
    if (child !== null && child !== undefined) element.append(child);
  }
  return element;
}

function normalizeRole(code) {
  return String(code || "").replace(/^ROLE_/, "");
}

function roleCodes() {
  return state.roles.map((role) => normalizeRole(role.code));
}

function permissionCodes() {
  return state.permissions.map((permission) => permission.code).filter(Boolean);
}

function hasRole(...codes) {
  const current = new Set(roleCodes());
  return codes.some((code) => current.has(code));
}

function hasPermission(code) {
  return permissionCodes().includes(code) || hasRole("ADMIN", "SUPER_ADMIN");
}

function canReviewPhotos() {
  return hasPermission("photo:review:read") || hasRole("REVIEW_MANAGER", "PHOTO_REVIEWER");
}

function canReviewInterests() {
  return hasPermission("interest:review:read") || hasRole("REVIEW_MANAGER", "INTEREST_REVIEWER");
}

function canManageUsers() {
  return hasRole("SUPER_ADMIN") && permissionCodes().includes("staff:manage");
}

function canManageHighRiskUsers() {
  return hasPermission("photo:review:decide") || hasRole("REVIEW_MANAGER", "PHOTO_REVIEWER", "SUPER_ADMIN");
}

function canManagePolicies() {
  return hasRole("SUPER_ADMIN") && permissionCodes().includes("review:policy:manage");
}

function canGovernCatalog() {
  return permissionCodes().includes("interest:graph:publish");
}

function canReviewAppeals() { return permissionCodes().includes("review:appeal:read"); }

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

function setSessionToken(token) {
  state.token = token || "";
  if (state.token) sessionStorage.setItem(SESSION_TOKEN_KEY, state.token);
  else sessionStorage.removeItem(SESSION_TOKEN_KEY);
}

function clearSession() {
  setSessionToken("");
  state.identity = null;
  state.roles = [];
  state.permissions = [];
  state.photoItems = [];
  state.interestItems = [];
  state.userItems = [];
  state.selectedUser = null;
}

function showLogin(message = "") {
  $("#app-shell").hidden = true;
  $("#login-view").hidden = false;
  const error = $("#login-error");
  error.textContent = message;
  error.hidden = !message;
  $("#login-phone").focus();
}

function showApp() {
  $("#login-view").hidden = true;
  $("#app-shell").hidden = false;
  applyCapabilities();
  renderIdentity();
  navigate("dashboard");
}

function showToast(message, type = "success") {
  const toast = node("div", { className: `toast ${type === "error" ? "error" : ""}`, text: message });
  $("#toast-region").append(toast);
  window.setTimeout(() => toast.remove(), 4200);
}

async function bootstrap() {
  updateClock();
  window.setInterval(updateClock, 30_000);
  bindEvents();
  if (!state.token) {
    showLogin();
    return;
  }
  try {
    await loadIdentity();
    showApp();
  } catch (error) {
    if (error.status !== 401) {
      clearSession();
      showLogin("无法验证后台访问权限，请重新登录。");
    }
  }
}

async function loadIdentity(loginData = null) {
  const [roles, permissions] = await Promise.all([
    api("/api/v1/auth/roles/my"),
    api("/api/v1/auth/permissions/my"),
  ]);
  state.roles = Array.isArray(roles) ? roles : [];
  state.permissions = Array.isArray(permissions) ? permissions : [];
  state.identity = loginData || state.identity || {};

  const allowedRoles = new Set([
    "ADMIN", "SUPER_ADMIN", "REVIEW_MANAGER", "PHOTO_REVIEWER", "INTEREST_REVIEWER", "INTEREST_PUBLISHER", "AUDITOR",
  ]);
  if (!roleCodes().some((role) => allowedRoles.has(role))) {
    throw new ApiError("当前账号未分配后台角色", 403, null);
  }
}

function bindEvents() {
  $("#login-form").addEventListener("submit", handleLogin);
  $("#send-code").addEventListener("click", sendSmsCode);
  $("#logout-button").addEventListener("click", logout);
  $("#mobile-menu").addEventListener("click", () => $(".sidebar").classList.toggle("open"));

  $$(".nav-item").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.view)));
  $$('[data-go]').forEach((button) => button.addEventListener("click", () => navigate(button.dataset.go)));
  $('[data-action="refresh-dashboard"]').addEventListener("click", loadDashboard);
  $('[data-refresh="photos"]').addEventListener("click", () => loadPhotos(true));
  $('[data-refresh="interests"]').addEventListener("click", () => loadInterests(true));
  $("#photo-status").addEventListener("change", () => loadPhotos(true));
  $("#photo-mine").addEventListener("change", () => loadPhotos(true));
  $("#interest-status").addEventListener("change", () => loadInterests(true));
  $("#interest-mine").addEventListener("change", () => loadInterests(true));
  $('[data-refresh="users"]').addEventListener("click", () => loadUsers(true));
  $('[data-refresh="risks"]').addEventListener("click", () => loadHighRiskUsers());
  $("#risk-search").addEventListener("click", loadHighRiskUsers);
  $("#risk-status").addEventListener("change", loadHighRiskUsers);
  $("#risk-keyword").addEventListener("keydown", (event) => { if (event.key === "Enter") loadHighRiskUsers(); });
  $("#risk-action-form").addEventListener("submit", submitRiskAction);
  $("#risk-action-close").addEventListener("click", closeRiskAction);
  $("#risk-action-cancel").addEventListener("click", closeRiskAction);
  $("#user-create").addEventListener("click", () => openUserForm());
  $("#user-form").addEventListener("submit", submitUserForm);
  $("#user-form-close").addEventListener("click", closeUserForm);
  $("#user-form-cancel").addEventListener("click", closeUserForm);
  $('[data-refresh="policies"]').addEventListener("click", loadPolicies);
  $("#policy-type").addEventListener("change", loadPolicies);
  $("#catalog-prepare").addEventListener("click", prepareCatalog);
  $("#catalog-readiness").addEventListener("click", checkCatalogReadiness);
  $('[data-refresh="appeals"]').addEventListener("click", loadAppeals);
  $("#appeal-status").addEventListener("change", loadAppeals);
  $("#user-search").addEventListener("click", () => loadUsers(true));
  $("#user-status").addEventListener("change", () => loadUsers(true));
  $("#user-keyword").addEventListener("keydown", (event) => {
    if (event.key === "Enter") loadUsers(true);
  });

  $$('input[name="decision"]').forEach((radio) => radio.addEventListener("change", syncDecisionFields));
  $("#decision-reason").addEventListener("change", syncDecisionFields);
  $("#decision-form").addEventListener("submit", submitDecision);
}

async function handleLogin(event) {
  event.preventDefault();
  const phone = $("#login-phone").value.trim();
  const smsCode = $("#login-code").value.trim();
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
      body: { loginType: "CL_SMS", phone, smsCode, deviceId: state.deviceId, platform: "h5" },
    });
    if (!loginData?.accessToken || loginData.nextAction !== "AUTHENTICATED") {
      throw new ApiError("该账号尚未完成注册或后台身份验证。", 403, null);
    }
    setSessionToken(loginData.accessToken);
    await loadIdentity(loginData);
    showApp();
    showToast("身份验证成功，欢迎进入审核控制台。");
  } catch (error) {
    clearSession();
    errorBox.textContent = error.message || "登录失败，请稍后重试。";
    errorBox.hidden = false;
  } finally {
    submit.disabled = false;
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

async function logout() {
  try {
    if (state.token) await api("/api/v1/auth/logout", { method: "POST" });
  } catch {
    // Local session cleanup must still happen if the server is unavailable.
  } finally {
    clearSession();
    showLogin();
  }
}

function applyCapabilities() {
  $('[data-capability="photo"]').disabled = !canReviewPhotos();
  $('[data-capability="interest"]').disabled = !canReviewInterests();
  $('[data-go="photos"]').disabled = !canReviewPhotos();
  $('[data-go="interests"]').disabled = !canReviewInterests();
  $('[data-capability="users"]').hidden = !canManageUsers();
  $('[data-capability="risks"]').hidden = !canManageHighRiskUsers();
  $('[data-capability="policies"]').hidden = !canManagePolicies();
  $('[data-capability="catalog"]').hidden = !canGovernCatalog();
  $('[data-capability="appeals"]').hidden = !canReviewAppeals();
}

function renderIdentity() {
  const displayName = state.identity?.userInfo?.nickName || maskPhone($("#login-phone").value) || "审核人员";
  const primaryRole = state.roles[0]?.name || roleCodes()[0] || "后台人员";
  $("#operator-name").textContent = displayName;
  $("#operator-role").textContent = primaryRole;
  $("#operator-avatar").textContent = displayName.slice(0, 1);
  $("#session-label").textContent = "当前标签页有效";
  renderChips($("#dashboard-roles"), state.roles.map((role) => role.name || normalizeRole(role.code)));
  renderChips($("#security-roles"), state.roles.map((role) => role.name || normalizeRole(role.code)));

  const capabilities = [];
  if (canReviewPhotos()) capabilities.push("照片审核");
  if (canReviewInterests()) capabilities.push("标签审核");
  if (hasRole("INTEREST_PUBLISHER")) capabilities.push("图谱发布");
  if (hasPermission("audit:read")) capabilities.push("审计读取");
  if (canManageUsers()) capabilities.push("用户管理");
  if (canManageHighRiskUsers()) capabilities.push("高风险用户处置");
  $("#access-summary").textContent = capabilities.length
    ? `可访问：${capabilities.join("、")}。最终授权始终由服务端判定。`
    : "当前角色没有审核队列权限，可查看本页的会话与授权信息。";

  const permissionList = $("#security-permissions");
  const items = state.permissions.map((permission) => node("div", { className: "permission-item" }, [
    node("strong", { text: permission.name || "未命名权限" }),
    node("span", { text: permission.code || "—" }),
  ]));
  permissionList.replaceChildren(...(items.length ? items : [node("p", { className: "muted", text: "当前角色没有显式权限记录。" })]));
}

function renderChips(container, values) {
  const unique = [...new Set(values.filter(Boolean))];
  container.replaceChildren(...unique.map((value) => node("span", { className: "chip", text: value })));
}

function maskPhone(value) {
  return /^\d{11}$/.test(value || "") ? `${value.slice(0, 3)}****${value.slice(-4)}` : "";
}

function navigate(view) {
  if (view === "photos" && !canReviewPhotos()) return;
  if (view === "interests" && !canReviewInterests()) return;
  if (view === "users" && !canManageUsers()) return;
  if (view === "risks" && !canManageHighRiskUsers()) return;
  if (view === "policies" && !canManagePolicies()) return;
  if (view === "catalog" && !canGovernCatalog()) return;
  if (view === "appeals" && !canReviewAppeals()) return;
  state.view = view;
  const titles = {
    dashboard: ["OPERATIONS OVERVIEW", "审核工作台"],
    photos: ["PHOTO MODERATION", "照片审核"],
    interests: ["INTEREST GOVERNANCE", "标签审核"],
    users: ["USER MANAGEMENT", "用户管理"],
    risks: ["PHOTO RISK", "高风险用户管理"],
    policies: ["REVIEW POLICY", "审核配置"],
    catalog: ["INTEREST CATALOG", "图谱治理"],
    appeals: ["SECOND REVIEW", "复核申诉"],
    security: ["ACCESS CONTROL", "权限与安全"],
  };
  $$(".view-panel").forEach((panel) => panel.classList.toggle("active", panel.id === `view-${view}`));
  $$(".nav-item").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  $("#view-kicker").textContent = titles[view][0];
  $("#view-title").textContent = titles[view][1];
  $(".sidebar").classList.remove("open");
  $("#main-content").focus({ preventScroll: true });
  if (view === "dashboard") loadDashboard();
  if (view === "photos" && state.photoItems.length === 0) loadPhotos(true);
  if (view === "interests" && state.interestItems.length === 0) loadInterests(true);
  if (view === "users" && state.userItems.length === 0) loadUsers(true);
  if (view === "risks" && state.riskItems.length === 0) loadHighRiskUsers();
  if (view === "policies") loadPolicies();
  if (view === "appeals") loadAppeals();
}

async function loadDashboard() {
  if (state.dashboardLoading) return;
  state.dashboardLoading = true;
  const queries = [];
  if (canReviewPhotos()) {
    queries.push(api("/admin-api/v1/photo-reviews?status=WAITING_REVIEW&offset=0&limit=1"));
    queries.push(api("/admin-api/v1/photo-reviews?status=IN_REVIEW&mine=true&offset=0&limit=1"));
  } else queries.push(Promise.resolve({ total: 0 }), Promise.resolve({ total: 0 }));
  if (canReviewInterests()) {
    queries.push(api("/admin-api/v1/interest-reviews?status=OPEN&offset=0&limit=1"));
    queries.push(api("/admin-api/v1/interest-reviews?status=PENDING_REVIEW&offset=0&limit=1"));
    queries.push(api("/admin-api/v1/interest-reviews?status=REVIEWING&mine=true&offset=0&limit=1"));
  } else queries.push(Promise.resolve({ total: 0 }), Promise.resolve({ total: 0 }), Promise.resolve({ total: 0 }));
  try {
    const [photoPending, photoMine, interestOpen, interestPending, interestMine] = await Promise.all(queries);
    const interestWaiting = Number(interestOpen.total || 0) + Number(interestPending.total || 0);
    setMetric("#metric-photo-pending", photoPending.total);
    setMetric("#metric-interest-pending", interestWaiting);
    setMetric("#metric-mine", Number(photoMine.total || 0) + Number(interestMine.total || 0));
    $("#photo-nav-count").textContent = compactNumber(photoPending.total);
    $("#interest-nav-count").textContent = compactNumber(interestWaiting);
  } catch (error) {
    showToast(error.message || "总览数据加载失败。", "error");
  } finally {
    state.dashboardLoading = false;
  }
}

function setMetric(selector, value) {
  $(selector).textContent = compactNumber(value);
}

function compactNumber(value) {
  const number = Number(value || 0);
  return number > 999 ? `${(number / 1000).toFixed(1)}k` : String(number);
}

async function loadPhotos(reset) {
  if (reset) {
    state.photoOffset = 0;
    state.photoItems = [];
    state.selectedPhoto = null;
    renderEmptyDetail("photo");
  }
  renderListLoading("photo", reset ? "正在读取照片审核队列…" : "正在加载更多任务…");
  const status = $("#photo-status").value;
  const mine = $("#photo-mine").checked;
  const query = new URLSearchParams({ offset: String(state.photoOffset), limit: String(PAGE_SIZE), mine: String(mine) });
  if (status) query.set("status", status);
  try {
    const page = await api(`/admin-api/v1/photo-reviews?${query}`);
    state.photoItems = reset ? page.items : [...state.photoItems, ...page.items];
    state.photoTotal = page.total;
    state.photoOffset = state.photoItems.length;
    renderPhotoList();
  } catch (error) {
    renderListError("photo", error.message);
  }
}

function renderPhotoList() {
  const container = $("#photo-list");
  $("#photo-total").textContent = `共 ${state.photoTotal} 项 · 已加载 ${state.photoItems.length} 项`;
  if (!state.photoItems.length) {
    container.replaceChildren(node("div", { className: "loading-state", text: "当前筛选条件下没有照片审核任务。" }));
    return;
  }
  const rows = state.photoItems.map((item) => {
    const button = node("button", { className: `task-row ${state.selectedPhoto?.taskId === item.taskId ? "selected" : ""}`, type: "button" }, [
      node("div", { className: "task-top" }, [
        node("strong", { text: `照片 #${item.photoId}` }),
        statusBadge(item.status),
      ]),
      node("span", { className: "task-id", text: `TASK ${item.taskId} · V${item.version}` }),
      node("div", { className: "task-meta" }, [
        node("span", { text: item.provider || "未知审核源" }),
        node("span", { text: formatTime(item.submittedAt) }),
      ]),
      node("span", { className: "task-id", text: `命中原因：${primaryModerationReason(item.findingsJson)}` }),
    ]);
    button.addEventListener("click", () => selectPhoto(item.taskId));
    return button;
  });
  if (state.photoItems.length < state.photoTotal) rows.push(loadMoreButton("photo"));
  container.replaceChildren(...rows);
}

async function selectPhoto(taskId) {
  renderDetailLoading("photo");
  try {
    state.selectedPhoto = await api(`/admin-api/v1/photo-reviews/${encodeURIComponent(taskId)}`);
    renderPhotoList();
    renderPhotoDetail();
  } catch (error) {
    renderDetailError("photo", error.message);
  }
}

function renderPhotoDetail() {
  const item = state.selectedPhoto;
  if (!item) return renderEmptyDetail("photo");
  const header = detailHeader(`照片 #${item.photoId}`, `TASK ${item.taskId}`, item.status);
  const preview = node("div", { className: "preview-frame" });
  if (item.previewUrl) {
    const image = node("img", { attributes: { src: item.previewUrl, alt: `待审核照片 ${item.photoId}`, referrerpolicy: "no-referrer" } });
    image.addEventListener("error", () => preview.replaceChildren(node("span", { className: "preview-placeholder", text: "安全预览已过期，请重新选择任务。" })));
    preview.append(image, node("span", { className: "preview-expiry", text: `${item.previewExpiresInSeconds || 60}s 临时预览` }));
  } else preview.append(node("span", { className: "preview-placeholder", text: "该任务暂无可用安全预览" }));

  const content = node("div", { className: "detail-content" }, [
    header,
    preview,
    facts([
      ["机器结论", item.machineDecision || "—"], ["审核提供方", item.provider || "—"],
      ["提交时间", formatTime(item.submittedAt)], ["租约截止", formatTime(item.leaseExpiresAt)],
      ["审核人 ID", item.reviewerId || "未领取"], ["当前版本", item.version],
    ]),
    evidenceSection("场景识别", parseEvidence(item.scenesJson)),
    moderationReasonsSection(item.findingsJson),
    photoActions(item),
  ]);
  $("#photo-detail").replaceChildren(content);
}

function photoActions(item) {
  const actions = node("div", { className: "detail-actions" });
  if (item.status === "WAITING_REVIEW") {
    const claim = node("button", { className: "claim-button", type: "button", text: "领取任务" });
    claim.addEventListener("click", () => claimPhoto(item));
    actions.append(claim);
  } else if (item.status === "IN_REVIEW") {
    const approve = node("button", { className: "approve-button", type: "button", text: "通过照片" });
    const reject = node("button", { className: "reject-button", type: "button", text: "拒绝照片" });
    approve.addEventListener("click", () => openDecision("photo", item, "APPROVE"));
    reject.addEventListener("click", () => openDecision("photo", item, "REJECT"));
    actions.append(approve, reject);
  } else actions.append(node("span", { className: "muted", text: "该任务已完成裁决，仅供查看。" }));
  return actions;
}

async function claimPhoto(item) {
  try {
    const claimed = await api(`/admin-api/v1/photo-reviews/${item.taskId}/claim`, {
      method: "POST", body: { expectedVersion: item.version },
    });
    state.selectedPhoto = claimed;
    showToast("照片任务已领取，租约有效期为 15 分钟。");
    await loadPhotos(true);
    await selectPhoto(item.taskId);
    loadDashboard();
  } catch (error) {
    showToast(error.message || "领取失败，请刷新任务。", "error");
  }
}

async function loadInterests(reset) {
  if (reset) {
    state.interestOffset = 0;
    state.interestItems = [];
    state.selectedInterest = null;
    renderEmptyDetail("interest");
  }
  renderListLoading("interest", reset ? "正在读取新增标签审核队列…" : "正在加载更多申请…");
  const status = $("#interest-status").value;
  const mine = $("#interest-mine").checked;
  const query = new URLSearchParams({ offset: String(state.interestOffset), limit: String(PAGE_SIZE), mine: String(mine) });
  if (status) query.set("status", status);
  try {
    const page = await api(`/admin-api/v1/interest-reviews?${query}`);
    state.interestItems = reset ? page.items : [...state.interestItems, ...page.items];
    state.interestTotal = page.total;
    state.interestOffset = state.interestItems.length;
    renderInterestList();
  } catch (error) {
    renderListError("interest", error.message);
  }
}

function renderInterestList() {
  const container = $("#interest-list");
  $("#interest-total").textContent = `共 ${state.interestTotal} 项 · 已加载 ${state.interestItems.length} 项`;
  if (!state.interestItems.length) {
    container.replaceChildren(node("div", { className: "loading-state", text: "当前筛选条件下没有新增标签申请。" }));
    return;
  }
  const rows = state.interestItems.map((item) => {
    const button = node("button", { className: `task-row ${state.selectedInterest?.candidateId === item.candidateId ? "selected" : ""}`, type: "button" }, [
      node("div", { className: "task-top" }, [node("strong", { text: item.normalizedText || "未命名标签" }), statusBadge(item.status)]),
      node("span", { className: "task-id", text: `${shortId(item.candidateId)} · V${item.version}` }),
      node("div", { className: "task-meta" }, [
        node("span", { text: `证据分 ${formatScore(item.evidenceScore)}` }),
        node("span", { text: formatTime(item.createdAt) }),
      ]),
    ]);
    button.addEventListener("click", () => selectInterest(item.candidateId));
    return button;
  });
  if (state.interestItems.length < state.interestTotal) rows.push(loadMoreButton("interest"));
  container.replaceChildren(...rows);
}

async function selectInterest(candidateId) {
  renderDetailLoading("interest");
  try {
    state.selectedInterest = await api(`/admin-api/v1/interest-reviews/${encodeURIComponent(candidateId)}`);
    renderInterestList();
    renderInterestDetail();
  } catch (error) {
    renderDetailError("interest", error.message);
  }
}

function renderInterestDetail() {
  const item = state.selectedInterest;
  if (!item) return renderEmptyDetail("interest");
  const signalChips = node("div", { className: "chip-list" }, (item.evidenceSignals || []).map((signal) => node("span", { className: "chip", text: signal })));
  const content = node("div", { className: "detail-content" }, [
    detailHeader(item.normalizedText || "未命名标签", shortId(item.candidateId), item.status),
    facts([
      ["候选类型", item.type], ["证据得分", formatScore(item.evidenceScore)],
      ["基础图版本", item.baseGraphVersion], ["当前版本", item.version],
      ["审核人员", item.reviewerId || "未领取"], ["候选过期", formatTime(item.expiresAt)],
    ]),
    node("div", { className: "evidence-block" }, [node("h4", { text: "证据信号" }), signalChips]),
    evidenceSection("证据属性", Object.entries(item.evidenceAttributes || {})),
    evidenceSection("关联节点", (item.nodeIds || []).map((value, index) => [`节点 ${index + 1}`, value])),
    interestActions(item),
  ]);
  $("#interest-detail").replaceChildren(content);
}

function interestActions(item) {
  const actions = node("div", { className: "detail-actions" });
  if (["OPEN", "PENDING_REVIEW"].includes(item.status)) {
    const claim = node("button", { className: "claim-button", type: "button", text: "领取申请" });
    claim.addEventListener("click", () => claimInterest(item));
    actions.append(claim);
  } else if (item.status === "REVIEWING") {
    const approve = node("button", { className: "approve-button", type: "button", text: "通过标签" });
    const reject = node("button", { className: "reject-button", type: "button", text: "拒绝标签" });
    approve.addEventListener("click", () => openDecision("interest", item, "APPROVE"));
    reject.addEventListener("click", () => openDecision("interest", item, "REJECT"));
    actions.append(approve, reject);
  } else actions.append(node("span", { className: "muted", text: "该申请已完成审核，仅供查看。" }));
  return actions;
}

async function claimInterest(item) {
  try {
    const claimed = await api(`/admin-api/v1/interest-reviews/${encodeURIComponent(item.candidateId)}/claim`, {
      method: "POST", body: { expectedVersion: item.version },
    });
    state.selectedInterest = claimed;
    showToast("新增标签申请已领取，租约有效期为 15 分钟。");
    await loadInterests(true);
    await selectInterest(item.candidateId);
    loadDashboard();
  } catch (error) {
    showToast(error.message || "领取失败，请刷新申请。", "error");
  }
}

function detailHeader(title, id, status) {
  return node("div", { className: "detail-title" }, [
    node("div", {}, [node("span", { className: "task-id", text: id }), node("h3", { text: title })]),
    statusBadge(status),
  ]);
}

function statusBadge(status) {
  const labels = {
    WAITING_REVIEW: "等待审核", IN_REVIEW: "审核中", OPEN: "等待审核", PENDING_REVIEW: "等待审核",
    REVIEWING: "审核中", APPROVED: "已通过", REJECTED: "已拒绝", EXPIRED: "已过期",
  };
  const style = ["WAITING_REVIEW", "OPEN", "PENDING_REVIEW"].includes(status)
    ? "waiting" : status === "IN_REVIEW" || status === "REVIEWING" ? "reviewing" : String(status || "").toLowerCase();
  return node("span", { className: `status-badge ${style}`, text: labels[status] || status || "未知" });
}

function facts(entries) {
  return node("div", { className: "detail-facts" }, entries.map(([label, value]) => node("div", { className: "fact" }, [
    node("span", { text: label }), node("strong", { text: value ?? "—" }),
  ])));
}

function evidenceSection(title, entries) {
  const normalized = Array.isArray(entries) ? entries : [];
  const list = normalized.length
    ? normalized.slice(0, 24).map(([key, value]) => node("li", {}, [node("span", { text: key }), node("strong", { text: displayValue(value) })]))
    : [node("li", {}, [node("span", { text: "暂无数据" })])];
  return node("div", { className: "evidence-block" }, [node("h4", { text: title }), node("ul", { className: "evidence-list" }, list)]);
}

function moderationReasonsSection(raw) {
  const findings = parseJsonArray(raw);
  if (!findings.length) return evidenceSection("百度审核原因", []);
  const cards = findings.slice(0, 24).map((finding, index) => {
    if (!finding || typeof finding !== "object") {
      return node("article", { className: "moderation-reason-card" }, [
        node("strong", { text: `审核发现 ${index + 1}` }), node("p", { text: displayValue(finding) }),
      ]);
    }
    const details = finding.details && typeof finding.details === "object" ? finding.details : {};
    const rate = finding.rate === null || finding.rate === undefined ? Number.NaN : Number(finding.rate);
    const confidence = Number.isFinite(rate) ? `${rate.toFixed(1)}%` : "—";
    const rows = [
      ["审核结论", moderationSuggestionLabel(finding.suggestion)],
      ["命中原因", finding.label || details.msg || details.conclusion || "—"],
      ["风险场景", moderationSceneLabel(finding.scene)],
      ["置信度", confidence],
      ["百度类型", details.type ?? "—"],
      ["百度子类型", details.subType ?? "—"],
    ];
    return node("article", { className: "moderation-reason-card" }, [
      node("div", { className: "moderation-reason-head" }, [
        node("strong", { text: finding.label || details.msg || `审核发现 ${index + 1}` }),
        moderationSuggestionBadge(finding.suggestion),
      ]),
      facts(rows),
    ]);
  });
  return node("section", { className: "evidence-block" }, [
    node("h4", { text: "百度审核原因" }),
    node("div", { className: "moderation-reason-list" }, cards),
  ]);
}

function parseJsonArray(raw) {
  if (!raw) return [];
  try {
    const value = typeof raw === "string" ? JSON.parse(raw) : raw;
    return Array.isArray(value) ? value : value ? [value] : [];
  } catch {
    return [];
  }
}

function primaryModerationReason(raw) {
  const findings = parseJsonArray(raw).filter((item) => item && typeof item === "object");
  const finding = findings.find((item) => ["block", "review"].includes(String(item.suggestion || "").toLowerCase()))
    || findings[0];
  return finding?.label || finding?.details?.msg || finding?.details?.conclusion || "待查看详情";
}

function moderationSuggestionLabel(value) {
  return { pass: "通过", review: "需要人工确认", block: "违规" }[String(value || "").toLowerCase()] || value || "—";
}

function moderationSuggestionBadge(value) {
  const suggestion = String(value || "review").toLowerCase();
  const style = suggestion === "pass" ? "approved" : suggestion === "block" ? "rejected" : "waiting";
  return node("span", { className: `status-badge ${style}`, text: moderationSuggestionLabel(suggestion) });
}

function moderationSceneLabel(value) {
  const labels = {
    forbidden_image: "违禁图像", porn: "色情内容", terrorism: "暴恐内容", disgusting: "恶心内容",
    ad: "广告内容", political: "政治敏感", quality: "图像质量", custom_blacklist: "自定义黑名单",
    custom_whitelist: "自定义白名单", avatar: "头像风险", text: "文字风险", custom_model: "自定义模型",
    prohibited: "违禁内容", live: "直播风险", public_figure: "公众人物", official_scene: "官方场景",
    custom_scene: "自定义场景", overall: "百度综合结论",
    python_fgclip_violation: "Python FGCLIP 违规检测",
  };
  return labels[value] || value || "—";
}

function parseEvidence(raw) {
  if (!raw) return [];
  try {
    const value = JSON.parse(raw);
    if (Array.isArray(value)) return value.map((entry, index) => [`项目 ${index + 1}`, displayValue(entry)]);
    if (value && typeof value === "object") return Object.entries(value);
    return [["结果", value]];
  } catch {
    return [["原始结果", raw]];
  }
}

function displayValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function formatTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(date);
}

function formatScore(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(3) : "—";
}

function shortId(value) {
  const text = String(value || "—");
  return text.length > 24 ? `${text.slice(0, 12)}…${text.slice(-8)}` : text;
}

function loadMoreButton(kind) {
  const button = node("button", { className: "task-row", type: "button", text: "加载更多任务 ↓" });
  button.addEventListener("click", () => kind === "photo" ? loadPhotos(false) : loadInterests(false));
  return button;
}

function renderListLoading(kind, message) {
  const container = kind === "photo" ? $("#photo-list") : $("#interest-list");
  if ((kind === "photo" ? state.photoItems : state.interestItems).length === 0) {
    container.replaceChildren(node("div", { className: "loading-state", text: message }));
  }
}

function renderListError(kind, message) {
  const container = kind === "photo" ? $("#photo-list") : $("#interest-list");
  container.replaceChildren(node("div", { className: "error-state", text: message || "队列读取失败" }));
}

function renderDetailLoading(kind) {
  const container = kind === "photo" ? $("#photo-detail") : $("#interest-detail");
  container.replaceChildren(node("div", { className: "loading-state", text: "正在安全读取任务详情…" }));
}

function renderDetailError(kind, message) {
  const container = kind === "photo" ? $("#photo-detail") : $("#interest-detail");
  container.replaceChildren(node("div", { className: "error-state", text: message || "详情读取失败" }));
}

function renderEmptyDetail(kind) {
  const isPhoto = kind === "photo";
  const container = isPhoto ? $("#photo-detail") : $("#interest-detail");
  container.replaceChildren(node("div", { className: "empty-detail" }, [
    node("span", { text: isPhoto ? "02 / PHOTO" : "03 / TAG" }),
    node("p", { text: isPhoto ? "从左侧选择一项任务查看安全预览与机器审核信息。" : "从左侧选择一项申请查看证据、信号与建议标签。" }),
  ]));
}

async function openDecision(kind, item, decision) {
  state.decisionTarget = { kind, item };
  const title = kind === "photo" ? `照片 #${item.photoId}` : item.normalizedText;
  $("#decision-title").textContent = kind === "photo" ? "提交照片审核结论" : "提交标签审核结论";
  $("#decision-context").textContent = `${title} · 当前版本 V${item.version}。提交后结论将写入业务审计。`;
  $(`input[name="decision"][value="${decision}"]`).checked = true;
  $("#decision-comment").value = "";
  if (decision === "REJECT") {
    try {
      const reasons = await loadDecisionReasons(kind);
      $("#decision-reason").replaceChildren(...reasons.map((reason) =>
        node("option", { text: reason.label, attributes: { value: reason.code } })));
    } catch (error) {
      showToast(error.message || "审核原因加载失败", "error");
      return;
    }
  }
  $("#decision-error").hidden = true;
  syncDecisionFields();
  $("#decision-dialog").showModal();
}

function syncDecisionFields() {
  const decision = $('input[name="decision"]:checked').value;
  const isPhoto = state.decisionTarget?.kind === "photo";
  $("#reason-field").hidden = decision !== "REJECT";
  const reasons = state.reviewReasons[state.decisionTarget?.kind] || [];
  const selected = reasons.find((reason) => reason.code === $("#decision-reason").value);
  $("#comment-label").textContent = selected?.requireComment ? "审核备注（必填）" : "审核备注（可选）";
  $("#decision-comment").maxLength = isPhoto ? 500 : 2048;
}

async function submitDecision(event) {
  event.preventDefault();
  if (event.submitter?.value === "cancel") {
    $("#decision-dialog").close();
    return;
  }
  const target = state.decisionTarget;
  if (!target) return;
  const decision = $('input[name="decision"]:checked').value;
  const comment = $("#decision-comment").value.trim();
  const errorBox = $("#decision-error");
  const selectedReason = state.reviewReasons[target.kind]?.find((reason) => reason.code === $("#decision-reason").value);
  if (decision === "REJECT" && selectedReason?.requireComment && !comment) {
    errorBox.textContent = "该拒绝原因必须填写审核备注。";
    errorBox.hidden = false;
    return;
  }
  const confirm = $("#confirm-decision");
  confirm.disabled = true;
  errorBox.hidden = true;
  try {
    if (target.kind === "photo") {
      await api(`/admin-api/v1/photo-reviews/${target.item.taskId}/decision`, {
        method: "POST",
        body: {
          decisionId: crypto.randomUUID(), decision,
          reasonCode: decision === "REJECT" ? $("#decision-reason").value : null,
          comment: comment || null, expectedVersion: target.item.version,
        },
      });
      $("#decision-dialog").close();
      showToast(`照片已${decision === "APPROVE" ? "通过" : "拒绝"}。`);
      await loadPhotos(true);
    } else {
      await api(`/admin-api/v1/interest-reviews/${encodeURIComponent(target.item.candidateId)}/decision`, {
        method: "POST", body: { decision, reasonCode: decision === "REJECT" ? $("#decision-reason").value : null,
          comment: comment || null, expectedVersion: target.item.version },
      });
      $("#decision-dialog").close();
      showToast(`新增标签已${decision === "APPROVE" ? "通过" : "拒绝"}。`);
      await loadInterests(true);
    }
    loadDashboard();
  } catch (error) {
    errorBox.textContent = error.message || "裁决提交失败，请刷新后重试。";
    errorBox.hidden = false;
  } finally {
    confirm.disabled = false;
  }
}

async function loadDecisionReasons(kind) {
  if (!state.reviewReasons[kind]?.length) {
    state.reviewReasons[kind] = await api(`/admin-api/v1/review-reasons/${kind}`);
  }
  return state.reviewReasons[kind];
}

async function loadHighRiskUsers() {
  if (!canManageHighRiskUsers()) return;
  $("#risk-list").replaceChildren(node("div", { className: "loading-state", text: "加载中…" }));
  const query = new URLSearchParams({ page: "1", size: String(PAGE_SIZE) });
  const keyword = $("#risk-keyword").value.trim();
  const status = $("#risk-status").value;
  if (keyword) query.set("keyword", keyword);
  if (status) query.set("status", status);
  try {
    const page = await api(`/admin-api/v1/high-risk-users?${query}`);
    state.riskItems = Array.isArray(page.list) ? page.list : [];
    state.riskTotal = Number(page.total || 0);
    renderHighRiskUsers();
  } catch (error) {
    $("#risk-list").replaceChildren(node("div", { className: "loading-state error-text", text: error.message }));
  }
}

function renderHighRiskUsers() {
  $("#risk-total").textContent = `${state.riskTotal} 位高风险用户`;
  if (!state.riskItems.length) {
    $("#risk-list").replaceChildren(node("div", { className: "loading-state", text: "当前没有风险合计大于 5 的用户" }));
    return;
  }
  const cards = state.riskItems.map((user) => {
    const review = Number(user.photoReviewCount || 0);
    const blocked = Number(user.photoBlockedCount || 0);
    const ban = node("button", { className: "secondary-button", type: "button", text: "封禁" });
    const remove = node("button", { className: "reject-button", type: "button", text: "注销" });
    ban.disabled = user.status !== 1;
    ban.addEventListener("click", () => openRiskAction(user, "ban"));
    remove.addEventListener("click", () => openRiskAction(user, "delete"));
    return node("article", { className: "security-card" }, [
      node("div", { className: "section-inline-title" }, [
        node("div", {}, [node("h3", { text: user.nickname || "未设置昵称" }),
          node("p", { className: "muted", text: `${user.phone || "无手机号"} · #${user.id}` })]),
        statusBadge(user.status === 1 ? "正常" : "已封禁"),
      ]),
      facts([["人工审核", review], ["已拦截", blocked], ["风险合计", review + blocked],
        ["最近风险", formatTime(user.photoRiskUpdatedAt)]]),
      node("div", { className: "detail-actions" }, [ban, remove]),
    ]);
  });
  $("#risk-list").replaceChildren(...cards);
}

function openRiskAction(user, action) {
  state.riskTarget = user;
  state.riskAction = action;
  const deleting = action === "delete";
  $("#risk-action-title").textContent = deleting ? "注销高风险用户" : "封禁高风险用户";
  $("#risk-action-context").textContent = `${user.nickname || "未设置昵称"} · #${user.id} · 风险合计 ${Number(user.photoReviewCount || 0) + Number(user.photoBlockedCount || 0)}`;
  $("#risk-action-reason").value = "";
  $("#risk-phone-allowed").checked = true;
  $("#risk-wechat-allowed").checked = true;
  $("#risk-action-error").hidden = true;
  $("#risk-action-submit").textContent = deleting ? "确认注销" : "确认封禁";
  $("#risk-action-dialog").showModal();
}

function closeRiskAction() {
  $("#risk-action-dialog").close();
  state.riskTarget = null;
  state.riskAction = null;
}

async function submitRiskAction(event) {
  event.preventDefault();
  if (!state.riskTarget || !state.riskAction) return;
  const reason = $("#risk-action-reason").value.trim();
  const errorBox = $("#risk-action-error");
  if (!reason) {
    errorBox.textContent = "必须填写处置原因。";
    errorBox.hidden = false;
    return;
  }
  const submit = $("#risk-action-submit");
  submit.disabled = true;
  try {
    await api(`/admin-api/v1/high-risk-users/${encodeURIComponent(state.riskTarget.id)}/${state.riskAction}`, {
      method: "POST",
      body: {
        reason,
        phoneRegistrationAllowed: $("#risk-phone-allowed").checked,
        wechatRegistrationAllowed: $("#risk-wechat-allowed").checked,
      },
    });
    showToast(state.riskAction === "delete" ? "注销任务已受理" : "用户已封禁并撤销会话");
    closeRiskAction();
    await loadHighRiskUsers();
  } catch (error) {
    errorBox.textContent = error.message || "处置失败";
    errorBox.hidden = false;
  } finally {
    submit.disabled = false;
  }
}

async function loadUsers(reset) {
  if (!canManageUsers()) return;
  if (reset) {
    state.userPage = 1;
    state.userItems = [];
    state.selectedUser = null;
    $("#user-detail").replaceChildren(node("div", { className: "empty-detail" }, [
      node("span", { text: "USER" }), node("p", { text: "选择用户查看资料与生活照。" }),
    ]));
  }
  $("#user-list").replaceChildren(node("div", { className: "loading-state", text: "加载中…" }));
  const query = new URLSearchParams({ page: String(state.userPage), size: String(PAGE_SIZE) });
  const keyword = $("#user-keyword").value.trim();
  const status = $("#user-status").value;
  if (keyword) query.set("keyword", keyword);
  if (status) query.set("status", status);
  try {
    const page = await api(`/admin-api/v1/users?${query}`);
    state.userItems = Array.isArray(page.list) ? page.list : [];
    state.userTotal = Number(page.total || 0);
    renderUserList();
  } catch (error) {
    $("#user-list").replaceChildren(node("div", { className: "loading-state error-text", text: error.message }));
  }
}

function renderUserList() {
  $("#user-total").textContent = `${state.userTotal} 位用户`;
  if (!state.userItems.length) {
    $("#user-list").replaceChildren(node("div", { className: "loading-state", text: "没有匹配用户" }));
    return;
  }
  const rows = state.userItems.map((user) => {
    const name = user.nickname || "未设置昵称";
    const button = node("button", {
      className: `task-row user-row ${String(state.selectedUser?.id || "") === String(user.id) ? "selected" : ""}`, type: "button",
    }, [
      node("span", { className: "user-list-avatar", text: name.slice(0, 1) }),
      node("div", { className: "user-list-main" }, [
        node("strong", { text: name }),
        node("span", { text: `${user.phone || "无手机号"} · #${user.id}` }),
        node("span", { text: `曝光状态 ${activityStatusLabel(user.activityStatus)}` }),
        node("span", { text: `人工审核 ${user.photoReviewCount || 0} · 拦截 ${user.photoBlockedCount || 0}` }),
        node("div", { className: "chip-list compact-chips" }, (user.roles || []).map((role) =>
          node("span", { className: "chip", text: role.name || normalizeRole(role.code) }))),
      ]),
      statusBadge(user.status === 1 ? "正常" : "禁用"),
    ]);
    button.addEventListener("click", () => selectUser(user.id));
    return button;
  });
  $("#user-list").replaceChildren(...rows);
}

async function selectUser(userId) {
  $("#user-detail").replaceChildren(node("div", { className: "loading-state", text: "加载资料…" }));
  try {
    if (!state.manageableRoles.length) state.manageableRoles = await api("/admin-api/v1/staff-roles");
    state.selectedUser = await api(`/admin-api/v1/users/${encodeURIComponent(userId)}`);
    renderUserList();
    renderUserDetail();
  } catch (error) {
    $("#user-detail").replaceChildren(node("div", { className: "loading-state error-text", text: error.message }));
  }
}

function renderUserDetail() {
  const user = state.selectedUser;
  if (!user) return;
  const name = user.nickname || "未设置昵称";
  const header = node("div", { className: "user-profile-head" }, [
    user.avatarUrl
      ? node("img", { className: "profile-avatar", attributes: { src: user.avatarUrl, alt: "用户头像", referrerpolicy: "no-referrer" } })
      : node("span", { className: "profile-avatar fallback", text: name.slice(0, 1) }),
    node("div", {}, [node("h3", { text: name }), node("p", { text: `#${user.id} · ${user.status === 1 ? "正常" : "禁用"}` })]),
  ]);
  const profile = user.profile || {};
  const detail = user.profileDetails || {};
  const info = facts([
    ["手机号", user.phone || "—"], ["性别", genderLabel(user.gender)],
    ["生日", user.birthday || "—"], ["地区", [user.country, user.province, user.city].filter(Boolean).join(" ") || "—"],
    ["注册状态", user.onboardingStatus || "—"], ["注册时间", formatTime(user.createdAt)],
    ["曝光活跃状态", activityStatusLabel(user.activityStatus)],
    ["最后登录", formatTime(user.lastLoginTime)], ["最后活跃", formatTime(user.lastHeartbeatTime)],
    ["交友目的", detail.datingGoal || "—"], ["MBTI", detail.mbti || "—"],
    ["身高", detail.heightCm ? `${detail.heightCm} cm` : "—"], ["学历", educationLabel(detail.educationLevel)],
    ["学校", detail.school || "—"], ["职业", detail.job || "—"],
    ["简介", detail.bio || "—"], ["兴趣标签", formatInterests(detail.interests)],
    ["会员等级", profile.vipLevel || "NONE"], ["认证状态", profile.verificationStatus || "—"],
    ["获赞", profile.receivedLikeCount ?? 0], ["算力余额", profile.creditBalance ?? 0],
    ["审美共鸣", profile.aestheticResonanceCount ?? 0], ["曝光算力", profile.exposureCreditBalance ?? 0],
    ["进入人工审核", user.photoReviewCount ?? 0], ["照片被拦截", user.photoBlockedCount ?? 0],
    ["照片风险合计", Number(user.photoReviewCount || 0) + Number(user.photoBlockedCount || 0)],
    ["最近风险时间", formatTime(user.photoRiskUpdatedAt)],
  ]);
  const active = new Set((user.roles || []).map((role) => String(role.id)));
  const roleOptions = node("div", { className: "role-options" }, state.manageableRoles.map((role) => {
    const input = node("input", { type: "checkbox", attributes: { value: String(role.id) } });
    input.checked = active.has(String(role.id));
    return node("label", {}, [input, node("span", { text: role.name })]);
  }));
  const save = node("button", { className: "primary-button", type: "button", text: "保存角色" });
  const revoke = node("button", { className: "reject-button", type: "button", text: "强制下线" });
  const statusButton = node("button", { className: "secondary-button", type: "button",
    text: user.status === 1 ? "停用账号" : "恢复账号" });
  const activateButton = node("button", { className: "secondary-button", type: "button",
    text: user.activityStatus === "INACTIVE" ? "激活曝光" : "刷新曝光活跃" });
  const edit = node("button", { className: "secondary-button", type: "button", text: "编辑资料" });
  const upload = node("button", { className: "secondary-button", type: "button", text: "添加生活照" });
  const exportWeights = node("button", { className: "secondary-button", type: "button", text: "导出偏爱权重" });
  const fileInput = node("input", { className: "upload-photo-input", type: "file",
    attributes: { accept: "image/jpeg,image/png,image/webp,image/heic,image/heif", multiple: "multiple" } });
  const uploadProgress = node("div", { className: "upload-progress", attributes: { hidden: "hidden" } }, [
    node("div", { className: "upload-progress-track" }, [node("span", { className: "upload-progress-fill" })]),
    node("p", { text: "等待上传" }),
  ]);
  const ownId = state.identity?.userInfo?.id;
  if (String(ownId || "") === String(user.id)) {
    save.disabled = true;
    save.title = "不能修改自己的角色";
    revoke.disabled = true;
    statusButton.disabled = true;
  }
  save.addEventListener("click", () => saveUserRoles(user.id, roleOptions, save));
  revoke.addEventListener("click", () => revokeUserSessions(user.id, revoke));
  statusButton.addEventListener("click", () => updateUserStatus(user.id, user.status !== 1, statusButton));
  activateButton.addEventListener("click", () => activateUserDistribution(user.id, activateButton));
  edit.addEventListener("click", () => openUserForm(user));
  upload.addEventListener("click", () => fileInput.click());
  exportWeights.addEventListener("click", () => downloadPreferenceWeights(user.id, exportWeights));
  fileInput.addEventListener("change", () => prepareUserPhotos(user.id, fileInput, upload, uploadProgress));
  const roleSection = node("section", { className: "detail-section" }, [
    node("div", { className: "section-inline-title" }, [node("h4", { text: "后台角色与账号状态" }), node("div", { className: "detail-actions" }, [activateButton, statusButton, revoke, save])]), roleOptions,
  ]);
  const photos = node("section", { className: "detail-section" }, [
    node("div", { className: "section-inline-title" }, [node("h4", { text: `生活照 ${(user.lifestylePhotos || []).length}` }),
      node("div", { className: "detail-actions" }, [exportWeights, edit, upload, fileInput])]), uploadProgress,
    renderUserPhotoSearch(user.lifestylePhotos || []),
  ]);
  $("#user-detail").replaceChildren(node("div", { className: "detail-content user-detail-content" }, [header, info, roleSection, photos]));
}

function openUserForm(user = null) {
  state.editingUserId = user?.id || null;
  const form = $("#user-form");
  form.reset();
  $("#user-form-title").textContent = user ? "编辑用户" : "加入用户";
  $("#user-form-error").hidden = true;
  const detail = user?.profileDetails || {};
  const values = user ? {
    phone: user.phone, nickname: user.nickname, gender: user.gender, birthday: user.birthday,
    country: user.country, province: user.province, city: user.city,
    datingGoal: detail.datingGoal, mbti: detail.mbti, heightCm: detail.heightCm,
    school: detail.school, educationLevel: detail.educationLevel, job: detail.job, bio: detail.bio,
  } : {};
  for (const [name, value] of Object.entries(values)) {
    const input = form.elements.namedItem(name);
    if (input) input.value = value ?? "";
  }
  $("#user-form-dialog").showModal();
}

function closeUserForm() { $("#user-form-dialog").close(); }

async function submitUserForm(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $("#user-form-submit");
  const error = $("#user-form-error");
  const data = Object.fromEntries(new FormData(form));
  data.gender = Number(data.gender);
  data.heightCm = data.heightCm ? Number(data.heightCm) : null;
  data.mbti = String(data.mbti || "").toUpperCase();
  for (const key of ["country", "province", "city", "datingGoal", "mbti", "school", "educationLevel", "job", "bio"])
    data[key] = data[key] || null;
  button.disabled = true;
  error.hidden = true;
  try {
    const editingId = state.editingUserId;
    const saved = await api(editingId ? `/admin-api/v1/users/${encodeURIComponent(editingId)}` : "/admin-api/v1/users", {
      method: editingId ? "PUT" : "POST", body: data,
    });
    closeUserForm();
    showToast(editingId ? "用户资料已更新" : "用户已加入");
    await loadUsers(true);
    await selectUser(saved.id);
  } catch (caught) {
    error.textContent = caught.message || "保存失败";
    error.hidden = false;
  } finally { button.disabled = false; }
}

function prepareUserPhotos(userId, input, button, progress) {
  const files = Array.from(input.files || []);
  if (!files.length) return;
  progress.hidden = false;
  const sex = node("select", {}, [node("option", { text: "男", attributes: { value: "1" } }),
    node("option", { text: "女", attributes: { value: "2" } })]);
  const start = node("button", { className: "primary-button", type: "button", text: `开始上传 ${files.length} 张` });
  start.addEventListener("click", () => uploadUserPhotos(userId, input, button, progress, files, sex.value, start));
  progress.replaceChildren(
    node("label", { className: "batch-sex-field" }, [node("span", { text: "整批照片性别" }), sex]),
    node("p", { text: `${files.length} 张：${files.map((file) => file.name).join("、")}` }), start);
}

async function uploadUserPhotos(userId, input, button, progress, files, sex, start) {
  button.disabled = true;
  start.disabled = true;
  progress.replaceChildren(
    node("div", { className: "upload-progress-track" }, [node("span", { className: "upload-progress-fill" })]),
    node("p", { text: "准备上传" }));
  const fill = $(".upload-progress-fill", progress);
  const label = $("p", progress);
  let completed = 0;
  const failures = [];
  const queue = files.slice();
  const worker = async () => {
    while (queue.length) {
      const file = queue.shift();
      label.textContent = `正在分析 ${file.name} · ${completed}/${files.length}`;
      const body = new FormData();
      body.append("file", file, file.name);
      body.append("sex", sex);
      body.append("clientRequestId", `admin-${crypto.randomUUID()}`);
      try {
        const result = await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/lifestyle-photos`, { method: "POST", body });
        if (result?.clipAesthetic?.analysisStatus !== "DONE" || !result?.clipAesthetic?.aesthetic)
          throw new Error("分析结果不完整");
      } catch (error) { failures.push(`${file.name}：${error.message || "失败"}`); }
      completed += 1;
      fill.style.width = `${Math.round(completed * 100 / files.length)}%`;
      label.textContent = `已完成 ${completed}/${files.length}${failures.length ? ` · 失败 ${failures.length}` : ""}`;
    }
  };
  try {
    await Promise.all(Array.from({ length: Math.min(2, files.length) }, worker));
    if (failures.length) showToast(`批量上传未全部完成：${failures.join("；")}`, "error");
    else showToast(`${files.length} 张生活照均已完成上传与分析`);
    await selectUser(userId);
  } finally { input.value = ""; button.disabled = false; }
}

async function deleteUserPhoto(userId, photoId, button) {
  if (!window.confirm("确认删除这张生活照？照片会立即停止分发，原图按现有延迟删除策略清理。")) return;
  button.disabled = true;
  try {
    await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/lifestyle-photos/${encodeURIComponent(photoId)}`, {
      method: "DELETE",
    });
    showToast("生活照删除已受理");
    await selectUser(userId);
  } catch (error) {
    showToast(error.message || "删除失败", "error");
    button.disabled = false;
  }
}

function educationLabel(value) {
  return ({ HIGH_SCHOOL: "高中及以下", ASSOCIATE: "大专", BACHELOR: "本科", MASTER: "硕士", DOCTOR: "博士", OTHER: "其他" })[value] || "—";
}

function formatInterests(value) {
  if (!value) return "—";
  try { const items = JSON.parse(value); return Array.isArray(items) && items.length ? items.join("、") : "—"; }
  catch { return String(value); }
}

function renderUserPhotos(photos) {
  if (!photos.length) return node("p", { className: "muted", text: "暂无生活照" });
  return node("div", { className: "user-photo-grid" }, photos.map((photo) => {
    const frame = node("div", { className: "user-photo" });
    if (photo.url) frame.append(node("img", {
      attributes: { src: photo.url, alt: `生活照 ${photo.photoId}`, loading: "lazy", referrerpolicy: "no-referrer" },
    }));
    else frame.append(node("span", { text: photo.status || "不可预览" }));
    frame.append(node("span", { className: "user-photo-id", text: `#${photo.photoId}` }));
    const remove = node("button", { className: "user-photo-delete", type: "button", text: "删除",
      attributes: { "aria-label": `删除生活照 ${photo.photoId}` } });
    remove.addEventListener("click", () => deleteUserPhoto(state.selectedUser.id, photo.photoId, remove));
    frame.append(remove);
    return frame;
  }));
}

function renderUserPhotoSearch(photos) {
  const results = node("div");
  const input = node("input", { type: "search", attributes: {
    placeholder: "输入照片 ID 搜索", "aria-label": "按照片 ID 搜索生活照", inputmode: "numeric",
  } });
  const count = node("span", { className: "result-total", text: `${photos.length} 张` });
  const render = () => {
    const keyword = input.value.trim();
    const filtered = keyword ? photos.filter((photo) => String(photo.photoId).includes(keyword)) : photos;
    count.textContent = `${filtered.length} 张`;
    results.replaceChildren(renderUserPhotos(filtered));
  };
  input.addEventListener("input", render);
  render();
  return node("div", { className: "user-photo-search" }, [
    node("div", { className: "photo-search-bar" }, [input, count]), results,
  ]);
}

async function downloadPreferenceWeights(userId, button) {
  button.disabled = true;
  try {
    const response = await fetch(`/admin-api/v1/users/${encodeURIComponent(userId)}/preference-weights/export`, {
      headers: { Accept: "application/json", Authorization: `Bearer ${state.token}` },
      credentials: "omit", cache: "no-store",
    });
    const renewedToken = response.headers.get("X-New-Access-Token");
    if (renewedToken) setSessionToken(renewedToken);
    if (!response.ok) {
      const payload = await response.json().catch(() => null);
      if (response.status === 401) { clearSession(); showLogin("登录状态已失效，请重新验证身份。"); }
      throw new ApiError(payload?.msg || `下载失败（HTTP ${response.status}）`, response.status, payload?.traceId);
    }
    const url = URL.createObjectURL(await response.blob());
    const link = node("a", { attributes: { href: url, download: `preference-weights-${userId}.json` } });
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showToast("偏爱权重已下载");
  } catch (error) {
    showToast(error.message || "偏爱权重下载失败", "error");
  } finally { button.disabled = false; }
}

async function saveUserRoles(userId, container, button) {
  const roleIds = $$('input[type="checkbox"]:checked', container).map((input) => input.value);
  button.disabled = true;
  try {
    state.selectedUser = await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/roles`, {
      method: "PUT", body: { roleIds },
    });
    showToast("角色已更新");
    await loadUsers(true);
    await selectUser(userId);
  } catch (error) {
    showToast(error.message || "角色更新失败", "error");
  } finally {
    button.disabled = false;
  }
}

async function revokeUserSessions(userId, button) {
  if (!window.confirm("确认强制该用户退出所有已登录设备？")) return;
  button.disabled = true;
  try {
    await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/sessions/revoke`, { method: "POST" });
    showToast("用户会话已撤销");
  } catch (error) {
    showToast(error.message || "强制下线失败", "error");
  } finally { button.disabled = false; }
}

async function updateUserStatus(userId, enabled, button) {
  const action = enabled ? "恢复" : "停用";
  if (!window.confirm(`确认${action}该用户？${enabled ? "" : "停用后将同时撤销全部会话。"}`)) return;
  button.disabled = true;
  try {
    state.selectedUser = await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/status`, {
      method: "PUT", body: { enabled },
    });
    showToast(`用户已${action}`);
    renderUserDetail();
    await loadUsers(true);
    await selectUser(userId);
  } catch (error) {
    showToast(error.message || `${action}失败`, "error");
  } finally { button.disabled = false; }
}

async function activateUserDistribution(userId, button) {
  if (!window.confirm("确认刷新该账号的曝光活跃状态？激活后其合格照片可重新进入投票分发。")) return;
  button.disabled = true;
  try {
    state.selectedUser = await api(`/admin-api/v1/users/${encodeURIComponent(userId)}/activity/activate`, {
      method: "POST",
    });
    showToast("照片曝光已激活");
    renderUserDetail();
    await loadUsers(true);
    await selectUser(userId);
  } catch (error) {
    showToast(error.message || "激活曝光失败", "error");
  } finally { button.disabled = false; }
}

function activityStatusLabel(status) {
  return status === "ONLINE" ? "在线" : status === "ACTIVE" ? "活跃" : "沉睡";
}

function genderLabel(gender) {
  return gender === 1 ? "男" : gender === 2 ? "女" : "未设置";
}

async function loadPolicies() {
  if (!canManagePolicies()) return;
  $("#policy-list").replaceChildren(node("div", { className: "loading-state", text: "加载中…" }));
  try {
    const type = $("#policy-type").value;
    state.policyItems = await api(`/admin-api/v1/review-policies/reasons?type=${encodeURIComponent(type)}`);
    renderPolicies();
  } catch (error) {
    $("#policy-list").replaceChildren(node("div", { className: "loading-state error-text", text: error.message }));
  }
}

function renderPolicies() {
  const cards = state.policyItems.map((item) => {
    const label = node("input", { attributes: { value: item.label, maxlength: "128", "aria-label": "显示名称" } });
    const description = node("input", { attributes: { value: item.description || "", maxlength: "512", "aria-label": "说明" } });
    const enabled = node("input", { type: "checkbox" });
    enabled.checked = item.enabled;
    const requireComment = node("input", { type: "checkbox" });
    requireComment.checked = item.requireComment;
    const save = node("button", { className: "secondary-button", type: "button", text: "保存" });
    save.addEventListener("click", async () => {
      save.disabled = true;
      try {
        await api(`/admin-api/v1/review-policies/reasons/${encodeURIComponent(item.id)}`, {
          method: "PUT", body: { label: label.value.trim(), description: description.value.trim() || null,
            enabled: enabled.checked, requireComment: requireComment.checked,
            sortOrder: item.sortOrder, expectedVersion: item.version },
        });
        state.reviewReasons = { photo: [], interest: [] };
        showToast("配置已保存");
        await loadPolicies();
      } catch (error) {
        showToast(error.message || "保存失败", "error");
      } finally { save.disabled = false; }
    });
    return node("article", { className: "policy-card" }, [
      node("div", { className: "policy-code" }, [node("strong", { text: item.code }), save]),
      node("label", {}, [node("span", { text: "名称" }), label]),
      node("label", {}, [node("span", { text: "说明" }), description]),
      node("div", { className: "policy-switches" }, [
        node("label", {}, [enabled, node("span", { text: "启用" })]),
        node("label", {}, [requireComment, node("span", { text: "强制备注" })]),
      ]),
    ]);
  });
  $("#policy-list").replaceChildren(...cards);
}

async function prepareCatalog() {
  const runId = $("#catalog-run-id").value.trim();
  if (!/^\d+$/.test(runId) || runId === "0") return showToast("请输入有效运行 ID", "error");
  await runCatalogAction(`/admin-api/v1/interest-catalog/runs/${encodeURIComponent(runId)}/prepare`, "POST");
}

async function checkCatalogReadiness() {
  const version = $("#catalog-version").value.trim();
  if (!/^\d+$/.test(version) || version === "0") return showToast("请输入有效图谱版本", "error");
  await runCatalogAction(`/admin-api/v1/interest-catalog/graphs/${encodeURIComponent(version)}/readiness`, "GET");
}

async function runCatalogAction(path, method) {
  $("#catalog-result").textContent = "执行中…";
  try {
    const result = await api(path, { method });
    $("#catalog-result").textContent = JSON.stringify(result, null, 2);
  } catch (error) {
    $("#catalog-result").textContent = error.message || "操作失败";
  }
}

async function loadAppeals() {
  if (!canReviewAppeals()) return;
  $("#appeal-list").replaceChildren(node("div", { className: "loading-state", text: "加载中…" }));
  try {
    state.appealItems = await api(`/admin-api/v1/interest-appeals?status=${encodeURIComponent($("#appeal-status").value)}&offset=0&limit=100`);
    renderAppeals();
  } catch (error) {
    $("#appeal-list").replaceChildren(node("div", { className: "loading-state error-text", text: error.message }));
  }
}

function renderAppeals() {
  if (!state.appealItems.length) {
    $("#appeal-list").replaceChildren(node("div", { className: "loading-state", text: "当前没有申诉" }));
    return;
  }
  $("#appeal-list").replaceChildren(...state.appealItems.map((item) => {
    const children = [
      node("div", { className: "policy-code" }, [node("strong", { text: item.candidateId }), statusBadge(item.appealStatus)]),
      node("h3", { text: item.normalizedText || "未命名标签" }),
      node("p", { className: "muted", text: item.appealComment || "未填写申诉说明" }),
      facts([["原审核意见", item.originalReviewComment || "—"], ["更新时间", formatTime(item.updatedAt)]])
    ];
    if (item.appealStatus === "OPEN" && hasPermission("review:appeal:resolve")) {
      const accept = node("button", { className: "approve-button", type: "button", text: "接受申诉" });
      const reject = node("button", { className: "reject-button", type: "button", text: "驳回申诉" });
      accept.addEventListener("click", () => resolveAppeal(item, "ACCEPT"));
      reject.addEventListener("click", () => resolveAppeal(item, "REJECT"));
      children.push(node("div", { className: "detail-actions" }, [accept, reject]));
    }
    return node("article", { className: "policy-card" }, children);
  }));
}

async function resolveAppeal(item, decision) {
  const comment = window.prompt(decision === "ACCEPT" ? "填写接受申诉的复核意见" : "填写驳回申诉的复核意见");
  if (!comment?.trim()) return;
  try {
    await api(`/admin-api/v1/interest-appeals/${encodeURIComponent(item.candidateId)}/resolve`, {
      method: "POST", body: { decision, comment: comment.trim(), expectedVersion: item.version },
    });
    showToast("申诉已处理");
    await loadAppeals();
  } catch (error) { showToast(error.message || "申诉处理失败", "error"); }
}

function updateClock() {
  const now = new Date();
  $("#current-date").textContent = new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" }).format(now);
  $("#current-time").textContent = new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(now);
}

bootstrap();
