#!/usr/bin/env python3
"""Gom bảng danh_sach (D1) theo model cho trang SEO vgs.zone/cleantv/tivi. Chỉ ra số đếm: số máy, đánh giá ổn / chưa ổn,
gói hay tắt, gói hay giữ, bản Android, tháng gần nhất. Model dưới NGUONG máy thì bỏ (ít người quá dễ đoán ra ai)."""
import json, re, sys, unicodedata
from collections import Counter, defaultdict

NGUONG = 3
vao, du_lieu, du_lieu_kt, ra = sys.argv[1:5]
raw = open(vao, encoding="utf-8").read()
rows = json.loads(raw[raw.index("["):])[0]["results"]

ten = {}
# Chỉ tên tiếng Việt trong app (TEN_APP); mô tả tiếng Anh của du-lieu.json lẫn vào trang tiếng Việt nên bỏ.
kt = open(du_lieu_kt, encoding="utf-8").read()
khoi = kt[kt.index("val TEN_APP = mapOf("):]
khoi = khoi[:khoi.index("\n)")]
for p, n in re.findall(r'"([A-Za-z0-9_.]+)" to "([^"]+)"', khoi): ten[p] = n

HANG = {"tcl": "TCL", "lg": "LG", "jvc": "JVC", "aqua": "AQUA", "sharp": "Sharp", "xiaomi": "Xiaomi", "sony": "Sony",
        "samsung": "Samsung", "haier": "Haier", "casper": "Casper", "coocaa": "Coocaa", "skyworth": "Skyworth", "hisense": "Hisense",
        "philips": "Philips", "panasonic": "Panasonic", "toshiba": "Toshiba", "google": "Google", "nvidia": "NVIDIA"}

def bo_dau(s):
    s = unicodedata.normalize("NFD", s).replace("đ", "d").replace("Đ", "D")
    return "".join(c for c in s if unicodedata.category(c) != "Mn")

def slug(*phan):
    s = "-".join(bo_dau(p).lower() for p in phan if p)
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", s)).strip("-")[:80]

nhom = defaultdict(list)
for r in rows:
    hang, model, device = (r.get("hang") or "").strip(), (r.get("model") or "").strip(), (r.get("device") or "").strip()
    if not hang or not model or not r.get("may"): continue
    nhom[(hang.lower(), model.lower(), device.lower())].append(r)

def ds(x):
    try:
        v = json.loads(x or "[]")
        return [p for p in v if isinstance(p, str) and re.fullmatch(r"[A-Za-z0-9_.]+", p)]
    except Exception:
        return []

out = []
for (h, m, d), rs in nhom.items():
    may = {r["may"] for r in rs}
    if len(may) < NGUONG: continue
    # Mỗi máy tính một lần: lấy lượt gửi mới nhất của máy đó.
    moi = {}
    for r in sorted(rs, key=lambda r: r["luc"]): moi[r["may"]] = r
    rs1 = list(moi.values())
    co_goi = [r for r in rs1 if ds(r["goi"])]
    tat, giu = Counter(), Counter()
    for r in co_goi: tat.update(set(ds(r["goi"])))
    for r in rs1: giu.update(set(ds(r["giu"])))
    hien = Counter(r["hang"].strip() for r in rs).most_common(1)[0][0]
    hang_dep = HANG.get(h, hien if not hien.islower() else hien.capitalize())
    model_dep = Counter(r["model"].strip() for r in rs).most_common(1)[0][0]
    device_dep = Counter((r["device"] or "").strip() for r in rs).most_common(1)[0][0]
    out.append({
        "slug": slug(hang_dep if not bo_dau(model_dep).lower().startswith(bo_dau(hang_dep).lower()) else "", model_dep, device_dep if device_dep.lower() not in model_dep.lower() else ""),
        "hang": hang_dep, "model": model_dep, "device": device_dep,
        "android": sorted({r["android"] for r in rs1 if r.get("android")}, key=lambda a: float(a) if re.fullmatch(r"\d+(\.\d+)?", a) else 0),
        "soMay": len(may), "on": sum(r["danh_gia"] == "on" for r in rs1), "chuaOn": sum(r["danh_gia"] != "on" for r in rs1),
        "soCoGoi": len(co_goi),
        "goiTat": [{"p": p, "ten": ten.get(p, ""), "n": n} for p, n in tat.most_common(20)],
        "goiGiu": [{"p": p, "ten": ten.get(p, ""), "n": n} for p, n in giu.most_common(6) if n >= 2],
        "mau": Counter(r["mau"] for r in rs1 if r.get("mau")).most_common(1)[0][0] if any(r.get("mau") for r in rs1) else "",
        "thang": max(r["luc"] for r in rs)[:7],
    })
# slug trùng (hai cách gõ hãng) thì thêm hậu tố.
dem = Counter()
for x in sorted(out, key=lambda x: -x["soMay"]):
    dem[x["slug"]] += 1
    if dem[x["slug"]] > 1: x["slug"] += f"-{dem[x['slug']]}"
out.sort(key=lambda x: (-x["soMay"], x["slug"]))
json.dump({"capNhat": max(r["luc"] for r in rows)[:10], "tongMay": len({r["may"] for r in rows if r.get("may")}), "ds": out},
          open(ra, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(out)} model (>= {NGUONG} máy) -> {ra}")
