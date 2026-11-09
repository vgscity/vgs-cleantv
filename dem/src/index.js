// Bộ đếm ẩn danh của CleanTV (21/10/2026). Không lưu IP, email hay tên: app gửi một mã máy ngẫu nhiên tự sinh,
// lượt tải chỉ lưu băm IP+ngày để đếm số người tải khác nhau trong ngày.
const LOAI = new Set(["mo", "cap-nhat", "don", "home", "home-phim", "khoi-phuc", "may-chieu", "don-loi", "khoa"]);
const cat = (s, n) => String(s ?? "").replace(/[\u0000-\u001f]/g, "").trim().slice(0, n);

async function bam(chu) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(chu));
  return [...new Uint8Array(d)].slice(0, 8).map(b => b.toString(16).padStart(2, "0")).join("");
}

// Ghi một sự kiện, bỏ bản trùng: cùng máy + loại + tivi + ghi chú trong [trongGiay] giây gần nhất (lượt tải: cả ngày,
// vì mã máy của lượt tải đã là băm IP+ngày). Một câu lệnh, nên vòng lặp gửi lại y hệt không tốn thêm dòng ghi D1.
// Loại chỉ ghi một dòng cho mỗi máy mỗi ngày giờ VN (25/10/2026, D1 miễn phí chạm 76% trần 100.000 dòng ghi/ngày:
// "đổi giao diện" một máy bấm cả chục lần). Lượt sau trong ngày không thêm dòng, "home" vẫn cộng vào dem_so cho landing.
const MOT_LAN_NGAY = new Set(["home", "mo"]);
const dauNgayVN = () => { const d = new Date(Date.now() + 7 * 3600e3); d.setUTCHours(0, 0, 0, 0); return new Date(d.getTime() - 7 * 3600e3).toISOString(); };

async function ghi(env, r, trongGiay = 600) {
  if (MOT_LAN_NGAY.has(r.loai) && r.may) {
    const daCo = await env.DB.prepare("SELECT 1 FROM su_kien WHERE loai = ?1 AND may = ?2 AND luc >= ?3 LIMIT 1")
      .bind(r.loai, r.may, dauNgayVN()).first();
    if (daCo) {
      if (r.loai === "home") await env.DB.prepare("UPDATE dem_so SET n = n + 1 WHERE khoa = 'home'").run();
      return;
    }
  }
  const kq = await env.DB.prepare(`INSERT INTO su_kien (luc, loai, may, ban, tivi, so, them, quoc_gia)
      SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8 WHERE NOT EXISTS (
        SELECT 1 FROM su_kien WHERE loai = ?2 AND may = ?3 AND tivi = ?5 AND them = ?7 AND luc >= ?9)`)
    .bind(new Date().toISOString(), r.loai, r.may || "", r.ban || "", r.tivi || "", r.so || 0, r.them || "", r.quoc_gia || "",
      new Date(Date.now() - trongGiay * 1000).toISOString()).run();
  // Bộ đếm sẵn cho /so-nguoi (25/10/2026): đọc 1 dòng thay vì COUNT(*) quét cả bảng mỗi lần landing hỏi.
  if (r.loai === "home" && kq.meta?.changes) await env.DB.prepare("UPDATE dem_so SET n = n + 1 WHERE khoa = 'home'").run();
}

/** Hạn mức theo IP (binding Rate Limiting của Workers, 23/10/2026): quá hạn thì vẫn trả lời bình thường nhưng không ghi. */
async function trongHan(env, req) {
  if (!env.GIOI_HAN) return true;
  const ip = req.headers.get("cf-connecting-ip") || "";
  try { return (await env.GIOI_HAN.limit({ key: await bam(ip) })).success; } catch { return true; }
}

