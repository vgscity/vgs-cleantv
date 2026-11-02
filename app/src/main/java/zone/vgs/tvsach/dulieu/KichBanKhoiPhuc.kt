package zone.vgs.tvsach.dulieu

/**
 * Kịch bản khôi phục dùng bằng máy tính (24/10/2026, học cleanyourtv-app "Save a copy"): mất điện thoại, gỡ CleanTV
 * hay app hỏng vẫn đưa được tivi về như cũ chỉ với adb. Mỗi dòng là một lệnh adb thuần, không dùng tính năng riêng
 * của shell nào, nên cùng một file chạy được trên Windows (đuôi .bat) lẫn Mac / Linux (`sh file.bat`). Dòng chú thích
 * bắt đầu bằng ": " — Windows coi là nhãn, sh coi là lệnh rỗng — nên cả hai đều bỏ qua.
 */
object KichBanKhoiPhuc {
    private val HOME_SACH = listOf("com.gathin.tvhome", "com.ts.launcher")
    private const val PKG_KHOA = "zone.vgs.cleantv.khoa"
    private val TEN_GOI = Regex("^[A-Za-z0-9_.]+$")
    private val THANH_PHAN = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    /** [ipLan]: IP mạng của tivi khi sl.ip là 127.0.0.1 (CleanTV chạy ngay trên tivi), máy tính không nối được địa chỉ đó. */
    fun tao(sl: BanSaoLuu, vi: Boolean, ipLan: String? = null): String {
        fun t(v: String, e: String) = if (vi) v else e
        val dong = mutableListOf<String>()
        fun chu(s: String) { dong += ": $s" }
        chu(t("CleanTV: kịch bản khôi phục cho ${sl.ten}", "CleanTV: restore script for ${sl.ten}"))
        chu(t("Cần adb (Android platform-tools). Windows: bấm đúp file này. Mac / Linux: sh tên-file.bat",
            "Needs adb (Android platform-tools). Windows: double-click this file. Mac / Linux: sh file-name.bat"))
        chu(t("Tivi phải bật Gỡ lỗi USB. Sai IP thì sửa dòng adb connect bên dưới.", "USB debugging must be on. If the IP changed, edit the adb connect line below."))
        dong += "adb connect ${ipLan?.takeIf { it.isNotBlank() } ?: sl.ip}:5555"
        val goi = sl.daTatBoiApp.filter { TEN_GOI.matches(it) }
        if (goi.isNotEmpty()) chu(t("Bật lại ${goi.size} gói CleanTV đã tắt", "Re-enable the ${goi.size} packages CleanTV disabled"))
        goi.forEach { dong += "adb shell pm enable --user 0 $it" }
        if (sl.launcherDaCai && THANH_PHAN.matches(sl.homeTruoc) && HOME_SACH.none { sl.homeTruoc.startsWith("$it/") }) {
            chu(t("Trả Home gốc", "Restore the stock Home"))
            dong += "adb shell cmd package set-home-activity --user 0 ${sl.homeTruoc}"
        }
        if (sl.launcherDaCai) {
            chu(t("Trả dịch vụ trợ năng như trước", "Put the accessibility services back as before"))
            val tc = sl.truyCapTruoc
            dong += if (tc.isBlank() || tc == "null" || !Regex("^[A-Za-z0-9_.:/$]+$").matches(tc)) "adb shell settings delete secure enabled_accessibility_services"
            else "adb shell settings put secure enabled_accessibility_services $tc"
            chu(t("Muốn gỡ luôn Home sạch thì bỏ \": \" ở đầu dòng dưới", "To also uninstall the clean Home, remove the \": \" at the start of the next line"))
            HOME_SACH.forEach { chu("adb uninstall $it") }
        }
        if (sl.dnsDaDoi) {
            chu(t("Trả DNS như cũ", "Restore DNS"))
            val mode = sl.dnsModeTruoc.takeIf { it.isNotBlank() && it != "null" && TEN_GOI.matches(it) } ?: "opportunistic"
            dong += "adb shell settings put global private_dns_mode $mode"
            dong += if (sl.dnsHostTruoc.isBlank() || sl.dnsHostTruoc == "null" || !TEN_GOI.matches(sl.dnsHostTruoc)) "adb shell settings delete global private_dns_specifier"
            else "adb shell settings put global private_dns_specifier ${sl.dnsHostTruoc}"
        }
        chu(t("Gỡ Khoá trẻ em nếu có cài (chưa cài thì báo lỗi, không sao)", "Remove the Kids lock if installed (an error here is fine)"))
        dong += "adb uninstall $PKG_KHOA"
        dong += "adb shell input keyevent KEYCODE_HOME"
        chu(t("Xong. Nên tắt Gỡ lỗi USB trên tivi.", "Done. Turn USB debugging off on the TV."))
        return dong.joinToString("\r\n", postfix = "\r\n")
    }
}
