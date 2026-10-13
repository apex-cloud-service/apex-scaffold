import os, json, subprocess, sys, time

HOME = os.path.expanduser("~")
WORK = "/Users/work/apex-scaffold/cleanup_run"
os.makedirs(WORK, exist_ok=True)

# ============ 1. Gather installed apps ============
def get_installed_apps():
    bundles = set()
    names = set()
    app_paths = []
    for base in ["/Applications", os.path.join(HOME, "Applications"), "/System/Applications", "/System/Applications/Utilities"]:
        if not os.path.isdir(base):
            continue
        for entry in os.listdir(base):
            p = os.path.join(base, entry)
            if not entry.endswith(".app"):
                continue
            app_paths.append(p)
            # try bundle id
            try:
                r = subprocess.run(["defaults", "read", os.path.join(p, "Contents", "Info"), "CFBundleIdentifier"],
                                   capture_output=True, text=True, timeout=5)
                if r.returncode == 0:
                    bid = r.stdout.strip()
                    if bid:
                        bundles.add(bid.lower())
            except Exception:
                pass
            # display name
            try:
                r = subprocess.run(["defaults", "read", os.path.join(p, "Contents", "Info"), "CFBundleDisplayName"],
                                   capture_output=True, text=True, timeout=5)
                if r.returncode == 0:
                    dn = r.stdout.strip()
                    if dn:
                        names.add(dn.lower())
            except Exception:
                pass
            # fallback to app name without .app
            names.add(entry[:-4].lower())
    return bundles, names, app_paths

# ============ 2. Gather brew packages ============
def get_brew_packages():
    pkgs = set()
    for cmd in [["brew", "list", "--formula"], ["brew", "list", "--cask"]]:
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if r.returncode == 0:
                for line in r.stdout.strip().splitlines():
                    line = line.strip()
                    if line:
                        pkgs.add(line.lower())
        except Exception:
            pass
    return pkgs

# ============ 3. Scan Library directories ============
DIRS_TO_SCAN = [
    ("Library/Application Support", "AppSupport"),
    ("Library/Containers", "Containers"),
    ("Library/Caches", "Caches"),
    ("Library/HTTPStorages", "HTTPStorages"),
    ("Library/Logs", "Logs"),
    ("Library/Preferences", "Preferences"),
]

def scan_library_dir(rel_path, label):
    base = os.path.join(HOME, rel_path)
    results = []
    if not os.path.isdir(base):
        return results
    for entry in os.listdir(base):
        p = os.path.join(base, entry)
        # skip symlinks pointing outside home
        if os.path.islink(p):
            rp = os.path.realpath(p)
            if not rp.startswith(HOME):
                continue
        st = os.lstat(p)
        size = 0
        try:
            size = int(subprocess.run(["du", "-sk", p], capture_output=True, text=True, timeout=5).stdout.split()[0]) * 1024
        except Exception:
            pass
        results.append({
            "path": os.path.join(rel_path, entry),
            "abs": p,
            "name": entry,
            "mtime": st.st_mtime,
            "size": size,
            "label": label
        })
    return results

# ============ 4. Home root junk scan ============
HOME_JUNK_PATTERNS = [
    ".bak", ".backup", ".bak.", ".pysave", ".pre-oh-my-zsh", ".swp",
    "launchpad_backup", "aom-", "brew-", "cask-fix", "dsh.log",
    "wechat-reinstall", "jmeter.log", "sbtest_nohup", "nohup.out",
    ".dsh-web", ".zshrc.bak", ".zshrc.backup", ".zshrcbak", ".zprofile.bak",
    ".zprofile.pysave", ".trae-cn-settings.backup", ".brew_install.swp",
    ".zcompdump", ".zcompdump-",
]

def scan_home_junk():
    results = []
    for entry in os.listdir(HOME):
        p = os.path.join(HOME, entry)
        if entry.startswith(".") and entry == ".Trash":
            continue
        # dotfiles are generally config, but some are junk backups
        is_junk = False
        low = entry.lower()
        for pat in HOME_JUNK_PATTERNS:
            if pat in low or low.startswith(pat):
                is_junk = True
                break
        # also detect orphaned project dirs at root
        if not entry.startswith(".") and os.path.isdir(p):
            # heuristic: very old mtime (> 1 year) and not in common whitelist
            st = os.lstat(p)
            age_days = (time.time() - st.st_mtime) / 86400
            if age_days > 365:
                is_junk = True
        # skip known essential directories
        essential = {"Desktop", "Documents", "Downloads", "Library", "Movies", "Music", "Pictures", "Public", "Sites", 
                     "Virtual Machines.localized", "OrbStack", "Parallels", "iCloud Drive (Archive)", "go", "workspace", "work", "code", "projects",
                     "cleanup_backup_20261012", "cleanup_analysis"}
        if entry in essential:
            continue
        # skip if mtime within last 7 days (actively used)
        st = os.lstat(p)
        age_days = (time.time() - st.st_mtime) / 86400
        if age_days < 7:
            continue
        if is_junk or (not entry.startswith(".") and age_days > 180 and os.path.isdir(p)):
            size = 0
            try:
                size = int(subprocess.run(["du", "-sk", p], capture_output=True, text=True, timeout=5).stdout.split()[0]) * 1024
            except Exception:
                pass
            results.append({
                "path": entry,
                "abs": p,
                "name": entry,
                "mtime": st.st_mtime,
                "size": size,
                "label": "home_junk"
            })
    return results

