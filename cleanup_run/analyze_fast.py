import os, json, subprocess, sys, time, multiprocessing

HOME = os.path.expanduser("~")
WORK = "/Users/work/apex-scaffold/cleanup_run"
os.makedirs(WORK, exist_ok=True)

def sh(cmd, timeout=60):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return r.stdout, r.stderr, r.returncode

# ========== 1. Installed apps ==========
print("[1/5] Scanning installed apps...")
stdout, _, _ = sh(r"""
for d in /Applications ~/Applications /System/Applications /System/Applications/Utilities; do
  [ -d "$d" ] || continue
  for app in "$d"/*.app; do
    [ -d "$app" ] || continue
    bid=$(defaults read "$app/Contents/Info" CFBundleIdentifier 2>/dev/null || echo "")
    name=$(defaults read "$app/Contents/Info" CFBundleDisplayName 2>/dev/null || basename "$app" .app)
    echo "$bid|$name"
  done
done
""")
bundles = set()
names = set()
for line in stdout.strip().splitlines():
    parts = line.split("|", 1)
    if len(parts) == 2:
        bid, name = parts
        if bid:
            bundles.add(bid.lower())
        names.add(name.lower())
print(f"   {len(bundles)} bundle IDs, {len(names)} app names")

# ========== 2. Brew packages ==========
print("[2/5] Brew packages...")
stdout, _, _ = sh("brew list --formula 2>/dev/null; brew list --cask 2>/dev/null")
brew = set(l.strip().lower() for l in stdout.splitlines() if l.strip())
print(f"   {len(brew)} brew packages")

# ========== 3. Scan Library dirs (name-filter first, size later) ==========
LIB_DIRS = [
    ("Library/Application Support", "AppSupport"),
    ("Library/Containers", "Containers"),
    ("Library/Caches", "Caches"),
    ("Library/HTTPStorages", "HTTPStorages"),
    ("Library/Logs", "Logs"),
]

# Exclusion patterns (lowercase contains match)
SKIP_PREFIXES = [
    "com.apple", "apple.", "system", "org.openbsd", "com.googlecode",
    "com.microsoft", "org.chromium", "org.mozilla",
    "com.jetbrains", "com.github", "io.github", "com.electron",
    "com.sublimetext", "com.raycast", "us.zoom", "com.tencent", "com.alibaba",
    "net.kovidgoyal", "com.sequelpro", "com.tableplus", "com.postmanlabs",
    "com.agilebits", "com.kagi", "com.1password", "com.docker",
    "com.vmware", "com.parallels", "com.canonical", "com.obsidian",
    "com.vscodium", "com.openai", "com.anthropic", "com.cursor",
    "com.trae", "sh.trae", "ai.anthropic", "com.claude", "com.deepseek",
    "com.qwen", "com.kimi", "com.doubao", "com.baidu.netdisk",
    "com.tencent.qq", "com.tencent.wechat", "com.tencent.meeting",
    "com.microsoft.onedrive", "com.microsoft.office", "com.microsoft.edgemac",
    "com.google.chrome", "com.apple.dt", "com.apple.iwork", "com.apple.ilife",
    "com.jamfsoftware", "com.paddle", "com.setapp", "com.macpaw",
]
SKIP_NAMES = [
    "adobe", "bravesoftware", "chromium", "chrome", "firefox", "safari",
    "microsoft", "onedrive", "eolink", "wd discovery", "omni", "mysql",
    "autodesk", "ultracompare", "notion", "apifox", "slack", "discord",
    "telegram", "wechat", "qq", "dingtalk", "feishu", "lark", "copilot",
    "github", "cursor", "vscode", "trae", "claude", "ollama", "orbstack",
    "docker", "podman", "parallels", "vmware", "xcode", "jetbrains", "zsh",
    "oh-my-zsh", "homebrew", "config", "cache", "logs", "preferences",
    "containers", "webkit", "google", "mozilla", "zoom", "dropbox", "icloud",
    "google drive", "box", "saved application state", "group containers",
    "mobilesync", "mobile documents", "mobile applications",
    "application scripts", "assistants", "speech", "cefnetwork", "cef",
    "zoxide", "lazygit", "lazydocker", "superfile", "fzf", "fd", "ripgrep",
    "bat", "eza", "claude-code", "gh", "git", "node", "npm", "pnpm", "bun",
    "yarn", "go", "rust", "cargo", "python", "pyenv", "rbenv", "nvm", "fnm",
    "firestore", "caches", "spotlight", "syncservices", "addressbook",
    "calendars", "keychains", "mail", "messages", "notes", "reminders",
    "photos", "facetime", "findmy", "health", "wallet", "music", "tv",
    "podcasts", "books", "news", "stocks", "home", "shortcuts", "translate",
    "weather", "maps", "compass", "measure", "voice memos", "contacts",
    "freeform", "clock", "calculator", "tips", "feedback", "support",
    "diagnostics", "migration", "bootcamp", "razer", "logi", "logitech",
    "lenovo", "dell", "hp", "asus", "acer", "msi", "corsair", "steelseries",
    "razer synapse", "karabiner", "hammerspoon", "alt-tab", "rectangle",
    "magnet", "bettertouchtool", "better snap tool", "yoink", "paste",
    "maccy", "clipy", "clipmenu", "flycut", "copyclip",
]

