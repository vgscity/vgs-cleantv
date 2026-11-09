package zone.vgs.tvsach.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Bộ lập kế hoạch với dữ liệu thật (assets/du-lieu.json, 213 mẫu). 22/10/2026: Mi Box S mất mic, Google Home mất tivi. */
class KeHoachTest {
    private val du = DuLieu.docJson(File("src/main/assets/du-lieu.json").readText())

    private fun tivi(mauId: String, them: Set<String> = emptySet()): Pair<MauTv, List<MucGoi>> {
        val mau = du.mau.first { it.id == mauId }
        val caiDat = mau.goi.map { it.pkg }.toSet() + them
        val tt = TrangThaiGoi(caiDat = caiDat, daTat = emptySet(), coBieuTuong = emptySet(), homeHienTai = "", banPhim = emptySet())
        return mau to KeHoach.lap(du, mau, tt)
    }

    @Test fun miBoxS_khongTichSanKatniss() {
        val (_, ds) = tivi("xiaomi-mi-box-s")
        val k = ds.first { it.pkg == "com.google.android.katniss" }
        assertEquals(LoaiMuc.GIONG_NOI, k.loai)
        assertFalse("katniss không được tích sẵn", k.chonSan)
    }

    @Test fun mediashell_luonNhomGoogleHome_khongTichSan() {
        val (_, ds) = tivi("xiaomi-mi-box-s", setOf("com.google.android.apps.mediashell"))
        ds.filter { it.pkg == "com.google.android.apps.mediashell" }.forEach {
            assertEquals(LoaiMuc.GOOGLE_HOME, it.loai); assertFalse(it.chonSan)
        }
    }

    @Test fun nhomTheoYRieng_khongTichSan_conQuangCaoVanTich() {
        var tichQc = 0; var tongTheoY = 0
        for (m in du.mau) {
            val (_, ds) = tivi(m.id)
            for (x in ds) {
                if (Regex("by choice|personal choice|owner's choice", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom)) {
                    tongTheoY++; assertFalse("${m.id} ${x.pkg} (${x.nhom}) không được tích sẵn", x.chonSan)
                }
                if (Regex("^(ads|telemetry)", RegexOption.IGNORE_CASE).containsMatchIn(x.nhom) && x.chonSan) tichQc++
            }
        }
        println("dòng theo ý riêng: $tongTheoY, dòng quảng cáo/theo dõi vẫn tích sẵn: $tichQc")
        assertTrue(tongTheoY > 500); assertTrue(tichQc > 50)
    }

    /** JVC LT-43N7115 treo màn khởi động sau khi tắt Home CVTE (upstream #741): không mẫu nào được đưa Home CVTE vào danh sách tắt. */
    @Test fun homeCvte_khongBaoGioVaoDanhSachTat() {
        var coTrongDuLieu = 0
        for (m in du.mau) {
            if (m.goi.any { KeHoach.laHomeCvte(it.pkg) }) coTrongDuLieu++
            val (_, ds) = tivi(m.id, setOf("com.cvte.tv.launcher.space", "com.cvte.tv.launcher.highend"))
            assertTrue("${m.id}: ${ds.filter { KeHoach.laHomeCvte(it.pkg) }.map { it.pkg }}", ds.none { KeHoach.laHomeCvte(it.pkg) })
        }
        assertTrue("dữ liệu phải có mẫu ghi tắt Home CVTE để bài thử có nghĩa", coTrongDuLieu >= 2)
        assertTrue(KeHoach.laHomeCvte("com.cvte.tv.launcher.space.skin"))
        assertFalse(KeHoach.laHomeCvte("com.cvte.tv.appstore"))
    }

    /** Upstream #884: Mi TV Stick không khởi động lại sau khi tắt gói lõi Android. */
    @Test fun goiLoiAndroid_luonGiu() {
        val tt = TrangThaiGoi(caiDat = emptySet(), daTat = emptySet(), coBieuTuong = emptySet(), homeHienTai = "", banPhim = emptySet())
        listOf("com.google.android.ext.services", "com.android.ext.shared", "com.google.android.modulemetadata",
            "com.google.android.overlay.modules.permissioncontroller", "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.providers.tv").forEach {
            assertTrue(it, KeHoach.laGoiCanGiu(it, tt))
        }
        assertFalse(KeHoach.laGoiCanGiu("com.google.android.feedback", tt))
    }

    /** 4.0.8 (Xiaomi AXSO2, sushil 07/11/2026): launcher gốc có biểu tượng mà mẫu ghi tắt được tích sẵn khi Home sạch đã là Home. */
    @Test fun launcherGocCoBieuTuong_tichSanKhiHomeSachDaLaHome() {
        val pw = "com.mitv.tvhome.atv"
        val goc = du.mau.first { it.id == "xiaomi-mi-box-s" }
        val mau = goc.copy(goi = goc.goi + GoiMau(pw, "Stock launcher", ""))
        fun chon(home: String, coHome: Set<String>) = KeHoach.lap(du, mau,
            TrangThaiGoi(mau.goi.map { it.pkg }.toSet(), emptySet(), coBieuTuong = setOf(pw), homeHienTai = home,
                banPhim = emptySet(), coHome = coHome)).first { it.pkg == pw }.chonSan
        assertTrue("Home sạch đã là Home: tích sẵn", chon("com.gathin.tvhome", setOf(pw)))
        assertFalse("chưa có Home sạch: để chủ nhà chọn", chon("com.google.android.tvlauncher", setOf(pw)))
        assertFalse("không có màn HOME (vd trình duyệt tên ...launcher): không tích", chon("com.gathin.tvhome", emptySet()))
    }
}
