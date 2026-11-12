package zone.vgs.tvsach.dulieu

import org.json.JSONArray
import org.json.JSONObject

/**
 * App người dùng tự cài mà chỉ có lối vào kiểu điện thoại (Phim4K, TizenTube bản điện thoại… — góp ý nguyenminhtrung201098
 * 10/11/2026): Home sạch chỉ hiện app có LEANBACK_LAUNCHER, còn app điện thoại phải bật Thư viện app rồi thêm tay. CleanTV
 * ghi các app đó vào library_apps của config.json; launcher coi library_apps là "đã thêm", nên app hiện cả khi Thư viện app tắt.
 * Gói đang có trong hidden_apps (của theme hay Nâng cao) không thêm, vì library_apps đè hidden_apps. "Bỏ khỏi màn hình chính"
 * trên tivi vẫn thắng library_apps, nên người dùng ẩn lại được ngay trên tivi.
 */
object AppNgoai {
    /** App tự cài + có lối vào điện thoại − có lối vào tivi − app nhận Home − gói của CleanTV / Home sạch. */
    fun loc(tuCai: Set<String>, loiDienThoai: Set<String>, loiTivi: Set<String>, nhanHome: Set<String>): Set<String> =
        tuCai.filter { it in loiDienThoai && it !in loiTivi && it !in nhanHome && !laGoiCleanTv(it) }.toSortedSet()

    private fun laGoiCleanTv(pkg: String) = pkg == "com.gathin.tvhome" || pkg == "zone.vgs.cleantv" || pkg.startsWith("zone.vgs.cleantv.")

    /** Gộp [ngoai] vào library_apps của [json], bỏ gói trong hidden_apps. Không có gì mới thì trả nguyên [json]. */
    fun gop(json: String, ngoai: Set<String>): String {
        if (ngoai.isEmpty()) return json
        val j = JSONObject(json)
        val an = mang(j, "hidden_apps").toSet()
        val cu = mang(j, "library_apps")
        val moi = ngoai.filter { it !in an && it !in cu }
        if (moi.isEmpty()) return json
        j.put("library_apps", JSONArray(cu + moi))
        return j.toString()
    }

    private fun mang(j: JSONObject, khoa: String): List<String> {
        val a = j.optJSONArray(khoa) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
    }
}
