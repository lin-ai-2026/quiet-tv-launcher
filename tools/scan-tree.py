#!/usr/bin/env python3
"""Scan the git tree for files that must not be published. Not a full security audit."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCKED_EXT = {
    ".jks", ".keystore", ".pem", ".pk8", ".der", ".apk", ".aab", ".idsig",
    ".otf", ".ttf", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".zip",
}
ALLOWED_PNG = {
    "app/res/mipmap-xhdpi/ic_launcher.png",
    "app/res/drawable-xhdpi/banner.png",
}
TEXT_RULES = [
    ("private-key", re.compile(r"-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----")),
    ("placeholder-secret", re.compile("REDACTED_" + "PENDING_SECURITY_REVIEW")),
    ("aws-key", re.compile(r"AKIA[0-9A-Z]{16}")),
    ("github-token", re.compile(r"github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9]{20,}")),
]
SKIP_DIRS = {".git", "build"}


def main():
    problems = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
            ext = os.path.splitext(name)[1].lower()
            if ext == ".png" and rel not in ALLOWED_PNG:
                problems.append(rel + ": unexpected png")
            elif ext in BLOCKED_EXT:
                problems.append(rel + ": blocked file type " + ext)
            if ext in {".png", ".jar"}:
                continue
            try:
                with open(path, "rb") as f:
                    data = f.read()
            except OSError as e:
                problems.append(rel + ": " + str(e))
                continue
            if b"\0" in data:
                problems.append(rel + ": binary content")
                continue
            text = data.decode("utf-8", "replace")
            for label, rule in TEXT_RULES:
                if rule.search(text):
                    problems.append(rel + ": matched " + label)
    if problems:
        print("scan-tree: " + str(len(problems)) + " problem(s)")
        for p in problems:
            print("  " + p)
        return 1
    print("scan-tree: no blocked files or known secret markers")
    return 0


if __name__ == "__main__":
    sys.exit(main())