# ============ 5. Filter orphans ============
# Known safe system/vendor prefixes (conservative)
SYSTEM_PREFIXES = {
    "com.apple", "apple", "system", "org.openbsd", "com.googlecode",
    "com.microsoft", "com.google", "org.chromium", "org.mozilla",
    "com.jetbrains", "com.github", "io.github", "com.electron",
    "com.sublimetext", "com.microsoft.vscode", "com.raycast",
    "us.zoom", "com.tencent", "com.alibaba", "com.baidu",
    "net.kovidgoyal", "com.sequelpro", "com.tableplus", "com.postmanlabs",
    "com.agilebits", "com.kagi", "com.1password", "com.docker",
    "com.vmware", "com.parallels", "com.canonical", "com.obsidian",
    "com.vscodium", "com.openai", "com.anthropic", "com.cursor",
    "com.trae", "sh.trae", "ai.anthropic", "com.claude", "com.deepseek",
    "com.qwen", "com.kimi", "com.doubao", "com.baidu.netdisk",
    "com.tencent.qq", "com.tencent.wechat", "com.tencent.meeting",
    "com.microsoft.onedrive", "com.microsoft.office", "com.microsoft.edgemac",
    "com.google.chrome", "com.google.Chrome", "org.mozilla.firefox",
    "com.apple.dt", "com.apple.iwork", "com.apple.iLife", "com.apple.mail",
    "com.jamfsoftware", "com.paddle", "com.setapp", "com.macpaw",
    "com.ankiapp", "com.logi", "com.logitech",
}

# Specific known safe / active directories to keep
KEEP_NAMES = {
    "adobe", "adobe acrobat dc", "adobe audition 2021", "bravesoftware",
    "cefnetwork", "cef", "chromium", "chrome", "firefox", "safari",
    "microsoft", "onedrive", "teams", "eolink", "wd discovery",
    "the omni group", "omni group", "omnigroup",
    "mysql", "mysqlworkbench", "navicat", "tableplus", "sequel pro", "sequel ace",
    "autodesk", "ultracomparex", "ultracompare",
    "aocstudent", "notion", "apifox", "eolink", "brave",
    "slack", "discord", "telegram", "wechat", "qq", "dingtalk", "feishu", "lark",
    "copilot", "github", "cursor", "vscode", "trae", "claude", "windsurf",
    "ollama", "orbstack", "docker", "podman", "parallels", "vmware",
    "xcode", "jetbrains", "idea", "pycharm", "goland", "webstorm", "rubymine",
    "zsh", "oh-my-zsh", "bash", "fish", "nvm", "fnm", "pyenv", "rbenv", "sdkman",
    "go", "golang", "rust", "cargo", "node", "npm", "pnpm", "yarn", "bun",
    "homebrew", "brew", "mas", "port",
    "config", "cache", "logs", "preferences", "containers", "saved application state",
    "group containers", "webkit", "google", "mozilla",
    "zoom", "zoom.us", "tencent meeting", "tencentdocs",
    "dropbox", "icloud", "google drive", "box", "onedrive",
    # brew installed tools (from last time + current)
    "zoxide", "lazygit", "lazydocker", "superfile", "fzf", "fd", "ripgrep", "bat", "eza", "dust",
    "claude-code", "copilot-cli", "gh", "git",
}

def is_orphan(entry, bundles, names, brew_pkgs):
    low = entry["name"].lower()
    # system prefixes
    for pfx in SYSTEM_PREFIXES:
        if low.startswith(pfx):
            return False
    # keep names
    for kn in KEEP_NAMES:
        if kn in low or low == kn:
            return False
    # match against bundle ids (partial)
    for bid in bundles:
        if low in bid or bid in low:
            return False
    # match against app display names
    for n in names:
        if low in n or n in low:
            return False
    # match against brew packages
    for bp in brew_pkgs:
        if low.replace("-", "").replace("_", "") in bp.replace("-", "").replace("_", ""):
            return False
        if bp.replace("-", "").replace("_", "") in low.replace("-", "").replace("_", ""):
            return False
    # active recently (last 14 days)
    age_days = (time.time() - entry["mtime"]) / 86400
    if age_days < 14:
        return False
    return True

# ============ Run analysis ============
print("[1/4] Gathering installed apps...", flush=True)
bundles, names, app_paths = get_installed_apps()
print(f"   Apps: {len(app_paths)} paths, {len(bundles)} bundle IDs, {len(names)} names")

print("[2/4] Gathering brew packages...", flush=True)
brew_pkgs = get_brew_packages()
print(f"   Brew: {len(brew_pkgs)} packages")

print("[3/4] Scanning Library directories...", flush=True)
all_lib = []
for rel, label in DIRS_TO_SCAN:
    items = scan_library_dir(rel, label)
    all_lib.extend(items)
