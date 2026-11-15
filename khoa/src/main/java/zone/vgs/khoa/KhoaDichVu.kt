package zone.vgs.khoa

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Locale

/**
 * Dịch vụ khoá (23/10/2026): trong khung giờ phụ huynh đặt, phủ một lớp kín màn hình (lớp trợ năng nằm trên mọi
 * app, kể cả HDMI) và nuốt mọi phím remote — chỉ còn số / mũi tên / OK để nhập PIN. Đúng PIN thì mở tạm
 * moTamPhut (mặc định 60). Sai 5 lần thì chờ 60 giây. Đang khoá thì dừng tiếng (giành audio focus + tắt tiếng nhạc).
 *
 * Khoá từng app (1.1, 15/11/2026): mở một app trong apps thì phủ màn PIN lên app đó. Đúng PIN thì dùng tiếp tới khi về
 * màn hình chính (một app nhận Home hiện lên) hoặc tắt màn hình; Back / Home thì về màn hình chính. Khoá theo giờ thắng.
 */
class KhoaDichVu : AccessibilityService() {
    companion object {
        @Volatile var dangChay: KhoaDichVu? = null
        @Volatile var dangKhoa = false
        private const val NHIP_MS = 15_000L
        private const val SAI_TOI_DA = 5
        private const val CHAN_MS = 60_000L
        /** Cài đặt có màn HOME dự phòng (FallbackHome) nhưng không phải màn hình chính: khoá được, không xoá appDaMo. */
        val GOI_CAI_DAT = setOf("com.android.tv.settings", "com.android.settings")
        private val PHIM = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
    }