def should_keep(name):
    low = name.lower()
    for p in SKIP_PREFIXES:
        if low.startswith(p):
            return True
    for n in SKIP_NAMES:
        if n in low:
            return True
    # Active recently (< 14 days)
    return False

def name_matches_installed(name, bundles, names, brew):
    low = name.lower()
    for n in names:
        if low in n or n in low:
            return True
    for b in bundles:
        if low in b or b in low:
            return True
    for bp in brew:
        bp_clean = bp.replace("-", "").replace("_", "")
        low_clean = low.replace("-", "").replace("_", "")
        if bp_clean in low_clean or low_clean in bp_clean:
            return True
    return False

print("[3/5] Scanning Library directories...")
all_candidates = []
for rel, label in LIB_DIRS:
    base = os.path.join(HOME, rel)
    if not os.path.isdir(base):
        continue
    for entry in os.listdir(base):
        p = os.path.join(base, entry)
        if os.path.islink(p):
            continue
        if should_keep(entry):
            continue
        if name_matches_installed(entry, bundles, names, brew):
            continue
        all_candidates.append((rel, entry, p, label))
print(f"   {len(all_candidates)} Library candidates after name filter")

# Batch size all at once for du
if all_candidates:
    print("[4/5] Computing sizes...")
    paths = [c[2] for c in all_candidates]
    # write to file and use xargs for speed
    with open(os.path.join(WORK, "_du_paths.txt"), "w") as f:
        for p in paths:
            f.write(p + "\n")
    stdout, _, rc = sh("cat /Users/work/apex-scaffold/cleanup_run/_du_paths.txt | xargs -I{} du -sk '{}' 2>/dev/null | awk '{print $1, $2}'", timeout=120)
    size_map = {}
    for line in stdout.strip().splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2:
            try:
                size_map[parts[1]] = int(parts[0]) * 1024
            except ValueError:
                pass
else:
    size_map = {}

lib_orphans = []
for rel, entry, p, label in all_candidates:
    st = os.lstat(p)
    age_days = (time.time() - st.st_mtime) / 86400
    if age_days < 14:
        continue
    size = size_map.get(p, 0)
    lib_orphans.append({
        "path": os.path.join(rel, entry),
        "name": entry,
        "size": size,
        "mtime": st.st_mtime,
        "label": label
    })

