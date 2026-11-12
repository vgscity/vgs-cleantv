package zone.vgs.tvsach.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNgoaiTest {
    @Test fun locChiGiuAppDienThoaiTuCai() {
        val r = AppNgoai.loc(
            tuCai = setOf("vn.phim4k", "com.smarttube", "com.projectivy", "zone.vgs.cleantv", "com.gathin.tvhome", "com.dichvu"),
            loiDienThoai = setOf("vn.phim4k", "com.smarttube", "com.projectivy", "zone.vgs.cleantv", "com.gathin.tvhome"),
            loiTivi = setOf("com.smarttube"),
            nhanHome = setOf("com.projectivy"),
        )
        assertEquals(setOf("vn.phim4k"), r)
    }

    @Test fun gopGiuLibraryCuVaBoHidden() {
        val json = """{"library_apps":["a.b"],"hidden_apps":["x.y"]}"""
        val j = JSONObject(AppNgoai.gop(json, setOf("a.b", "x.y", "c.d")))
        val a = j.getJSONArray("library_apps")
        assertEquals(listOf("a.b", "c.d"), (0 until a.length()).map { a.getString(it) })
    }

    @Test fun khongCoGiMoiThiGiuNguyen() {
        val json = """{"columns":5}"""
        assertEquals(json, AppNgoai.gop(json, emptySet()))
    }
}
