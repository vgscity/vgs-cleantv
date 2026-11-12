package zone.vgs.tvsach

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lưu cấu hình tivi vào tài khoản VGS (12/11/2026, anh Alex duyệt): app xin mã ghép ở vgs.zone, mở trang xác nhận trên
 * trình duyệt, người dùng đăng nhập bấm Xác nhận, app hỏi lại kèm khoá riêng thì nhận mã truy cập (giữ trong máy).
 * Máy chủ: vgs-contest/lib/cleantvTaiKhoan.ts + app/api/cleantv/{ghep,cau-hinh}. Mọi hàm gọi trên luồng nền.
 */
object TaiKhoanVgs {
    private const val GOC = "https://vgs.zone"

    data class MaGhep(val ma: String, val khoa: String, val link: String)
    data class TiviLuu(val id: String, val tivi: String, val kichThuoc: Int, val banApp: String, val luc: String)
    data class DanhSach(val ten: String, val ds: List<TiviLuu>, val toiDa: Int)
    class LoiVgs(msg: String, val hetPhien: Boolean = false) : Exception(msg)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("vgs", Context.MODE_PRIVATE)
    fun token(ctx: Context): String = prefs(ctx).getString("token", "") ?: ""
    fun daKetNoi(ctx: Context) = token(ctx).isNotEmpty()
    fun tenDaLuu(ctx: Context): String = prefs(ctx).getString("ten", "") ?: ""
    private fun luuToken(ctx: Context, tk: String) = prefs(ctx).edit().putString("token", tk).apply()
    fun luuTen(ctx: Context, ten: String) = prefs(ctx).edit().putString("ten", ten).apply()
    fun quen(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun goi(phuongThuc: String, duong: String, token: String = "", body: JSONObject? = null): Pair<Int, JSONObject> {
        val c = URL(GOC + duong).openConnection() as HttpURLConnection
        try {
            c.requestMethod = phuongThuc; c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val ma = c.responseCode
            val chu = (if (ma in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "{}"
            return ma to (runCatching { JSONObject(chu) }.getOrNull() ?: JSONObject())
        } finally { c.disconnect() }
    }

    private fun kiem(kq: Pair<Int, JSONObject>): JSONObject {
        val (ma, j) = kq
        if (ma == 401) throw LoiVgs(j.optString("error", "Phiên đăng nhập đã hết."), hetPhien = true)
        if (ma !in 200..299) throw LoiVgs(j.optString("error", "vgs.zone báo lỗi $ma."))
        return j
    }

    fun xinMa(ctx: Context): MaGhep {
        val j = kiem(goi("POST", "/api/cleantv/ghep", body = JSONObject().put("may", Dem.maMay(ctx.applicationContext))))
        return MaGhep(j.getString("ma"), j.getString("khoa"), j.getString("link"))
    }

    /** "cho" / "het" / "dung" (đã nhận và lưu mã truy cập). */
    fun hoiMa(ctx: Context, m: MaGhep): String {
        val j = kiem(goi("GET", "/api/cleantv/ghep?ma=${URLEncoder.encode(m.ma, "UTF-8")}&khoa=${URLEncoder.encode(m.khoa, "UTF-8")}"))
        val tk = j.optString("token")
        if (tk.isNotEmpty()) { luuToken(ctx, tk); return "dung" }
        return j.optString("trangThai", "het")
    }

    fun danhSach(ctx: Context): DanhSach {
        val j = kiem(goi("GET", "/api/cleantv/cau-hinh", token(ctx)))
        val a = j.optJSONArray("ds")
        val ds = (0 until (a?.length() ?: 0)).map { i -> a!!.getJSONObject(i).let {
            TiviLuu(it.optString("id"), it.optString("tivi"), it.optInt("kichThuoc"), it.optString("banApp"), it.optString("luc"))
        } }
        return DanhSach(j.optString("ten"), ds, j.optInt("toiDa", 10)).also { if (it.ten.isNotEmpty()) luuTen(ctx, it.ten) }
    }

    fun luu(ctx: Context, tivi: String, json: String) {
        kiem(goi("POST", "/api/cleantv/cau-hinh", token(ctx), JSONObject().put("tivi", tivi).put("json", json).put("banApp", BuildConfig.VERSION_NAME)))
    }

    fun lay(ctx: Context, id: String): String =
        kiem(goi("GET", "/api/cleantv/cau-hinh?id=${URLEncoder.encode(id, "UTF-8")}", token(ctx))).getString("json")

    fun xoa(ctx: Context, id: String) { kiem(goi("DELETE", "/api/cleantv/cau-hinh?id=${URLEncoder.encode(id, "UTF-8")}", token(ctx))) }

    /** Thoát: gỡ ghép trên máy chủ (hỏng mạng cũng quên ở máy). */
    fun thoat(ctx: Context) { runCatching { goi("DELETE", "/api/cleantv/cau-hinh?thoat=1", token(ctx)) }; quen(ctx) }
}
