#!/usr/bin/env bash
# =============================================================================
# Sal-Subsurf — آپلود پروژه به یک ریپوی خالی جدید GitHub (Termux / Linux)
# Preserves hidden files (.github/workflows) that the GitHub web uploader drops.
#
# Usage (from the extracted repository root):
#   bash scripts/upload-to-github.sh https://github.com/USER/REPO.git [branch]
#
# With a personal access token (recommended in Termux):
#   bash scripts/upload-to-github.sh https://<TOKEN>@github.com/USER/REPO.git
# Token: GitHub → Settings → Developer settings → Personal access tokens →
#        "Generate new token (classic)" → scope: repo
# =============================================================================
set -euo pipefail

REMOTE="${1:?Usage: upload-to-github.sh https://github.com/USER/REPO.git [branch]}"
BRANCH="${2:-main}"

banner() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }

banner "0/3 ابزارها"
if ! command -v git >/dev/null 2>&1; then
  echo "git not found → installing …"
  pkg install -y git 2>/dev/null || sudo apt-get install -y git
fi

if [ ! -d .git ]; then
  banner "1/3 مقداردهی مخزن محلی"
  git init -b "$BRANCH" 2>/dev/null || { git init && git checkout -b "$BRANCH"; }
  git config user.name  >/dev/null 2>&1 || git config --global user.name  "Sal Dev"
  git config user.email >/dev/null 2>&1 || git config --global user.email "sal@example.invalid"
  git add -A
  git commit -m "Sal-Subsurf v1.1.0: SubsurfaceExplorer (app + API + CI)" -q
else
  banner "1/3 مخزن محلی از قبل موجود است"
  git add -A
  git commit -m "Update Sal-Subsurf" -q || echo "(چیزی برای commit نبود)"
fi

banner "2/3 اتصال ریموت و push"
git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE"
git push -u origin "$BRANCH"

banner "3/3 تمام شد ✔"
echo "حالا در گیت‌هاب: تب Actions ریپو → ورک‌فلو «Android APK» به‌صورت خودکار اجرا می‌شود."
echo "پس از چند دقیقه، از صفحهٔ همان run بخش Artifacts را دانلود کنید:"
echo "    Sal-Subsurf-debug.apk            (همیشه)"
echo "    Sal-Subsurf-release-signed.apk   (اگر Secrets امضا را تنظیم کرده باشید)"
echo "برای انتشار رسمی:  git tag v1.1.0 && git push origin v1.1.0"
echo "→ APK ها به یک GitHub Release خودکار پیوست می‌شوند."
