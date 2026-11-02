package zone.vgs.tvsach.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kịch bản khôi phục cho máy tính: mỗi dòng phải là lệnh adb thuần hoặc chú thích ": ", chạy được cả .bat lẫn sh. */
class KichBanKhoiPhucTest {
    private fun sl() = BanSaoLuu(
        khoa = "k", ten = "TCL Smart TV Pro", ip = "192.168.1.20", luc = 0, daTatTruoc = emptySet(),
        homeTruoc = "com.google.android.apps.tv.launcherx/.home.HomeActivity", dnsModeTruoc = "opportunistic", dnsHostTruoc = "null",
        truyCapTruoc = "null", getprop = "", goiTruoc = emptyList(),
        daTatBoiApp = linkedSetOf("com.tcl.ttvs", "com.google.android.apps.tv.launcherx"),
        launcherDaCai = true, dnsDaDoi = true,
    )

    @Test fun moiDongLaLenhAdbHoacChuThich() {
        val ds = KichBanKhoiPhuc.tao(sl(), vi = true).trimEnd().split("\r\n")
        ds.forEach { assertTrue(it, it.startsWith("adb ") || it.startsWith(": ")) }
        // Không có ký tự mà cmd.exe hay sh hiểu khác nhau.
        ds.filter { it.startsWith("adb ") }.forEach { d -> assertFalse(d, d.any { it in "&|<>'\"%;`" }) }
    }

    @Test fun duBuocKhoiPhuc() {
        val s = KichBanKhoiPhuc.tao(sl(), vi = false)
        assertTrue(s.contains("adb connect 192.168.1.20:5555"))
        assertTrue(s.contains("adb shell pm enable --user 0 com.tcl.ttvs"))
        assertTrue(s.contains("adb shell pm enable --user 0 com.google.android.apps.tv.launcherx"))
        assertTrue(s.contains("adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity"))
        assertTrue(s.contains("adb shell settings delete secure enabled_accessibility_services"))
        assertTrue(s.contains("adb shell settings put global private_dns_mode opportunistic"))
        assertTrue(s.contains("adb shell settings delete global private_dns_specifier"))
        // Gỡ Home sạch chỉ là gợi ý, nằm trong dòng chú thích.
        assertTrue(s.contains(": adb uninstall com.gathin.tvhome"))
        assertFalse(s.lines().any { it.trim() == "adb uninstall com.gathin.tvhome" })
    }

    @Test fun chayTrenTiviThiDungIpMang() {
        val s = KichBanKhoiPhuc.tao(sl().copy(ip = "127.0.0.1"), vi = true, ipLan = "192.168.1.45")
        assertTrue(s.contains("adb connect 192.168.1.45:5555"))
        assertFalse(s.contains("127.0.0.1"))
    }

    @Test fun homeTruocLaHomeSachThiKhongDat() {
        val s = KichBanKhoiPhuc.tao(sl().copy(homeTruoc = "com.gathin.tvhome/.MainActivity"), vi = true)
        assertEquals(0, s.lines().count { it.contains("set-home-activity") })
    }
}
