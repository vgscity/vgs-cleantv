#!/usr/bin/env bash
# Số liệu trang SEO "Tivi <model> dọn quảng cáo được không" trên vgs.zone (12/11/2026).
# Đọc bảng danh_sach của D1 cleantv-dem, gom theo model, chỉ giữ số đếm (không email, ghi chú, mã máy), ghi ra
# vgs-contest/lib/cleantvTiviSo.json. Chạy lại rồi deploy vgs.zone để cập nhật:  scripts/lam-trang-tivi.sh [đường ra]
set -euo pipefail
cd "$(dirname "$0")/../dem"
set -a; . ../.env.cloudflare; set +a
export CLOUDFLARE_API_TOKEN=$CF_API_TOKEN CLOUDFLARE_ACCOUNT_ID=$CF_ACCOUNT_ID
TAM=$(mktemp)
trap 'rm -f "$TAM"' EXIT
npx wrangler d1 execute cleantv-dem --remote --json --command \
  "SELECT luc, may, hang, model, device, android, mau, danh_gia, goi, giu FROM danh_sach" 2>/dev/null > "$TAM"
python3 ../scripts/lam-trang-tivi.py "$TAM" ../app/src/main/assets/du-lieu.json ../app/src/main/java/zone/vgs/tvsach/dulieu/DuLieu.kt \
  "${1:-$HOME/VGSCODE/vgs/vgs-contest/lib/cleantvTiviSo.json}"