# ========== 4. Home root junk ==========
print("[5/5] Scanning home root...")
HOME_JUNK_PATTERNS = [
    ".bak", ".backup", ".pysave", ".pre-oh-my-zsh", ".swp",
    "launchpad_backup", "aom-", "brew-", "cask-fix", "dsh.log",
    "wechat-reinstall", "jmeter.log", "sbtest_nohup", "nohup.out",
    ".dsh-web", ".zshrc.bak", ".zshrc.backup", ".zshrcbak", ".zprofile.bak",
    ".zprofile.pysave", ".trae-cn-settings.backup", ".brew_install.swp",
    ".zcompdump", ".zcompdump-",
]
ESSENTIAL = {"Desktop", "Documents", "Downloads", "Library", "Movies", "Music", "Pictures", "Public", "Sites",
             "Virtual Machines.localized", "OrbStack", "Parallels", "iCloud Drive (Archive)", "go", "workspace", "work", "code", "projects",
             "cleanup_backup_20261012", "cleanup_analysis", "tmp", "temp", ".Trash"}

home_candidates = []
for entry in os.listdir(HOME):
    if entry in ESSENTIAL:
        continue
    p = os.path.join(HOME, entry)
    if os.path.islink(p):
        continue
    st = os.lstat(p)
    age_days = (time.time() - st.st_mtime) / 86400
    if age_days < 7:
        continue
    low = entry.lower()
    is_junk = False
    for pat in HOME_JUNK_PATTERNS:
        if pat in low or low.startswith(pat):
            is_junk = True
            break
    if not is_junk and not entry.startswith(".") and os.path.isdir(p) and age_days > 365:
        is_junk = True
    if not is_junk:
        continue
    if name_matches_installed(entry, bundles, names, brew):
        continue
    home_candidates.append(p)

# Batch du for home
home_orphans = []
if home_candidates:
    with open(os.path.join(WORK, "_du_home.txt"), "w") as f:
        for p in home_candidates:
            f.write(p + "\n")
    stdout, _, _ = sh("cat /Users/work/apex-scaffold/cleanup_run/_du_home.txt | xargs -I{} du -sk '{}' 2>/dev/null | awk '{print $1, $2}'", timeout=60)
    size_map_home = {}
    for line in stdout.strip().splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2:
            try:
                size_map_home[parts[1]] = int(parts[0]) * 1024
            except ValueError:
                pass
    for p in home_candidates:
        entry = os.path.basename(p)
        st = os.lstat(p)
        home_orphans.append({
            "path": entry,
            "name": entry,
            "size": size_map_home.get(p, 0),
            "mtime": st.st_mtime,
            "label": "home_junk"
        })

# ========== 5. Save results ==========
lib_orphans.sort(key=lambda x: x["size"], reverse=True)
home_orphans.sort(key=lambda x: x["size"], reverse=True)

with open(os.path.join(WORK, "orphans.json"), "w") as f:
    json.dump({"library": lib_orphans, "home": home_orphans}, f, indent=2, ensure_ascii=False)

with open(os.path.join(WORK, "to_backup.txt"), "w") as f:
    for e in lib_orphans:
        f.write(e["path"] + "\n")
    for e in home_orphans:
        f.write(e["path"] + "\n")

lib_size = sum(e["size"] for e in lib_orphans)
home_size = sum(e["size"] for e in home_orphans)
print(f"\n=== RESULTS ===")
print(f"Library orphans: {len(lib_orphans)} items, {lib_size/1024/1024:.1f} MB")
print(f"Home junk:       {len(home_orphans)} items, {home_size/1024/1024:.1f} MB")
print(f"Total:           {len(lib_orphans)+len(home_orphans)} items, {(lib_size+home_size)/1024/1024/1024:.2f} GB")
print(f"Top 10 Library:")
for e in lib_orphans[:10]:
    mb = e["size"]/1024/1024
    print(f"  {e['path']:60s} {mb:8.1f} MB")
print(f"Top 10 Home:")
for e in home_orphans[:10]:
    mb = e["size"]/1024/1024
    print(f"  {e['path']:60s} {mb:8.1f} MB")
