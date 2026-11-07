#!/usr/bin/env bash
# =============================================================================
# Sal-Subsurf — ساخت کلید امضای شخصی و ثبت آن در GitHub Secrets (Termux)
# Automates docs/SIGNING-TERMUX.md sections 1–4.
# Run it from the extracted repository root:  bash scripts/termux-sign.sh
# =============================================================================
set -euo pipefail

KEYSTORE="$HOME/sal-subsurf-release.keystore"
ALIAS="sal-subsurf"
B64="$HOME/keystore.b64"

banner() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }

banner "0/5 ابزارها (Termux)"
if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found → installing openjdk-17 …"
  pkg install -y openjdk-17
fi
command -v keytool >/dev/null 2>&1 || { echo "[!] keytool باز هم پیدا نشد؛ دستی نصب کنید."; exit 1; }

banner "1/5 ساخت keystore (اگر از قبل ندارید)"
if [ -f "$KEYSTORE" ]; then
  echo "[$KEYSTORE] از قبل موجود است — از همان استفاده می‌کنیم."
  echo "(برای ساخت کلید تازه، ابتدا فایل را پشتیبان‌گیری و حذف کنید.)"
else
  echo "RSA-4096، اعتبار ۳۰ سال، فرمت PKCS12."
  echo "یک رمز قوی انتخاب کنید و آن را در جای امن یادداشت کنید (دو بار پرسیده می‌شود)."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 \
    -validity 10950 \
    -storetype PKCS12
fi
ls -l "$KEYSTORE"

banner "2/5 کدگذاری Base64"
base64 -w0 "$KEYSTORE" > "$B64"
echo "نوشته شد: $B64 ($(wc -c < "$B64") کاراکتر)"

banner "3/5 GitHub CLI"
if ! command -v gh >/dev/null 2>&1; then
  echo "gh not found → installing …"
  pkg install -y gh
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "ابتدا باید وارد GitHub شوید. دستور:"
  echo "    gh auth login"
  echo "گزینه‌ها: GitHub.com → HTTPS → Login with a web browser"
  echo "پس از ورود، دوباره همین اسکریپت را اجرا کنید."
  exit 0
fi

banner "4/5 ثبت Secrets در ریپوی فعلی"
gh secret set KEYSTORE_BASE64 < "$B64"
echo "رمز keystore را وارد کنید (برای KEYSTORE_PASSWORD و KEY_PASSWORD):"
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body "$ALIAS"
gh secret set KEY_PASSWORD

banner "5/5 تمام شد ✔"
echo "از push بعدی، ورک‌فلو Android APK علاوه بر debug، این را هم می‌سازد:"
echo "    Sal-Subsurf-release-signed.apk   ← امضاشده با کلید شخصی شما"
echo
echo "یادآوری: keystore و رمز را در جای امن نگه دارید (هرگز commit نشود —"
echo ".gitignore آن‌ها را مسدود می‌کند). برای امضای دستی بدون Secrets:"
echo "docs/SIGNING-TERMUX.md بخش ۶ را ببینید."