    private val tay = Handler(Looper.getMainLooper())
    private lateinit var kho: Kho
    private var lop: View? = null
    private lateinit var tvPhu: TextView
    private lateinit var tvCham: TextView
    private lateinit var tvBao: TextView
    private val oPhim = mutableListOf<TextView>()
    private var chon = 4
    private var pin = ""
    private var focus: AudioFocusRequest? = null
    /** Gói đang bị màn PIN che (khoá từng app); null = màn khoá cả tivi hoặc không có lớp. */
    private var appDangChe: String? = null
    /** App đã mở đúng PIN, xoá khi về màn hình chính hoặc tắt màn hình. */
    private val appDaMo = mutableSetOf<String>()
    private var goiHome: Set<String> = emptySet()
    private val tatManHinh = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { tay.post { appDaMo.clear() } }
    }
    private val vi get() = Locale.getDefault().language == "vi"
    private fun t(v: String, e: String) = if (vi) v else e

    private val nhip = object : Runnable {
        override fun run() { capNhat(); tay.postDelayed(this, NHIP_MS) }
    }

    override fun onServiceConnected() {
        kho = Kho(this)
        dangChay = this
        CanhGac.hen(this)
        goiHome = docGoiHome()
        runCatching { registerReceiver(tatManHinh, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        tay.post(nhip)
    }

    /** Các app nhận Home (Home sạch, launcher gốc…): hiện lên nghĩa là người dùng đã về màn hình chính. */
    private fun docGoiHome(): Set<String> = runCatching {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() - GOI_CAI_DAT
    }.getOrDefault(emptySet())

    override fun onDestroy() {
        tay.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(tatManHinh) }
        anKhoa()
        dangChay = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val goi = e.packageName?.toString() ?: return
        if (goi == packageName) return
        if (goi in goiHome) { appDaMo.clear(); if (appDangChe != null) anKhoa(); return } // bấm Home lúc màn PIN đang che app
        if (lop != null) return // đang có màn khoá (cả tivi hoặc app khác)
        val c = kho.cauHinh ?: return
        if (Luat.khoaApp(c, goi) && goi !in appDaMo) hienKhoa(goi)
    }
    override fun onInterrupt() {}

    fun capNhatSau() = tay.post { capNhat() }

    private fun capNhat() {
        val c = kho.cauHinh
        val bay = Calendar.getInstance()
        val khoa = c != null && Luat.trongGio(c, bay) && System.currentTimeMillis() >= kho.moTamDen
        if (khoa && appDangChe != null) anKhoa() // khoá cả tivi thắng màn PIN của một app
        if (khoa && lop == null) hienKhoa()
        if (!khoa && lop != null && appDangChe == null) anKhoa()
        // Phụ huynh bỏ app khỏi danh sách (hoặc tắt khoá) trong lúc màn PIN đang che app đó.
        appDangChe?.let { g -> if (c == null || !Luat.khoaApp(c, g)) anKhoa() }
        if (khoa) {
            val het = Luat.hetKhoaLuc(c!!, bay)?.let { Luat.chuGio(it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)) }
            tvPhu.text = (if (het != null) t("Mở lại lúc $het. ", "Unlocks at $het. ") else "") +
                t("Nhập mã PIN để mở ${c.moTamPhut} phút.", "Enter the PIN to unlock for ${c.moTamPhut} minutes.")
            veChan()
        }
    }

    // ---------- Lớp khoá ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun chu(s: String, sp: Float, mau: Int = Color.WHITE, dam: Boolean = false) = TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(mau); gravity = Gravity.CENTER
        if (dam) typeface = Typeface.DEFAULT_BOLD
    }

    private fun tenApp(goi: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(goi, 0)).toString()
    }.getOrDefault(goi.substringAfterLast('.'))

    /** [app] null = khoá cả tivi theo giờ; có gói = màn PIN che riêng app đó. */
    private fun hienKhoa(app: String? = null) {
        pin = ""; chon = 4
        val cot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        cot.addView(chu("🔒", 44f))
        val tieuDe = if (app == null) t("Tivi đang nghỉ", "The TV is resting") else t("${tenApp(app)} đang khoá", "${tenApp(app)} is locked")
        cot.addView(chu(tieuDe, 34f, dam = true).apply { setPadding(0, dp(8), 0, dp(6)) })
        tvPhu = chu("", 18f, Color.parseColor("#C9C3FF")); cot.addView(tvPhu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tvCham = chu("", 34f).apply { letterSpacing = 0.4f; setPadding(0, dp(18), 0, dp(10)) }; cot.addView(tvCham)
        val luoi = GridLayout(this).apply { columnCount = 3 }
        oPhim.clear()
        PHIM.forEach { k ->
            val o = chu(k, 24f, dam = true).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dp(72); height = dp(50); setMargins(dp(5), dp(5), dp(5), dp(5)) }
            }
            oPhim += o; luoi.addView(o)
        }
        cot.addView(luoi, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        tvBao = chu("", 16f, Color.parseColor("#FF8FA3")).apply { setPadding(0, dp(12), 0, 0) }; cot.addView(tvBao)
        val chan = if (app == null) t("Phụ huynh đổi giờ khoá trong app CleanTV.", "Parents change the schedule in the CleanTV app.")
            else t("Phụ huynh chọn app khoá trong app CleanTV.", "Parents choose locked apps in the CleanTV app.")
        cot.addView(chu(chan, 13f, Color.parseColor("#8A84B8")).apply { setPadding(0, dp(18), 0, 0) })
        val nen = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#FF0A0718"))
            addView(cot, FrameLayout.LayoutParams(dp(640), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        // 1.1 (15/11/2026): lớp nhận tiêu điểm và tự đọc phím. Máy ảo Android TV 14 không chuyển phím nào cho bộ lọc
        // phím của dịch vụ, nên trước đó màn khoá hiện mà không gõ được PIN. Khoá cả tivi: bộ lọc (nếu chạy) vẫn nhận
        // trước và nuốt cả Home; không chạy thì lớp tự đọc phím, Home về màn hình chính nhưng lớp vẫn che.
        // Màn PIN của một app: bộ lọc nhường phím cho lớp; Home về màn hình chính thì onAccessibilityEvent gỡ lớp.
        val coTieuDiem = true
        nen.apply {
            isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, ma, ev -> if (ev.action == KeyEvent.ACTION_DOWN) xuLy(ma); true }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (if (coTieuDiem) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT)
        runCatching { getSystemService(WindowManager::class.java).addView(nen, lp) }.onSuccess {
            lop = nen; dangKhoa = true; appDangChe = app
            if (app == null) performGlobalAction(GLOBAL_ACTION_HOME) // app đang chạy lui về nền
            else tvPhu.text = t("Nhập mã PIN để mở app. Bấm Back để quay lại.", "Enter the PIN to open the app. Press Back to go back.")
            imTieng(true)
            if (coTieuDiem) nen.requestFocus()
            vePin(); veChon()
        }
    }

    private fun anKhoa() {
        lop?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        if (lop != null) imTieng(false)
        lop = null; dangKhoa = false; appDangChe = null
    }

    private fun imTieng(bat: Boolean) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (bat) {
                val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build()
                am.requestAudioFocus(r); focus = r
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                focus?.let { am.abandonAudioFocusRequest(it) }; focus = null
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
    }

    private fun vePin() { tvCham.text = (0 until 4).joinToString(" ") { if (it < pin.length) "●" else "○" } }

    private fun veChon() = oPhim.forEachIndexed { i, o ->
        o.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (i == chon) Color.parseColor("#8B5CF6") else Color.parseColor("#221B45"))
        }
    }

    private fun veChan() {
        val con = kho.chanDen - System.currentTimeMillis()
        if (con > 0) tvBao.text = t("Sai nhiều lần. Thử lại sau ${(con + 999) / 1000} giây.", "Too many tries. Try again in ${(con + 999) / 1000} s.")
    }

    // ---------- Phím remote ----------

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (lop == null || appDangChe != null) return false // màn PIN của app tự nhận phím (cửa sổ có tiêu điểm)
        if (e.action == KeyEvent.ACTION_DOWN) tay.post { xuLy(e.keyCode) }
        return true // đang khoá: nuốt mọi phím, kể cả Home, Back, nút app, giọng nói
    }

    private fun xuLy(k: Int) {
        if (lop == null) return
        when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> go(('0' + (k - KeyEvent.KEYCODE_0)).toString())
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> go(('0' + (k - KeyEvent.KEYCODE_NUMPAD_0)).toString())
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (chon % 3 > 0) chon--; veChon() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (chon % 3 < 2) chon++; veChon() }
            KeyEvent.KEYCODE_DPAD_UP -> { if (chon >= 3) chon -= 3; veChon() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (chon < 9) chon += 3; veChon() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> bam(PHIM[chon])
            // Màn PIN của một app: Back khi chưa gõ số / Home = thôi, về màn hình chính.
            KeyEvent.KEYCODE_HOME -> if (appDangChe != null) thoiMoApp()
            KeyEvent.KEYCODE_BACK -> if (appDangChe != null && pin.isEmpty()) thoiMoApp() else bam("⌫")
            KeyEvent.KEYCODE_DEL -> bam("⌫")
        }
    }

    private fun bam(nhan: String) = when (nhan) {
        "⌫" -> { pin = pin.dropLast(1); vePin() }
        "✓" -> if (pin.length == 4) thu() else Unit
        else -> go(nhan)
    }

    private fun go(so: String) {
        if (System.currentTimeMillis() < kho.chanDen) { veChan(); return }
        if (pin.length >= 4) return
        pin += so; tvBao.text = ""; vePin()
        if (pin.length == 4) tay.postDelayed({ thu() }, 150)
    }

    private fun thoiMoApp() {
        pin = ""; anKhoa()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun thu() {
        val c = kho.cauHinh ?: return
        if (Luat.dungPin(c, pin)) {
            kho.sai = 0
            appDangChe?.let { g -> appDaMo += g; pin = ""; anKhoa(); return }
            kho.moTamDen = System.currentTimeMillis() + c.moTamPhut * 60_000L
            pin = ""; anKhoa()
            return
        }
        pin = ""; vePin()
        kho.sai = kho.sai + 1
        if (kho.sai >= SAI_TOI_DA) { kho.sai = 0; kho.chanDen = System.currentTimeMillis() + CHAN_MS; veChan() }
        else tvBao.text = t("Sai mã PIN, còn ${SAI_TOI_DA - kho.sai} lần.", "Wrong PIN, ${SAI_TOI_DA - kho.sai} tries left.")
    }
}
