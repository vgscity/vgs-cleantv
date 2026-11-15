package zone.vgs.khoa

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar

/** Một khung giờ khoá: phút trong ngày [tu, den) — den < tu là qua đêm (21:00–06:00); ngay 1 = Thứ Hai … 7 = Chủ Nhật. */
data class Khung(val tu: Int, val den: Int, val ngay: Set<Int>)

/** Cấu hình phụ huynh đặt từ CleanTV. pinBam = SHA-256(muoi + PIN), tivi không giữ PIN gốc.
 *  apps (1.1, 15/11/2026): gói khoá riêng — mở app đó là hỏi PIN, không phụ thuộc giờ khoá hay công tắc bat. */
data class CauHinh(val bat: Boolean, val muoi: String, val pinBam: String, val khung: List<Khung>, val moTamPhut: Int = 60, val apps: Set<String> = emptySet())

/** Luật khoá, không đụng Android để test được trên máy tính. */
object Luat {
    fun docJson(s: String?): CauHinh? = runCatching {
        val o = JSONObject(s ?: return null)
        val ks = o.optJSONArray("khung") ?: JSONArray()
        CauHinh(
            bat = o.optBoolean("bat"),
            muoi = o.optString("muoi"),
            pinBam = o.optString("pinBam"),
            khung = (0 until ks.length()).map { i ->
                val k = ks.getJSONObject(i)
                val ng = k.optJSONArray("ngay") ?: JSONArray("[1,2,3,4,5,6,7]")
                Khung(phut(k.getString("tu")), phut(k.getString("den")), (0 until ng.length()).map { ng.getInt(it) }.filter { it in 1..7 }.toSet())
            },
            moTamPhut = o.optInt("moTamPhut", 60).coerceIn(5, 240),
            apps = o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }.toSet() } ?: emptySet(),
        )
    }.getOrNull()

    /** "21:30" → 1290. */
    fun phut(hhmm: String): Int {
        val (h, m) = hhmm.trim().split(":").map { it.toInt() }
        require(h in 0..23 && m in 0..59)
        return h * 60 + m
    }

    fun chuGio(phut: Int): String = "%02d:%02d".format(phut / 60 % 24, phut % 60)

    /** 1 = Thứ Hai … 7 = Chủ Nhật. */
    fun thu(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    private fun homTruoc(ngay: Int) = if (ngay == 1) 7 else ngay - 1

    fun trongKhung(k: Khung, ngay: Int, phut: Int): Boolean = when {
        k.tu < k.den -> ngay in k.ngay && phut >= k.tu && phut < k.den
        k.tu > k.den -> (ngay in k.ngay && phut >= k.tu) || (homTruoc(ngay) in k.ngay && phut < k.den)
        else -> ngay in k.ngay // tu == den: cả ngày
    }

    fun trongGio(c: CauHinh, luc: Calendar): Boolean {
        if (!c.bat || c.pinBam.isBlank()) return false
        val ngay = thu(luc); val p = luc.get(Calendar.HOUR_OF_DAY) * 60 + luc.get(Calendar.MINUTE)
        return c.khung.any { trongKhung(it, ngay, p) }
    }

    /** Giờ khung khoá đang chạy kết thúc (dò từng phút, tối đa 8 ngày); null nếu không khoá. */
    fun hetKhoaLuc(c: CauHinh, bayGio: Calendar): Calendar? {
        if (!trongGio(c, bayGio)) return null
        val x = bayGio.clone() as Calendar
        x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0)
        repeat(8 * 24 * 60) {
            x.add(Calendar.MINUTE, 1)
            if (!trongGio(c, x)) return x
        }
        return null
    }

    /** Mở [goi] có phải hỏi PIN không: gói nằm trong apps và đã có PIN. */
    fun khoaApp(c: CauHinh, goi: String?): Boolean = goi != null && c.pinBam.isNotBlank() && goi in c.apps

    fun bam(muoi: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((muoi + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun dungPin(c: CauHinh, pin: String): Boolean = c.pinBam.isNotBlank() && bam(c.muoi, pin) == c.pinBam
}
