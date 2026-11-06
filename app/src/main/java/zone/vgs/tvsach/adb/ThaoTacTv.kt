package zone.vgs.tvsach.adb

import zone.vgs.tvsach.t
import zone.vgs.tvsach.dulieu.BanSaoLuu
import zone.vgs.tvsach.dulieu.KeHoach
import zone.vgs.tvsach.dulieu.KhoSaoLuu
import zone.vgs.tvsach.dulieu.ThongTinTv
import zone.vgs.tvsach.dulieu.TrangThaiGoi

/**
 * Mọi việc làm trên tivi. Thứ tự và các lớp bảo vệ lấy từ tools/disable.sh và
 * launcher/build-and-install.sh của cleanyourtv; ghi chú "(#số)" là số issue bên đó.
 */
class ThaoTacTv(private val adb: KetNoiAdb, private val kho: KhoSaoLuu) {

    /**
     * Tên gói Home sạch trên tivi này. Tivi Xiaomi bản Trung Quốc (Home gốc com.mitv.tvhome) chỉ nhận Home có tên trong
     * danh sách của hãng: dùng bản dựng tên com.ts.launcher (upstream docs/devices/xiaomi-mitv-aetp0-dofus.md), trừ khi
     * tivi đã có app khác mang tên đó. 22/10/2026, MiTV-ASTP0: bản com.gathin.tvhome cài được nhưng nút Home ra màn đen.
     */
    private var pkgHomeDaDo: String? = null
    val pkgHome: String get() = pkgHomeDaDo ?: run {
        val cuaMinh = sh("dumpsys package $PKG_HOME_XIAOMI | grep -c '$PKG_HOME_XIAOMI/$NS_HOME.MainActivity'").trim().toIntOrNull()?.let { it > 0 } == true
        val coTen = sh("pm path $PKG_HOME_XIAOMI").isNotBlank()
        val xiaomi = sh("pm list packages com.mitv.tvhome").lines().any { it.trim() == "package:com.mitv.tvhome" }
        if (cuaMinh || (xiaomi && !coTen)) PKG_HOME_XIAOMI else PKG_HOME
    }.also { pkgHomeDaDo = it }

    /** Gọi khi kết nối tivi mới: quên những gì đã dò của tivi trước. */
    fun quenTivi() { pkgHomeDaDo = null; cvteDaDo = null }
    val cmpHome get() = "$pkgHome/$NS_HOME.MainActivity"
    /** File APK trong assets cho pkgHome. */
    val apkHome get() = if (pkgHome == PKG_HOME_XIAOMI) "tvhome-xiaomi.apk" else "tvhome.apk"

    /**
     * Cho Home sạch đọc lại cấu hình: dựng lại màn Home (NEW_TASK | CLEAR_TASK) và báo hai dịch vụ bắt phím đọc lại file
     * (reload_config của bản vá). KHÔNG force-stop: force-stop lúc dịch vụ trợ năng của Home đang chạy làm sập
     * system_server trên TCL G06 Android 11, 2/5 lần, có hay không TalkBack (issue #5, brooklyn96, 01/11/2026).
     */
    private val KHOI_DONG_LAI_HOME get() =
            "am start -W -f 0x10008000 -n $cmpHome >/dev/null; am broadcast -a $RELOAD_CONFIG -p $pkgHome >/dev/null"


    private fun sh(lenh: String): String = adb.shell(lenh).replace("\r", "")

    // ---------- Bộ nhớ ----------
    // Tivi dùng lâu đầy bộ nhớ thì đẩy Home sạch vào không được (góp ý TCL, 01/11/2026: hard reset mới cài được).

    /** Chỗ trống của /data (byte trống, tổng byte); null khi không đọc được df. */
    fun boNhoTrong(): Pair<Long, Long>? = runCatching { docDf(sh("df -k /data")) }.getOrNull()

    /** Xoá cache mọi app (pm trim-caches, không đụng dữ liệu hay tài khoản), trả chỗ trống sau khi dọn. */
    fun donBoNhoDem(): Pair<Long, Long>? {
        sh("pm trim-caches 999G")
        return boNhoTrong()
    }

    /**
     * App chiếm nhiều chỗ nhất (dumpsys diskstats, Android tự tính định kỳ nên có thể cũ vài giờ), trừ gói lõi. Cho người
     * dùng gỡ bản cập nhật / xoá dữ liệu ngay trong CleanTV khi tivi hết chỗ (sohuyet8, Sony BRAVIA 0 MB, 02/11/2026).
     */
    fun appChiemCho(): List<AppChiemCho> = runCatching {
        val ds = docDiskstats(sh("dumpsys diskstats"))
        val capNhat = sh("pm list packages -s -f").lines().filter { it.contains("=") && it.substringAfter("package:").startsWith("/data/") }
            .map { it.substringAfterLast('=').trim() }.toSet()
        ds.filter { (pkg, _) -> pkg !in KHONG_DUNG_VAO && !pkg.startsWith("android") && pkg != pkgHome && pkg != PKG_CLEANTV }
            .sortedByDescending { it.second.sum() }.take(6)
            .map { (pkg, so) -> AppChiemCho(pkg, so.sum(), so[1], pkg in capNhat) }
    }.getOrDefault(emptyList())

    /** Trả app hệ thống về bản đi kèm tivi (bỏ bản cập nhật); Android < 10 không có lệnh riêng, pm uninstall làm việc đó. */
    fun goCapNhat(pkg: String): Boolean =
        sh("pm uninstall-system-updates $pkg 2>&1 || pm uninstall $pkg 2>&1").let { it.contains("Success") || !it.contains("Failure") && !it.contains("Unknown") }

    /** Xoá dữ liệu một app (như Cài đặt → Xoá dữ liệu). */
    fun xoaDuLieu(pkg: String): Boolean = sh("pm clear $pkg").contains("Success")

    /** Tivi còn trống dưới mức cần để cài Home sạch (canTrong). Không đọc được df thì coi như đủ. */
    private fun boNhoThap(): Boolean = boNhoTrong()?.let { (trong, tong) -> trong < canTrong(tong) } == true

    /** Lần cài gần nhất hỏng vì tivi thiếu bộ nhớ: màn lỗi hiện thêm nút dọn cache. */
    var thieuBoNho = false
        private set

    // ---------- Đọc ----------

    fun docThongTin(): Pair<ThongTinTv, String> {
        val gp = sh("getprop")
        return ThongTinTv.tuGetprop(gp) to gp
    }

    /**
     * Máy có phải tivi / hộp tivi / máy chiếu không: Android TV khai android.software.leanback hoặc
     * android.hardware.type.television. Điện thoại không có cả hai (26/10/2026: một người dọn nhầm chính điện thoại,
     * máy không khởi động được). Lệnh lỗi thì coi như tivi để không chặn nhầm.
     */
    fun laTivi(): Boolean {
        val f = runCatching { sh("pm list features") }.getOrDefault("")
        if (!f.contains("feature:")) return true
        return f.contains("feature:android.software.leanback") || f.contains("feature:android.hardware.type.television")
    }

    /**
     * Dò một nút remote (3.1.1, "Dò phím remote"): nhờ HomeKeyService của Home sạch học phím kế tiếp (broadcast LEARN_KEY,
     * nó ghi logcat CleanTVKey "key=… scan=…" rồi nuốt phím), song song đọc getevent làm dự phòng cho tivi chưa lọc phím.
     * Trả về (keycode, scancode), keycode 0 khi chỉ có getevent; null nếu hết giờ không thấy phím.
     */
    fun docPhimRemote(giay: Int = 20, conDung: () -> Boolean = { true }): Pair<Int, Int>? {
        val tam = "/data/local/tmp/cleantv-phim"
        sh("logcat -c 2>/dev/null; rm -f $tam; am broadcast -a zone.vgs.cleantv.LEARN_KEY -p $pkgHome >/dev/null 2>&1; " +
            "(timeout $giay getevent -q 2>/dev/null | grep -m1 -E ' 0001 [0-9a-f]{4} 00000001' > $tam) >/dev/null 2>&1 & true")
        val het = System.currentTimeMillis() + giay * 1000L
        while (System.currentTimeMillis() < het && conDung()) {
            Thread.sleep(800)
            Regex("""key=(\d+) scan=(\d+)""").find(sh("logcat -d -s CleanTVKey:I 2>/dev/null | tail -1"))?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
            Regex(""" 0001 ([0-9a-f]{4}) 00000001""").find(sh("cat $tam 2>/dev/null"))?.let { return 0 to it.groupValues[1].toInt(16) }
        }
        return null
    }

    /** Múi giờ tivi đang dùng (vd Asia/Shanghai trên tivi Xiaomi / Redmi nội địa), "" nếu không đọc được. */
    fun muiGioTv(): String = runCatching { sh("getprop persist.sys.timezone").trim() }.getOrDefault("")

    /**
     * Đặt múi giờ tivi (3.0.1, Redmi nội địa giờ Bắc Kinh nhanh hơn Việt Nam 1 tiếng mà giấu mục chỉnh giờ): tắt tự dò
     * múi giờ rồi gọi IAlarmManager.setTimeZone (mã 3) qua service call, shell có quyền SET_TIME_ZONE. Trả về múi giờ sau
     * khi đặt để bên gọi so lại.
     */
    fun datMuiGio(tz: String): String {
        if (!Regex("[A-Za-z_]+(/[A-Za-z0-9_+-]+){1,2}").matches(tz)) return muiGioTv()
        sh("settings put global auto_time_zone 0; service call alarm 3 s16 $tz >/dev/null 2>&1; true")
        return muiGioTv()
    }

    fun docTrangThai(): TrangThaiGoi {
        fun goi(lenh: String) = sh(lenh).lines().mapNotNull { l ->
            l.trim().removePrefix("package:").takeIf { it.isNotEmpty() && TEN_GOI.matches(it) }
        }.toSet()
        val caiDat = goi("pm list packages")
        val daTat = goi("pm list packages -d")
        val coBieuTuong = (lopActivity("android.intent.category.LEANBACK_LAUNCHER") +
            lopActivity("android.intent.category.LAUNCHER"))
        val banPhim = sh("ime list -s; settings get secure default_input_method").lines()
            .mapNotNull { it.trim().substringBefore('/').takeIf { p -> TEN_GOI.matches(p) } }.toSet()
        return TrangThaiGoi(caiDat, daTat, coBieuTuong, homeHienTai().substringBefore('/'), banPhim)
    }

