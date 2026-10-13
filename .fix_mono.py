import os

BASE = "/Users/work/apex-scaffold/apex-office-service/src/test/java/com/apex/scaffold/officeservice"

FILES = [
    "interfaces/handler/OfficeAdminHandlerTest.java",
    "interfaces/handler/office/OfficeApprovalHandlerTest.java",
    "interfaces/handler/office/OfficeCalendarHandlerTest.java",
    "interfaces/handler/office/OfficeDocumentHandlerTest.java",
    "interfaces/handler/office/OfficeMessageHandlerTest.java",
    "interfaces/handler/office/OfficeOrganizationHandlerTest.java",
    "interfaces/off/handler/MessageHandlerTest.java",
    "interfaces/off/handler/MessageTemplateHandlerTest.java",
    "interfaces/off/handler/OrgSyncHandlerTest.java",
    "interfaces/util/RequestHandlerUtilTest.java",
]

NEEDLE = ".body("


def extract_arg(content, start):
    depth = 1
    j = start
    n = len(content)
    in_str = False
    while j < n and depth > 0:
        c = content[j]
        if in_str:
            if c == "\\":
                j += 2
                continue
            if c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
        j += 1
    return content[start:j - 1], j


def wrap(content):
    out = []
    i = 0
    n = len(content)
    count = 0
    while True:
        idx = content.find(NEEDLE, i)
        if idx == -1:
            out.append(content[i:])
            break
        out.append(content[i:idx])
        arg, j = extract_arg(content, idx + len(NEEDLE))
        if arg.strip().startswith("Mono"):
            out.append(".body(" + arg + ")")
        else:
            out.append(".body(Mono.just(" + arg + "))")
            count += 1
        i = j
    return "".join(out), count


def ensure_import(content):
    if "import reactor.core.publisher.Mono;" in content:
        return content, False
    lines = content.split("\n")
    last_import = -1
    for k, ln in enumerate(lines):
        if ln.startswith("import "):
            last_import = k
    if last_import == -1:
        raise RuntimeError("no imports found")
    lines.insert(last_import + 1, "import reactor.core.publisher.Mono;")
    return "\n".join(lines), True


total = 0
for rel in FILES:
    path = os.path.join(BASE, rel)
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    new_content, count = wrap(content)
    new_content, added = ensure_import(new_content)
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
    total += count
    print(f"{rel}: wrapped={count} importAdded={added}")

print("TOTAL wrapped:", total)
