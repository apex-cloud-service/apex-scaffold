import json, os
WORK = "/Users/work/apex-scaffold/cleanup_run"
d = json.load(open(os.path.join(WORK, "orphans.json")))

# 保留的系统/框架关键词
SYSTEM_KEEP = [
    "cloudkit", "fileprovider", "videosubscriptionsd", "familycircled", "askpermissiond",
    "differentialprivacy", "animoji", "quick look", "accountsd", "controlcenter",
    "coreparsec", "xplan", "vfkit", "dmd", "appplaceholdersyncd", "kotlin",
    "ilifemediabrowser", "privatecloudcomputed", "revenuecat", "designer",
    "askpermission", "io.sentry", "transparencyd", "themes", "syncdefaultsd",
    "symbolsourcesymbols", "policy", "eltima software", "diskimages", "plug-ins",
    "memory meter 3", ".gmtemporary", ".gbda", ".mibprofile", ".f1d4f3959d97e06ec2090",
    ".com.felttip.1999", "librarylocationv4url", "libraryrecentdocumentitems",
    "heartbeats", ".accc_lock", ".adcs_lock", ".cch_nglw_lock", ".cch_upi_lock",
    "runtime-info-darwin-x64", ".binarycookies", "updater_new", "users",
]

# 已安装应用保留
INSTALLED_KEEP = [
    "微信web开发者工具", "ecosiabrowser", "premiumsoft cybertech", "knowledge",
    "cs odessa", "com.fiplab.memoryclean3", "dsh-desktop", "memory-cleaner-siii",
    "macclean.app", "maccleaner-pro-siii", "app-cleaner-siii", "duplicate-file-finder-siii",
    "funter-siii", "nektony", "bitz_net", "fefileexplorer", "mweblibrary",
    "wondershare.updatehelper", "com.wondershare", "com.wdc.wddesktop",
    "grammarly", "com.oray.remote", "fn2v63ad2j.com.tencent.screencapture3",
    "com.brohawn.activitymonitor", "feishu", "lark", "realdoc",
    "deepseek-harness-desktop", "com.ggt.vm", "shark-mac-client",
    "io.coressh.askpass", "com.baiqian.happyread",
]

lib_delete = []
lib_keep = []
for e in d["library"]:
    low = e["name"].lower()
    reason = ""
    for sk in SYSTEM_KEEP:
        if sk in low:
            reason = "系统/框架"
            break
    for ik in INSTALLED_KEEP:
        if ik in low:
            reason = "已安装应用相关"
            break
    if reason:
        lib_keep.append({**e, "reason": reason})
    else:
        lib_delete.append(e)

# Home: 只保留绝对安全的
home_delete = []
home_keep = []
for e in d["home"]:
    low = e["name"].lower()
    if low.startswith(".oh-my-zsh.backup") or low == "dbeaver-agent":
        home_delete.append(e)
    else:
        home_keep.append({**e, "reason": "保守保留"})

# Save
with open(os.path.join(WORK, "to_delete.txt"), "w") as f:
    for e in lib_delete:
        f.write(e["path"] + "\n")
    for e in home_delete:
        f.write(e["path"] + "\n")

total_del = len(lib_delete) + len(home_delete)
del_size = sum(e["size"] for e in lib_delete) + sum(e["size"] for e in home_delete)
print(f"=== 最终删除清单 ===")
print(f"Library 删除: {len(lib_delete)} 项")
print(f"Home 删除:    {len(home_delete)} 项")
print(f"总计:         {total_del} 项, {del_size/1024/1024:.1f} MB")
print(f"\n保留项: {len(lib_keep)+len(home_keep)} 项")
print("\n--- 保留项明细 ---")
for e in lib_keep[:20]:
    print(f"  [KEEP] {e['path']:70s} ({e['reason']})")
for e in home_keep:
    print(f"  [KEEP] {e['path']:70s} ({e['reason']})")
print("\n--- 删除项明细 ---")
for e in lib_delete:
    mb = e['size']/1024/1024
    print(f"  [DEL]  {e['path']:70s} {mb:8.1f} MB")
for e in home_delete:
    mb = e['size']/1024/1024
    print(f"  [DEL]  {e['path']:70s} {mb:8.1f} MB")

# JSON
with open(os.path.join(WORK, "final.json"), "w") as f:
    json.dump({"delete": lib_delete + home_delete, "keep": lib_keep + home_keep}, f, indent=2, ensure_ascii=False)