print(f"   Library entries: {len(all_lib)}")

print("[4/4] Scanning home root...", flush=True)
home_junk = scan_home_junk()
print(f"   Home junk candidates: {len(home_junk)}")

# Filter
print("Filtering orphans...", flush=True)
lib_orphans = [e for e in all_lib if is_orphan(e, bundles, names, brew_pkgs)]
# Additional exclusions for Library items
EXCLUDE_PATTERNS = [
    "com.apple", "apple.", "system", "org.openbsd", "com.googlecode",
    "com.microsoft", "com.google.", "org.chromium", "org.mozilla",
    "com.jetbrains", "com.github", "io.github", "com.electron",
    "com.sublimetext", "com.microsoft.vscode", "com.raycast",
    "us.zoom", "com.tencent", "com.alibaba", "com.baidu",
    "net.kovidgoyal", "com.sequelpro", "com.tableplus", "com.postmanlabs",
    "com.agilebits", "com.kagi", "com.1password", "com.docker",
    "com.vmware", "com.parallels", "com.canonical", "com.obsidian",
    "com.vscodium", "com.openai", "com.anthropic", "com.cursor",
    "com.trae", "sh.trae", "ai.anthropic", "com.claude", "com.deepseek",
    "com.qwen", "com.kimi", "com.doubao", "com.baidu.netdisk",
    "com.tencent.qq", "com.tencent.wechat", "com.tencent.meeting",
    "com.microsoft.onedrive", "com.microsoft.office", "com.microsoft.edgemac",
    "com.google.chrome", "com.google.Chrome", "org.mozilla.firefox",
    "com.apple.dt", "com.apple.iwork", "com.apple.iLife", "com.apple.mail",
    "com.jamfsoftware", "com.paddle", "com.setapp", "com.macpaw",
    "adobe", "bravesoftware", "chromium", "chrome", "firefox", "safari",
    "microsoft", "onedrive", "eolink", "wd discovery", "omni", "mysql",
    "autodesk", "ultracompare", "notion", "apifox", "slack", "discord",
    "telegram", "wechat", "qq", "dingtalk", "feishu", "lark", "copilot",
    "github", "cursor", "vscode", "trae", "claude", "ollama", "orbstack",
    "docker", "podman", "parallels", "vmware", "xcode", "jetbrains", "zsh",
    "oh-my-zsh", "homebrew", "brew", "config", "cache", "logs", "preferences",
    "containers", "webkit", "google", "mozilla", "zoom", "dropbox", "icloud",
    # Active dev tools
    "zoxide", "lazygit", "lazydocker", "superfile", "fzf", "fd", "ripgrep", "bat", "eza",
    "claude-code", "gh", "git", "node", "npm", "pnpm", "bun", "yarn",
    "go", "rust", "cargo", "python", "pyenv", "rbenv", "nvm", "fnm",
    # Common shared frameworks
    "cef", "cefnetwork", "chromium framework", "electron",
    # Safelist exact names that are shared / ambiguous
    "group containers", "saved application state", "webkit",
    "mobilesync", "mobile documents", "mobile applications",
    "application scripts", "assistants", "speech",
    # Specific known safe folders from last run
    "firestore", "caches",  # Caches dir contains updater caches for installed apps
]

# Apply strict filter
final_lib_orphans = []
for e in lib_orphans:
    low = e["name"].lower()
    skip = False
    for pat in EXCLUDE_PATTERNS:
        if pat in low:
            skip = True
            break
    if not skip:
        final_lib_orphans.append(e)

# For home junk, also cross-check against installed apps
final_home = []
for e in home_junk:
    low = e["name"].lower()
    # skip if matches any installed app
    skip = False
    for n in names:
        if low in n or n in low:
            skip = True
            break
    for bp in brew_pkgs:
        if bp in low or low in bp:
            skip = True
            break
    if not skip:
        final_home.append(e)

# Write outputs
with open(os.path.join(WORK, "orphans.json"), "w") as f:
    json.dump({
        "library": final_lib_orphans,
        "home": final_home,
        "stats": {
            "apps": len(app_paths),
            "bundles": len(bundles),
            "names": len(names),
            "brew_pkgs": len(brew_pkgs),
            "lib_total": len(all_lib),
            "lib_orphans": len(final_lib_orphans),
            "home_candidates": len(final_home),
        }
    }, f, indent=2, ensure_ascii=False)

# Summary
lib_size = sum(e["size"] for e in final_lib_orphans)
home_size = sum(e["size"] for e in final_home)
print(f"\n=== RESULTS ===")
print(f"Library orphans: {len(final_lib_orphans)} items, {lib_size/1024/1024:.1f} MB")
print(f"Home junk:       {len(final_home)} items, {home_size/1024/1024:.1f} MB")
print(f"Total:           {len(final_lib_orphans)+len(final_home)} items, {(lib_size+home_size)/1024/1024:.1f} MB")

# Write simple text list for backup
with open(os.path.join(WORK, "to_backup.txt"), "w") as f:
    for e in final_lib_orphans:
        f.write(e["path"] + "\n")
    for e in final_home:
        f.write(e["path"] + "\n")