/** File APK từ R2 (binding APK, bucket cleantv) — thay tên miền thử pub-…r2.dev (23/10/2026). */
async function guiApk(env, req, ten) {
  if (!/^CleanTV(-[0-9][0-9A-Za-z.-]*)?\.apk$/.test(ten)) return new Response("Không có", { status: 404 });
  const coRange = req.headers.has("range");
  const coDieuKien = ["if-none-match", "if-modified-since", "if-match", "if-unmodified-since"].some(k => req.headers.has(k));
  const tuyChon = {};
  if (coRange) tuyChon.range = req.headers;
  if (coDieuKien) tuyChon.onlyIf = req.headers;
  // HEAD: chỉ cần siêu dữ liệu.
  const obj = req.method === "HEAD" ? await env.APK.head(ten) : await env.APK.get(ten, tuyChon);
  if (!obj) return new Response("Không có", { status: 404 });
  const h = new Headers();
  obj.writeHttpMetadata(h);
  h.set("content-type", "application/vnd.android.package-archive");
  h.set("content-disposition", `attachment; filename="${ten}"`);
  h.set("etag", obj.httpEtag);
  h.set("accept-ranges", "bytes");
  // Bản có số (CleanTV-2.4.apk) không bao giờ đổi; CleanTV.apk là bản mới nhất nên cache ngắn.
  h.set("cache-control", ten === "CleanTV.apk" ? "public, max-age=300" : "public, max-age=31536000, immutable");
  h.set("content-length", String(obj.size));
  if (req.method === "HEAD") return new Response(null, { headers: h });
  if (!("body" in obj)) return new Response(null, { status: 304, headers: h });
  if (coRange && obj.range) {
    const off = obj.range.offset ?? 0, len = obj.range.length ?? obj.size - off;
    h.set("content-range", `bytes ${off}-${off + len - 1}/${obj.size}`);
    h.set("content-length", String(len));
    return new Response(obj.body, { status: 206, headers: h });
  }
  return new Response(obj.body, { headers: h });
}

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    const qg = req.cf?.country || "";

    // Lượt tải APK: đếm rồi chuyển sang file trên R2.
    if (url.pathname === "/tai" || url.pathname.startsWith("/tai/")) {
      const ip = req.headers.get("cf-connecting-ip") || "";
      const ngay = new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10);
      const may = await bam(`${ip}|${ngay}|${env.MUOI || "cleantv"}`);
      const nguon = cat(url.searchParams.get("tu"), 40);
      if (req.method === "GET" && await trongHan(env, req)) ctx.waitUntil(ghi(env, { loai: "tai", may, them: nguon, quoc_gia: qg }, 86400).catch(() => {}));
      return Response.redirect(new URL("/apk/CleanTV.apk", url).toString(), 302);
    }

    if (url.pathname.startsWith("/apk/") && (req.method === "GET" || req.method === "HEAD")) return guiApk(env, req, url.pathname.slice(5));

    // Tín hiệu từ app.
    if (url.pathname === "/su-kien" && req.method === "POST") {
      const raw = await req.text();
      if (raw.length > 2000) return new Response("qua dai", { status: 413 });
      let b; try { b = JSON.parse(raw); } catch { return new Response("sai", { status: 400 }); }
      if (!LOAI.has(b.loai) || !/^[a-f0-9-]{8,40}$/i.test(b.may || "")) return new Response("sai", { status: 400 });
      if (!(await trongHan(env, req))) return new Response("ok");
      ctx.waitUntil(ghi(env, {
        loai: b.loai, may: cat(b.may, 40), ban: cat(b.ban, 10), tivi: cat(b.tivi, 80),
        so: Math.max(0, Math.min(10000, Number(b.so) || 0)), them: cat(b.them, 120), quoc_gia: qg,
      }).catch(() => {}));
      return new Response("ok");
    }

    // Danh sách gói người dùng TỰ BẤM gửi từ màn Xong (23/10/2026): model tivi + gói đang tắt + đánh giá, để làm mẫu
    // cho tivi chưa có trong danh sách. Không IP, không email (trừ khi người dùng tự ghi chỗ liên hệ).
    if (url.pathname === "/danh-sach" && req.method === "POST") {
      const raw = await req.text();
      if (raw.length > 40000) return new Response("qua dai", { status: 413 });
      let b; try { b = JSON.parse(raw); } catch { return new Response("sai", { status: 400 }); }
      const goi = (a, n) => (Array.isArray(a) ? a : []).map(x => cat(x, 120)).filter(x => /^[A-Za-z0-9_.]+$/.test(x)).slice(0, n);
      if (!/^[a-f0-9-]{8,40}$/i.test(b.may || "")) return new Response("sai", { status: 400 });
      if (!(await trongHan(env, req))) return new Response("ok");
      await env.DB.prepare(`INSERT INTO danh_sach (luc, may, ban, hang, model, device, android, mau, danh_gia, ghi_chu, lien_he, goi, giu, quoc_gia, chan_doan)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`).bind(
        new Date().toISOString(), cat(b.may, 40), cat(b.ban, 10), cat(b.hang, 60), cat(b.model, 80), cat(b.device, 80), cat(b.android, 10),
        cat(b.mau, 80), b.danhGia === "on" ? "on" : "chua-on", cat(b.ghiChu, 1000), cat(b.lienHe, 120),
        JSON.stringify(goi(b.goi, 400)), JSON.stringify(goi(b.giu, 100)), qg, String(b.chanDoan ?? "").replace(/[\u0000-\u0008]/g, "").slice(0, 4500)).run();
      return new Response("ok");
    }
    if (url.pathname === "/danh-sach" && req.method === "GET") {
      if (!env.XEM_KHOA || req.headers.get("x-khoa") !== env.XEM_KHOA) return new Response("Không có quyền", { status: 403 });
      const r = await env.DB.prepare(`SELECT * FROM danh_sach ORDER BY luc DESC LIMIT 50`).all();
      return Response.json(r.results, { headers: { "cache-control": "no-store" } });
    }

    // Số liệu dạng JSON cho admin vgs.zone (tab CleanTV) — khoá đi bằng header, không nằm trong URL.
    // Con số công khai cho landing (vgs.zone/cleantv, /en): Alex chốt 25/10/2026 dùng số lượt cài / đổi Home sạch, ghi nhãn
    // "người đã dùng app".
    if (url.pathname === "/so-nguoi") {
      // Landing hỏi lại 30 giây một lần khi tab đang mở (số nhảy gần thời gian thực): mỗi lần đọc 1 dòng, cache biên 15 giây.
      const khoa = new Request("https://cleantv-dem/so-nguoi");
      const daCo = await caches.default.match(khoa);
      if (daCo) return daCo;
      const r = await env.DB.prepare("SELECT n FROM dem_so WHERE khoa = 'home'").first();
      const tl = Response.json({ n: r?.n ?? 0 }, { headers: { "cache-control": "public, max-age=15", "access-control-allow-origin": "*" } });
      ctx.waitUntil(caches.default.put(khoa, tl.clone()));
      return tl;
    }

    if (url.pathname === "/so-lieu") {
      if (!env.XEM_KHOA || req.headers.get("x-khoa") !== env.XEM_KHOA) return new Response("Không có quyền", { status: 403 });
      const n = [7, 14, 30, 90].includes(Number(url.searchParams.get("ngay"))) ? Number(url.searchParams.get("ngay")) : 14;
      return Response.json(await soLieu(env, n), { headers: { "cache-control": "no-store" } });
    }

    if (url.pathname === "/thong-ke") {
      // Khoá qua header như /so-lieu (không để trong URL: lộ qua log, lịch sử trình duyệt). Xem số ở vgs.zone/admin#cleantv.
      if (!env.XEM_KHOA || req.headers.get("x-khoa") !== env.XEM_KHOA) return new Response("Không có quyền", { status: 403 });
      return new Response(await trangThongKe(env), { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
    }
    return new Response("CleanTV", { status: 404 });
  },
};

