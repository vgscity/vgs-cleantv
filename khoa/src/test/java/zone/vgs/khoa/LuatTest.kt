package zone.vgs.khoa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LuatTest {
    private fun luc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }
    private val muoi = "abc"
    private fun ch(vararg k: Khung, bat: Boolean = true) = CauHinh(bat, muoi, Luat.bam(muoi, "1234"), k.toList())
    private val CA_TUAN = (1..7).toSet()

    @Test fun thuHai_laMot() { assertEquals(1, Luat.thu(luc(2026, 10, 19, 10, 0))); assertEquals(7, Luat.thu(luc(2026, 10, 25, 10, 0))) }

    @Test fun quaDem() {
        val c = ch(Khung(Luat.phut("21:00"), Luat.phut("06:00"), CA_TUAN))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 23, 22, 30)))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 24, 5, 59)))
        assertFalse(Luat.trongGio(c, luc(2026, 10, 24, 6, 0)))
        assertFalse(Luat.trongGio(c, luc(2026, 10, 23, 20, 59)))
        val het = Luat.hetKhoaLuc(c, luc(2026, 10, 23, 22, 30))!!
        assertEquals(24, het.get(Calendar.DAY_OF_MONTH)); assertEquals(6, het.get(Calendar.HOUR_OF_DAY)); assertEquals(0, het.get(Calendar.MINUTE))
    }

    @Test fun chiNgayHoc_quaDemTinhTheoNgayBatDau() {
        // Khoá tối thứ Hai–Thứ Năm 21:00–06:00: sáng thứ Bảy (đêm thứ Sáu không khoá) phải mở.
        val c = ch(Khung(Luat.phut("21:00"), Luat.phut("06:00"), setOf(1, 2, 3, 4)))
        assertTrue(Luat.trongGio(c, luc(2026, 10, 23, 5, 0)))   // Thứ Sáu 05:00, từ tối Thứ Năm
        assertFalse(Luat.trongGio(c, luc(2026, 10, 23, 22, 0)))  // Thứ Sáu 22:00
        assertFalse(Luat.trongGio(c, luc(2026, 10, 24, 5, 0)))   // Thứ Bảy 05:00
    }

    @Test fun haiKhung_noiTiep() {
        val c = ch(Khung(Luat.phut("14:00"), Luat.phut("16:30"), CA_TUAN), Khung(Luat.phut("16:30"), Luat.phut("17:00"), CA_TUAN))
        assertEquals(17, Luat.hetKhoaLuc(c, luc(2026, 10, 23, 15, 0))!!.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun tatHoacChuaCoPin_khongKhoa() {
        val k = Khung(0, 0, CA_TUAN)
        assertFalse(Luat.trongGio(ch(k, bat = false), luc(2026, 10, 23, 10, 0)))
        assertFalse(Luat.trongGio(CauHinh(true, muoi, "", listOf(k)), luc(2026, 10, 23, 10, 0)))
        assertNull(Luat.hetKhoaLuc(ch(k, bat = false), luc(2026, 10, 23, 10, 0)))
    }

    @Test fun pin() {
        val c = ch(Khung(0, 0, CA_TUAN))
        assertTrue(Luat.dungPin(c, "1234")); assertFalse(Luat.dungPin(c, "1243"))
    }

    @Test fun docJson() {
        val c = Luat.docJson("""{"bat":true,"muoi":"m","pinBam":"x","khung":[{"tu":"21:00","den":"06:30","ngay":[1,2,9]}],"moTamPhut":60}""")!!
        assertEquals(Khung(1260, 390, setOf(1, 2)), c.khung[0]); assertEquals(60, c.moTamPhut)
        assertNull(Luat.docJson("""{"khung":[{"tu":"25:00","den":"06:00"}]}"""))
    }

    @Test fun khoaTungApp() {
        val j = """{"bat":false,"muoi":"abc","pinBam":"${Luat.bam("abc", "1234")}","khung":[],"apps":["com.google.android.youtube.tv"," "]}"""
        val c = Luat.docJson(j)!!
        assertEquals(setOf("com.google.android.youtube.tv"), c.apps)
        assertTrue(Luat.khoaApp(c, "com.google.android.youtube.tv"))
        assertFalse(Luat.khoaApp(c, "com.netflix.ninja"))
        assertFalse(Luat.khoaApp(c, null))
        // Không có PIN thì không khoá app nào; tắt khoá theo giờ (bat=false) không ảnh hưởng khoá app.
        assertFalse(Luat.khoaApp(c.copy(pinBam = ""), "com.google.android.youtube.tv"))
        assertFalse(Luat.trongGio(c, luc(2026, 11, 15, 22, 0)))
    }

    @Test fun cauHinhCu_khongCoApps() { assertTrue(Luat.docJson("""{"bat":true,"khung":[]}""")!!.apps.isEmpty()) }
}