    /** Các gói có activity thuộc category này (app hiện biểu tượng). */
    private fun lopActivity(cat: String): Set<String> =
        sh("cmd package query-activities --brief -a android.intent.action.MAIN -c $cat").lines()
            .mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/""").find(it)?.groupValues?.get(1) }.toSet()

    /** Vài dòng chẩn đoán Home (gửi kèm danh sách khi người dùng bấm gửi, 23/10/2026): Home mặc định, app nhận Home, màn đang hiện. */
    /** 3.5.3 (Võ Thái TCL 43P618, Đức Anh Google TV, 27/10/2026): thêm "# boot" — tivi lên bao lâu, Home sạch có bị buộc dừng
     *  (stopped=true thì không nhận BOOT_COMPLETED), quyền mở đè / ghi cài đặt, và log CleanTVBoot của launcher lúc khởi động.
     *  Người dùng rút điện bật lại rồi kết nối và gửi ngay, không bấm Áp dụng, thì đây là trạng thái thật sau khởi động.
     *  3.5.5: log TCL bị xoay mất sau 9 phút, nên launcher ghi thêm dấu vào settings global cleantv_boot (BootHome.note). */
    fun chanDoanHome(goi: String = PKG_HOME): String = runCatching {
        sh("echo '# boot'; cat /proc/uptime | cut -d' ' -f1; settings get global cleantv_boot; settings get global boot_count; dumpsys package $goi 2>/dev/null | grep -m1 -o 'stopped=[a-z]*'; " +
            "appops get $goi SYSTEM_ALERT_WINDOW 2>&1 | head -1; dumpsys package $goi 2>/dev/null | grep -m1 -o 'WRITE_SECURE_SETTINGS: granted=[a-z]*'; " +
            "logcat -d -v brief -s CleanTVBoot:* 2>/dev/null | grep CleanTVBoot | tail -6 | cut -c1-120; " +
            "echo '# resolve'; $HOME_QUERY 2>&1 | tail -2; echo '# query'; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | grep '/' | head -8; " +
            "echo '# focus'; dumpsys window 2>/dev/null | grep -m2 -E 'mCurrentFocus|mFocusedApp'; echo '# a11y'; settings get secure enabled_accessibility_services")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            .let { if (loiGhiFile.isNotEmpty()) "# ghi-file\n$loiGhiFile\n$it" else it }.take(2700)
    }.getOrDefault("")

    fun homeHienTai(): String = sh(HOME_QUERY).lines().lastOrNull { it.isNotBlank() }?.trim() ?: ""

    fun chupManHinh(): ByteArray = adb.chupPng()

    // ---------- Máy chiếu ----------

    /** Có dấu hiệu là máy chiếu: tên máy hoặc tên gói kiểu focus / keystone / hãng máy chiếu. */
    fun coVeMayChieu(tt: TrangThaiGoi, tv: ThongTinTv): Boolean =
        TU_MAY_CHIEU.containsMatchIn("${tv.brand} ${tv.manufacturer} ${tv.model} ${tv.device}") ||
            tt.caiDat.any { TU_MAY_CHIEU.containsMatchIn(it) }

    /**
     * Dò các màn (activity) của hãng có tên giống lấy nét / chỉnh hình / thu phóng trong mọi gói hệ thống.
     * Một lệnh shell chạy vòng lặp trên chính máy chiếu cho nhanh.
     */
    fun doMayChieu(): List<String> {
        val loc = "focus|keystone|trapez|tixing|jiaozheng|duijiao|correct|projector|projection|zoom|scale|calibr|geometr|ladder|picturesize|screensize"
        val out = sh("for p in \$(pm list packages -s | cut -d: -f2 | grep -v ^com.google); do dumpsys package \$p | grep -oE \"\$p/[A-Za-z0-9_.\\\$]+\" ; done | sort -u | grep -iE '$loc'; true")
        return out.lines().map { it.trim() }.filter { it.contains('/') && TEN_GOI.matches(it.substringBefore('/')) }
            // Chỉ lấy màn (activity): bỏ dịch vụ, bộ nhận, nhà cung cấp — không mở được bằng am start.
            .filterNot { Regex("(Service|Receiver|Provider)$").containsMatchIn(it) }.distinct().take(40)
    }

    /** Mở thử một màn của hãng; trả lỗi nếu Android từ chối. */
    fun moThu(comp: String): String? {
        val out = sh("am start -n '$comp' 2>&1")
        return if (out.contains("Error") || out.contains("Exception")) out.trim().lines().lastOrNull() else null
    }

    // ---------- Sao lưu ----------

    /** Lấy bản sao lưu đã có của tivi này, chưa có thì chụp trạng thái hiện tại và ghi xuống máy. */
    /**
     * Ghi bản sao lưu vào điện thoại VÀ một bản lên chính tivi ([TEP_SAO_LUU_TV]), để gỡ app / đổi điện thoại vẫn khôi
     * phục được (22/10/2026). Bản trên tivi bỏ getprop + danh sách gói (chỉ để tham khảo) cho nhẹ: ghi lại sau mỗi gói.
     * Ghi file tạm rồi mv, mất kết nối giữa chừng không hỏng bản cũ. Lỗi ghi lên tivi không chặn việc đang làm.
     */
    fun luuSaoLuu(b: BanSaoLuu) {
        kho.ghi(b)
        runCatching {
            val gon = b.copy(getprop = "", goiTruoc = emptyList()).toJson().toString().toByteArray()
            adb.ghiFile("$TEP_SAO_LUU_TV.tam", gon)
            sh("mv -f $TEP_SAO_LUU_TV.tam $TEP_SAO_LUU_TV")
        }
    }

    /** Bản sao lưu nằm trên tivi (do điện thoại nào cũng được ghi), đúng tivi này; null nếu không có / hỏng. */
    fun docSaoLuuTrenTv(khoa: String): BanSaoLuu? = runCatching {
        BanSaoLuu.tuJson(org.json.JSONObject(sh("cat $TEP_SAO_LUU_TV 2>/dev/null").trim()))
    }.getOrNull()?.takeIf { it.khoa == khoa }

    fun damBaoSaoLuu(tv: ThongTinTv, getprop: String, ip: String): BanSaoLuu {
        (kho.doc(tv.khoaLuu) ?: docSaoLuuTrenTv(tv.khoaLuu))?.let { cu -> cu.ip = ip; luuSaoLuu(cu); return cu }
        val tt = docTrangThai()
        val b = BanSaoLuu(
            khoa = tv.khoaLuu, ten = tv.tenHienThi, ip = ip, luc = System.currentTimeMillis(),
            daTatTruoc = tt.daTat, homeTruoc = homeHienTai(),
            dnsModeTruoc = sh("settings get global private_dns_mode").trim(),
            dnsHostTruoc = sh("settings get global private_dns_specifier").trim(),
            truyCapTruoc = sh("settings get secure enabled_accessibility_services").trim(),
            getprop = getprop, goiTruoc = tt.caiDat.sorted(),
        )
        luuSaoLuu(b)
        return b
    }

    // ---------- Tắt gói ----------

    data class KetQuaTat(val daTat: List<String>, val boQua: List<Pair<String, String>>, val loi: List<Pair<String, String>>)

    /**
     * Lọc qua các lớp bảo vệ rồi tắt từng gói một (mỗi lệnh pm một lần gọi, như PROMPT.md dặn).
     * [epSetupwraith]: tivi chạy dây mạng, cho tắt setupwraith dù nó giữ Wi-Fi.
     */
    /**
     * Gói lõi đã lỡ tắt từ trước (bản cũ chưa chặn phần mềm lõi MediaTek: 6 danh sách gửi về có com.cltv.mal,
     * com.mediatek.tis hoặc tv.service tắt, 28/10/2026) thì bật lại, kẻo lần khởi động sau tivi treo như Haier #1578.
     * Chạy khi dọn và khi Áp dụng. Trả các gói đã bật lại, null khi không có.
     */
    fun batLaiGoiLoi(bao: (String) -> Unit): List<String>? {
        val ds = sh("pm list packages -d").lines().map { it.substringAfter("package:").trim() }
            .filter { zone.vgs.tvsach.dulieu.KeHoach.laGoiLoiAndroid(it) }
        ds.forEach { p ->
            bao(t("Bật lại gói lõi $p…", "Re-enabling core package $p…"))
            sh("pm enable --user 0 $p")
        }
        return ds.ifEmpty { null }
    }

    fun tatGoi(
        goi: List<String>,
        sl: BanSaoLuu,
        epSetupwraith: Boolean,
        bao: (String) -> Unit,
    ): KetQuaTat {
        val boQua = mutableListOf<Pair<String, String>>()
        var ds = goi.filter { TEN_GOI.matches(it) }.distinct()

        // Chốt cuối như repo CleanYourTV: không bao giờ tắt gói lõi Android (#884) hay gói đang giữ nút Home (Home guard).
        val homeNay = runCatching { homeHienTai().substringBefore('/') }.getOrDefault("")
        ds.filter { zone.vgs.tvsach.dulieu.KeHoach.laGoiLoiAndroid(it) }.forEach {
            boQua += it to t("Gói lõi của Android hoặc của tivi: tắt có thể làm tivi không khởi động lại được.", "Core Android or TV package: disabling it can stop the TV from booting.")
        }
        batLaiGoiLoi(bao)?.let { p -> sl.daTatBoiApp.removeAll(p.toSet()) }
        if (homeNay.isNotBlank() && homeNay in ds) boQua += homeNay to t("Đang là màn Home của tivi: tắt là không còn Home.", "It is the TV's Home right now: disabling it leaves no Home.")
        ds = ds.filter { p -> boQua.none { it.first == p } }

        // setupwraith: Wi-Fi lưu lúc cài đặt ban đầu thuộc về nó; tắt nó là Android xoá mạng đó và
        // tivi rớt Wi-Fi, mất luôn kết nối này.
        if (SETUP in ds && !epSetupwraith) {
            bao(t("Kiểm tra ai giữ mạng Wi-Fi đã lưu…", "Checking which app owns the saved Wi-Fi…"))
            // Chỉ xét mạng ĐANG kết nối (23/10/2026, Sony VU3: quên + vào lại mạng nhà rồi mà vẫn bị chặn, vì tivi còn lưu
            // mạng cũ do setupwraith tạo). Không đọc được mạng đang dùng thì quay về cách cũ: xét mọi mạng đã lưu.
            val dump = sh("dumpsys wifi")
            val chuDangDung = chuWifiDangDung(dump)
            val dayLan = chuDangDung == null && sh("cat /sys/class/net/eth0/operstate 2>/dev/null").trim() == "up"
            val chu = when {
                dayLan -> "lan"
                chuDangDung != null -> chuDangDung.ifBlank { Regex("cname=[^ ]*").findAll(dump).joinToString(" ") { it.value } }
                else -> Regex("cname=[^ ]*").findAll(dump).joinToString(" ") { it.value }
            }
            val lyDo = when {
                chu.isBlank() -> t("Không đọc được mạng Wi-Fi đã lưu thuộc app nào, giữ lại cho chắc.", "Could not tell which app owns the saved Wi-Fi, keeping it to be safe.")
                chu.contains("setupwraith") -> t("Wi-Fi đang lưu thuộc về nó: tắt là tivi rớt mạng. Vào Cài đặt > Mạng, ", "It owns the saved Wi-Fi: disabling it drops the TV off the network. In Settings > Network, ") +
                    t("quên mạng rồi kết nối lại, sau đó chạy lại.", "forget the network and join it again, then run this again.")
                else -> null
            }
            if (lyDo != null) { boQua += SETUP to lyDo; ds = ds - SETUP }
        }

        // Không tắt app đang làm màn Home khi không còn app nào khác nhận Home (#239).
        val home = homeHienTai().substringBefore('/')
        if (home.isNotEmpty() && home in ds) {
            val khac = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lines().mapNotNull { Regex("""^\s*([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)""").find(it) }
                .filterNot { it.groupValues[2].endsWith("FallbackHome") }
                .map { it.groupValues[1] }.toSet() - ds.toSet() - home - KHONG_PHAI_HOME
            if (khac.isEmpty()) {
                boQua += home to t("Đang là màn Home và chưa có Home nào khác. Cài Home sạch trước rồi mới tắt.", "It is the Home screen and nothing else can take Home. Install the clean Home first.")
                ds = ds - home
            }
        }

        // Gói xin quyền TV_INPUT_HARDWARE là lớp phần mềm dưới cổng HDMI/AV/ăng-ten: tắt là cổng
        // báo "Không có tín hiệu" mãi, bật lại từng gói cũng không cứu được (#321).
        if (ds.isNotEmpty()) {
            bao(t("Kiểm tra gói nào điều khiển cổng HDMI…", "Checking which packages drive the HDMI inputs…"))
            val ten = ds.joinToString(" ")
            val hw = sh(
                "for p in $ten; do dumpsys package \$p | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' " +
                    "| grep -q TV_INPUT_HARDWARE && echo \$p; done; true"
            ).lines().map { it.trim() }.filter { it in ds }
            hw.forEach { boQua += it to t("Điều khiển cổng HDMI/AV/ăng-ten (TV_INPUT_HARDWARE): tắt là mất hình.", "Drives the HDMI/AV/tuner inputs (TV_INPUT_HARDWARE): disabling it means no picture.") }
            ds = ds - hw.toSet()
        }

        // App MỞ cổng HDMI / kênh (nhận VIEW vnd.android.cursor.item/channel) hay mở Cài đặt: tắt là
        // Home sạch báo "TV này không hỗ trợ" khi chọn HDMI, nút Cài đặt không mở (22/10/2026: Sony KDL-43W800G mất HDMI,
        // Sony W800C không vào Cài đặt). Hỏi chính tivi đang dùng app nào rồi giữ, không đoán theo tên.
        if (ds.isNotEmpty()) {
            val q = "cmd package query-activities --brief"
            val giuHdmi = goiTrongDong(sh("$q -a android.intent.action.VIEW -t vnd.android.cursor.item/channel 2>/dev/null; true"))
            val giuCaiDat = goiTrongDong(sh("$q -a android.settings.SETTINGS 2>/dev/null; $q -a android.intent.action.MAIN -c android.intent.category.PREFERENCE 2>/dev/null; true"))
            ds.filter { it in giuHdmi }.forEach { boQua += it to t("Tivi dùng app này để mở cổng HDMI / kênh truyền hình: tắt là chọn HDMI báo không hỗ trợ.", "The TV opens HDMI inputs / channels with this app: disabling it breaks input switching.") }
            ds.filter { it in giuCaiDat && it !in giuHdmi }.forEach { boQua += it to t("Tivi dùng app này để mở Cài đặt: tắt là nút Cài đặt không mở được.", "The TV opens Settings with this app: disabling it breaks the Settings button.") }
            ds = ds - giuHdmi - giuCaiDat
        }

        val daTat = mutableListOf<String>()
        val loi = mutableListOf<Pair<String, String>>()
        val heThong0 = pidHeThong()
        ds.forEachIndexed { i, p ->
            bao(t("Tắt ${i + 1}/${ds.size}: $p", "Disabling ${i + 1}/${ds.size}: $p"))
            var matKetNoi = false
            var out = try {
                sh("pm disable-user --user 0 $p")
            } catch (e: Exception) {
                if (!laMatKetNoi(e)) t("lỗi: ${e.message}", "error: ${e.message}") else { matKetNoi = true; "" }
            }
            // Tivi đang tắt máy: ADB còn trả lời nhưng dịch vụ gói đã chết ("Failure calling service package: Broken pipe").
            if (DICH_VU_CHET.containsMatchIn(out)) matKetNoi = true
            if (matKetNoi) {
                // 22/10/2026, Sony KD-55X8500D: rớt ADB giữa lúc dọn, app báo "connect() must be called first" rồi
                // dừng. Chờ tivi trả lời lại; tivi vừa khởi động lại thì dừng hẳn, gói vừa tắt là nghi phạm.
                bao(t("Mất kết nối với tivi, đang chờ tivi trả lời lại…", "Lost the connection to the TV, waiting for it to answer…"))
                if (!choTiviTraLoi(bao)) throw MatKetNoiTvException(daTat.size, p)
                val daTatRoi = sh("pm list packages -d").lines().any { it.trim() == "package:$p" }
                if (daTatRoi) { daTat += p; if (p !in sl.daTatTruoc) sl.daTatBoiApp += p; luuSaoLuu(sl) }
                val heThong = pidHeThong()
                if (heThong0 != null && heThong != null && heThong != heThong0)
                    throw TiviKhoiDongLaiException(if (daTatRoi) p else daTat.lastOrNull() ?: p, daTat.size)
                out = if (daTatRoi) "new state: disabled-user" else runCatching { sh("pm disable-user --user 0 $p") }.getOrElse { "lỗi: ${it.message}" }
                if (daTatRoi) return@forEachIndexed
            }
            if (out.contains("disabled-user") || out.contains("new state: disabled")) {
                daTat += p
                if (p !in sl.daTatTruoc) sl.daTatBoiApp += p
                luuSaoLuu(sl) // ghi sau MỖI gói: rớt mạng giữa chừng vẫn khôi phục đúng.
            } else {
                loi += p to out.trim().lines().lastOrNull().orEmpty()
            }
        }
        return KetQuaTat(daTat, boQua, loi)
    }

    /** Tên gói trong kết quả `query-activities --brief` (dòng dạng gói/lớp). */
    private fun goiTrongDong(out: String): Set<String> =
        out.lines().mapNotNull { Regex("""^\s*([A-Za-z_][A-Za-z0-9_.]*)/""").find(it)?.groupValues?.get(1) }.filter { TEN_GOI.matches(it) }.toSet()

    /**
     * PID của system_server: đổi = tivi vừa khởi động lại, kể cả kiểu chỉ khởi động lại phần mềm (tắt nhầm gói làm
     * system_server chết) mà /proc/uptime không về 0.
     */
    private fun pidHeThong(): String? = runCatching { sh("pidof system_server").trim().ifBlank { null } }.getOrNull()

    /**
     * Chờ tivi trả lời lại tới mức dịch vụ gói chạy được: tối đa 12 lần × 8 giây (~1,5 phút, đủ cho tivi khởi động
     * lại xong). Lệnh lỗi kiểu mất kết nối thì nối lại ADB.
     */
    private fun choTiviTraLoi(bao: (String) -> Unit): Boolean {
        repeat(12) { lan ->
            Thread.sleep(8000)
            bao(t("Chờ tivi, lần ${lan + 1}/12…", "Waiting for the TV, try ${lan + 1}/12…"))
            val ok = try { sh("pm path android").contains("package:") } catch (e: Exception) { runCatching { adb.ketNoiLai() }; false }
            if (ok) return true
        }
        return false
    }

    // ---------- Home sạch (launcher) ----------

    /**
     * Cài launcher và giao màn Home cho nó, theo đúng thứ tự của build-and-install.sh:
     * cài → mở thử → tắt Home gốc → đặt Home → kiểm tra, sai thì trả Home gốc lại.
     */
    fun caiLauncher(
        apk: ByteArray,
        sl: BanSaoLuu,
        cauHinh: String,
        anhNen: ByteArray?,
        boAnh: List<Pair<String, ByteArray>>?,
        nutInput: Boolean,
        nutHome: Boolean,
        apkXiaomi: (() -> ByteArray)? = null,
        bao: (String) -> Unit,
    ): String? {
        val home = homeHienTai().substringBefore('/')
        val homeGoc = when {
            home == pkgHome || home in HOME_SACH -> "none"
            // Tivi đang không có Home nào (Home gốc bị tắt từ trước, Android rơi về FallbackHome của Cài đặt — 23/10/2026,
            // Xiaomi MiTV-AXSP0): không có gì để tắt, cài Home sạch thẳng.
            home == "com.android.tv.settings" || home == "com.android.settings" -> "none"
            home in HOME_GOC_BIET -> home
            home == "android" -> {
                val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                val tim = HOME_GOC_BIET.filter { ung.contains("$it/") }
                if (tim.size == 1) tim[0] else return t("Tivi đang hỏi chọn Home (có ${tim.size} Home gốc). Chọn một Home trên tivi rồi thử lại.", "The TV is asking which Home to use (${tim.size} stock launchers). Pick one on the TV and try again.")
            }
            home == SETUP -> return t("Home đang là trình cài đặt của Google TV (setupwraith). Xem lại phần tắt gói: setupwraith phải tắt cùng Home gốc.", "Home is the Google TV setup wizard (setupwraith). Disable setupwraith together with the stock Home.")
            laHomeHangLa(home) -> home
            // Launcher CÀI THÊM (Dangbei trên tivi nội địa Trung, Projectivy, ATV Launcher…): không thuộc phần mềm gốc,
            // thay được — giữ nó bật, đặt Home sạch làm mặc định; nút Home vẫn về nó thì mới tắt (ghi sao lưu). 22/10/2026.
            laLauncherCaiThem(home) -> home
            else -> return t("Home hiện là '$home', không phải Home gốc app biết, nên app để nguyên cho chắc.", "Home is '$home', not a stock launcher this app knows. Leaving it alone to be safe.")
        }
        // Home của hãng lạ (Fengmi, máy chiếu, tivi nội địa…): thử giữ nó bật, chỉ đặt Home mặc định + bắt nút Home trên
        // remote; không được mới tắt (21/10/2026, Formovie Nemo: com.tvfengmi.launcher).
        val hangLa = homeGoc != "none" && homeGoc !in HOME_GOC_BIET
        // Bo mạch CVTE: chỉ đặt Home mặc định, không tắt Home gốc, không bật dịch vụ bắt phím cùng lượt (upstream #741).
        val cvte = laBoMachCvte()
        val giuHomeGoc = KeHoach.laHomeCvte(homeGoc)

        thieuBoNho = boNhoThap()
        if (thieuBoNho) return baoThieuBoNho(boNhoTrong())
        bao(t("Đẩy và cài Home sạch (${apk.size / 1024} KB)…", "Pushing and installing the clean Home (${apk.size / 1024} KB)…"))
        var out = caiApkHome(apk, bao)
        if (!out.contains("Success")) {
            // Dự phòng: đẩy file rồi pm install (Android 14 đôi khi treo kiểu cài luồng).
            bao(t("Cài kiểu luồng không được, thử đẩy file…", "Streamed install failed, trying a file push…"))
            if (!out.contains("TIMEOUT") && !THIEU_CHO.containsMatchIn(out)) {
                adb.ghiFile("/data/local/tmp/tvhome.apk", apk)
                out = voiHan(120) { sh("pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk") }
            }
        }
        // Thiếu chỗ thì kiểu cài luồng treo tới hết hạn, không in lỗi gì (máy ảo còn 240 MB, 01/11/2026): đo lại trước khi
        // đổ cho Play Protect.
        if (out.contains("TIMEOUT") && boNhoThap()) { thieuBoNho = true; return baoThieuBoNho(boNhoTrong()) }
        if (out.contains("TIMEOUT")) return t("Cài Home sạch quá 2 phút chưa xong: Android có thể đang hỏi xác minh ứng dụng (Play Protect) mà màn hỏi bị che. Vào Tuỳ chọn nhà phát triển, tắt \"Xác minh ứng dụng qua USB\" (hoặc tắt quét của Play Protect trong CH Play), rồi thử lại. Home gốc vẫn nguyên.",
            "Installing the clean Home took over 2 minutes: Android may be showing an app-verification prompt (Play Protect) behind the app. In Developer options turn off \"Verify apps over USB\" (or Play Protect scanning), then retry. The stock Home is untouched.")
        if (!out.contains("Success") && THIEU_CHO.containsMatchIn(out)) {
            thieuBoNho = true
            return baoThieuBoNho(boNhoTrong())
        }
        if (!out.contains("Success")) {
            return if (out.contains("UPDATE_INCOMPATIBLE"))
                t("Tivi đã có Home sạch ký bằng khoá khác (cài từ máy khác). Gỡ bản cũ trước: pm uninstall $pkgHome.", "The TV has a clean Home signed with another key (installed from another computer). Uninstall it first: pm uninstall $pkgHome.")
            else t("Cài không được: ${out.trim().lines().lastOrNull()}. Home gốc vẫn nguyên.", "Install failed: ${out.trim().lines().lastOrNull()}. The stock Home is untouched.")
        }
        sl.launcherDaCai = true; luuSaoLuu(sl)

        bao(t("Mở thử Home sạch…", "Starting the clean Home…"))
        val start = sh("am start -W -n $cmpHome")
        if (!start.contains("Status: ok") || start.lines().any { it.startsWith("Error") }) {
            // Xiaomi gỡ ngay launcher không có tên trong danh sách của hãng ("Activity class … does not exist" ngay sau
            // Success; góp ý anhntcn, MiTV-MOOR2 26/10/2026): cài lại bằng bản tên com.ts.launcher mà hãng cho phép.
            val xiaomi = Regex("xiaomi|redmi", RegexOption.IGNORE_CASE).containsMatchIn(sh("getprop ro.product.brand; getprop ro.product.manufacturer"))
            if (apkXiaomi != null && pkgHome == PKG_HOME && xiaomi && start.contains("does not exist")) {
                bao(t("Tivi Xiaomi gỡ mất Home sạch, cài lại bằng tên Xiaomi cho phép…", "The Xiaomi TV removed the clean Home; reinstalling under a name Xiaomi allows…"))
                pkgHomeDaDo = PKG_HOME_XIAOMI
                return caiLauncher(apkXiaomi(), sl, cauHinh, anhNen, boAnh, nutInput, nutHome, null, bao)
            }
            return t("Home sạch không mở được, Home gốc vẫn giữ nguyên.\n$start", "The clean Home did not start; the stock Home is untouched.\n$start")
        }
        sh("pm grant $pkgHome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; appops set $pkgHome SCHEDULE_EXACT_ALARM allow 2>/dev/null; true")
        bao(t("Tối ưu cho tivi yếu…", "Optimising for slow TVs…"))
        runCatching { sh("cmd package compile -m speed -f $pkgHome") }

        fun tatHomeGoc() {
            bao(t("Tắt Home gốc ($homeGoc)…", "Disabling the stock Home ($homeGoc)…"))
            gacThem(homeGoc)
            sh("pm disable-user --user 0 $homeGoc")
            sl.homeGocDaTat = homeGoc
            if (homeGoc !in sl.daTatTruoc) sl.daTatBoiApp += homeGoc
            luuSaoLuu(sl)
        }
        if (homeGoc != "none" && !hangLa && !giuHomeGoc) tatHomeGoc()
        sh("cmd package set-home-activity --user 0 $cmpHome")
        if (hangLa && !giuHomeGoc) {
            // Bắt nút Home trên remote về Home sạch dù hãng có dịch vụ bắt phím riêng (bo CVTE: không, xem trên).
            if (!cvte) batDichVu("HomeKeyService")
            if (!homeHienTai().startsWith("$pkgHome/")) {
                bao(t("Home của hãng vẫn giữ quyền, tắt nó…", "The maker's Home still wins, disabling it…"))
                tatHomeGoc()
                sh("cmd package set-home-activity --user 0 $cmpHome")
            }
        }

        var bayGio = homeHienTai()
        // Hộp "chọn Home" (android/ResolverActivity): tivi còn launcher người dùng tự cài (Launchpad TV, Home TV…) tranh Home,
        // set-home-activity không thắng được (25/10/2026, Xiaomi ASTR0 nội địa: app trả Home gốc mỗi lần cài). Tắt các
        // launcher tự cài đó (ghi vào sao lưu, Khôi phục bật lại) rồi đặt lại Home sạch.
        if (bayGio.startsWith("android/") && !giuHomeGoc) {
            val khac = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lines().map { it.trim().substringBefore('/') }.filter { it.isNotBlank() && it != pkgHome }.distinct().filter { laLauncherCaiThem(it) }
            if (khac.isNotEmpty()) {
                bao(t("Tivi có launcher khác tranh Home (${khac.joinToString()}), tắt để Home sạch làm Home…", "Other launchers compete for Home (${khac.joinToString()}), disabling them…"))
                khac.forEach { p ->
                    gacThem(p)
                    sh("pm disable-user --user 0 $p")
                    if (p !in sl.daTatTruoc && p !in sl.daTatBoiApp) sl.daTatBoiApp += p
                }
                luuSaoLuu(sl)
                sh("cmd package set-home-activity --user 0 $cmpHome")
                Thread.sleep(800)
                bayGio = homeHienTai()
            }
        }
        // Màn cài đặt lần đầu của AOSP (com.android.provision/.DefaultActivity) còn bật và nhận Home khi Home gốc vừa tắt
        // (góp ý anhht87vn, RockChip TV98 27/10/2026). Tivi đã dùng rồi nên đánh dấu cài xong như chính màn đó làm, tắt nó
        // (Khôi phục bật lại), đặt lại Home sạch.
        if (bayGio.startsWith("com.android.provision/") && !giuHomeGoc) {
            bao(t("Màn cài đặt lần đầu của Android tranh Home, tắt nó…", "Android's first-run setup screen claims Home, disabling it…"))
            sh("settings put global device_provisioned 1; settings put secure user_setup_complete 1; pm disable-user --user 0 com.android.provision")
            if ("com.android.provision" !in sl.daTatTruoc && "com.android.provision" !in sl.daTatBoiApp) sl.daTatBoiApp += "com.android.provision"
            luuSaoLuu(sl)
            sh("cmd package set-home-activity --user 0 $cmpHome")
            Thread.sleep(800)
            bayGio = homeHienTai()
        }
        if (!bayGio.startsWith("$pkgHome/") && giuHomeGoc) {
            traHomeGoc(sl)
            return t("Tivi bo mạch CVTE ('${bayGio.ifBlank { "không rõ" }}' vẫn nhận Home). Với loại bo này app không tắt Home gốc vì đã có tivi treo ở màn khởi động sau khi tắt. Tivi vẫn như cũ; Home sạch vẫn mở được từ danh sách ứng dụng.",
                "CVTE board ('${bayGio.ifBlank { "unknown" }}' still claims Home). On this board the app never disables the stock Home: one TV hung on its boot animation after that. The TV is as before; open the clean Home from the app list.")
        }
        if (!bayGio.startsWith("$pkgHome/")) {
            bao(t("Home không chuyển được, trả lại Home gốc…", "Home did not switch, restoring the stock Home…"))
            traHomeGoc(sl)
            // Google TV còn setupwraith (2.6.2): Home gốc đã trả lại, vẫn ghi cấu hình rồi để thuPhimHome cho Home sạch mở đè
            // (damBaoPhimHome → chuyenDe); không được thì nó báo cách quên Wi-Fi.
            if (!(bayGio.startsWith("$SETUP/") || dangBat(SETUP)))
                return t("Home sau khi đổi là '${bayGio.ifBlank { "không rõ" }}'. Đã trả Home gốc, tivi vẫn như cũ. Gửi góp ý kèm hãng, model để VGS thêm hỗ trợ.",
                "Home resolved to '${bayGio.ifBlank { "unknown" }}'. The stock Home is back, the TV is as before. Send feedback with the brand and model so VGS can add support.")
        }

        // Xiaomi: bản com.gathin.tvhome cài từ trước (bị hãng chặn) thì gỡ, để không còn hai Home sạch.
        if (pkgHome != PKG_HOME && sh("pm path $PKG_HOME").isNotBlank()) {
            bao(t("Gỡ Home sạch bản cũ ($PKG_HOME)…", "Removing the old clean Home ($PKG_HOME)…"))
            sh("pm uninstall $PKG_HOME")
        }
        bao(t("Ghi cấu hình dock…", "Writing the dock settings…"))
        dayCauHinh(cauHinh, anhNen, boAnh)
        if (cvte && (nutInput || nutHome)) {
            bao(t("Tivi bo mạch CVTE: chưa bật bắt phím remote lượt này. Tắt mở tivi một lần, thấy Home sạch lên bình thường rồi vào Đổi giao diện bật lại.",
                "CVTE board: remote-key helpers stay off this pass. Restart the TV once and, once the clean Home comes up, turn them on under Change look."))
        } else {
            if (nutInput) batDichVu("InputKeyService")
            if (nutHome) batDichVu("HomeKeyService")
        }
        return null
    }

    /**
     * Cài Home sạch; tivi đã có Home sạch ký khoá khác (dọn bằng CleanYourTV gốc / Claude, mỗi máy dựng một khoá) thì
     * Android chối cài đè → gỡ bản cũ rồi cài lại, trả Home + dịch vụ trợ năng như trước. Cấu hình nằm trong thư mục
     * của app nên mất theo, người gọi tự đẩy lại (21/10/2026, Formovie L206 nhà anh: app báo xong mà Home vẫn bản 2.2).
     */
    /**
     * Chạy việc cài với giới hạn thời gian (23/10/2026, hộp RockChip Android 14 chạy CleanTV trên chính nó xoay mãi ở
     * bước cài: màn hỏi "Xác minh ứng dụng qua USB" bị che). Quá hạn thì nối lại ADB và trả "Failure TIMEOUT".
     */
    private fun voiHan(giay: Long, viec: () -> String): String {
        val ex = java.util.concurrent.Executors.newSingleThreadExecutor()
        val f = ex.submit<String> { runCatching(viec).getOrElse { "Failure ${it.message}" } }
        return try { f.get(giay, java.util.concurrent.TimeUnit.SECONDS) }
        catch (e: java.util.concurrent.TimeoutException) { f.cancel(true); runCatching { adb.ketNoiLai() }; "Failure TIMEOUT" }
        finally { ex.shutdownNow() }
    }

    private fun caiApkHome(apk: ByteArray, bao: (String) -> Unit = {}): String {
        val out = voiHan(120) { adb.caiApk(apk) }
        if (out.contains("TIMEOUT")) return out
        if (!out.contains("UPDATE_INCOMPATIBLE")) return out
        bao(t("Home sạch cũ ký khoá khác, gỡ rồi cài lại…", "The old clean Home has another signature, reinstalling…"))
        val troNang = sh("settings get secure enabled_accessibility_services").trim()
        val laHome = homeHienTai().startsWith("$pkgHome/")
        sh("pm uninstall $pkgHome")
        val lai = runCatching { adb.caiApk(apk) }.getOrElse { "Failure ${it.message}" }
        if (lai.contains("Success")) {
            if (laHome) sh("cmd package set-home-activity --user 0 $cmpHome")
            if (troNang.contains(pkgHome)) sh("settings put secure enabled_accessibility_services '$troNang'; settings put secure accessibility_enabled 1")
        }
        return lai
    }

    /** Cài đè Home sạch bản đi kèm app; bật lại dịch vụ trợ năng mà Android tắt khi cài lại. */
    fun capNhatLauncher(apk: ByteArray): Boolean {
        // Trên tivi đã đúng bản đi kèm thì thôi (24/10/2026): cài lại Home đang chạy làm Home bật lên trước, đẩy CleanTV
        // xuống nền — chạy CleanTV ngay trên TCL 65Q646 kẹt ở "Đang cập nhật Home sạch…" mỗi lần Áp dụng.
        val bam = java.security.MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val coSan = runCatching { sh("sha256sum \$(pm path $pkgHome | head -n 1 | sed 's/^package://') 2>/dev/null").trim().substringBefore(' ') }.getOrDefault("")
        if (coSan == bam) return true
        thieuBoNho = boNhoThap()
        if (thieuBoNho) return false
        val truoc = sh("settings get secure enabled_accessibility_services").trim()
        val out = caiApkHome(apk)
        thieuBoNho = !out.contains("Success") && (THIEU_CHO.containsMatchIn(out) || (out.contains("TIMEOUT") && boNhoThap()))
        if (truoc.contains(pkgHome)) {
            sh("settings put secure enabled_accessibility_services '$truoc'; settings put secure accessibility_enabled 1")
        }
        return out.contains("Success")
    }

    /**
     * Màn hình chờ cho nút trên Home sạch: bật lại Backdrop (Android TV) / Ambient (Google TV) nếu chính app đã tắt,
     * rồi đặt nó làm màn chờ và bật màn chờ. Gói vốn tắt từ trước khi dọn thì để nguyên.
     */
    fun chuanBiManCho(sl: BanSaoLuu?) {
        val ung = listOf("com.google.android.apps.tv.dreamx", "com.google.android.backdrop")
        // Bật lại cả khi gói bị tắt từ lần dọn bằng công cụ khác (Formovie nhà anh: dreamx tắt sẵn → nút màn chờ im).
        val daTat = sh("pm list packages -d").lines().map { it.substringAfter("package:").trim() }
        for (p in ung) if (p in daTat) {
            sh("pm enable --user 0 $p")
            if (sl != null && p in sl.daTatBoiApp) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
        }
        val dv = sh("cmd package query-services --brief -a android.service.dreams.DreamService").lines().map { it.trim() }
        val chon = ung.firstNotNullOfOrNull { p -> dv.firstOrNull { it.startsWith("$p/") } }
        if (chon != null) sh("settings put secure screensaver_components $chon")
        sh("settings put secure screensaver_enabled 1")
    }

    /** Ghi config.json cho launcher rồi khởi động lại Home (theo tools/theme.sh). */
    /**
     * Ghi một file theme vào thư mục của Home sạch. ĐI QUA ThemeProvider TRƯỚC (chạy dưới quyền Home sạch nên file thuộc
     * về nó); `cp` của shell chỉ là dự phòng. 22/10/2026: `cp` tạo file mới mang uid shell (2000) — Android 14 cấm Home
     * sạch đọc (log "TvHome.Config: ignoring … EACCES"), theme/dock người dùng chọn không được áp sau lần cài đầu hoặc
     * sau khi gỡ-cài lại Home. File cũ của shell phải xoá trước, không thì provider cũng không ghi đè được.
     */
    /** Cỡ file của Home sạch trên tivi: đọc thẳng thư mục, không được (Android 11+ chặn shell) thì đọc qua ThemeProvider. */
    private fun coFileTheme(dir: String, ten: String): Int? =
        sh("wc -c < $dir/$ten 2>/dev/null").trim().toIntOrNull()
            ?: sh("content read --uri content://$pkgHome.theme/$ten 2>/dev/null | wc -c").trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * Ghi một file cho Home sạch và kiểm nó tới nơi đủ cỡ. Trả về false nếu sau hai lượt vẫn sai.
     * 25/10/2026 (Sony 2015 Android 8): `content write` có thể chưa có mà vẫn trả mã 0. 26/10/2026 (TCL C06, hộp hx322x,
     * Xiaomi Android 11: ảnh nền riêng không lên, phải áp dụng vài lần): shell không đọc được Android/data nên lượt kiểm cũ
     * luôn "sai cỡ" rồi chép thẳng (cũng không được), ảnh hỏng nửa chừng vẫn nằm đó. Giờ kiểm qua ThemeProvider và ghi lại.
     */
    private fun ghiFileTheme(ten: String, du: ByteArray): Boolean {
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val tam = "/data/local/tmp/cyt-${ten.replace('/', '-')}"
        adb.ghiFile(tam, du)
        if (ten.contains('/')) sh("mkdir -p $dir/${ten.substringBeforeLast('/')} 2>/dev/null; true")
        var ok = false
        for (luot in 1..2) {
            sh("rm -f $dir/$ten 2>/dev/null; content write --uri content://$pkgHome.theme/$ten < $tam 2>/dev/null || cp $tam $dir/$ten 2>/dev/null")
            if (coFileTheme(dir, ten) == du.size) { ok = true; break }
            sh("cp $tam $dir/$ten 2>/dev/null; chmod 666 $dir/$ten 2>/dev/null; true")
            if (coFileTheme(dir, ten) == du.size) { ok = true; break }
        }
        // 3.5.16 (TCL 65T66, truongthanguit 28/10/2026: "tivi chặn ghi file" mà không biết bước nào): hỏng thì giữ lại lỗi
        // của từng lệnh, đưa vào chẩn đoán khi người dùng bấm Gửi danh sách.
        if (!ok) loiGhiFile = runCatching {
            sh("echo \"$ten ${du.size}\"; ls -l $tam 2>&1; content write --uri content://$pkgHome.theme/$ten < $tam 2>&1; echo rc=\$?; " +
                "cp $tam $dir/$ten 2>&1; echo cp=\$?; ls -ld $dir 2>&1").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("at ") }.joinToString("\n").take(500)
        }.getOrDefault("")
        sh("rm -f $tam")
        return ok
    }

    /** Lỗi lần ghi file hỏng gần nhất (ghiFileTheme), rỗng nếu chưa hỏng lần nào. */
    var loiGhiFile = ""
    /** Quyền Home sạch cần cho Luôn mở / Mở đè mà tivi không cho cấp (dayCauHinh), rỗng nếu đủ. */
    var thieuQuyen = ""

    /**
     * Ảnh có sẵn trên tivi / USB cắm vào tivi (24/10/2026): tivi không có trình chọn ảnh ("không có ứng dụng phù hợp"),
     * người dùng iPhone chạy CleanTV ngay trên tivi chỉ chọn ảnh được kiểu này. Tối đa 200 file.
     */
    fun timAnhTrenTv(): List<String> = sh(
        "for d in /sdcard/Download /sdcard/Pictures /sdcard/DCIM /sdcard/Movies /storage/*-*; do [ -d \"\$d\" ] && find \"\$d\" -maxdepth 4 -type f " +
            "\\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.heic' \\) 2>/dev/null; done | head -n 200"
    ).lines().map { it.trim() }.filter { it.startsWith("/") && !it.contains('\'') }

    /** File có đuôi [duoi] trong Download và USB của tivi (CleanTV chạy trên tivi, 29/10/2026: không có trình chọn file). */
    fun timFileTrenTv(duoi: List<String>): List<String> = sh(
        "for d in /sdcard/Download /storage/*-*; do [ -d \"\$d\" ] && find \"\$d\" -maxdepth 4 -type f \\( " +
            duoi.joinToString(" -o ") { "-iname '*.$it'" } + " \\) 2>/dev/null; done | head -n 200"
    ).lines().map { it.trim() }.filter { it.startsWith("/") && !it.contains('\'') }

    /** IP mạng LAN của tivi (Wi-Fi hoặc dây), null nếu không có. */
    fun ipMang(): String? = sh("ip -4 -o addr show 2>/dev/null | grep -v ' lo ' | awk '{print \$4}' | cut -d/ -f1 | head -n 1").trim()
        .takeIf { Regex("""\d+\.\d+\.\d+\.\d+""").matches(it) }

    /** Ghi file vào Download của tivi, trả đường dẫn (thay hộp "Lưu" khi CleanTV chạy trên tivi). */
    fun ghiVaoDownload(ten: String, du: ByteArray): String {
        val p = "/sdcard/Download/" + ten.replace(Regex("[^A-Za-z0-9._-]+"), "-")
        sh("mkdir -p /sdcard/Download")
        adb.ghiFile(p, du)
        return p
    }

    /** Cho CleanTV quyền cài bản cập nhật của chính nó (appops qua ADB; tivi thường giấu mục "nguồn không rõ"). */
    fun choPhepCaiApp(goi: String) { sh("appops set $goi REQUEST_INSTALL_PACKAGES allow") }

    /** Đọc một file trên tivi (qua base64); quá 25 MB hoặc lỗi thì null. */
    fun docFileTv(duongDan: String): ByteArray? = runCatching {
        if (duongDan.contains('\'')) return null
        val co = sh("wc -c < '$duongDan'").trim().toLongOrNull() ?: return null
        if (co <= 0 || co > 25_000_000) return null
        val b64 = sh("base64 '$duongDan' | tr -d '\\n'").trim()
        java.util.Base64.getDecoder().decode(b64)
    }.getOrNull()

    /** Ảnh nền đang có trên tivi (wallpaper.jpg của Home sạch), qua base64 trong shell; null nếu không có / lỗi. */
    fun docAnhNenTv(): ByteArray? = docFileTheme("wallpaper.jpg")

    /** Một file của Home sạch qua ThemeProvider (vd wallpapers/r02.jpg của bộ ảnh riêng); null nếu không có / lỗi. */
    fun docFileTheme(ten: String): ByteArray? = runCatching {
        val b64 = sh("content read --uri content://$pkgHome.theme/$ten 2>/dev/null | base64 | tr -d '\\n'").trim()
        if (b64.length < 100) null else java.util.Base64.getDecoder().decode(b64)
    }.getOrNull()

    /** Ảnh ô kênh (kenh-vtv1.jpg…) cho link_tiles của bản vá launcher; PhienLamViec đặt trước khi đẩy cấu hình. */
    var anhKenh: List<Pair<String, ByteArray>> = emptyList()

    fun dayCauHinh(json: String, anhNen: ByteArray? = null, boAnh: List<Pair<String, ByteArray>>? = null) {
        giaHanGac()
        batLaiGoiLoi {}
        val dir = "/sdcard/Android/data/$pkgHome/files"
        val uri = "content://$pkgHome.theme"
        sh("mkdir -p $dir 2>/dev/null; true")
        // restore_key_services (Nâng cao) chỉ chạy khi Home sạch có WRITE_SECURE_SETTINGS (upstream build-and-install.sh
        // RESTORE_KEY_SERVICES=1). Tắt thì thu lại quyền cho gọn.
        val moi = runCatching { org.json.JSONObject(json) }.getOrNull()
        // Khoá chuyển đè Home cũ do damBaoPhimHome gắn (tivi không cho tắt Home gốc): đổi giao diện không được làm mất nó.
        val ghi = moi?.takeIf { !it.has("stock_home_redirect") }?.let { m ->
            docCauHinhTv()?.optString("stock_home_redirect")?.takeIf { it.isNotBlank() }?.let { m.put("stock_home_redirect", it).toString() }
        } ?: json
        val cuoi = runCatching { org.json.JSONObject(ghi) }.getOrNull()
        val khoiPhucPhim = cuoi?.optBoolean("restore_key_services") ?: false
        // BootHome của bản vá (3.4.2, TCL 43P618 về Home gốc sau khi rút điện): lúc khởi động, Home sạch tự bật lại dịch vụ
        // bắt phím mà hãng gỡ mất (cần WRITE_SECURE_SETTINGS) và tự mở lên (cần quyền hiện trên app khác).
        val luonVe = cuoi?.optBoolean("power_on_home_always") == true
        // Kiểu mở đè Home cũ (stock_home_redirect): 3.5.1 BootHome cũng mở Home sạch sau khi rút điện.
        val moDe = !cuoi?.optString("stock_home_redirect").isNullOrBlank()
        val canQuyen = khoiPhucPhim || luonVe || moDe
        sh("pm ${if (canQuyen) "grant" else "revoke"} $pkgHome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; true")
        sh("appops set $pkgHome SYSTEM_ALERT_WINDOW ${if (luonVe || moDe) "allow" else "default"} 2>/dev/null; true")
        // 3.5.6 (TCL 43P618 chặn BOOT_COMPLETED với app ngoài): Android tự nối dịch vụ nghe thông báo lúc khởi động, Home sạch
        // dùng lúc đó để tự mở. Chỉ cấp, không thu lại: nút "đang phát" cũng dùng dịch vụ này.
        if (luonVe || moDe) choPhepDangPhat()
        // 3.5.16 (Xiaomi MiTV-MZTU0, famquanh 28/10/2026: Luôn mở bật mà vẫn về Netflix; danh sách cho thấy quyền chưa cấp):
        // Xiaomi chặn app ngoài tự chạy lúc khởi động nếu thiếu AUTO_START, và lệnh cấp quyền có thể bị từ chối im lặng.
        thieuQuyen = ""
        // TCL AppBoot chặn nối lại dịch vụ bắt phím sau khi khởi động ("callee_does't_have_OP_AUTO_START_permission",
        // TCL G06, issue #5): tivi lên Google TV thay vì Home sạch. Máy khác không có quyền này, lệnh báo lỗi thì thôi.
        sh("appops set $pkgHome APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true")
        // Xiaomi: thiếu AUTO_START thì dịch vụ bắt phím không chạy, gán nút remote Dò được mà bấm không ăn (minhtu, EA Pro
        // nội địa 02/11/2026). Cấp luôn, không chỉ khi Luôn mở / Mở đè; máy khác không có quyền này thì lệnh báo lỗi, thôi.
        sh("cmd appops set $pkgHome AUTO_START allow 2>/dev/null; true")
        if (luonVe || moDe) {
            val q = sh("dumpsys package $pkgHome 2>/dev/null | grep -m1 -o 'WRITE_SECURE_SETTINGS: granted=[a-z]*'; appops get $pkgHome SYSTEM_ALERT_WINDOW 2>&1 | head -1")
            thieuQuyen = listOfNotNull(
                t("sửa cài đặt hệ thống", "change system settings").takeIf { !q.contains("granted=true") },
                t("hiện trên app khác", "show over other apps").takeIf { !q.contains("allow") }).joinToString(", ")
        }
        // Nút hẹn giờ tắt (sleep_timer_button) cần báo thức đúng giờ: cấp sẵn, máy cũ không có lệnh thì thôi.
        sh("appops set $pkgHome SCHEDULE_EXACT_ALARM allow 2>/dev/null; true")
        ghiFileTheme("config.json", ghi.toByteArray())
        if (anhNen != null) {
            if (!ghiFileTheme("wallpaper.jpg", anhNen)) throw java.io.IOException(
                t("Ảnh nền chưa sang được tivi (tivi chặn ghi file của Home sạch). Bấm Áp dụng lên tivi lại; vẫn không được thì bấm \"Gửi danh sách cho VGS\" để gửi kèm lỗi cho VGS xem.",
                    "The wallpaper did not reach the TV (it blocks writing the clean Home's files). Apply again; if it still fails, press \"Send the list to VGS\" so VGS gets the error."))
        } else sh("rm -f $dir/wallpaper.jpg 2>/dev/null; content delete --uri $uri/wallpaper.jpg 2>/dev/null; true")
        // Bộ ảnh xoay vòng (wallpapers/): xoá bộ cũ trước, theme không có bộ ảnh thì chỉ xoá.
        sh("rm -rf $dir/wallpapers 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null; true")
        boAnh?.forEach { (ten, du) -> ghiFileTheme("wallpapers/$ten", du) }
        anhKenh.forEach { (ten, du) -> ghiFileTheme("kenh-$ten", du) }
        ghiFileTheme("theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray())
        sh(KHOI_DONG_LAI_HOME)
    }

    /**
     * Home lạ mà app vẫn dám nhận là Home gốc: app HỆ THỐNG, có màn HOME thật, không phải trình cài đặt / Cài đặt /
     * hộp chọn. App bên thứ ba (launcher người dùng tự cài) thì để nguyên.
     */
    /** Kết quả thử nút Home thật: [gianh] = các gói đã tắt vì giành nút Home, [conLai] = gói vẫn giành (không dám tắt). */
    data class KetQuaPhimHome(val ok: Boolean, val gianh: List<String>, val conLai: String, val lyDo: String = "", val daBatBatPhim: Boolean = false)

    /** config.json đang có trên tivi (để "Đổi giao diện" mở ra đúng lựa chọn cũ); null nếu chưa có / đọc không được. */
    fun docCauHinhTv(): org.json.JSONObject? {
        val s = sh("content read --uri content://$pkgHome.theme/config.json 2>/dev/null || cat /sdcard/Android/data/$pkgHome/files/config.json 2>/dev/null").trim()
        return runCatching { org.json.JSONObject(s) }.getOrNull()
    }

    /** Gói của màn đang hiện trên tivi (đọc cửa sổ đang giữ tiêu điểm — chạy được từ Android 8 tới 14). */
    fun goiDangHien(): String {
        val ten = Regex("""([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+""")
        repeat(5) {
            val w = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2")
            ten.find(w)?.groupValues?.get(1)?.let { return it }
            // Sony BRAVIA BF1 (Google TV 12, 22/10/2026): Home Google mở chậm, cửa sổ chưa nhận tiêu điểm — đọc activity
            // đang chạy trên cùng thay vào.
            val a = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -2")
            ten.find(a)?.groupValues?.get(1)?.let { return it }
            Thread.sleep(1000) // đang chuyển màn, tiêu điểm tạm trống
        }
        return ""
    }

    /** Gói có màn HOME đang bật. */
    private fun coManHome(pkg: String): Boolean =
        sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME").lines().any { it.trim().startsWith("$pkg/") }

    private fun dangBat(pkg: String) = sh("pm list packages -e $pkg").lines().any { it.trim() == "package:$pkg" }

    /**
     * Bấm Home THẬT trên tivi rồi xem màn nào hiện ra (21/10/2026: nhiều người báo cài xong bấm Home lại về màn cũ).
     * Gói hệ thống nào giành nút Home (có màn Home / tên kiểu launcher, không phải Cài đặt/HDMI/setupwraith) thì tắt
     * (ghi vào sao lưu), lặp tối đa 3 lần.
     *
     * 22/10/2026 (bản 1.1 làm tivi TCL Google TV đen màn): KHÔNG THÀNH CÔNG THÌ BẬT LẠI MỌI GÓI VỪA TẮT — tivi phải về
     * đúng như trước khi thử, không bao giờ bị bỏ lại không có Home. Và Home gốc của Google (launcherx/tvlauncher)
     * chỉ được tắt khi setupwraith đã tắt: còn bật thì nó nhận Home, tivi kẹt ở màn trình cài đặt.
     */
    fun damBaoPhimHome(sl: BanSaoLuu?, bao: (String) -> Unit = {}): KetQuaPhimHome {
        val gianh = mutableListOf<String>()
        var daBatBatPhim = false
        var lyDo = ""
        fun tra(pkg: String): KetQuaPhimHome {
            if (gianh.isNotEmpty()) {
                bao(t("Không chuyển được nút Home, bật lại ${gianh.joinToString()}…", "Couldn't take over the Home button, re-enabling ${gianh.joinToString()}…"))
                gianh.forEach { p -> sh("pm enable --user 0 $p"); sl?.daTatBoiApp?.remove(p); if (sl?.homeGocDaTat == p) sl.homeGocDaTat = "" }
                sl?.let { luuSaoLuu(it) }
            }
            // Home gốc đã tắt từ bước cài (caiLauncher) mà nút Home vẫn không ra Home sạch: bật lại + đặt làm mặc định.
            // 22/10/2026, Xiaomi MiTV-ASTP0 bản Trung Quốc: hãng chỉ nhận Home theo danh sách tên gói, nút Home ra
            // FallbackHome (com.android.tv.settings) = màn đen, mà app báo "để tivi như cũ" khi Home Xiaomi vẫn tắt.
            if (sl != null && sl.homeGocDaTat.isNotBlank()) {
                bao(t("Bật lại Home gốc (${sl.homeGocDaTat})…", "Re-enabling the stock Home (${sl.homeGocDaTat})…"))
                traHomeGoc(sl)
            }
            if (gianh.isNotEmpty() || sl != null) sh("input keyevent KEYCODE_HOME")
            return KetQuaPhimHome(false, gianh.toList(), pkg, lyDo)
        }
        /**
         * Home gốc không tắt được hoặc hãng tự bật lại (SCBC R3 Android 14, 23/10/2026: tắt tvlauncher 3 lần, lần nào bấm
         * Home cũng về nó): tắt tiếp là vô ích. Bật lại mọi gói vừa tắt, để Home sạch tự mở đè mỗi khi màn Home cũ hiện ra
         * (stock_home_redirect của HomeKeyService, upstream #479/#1116). Thử trên máy ảo Android TV 14: tvlauncher vẫn bật
         * và vẫn là Home mặc định, bấm Home từ Cài đặt 3/3 lần ra Home sạch. Không được thì gỡ khoá ra, trả null.
         */
        fun chuyenDe(pkg: String): KetQuaPhimHome? {
            if (laBoMachCvte()) return null
            bao(t("Không tắt '$pkg' được: cho Home sạch tự mở đè lên Home cũ…", "'$pkg' can't be disabled: letting the clean Home open over the old one…"))
            if (gianh.isNotEmpty()) {
                gianh.forEach { p -> sh("pm enable --user 0 $p"); sl?.daTatBoiApp?.remove(p); if (sl?.homeGocDaTat == p) sl.homeGocDaTat = "" }
                sl?.let { luuSaoLuu(it) }
                gianh.clear()
            }
            if (!ganChuyenHomeCu(pkg)) return null
            Thread.sleep(1500)
            sh("cmd package set-home-activity --user 0 $cmpHome")
            // Home cũ đang nằm sẵn trên màn (nhánh setupwraith không tắt gì) thì bấm Home không đổi cửa sổ, HomeKeyService
            // không có sự kiện để bắt (2.6.2–2.6.3 vì thế báo hỏng trên mọi Google TV; chẩn đoán 23/10/2026 cho thấy
            // focus đúng là HomeActivity của launcherx). Mở Cài đặt trước rồi mới bấm Home. Hai lần: dịch vụ vừa bật có thể
            // chưa kịp bind.
            repeat(2) { lan ->
                sh("am start -a android.settings.SETTINGS >/dev/null 2>&1")
                Thread.sleep(1500)
                sh("input keyevent KEYCODE_HOME")
                Thread.sleep(3000)
                if (goiDangHien() == pkgHome) return KetQuaPhimHome(true, emptyList(), "", "chuyen", true)
                if (lan == 0) { bao(t("Chờ Home sạch nhận lệnh, thử lại…", "Waiting for the clean Home to pick it up, retrying…")); Thread.sleep(3000) }
            }
            boChuyenHomeCu()
            return null
        }
        repeat(4) { lan ->
            giaHanGac()
            sh("cmd package set-home-activity --user 0 $cmpHome")
            sh("input keyevent KEYCODE_HOME")
            Thread.sleep(2500)
            val pkg = goiDangHien()
            if (pkg == pkgHome) return KetQuaPhimHome(true, gianh, "", "", daBatBatPhim)
            // Đã tắt mà vẫn hiện: hãng bật lại sau lưng.
            if (pkg in gianh) {
                chuyenDe(pkg)?.let { return it }
                lyDo = "khong-tat"
                return tra(pkg)
            }
            // Không đọc được màn nào đang hiện mà Google TV còn bật setupwraith: gần như chắc là ca setupwraith giữ Home,
            // báo đúng cách sửa (quên Wi-Fi…) thay vì "không rõ" (Sony 65S30, 22/10/2026).
            if (pkg.isBlank() && dangBat(SETUP)) {
                lyDo = "setupwraith"
                val goc = HOME_GOC_BIET.firstOrNull { dangBat(it) } ?: ""
                if (goc.isNotBlank()) chuyenDe(goc)?.let { return it }
                return tra(goc)
            }
            if (KeHoach.laHomeCvte(pkg)) {
                lyDo = "cvte"
                return tra(pkg)
            }
            // Nút Home mở một app KHÔNG có màn Home (hộp nhà mạng NES: com.nes.recents — 23/10/2026, SEI RT-G2): phần mềm
            // hãng tự đổi nút Home. Bật dịch vụ bắt phím Home của Home sạch rồi bấm thử lại, không tắt gì. Bo CVTE: không
            // bật dịch vụ bắt phím cùng lượt (upstream #741).
            if (!daBatBatPhim && pkg.isNotBlank() && pkg != SETUP && pkg !in KHONG_PHAI_HOME && !coManHome(pkg) && !laBoMachCvte()) {
                // Tivi chậm có thể còn hiện app cũ ngay sau khi bấm Home: chờ thêm rồi đọc lại, chỉ bật khi vẫn đúng app đó.
                Thread.sleep(2000)
                val lai = goiDangHien()
                if (lai == pkgHome) return KetQuaPhimHome(true, gianh, "", "")
                if (lai != pkg) return@repeat
                bao(t("Nút Home đang mở '$pkg' (không phải màn Home): bật bắt phím Home của Home sạch rồi thử lại…", "The Home button opens '$pkg' (not a home screen): turning on the clean Home's Home-key helper and trying again…"))
                batDichVu("HomeKeyService")
                daBatBatPhim = true
                Thread.sleep(1500)
                return@repeat
            }
            // Google TV còn setupwraith: không tắt Home Google (sự cố 1.1, TCL BeyondTV4 đen màn), chỉ cho Home sạch mở đè
            // (2.6.2, ~10 TCL Google TV G07–G15 tối 23/10/2026 báo loi:launcherx). Không được thì báo cách quên Wi-Fi như cũ.
            if (pkg in HOME_GOC_BIET && dangBat(SETUP)) {
                lyDo = "setupwraith"
                chuyenDe(pkg)?.let { return it }
                return tra(pkg)
            }
            // Hộp "chọn Home" của hệ thống (gói android, ResolverActivity): tivi có nhiều launcher (Launchpad TV, Home TV…) mà
            // chưa có Home mặc định. Đóng hộp rồi lượt sau đặt lại Home sạch và bấm Home (25/10/2026, Xiaomi ASTR0 nội địa:
            // mỗi lần đổi giao diện app lại trả Home gốc vì coi hộp này là hỏng).
            if (pkg == "android" && lan < 3) {
                bao(t("Tivi đang hỏi chọn Home: đóng hộp, đặt Home sạch làm mặc định rồi thử lại…", "The TV is asking which Home to use: closing it, setting the clean Home as default and retrying…"))
                sh("input keyevent KEYCODE_BACK")
                Thread.sleep(1000)
                return@repeat
            }
            // Trên màn vẫn là một app chứ chưa phải Home nào (Cài đặt app vừa mở để thử, hộp cài đặt, app người dùng đang
            // xem): phím Home chưa ăn, chưa phải Home cũ giành nút. Chờ rồi bấm lại thay vì báo hỏng ngay lượt đầu (25/10/2026:
            // Sony VH21 / BF1, Xiaomi MOOR2, onn 4K… báo "Nút Home vẫn mở com.android.tv.settings"; Sony VU1 "com.goodtoolapps.zeus").
            if (lan < 3 && pkg.isNotBlank() && pkg != SETUP && (pkg in KHONG_PHAI_HOME || pkg.contains("packageinstaller") || !coManHome(pkg))) {
                Thread.sleep(2000)
                return@repeat
            }
            if (lan == 3 || sl == null || pkg == SETUP || !nenTatVuiGianhHome(pkg)) return tra(pkg)
            bao(t("Nút Home vẫn mở '$pkg', tắt nó…", "The Home button still opens '$pkg', disabling it…"))
            gacThem(pkg)
            sh("pm disable-user --user 0 $pkg")
            if (pkg !in sl.daTatTruoc && pkg !in sl.daTatBoiApp) sl.daTatBoiApp += pkg
            // Nhớ là Home gốc: lần "Đổi giao diện" sau ghi đè config.json vẫn gắn lại stock_home_redirect cho nó.
            if (sl.homeGocDaTat.isBlank() && pkg in HOME_GOC_BIET + listOf(sl.homeTruoc.substringBefore('/'))) sl.homeGocDaTat = pkg
            luuSaoLuu(sl)
            gianh += pkg
            // Lệnh tắt bị từ chối (gói được hãng bảo vệ) hoặc vừa tắt đã bật lại.
            Thread.sleep(800)
            if (dangBat(pkg)) {
                chuyenDe(pkg)?.let { return it }
                lyDo = "khong-tat"
                return tra(pkg)
            }
        }
        return tra(goiDangHien())
    }

    private fun nenTatVuiGianhHome(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || KeHoach.laHomeCvte(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg) ||
            pkg.startsWith("com.android.systemui") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) return false
        if (!sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return laLauncherCaiThem(pkg)
        if (sh("dumpsys package $pkg | grep -c TV_INPUT_HARDWARE").trim().toIntOrNull()?.let { it > 0 } == true) return false
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
        return coHome || Regex("launcher|tvhome|home", RegexOption.IGNORE_CASE).containsMatchIn(pkg.substringAfterLast('.').ifBlank { pkg }) ||
            Regex("launcher", RegexOption.IGNORE_CASE).containsMatchIn(pkg)
    }

    private fun laHomeHangLa(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in KHONG_PHAI_HOME || pkg == "android" || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        val heThong = sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }
        val coHome = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") && !it.trim().endsWith("FallbackHome") }
        return heThong && coHome
    }

    /**
     * Bật dịch vụ trợ năng của Home sạch. TCL (TclAppBoot) không cho bind dịch vụ của app chưa chạy nếu thiếu app-op
     * AUTO_START (upstream #526): cấp trước; dịch vụ đã có trong danh sách thì xoá đi ghi lại để Android bind lại.
     */
    /** App NGƯỜI DÙNG (không phải hệ thống) có màn HOME thật: launcher cài thêm như Dangbei. */
    private fun laLauncherCaiThem(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg) || pkg in HOME_SACH || pkg in KHONG_PHAI_HOME || APP_XUAT_XUONG.containsMatchIn(pkg)) return false
        if (sh("pm list packages -s $pkg").lines().any { it.trim() == "package:$pkg" }) return false
        return sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().any { it.trim().startsWith("$pkg/") }
    }

    /** Tivi dùng bo mạch CVTE (có gói com.cvte.* / cn.cvte.*). Nhớ theo kết nối. */
    private var cvteDaDo: Boolean? = null
    fun laBoMachCvte(): Boolean = cvteDaDo ?: sh("pm list packages | grep -c -E 'package:(com|cn)\\.cvte\\.'").trim()
        .toIntOrNull().let { (it ?: 0) > 0 }.also { cvteDaDo = it }

    private fun batDichVu(ten: String) {
        val svc = "$pkgHome/$NS_HOME.$ten"
        sh("cmd appops set $pkgHome AUTO_START allow 2>/dev/null; s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$ten*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1")
    }

    /**
     * stock_home_redirect (upstream #479): hãng tự bật lại Home cũ (XGIMI bật lại launcherx mỗi lần bấm nút app trên
     * remote) thì HomeKeyService thấy màn Home cũ hiện lên là mở lại Home sạch. Ghi khoá vào config.json đang có trên
     * tivi + bật HomeKeyService.
     */
    /**
     * Nút "Đang phát" của Home sạch (24/10/2026, góp ý nhiều app phát chồng tiếng): cho phép NowPlayingListener của
     * launcher làm trình nghe thông báo, nhờ đó launcher đọc được các phiên phát nhạc / video. Lỗi thì nút không hiện.
     */
    /** Mở màn Cài đặt launcher của Home sạch trên tivi (chỗ chọn "Mở khi khởi động"). Bấm nút Cài đặt thì ra Cài đặt hệ thống. */
    fun moCaiDatLauncher() { sh("am start -a com.gathin.tvhome.action.LAUNCHER_SETTINGS -n $cmpHome >/dev/null 2>&1; true") }

    fun choPhepDangPhat() { runCatching { sh("cmd notification allow_listener $pkgHome/$NS_HOME.NowPlayingListener 2>/dev/null; true") } }

    /** Bật HomeKeyService cho phím remote của mẫu (nút app, Cài đặt). Bo CVTE: không (upstream #741). */
    fun batBatPhimHome() { if (!laBoMachCvte()) batDichVu("HomeKeyService") }

    /**
     * Công tắc nút Input khi Áp dụng (góp ý ngocnd156, Sony KD-43X75K 29/10/2026): InputKeyService luôn bắt phím TV,
     * nên tắt công tắc mà không tắt dịch vụ thì phím TV vẫn mở bảng chọn nguồn. Nút app remote vẫn có HomeKeyService.
     */
    fun datNutInput(bat: Boolean) {
        if (bat) { if (!laBoMachCvte()) batDichVu("InputKeyService"); return }
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$pkgHome/*InputKeyService*) " +
            "n=\$(echo \"\$s\" | sed -E 's#(^|:)$pkgHome/[^:]*InputKeyService(:|\$)#\\1\\2#; s#::#:#; s#^:##; s#:\$##'); " +
            "settings put secure enabled_accessibility_services \"\$n\" ;; esac")
    }

    fun ganChuyenHomeCu(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg)) return false
        val j = docCauHinhTv() ?: return false
        if (j.optString("stock_home_redirect") != pkg) {
            j.put("stock_home_redirect", pkg)
            ghiFileTheme("config.json", j.toString().toByteArray())
            ghiFileTheme("theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray())
        }
        // BootHome (3.5.1): rút điện bật lại thì Home sạch tự bật lại dịch vụ và tự mở đè lên Home cũ.
        sh("pm grant $pkgHome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; appops set $pkgHome SYSTEM_ALERT_WINDOW allow 2>/dev/null; true")
        batDichVu("HomeKeyService")
        return true
    }

    private fun boChuyenHomeCu() {
        val j = docCauHinhTv() ?: return
        if (j.remove("stock_home_redirect") == null) return
        ghiFileTheme("config.json", j.toString().toByteArray())
        batDichVu("HomeKeyService")
    }

    /**
     * Khôi phục mà giữ Home sạch (góp ý dtafzum 28/10/2026, Chromecast: khôi phục xong bấm Home vẫn vào Home sạch): nhả
     * nút Home hẳn. Bỏ dịch vụ trợ năng của Home sạch, xoá mở đè / luôn mở khi bật tivi trong cấu hình, thu quyền ghi cài
     * đặt hệ thống và hiện đè (không thì BootHome bật lại bộ bắt phím ở lần khởi động sau). [truoc]: danh sách trợ năng
     * trước khi dọn, có thì trả nguyên, không thì chỉ gỡ phần của Home sạch.
     */
    fun nhaNutHome(truoc: String?) {
        val tc = sh("settings get secure enabled_accessibility_services").trim()
        val ve = (truoc?.takeIf { it.isNotBlank() && it != "null" } ?: tc)
            .split(':').filter { d -> d.isNotBlank() && d != "null" && HOME_SACH.none { d.startsWith("$it/") } }.joinToString(":")
        if (ve != tc) {
            if (ve.isBlank()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$ve'")
        }
        docCauHinhTv()?.let { j ->
            val bo = listOf("stock_home_redirect", "power_on_home_always").map { j.remove(it) != null }.any { it }
            if (bo) {
                ghiFileTheme("config.json", j.toString().toByteArray())
                // Home sạch giữ một bản sao cấu hình để đọc lúc khởi động; mở nó một lần cho bản sao theo file mới, không
                // thì lần rút điện sau nó vẫn đọc mở đè cũ và tự mở lên (thử máy ảo 28/10/2026).
                sh("am start -n $pkgHome/$NS_HOME.MainActivity >/dev/null 2>&1; sleep 3; true")
            }
        }
        HOME_SACH.forEach { p ->
            sh("pm revoke $p android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; appops set $p SYSTEM_ALERT_WINDOW default 2>/dev/null; true")
        }
    }

    private fun traHomeGoc(sl: BanSaoLuu) {
        val goc = sl.homeGocDaTat.ifBlank { sl.homeTruoc.substringBefore('/') }
        if (goc.isBlank()) return
        sh("pm enable --user 0 $goc")
        sh("cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc | tail -n 1)")
        sl.daTatBoiApp -= goc; sl.homeGocDaTat = ""; luuSaoLuu(sl)
    }

    // ---------- Canh gác Home trên tivi (24/10/2026) ----------

    /**
     * Trước khi tắt một Home trên tivi, hẹn giờ NGAY TRÊN TIVI: quá GAC_GIAY giây không được gia hạn (điện thoại rớt
     * Wi-Fi, hết pin, app bị tắt giữa chừng) thì tivi tự bật lại các Home vừa tắt và đặt lại Home gốc. Sự cố 1.1 (TCL
     * BeyondTV4 tắt launcherx rồi mất kết nối → đen màn) mà có nó thì tivi đã tự cứu. Học từ cleanyourtv-app
     * (udhaykumarbala, watchdog 150 giây). Kịch bản chạy bằng setsid nên sống qua lúc phiên adb đóng.
     */
    private var gacToken: String? = null

    /** Dời hạn canh gác thêm GAC_GIAY tính từ bây giờ (giờ của tivi); chưa hẹn thì thôi. */
    fun giaHanGac() {
        if (gacToken != null) runCatching { sh("echo \$((\$(date +%s) + $GAC_GIAY)) > $GAC-han") }
    }

    /** Ghi [pkg] vào danh sách tivi sẽ tự bật lại; lần đầu thì đặt kịch bản canh gác. Gói đầu danh sách là Home gốc. */
    fun gacThem(pkg: String) {
        if (!TEN_GOI.matches(pkg)) return
        if (gacToken == null) {
            val tk = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            runCatching {
                adb.ghiFile("$GAC.sh", KICH_BAN_GAC.toByteArray())
                sh(": > $GAC-list; echo $tk > $GAC-token")
                gacToken = tk
                giaHanGac()
                // Trong ngoặc: KetNoiAdb.shell nối "; echo <dấu>" vào sau, "… &; echo" là lỗi cú pháp. setsid: tách khỏi
                // phiên shell của app để sống qua lúc phiên đóng (thử trên máy ảo 24/10/2026).
                // Đợi 1 giây trong cùng lệnh: app đóng phiên ngay khi thấy dấu kết thúc, đóng sớm quá là tiến trình
                // con chết trước khi kịp tách. Không thấy chạy thì thử lại một lần.
                repeat(2) {
                    if (sh("(setsid sh $GAC.sh $tk </dev/null >/dev/null 2>&1 &); sleep 1; ps -A -o ARGS | grep -c '^sh $GAC.sh $tk'")
                            .trim().lines().lastOrNull()?.toIntOrNull()?.let { it > 0 } == true) return@runCatching
                }
            }
        }
        runCatching { sh("grep -qx '$pkg' $GAC-list || echo '$pkg' >> $GAC-list") }
        giaHanGac()
    }

    /** Việc với Home đã xong (thành công hay đã tự lui): huỷ canh gác. */
    fun boGac() {
        gacToken = null
        runCatching { sh("rm -f $GAC-token $GAC-list $GAC-han") }
    }

    // ---------- DNS chặn quảng cáo ----------

    /** Tivi đang dùng DNS chặn quảng cáo của AdGuard (do CleanTV hay người dùng đặt). */
    fun dnsDangChan(): Boolean = runCatching {
        sh("settings get global private_dns_specifier").trim() == "dns.adguard-dns.com" &&
            sh("settings get global private_dns_mode").trim() == "hostname"
    }.getOrDefault(false)

    /** Tắt DNS AdGuard: trả DNS như trước khi CleanTV đổi (bản sao lưu), không có thì về tự động. */
    fun tatDnsChanQc(sl: BanSaoLuu?) {
        val mode = sl?.dnsModeTruoc?.takeIf { it.isNotBlank() && it != "null" && it != "hostname" } ?: "opportunistic"
        sh("settings put global private_dns_mode $mode")
        val host = sl?.dnsHostTruoc?.takeIf { it.isNotBlank() && it != "null" && it != "dns.adguard-dns.com" }
        if (host == null) sh("settings delete global private_dns_specifier") else sh("settings put global private_dns_specifier $host")
        sl?.let { it.dnsDaDoi = false; luuSaoLuu(it) }
    }

    fun batDnsChanQc(sl: BanSaoLuu) {
        sh("settings put global private_dns_specifier dns.adguard-dns.com")
        sh("settings put global private_dns_mode hostname")
        sl.dnsDaDoi = true; luuSaoLuu(sl)
    }

    // ---------- Khôi phục ----------

    fun xoaSaoLuuTrenTv() { runCatching { sh("rm -f $TEP_SAO_LUU_TV") } }

    /**
     * Bật một gói, chờ tối đa [giay] giây (06/11/2026, Sony VU3 ngocnd156: pm enable treo 30 phút, phải rút điện).
     * Không dựa vào lệnh timeout (Android 6 không có): chạy nền rồi tự canh. Treo thì trả "TREO", tivi có thể vẫn bật xong sau đó.
     */
    fun batGoiCoHan(p: String, giay: Int = 20): String = runCatching {
        sh("f=/data/local/tmp/cleantv-bat; pm enable --user 0 $p > \$f 2>&1 & k=\$!; i=0; " +
            "while kill -0 \$k 2>/dev/null && [ \$i -lt $giay ]; do sleep 1; i=\$((i+1)); done; " +
            "if kill -0 \$k 2>/dev/null; then kill \$k 2>/dev/null; echo TREO; else cat \$f; fi; rm -f \$f")
    }.getOrElse { "${it.message}" }

    /** Gói đang bị tắt bằng tay / bằng shell (enabled=3: disable-user), không gồm gói hãng tắt sẵn từ xưởng. */
    /** Bật lại một gói; trả true khi tivi báo đã bật. Có bản sao lưu thì bỏ gói đó khỏi danh sách CleanTV đã tắt. */
    fun batLaiGoi(p: String, sl: BanSaoLuu?): Boolean {
        val ok = batGoiCoHan(p).contains("enabled")
        if (ok && sl != null && p in sl.daTatBoiApp) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
        return ok
    }

    /** Mọi gói đang tắt, kể cả kiểu khác disable-user (06/11/2026: Sony VU3 còn 7 gói m2.* tắt mà danh sách cũ không thấy). */
    fun goiDangTat(): List<String> =
        sh("pm list packages -d").lines().map { it.trim().removePrefix("package:") }.filter { TEN_GOI.matches(it) }.sorted()

    fun goiTatBoiNguoiDung(): List<String> =
        sh("for p in \$(pm list packages -d | cut -d: -f2); do dumpsys package \$p | grep -q 'User 0:.*enabled=3' && echo \$p; done")
            .lines().map { it.trim() }.filter { TEN_GOI.matches(it) }.sorted()

    /** Bật lại mọi gói đang tắt kiểu disable-user; trả danh sách lỗi. */
    fun batLaiMoiGoiTat(bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = goiTatBoiNguoiDung()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = batGoiCoHan(p)
            if (!out.contains("enabled")) loi += if (out.contains("TREO")) t("$p: tivi treo khi bật gói này, đã bỏ qua", "$p: the TV hung enabling it, skipped") else "$p: ${out.trim()}"
        }
        return loi
    }

    /**
     * Khôi phục khi KHÔNG có bản sao lưu (gỡ app, đổi máy, người khác dọn — 22/10/2026): bật lại mọi gói đang tắt kiểu
     * disable-user (kể cả gói chủ tivi tự tắt trước đó, màn hình đã hỏi xác nhận), bỏ dịch vụ trợ năng của Home sạch,
     * trả DNS AdGuard của app về tự động, đặt lại Home hệ thống của hãng; tuỳ chọn gỡ Home sạch.
     */
    fun khoiPhucKhongSaoLuu(goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = batLaiMoiGoiTat(bao).toMutableList()
        nhaNutHome(null)
        if (sh("settings get global private_dns_specifier").trim() == "dns.adguard-dns.com") {
            bao(t("Trả DNS về tự động…", "Setting DNS back to automatic…"))
            sh("settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier")
        }
        if (khoaDaCai()) { bao(t("Gỡ Khoá trẻ em…", "Removing the Kids lock…")); runCatching { tatKhoa(go = true) } }
        if (goLauncher) HOME_SACH.forEach { p ->
            if (sh("pm path $p").isNotBlank()) { bao(t("Gỡ Home sạch ($p)…", "Removing the clean Home ($p)…")); sh("pm uninstall $p") }
        }
        // Home hệ thống của hãng: ưu tiên Home gốc app biết, rồi app hệ thống đầu tiên có màn HOME dùng được.
        val heThong = sh("pm list packages -s").lines().map { it.trim().removePrefix("package:") }.toSet()
        val ung = sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines().map { it.trim() }.filter { it.contains('/') && !it.endsWith("FallbackHome") }
            .filter { c -> val p = c.substringBefore('/'); p in heThong && p !in HOME_SACH && p !in KHONG_PHAI_HOME && !APP_XUAT_XUONG.containsMatchIn(p) }
        val home = HOME_GOC_BIET.firstNotNullOfOrNull { g -> ung.firstOrNull { it.startsWith("$g/") } } ?: ung.firstOrNull()
        if (home != null) {
            bao(t("Đặt Home gốc: $home", "Setting the stock Home: $home"))
            sh("cmd package set-home-activity --user 0 $home")
        }
        sh("input keyevent KEYCODE_HOME")
        return loi
    }

    /** Đưa tivi về như trước: bật lại gói app đã tắt, trả Home gốc, gỡ Home sạch nếu muốn, trả DNS. */
    fun khoiPhuc(sl: BanSaoLuu, goLauncher: Boolean, bao: (String) -> Unit): List<String> {
        val loi = mutableListOf<String>()
        val ds = sl.daTatBoiApp.toList()
        ds.forEachIndexed { i, p ->
            bao(t("Bật lại ${i + 1}/${ds.size}: $p", "Re-enabling ${i + 1}/${ds.size}: $p"))
            val out = batGoiCoHan(p)
            if (out.contains("enabled")) { sl.daTatBoiApp -= p; luuSaoLuu(sl) }
            else loi += if (out.contains("TREO")) t("$p: tivi treo khi bật gói này, đã bỏ qua", "$p: the TV hung enabling it, skipped") else "$p: ${out.trim()}"
        }
        if (sl.launcherDaCai) {
            bao(t("Trả Home gốc…", "Restoring the stock Home…"))
            val goc = sl.homeTruoc
            if (goc.isNotBlank() && HOME_SACH.none { goc.startsWith("$it/") }) {
                sh("cmd package set-home-activity --user 0 $goc")
            }
            if (!goLauncher) {
                bao(t("Nhả nút Home cho Home gốc…", "Handing the Home button back…"))
                nhaNutHome(sl.truyCapTruoc)
                sh("input keyevent KEYCODE_HOME")
            }
            if (goLauncher) {
                bao(t("Gỡ Home sạch…", "Removing the clean Home…"))
                HOME_SACH.filter { it == PKG_HOME || it == pkgHome }.forEach { sh("pm uninstall $it") }
                // Trả danh sách dịch vụ trợ năng như trước khi làm (app có thể đã bật HomeKeyService / InputKeyService).
                val tc = sl.truyCapTruoc
                if (tc.isBlank() || tc == "null") sh("settings delete secure enabled_accessibility_services")
                else sh("settings put secure enabled_accessibility_services '$tc'")
            }
            sl.launcherDaCai = !goLauncher && sl.launcherDaCai
            sl.homeGocDaTat = ""
            luuSaoLuu(sl)
        }
        if (sl.dnsDaDoi) {
            bao(t("Trả DNS như cũ…", "Restoring DNS…"))
            val mode = sl.dnsModeTruoc.takeIf { it.isNotBlank() && it != "null" } ?: "opportunistic"
            sh("settings put global private_dns_mode $mode")
            if (sl.dnsHostTruoc.isBlank() || sl.dnsHostTruoc == "null") sh("settings delete global private_dns_specifier")
            else sh("settings put global private_dns_specifier ${sl.dnsHostTruoc}")
            sl.dnsDaDoi = false; luuSaoLuu(sl)
        }
        if (khoaDaCai()) { bao(t("Gỡ Khoá trẻ em…", "Removing the Kids lock…")); runCatching { tatKhoa(go = true) } }
        return loi
    }

    // ---------- Khoá trẻ em (app zone.vgs.cleantv.khoa, 23/10/2026) ----------

    private val khoaUri = "content://$PKG_KHOA"
    private val khoaDv = "$PKG_KHOA/zone.vgs.khoa.KhoaDichVu"

    fun khoaDaCai(): Boolean = sh("pm path $PKG_KHOA 2>/dev/null").contains("package:")

    /** Trạng thái trên tivi (không có PIN): {bat, khung, moTamPhut, coPin, moTamDen, dangKhoa, dichVuChay}; null nếu chưa cài. */
    fun docKhoa(): org.json.JSONObject? {
        if (!khoaDaCai()) return null
        val out = sh("content call --uri $khoaUri --method doc 2>/dev/null")
        val a = out.indexOf("json="); val b = out.lastIndexOf("}]")
        return if (a < 0 || b <= a) null else runCatching { org.json.JSONObject(out.substring(a + 5, b)) }.getOrNull()
    }

    /** Cài (nếu cần), cấp quyền tự canh gác, ghi cấu hình, bật dịch vụ khoá. [json] không chứa dấu nháy đơn. */
    fun caiKhoa(apk: ByteArray, json: String, bao: (String) -> Unit) {
        require(!json.contains('\'')) { "json" }
        if (!khoaDaCai()) {
            bao(t("Cài app Khoá trẻ em lên tivi…", "Installing the Kids lock app on the TV…"))
            adb.ghiFile("/data/local/tmp/khoa.apk", apk)
            val out = sh("pm install -r /data/local/tmp/khoa.apk; rm -f /data/local/tmp/khoa.apk")
            if (!out.contains("Success")) error(t("Cài không được: ${out.trim().lines().lastOrNull()}", "Install failed: ${out.trim().lines().lastOrNull()}"))
        }
        sh("pm grant $PKG_KHOA android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; cmd appops set $PKG_KHOA AUTO_START allow 2>/dev/null; true")
        bao(t("Ghi giờ khoá và mã PIN…", "Saving the schedule and PIN…"))
        val kq = sh("content call --uri $khoaUri --method dat --arg '$json'")
        if (!kq.contains("ok=1")) error(t("Tivi không nhận cấu hình: ${kq.trim()}", "The TV rejected the settings: ${kq.trim()}"))
        bao(t("Bật dịch vụ khoá…", "Turning the lock service on…"))
        sh("s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$khoaDv*) ;; null|'') settings put secure enabled_accessibility_services $khoaDv ;; *) settings put secure enabled_accessibility_services \"\$s:$khoaDv\" ;; esac; settings put secure accessibility_enabled 1")
    }

    /** Mở tạm [phut] phút từ điện thoại (0 = khoá lại ngay nếu đang trong giờ khoá). */
    fun moTamKhoa(phut: Int) { sh("content call --uri $khoaUri --method mo-tam --arg $phut") }

    /** Tắt khoá: cấu hình bat=false, bỏ dịch vụ khỏi danh sách trợ năng; [go] = gỡ luôn app. */
    fun tatKhoa(go: Boolean) {
        if (!khoaDaCai()) return
        sh("content call --uri $khoaUri --method dat --arg '{\"bat\":false,\"khung\":[]}' 2>/dev/null; true")
        val tc = sh("settings get secure enabled_accessibility_services").trim()
        if (tc.contains(khoaDv)) {
            val con = tc.split(':').filter { it.isNotBlank() && it != khoaDv && it != "null" }.joinToString(":")
            if (con.isBlank()) sh("settings delete secure enabled_accessibility_services") else sh("settings put secure enabled_accessibility_services '$con'")
        }
        if (go) sh("pm uninstall $PKG_KHOA")
    }

    // ---------- Đóng ADB ----------

    /**
     * Hẹn TIVI tự tắt ADB sau [giay] giây (24/10/2026): hẹn giờ trong app điện thoại không chạy được vì Android 14 đóng
     * băng app nằm nền, nên để tivi tự đếm. Huỷ bằng huyTatAdbTrenTv (xoá file hẹn).
     */
    fun henTatAdbTrenTv(giay: Int = 60) {
        sh("echo 1 > $HEN_TAT_ADB; (setsid sh -c 'sleep $giay; [ -f $HEN_TAT_ADB ] && { rm -f $HEN_TAT_ADB; settings put global adb_wifi_enabled 0; settings put global adb_enabled 0; }' </dev/null >/dev/null 2>&1 &); sleep 1")
    }

    /** Xoá file hẹn; false khi ADB trên tivi đã tắt (hết hạn trước khi quay lại) hoặc không trả lời. */
    fun huyTatAdbTrenTv(): Boolean = voiHan(8) { sh("rm -f $HEN_TAT_ADB; settings get global adb_enabled") }.trim() == "1"

    // ---- Remote trên điện thoại (3.5.0, góp ý build.dng 27/10/2026) ----

    /** Một phím remote (KeyEvent.KEYCODE_*). */
    fun phim(ma: Int) { sh("input keyevent $ma") }

    /** Dịch vụ bắt phím của Home sạch đang bật: gõ chữ có dấu đi qua nó (TYPE_TEXT của bản vá). */
    fun coGoCoDau(): Boolean = sh("settings get secure enabled_accessibility_services").contains("$pkgHome/") &&
        sh("settings get secure enabled_accessibility_services").contains("HomeKeyService")

    /**
     * Gõ chữ vào ô đang chọn trên tivi. Có Home sạch + dịch vụ bắt phím: đặt nguyên văn (tiếng Việt có dấu) qua trợ năng.
     * Không thì `input text`, chỉ gõ được chữ không dấu: trả false để màn remote báo.
     */
    fun goChu(chu: String): Boolean {
        if (chu.isEmpty()) return true
        val b64 = java.util.Base64.getEncoder().encodeToString(chu.toByteArray(Charsets.UTF_8))
        val ra = runCatching { sh("am broadcast -a zone.vgs.cleantv.TYPE_TEXT -p $pkgHome --es b64 '$b64' 2>/dev/null") }.getOrDefault("")
        if (ra.contains("result=1")) return true
        val khongDau = java.text.Normalizer.normalize(chu.replace('đ', 'd').replace('Đ', 'D'), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").filter { it.code in 32..126 }
        // input text: dấu cách là %s; phần còn lại đặt trong nháy đơn cho shell.
        khongDau.chunked(80).forEach { phan -> sh("input text '${phan.replace(" ", "%s").replace("'", "'\\''")}'") }
        return khongDau == chu
    }

    /** Bật dịch vụ bắt phím (bật lại nếu đang bật, để Android nạp quyền đọc ô chữ của bản Home sạch mới). */
    fun batGoCoDau() = batBatPhimHome()

    /**
     * Hẹn giờ tắt tivi: tivi tự đếm bằng lệnh nền (không cần Home sạch, điện thoại tắt app vẫn chạy), hết giờ bấm phím
     * Ngủ (223). File hẹn giữ giờ kết thúc; hẹn lại / huỷ là đổi / xoá file, lượt đếm cũ thấy khác thì thôi.
     */
    fun henTatTivi(phut: Int) {
        if (phut <= 0) { sh("rm -f $HEN_TAT_TV"); return }
        val het = System.currentTimeMillis() / 1000 + phut * 60L
        sh("echo $het > $HEN_TAT_TV; (setsid sh -c 'sleep ${phut * 60}; [ \"\$(cat $HEN_TAT_TV 2>/dev/null)\" = \"$het\" ] && { rm -f $HEN_TAT_TV; input keyevent 223; }' </dev/null >/dev/null 2>&1 &); sleep 1")
    }

    /** Giờ hẹn tắt (giây epoch) đang chờ, null nếu không có. */
    fun docHenTatTivi(): Long? = runCatching { sh("cat $HEN_TAT_TV 2>/dev/null").trim().toLongOrNull() }.getOrNull()
        ?.takeIf { it > System.currentTimeMillis() / 1000 }

    /** App người dùng tự cài (gỡ được). */
    fun appTuCai(): Set<String> = sh("pm list packages -3").lines().map { it.substringAfter("package:").trim() }.filter { it.isNotBlank() }.toSet()

    /** Mở một app trên tivi (màn chính kiểu tivi, không có thì kiểu điện thoại). */
    fun moApp(pkg: String): Boolean {
        if (!TEN_GOI.matches(pkg)) return false
        val ra = sh("monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1")
        if (!ra.contains("No activities")) return true
        return !sh("monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>&1").contains("No activities")
    }

    /** Gỡ hẳn một app người dùng tự cài. */
    fun goApp(pkg: String): Boolean = TEN_GOI.matches(pkg) && pkg !in HOME_SACH && sh("pm uninstall $pkg").contains("Success")

    /**
     * Kết nối còn sống không; rớt thì nối lại ngầm. Gọi khi app quay lại từ nền (góp ý uongcafe 28/10/2026: mỗi lần
     * Lưu ra file là mất kết nối; trình chọn file đẩy app xuống nền và vài điện thoại cắt kết nối của app nền).
     */
    fun giuKetNoi(): Boolean = voiHan(8) { sh("echo ok") }.trim() == "ok" || adb.ketNoiLai()

    /** Cài một file APK từ điện thoại (đè bản cũ nếu có); trả dòng kết quả của pm. */
    fun caiApkTuDienThoai(apk: ByteArray): String {
        // Chọn file làm app nằm nền một lúc; kết nối có thể đã rớt (máy ảo 27/10/2026: lần đầu báo mất kết nối, tivi
        // không nhận phiên cài nào). Thử một lệnh, hỏng thì nối lại trước khi gửi.
        if (runCatching { sh("echo ok") }.isFailure) adb.ketNoiLai()
        return voiHan(300) { adb.caiApk(apk) }.trim().lines().lastOrNull() ?: ""
    }

    /**
     * Màn chờ động (dream_motion của bản vá): đặt màn chờ đồng hồ của Home sạch làm màn chờ hệ thống (tivi tự bật theo
     * giờ chờ của nó). Không đổi screen_off_timeout: vài tivi hiểu đó là tắt màn. Tắt thì trả màn chờ về màn của Google
     * nếu đang là của Home sạch.
     */
    fun datManChoDong(bat: Boolean, sl: BanSaoLuu?) {
        if (bat) sh("settings put secure screensaver_components $pkgHome/$NS_HOME.ClockDream; settings put secure screensaver_enabled 1")
        else if (sh("settings get secure screensaver_components").contains("$NS_HOME.ClockDream")) chuanBiManCho(sl)
    }

    /** Tắt ADB qua mạng trên tivi. Lệnh cuối làm rớt kết nối này, nên lỗi ở đây là bình thường. */
    fun tatAdb() {
        runCatching { sh("settings put global adb_wifi_enabled 0") }
        runCatching { sh("settings put global adb_enabled 0") }
    }

    /** Rớt kết nối lúc dọn mà nối lại không được: [daTat] gói đã tắt (đều có trong sao lưu), đang làm dở [goi]. */
    class MatKetNoiTvException(val daTat: Int, val goi: String) : Exception("mat-ket-noi")
    /** Tivi tự khởi động lại ngay sau khi tắt [goi] (gói tắt xong gần nhất): dừng dọn, [goi] là nghi phạm. */
    class TiviKhoiDongLaiException(val goi: String, val daTat: Int) : Exception("khoi-dong-lai")

    /** Lời báo tivi hết bộ nhớ, kèm số trống / cần khi đọc được (bn = boNhoTrong()). */
    fun baoThieuBoNho(bn: Pair<Long, Long>?): String {
        val (trong, tong) = bn ?: (null to null)
        val con = trong?.let { t(" (chỉ còn ${mb(it)} trống)", " (only ${mb(it)} free)") } ?: ""
        val can = tong?.let { t(" Cần trống ít nhất ${mb(canTrong(it))}.", " It needs at least ${mb(canTrong(it))} free.") } ?: ""
        return t("Tivi hết bộ nhớ$con, không chép được Home sạch vào.$can Bấm \"Dọn bộ nhớ đệm\" để xoá cache các app (không mất dữ liệu hay tài khoản), hoặc gỡ bớt app không dùng rồi thử lại. Home gốc vẫn nguyên.",
            "The TV is out of storage$con, so the clean Home can't be copied over.$can Press \"Clear cache\" to clear app caches (no data or accounts are lost), or uninstall unused apps, then try again. The stock Home is untouched.")
    }

    companion object {
        /** Không đưa vào danh sách app chiếm chỗ: xoá dữ liệu / gỡ cập nhật mấy gói này làm hỏng tivi hoặc đăng xuất cả máy. */
        private val KHONG_DUNG_VAO = setOf("com.google.android.gms", "com.google.android.gsf", "com.google.android.webview",
            "com.android.webview", "com.google.android.trichromelibrary", "com.android.vending.billing", "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith", "com.android.providers.media",
            "com.google.android.providers.media.module", "com.android.providers.settings", "com.android.shell")
        private const val PKG_CLEANTV = "zone.vgs.cleantv"

        /** "Package Names", "App Sizes", "App Data Sizes", "Cache Sizes" của dumpsys diskstats → gói → [app, dữ liệu, cache]. */
        fun docDiskstats(d: String): List<Pair<String, LongArray>> {
            fun mang(ten: String): List<String> = Regex("""(?m)^$ten: \[(.*)\]""").find(d)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim().trim('"') }?.filter { it.isNotEmpty() } ?: emptyList()
            val ten = mang("Package Names"); val app = mang("App Sizes"); val du = mang("App Data Sizes"); val cache = mang("Cache Sizes")
            if (ten.isEmpty() || app.size != ten.size || du.size != ten.size) return emptyList()
            return ten.indices.map { i -> ten[i] to longArrayOf(app[i].toLongOrNull() ?: 0, du[i].toLongOrNull() ?: 0, cache.getOrNull(i)?.toLongOrNull() ?: 0) }
        }

        /** Tín hiệu reload_config của bản vá launcher (HomeKeyService.ACTION_RELOAD_CONFIG). */
        const val RELOAD_CONFIG = "zone.vgs.cleantv.RELOAD_CONFIG"

        /**
         * Chỗ trống tối thiểu để cài Home sạch. Android giữ lại phần "bộ nhớ thấp" (5% bộ nhớ, tối đa 500 MB) và từ chối
         * cài vào đó ("Requested internal only, but not enough space": máy ảo 9,7 GB còn 240 MB, 01/11/2026), cộng thêm
         * 30 MB cho APK + bản dịch tối ưu.
         */
        fun canTrong(tong: Long): Long = minOf(tong / 20, 500L * 1024 * 1024) + 30L * 1024 * 1024

        /** Android báo hết chỗ khi cài hoặc khi đẩy file. */
        private val THIEU_CHO = Regex("INSUFFICIENT_STORAGE|No space left|ENOSPC|not enough space", RegexOption.IGNORE_CASE)

        /** Đọc dòng cuối của `df -k` (toybox, Android 8+): cột 2 là tổng, cột 4 là còn trống, đơn vị KB. */
        fun docDf(df: String): Pair<Long, Long>? {
            val cot = df.trim().lines().lastOrNull()?.trim()?.split(Regex("\\s+")) ?: return null
            val tong = cot.getOrNull(1)?.toLongOrNull() ?: return null
            val trong = cot.getOrNull(3)?.toLongOrNull() ?: return null
            return trong * 1024 to tong * 1024
        }

        /** 12 MB / 1,4 GB. */
        fun mb(b: Long): String = if (b >= 1024L * 1024 * 1024) String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0).let { t(it.replace('.', ','), it) } else "${b / 1048576} MB"

        /**
         * App tạo ra mạng Wi-Fi ĐANG kết nối (cname trong khối cấu hình của SSID đó ở `dumpsys wifi`). null = không đang
         * dùng Wi-Fi hoặc không đọc được SSID; "" = thấy SSID nhưng không thấy khối cấu hình của nó.
         */
        fun chuWifiDangDung(dump: String): String? {
            val ssid = Regex("""mWifiInfo SSID: "([^"]*)"""").find(dump)?.groupValues?.get(1) ?: return null
            val dau = dump.indexOf("Configured networks Begin"); val cuoi = dump.indexOf("Configured networks End")
            if (dau < 0 || cuoi < dau) return ""
            val khoi = dump.substring(dau, cuoi).split(Regex("""\n(?=[\s*]*ID: \d+ )"""))
            // Cùng một tên Wi-Fi có thể lưu thành nhiều mạng (khác kiểu bảo mật): không biết đang nối mạng nào thì trả ""
            // để xét mọi mạng đã lưu (lỗ hổng @SaviorOneZero tìm ra ở Gathin23/cleanyourtv#1091, 23/10/2026).
            val trung = khoi.filter { it.contains("SSID: \"$ssid\"") }
            val cua = trung.singleOrNull() ?: return ""
            return Regex("""cname=([^ \n]+)""").find(cua)?.groupValues?.get(1) ?: ""
        }

        /** Lỗi kiểu kết nối ADB đã đứt (libadb: "connect() must be called first", luồng đóng, mạng reset). */
        fun laMatKetNoi(e: Throwable): Boolean {
            val m = listOfNotNull(e.message, e.cause?.message).joinToString(" ")
            return Regex("must be called first|not connected|Stream closed|closed|Broken pipe|EPIPE|reset|ECONNRESET|Socket", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
                e is java.io.EOFException || e is java.net.SocketException
        }

        /** Dịch vụ hệ thống không trả lời: tivi đang tắt máy / khởi động lại. */
        private val DICH_VU_CHET = Regex("Failure calling service|Can't find service|DeadObjectException|DeadSystemException")

        val TEN_GOI = Regex("""^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
        /** Tên máy / tên gói gợi ý máy chiếu (21/10/2026, Formovie Nemo). */
        val TU_MAY_CHIEU = Regex("projector|formovie|fengmi|xgimi|jmgo|dangbei|nebula|wanbo|byintek|keystone|autofocus|tixing|duijiao", RegexOption.IGNORE_CASE)
        const val PKG_HOME = "com.gathin.tvhome"
        const val PKG_HOME_XIAOMI = "com.ts.launcher"
        /** App Khoá trẻ em (module khoa/). */
        const val PKG_KHOA = "zone.vgs.cleantv.khoa"
        /** Bản sao lưu trên tivi: thư mục của shell, còn sau khi gỡ app / Home sạch, chỉ mất khi khôi phục cài đặt gốc. */
        const val TEP_SAO_LUU_TV = "/data/local/tmp/cleantv-sao-luu.json"
        const val GAC = "/data/local/tmp/cleantv-gac"
        const val HEN_TAT_ADB = "/data/local/tmp/cleantv-tat-adb"
        const val HEN_TAT_TV = "/data/local/tmp/cleantv-hen-tat"
        const val GAC_GIAY = 180
        /** Kịch bản canh gác chạy trên tivi: `sh cleantv-gac.sh <token>`. Token đổi / mất thì tự thoát. */
        val KICH_BAN_GAC = """
            |# CleanTV: canh gác Home. Hết hạn mà điện thoại không gia hạn thì bật lại Home vừa tắt.
            |G=$GAC
            |while :; do
            |  sleep 5
            |  [ "${'$'}(cat ${'$'}G-token 2>/dev/null)" = "${'$'}1" ] || exit 0
            |  [ "${'$'}(date +%s)" -ge "${'$'}(cat ${'$'}G-han 2>/dev/null || echo 0)" ] && break
            |done
            |for p in ${'$'}(cat ${'$'}G-list); do pm enable --user 0 ${'$'}p; done
            |h=${'$'}(head -n 1 ${'$'}G-list)
            |[ -n "${'$'}h" ] && cmd package set-home-activity --user 0 ${'$'}(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ${'$'}h | tail -n 1)
            |input keyevent KEYCODE_HOME
            |echo "${'$'}(date) ${'$'}1 ${'$'}(cat ${'$'}G-list | tr '\n' ' ')" >> ${'$'}G.log
            |rm -f ${'$'}G-token ${'$'}G-list ${'$'}G-han
            |""".trimMargin()
        /** Namespace Java của launcher: không đổi theo tên gói. */
        const val NS_HOME = "com.gathin.tvhome"
        val HOME_SACH = setOf(PKG_HOME, PKG_HOME_XIAOMI)
        const val SETUP = "com.google.android.tungsten.setupwraith"
        const val HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        val HOME_GOC_BIET = listOf(
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome",
        )
        /** Nhận Home nhưng không phải màn Home dùng được. */
        private val KHONG_PHAI_HOME = setOf(SETUP, "com.android.tv.settings", "com.android.settings", "com.android.provision")
        /**
         * App kiểm tra xuất xưởng cũng khai báo HOME (Xiaomi Mi TV 4A: com.duokan.factorytest/.FactoryLauncher) — không bao
         * giờ là Home của hãng, không tắt (theo upstream 0357546, 22/10/2026).
         */
        private val APP_XUAT_XUONG = Regex("factorytest|factory_test|factorymode", RegexOption.IGNORE_CASE)
    }
}

/** Một app trong danh sách chiếm chỗ: tổng byte (app + dữ liệu + cache), byte dữ liệu, có bản cập nhật gỡ được không. */
data class AppChiemCho(val pkg: String, val tong: Long, val duLieu: Long, val coCapNhat: Boolean)