// ---------- Trang thống kê (giờ VN) ----------
const VN = `datetime(luc, '+7 hours')`;
const html = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");

async function trangThongKe(env) {
  const q = (sql, ...a) => env.DB.prepare(sql).bind(...a).all().then(r => r.results);
  const mot = async (sql, ...a) => (await q(sql, ...a))[0] || {};
  const [tai, may, cn, don, home, banDs, tivi, ngay, qg] = await Promise.all([
    mot(`SELECT COUNT(*) tong, SUM(${VN} >= datetime('now','+7 hours','-7 days')) tuan, SUM(date(${VN}) = date('now','+7 hours')) nay, COUNT(DISTINCT may) nguoi FROM su_kien WHERE loai='tai'`),
    mot(`SELECT COUNT(DISTINCT may) tong, COUNT(DISTINCT CASE WHEN ${VN} >= datetime('now','+7 hours','-7 days') THEN may END) tuan, COUNT(DISTINCT CASE WHEN date(${VN}) = date('now','+7 hours') THEN may END) nay FROM su_kien WHERE loai='mo'`),
    mot(`SELECT COUNT(*) tong, COUNT(DISTINCT may) may FROM su_kien WHERE loai='cap-nhat'`),
    mot(`SELECT COUNT(*) tong, COUNT(DISTINCT tivi) tivi, SUM(so) goi FROM su_kien WHERE loai='don'`),
    mot(`SELECT COUNT(*) tong FROM su_kien WHERE loai='home'`),
    q(`SELECT ban, COUNT(*) n FROM (SELECT may, ban, MAX(luc) l FROM su_kien WHERE loai='mo' GROUP BY may) GROUP BY ban ORDER BY ban DESC`),
    q(`SELECT tivi, SUM(loai='don') don, SUM(loai='home') home, SUM(loai='khoi-phuc') kp, SUM(loai='may-chieu') mc FROM su_kien WHERE tivi != '' GROUP BY tivi ORDER BY don + home DESC LIMIT 40`),
    q(`SELECT date(${VN}) d, SUM(loai='tai') tai, COUNT(DISTINCT CASE WHEN loai='mo' THEN may END) mo, SUM(loai='don') don FROM su_kien WHERE ${VN} >= datetime('now','+7 hours','-14 days') GROUP BY d ORDER BY d`),
    q(`SELECT quoc_gia, COUNT(*) n FROM su_kien WHERE loai='tai' GROUP BY quoc_gia ORDER BY n DESC LIMIT 8`),
  ]);
  const max = Math.max(1, ...ngay.map(r => Math.max(r.tai, r.mo, r.don)));
  const o = (nhan, so, phu) => `<div class="o"><b>${so ?? 0}</b><span>${nhan}</span>${phu ? `<small>${phu}</small>` : ""}</div>`;
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CleanTV — thống kê</title>
<style>body{margin:0;background:#0b0b10;color:#f4f4f8;font:15px/1.5 system-ui,sans-serif;padding:20px;max-width:1000px;margin:auto}
h1{font-size:24px}h2{font-size:17px;margin-top:28px}.luoi{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}
.o{background:#15151c;border:1px solid #2a2a36;border-radius:16px;padding:16px}.o b{font-size:30px;display:block}.o span{color:#a1a1b5}.o small{display:block;color:#a1a1b5;margin-top:4px}
table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid #2a2a36;text-align:left}th{color:#a1a1b5;font-weight:500}
.cot{display:flex;gap:6px;align-items:flex-end;height:140px}.cot div{flex:1;display:flex;flex-direction:column;justify-content:flex-end;gap:2px;align-items:center;font-size:11px;color:#a1a1b5}
.cot i{display:block;width:100%;border-radius:4px}.t{background:#8b5cf6}.m{background:#ec4899}.d{background:#22c55e}</style>
<h1>📺 CleanTV — thống kê</h1>
<div class="luoi">
${o("lượt tải APK", tai.tong, `hôm nay ${tai.nay ?? 0} · 7 ngày ${tai.tuan ?? 0} · ${tai.nguoi ?? 0} người`)}
${o("máy đã mở app", may.tong, `hôm nay ${may.nay ?? 0} · 7 ngày ${may.tuan ?? 0}`)}
${o("lượt cập nhật app", cn.tong, `${cn.may ?? 0} máy`)}
${o("lần dọn tivi", don.tong, `${don.tivi ?? 0} mẫu tivi · ${don.goi ?? 0} gói đã tắt`)}
${o("lần cài Home sạch", home.tong)}
</div>
<h2>14 ngày qua <small style="color:#a1a1b5">(<span style="color:#8b5cf6">■</span> tải · <span style="color:#ec4899">■</span> máy mở app · <span style="color:#22c55e">■</span> dọn tivi)</small></h2>
<div class="cot">${ngay.map(r => `<div title="${r.d}: tải ${r.tai}, mở ${r.mo}, dọn ${r.don}"><i class="t" style="height:${r.tai / max * 110}px"></i><i class="m" style="height:${r.mo / max * 110}px"></i><i class="d" style="height:${r.don / max * 110}px"></i>${r.d.slice(8)}</div>`).join("")}</div>
<h2>Bản app đang dùng</h2><table><tr><th>Bản</th><th>Số máy</th></tr>${banDs.map(r => `<tr><td>${html(r.ban)}</td><td>${r.n}</td></tr>`).join("")}</table>
<h2>Tivi</h2><table><tr><th>Mẫu</th><th>Dọn</th><th>Home sạch</th><th>Khôi phục</th><th>Máy chiếu</th></tr>${tivi.map(r => `<tr><td>${html(r.tivi)}</td><td>${r.don}</td><td>${r.home}</td><td>${r.kp}</td><td>${r.mc}</td></tr>`).join("")}</table>
<h2>Nơi tải</h2><table>${qg.map(r => `<tr><td>${html(r.quoc_gia || "?")}</td><td>${r.n}</td></tr>`).join("")}</table>
<p style="color:#a1a1b5;font-size:13px">Ẩn danh: không lưu IP, email hay tên. Giờ Việt Nam.</p>`;
}

async function soLieu(env, n) {
  const q = (sql, ...a) => env.DB.prepare(sql).bind(...a).all().then(r => r.results);
  const mot = async (sql, ...a) => (await q(sql, ...a))[0] || {};
  const tu = `datetime('now','+7 hours','-${n} days')`;
  const truoc = `datetime('now','+7 hours','-${2 * n} days')`;
  // Mỗi chỉ số: cả đời · trong kỳ · kỳ liền trước (để so lên/xuống).
  const chiSo = (loai, dem = "COUNT(*)") => mot(`SELECT ${dem.replace("X", "1")} tong,
      ${dem.replace("COUNT(*)", `SUM(${VN} >= ${tu})`).replace("COUNT(DISTINCT may)", `COUNT(DISTINCT CASE WHEN ${VN} >= ${tu} THEN may END)`)} ky,
      ${dem.replace("COUNT(*)", `SUM(${VN} >= ${truoc} AND ${VN} < ${tu})`).replace("COUNT(DISTINCT may)", `COUNT(DISTINCT CASE WHEN ${VN} >= ${truoc} AND ${VN} < ${tu} THEN may END)`)} kyTruoc
      FROM su_kien WHERE loai='${loai}'`);
  const [tai, mo, capNhat, don, home, khoiPhuc, mayChieu, goi, ban, nguon, quocGia, tivi, ngay, ganDay] = await Promise.all([
    chiSo("tai"), chiSo("mo", "COUNT(DISTINCT may)"), chiSo("cap-nhat"), chiSo("don"), chiSo("home"), chiSo("khoi-phuc"), chiSo("may-chieu"),
    mot(`SELECT SUM(so) tong, COUNT(DISTINCT tivi) tivi FROM su_kien WHERE loai='don'`),
    q(`SELECT ban, COUNT(*) n FROM (SELECT may, ban, MAX(luc) l FROM su_kien WHERE loai='mo' GROUP BY may) GROUP BY ban ORDER BY n DESC`),
    q(`SELECT CASE WHEN them = '' THEN 'khác' ELSE them END nguon, COUNT(*) n FROM su_kien WHERE loai='tai' AND ${VN} >= ${tu} GROUP BY 1 ORDER BY n DESC LIMIT 8`),
    q(`SELECT CASE WHEN quoc_gia = '' THEN '?' ELSE quoc_gia END qg, COUNT(*) n FROM su_kien WHERE loai='tai' AND ${VN} >= ${tu} GROUP BY 1 ORDER BY n DESC LIMIT 8`),
    q(`SELECT tivi, SUM(loai='don') don, SUM(loai='home') home, SUM(loai='khoi-phuc') khoiPhuc, SUM(loai='may-chieu') mayChieu, MAX(${VN}) lanCuoi
       FROM su_kien WHERE tivi != '' GROUP BY tivi ORDER BY lanCuoi DESC LIMIT 50`),
    q(`SELECT date(${VN}) d, SUM(loai='tai') tai, COUNT(DISTINCT CASE WHEN loai='mo' THEN may END) mo, SUM(loai='don') don, SUM(loai='cap-nhat') capNhat
       FROM su_kien WHERE ${VN} >= ${tu} GROUP BY d ORDER BY d`),
    q(`SELECT ${VN} luc, loai, ban, tivi, so, quoc_gia qg FROM su_kien WHERE loai != 'mo' ORDER BY luc DESC LIMIT 20`),
  ]);
  return { ngay: n, chiSo: { tai, mo, capNhat, don, home, khoiPhuc, mayChieu }, goi, ban, nguon, quocGia, tivi, theoNgay: ngay, ganDay };
}
