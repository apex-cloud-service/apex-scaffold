import re

files = [
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/OfficeAdminHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/office/OfficeApprovalHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/office/OfficeCalendarHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/office/OfficeDocumentHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/office/OfficeMessageHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/handler/office/OfficeOrganizationHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/off/handler/MessageHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/off/handler/MessageTemplateHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/off/handler/OrgSyncHandlerTest.java",
    "apex-office-service/src/test/java/com/apex/scaffold/officeservice/interfaces/util/RequestHandlerUtilTest.java",
]

SAME_LINE = re.compile(r'\.body\((.+)\)\.build\(\)')
STANDALONE = re.compile(r'^(\s*)\.build\(\)(.*)$')

for path in files:
    with open(path) as f:
        lines = f.read().split("\n")
    out = []
    changed = 0
    for line in lines:
        if ".body(" in line:
            newline = SAME_LINE.sub(r'.body(\1)', line)
            if newline != line:
                line = newline
                changed += 1
        out.append(line)

    final = []
    for line in out:
        m = STANDALONE.match(line)
        if m:
            prev = None
            for j in range(len(final) - 1, -1, -1):
                if final[j].strip():
                    prev = final[j]
                    break
            if prev is not None and ".body(" in prev:
                final.append(m.group(1) + m.group(2))
                changed += 1
                continue
        final.append(line)

    with open(path, "w") as f:
        f.write("\n".join(final))
    print(f"{path.split('/')[-1]}: {changed} replacements")
