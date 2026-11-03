package zone.vgs.tvsach.ui

import zone.vgs.tvsach.adb.ThaoTacTv
import zone.vgs.tvsach.dulieu.KeHoach
import zone.vgs.tvsach.dulieu.XaPhuong
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.alpha
import zone.vgs.tvsach.AnhTivi
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import zone.vgs.tvsach.BuildConfig
import zone.vgs.tvsach.NgonNgu
import zone.vgs.tvsach.R
import zone.vgs.tvsach.dulieu.LoaiMuc
import zone.vgs.tvsach.dulieu.MucGoi
import zone.vgs.tvsach.dulieu.THEMES
import zone.vgs.tvsach.dulieu.HUONG_DAN
import zone.vgs.tvsach.dulieu.THANH_PHO
import zone.vgs.tvsach.dulieu.Theme
import zone.vgs.tvsach.dulieu.tenDep
import zone.vgs.tvsach.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Màu (gradient tím–hồng như logo) ----------------

private val Tim = Color(0xFF7C3AED)
private val Hong = Color(0xFFEC4899)
private val Xanh = Color(0xFF22C55E)
private val Cam = Color(0xFFF59E0B)
private val Nen = Color(0xFF0B0B10)
private val The1 = Color(0xFF15151C)
private val The2 = Color(0xFF1E1E28)
private val ChuPhu = Color(0xFFA1A1B5)
private val Vien = Color(0xFF2A2A36)
private val Gradient = Brush.linearGradient(listOf(Tim, Hong))
private val NenXam = Brush.linearGradient(listOf(The2, The2))

private val BangMau = darkColorScheme(
    primary = Tim, onPrimary = Color.White, secondary = Hong, background = Nen, surface = The1,
    surfaceVariant = The2, onSurfaceVariant = ChuPhu, outline = Vien,
)

// ---------------- Khung ----------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UngDung(vm: PhienLamViec = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { NgonNgu.nap(ctx) }
    // Quay lại từ nền (trình chọn file, app khác): nối lại tivi nếu điện thoại đã cắt kết nối.
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.kiemKetNoiKhiVe()
        }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe) }
    }
    MaterialTheme(colorScheme = BangMau) {
        // Chế độ tivi: đi bằng remote nên ô đang chọn phải thấy rõ — lớp sáng khi focus đậm hơn hẳn mặc định.
        val gon = if (zone.vgs.tvsach.ManHinh.laTv)
            androidx.compose.material3.RippleConfiguration(Color.White, androidx.compose.material.ripple.RippleAlpha(0.3f, 0.35f, 0.3f, 0.2f))
        else androidx.compose.material3.LocalRippleConfiguration.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalRippleConfiguration provides gon) {
        Surface(Modifier.fillMaxSize(), color = Nen) {
            BackHandler(enabled = vm.man != Man.CHINH && !vm.dangBan) { quayLai(vm) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight().systemBarsPadding().then(
                if (zone.vgs.tvsach.ManHinh.laTv) Modifier.widthIn(max = 820.dp).padding(top = 12.dp) else Modifier.fillMaxWidth())) {
                ThanhTren(vm)
                buoc(vm.man)?.let { ThanhBuoc(it) }
                Box(Modifier.weight(1f)) {
                    when (vm.man) {
                        Man.CHINH -> ManChinh(vm)
                        Man.KET_NOI -> ManKetNoi(vm)
                        Man.THONG_TIN -> ManThongTin(vm)
                        Man.CHON_GOI -> ManChonGoi(vm)
                        Man.DANG_TAT -> ManDangTat(vm)
                        Man.HOME_SACH -> ManHomeSach(vm)
                        Man.XONG -> ManXong(vm)
                        Man.KHOI_PHUC -> ManKhoiPhuc(vm)
                        Man.HUONG_DAN -> ManHuongDan(vm)
                        Man.GIAY_PHEP -> ManGiayPhep()
                        Man.KHOA_TRE -> ManKhoaTre(vm)
                        Man.REMOTE -> ManRemote(vm)
                    }
                }
                if (vm.loi.isNotEmpty() || (vm.dangBan && vm.man != Man.DANG_TAT)) ThanhTrangThai(vm)
                HopAppChiemCho(vm)
            }
            }
            vm.banMoi?.takeIf { vm.hoiCapNhat }?.let { b ->
                AlertDialog(
                    onDismissRequest = { vm.hoiCapNhat = false },
                    icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = Hong) },
                    title = { Text(t("Có bản mới ${b.versionName}", "CleanTV ${b.versionName} is out")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.ghiChu.isNotBlank()) Text(b.ghiChu)
                            Phu(t("Bạn đang dùng bản ${BuildConfig.VERSION_NAME}. Cập nhật mất chừng một phút, các tivi đã dọn vẫn giữ nguyên.",
                                "You're on ${BuildConfig.VERSION_NAME}. Updating takes about a minute; your cleaned TVs stay as they are."))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.hoiCapNhat = false; vm.capNhatApp() }) { Text(t("Cập nhật ngay", "Update now"), fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = { TextButton(onClick = { vm.hoiCapNhat = false }) { Text(t("Để sau", "Later")) } },
                    containerColor = The1,
                )
            }
            vm.fileTrenTv?.let { (loai, ds) ->
                AlertDialog(
                    onDismissRequest = { vm.fileTrenTv = null }, containerColor = The1,
                    title = { Text(if (loai == "apk") t("File APK trên tivi / USB", "APK files on the TV / USB") else t("File cấu hình trên tivi / USB", "Settings files on the TV / USB")) },
                    text = {
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            if (ds.isEmpty()) Phu(t("Không thấy file .$loai nào trong Download hay USB. Chép file vào USB rồi cắm vào tivi, hoặc tải về tivi.",
                                "No .$loai files in Download or on USB. Copy one to a USB stick and plug it into the TV, or download it to the TV."))
                            ds.forEach { f ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.chonFileTrenTv(f) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                    Text(f.substringAfterLast('/'), fontSize = 14.sp, maxLines = 1)
                                    Text(f.substringBeforeLast('/'), fontSize = 11.sp, color = ChuPhu, maxLines = 1)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { vm.fileTrenTv = null }) { Text(t("Đóng", "Close")) } },
                )
            }
            vm.anhTv?.let { anh ->
                val ctxAnh = LocalContext.current
                AlertDialog(
                    onDismissRequest = { vm.anhTv = null },
                    confirmButton = {
                        Row {
                            if (AnhTivi.luuDuoc) TextButton(onClick = {
                                val ok = AnhTivi.luu(ctxAnh, anh)
                                Toast.makeText(ctxAnh, if (ok) t("Đã lưu vào Ảnh › CleanTV", "Saved to Pictures › CleanTV") else t("Không lưu được ảnh", "Couldn't save the picture"), Toast.LENGTH_SHORT).show()
                            }) { Text(t("Lưu ảnh", "Save"), fontWeight = FontWeight.SemiBold) }
                            TextButton(onClick = { AnhTivi.chiaSe(ctxAnh, anh) }) { Text(t("Chia sẻ", "Share")) }
                            TextButton(onClick = { vm.anhTv = null }) { Text(t("Đóng", "Close")) }
                        }
                    },
                    title = { Text(t("Màn hình tivi", "TV screen")) },
                    text = { Image(anh.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))) },
                    containerColor = The1,
                )
            }
        }
    }
    }
}

private fun buoc(m: Man): Int? = when (m) {
    Man.KET_NOI -> 0
    Man.THONG_TIN, Man.CHON_GOI -> 1
    Man.DANG_TAT -> 2
    Man.HOME_SACH, Man.XONG -> 3
    else -> null
}

private fun quayLai(vm: PhienLamViec) {
    vm.loi = ""
    vm.man = when (vm.man) {
        Man.KET_NOI -> { vm.veManChinh(); Man.CHINH }
        Man.THONG_TIN, Man.KHOI_PHUC -> Man.KET_NOI
        Man.CHON_GOI -> Man.THONG_TIN
        Man.DANG_TAT -> Man.CHON_GOI
        Man.HOME_SACH -> if (vm.moNangCao) { vm.moNangCao = false; Man.HOME_SACH } else vm.manTruocHome
        Man.XONG -> Man.HOME_SACH
        Man.HUONG_DAN, Man.GIAY_PHEP -> vm.manTruoc
        Man.KHOA_TRE, Man.REMOTE -> Man.THONG_TIN
        Man.CHINH -> Man.CHINH
    }
}

@Composable
private fun ThanhTren(vm: PhienLamViec) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (vm.man != Man.CHINH) {
            IconButton(onClick = { if (!vm.dangBan) quayLai(vm) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t("Quay lại", "Back")) }
        } else Spacer(Modifier.width(12.dp))
        Image(painterResource(R.drawable.logo_cleantv), "CleanTV", Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text("Clean", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("TV", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Hong)
        Spacer(Modifier.weight(1f))
        val ten = vm.thongTin?.tenHienThi
        if (vm.man != Man.CHINH && ten != null) {
            Text(ten, fontSize = 12.sp, color = ChuPhu, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.width(12.dp))
        } else {
            Surface(onClick = { NgonNgu.doi(ctx) }, shape = RoundedCornerShape(50), color = The2, border = BorderStroke(1.dp, Vien)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = ChuPhu)
                    Spacer(Modifier.width(6.dp))
                    Text(if (NgonNgu.tiengViet) "VI" else "EN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Thanh 4 bước: Kết nối · Chọn · Dọn · Home. */
@Composable
private fun ThanhBuoc(dangO: Int) {
    val ten = listOf(t("Kết nối", "Connect"), t("Chọn", "Choose"), t("Dọn", "Clean"), "Home")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ten.forEachIndexed { i, s ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= dangO) Gradient else Brush.linearGradient(listOf(Vien, Vien))))
                Text(s, fontSize = 11.sp, color = if (i == dangO) Color.White else ChuPhu, modifier = Modifier.padding(top = 4.dp),
                    fontWeight = if (i == dangO) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** Hộp "app chiếm chỗ" khi tivi hết bộ nhớ: gỡ bản cập nhật / xoá dữ liệu từng app, có hỏi lại trước khi làm. */
@Composable
private fun HopAppChiemCho(vm: PhienLamViec) {
    val ds = vm.appChiemCho ?: return
    AlertDialog(onDismissRequest = { vm.appChiemCho = null },
        title = { Text(t("App chiếm nhiều chỗ nhất", "Apps using the most space")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Phu(t("Gỡ bản cập nhật: app về bản đi kèm tivi, mở lên nó tự cập nhật lại (nhẹ hơn). Xoá dữ liệu: app như mới cài, có thể phải đăng nhập lại. Dung lượng từng app do tivi tự tính, có thể chậm vài giờ.",
                    "Uninstall updates: the app goes back to the TV's own version and updates itself again when opened (smaller). Clear data: the app is like new, you may need to sign in again. App sizes come from the TV and may lag a few hours."))
                if (vm.thongBaoDon.isNotEmpty()) Phu(vm.thongBaoDon, Xanh)
                ds.forEach { a ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(10.dp)) {
                        Text(zone.vgs.tvsach.dulieu.tenDep(a.pkg) + " · " + ThaoTacTv.mb(a.tong), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (a.coCapNhat) TextButton(onClick = { vm.hoiDonApp = a.pkg to "cap-nhat" }, enabled = !vm.dangBan) { Text(t("Gỡ bản cập nhật", "Uninstall updates")) }
                            if (a.duLieu > 1024L * 1024) TextButton(onClick = { vm.hoiDonApp = a.pkg to "du-lieu" }, enabled = !vm.dangBan) { Text(t("Xoá dữ liệu (${ThaoTacTv.mb(a.duLieu)})", "Clear data (${ThaoTacTv.mb(a.duLieu)})"), color = Color(0xFFFF8A80)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.appChiemCho = null }) { Text(t("Xong", "Done")) } },
        containerColor = The1)
    vm.hoiDonApp?.let { (pkg, viec) ->
        val ten = zone.vgs.tvsach.dulieu.tenDep(pkg)
        AlertDialog(onDismissRequest = { vm.hoiDonApp = null },
            title = { Text(if (viec == "cap-nhat") t("Gỡ bản cập nhật $ten?", "Uninstall $ten updates?") else t("Xoá dữ liệu $ten?", "Clear $ten data?")) },
            text = { Text(if (viec == "cap-nhat") t("$ten về bản đi kèm tivi. Lần mở sau nó tự cập nhật lại.", "$ten goes back to the TV's own version and updates itself next time it opens.")
                else t("Cài đặt, tài khoản đăng nhập và nội dung tải về của $ten bị xoá.", "$ten's settings, sign-in and downloads are removed.")) },
            confirmButton = { TextButton(onClick = { vm.hoiDonApp = null; vm.donApp(pkg, viec) }) { Text(t("Làm", "Do it"), color = Color(0xFFFF8A80)) } },
            dismissButton = { TextButton(onClick = { vm.hoiDonApp = null }) { Text(t("Thôi", "Cancel")) } },
            containerColor = The1)
    }
}

@Composable
private fun ThanhTrangThai(vm: PhienLamViec) {
    val laLoi = vm.loi.isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
        .background(if (laLoi) Color(0xFF3A1320) else The2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (laLoi) Icon(Icons.Rounded.ErrorOutline, null, tint = Hong)
        else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (laLoi) vm.loi else vm.thongBao, fontSize = 14.sp)
            if (laLoi && vm.canDonBoNho) NutPhu(t("Dọn bộ nhớ đệm", "Clear cache"), Icons.Rounded.CleaningServices) { vm.donBoNhoDem() }
            if (laLoi && vm.canDonBoNho) NutPhu(t("Xem app chiếm chỗ", "See what uses the space"), Icons.Rounded.Apps) { vm.thongBaoDon = ""; vm.moAppChiemCho() }
        }
        if (laLoi && vm.man == Man.KET_NOI) TextButton(onClick = { vm.loi = ""; vm.moHuongDan() }) { Text(t("Hướng dẫn", "Guide")) }
        if (laLoi) TextButton(onClick = { vm.loi = "" }) { Text(t("Ẩn", "Hide")) }
    }
}

// ---------------- Mảnh dùng chung ----------------

@Composable
private fun Cuon(noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = noiDung)
}

@Composable
private fun The(tieuDe: String? = null, icon: ImageVector? = null, noiDung: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tieuDe != null) Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = Hong); Spacer(Modifier.width(8.dp)) }
            Text(tieuDe, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        noiDung()
    }
}

@Composable
private fun NutChinh(chu: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val mo = if (enabled) 1f else 0.4f
    Box(modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Gradient, alpha = mo)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White.copy(alpha = mo)); Spacer(Modifier.width(8.dp)) }
            Text(chu, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White.copy(alpha = mo))
        }
    }
}

@Composable
private fun NutPhu(chu: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Vien)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(chu, color = Color.White)
    }
}

@Composable
private fun Phu(chu: String, mau: Color = ChuPhu) = Text(chu, fontSize = 13.sp, color = mau, lineHeight = 19.sp)

/**
 * Ô nhập chữ khi chạy trên tivi (góp ý ngocnd156 29/10/2026): TextField nuốt phím lên/xuống của remote nên đóng
 * bàn phím xong là kẹt trong ô, không xuống được danh sách kết quả. Trên tivi lên/xuống chuyển sang mục kế bên.
 */
@Composable
private fun Modifier.thoatOTv(): Modifier {
    if (!zone.vgs.tvsach.ManHinh.laTv) return this
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    return onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            androidx.compose.ui.input.key.Key.DirectionDown -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
            androidx.compose.ui.input.key.Key.DirectionUp -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
            else -> false
        }
    }
}

@Composable
private fun OIcon(icon: ImageVector, size: Dp = 44.dp, nen: Brush = Gradient) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(nen), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun Chip(chu: String, mau: Color = ChuPhu) {
    Text(chu, fontSize = 12.sp, color = mau, modifier = Modifier.clip(RoundedCornerShape(50)).background(The2).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Mục gập/mở. */
@Composable
private fun Gap(tieuDe: String, icon: ImageVector, moSan: Boolean = false, noiDung: @Composable ColumnScope.() -> Unit) {
    var mo by rememberSaveable(icon.name) { mutableStateOf(moSan) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().clickable { mo = !mo }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Hong); Spacer(Modifier.width(10.dp))
            Text(tieuDe, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(if (mo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ChuPhu)
        }
        AnimatedVisibility(mo) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = noiDung)
        }
    }
}

@Composable
private fun BuocSo(so: Int, chu: String) {
    Row {
        Box(Modifier.size(24.dp).clip(CircleShape).background(The2), contentAlignment = Alignment.Center) {
            Text("$so", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Hong)
        }
        Spacer(Modifier.width(10.dp))
        Text(chu, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** Màn kết quả lớn ở giữa: icon + tiêu đề + dòng phụ. */
@Composable
private fun DauTrang(icon: @Composable () -> Unit, tieuDe: String, phu: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Text(tieuDe, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
        if (phu != null) Phu(phu)
    }
}

/** Mở hộp góp ý chung của vgs.zone (mục App CleanTV tự chọn theo chữ "CleanTV"), kèm bản app + model tivi. */
@Composable
private fun NutGopY(vm: PhienLamViec) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    NutPhu(t("Góp ý / báo lỗi", "Feedback / report a bug"), Icons.Rounded.Feedback) {
        val tu = "CleanTV ${BuildConfig.VERSION_NAME}" + (vm.thongTin?.let { " · ${it.tenHienThi} (${it.device}, Android ${it.android})" } ?: "")
        uri.openUri("https://vgs.zone/gop-y?tu=" + java.net.URLEncoder.encode(tu, "UTF-8"))
    }
}

private fun ngay(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

// ---------------- Màn chính ----------------

@Composable
private fun ManChinh(vm: PhienLamViec) = Cuon {
    if (vm.daTuTatAdb) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tim.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = Hong); Spacer(Modifier.width(10.dp))
        Text(t("ADB trên tivi đã tự tắt vì bạn rời app quá ${vm.phutTuTatAdb} phút. Muốn chỉnh tiếp thì vào Tuỳ chọn nhà phát triển trên tivi, bật lại Gỡ lỗi USB rồi kết nối lại.", "ADB on the TV turned itself off because you left the app for over ${vm.phutTuTatAdb} min. To keep tweaking, turn USB debugging back on in the TV's Developer options and connect again."), fontSize = 13.sp)
    }
    vm.banMoi?.let { b ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tim.copy(alpha = 0.18f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Hong); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Có bản mới ${b.versionName}", "Update ${b.versionName} available"), fontWeight = FontWeight.SemiBold)
                if (b.ghiChu.isNotBlank()) Phu(b.ghiChu)
            }
            TextButton(onClick = { vm.capNhatApp() }, enabled = !vm.dangCapNhat) {
                Text(if (vm.dangCapNhat) t("Đang tải…", "Downloading…") else t("Cập nhật", "Update"))
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Tim.copy(alpha = 0.35f), The1))).border(1.dp, Vien, RoundedCornerShape(24.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo_cleantv), null, Modifier.size(64.dp))
        Text(t("Tivi hết\nquảng cáo", "No more\nTV ads"), fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp)
        Phu(t("Kết nối tivi qua Wi-Fi, tắt quảng cáo với app rác của hãng, rồi thay màn Home cho gọn. Muốn trả lại như cũ thì bấm Khôi phục.",
            "Connect to the TV over Wi-Fi, switch off the maker's ads and junk apps, then give it a clean Home. Want it back? Press Restore."))
        NutChinh(t("Dọn một tivi", "Clean a TV"), icon = Icons.Rounded.AutoAwesome) { vm.datLai(); vm.man = Man.KET_NOI }
    }
    TheNhacNpt(vm)
    if (vm.dsSaoLuu.isNotEmpty()) {
        Text(t("Tivi đã dọn", "Cleaned TVs"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        vm.dsSaoLuu.forEach { b ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OIcon(Icons.Rounded.Tv, 42.dp, NenXam)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.ten, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Phu("${b.ip} · ${ngay(b.luc)}")
                    Phu(t("tắt ${b.daTatBoiApp.size} gói", "${b.daTatBoiApp.size} disabled") + if (b.launcherDaCai) t(" · Home sạch", " · Clean Home") else "")
                }
                TextButton(onClick = { vm.moKhoiPhuc(b) }) {
                    Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Khôi phục", "Restore"))
                }
            }
        }
    }
    TheCongDong()
    NutGopY(vm)
    TextButton(onClick = { vm.moGiayPhep() }) { Icon(Icons.Rounded.Gavel, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Giấy phép mã nguồn mở", "Open-source licenses")) }
    Phu(t("Dựa trên CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) của Gathin và cộng đồng. Danh sách tivi và màn Home sạch là của dự án đó.",
        "Built on CleanYourTV (github.com/Gathin23/cleanyourtv, MIT) by Gathin and contributors: the 213 TV lists and the clean Home come from that project.") +
        "\nCleanTV ${BuildConfig.VERSION_NAME} · AlexVGS")
}

// ---------------- Kết nối ----------------

@Composable
private fun Radar(dangQuet: Boolean) {
    val tr = rememberInfiniteTransition(label = "radar")
    val p by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    Box(Modifier.size(170.dp).drawBehind {
        val r = size.minDimension / 2
        if (dangQuet) for (k in 0..2) {
            val f = (p + k / 3f) % 1f
            drawCircle(Hong.copy(alpha = (1f - f) * 0.5f), radius = r * (0.35f + 0.65f * f), style = Stroke(2.dp.toPx()))
        }
        drawCircle(Vien, radius = r * 0.98f, style = Stroke(1.dp.toPx()))
        drawCircle(Vien, radius = r * 0.66f, style = Stroke(1.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        OIcon(Icons.Rounded.Tv, 64.dp)
    }
}

@Composable
private fun ManKetNoi(vm: PhienLamViec) = Cuon {
    if (zone.vgs.tvsach.ManHinh.laTv) { KetNoiTiviNay(vm); return@Cuon }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF3A2A10)).clickable { vm.moHuongDan() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Tivi đã bật Gỡ lỗi USB chưa?", "Is USB debugging on?"), fontWeight = FontWeight.SemiBold)
            Phu(t("Chưa bật thì app không kết nối được. Bấm để xem hướng dẫn có hình.", "Without it the app can't connect. Tap for the picture guide."), Color.White.copy(alpha = 0.8f))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Cam)
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Radar(vm.dangQuet || vm.dangBan)
        Text(
            when {
                vm.dangKhoiPhuc -> t("Kết nối lại tivi cần khôi phục", "Reconnect the TV to restore")
                vm.dangQuet -> t("Đang tìm tivi trong nhà…", "Looking for TVs…")
                else -> t("Tìm tivi trong nhà", "Find the TV")
            }, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Phu(t("Điện thoại và tivi chung Wi-Fi, tivi đã bật Gỡ lỗi USB.", "Phone and TV on the same Wi-Fi, USB debugging on."))
    }
    NutPhu(if (vm.dangQuet) t("Đang quét…", "Scanning…") else t("Quét mạng tìm tivi", "Scan the network"), Icons.Rounded.WifiFind, enabled = !vm.dangQuet) { vm.quetMang() }
    vm.timThay.forEach { dc ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OIcon(Icons.Rounded.Tv, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dc, fontWeight = FontWeight.SemiBold)
                Phu(t("Android TV · cổng 5555 mở", "Android TV · port 5555 open"))
            }
            Button(onClick = { vm.ip = dc; vm.cong = "5555"; vm.ketNoi() }, enabled = !vm.dangBan, shape = RoundedCornerShape(12.dp)) {
                Text(t("Kết nối", "Connect"))
            }
        }
    }
    Gap(t("Nhập IP tay", "Enter IP manually"), Icons.Rounded.Keyboard, moSan = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(vm.ip, { vm.ip = it.trim() }, label = { Text(t("IP tivi", "TV IP")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.thoatOTv().weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(vm.cong, { vm.cong = it.filter(Char::isDigit) }, label = { Text(t("Cổng", "Port")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.thoatOTv().width(100.dp), shape = RoundedCornerShape(12.dp))
        }
        NutChinh(t("Kết nối", "Connect"), enabled = !vm.dangBan) { vm.ketNoi() }
        Phu(t("IP tivi: Cài đặt → Mạng → mạng đang dùng. Lần đầu tivi hỏi \"Cho phép gỡ lỗi?\": tích Luôn cho phép rồi bấm Cho phép.",
            "TV IP: Settings → Network → current network. The first time the TV asks \"Allow debugging?\": tick Always allow and press Allow."))
    }
    Gap(t("Ghép đôi bằng mã (Gỡ lỗi không dây)", "Pair with code (Wireless debugging)"), Icons.Rounded.Link) {
        Phu(t("Trên tivi: Tuỳ chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã.", "On the TV: Developer options → Wireless debugging → Pair device with pairing code."))
        OutlinedTextField(vm.ghepIpCong, { vm.ghepIpCong = it.trim() }, label = { Text(t("IP:cổng ghép đôi", "Pairing IP:port")) }, placeholder = { Text("192.168.1.20:37123") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        OutlinedTextField(vm.ghepMa, { vm.ghepMa = it.filter(Char::isDigit).take(6) }, label = { Text(t("Mã 6 số", "6-digit code")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        NutPhu(t("Ghép đôi & kết nối", "Pair & connect"), Icons.Rounded.Link, enabled = !vm.dangBan) { vm.ghepDoi() }
    }
}

/** Chế độ tivi: app chạy ngay trên tivi, kết nối ADB vào chính nó. */
@Composable
private fun ColumnScope.KetNoiTiviNay(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OIcon(Icons.Rounded.Tv, 64.dp)
        Text(if (vm.dangKhoiPhuc) t("Kết nối lại để khôi phục tivi này", "Reconnect to restore this TV") else t("Dọn chính tivi này", "Clean this TV"),
            fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Phu(t("CleanTV đang chạy ngay trên tivi, không cần điện thoại.", "CleanTV is running on the TV itself, no phone needed."))
    }
    The(t("Trước khi bấm", "Before you start"), Icons.Rounded.Warning) {
        BuocSo(1, t("Tivi đã bật Gỡ lỗi USB (Tùy chọn nhà phát triển).", "USB debugging is on (Developer options)."))
        BuocSo(2, t("Bấm Kết nối. Tivi hiện \"Cho phép gỡ lỗi USB?\": chọn Luôn cho phép rồi bấm Cho phép.", "Press Connect. When the TV asks \"Allow USB debugging?\", tick Always allow and press Allow."))
        BuocSo(3, t("Trong lúc dọn, màn hình có thể nháy sang Home vài giây rồi tự quay lại app.", "While it works the screen may jump to Home for a few seconds and then come back."))
    }
    NutChinh(t("Kết nối tivi này", "Connect this TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Link) { vm.ketNoiTiviNay() }
    NutPhu(t("Hướng dẫn bật Gỡ lỗi USB có hình", "Picture guide to USB debugging"), Icons.Rounded.Warning) { vm.moHuongDan() }
    val ctxTv = androidx.compose.ui.platform.LocalContext.current
    NutPhu(t("Mở Tuỳ chọn nhà phát triển", "Open Developer options"), Icons.Rounded.Tune) {
        runCatching { ctxTv.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctxTv.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

// ---------------- Thông tin + chọn mẫu ----------------

/**
 * Remote trên điện thoại (3.5.0, góp ý build.dng 27/10/2026): phím điều hướng, âm lượng, nguồn; gõ / nói chữ vào ô đang
 * chọn trên tivi; hẹn giờ tắt; mở, gỡ, cài app.
 */
@Composable
private fun ManRemote(vm: PhienLamViec) = Cuon {
    val ctx = LocalContext.current
    @Composable
    fun NutTron(icon: ImageVector, mo: String, ma: Int, co: Dp = 56.dp, nen: Color = The2) {
        Box(Modifier.size(co).clip(CircleShape).background(nen).border(1.dp, Vien, CircleShape).clickable { vm.bamPhim(ma) }.semantics { contentDescription = mo },
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
    }
    if (vm.remoteBao.isNotEmpty()) Phu(vm.remoteBao, Cam)
    // Chạy ngay trên tivi (29/10/2026): app đang chiếm màn hình nên phím và gõ chữ gửi vào chính nó, ẩn đi; giữ
    // hẹn giờ tắt và app trên tivi.
    if (!zone.vgs.tvsach.ManHinh.laTv) {
        Phu(t("Remote đi qua Wi-Fi bằng gỡ lỗi (ADB): lần sau mở CleanTV → Dọn một tivi → Kết nối → Remote. Đã tắt ADB thì bật lại Gỡ lỗi trên tivi.",
            "The remote works over Wi-Fi through debugging (ADB): next time open CleanTV → Clean a TV → Connect → Remote. If ADB was turned off, enable debugging on the TV again."))
        The {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NutTron(Icons.Rounded.PowerSettingsNew, t("Nguồn", "Power"), 26, nen = Color(0xFF4A1414))
                NutTron(Icons.Rounded.Home, "Home", 3)
                NutTron(Icons.AutoMirrored.Rounded.ArrowBack, t("Quay lại", "Back"), 4)
                NutTron(Icons.Rounded.Menu, "Menu", 82)
            }
            // Vòng điều hướng: lên / xuống / trái / phải quanh nút OK.
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(232.dp).clip(CircleShape).background(The2).border(1.dp, Vien, CircleShape)) {
                    @Composable
                    fun Mui(icon: ImageVector, mo: String, ma: Int, canh: Alignment) =
                        Box(Modifier.align(canh).size(76.dp).clip(CircleShape).clickable { vm.bamPhim(ma) }.semantics { contentDescription = mo },
                            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(36.dp), tint = Color.White) }
                    Mui(Icons.Rounded.KeyboardArrowUp, t("Lên", "Up"), 19, Alignment.TopCenter)
                    Mui(Icons.Rounded.KeyboardArrowDown, t("Xuống", "Down"), 20, Alignment.BottomCenter)
                    Mui(Icons.Rounded.KeyboardArrowLeft, t("Trái", "Left"), 21, Alignment.CenterStart)
                    Mui(Icons.Rounded.KeyboardArrowRight, t("Phải", "Right"), 22, Alignment.CenterEnd)
                    Box(Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Gradient).clickable { vm.bamPhim(23) },
                        contentAlignment = Alignment.Center) { Text("OK", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NutTron(Icons.Rounded.VolumeDown, t("Nhỏ tiếng", "Volume down"), 25)
                NutTron(Icons.Rounded.VolumeOff, t("Tắt tiếng", "Mute"), 164)
                NutTron(Icons.Rounded.VolumeUp, t("To tiếng", "Volume up"), 24)
                NutTron(Icons.Rounded.PlayArrow, t("Phát / dừng", "Play / pause"), 85)
            }
        }
        // Gõ chữ / nói: chữ vào ô đang chọn trên tivi (tìm YouTube, mật khẩu Wi-Fi…).
        val noi = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
            r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.chuGo = vm.chuGo + it; vm.guiChu() }
        }
        The(t("Gõ chữ lên tivi", "Type on the TV"), Icons.Rounded.Keyboard) {
            Phu(t("Chọn ô chữ trên tivi trước (ô tìm kiếm của YouTube, mật khẩu Wi-Fi…), rồi gõ hoặc nói ở đây.", "Pick a text field on the TV first (YouTube search, a Wi-Fi password…), then type or speak here."))
            OutlinedTextField(vm.chuGo, { vm.chuGo = it }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                label = { Text(t("Chữ cần gõ", "Text to type")) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { vm.guiChu() }),
                trailingIcon = {
                    IconButton(onClick = {
                        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (zone.vgs.tvsach.NgonNgu.tiengViet) "vi-VN" else java.util.Locale.getDefault().toLanguageTag())
                            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, t("Nói để gõ lên tivi", "Speak to type on the TV"))
                        runCatching { noi.launch(i) }.onFailure { Toast.makeText(ctx, t("Điện thoại không có nhận giọng nói", "No speech recognition on this phone"), Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Rounded.Mic, t("Nói", "Speak"), tint = Hong) }
                })
            NutPhu(t("Gửi lên tivi", "Send to the TV"), Icons.AutoMirrored.Rounded.Send) { vm.guiChu() }
            NutPhu(t("Gửi rồi bấm Enter (tìm luôn)", "Send, then Enter (search)"), Icons.AutoMirrored.Rounded.KeyboardReturn) { vm.guiChu(xuongDong = true) }
            NutPhu(t("Xoá lùi một chữ trên tivi", "Delete one letter on the TV"), Icons.AutoMirrored.Rounded.Backspace) { vm.bamPhim(67) }
            if (!vm.goCoDau) {
                Phu(t("Đang gõ kiểu không dấu. Gõ tiếng Việt có dấu cần Home sạch và dịch vụ bắt phím của nó.", "Typing plain letters only. Accented text needs the clean Home's key helper."))
                if (vm.daCoHomeSach) NutPhu(t("Bật gõ tiếng Việt có dấu", "Turn on accented typing"), Icons.Rounded.Translate, enabled = !vm.dangBan) { vm.batGoCoDau() }
            }
        }
    }
    The(t("Hẹn giờ tắt tivi", "Sleep timer"), Icons.Rounded.Bedtime) {
        val het = vm.henTatLuc
        Phu(if (het != null) t("Tivi sẽ tắt lúc ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(het * 1000))}.", "The TV switches off at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(het * 1000))}.")
            else if (zone.vgs.tvsach.ManHinh.laTv) t("Tivi tự đếm giờ, thoát CleanTV vẫn tắt đúng hẹn.", "The TV counts down itself; leaving CleanTV doesn't stop it.")
            else t("Tivi tự đếm giờ, đóng app điện thoại vẫn tắt đúng hẹn.", "The TV counts down itself; closing the phone app doesn't stop it."))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { p -> ChonChip(t("$p phút", "$p min"), false) { vm.henTat(p) } }
            if (het != null) ChonChip(t("Huỷ hẹn", "Cancel"), true) { vm.henTat(0) }
        }
    }
    val chonApk = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.caiApkLenTv(uri) }
    The(t("App trên tivi", "Apps on the TV"), Icons.Rounded.Apps) {
        if (zone.vgs.tvsach.ManHinh.laTv) NutPhu(t("Cài file APK có trên tivi / USB", "Install an APK from the TV / USB"), Icons.Rounded.InstallMobile, enabled = !vm.dangBan) { vm.moFileTrenTv("apk") }
        else NutPhu(t("Cài file APK từ điện thoại", "Install an APK from this phone"), Icons.Rounded.InstallMobile, enabled = !vm.dangBan) {
            runCatching { chonApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
        }
        vm.appTrenTv.forEach { pkg ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zone.vgs.tvsach.dulieu.tenDep(pkg), fontSize = 15.sp)
                    Text(pkg, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
                }
                TextButton(onClick = { vm.moAppTv(pkg) }) { Text(t("Mở", "Open")) }
                if (pkg in vm.appTuCai) TextButton(onClick = { vm.hoiGo = pkg }, enabled = !vm.dangBan) { Text(t("Gỡ", "Uninstall"), color = Color(0xFFFF8A80)) }
            }
        }
        Phu(t("Chỉ gỡ được app bạn tự cài; app hệ thống thì dùng phần Dọn của CleanTV (tắt được, bật lại được).", "Only apps you installed can be uninstalled; for system apps use CleanTV's cleanup (disable, re-enable later)."))
    }
    vm.hoiGo?.let { pkg ->
        AlertDialog(onDismissRequest = { vm.hoiGo = null },
            title = { Text(t("Gỡ ${zone.vgs.tvsach.dulieu.tenDep(pkg)}?", "Uninstall ${zone.vgs.tvsach.dulieu.tenDep(pkg)}?")) },
            text = { Text(t("App và dữ liệu của nó bị xoá khỏi tivi. Muốn dùng lại phải cài lại.", "The app and its data are removed from the TV. You'd have to install it again.")) },
            confirmButton = { TextButton(onClick = { vm.hoiGo = null; vm.goAppTv(pkg) }) { Text(t("Gỡ", "Uninstall"), color = Color(0xFFFF8A80)) } },
            dismissButton = { TextButton(onClick = { vm.hoiGo = null }) { Text(t("Thôi", "Cancel")) } },
            containerColor = The1)
    }
}

@Composable
private fun ManThongTin(vm: PhienLamViec) = Cuon {
    val info = vm.thongTin ?: return@Cuon
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Tim.copy(alpha = 0.3f), The1)))
        .border(1.dp, Vien, RoundedCornerShape(24.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        OIcon(Icons.Rounded.Tv, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Xanh); Spacer(Modifier.width(4.dp))
                Text(t("Đã kết nối", "Connected"), fontSize = 12.sp, color = Xanh)
            }
            Text(info.tenHienThi, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Android ${info.android}")
                vm.trangThai?.let { Chip(t("${it.caiDat.size} gói", "${it.caiDat.size} packages")) }
                vm.boNho?.let { (trong, tong) -> Chip(t("Trống ${ThaoTacTv.mb(trong)}", "${ThaoTacTv.mb(trong)} free"), if (trong < ThaoTacTv.canTrong(tong)) Hong else ChuPhu) }
            }
        }
    }
    if (vm.khongPhaiTivi) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF4A1414)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF8A80)); Spacer(Modifier.width(8.dp))
            Phu(t("Máy này không khai là tivi Android (có thể là điện thoại hoặc máy tính bảng). CleanTV chỉ dành cho tivi: dọn nhầm gói của điện thoại có thể làm máy không khởi động được.",
                "This device does not report itself as an Android TV (it may be a phone or tablet). CleanTV is only for TVs: disabling a phone's packages can stop it from booting."), Color.White)
        }
        Hang(t("Tôi chắc đây là tivi / hộp tivi / máy chiếu", "I am sure this is a TV / TV box / projector"),
            t("Chỉ bật nếu đúng là thiết bị xem tivi", "Only if it really is a TV device"), vm.xacNhanLaTivi) { vm.xacNhanLaTivi = it }
    }
    if (vm.muiGioLech.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A10)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Icon(Icons.Rounded.Schedule, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Phu(t("Giờ trên tivi theo múi giờ ${vm.muiGioLech}, lệch với điện thoại (${vm.muiGioDienThoai}), nên đồng hồ Home sạch hiện sai. Tivi bản Trung Quốc hay bị vậy và giấu mục chỉnh giờ.",
                "The TV uses the ${vm.muiGioLech} time zone, unlike your phone (${vm.muiGioDienThoai}), so the clean Home shows the wrong time. China-market TVs often do and hide the setting."), Color.White)
        }
        NutPhu(t("Đặt giờ tivi theo điện thoại", "Use the phone's time zone on the TV"), Icons.Rounded.Schedule) { vm.datGioNhuDienThoai() }
    }
    if (vm.laAndroidCu && !vm.laFireTv) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A10)).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Máy này chạy Android ${vm.thongTin?.android}: CleanTV dọn được quảng cáo, theo dõi và app rác, nhưng KHÔNG cài được Home sạch (cần Android 8 trở lên).",
            "This device runs Android ${vm.thongTin?.android}: CleanTV can clean ads, tracking and junk apps but can NOT install the clean Home (needs Android 8 or later)."))
    }
    if (vm.laFireTv) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A10)).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Fire TV: CleanTV dọn được quảng cáo, theo dõi và app rác, nhưng KHÔNG thay được màn Home. Amazon khoá Home cho launcher của họ, không app nào thay được.",
            "Fire TV: CleanTV can clean ads, tracking and junk apps but can NOT replace Home. Amazon locks Home to its own launcher; no app can change it."))
    }
    if (info.adbSecure == "0") Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A10)).padding(12.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
        Phu(t("Tivi mở ADB không cần xác nhận: ai cùng mạng cũng điều khiển được. Nhớ tắt ADB khi xong.",
            "This TV accepts ADB without a prompt: anyone on the network can control it. Turn ADB off when done."), Color.White)
    }
    vm.saoLuu?.let {
        Phu(t("Tivi này đã có bản sao lưu từ ${ngay(it.luc)}", "A backup from ${ngay(it.luc)} exists") +
            (if (vm.saoLuuTuTv) t(" (lấy từ bản lưu trên tivi)", " (read from the copy on the TV)") else "") +
            t(". Làm tiếp vẫn giữ mốc khôi phục cũ.", ". Continuing keeps that restore point."))
    }
    The(t("Danh sách mẫu", "Model list"), Icons.Rounded.FactCheck) {
        if (vm.ungVien.isEmpty()) Phu(t("Chưa có mẫu nào khớp đúng tivi này. App dùng luật chung: chỉ tích sẵn gói đã tắt trên nhiều tivi khác và không ai ghi là phải giữ.",
            "No tested list matches this TV. The app uses general rules: it pre-ticks only packages disabled on many other TVs that nobody marked as needed."))
        vm.ungVien.forEach { (m, diem) ->
            LuaChon(vm.mauChon == m, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("độ khớp", "match") + " $diem") { vm.mauChon = m }
        }
        if (vm.ganGiong.isNotEmpty()) {
            Phu(t("Mẫu gần giống (cùng hãng, tên máy khác một chút). Chọn nếu đúng dòng tivi của bạn:", "Close matches (same brand, slightly different model name). Pick one if it is your TV's line:"))
            vm.ganGiong.forEach { m -> LuaChon(vm.mauChon == m, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("gần giống", "close match")) { vm.mauChon = m } }
        }
        // Mẫu chọn tay từ hộp "Chọn mẫu khác" (không nằm trong danh sách trên) vẫn hiện để thấy đang dùng mẫu nào.
        vm.mauChon?.takeIf { c -> vm.ungVien.none { it.first == c } && c !in vm.ganGiong }?.let { m ->
            LuaChon(true, m.ten.take(90), "${m.goi.size} " + t("gói", "packages") + " · " + t("bạn chọn", "your pick")) {}
        }
        LuaChon(vm.mauChon == null, t("Không dùng mẫu, chỉ luật chung", "No list, general rules only"), null) { vm.mauChon = null }
        NutPhu(t("Chọn mẫu khác (${vm.tatCaMau.size} mẫu)", "Pick another list (${vm.tatCaMau.size})"), Icons.Rounded.Search) { vm.moChonMau = true }
    }
    if (vm.moChonMau) HopChonMau(vm)
    TheMayChieu(vm)
    NutChinh(t("Xem danh sách gói", "See packages"), enabled = !vm.dangBan && !vm.chanKhongPhaiTivi, icon = Icons.AutoMirrored.Rounded.ArrowForward) { vm.lapKeHoach() }
    NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
    if (zone.vgs.tvsach.ManHinh.laTv) NutPhu(t("Hẹn giờ tắt & app trên tivi", "Sleep timer & apps on the TV"), Icons.Rounded.Apps) { vm.moRemote() }
    else NutPhu(t("Remote trên điện thoại", "Phone remote"), Icons.Rounded.SettingsRemote) { vm.moRemote() }
    if (vm.daCoHomeSach) NutPhu(t("Đổi giao diện Home sạch", "Change the clean Home theme"), Icons.Rounded.Palette) { vm.moDoiTheme() }
    NutPhu(t("Khoá trẻ em (hẹn giờ + mã PIN)", "Kids lock (schedule + PIN)"), Icons.Rounded.Lock) { vm.moKhoaTre() }
    if (vm.saoLuu != null) NutPhu(t("Khôi phục tivi này", "Restore this TV"), Icons.Rounded.Restore) { vm.man = Man.KHOI_PHUC }
    else if (vm.coDauCleanTv) NutPhu(t("Khôi phục (không có sao lưu)", "Restore (no backup)"), Icons.Rounded.Restore) { vm.man = Man.KHOI_PHUC }
    else NutPhu(t("Bật lại app đã tắt", "Re-enable disabled apps"), Icons.Rounded.Restore) { vm.man = Man.KHOI_PHUC }
    // CleanTV chạy trên tivi (29/10/2026, anh Alex): báo lỗi ngay sau khi kết nối, không phải dọn / Áp dụng tới màn Xong.
    // App đã có ADB vào chính tivi nên gửi đủ chẩn đoán như bên điện thoại.
    if (zone.vgs.tvsach.ManHinh.laTv) TheGuiDanhSach(vm, baoLoi = true)
}

@Composable
private fun LuaChon(chon: Boolean, chu: String, phu: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (chon) Tim.copy(alpha = 0.18f) else The2)
        .border(1.dp, if (chon) Tim else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(chu, fontSize = 14.sp)
            if (phu != null) Phu(phu)
        }
        RadioButton(chon, onClick)
    }
}

// ---------------- Chọn gói ----------------

@Composable
private fun ManChonGoi(vm: PhienLamViec) {
    val nhom = vm.muc.groupBy { it.loai }
    val so = vm.soDaChon()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$so", fontWeight = FontWeight.Black, fontSize = 40.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("gói sẽ tắt", "to disable") + " / ${vm.muc.size}", color = ChuPhu, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    LinearProgressIndicator(progress = { if (vm.muc.isEmpty()) 0f else so.toFloat() / vm.muc.size },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Hong, trackColor = The2)
                    Phu(t("Đã tích sẵn gói an toàn. App có biểu tượng thì hỏi chủ nhà. Gói tivi cần (HDMI, remote, bàn phím, CH Play) app tự giữ, không hiện ở đây.",
                        "Safe packages are pre-ticked. Ask the owner about apps with icons. Packages the TV needs (HDMI, remote, keyboard, Play Store) are kept and not listed."))
                }
            }
            LoaiMuc.entries.forEach { loai ->
                val ds = nhom[loai].orEmpty()
                if (ds.isEmpty()) return@forEach
                item(key = "h-$loai") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(loai.tieuDe, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Phu(loai.moTa)
                        }
                        val tatCa = ds.all { vm.chon[it.pkg] == true }
                        TextButton(onClick = { ds.forEach { vm.chon[it.pkg] = !tatCa } }) {
                            Text(if (tatCa) t("Bỏ hết", "None") else t("Chọn hết", "All"))
                        }
                    }
                }
                items(ds, key = { it.pkg }) { m -> DongGoi(m, vm.chon[m.pkg] == true) { vm.chon[m.pkg] = it } }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.epSetupwraith = !vm.epSetupwraith }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(vm.epSetupwraith, { vm.epSetupwraith = it })
                    Phu(t("Tivi cắm dây LAN (cho tắt setupwraith dù nó giữ Wi-Fi)", "TV is on Ethernet (allow disabling setupwraith even if it owns the Wi-Fi)"))
                }
            }
        }
        var hoi by remember { mutableStateOf(false) }
        Column(Modifier.background(Nen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NutChinh(t("Sao lưu & dọn $so gói", "Back up & clean $so packages"), enabled = !vm.dangBan && so > 0, icon = Icons.Rounded.CleaningServices) { hoi = true }
            NutXemLenh { vm.lenhDon() }
            if (!vm.khongCaiHome) NutPhu(if (vm.daCoHomeSach) t("Bỏ qua, chỉ đổi giao diện", "Skip, just change the theme") else t("Bỏ qua, chỉ cài Home sạch", "Skip, just install the clean Home"),
                Icons.Rounded.Home, enabled = !vm.dangBan) { vm.boQuaDon() }
        }
        val nenGiu = vm.muc.filter { vm.chon[it.pkg] == true && KeHoach.nenGiu(it) }
        if (hoi && nenGiu.isNotEmpty()) AlertDialog(
            onDismissRequest = { hoi = false }, containerColor = The1,
            icon = { Icon(Icons.Rounded.Warning, null, tint = Cam) },
            title = { Text(t("Khoan đã: ${nenGiu.size} gói nhiều tivi phải giữ", "Wait: ${nenGiu.size} packages most TVs keep")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t("Bạn tự tích các gói dưới đây, nhưng nhiều danh sách đã thử ghi là PHẢI GIỮ (tắt có thể mất nút Input, HDMI, remote, Cài đặt…):",
                        "You ticked these, but many tested lists say KEEP them (disabling can break the Input key, HDMI, the remote, Settings…):"))
                    nenGiu.take(8).forEach { Text("• ${tenDep(it.pkg)} (${it.pkg}): " + t("${it.soMauGiu} danh sách giữ", "kept by ${it.soMauGiu} lists"), fontSize = 13.sp, color = ChuPhu) }
                    if (nenGiu.size > 8) Text(t("… và ${nenGiu.size - 8} gói nữa", "… and ${nenGiu.size - 8} more"), fontSize = 13.sp, color = ChuPhu)
                }
            },
            confirmButton = { TextButton(onClick = { nenGiu.forEach { vm.chon[it.pkg] = false }; hoi = false; vm.batDauTat() }) { Text(t("Bỏ tích các gói này rồi dọn", "Untick them and clean"), fontWeight = FontWeight.SemiBold) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { hoi = false; vm.batDauTat() }) { Text(t("Vẫn tắt cả", "Disable all anyway"), color = Cam) }
                    TextButton(onClick = { hoi = false }) { Text(t("Thôi", "Cancel")) }
                }
            },
        )
        else if (hoi) AlertDialog(
            onDismissRequest = { hoi = false }, containerColor = The1,
            icon = { Icon(Icons.Rounded.Shield, null, tint = Hong) },
            title = { Text(t("Dọn $so gói?", "Clean $so packages?")) },
            text = { Text(t("App lưu trạng thái tivi vào điện thoại trước, rồi tắt từng gói (không gỡ). Khôi phục được bất cứ lúc nào.",
                "The app saves the TV's state to this phone first, then disables each package (nothing uninstalled). You can restore any time.")) },
            confirmButton = { TextButton(onClick = { hoi = false; vm.batDauTat() }) { Text(t("Dọn", "Clean")) } },
            dismissButton = { TextButton(onClick = { hoi = false }) { Text(t("Thôi", "Cancel")) } },
        )
    }
}

@Composable
private fun DongGoi(m: MucGoi, chon: Boolean, doi: (Boolean) -> Unit) {
    val ten = tenDep(m.pkg)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(The1)
        .border(1.dp, if (chon) Tim.copy(alpha = 0.6f) else Vien, RoundedCornerShape(16.dp)).clickable { doi(!chon) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(The2), contentAlignment = Alignment.Center) {
            Text(ten.take(1).uppercase(), fontWeight = FontWeight.Bold, color = if (chon) Hong else ChuPhu)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ten, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text(m.pkg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
            val ghi = m.ghiChu.ifBlank { m.nhom }
            if (ghi.isNotBlank()) Text(ghi, fontSize = 12.sp, color = ChuPhu, maxLines = 2, lineHeight = 16.sp)
            if (m.soMauTat > 0 || m.soMauGiu > 0) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.soMauTat > 0) Chip(t("tắt trên ${m.soMauTat} mẫu", "off on ${m.soMauTat} models"))
                if (m.soMauGiu > 0) Chip(t("⚠ ${m.soMauGiu} mẫu nên giữ", "⚠ kept on ${m.soMauGiu}"), Cam)
            }
        }
        Checkbox(chon, doi)
    }
}

// ---------------- Đang dọn / kết quả ----------------

@Composable
private fun ManDangTat(vm: PhienLamViec) = Cuon {
    val kq = vm.ketQua
    if (kq == null) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                val p = vm.tienDo
                if (p <= 0f) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Hong, trackColor = The2)
                else CircularProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = Hong, trackColor = The2)
                Text(if (p <= 0f) "…" else "${(p * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
            Text(t("Đang dọn tivi", "Cleaning the TV"), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(vm.thongBao, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ChuPhu, textAlign = TextAlign.Center, maxLines = 2)
            Phu(t("Đừng tắt tivi hay đổi Wi-Fi lúc này.", "Don't turn off the TV or change Wi-Fi now."))
        }
    } else {
        DauTrang({
            Box(Modifier.size(84.dp).clip(CircleShape).background(Xanh.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(52.dp), tint = Xanh)
            }
        }, t("Đã dọn ${kq.daTat.size} gói", "Cleaned ${kq.daTat.size} packages"), null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Chip(t("${kq.boQua.size} giữ lại", "${kq.boQua.size} kept"), Cam)
            Spacer(Modifier.width(8.dp))
            Chip(t("${kq.loi.size} lỗi", "${kq.loi.size} failed"), if (kq.loi.isEmpty()) ChuPhu else Hong)
        }
        if (kq.boQua.isNotEmpty()) Gap(t("Giữ lại để an toàn (${kq.boQua.size})", "Kept for safety (${kq.boQua.size})"), Icons.Rounded.Shield, moSan = true) {
            kq.boQua.forEach { (p, ly) -> Column { Text(tenDep(p), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Phu(ly) } }
        }
        if (kq.loi.isNotEmpty()) Gap(t("Không tắt được (${kq.loi.size})", "Could not disable (${kq.loi.size})"), Icons.Rounded.ErrorOutline) {
            kq.loi.forEach { (p, ly) -> Text("$p: $ly", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            Phu(t("Thường là gói hệ thống được bảo vệ, bỏ qua được.", "Usually protected system packages; safe to ignore."))
        }
        The(t("Thử trên tivi", "Check on the TV"), Icons.Rounded.Tv) {
            Phu(t("Bấm Home, mở YouTube/Netflix, chuyển HDMI, mở Cài đặt, thử remote và giọng nói.", "Press Home, open YouTube/Netflix, switch HDMI, open Settings, try the remote and voice."))
            NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        }
        if (vm.khongCaiHome) NutChinh(if (vm.laFireTv) t("Xong (Fire TV không thay được Home)", "Done (Fire TV can't change Home)") else t("Xong (máy này không cài được Home sạch)", "Done (no clean Home on this device)"), icon = Icons.Rounded.CheckCircle) { vm.man = Man.XONG }
        else {
            NutChinh(t("Tiếp: Home sạch", "Next: clean Home"), icon = Icons.Rounded.Home) { vm.man = Man.HOME_SACH }
            NutPhu(t("Bỏ qua Home sạch", "Skip clean Home")) { vm.man = Man.XONG }
        }
        NutGopY(vm)
    }
}

// ---------------- Home sạch ----------------

/**
 * Màn Home sạch / Đổi giao diện (làm lại 23/10/2026 theo bản phác anh Alex duyệt): ảnh xem trước ở đầu, ba thẻ
 * Giao diện · Home · Tuỳ chỉnh, nút Áp dụng dính đáy; Nâng cao là màn riêng (ManNangCao).
 */
@Composable
private fun ManHomeSach(vm: PhienLamViec) {
    if (vm.moNangCao) { ManNangCao(vm); return }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XemTruocHome(vm)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("Giao diện", "Look"), t("Home", "Home"), t("Tuỳ chỉnh", "Options")).forEachIndexed { i, ten ->
                    val chon = vm.theHome == i
                    Text(ten, fontSize = 14.sp, fontWeight = if (chon) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (chon) Tim.copy(alpha = 0.3f) else The2)
                            .border(1.dp, if (chon) Tim else Vien, RoundedCornerShape(50)).clickable { vm.theHome = i }.padding(vertical = 10.dp))
                }
            }
            when (vm.theHome) {
                0 -> TheGiaoDien(vm)
                1 -> TheHomeNoiDung(vm)
                else -> TheTuyChinh(vm)
            }
            vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
        }
        // Nút dính đáy: đổi thẻ nào cũng bấm được, không phải cuộn hết trang.
        Column(Modifier.fillMaxWidth().background(The1).padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (vm.daCoHomeSach) NutChinh(if (vm.dangBan) t("Đang áp dụng…", "Applying…") else t("Áp dụng lên tivi", "Apply to the TV"),
                enabled = !vm.dangBan, icon = Icons.Rounded.Palette) { vm.apDungTheme() }
            else NutChinh(if (vm.dangBan) t("Đang cài…", "Installing…") else t("Cài Home sạch", "Install clean Home"), enabled = !vm.dangBan, icon = Icons.Rounded.Home) { vm.caiHomeSach() }
            NutXemLenh { vm.lenhHome() }
        }
    }
}

/** Ảnh xem trước Home: ảnh riêng / ảnh theme / dải màu, đồng hồ, dòng âm lịch + thời tiết, dock giả. */
@Composable
private fun XemTruocHome(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val th = vm.themeChon
    val anhRieng = vm.anhNenRieng
    val anh = remember(th.id, anhRieng) {
        runCatching {
            if (anhRieng != null) android.graphics.BitmapFactory.decodeByteArray(anhRieng, 0, anhRieng.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
            else (th.anhNen ?: th.anhXem)?.let { a -> ctx.assets.open(a).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) } }
        }.getOrNull()?.asImageBitmap()
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(th.mau.map { Color(it) })).border(1.dp, Vien, RoundedCornerShape(16.dp))) {
        if (anh != null) Image(anh, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        if (vm.lamToiNen > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = vm.lamToiNen / 100f)))
        val mauDau = vm.mauChu?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.White
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            if (vm.tenChao.isNotBlank()) Text(t("XIN CHÀO, ", "HELLO, ") + vm.tenChao.uppercase(), fontSize = 9.sp, color = mauDau.copy(alpha = 0.8f), maxLines = 1)
            Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()), fontSize = 26.sp, fontWeight = FontWeight.Light, color = mauDau)
            val dong = listOfNotNull(if (vm.amLich) t("âm lịch", "lunar date") else null, vm.thanhPho?.ten).joinToString(" · ")
            if (dong.isNotEmpty()) Text(dong, fontSize = 10.sp, color = mauDau.copy(alpha = 0.85f), maxLines = 1)
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { i -> Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(if (i == 0) Color.White else Color.White.copy(alpha = if (i < vm.dock.size) 0.6f else 0.25f))) }
        }
    }
    Phu(t(th.tenVi, th.tenEn) + " · " + t(th.moTaVi, th.moTaEn) + if (anhRieng != null) t(" · ảnh của bạn", " · your picture") else "")
}

@Composable
private fun TheGiaoDien(vm: PhienLamViec) {
    The {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { th -> TheTheme(th, th == vm.themeChon) { vm.chonTheme(th) } }
        }
        // Ảnh của chính mình làm hình nền (góp ý 23/10/2026): thay ảnh / bộ ảnh của theme, giữ màu và hiệu ứng theme.
        val chonNhieuAnh = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(ANH_XOAY_TOI_DA)) { uris ->
            if (uris.isNotEmpty()) vm.themAnhXoay(uris)
        }
        val chonAnh = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.chonAnhNen(uri)
        }
        // Chạy CleanTV ngay trên tivi: thường không có trình chọn ảnh (TCL báo "không có ứng dụng phù hợp", 24/10/2026)
        // → chọn ảnh có sẵn trên tivi / USB qua shell.
        val trenTv = zone.vgs.tvsach.ManHinh.laTv || vm.ip.trim() == "127.0.0.1"
        // Vừa khung, không cắt (3.0.0, góp ý hieuinter): chọn trước hoặc sau khi chọn ảnh đều được.
        Hang(t("Ảnh riêng vừa khung, không cắt", "Fit your picture, no cropping"),
            t("Cả ảnh nằm trọn trên màn, hai bên là chính ảnh làm mờ. Chọn ảnh dọc thì tự bật; tắt đi để ảnh phủ kín màn (cắt bớt trên dưới)", "The whole picture fits, with a blurred copy at the sides. On by itself for portrait shots; turn it off to fill the screen (cropping top and bottom)"),
            vm.anhVuaKhung) { vm.doiVuaKhung(it) }
        if (vm.anhNenRieng == null) {
            if (!trenTv) NutPhu(t("Dùng ảnh của bạn làm hình nền", "Use your own picture as wallpaper"), Icons.Rounded.Image) {
                runCatching { chonAnh.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .onFailure { vm.moAnhTrenTv() }
            }
            NutPhu(t("Chọn ảnh có trên tivi / USB", "Pick a picture on the TV / USB"), Icons.Rounded.Image) { vm.moAnhTrenTv() }
        }
        vm.anhTrenTv?.let { ds ->
            AlertDialog(
                onDismissRequest = { vm.anhTrenTv = null }, containerColor = The1,
                title = { Text(t("Ảnh trên tivi / USB", "Pictures on the TV / USB")) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        if (ds.isEmpty()) Phu(t("Không thấy ảnh nào trong Download, Pictures, DCIM hay USB. Chép ảnh JPG vào USB rồi cắm vào tivi, hoặc tải ảnh về tivi.",
                            "No pictures in Download, Pictures, DCIM or on USB. Copy a JPG to a USB stick and plug it into the TV, or download one to the TV."))
                        ds.forEach { f ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.chonAnhTrenTv(f) }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                Text(f.substringAfterLast('/'), fontSize = 14.sp, maxLines = 1)
                                Text(f.substringBeforeLast('/'), fontSize = 11.sp, color = ChuPhu, maxLines = 1)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.anhTrenTv = null }) { Text(t("Đóng", "Close")) } },
            )
        }
        if (vm.anhNenRieng == null) Unit
        else {
            // Bộ ảnh riêng tự đổi (3.5.0, góp ý build.dng): thêm ảnh sau ảnh chính, tivi đổi lần lượt theo số phút chọn.
            if (!trenTv && vm.anhXoay.size < ANH_XOAY_TOI_DA) NutPhu(
                if (vm.anhXoay.isEmpty()) t("Thêm ảnh để hình nền tự đổi", "Add pictures to rotate the wallpaper")
                else t("Thêm ảnh (đang có ${vm.anhXoay.size + 1} ảnh tự đổi)", "Add pictures (${vm.anhXoay.size + 1} rotating)"), Icons.Rounded.Image) {
                runCatching { chonNhieuAnh.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            if (vm.anhXoay.isNotEmpty()) {
                Text(t("Đổi ảnh sau", "Change picture every"), fontSize = 14.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { p -> ChonChip(t("$p phút", "$p min"), vm.phutDoiAnh == p) { vm.phutDoiAnh = p } }
                }
                NutPhu(t("Bỏ ${vm.anhXoay.size} ảnh thêm, giữ một ảnh", "Remove the ${vm.anhXoay.size} extra pictures"), Icons.Rounded.Close) { vm.anhXoay = emptyList() }
            }
            NutPhu(t("Bỏ ảnh riêng (${vm.anhNenRieng!!.size / 1024} KB), dùng ảnh của theme", "Remove your picture (${vm.anhNenRieng!!.size / 1024} KB)"), Icons.Rounded.Close) { vm.boAnhRieng() }
        }
        Text(t("Làm tối hình nền", "Dim the wallpaper"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 20, 40).forEach { p -> ChonChip(if (p == 0) t("Không", "None") else "$p%", vm.lamToiNen == p) { vm.lamToiNen = p } }
        }
    }
    if (vm.dsNangCao.isNotEmpty()) {
        val daDat = vm.nangCao.count { it.value.isNotBlank() }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp))
            .clickable { vm.moNangCao = true }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp), tint = Hong)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Nâng cao · ${vm.dsNangCao.size} tuỳ chọn", "Advanced · ${vm.dsNangCao.size} options"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Phu(if (daDat > 0) t("$daDat mục đã đổi", "$daDat changed") else t("Chỉnh sâu hơn: hiệu ứng, lưới app, remote…", "For tinkerers: effects, grid, remote…"))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = ChuPhu)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TheHomeNoiDung(vm: PhienLamViec) {
    The(t("Dock (tối đa $DOCK_TOI_DA)", "Dock (up to $DOCK_TOI_DA)"), Icons.Rounded.Apps) {
        // Thứ tự trên dock, dời bằng ↑ ↓ (góp ý daohoangthong1 23/10/2026: không biết cách đổi thứ tự).
        if (vm.dock.isNotEmpty()) {
            Text(t("Thứ tự trên dock", "Dock order"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            vm.dock.toList().forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tim.copy(alpha = 0.15f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Hong, modifier = Modifier.width(22.dp))
                    Text(tenDep(p), fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { vm.dichDock(p, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, t("Lên", "Up")) }
                    IconButton(onClick = { vm.dichDock(p, 1) }, enabled = i < vm.dock.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, t("Xuống", "Down")) }
                }
            }
            Text(t("Chọn app cho dock", "Pick dock apps"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        }
        Phu(t("Bấm để thêm / bỏ khỏi dock, giữ để sao chép tên package, nút mắt để ẩn app khỏi Home.",
            "Tap to add or remove from the dock, hold to copy the package name, the eye button hides the app from Home."))
        val boNho = androidx.compose.ui.platform.LocalClipboardManager.current
        val ctxDock = LocalContext.current
        vm.appChoDock().forEach { p ->
            val viTri = vm.dock.indexOf(p)
            val an = vm.daAn(p)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (viTri >= 0) Tim.copy(alpha = 0.2f) else The2)
                .border(1.dp, if (viTri >= 0) Tim else Color.Transparent, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { if (!an) vm.doiDock(p) }, onLongClick = {
                    boNho.setText(androidx.compose.ui.text.AnnotatedString(p))
                    Toast.makeText(ctxDock, t("Đã sao chép $p", "Copied $p"), Toast.LENGTH_SHORT).show()
                }).padding(start = 12.dp, top = 12.dp, bottom = 12.dp).alpha(if (an) 0.5f else 1f),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (viTri >= 0) Gradient else Brush.linearGradient(listOf(Vien, Vien))),
                    contentAlignment = Alignment.Center) {
                    Text(if (viTri >= 0) "${viTri + 1}" else "", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tenDep(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (an) t("Đã ẩn khỏi Home · $p", "Hidden from Home · $p") else p, fontSize = 11.sp, color = ChuPhu, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                IconButton(onClick = { vm.doiAn(p) }) {
                    Icon(if (an) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (an) t("Hiện lại trên Home", "Show on Home") else t("Ẩn khỏi Home", "Hide from Home"))
                }
            }
        }
    }
    if (vm.moChonKenh) HopChonKenh(vm)
    if (vm.dsIptv != null) HopChonIptv(vm)
    The(t("Kênh truyền hình trên màn chính", "TV channels on Home"), Icons.Rounded.LiveTv) {
        Phu(t("Mỗi kênh thành một ô có logo ở đầu dock, bấm là xem luôn, ông bà khỏi phải tìm. Tối đa $KENH_TOI_DA ô, lấy từ VTVgo, FPT Play, TV360 hay danh sách IPTV của bạn.",
            "Each channel becomes a tile with its logo at the start of the dock that plays it at once. Up to $KENH_TOI_DA, from VTVgo, FPT Play, TV360 or your own IPTV list."))
        // Các ô đã chọn (mọi nguồn), bấm để bỏ.
        if (vm.kenhChon.isNotEmpty()) {
            Text(t("Đã chọn ${vm.kenhChon.size}/$KENH_TOI_DA (bấm để bỏ)", "${vm.kenhChon.size}/$KENH_TOI_DA picked (tap to remove)"), fontSize = 13.sp, color = ChuPhu)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.kenhChon.toList().forEach { o -> ChonChip(o.ten, true) { vm.batTatO(o) } }
            }
        }
        Text("VTVgo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (vm.coVtvGo) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KENH_VTV.forEach { (ten, ma, _) -> ChonChip(ten, vm.coKenh(ma.toString())) { vm.batTatKenh(ten, ma.toString()) } }
            }
            NutPhu(t("Thêm kênh khác trên VTVgo (địa phương, VTVcab…)", "More VTVgo channels (local, VTVcab…)"), Icons.Rounded.Search) { vm.moHopKenh() }
        } else Phu(t("Cần app VTVgo trên tivi: vào Google Play trên tivi tìm \"VTVgo\", cài và mở một lần, rồi kết nối lại CleanTV.",
            "Needs the VTVgo app on the TV: install it from Google Play on the TV, open it once, then reconnect CleanTV."))
        // FPT Play (3.1.0): kênh có sẵn + dán link chia sẻ (kênh, trận đấu, phim).
        Text("FPT Play", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (vm.coFpt) {
            listOf("qg" to t("Toàn quốc, HTV", "National, HTV"), "dp" to t("Địa phương", "Local")).forEach { (nhom, tenNhom) ->
                Text(tenNhom, fontSize = 13.sp, color = ChuPhu)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zone.vgs.tvsach.dulieu.FptPlay.KENH.filter { it.third == nhom }.forEach { (ten, slug) -> ChonChip(ten, vm.coO(zone.vgs.tvsach.dulieu.FptPlay.linkKenh(slug))) { vm.batTatKenhFpt(ten, slug) } }
                }
            }
            OutlinedTextField(vm.linkFpt, { vm.linkFpt = it }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                label = { Text(t("Dán link FPT Play (kênh, trận đấu, phim)", "Paste an FPT Play link (channel, match, film)")) },
                placeholder = { Text("fptplay.vn/su-kien/…") })
            NutPhu(t("Thêm ô từ link này", "Add a tile from this link"), Icons.Rounded.AddLink, enabled = vm.linkFpt.isNotBlank() && !vm.dangBan) { vm.themLinkFpt() }
            Phu(t("Lấy link: trong app hoặc web FPT Play, mở kênh / trận / phim → Chia sẻ → Sao chép link. Nội dung cần gói FPT thì tivi phải đăng nhập FPT Play.",
                "Get a link: in the FPT Play app or website open the channel / match / film → Share → Copy link. Paid content needs an FPT Play login on the TV."))
        } else Phu(t("Cần app \"FPT Play for Android TV\" trên tivi (Google Play trên tivi tìm \"FPT Play\"), rồi kết nối lại CleanTV.",
            "Needs \"FPT Play for Android TV\" on the TV (search \"FPT Play\" in Google Play on the TV), then reconnect CleanTV."))
        // TV360 (3.5.14): kênh có sẵn, mở thẳng bằng tv360://recommendation (bản 6.4 trở lên).
        Text("TV360", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (vm.coTv360) {
            listOf("qg" to t("Toàn quốc, HTV, VTVcab", "National, HTV, VTVcab"), "dp" to t("Địa phương", "Local")).forEach { (nhom, tenNhom) ->
                Text(tenNhom, fontSize = 13.sp, color = ChuPhu)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zone.vgs.tvsach.dulieu.Tv360.KENH.filter { it.nhom == nhom }.forEach { k ->
                        ChonChip(k.ten + if (k.khoa) " 🔒" else "", vm.coO(zone.vgs.tvsach.dulieu.Tv360.uri(k.id))) { vm.batTatKenhTv360(k) }
                    }
                }
            }
            Phu(t("Cần TV360 bản 6.4 trở lên trên tivi (bản cũ chỉ mở trang chủ TV360). Kênh có ổ khoá cần đăng nhập TV360 trên tivi.",
                "Needs TV360 6.4 or later on the TV (older versions only open TV360's home). Locked channels need a TV360 login on the TV."))
        } else Phu(t("Cần app \"TV360 SmartTV\" bản 6.4 trở lên trên tivi (Google Play trên tivi tìm \"TV360\"), rồi kết nối lại CleanTV.",
            "Needs \"TV360 SmartTV\" 6.4 or later on the TV (search \"TV360\" in Google Play on the TV), then reconnect CleanTV."))
        // IPTV cá nhân (3.1.0): danh sách m3u, mở bằng trình phát video trên tivi.
        Text(t("Danh sách IPTV của bạn", "Your IPTV list"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (vm.trinhPhatCo.isEmpty()) Phu(t("Cần một trình phát video trên tivi: VLC (Google Play trên tivi tìm \"VLC\"), MX Player, Just Player hoặc Kodi, rồi kết nối lại CleanTV.",
            "Needs a video player on the TV: VLC (search \"VLC\" in Google Play on the TV), MX Player, Just Player or Kodi, then reconnect CleanTV."))
        else {
            if (vm.trinhPhatCo.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.trinhPhatCo.forEach { (goi, ten) -> ChonChip(t("Mở bằng $ten", "Play with $ten"), vm.trinhPhat == goi) { vm.trinhPhatChon = goi } }
            }
            OutlinedTextField(vm.nguonIptv, { vm.nguonIptv = it }, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 4,
                label = { Text(t("Link danh sách m3u (hoặc dán nội dung)", "m3u list link (or paste its content)")) },
                placeholder = { Text("https://…/playlist.m3u") })
            NutPhu(t("Mở danh sách và chọn kênh", "Open the list and pick channels"), Icons.Rounded.PlaylistAdd, enabled = vm.nguonIptv.isNotBlank() && !vm.dangBan) { vm.moIptv() }
            Phu(t("Lần đầu mở, VLC có thể xin quyền truy cập bộ nhớ: bật cho VLC rồi bấm lại ô kênh. Link IPTV cần thêm mật khẩu riêng thì có thể không phát được.",
                "On first use VLC may ask for storage access: allow it, then press the tile again. IPTV links that need extra headers may not play."))
        }
    }
    The(t("Thời tiết", "Weather"), Icons.Rounded.WbSunny) {
        val banPhimXa = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        OutlinedTextField(vm.timXa, { vm.doiTimXa(it) }, label = { Text(t("Tìm phường / xã hoặc thành phố", "Find your ward or city")) },
            placeholder = { Text(t("vd: Bến Thành, Cửa Lò, Bali, Osaka", "e.g. Ben Thanh, Bali, Osaka")) },
            // Bấm ✓ trên bàn phím: chỉ đóng bàn phím, giữ ô đang chọn (thoatOTv lo phím xuống tới kết quả). Chuyển focus
            // ngay trong onDone trên tivi nhảy quá kết quả sang ô Tên chào (thử emulator 29/10/2026).
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { banPhimXa?.hide() }),
            singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        vm.ketQuaXa.forEach { x ->
            Text("${x.ten} · ${x.tinh}", fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable { vm.chonXa(x) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        if (vm.baoTimXa.isNotEmpty()) Phu(vm.baoTimXa)
        Phu(t("Hoặc chọn nhanh thành phố:", "Or pick a city:"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Không hiện", "Off"), vm.thanhPho == null) { vm.thanhPho = null }
            vm.thanhPho?.takeIf { it !in THANH_PHO }?.let { tp -> ChonChip(tp.ten, true) { } }
            THANH_PHO.forEach { tp -> ChonChip(tp.ten, vm.thanhPho == tp) { vm.thanhPho = tp } }
        }
        Phu(t("Dữ liệu thời tiết: Open-Meteo.com (CC BY 4.0), tivi tự lấy nửa giờ một lần. Toạ độ phường/xã © OpenStreetMap.", "Weather data: Open-Meteo.com (CC BY 4.0), refreshed by the TV every 30 minutes. Ward locations © OpenStreetMap."))
    }
    The(t("Đồng hồ & lời chào", "Clock & greeting"), Icons.Rounded.Schedule) {
        OutlinedTextField(vm.tenChao, { vm.tenChao = it }, label = { Text(t("Tên chào (vd: Nhà bác Hùng)", "Greeting name (e.g. The Smiths)")) },
            singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Hang(t("Nút hẹn giờ tắt tivi", "Sleep timer button"), t("Một nút trên cùng: bấm lần lượt 15, 30, 60, 90 phút, bấm nữa là huỷ", "A top button: each press sets 15, 30, 60, 90 minutes, then off"), vm.nutHenTat) { vm.nutHenTat = it }
        Hang(t("Chữ giờ, ngày trắng sáng", "Bright white clock and date"), t("Dòng ngày tháng trắng hẳn thay vì xám nhạt, dễ đọc từ xa", "The date line in full white instead of soft grey, easier to read from afar"), vm.chuTrang) { vm.chuTrang = it }
        // Màu chữ đồng hồ, ngày, thời tiết (header_color; góp ý nggh, tranhoangnt555, Nam Tran 26–27/10/2026).
        Text(t("Màu chữ đồng hồ & thời tiết", "Clock & weather text colour"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Mặc định", "Default"), vm.mauChu == null) { vm.mauChu = null }
            MAU_CHU.forEach { (vi, en, ma) -> ChonChip(t(vi, en), vm.mauChu == ma) { vm.mauChu = ma } }
        }
        Hang(t("Khung mờ sau đồng hồ", "Panel behind the clock"), t("Một khung tối mờ sau đồng hồ, ngày, thời tiết (sáng mờ nếu chữ màu đen), dễ đọc trên ảnh nền sáng", "A soft dark panel behind the clock, date and weather (light if the text is black), readable on a bright wallpaper"), vm.khungDau) { vm.khungDau = it }
        Hang(t("Ẩn lời chào", "Hide the greeting"), t("Bỏ dòng \"Chào buổi tối\" phía trên đồng hồ", "Drops the \"Good evening\" line above the clock"), vm.anLoiChao) { vm.anLoiChao = it }
        Hang(t("Nút Wi-Fi, Cài đặt sát mép trên", "Wi-Fi, Settings buttons at the very top"), t("Không tụt xuống theo đồng hồ to và dòng ngày, thời tiết", "They no longer drop with a large clock and the date and weather lines"), vm.nutTrenCung) { vm.nutTrenCung = it }
        Hang(t("Âm lịch dưới ngày dương", "Lunar date under the date"), t("Ví dụ: 2/9 âm lịch · Bính Ngọ", "e.g. Lunar 2/9 · Year of the Horse"), vm.amLich) { vm.amLich = it }
        Text(t("Đồng hồ khi để yên", "Clock when idle"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 15, 30).forEach { p -> ChonChip(if (p == 0) t("Tắt", "Off") else t("$p phút", "$p min"), vm.dongHoKhiYen == p) { vm.dongHoKhiYen = p } }
        }
    }
    TheMayChieu(vm)
}

@Composable
private fun TheTuyChinh(vm: PhienLamViec) {
    The(t("Hiển thị", "Display"), Icons.Rounded.Visibility) {
        Hang(t("Tên app to phía trên dock", "Large app name above the dock"), t("Tắt để Home gọn hơn, chỉ còn viền sáng", "Turn off for a calmer Home, just the glow"), vm.tenToTrenDock) { vm.tenToTrenDock = it }
        Hang(t("Hiện tên dưới mọi app", "Names under every app"), t("Ông bà dễ nhận app hơn", "Easier to recognise apps"), vm.tenDuoiApp) { vm.tenDuoiApp = it }
        Hang(t("Tô màu ô app điện thoại", "Colour phone-app tiles"), t("App không có ảnh tivi (Chrome, Thư viện…) có ô màu theo icon thay vì nền xám", "Apps without a TV banner get a tile in their icon's colour"), vm.toMauOApp) { vm.toMauOApp = it }
        Hang(t("Icon to trong ô app", "Big icons in app tiles"), t("App không có ảnh tivi: icon gần kín chiều cao ô, bỏ chữ trong ô", "Apps without a TV banner: the icon fills most of the tile's height, no name inside"), vm.iconTo) { vm.iconTo = it }
        Hang(t("Tất cả app một hàng", "Every app in one row"), t("Một hàng cuộn ngang, không còn lưới bên dưới", "One sideways-scrolling row, no grid below"), vm.motHang) { vm.motHang = it }
        Hang(t("Ô icon đều nhau", "Uniform icon tiles"), t("Mọi ô là icon trên nền màu của app. Tắt để dùng lại ảnh banner của app (cả với giao diện Apple TV)", "Every tile is the icon on its own colour. Turn off for the apps' banners (Apple TV look too)"), vm.oDeuNhau) { vm.oDeuNhau = it }
        Hang(t("Gom nút trên cùng thành icon", "Compact icon buttons"), t("Một cụm gọn, bỏ chữ", "One tidy cluster, no labels"), vm.gonNut) { vm.gonNut = it }
        Text(t("Cỡ chữ đồng hồ, lịch, thời tiết", "Clock, date and weather size"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100 to t("Vừa", "Normal"), 125 to t("Lớn", "Large"), 150 to t("Rất lớn", "Extra large")).forEach { (v, chu) ->
                ChonChip(chu, vm.coChuDau == v) { vm.coChuDau = v }
            }
        }
        Text(t("Số app mỗi hàng", "Apps per row"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to t("Theo theme", "Theme's"), 4 to "4", 5 to "5", 6 to "6", 7 to "7").forEach { (v, chu) ->
                ChonChip(chu, vm.soCot == v) { vm.soCot = v }
            }
        }
        Text(t("Vị trí hàng app", "App rows position"), fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HANG_GIUA to t("Giữa màn hình", "Middle"), -120 to t("Cao hơn nhiều", "Much higher"), -60 to t("Cao hơn", "Higher"), 0 to t("Mặc định", "Default"), 60 to t("Thấp hơn", "Lower"), 300 to t("Thấp nhất", "Lowest")).forEach { (v, chu) ->
                ChonChip(chu, vm.doiHang == v) { vm.doiHang = v }
            }
        }
        Hang(t("Mở một app khi bật tivi", "Open an app at power-on"), t("Bật, Áp dụng lên tivi, rồi bấm nút bên dưới để chọn app ngay trên tivi. Chọn được cả ô kênh (📺 VTV1…) để bật tivi là vào thẳng kênh", "Turn on, apply to the TV, then use the button below to pick the app on the TV. Channel tiles (📺 VTV1…) work too, to power on straight into a channel"), vm.moKhiBat) { vm.moKhiBat = it }
        Hang(t("Luôn mở Home sạch khi bật tivi", "Always open the clean Home at power-on"),
            t("Cho tivi tự mở lại Home gốc, HDMI hay app cũ mỗi lần bật: khoảng 1 giây sau Home sạch hiện lên", "For TVs that come back to the stock Home, HDMI or the last app: the clean Home comes up a second after power-on"),
            vm.luonVeHome) { vm.luonVeHome = it }
        if (vm.moKhiBat) Hang(t("Cả khi bật lại từ chế độ chờ", "Also when waking from standby"),
            t("Bấm nút nguồn remote để bật là mở app luôn, không chỉ lúc khởi động lại tivi", "Turning the TV on with the remote opens the app too, not only after a restart"),
            vm.moKhiThuc) { vm.moKhiThuc = it }
        if (vm.moKhiBat) NutPhu(t("Mở màn chọn app trên tivi", "Open the app picker on the TV"), Icons.Rounded.Tv) { vm.moChonAppKhiBat() }
        if (vm.moKhiBat && vm.baoChonApp.isNotEmpty()) Phu(vm.baoChonApp, Xanh)
        Hang(t("Nút Đang phát", "Now playing button"), t("Hiện khi có app đang phát nhạc / video: tạm dừng từng app hay dừng tất cả", "Shows while apps play: pause each or stop them all"), vm.nutDangPhat) { vm.nutDangPhat = it }
        Hang(t("Dừng tiếng app cũ khi về Home", "Silence the app behind Home"), t("Bấm Home hay tắt tivi là app đang phát phim / nhạc tự dừng, như Home gốc. Tắt nếu muốn nhạc chạy tiếp khi ra Home", "Pressing Home or turning the TV off pauses whatever was playing, like the stock Home. Turn off to keep music going on Home"), vm.dungTiengNen) { vm.dungTiengNen = it }
        Hang(t("Màn chờ động", "Moving screensaver"), t("Đồng hồ trên ảnh nền trôi chậm, nhiều ảnh thì 20 giây đổi một ảnh. Hiện khi để yên ở Home sạch, và làm màn chờ của tivi", "A clock over your wallpaper drifting slowly, changing every 20 s with several pictures. Shows when Home is left idle, and as the TV's screensaver"), vm.manChoDong) { vm.manChoDong = it }
        if (vm.manChoDong) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5 to t("Để yên 5 phút", "Idle 5 min"), 10 to t("10 phút", "10 min"), 15 to t("15 phút", "15 min"), 30 to t("30 phút", "30 min"), 0 to t("Chỉ màn chờ của tivi", "TV's screensaver only")).forEach { (v, chu) ->
                ChonChip(chu, vm.phutManCho == v) { vm.phutManCho = v }
            }
        }
        Hang(t("Nút màn hình chờ", "Screensaver button"), t("Bấm là bật ảnh / đồng hồ, tiện khi nghe nhạc", "Starts the photo / clock screensaver, handy with music"), vm.nutManCho) { vm.nutManCho = it }
        Hang(t("Thư viện app", "App library"), t("Nút xem mọi app trên tivi, thêm / bớt khỏi Home", "A button listing every app; add or remove from Home"), vm.thuVienApp) { vm.thuVienApp = it }
        if (vm.thuVienApp) Hang(t("Nút Cài đặt mở Thư viện", "Settings button opens the library"), t("Kiểu Projectivy: bấm nút Cài đặt của Home sạch là ra danh sách app, giữ OK là vào cài đặt tivi, bấm nút Menu trên nó là menu cũ. Bỏ nút Thư viện trên thanh đầu và viên Thư viện cuối hàng app", "Projectivy style: pressing Home's Settings button lists every app, holding OK opens the TV settings, Menu on it shows the old menu. Removes the top-bar Library button and the Library pill after the last row"), vm.nutCaiDatThuVien) { vm.nutCaiDatThuVien = it }
        if (vm.thuVienApp && !vm.nutCaiDatThuVien) Hang(t("Thư viện trong menu Cài đặt", "Library in the Settings menu"), t("Bỏ nút Thư viện trên thanh đầu. Mở bằng cách giữ OK trên nút Cài đặt của Home sạch. Viên Thư viện cuối hàng app vẫn hiện (Nâng cao không ẩn được khi không có nút trên thanh đầu)", "No Library button in the top bar. Open it by holding OK on Home's Settings button. The Library pill after the last row stays (Advanced can't hide it without the top-bar button)"), vm.thuVienTrongMenu) { vm.thuVienTrongMenu = it }
        Hang(t("Hiện CPU / bộ nhớ", "Show CPU / memory"), t("Một dòng nhỏ dưới đồng hồ", "One small line under the clock"), vm.hienThongSo) { vm.hienThongSo = it }
    }
    The(t("Remote", "Remote"), Icons.Rounded.SettingsRemote) {
        Hang(t("Nút Input trên remote mở bảng chọn HDMI", "Remote Input button opens the input picker"), t("Nên bật cho Google TV", "Recommended on Google TV"), vm.nutInput) { vm.nutInput = it }
        Hang(t("Remote gửi phím Home lạ", "Remote sends an odd Home key"), t("Que Xiaomi, hộp nhà mạng…", "Xiaomi sticks, carrier boxes…"), vm.nutHome) { vm.nutHome = it }
        if (vm.phimRemote() != null) Hang(t("Nút app trên remote", "Remote app buttons"),
            if (vm.mauChon?.id == "tcl-g09-google-tv" || vm.laTclGoogleTv) t("YouTube, Netflix, Cài đặt… mở được khi Home gốc tắt. Trên máy này bật thì giữ nút âm lượng chỉ nhảy một nấc", "YouTube, Netflix, Settings… work with the stock Home off. On this set, holding volume then steps only once")
            else t("YouTube, Netflix, Cài đặt… vẫn mở được khi Home gốc tắt", "YouTube, Netflix, Settings… keep working with the stock Home off"),
            vm.nutAppRemote) { vm.nutAppRemoteChon = it }
        // Dò phím remote (3.1.1): gán nút app lạ của remote (VTVgo, FPT Play…) cho app trên tivi.
        Text(t("Nút app trên remote tự gán", "Your own remote app buttons"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Phu(t("Nút VTVgo, FPT Play… trên remote không mở app nữa sau khi đổi Home? Bấm Dò, rồi bấm nút đó trên remote, chọn app cho nó, Áp dụng lên tivi.",
            "A remote's VTVgo / FPT Play… button stopped opening its app after the Home change? Tap Detect, press that button on the remote, pick its app, then apply."))
        vm.phimGan.toList().forEach { d ->
            val goi = d.substringAfterLast(':'); val ma = d.removeSuffix(":$goi")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${zone.vgs.tvsach.dulieu.tenDep(goi)} · ${ma.replace("scan:", t("mã ", "code "))}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.phimGan.remove(d) }) { Text(t("Bỏ", "Remove")) }
            }
        }
        NutPhu(if (vm.dangDoPhim) t("Đang chờ bạn bấm nút trên remote…", "Waiting for a remote button…") else t("Dò một nút trên remote", "Detect a remote button"),
            Icons.Rounded.SettingsRemote, enabled = !vm.dangBan) { vm.doPhimRemote() }
        if (vm.giuAmLuong && vm.phimGan.isNotEmpty()) Phu(t("Lưu ý: đang bật \"Giữ nút âm lượng\" nên Home sạch không bắt phím, nút tự gán sẽ không chạy.",
            "Note: \"Hold volume\" is on, so the clean Home does not catch keys and these buttons won't work."), Cam)
        vm.phimVuaDo?.let { (key, scan) ->
            AlertDialog(
                onDismissRequest = { vm.phimVuaDo = null }, containerColor = The1,
                confirmButton = { TextButton(onClick = { vm.phimVuaDo = null }) { Text(t("Huỷ", "Cancel")) } },
                title = { Text(t("Nút này mở app nào?", "Which app should this button open?")) },
                text = {
                    Column {
                        Text(t("Đã bắt được nút (mã ${if (scan > 0) scan else key}).", "Button caught (code ${if (scan > 0) scan else key})."), fontSize = 13.sp, color = ChuPhu)
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(vm.appGanDuoc, key = { it }) { p ->
                                Text(zone.vgs.tvsach.dulieu.tenDep(p), fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { vm.ganPhim(p) }.padding(vertical = 11.dp))
                            }
                        }
                    }
                },
            )
        }
        Hang(t("Giữ nút âm lượng để tăng / giảm liên tục", "Hold volume to keep raising / lowering"),
            t("Bật nếu giữ nút chỉ nhảy một nấc (hay gặp trên Sony). Đổi lại, nút Input / Apps / nút app trên remote về như tivi gốc",
                "Turn on if holding volume only moves one step (common on Sony). In return the remote's Input / Apps / app buttons work as the TV's own"),
            vm.giuAmLuong) { vm.giuAmLuong = it }
    }
    The(t("File cấu hình", "Settings file"), Icons.Rounded.Save) {
        Phu(t("Lưu mọi lựa chọn (theme, dock, thời tiết, tuỳ chỉnh, Nâng cao, ảnh nền riêng) ra một file để giữ lại hoặc mang sang tivi khác.",
            "Save every choice (theme, dock, weather, options, Advanced, your wallpaper) to a file to keep or use on another TV."))
        val luu = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.xuatCauHinh(uri) }
        val mo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.nhapCauHinh(uri) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Trên tivi không có hộp Lưu / Mở (29/10/2026): lưu vào Download, mở từ Download / USB.
            Box(Modifier.weight(1f)) { NutPhu(t("Lưu ra file", "Save to file"), Icons.Rounded.Save) {
                if (zone.vgs.tvsach.ManHinh.laTv) vm.xuatCauHinhTv() else runCatching { luu.launch(vm.tenFileCauHinh) }.onFailure { vm.xuatCauHinhTv() } } }
            Box(Modifier.weight(1f)) { NutPhu(t("Mở file", "Open file"), Icons.Rounded.FolderOpen) {
                if (zone.vgs.tvsach.ManHinh.laTv) vm.moFileTrenTv("json") else runCatching { mo.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }.onFailure { vm.moFileTrenTv("json") } } }
        }
        if (vm.cauHinhBao.isNotEmpty()) Phu(vm.cauHinhBao, Xanh)
    }
    The(t("Tivi yếu & mạng", "Slow TVs & network"), Icons.Rounded.Speed) {
        Hang(t("Chế độ tivi yếu", "Slow TV mode"), t("Bớt hiệu ứng chuyển động cho tivi cũ, ít RAM", "Calmer motion for old, low-memory TVs"), vm.tiviYeu) { vm.tiviYeu = it }
        Hang(t("Hiệu ứng kính mờ", "Frosted glass effects"), t("Dock kính mờ, ánh sáng theo màu app", "Frosted dock, glow in the app's colour"), vm.hieuUngKinh) { vm.hieuUngKinh = it }
        Hang(t("DNS chặn quảng cáo (AdGuard)", "Ad-blocking DNS (AdGuard)"), t("Là cài đặt DNS của tivi, không phải app (xem ở Cài đặt → Mạng → DNS riêng tư). Không chặn được quảng cáo YouTube. Nhà đã chặn ở router (Pi-hole, AdGuard Home, VPN router) thì đừng bật: tivi sẽ đi vòng qua router. Có tivi bị YouTube hay app kênh chạy chập chờn khi bật: gặp thì tắt đi", "A TV DNS setting, not an app (Settings → Network → Private DNS). Does not block YouTube ads. If your router already filters (Pi-hole, AdGuard Home, router VPN), leave it off: the TV would bypass the router. On some TVs YouTube or channel apps stall with it on: if so, turn it off"), vm.dnsChanQc) { vm.dnsChanQc = it }
    }
}

/** Nhóm của một khoá Nâng cao, theo thứ tự hiện. */
private val NHOM_NANG_CAO = listOf(
    Triple("hinh-nen", "Hình nền & màu" to "Wallpaper & colour", Regex("^(background_color|sky_intensity|wallpaper_.*|accent_color|festival.*|glow_style|ambient_glow)$")),
    Triple("dock", "Dock & lưới app" to "Dock & app grid", Regex("^(fill_dock|hidden_apps|columns|rows_below_dock|dock_.*|library_apps|app_library_selection|app_labels|app_banners|module_cards|reorder_button|tile_menu|fade_rows|focus_overshoot)$")),
    Triple("thanh-tren", "Thanh trên & lời chào" to "Top bar & greeting", Regex("^(show_.*|icon_only_.*|top_menu|launcher_settings_button|wifi_.*|greeting|shamsi_date|vpn_app)$")),
    Triple("dien-anh", "Home điện ảnh" to "Cinematic Home", Regex("^cinematic_.*$")),
    Triple("remote", "Remote, HDMI & app hãng" to "Remote, HDMI & maker apps", Regex("^(live_tv_.*|source_panel_.*|coocaa_sources|launch_actions|input_.*|settings_key_.*|restore_key_services|app_keys|key_filter|stock_home_redirect|boot_to_live_tv)$")),
)

/** Màn Nâng cao riêng: tìm, lọc Đã đổi / Cẩn thận, chia nhóm, chấm tím ở mục đã đổi. */
@Composable
private fun ManNangCao(vm: PhienLamViec) {
    var tim by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf(0) } // 0 tất cả, 1 đã đổi, 2 cẩn thận
    val daDat = vm.nangCao.count { it.value.isNotBlank() }
    val q = XaPhuong.boDau(tim.trim())
    val ds = vm.dsNangCao.filter { tc ->
        (q.isEmpty() || tc.khoa.contains(q) || XaPhuong.boDau(tc.ten).contains(q) || XaPhuong.boDau(tc.moTaVi).contains(q) || tc.moTa.lowercase().contains(q)) &&
            when (loc) { 1 -> !vm.nangCao[tc.khoa].isNullOrBlank(); 2 -> tc.canThan; else -> true }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Nâng cao", "Advanced"), fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                if (daDat > 0) TextButton(onClick = { vm.nangCao.clear() }) { Text(t("Trả mặc định", "Reset all")) }
            }
            Phu(t("Để \"Mặc định\" là Home sạch tự quyết. Mục \"Cẩn thận\" đụng tới remote, HDMI, app của hãng: đặt sai có thể làm remote hoặc nguồn vào không chạy. Lỡ sai thì bấm Khôi phục là về như cũ.",
                "\"Default\" lets the clean Home decide. \"Careful\" items touch the remote, HDMI and the maker's apps: a wrong value can break them — Restore puts everything back."))
            OutlinedTextField(tim, { tim = it }, label = { Text(t("Tìm: tên app, đồng hồ, hình nền…", "Search: app name, clock, wallpaper…")) }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(50))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Tất cả", "All"), loc == 0) { loc = 0 }
                ChonChip(t("Đã đổi ($daDat)", "Changed ($daDat)"), loc == 1) { loc = 1 }
                ChonChip(t("Cẩn thận", "Careful"), loc == 2) { loc = 2 }
            }
            val conLai = ds.toMutableList()
            (NHOM_NANG_CAO.map { (id, ten, re) -> id to ten to re } + listOf("khac" to ("Khác" to "Other") to Regex(".*"))).forEach { (dau, re) ->
                val (_, ten) = dau
                val nhom = conLai.filter { re.matches(it.khoa) }
                conLai.removeAll(nhom)
                if (nhom.isEmpty()) return@forEach
                Text("${t(ten.first, ten.second)} · ${nhom.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ChuPhu, modifier = Modifier.padding(top = 8.dp))
                nhom.forEach { tc -> DongNangCao(tc, vm.nangCao[tc.khoa] ?: "") { v -> if (v.isBlank()) vm.nangCao.remove(tc.khoa) else vm.nangCao[tc.khoa] = v } }
            }
            if (ds.isEmpty()) Phu(t("Không có tuỳ chọn nào khớp.", "No matching options."))
        }
        Column(Modifier.fillMaxWidth().background(The1).padding(horizontal = 16.dp, vertical = 10.dp)) {
            NutChinh(t("Xong, quay lại", "Done"), icon = Icons.Rounded.Check) { vm.moNangCao = false }
        }
    }
}

@Composable
private fun DongNangCao(tc: zone.vgs.tvsach.dulieu.TuyChon, gt: String, doi: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gt.isNotBlank()) { Box(Modifier.size(8.dp).clip(CircleShape).background(Tim)); Spacer(Modifier.width(8.dp)) }
            Text(tc.tieuDe(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (gt.isNotBlank()) Hong else Color.White, modifier = Modifier.weight(1f))
            if (tc.canThan) Text(t("Cẩn thận", "Careful"), fontSize = 11.sp, color = Cam, modifier = Modifier.clip(RoundedCornerShape(50)).background(Cam.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        // Khoá kỹ thuật vẫn hiện nhỏ (tra tài liệu CleanYourTV, hỏi trên nhóm) khi đã có tên tiếng Việt.
        if (tc.tieuDe() != tc.khoa) Text(tc.khoa, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ChuPhu)
        Phu(tc.moTaHien())
        when (tc.kieu) {
            "bool" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                ChonChip(t("Bật", "On"), gt == "true") { doi("true") }
                ChonChip(t("Tắt", "Off"), gt == "false") { doi("false") }
            }
            "chon" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mặc định", "Default"), gt.isBlank()) { doi("") }
                tc.chon.forEach { c -> ChonChip(tc.nhanChon(c), gt == c) { doi(c) } }
            }
            else -> OutlinedTextField(gt, { doi(it) }, singleLine = tc.kieu == "so" || tc.kieu == "chu",
                placeholder = { Text(when (tc.kieu) {
                    "so" -> t("Số từ ${tc.min} đến ${tc.max}", "${tc.min} to ${tc.max}")
                    "ds", "ds-so" -> t("Cách nhau bằng dấu phẩy", "Comma-separated")
                    else -> t("Để trống = mặc định", "Empty = default")
                }, fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = if (tc.kieu == "so") KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        }
    }
}

@Composable
private fun ChonChip(chu: String, chon: Boolean, onClick: () -> Unit) {
    Text(chu, fontSize = 13.sp, fontWeight = if (chon) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (chon) Tim.copy(alpha = 0.3f) else The2)
            .border(1.dp, if (chon) Tim else Vien, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Thẻ xem trước theme: ảnh nền thật (nếu có) hoặc dải màu, kèm dock giả. */
@Composable
private fun TheTheme(th: Theme, chon: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val anh = remember(th.id) {
        (th.anhNen ?: th.anhXem)?.let { a -> runCatching {
            ctx.assets.open(a).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) }
        }.getOrNull()?.asImageBitmap() }
    }
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(th.mau.map { Color(it) }))
            .border(if (chon) 2.dp else 1.dp, if (chon) Hong else Vien, RoundedCornerShape(12.dp))) {
            if (anh != null) Image(anh, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i -> Box(Modifier.size(width = 20.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) Color.White else Color.White.copy(alpha = 0.55f))) }
            }
            Text("5:05", Modifier.align(Alignment.TopStart).padding(8.dp), fontSize = 13.sp, fontWeight = FontWeight.Light)
            if (chon) Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp), tint = Hong)
        }
        Text(t(th.tenVi, th.tenEn), fontSize = 13.sp, fontWeight = if (chon) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

@Composable
private fun Hang(chu: String, phu: String, bat: Boolean, doi: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { doi(!bat) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(chu, fontSize = 14.sp); Phu(phu) }
        Switch(bat, doi, colors = SwitchDefaults.colors(checkedTrackColor = Tim))
    }
}

// ---------------- Xong ----------------

@Composable
private fun ManXong(vm: PhienLamViec) = Cuon {
    // Rời app khi đang ở màn này: hẹn tự tắt ADB (vm.phutTuTatAdb); quay lại trước đó thì huỷ.
    val vongDoi = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(vongDoi) {
        val nghe = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.henTuTatAdb()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.huyTuTatAdb()
        }
        vongDoi.lifecycle.addObserver(nghe)
        onDispose { vongDoi.lifecycle.removeObserver(nghe) }
    }
    DauTrang({
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Gradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(52.dp), tint = Color.White)
        }
    }, t("Tivi đã sạch!", "The TV is clean!"),
        if (vm.homeXong && vm.phimHomeOk) t("Home sạch đang chạy. Bấm Home trên remote để xem.", "The clean Home is running — press Home on the remote.")
        else if (vm.homeXong) t("Home sạch đã cài nhưng nút Home trên remote vẫn mở Home cũ (xem lời báo bên dưới).", "The clean Home is installed, but the remote's Home button still opens the old Home (see the note below).")
        else null)
    // Hỏi thẳng hai lựa chọn (26/10/2026, anh Alex duyệt): trước đây chỉ có công tắc "Giữ ADB" dễ bị bỏ qua, nhiều người
    // muốn chỉnh tiếp lại phải bật gỡ lỗi trên tivi từ đầu. Chưa chọn mà rời app quá 1 phút thì vẫn tự tắt cho an toàn.
    The(t("Gỡ lỗi (ADB) trên tivi", "Debugging (ADB) on the TV"), Icons.Rounded.Lock) {
        if (vm.giuAdb) {
            Phu(t("Đang giữ ADB: lần sau đổi giao diện, khôi phục không phải bật lại gỡ lỗi trên tivi. Ai cùng mạng Wi-Fi cũng điều khiển được tivi, nên chỉnh xong hẳn thì nhớ tắt.",
                "ADB stays on: next time you change the look or restore, no need to turn debugging on again. Anyone on your Wi-Fi can control the TV, so turn it off once you are done for good."), Xanh)
            NutPhu(t("Đổi ý: tắt ADB ngay", "Changed my mind: turn ADB off"), Icons.Rounded.Lock) { vm.tatAdbVaKetThuc() }
        } else {
            Phu(t("Để gỡ lỗi bật thì ai cùng mạng Wi-Fi cũng điều khiển được tivi. Tắt đi thì lần sau muốn chỉnh giao diện phải bật lại gỡ lỗi trên tivi.",
                "With debugging on, anyone on your Wi-Fi can control the TV. Turn it off and you must enable debugging on the TV again to change anything later."))
            NutChinh(t("Tắt ADB (an toàn hơn)", "Turn ADB off (safer)"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { vm.tatAdbVaKetThuc() }
            NutPhu(t("Giữ ADB để chỉnh tiếp", "Keep ADB on to tweak later"), Icons.Rounded.LockOpen) { vm.chonGiuAdb() }
            // Remote trên điện thoại đi qua ADB: chọn nó là giữ ADB.
            if (!zone.vgs.tvsach.ManHinh.laTv) NutPhu(t("Giữ ADB và dùng remote trên điện thoại", "Keep ADB and use the phone remote"), Icons.Rounded.SettingsRemote) { vm.chonGiuAdb { vm.moRemote() } }
            val phut = vm.phutTuTatAdb
            Phu(t("Chưa chọn mà rời app quá $phut phút thì tivi tự tắt ADB.", if (phut == 1) "Leave the app for over a minute without choosing and the TV turns ADB off itself." else "Leave the app for over $phut minutes without choosing and the TV turns ADB off itself."))
        }
    }
    The(t("Trước khi về", "Before you leave"), Icons.Rounded.Checklist) {
        BuocSo(1, t("Thử lại YouTube, Netflix, HDMI, Cài đặt, remote.", "Try YouTube, Netflix, HDMI, Settings and the remote."))
        BuocSo(2, t("Khởi động lại một lần xem gói nào tự bật lại.", "Reboot once to see if anything re-enables itself."))
        NutPhu(t("Xem màn hình tivi", "View TV screen"), Icons.Rounded.Screenshot) { vm.chupManHinh() }
        NutPhu(t("Khởi động lại tivi", "Reboot the TV"), Icons.Rounded.RestartAlt) { vm.khoiDongLaiTv() }
    }
    TheCongDong()
    TheKichBan(vm)
    TheGuiDanhSach(vm)
    NutGopY(vm)
    Phu(t("Muốn chắc hơn nữa, trên tivi: Tuỳ chọn nhà phát triển → \"Thu hồi uỷ quyền gỡ lỗi USB\". Bản sao lưu vẫn trong điện thoại để khôi phục.",
        "To be extra safe, on the TV: Developer options → \"Revoke USB debugging authorisations\". The backup stays on this phone for restoring."))
}

// ---------------- Khôi phục ----------------

@Composable
private fun ManKhoiPhuc(vm: PhienLamViec) = Cuon {
    val sl = vm.saoLuu
    if (vm.khoiPhucXong) {
        DauTrang({ Icon(Icons.Rounded.CheckCircle, null, Modifier.size(72.dp), tint = Xanh) },
            t("Đã khôi phục", "Restored"), t("Tivi đã về như trước khi dọn.", "The TV is back to how it was."))
        NutChinh(t("Tiếp: tắt ADB", "Next: turn off ADB"), icon = Icons.Rounded.Lock) { vm.man = Man.XONG }
        return@Cuon
    }
    if (sl == null) {
        // Không có bản sao lưu cả trên điện thoại lẫn trên tivi: bật lại mọi gói đang tắt, hỏi xác nhận rõ (22/10/2026).
        DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục không cần sao lưu", "Restore without a backup"),
            t("Không thấy bản sao lưu của ${vm.thongTin?.tenHienThi} trên điện thoại này lẫn trên tivi.",
                "No backup of ${vm.thongTin?.tenHienThi} on this phone or on the TV."))
        val ds = vm.goiTatKhongSaoLuu
        if (ds == null) {
            The {
                Text(t("App có thể bật lại MỌI gói đang bị tắt trên tivi, gỡ Home sạch và trả Home gốc của hãng. Gói chính bạn tự tắt từ trước cũng sẽ bật lại.",
                    "The app can re-enable EVERY disabled package on the TV, remove the clean Home and bring back the maker's Home. Packages you disabled yourself earlier come back too."))
            }
            NutChinh(if (vm.dangBan) t("Đang đọc…", "Reading…") else t("Xem gói đang tắt", "See disabled packages"), enabled = !vm.dangBan, icon = Icons.Rounded.Search) { vm.docGoiTatKhongSaoLuu() }
            return@Cuon
        }
        var dongY by remember { mutableStateOf(false) }
        var goHomeKsl by remember { mutableStateOf(true) }
        The(t("${ds.size} gói đang tắt", "${ds.size} disabled packages"), Icons.Rounded.Restore) {
            if (ds.isEmpty()) Phu(t("Không có gói nào đang tắt.", "No packages are disabled."))
            ds.take(12).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
            if (ds.size > 12) Phu(t("…và ${ds.size - 12} gói nữa.", "…and ${ds.size - 12} more."))
        }
        The {
            Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHomeKsl) { goHomeKsl = it }
            Hang(t("Tôi hiểu: bật lại tất cả", "I understand: re-enable all"), t("Kể cả gói tôi tự tắt trước khi dùng CleanTV", "Including ones I disabled before using CleanTV"), dongY) { dongY = it }
        }
        NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = dongY && !vm.dangBan, icon = Icons.Rounded.Restore) {
            vm.chayKhoiPhucKhongSaoLuu(goHomeKsl)
        }
        vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
        TheBatTungApp(vm)
        return@Cuon
    }
    DauTrang({ OIcon(Icons.Rounded.Restore, 72.dp) }, t("Khôi phục ${sl.ten}", "Restore ${sl.ten}"),
        t("Bật lại ${sl.daTatBoiApp.size} gói", "Re-enable ${sl.daTatBoiApp.size} packages") +
            (if (sl.launcherDaCai) t(", trả Home gốc", ", restore the stock Home") else "") +
            (if (sl.dnsDaDoi) t(", trả DNS", ", restore DNS") else "") + ".")
    var goHome by remember { mutableStateOf(true) }
    if (sl.launcherDaCai) The {
        Hang(t("Gỡ luôn Home sạch", "Also uninstall the clean Home"), t("Bỏ chọn để giữ Home sạch trên tivi", "Untick to keep it on the TV"), goHome) { goHome = it }
    }
    NutChinh(if (vm.dangBan) t("Đang khôi phục…", "Restoring…") else t("Khôi phục", "Restore"), enabled = !vm.dangBan, icon = Icons.Rounded.Restore) {
        vm.chayKhoiPhuc(sl.launcherDaCai && goHome)
    }
    vm.nhatKy.takeLast(4).forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ChuPhu) }
    TheKichBan(vm)
    TheBatTungApp(vm)
}

/** Chữ nhỏ "Xem lệnh sẽ chạy": mở hộp liệt kê các lệnh chính app sắp gửi lên tivi (24/10/2026). */
@Composable
private fun NutXemLenh(lenh: () -> List<String>) {
    var mo by remember { mutableStateOf(false) }
    Text(t("Xem lệnh sẽ chạy trên tivi", "See the commands it will run"), fontSize = 13.sp, color = ChuPhu,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { mo = true }.padding(6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (mo) AlertDialog(
        onDismissRequest = { mo = false }, containerColor = The1,
        title = { Text(t("Lệnh sẽ chạy trên tivi", "Commands for the TV")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Phu(t("Các lệnh chính, theo thứ tự. Gói lõi Android và Home đang dùng app vẫn tự bỏ qua khi chạy.", "The main commands, in order. Core Android packages and the Home in use are still skipped when it runs."))
                lenh().forEach { d ->
                    Text(d, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (d.startsWith("#")) Hong else ChuPhu, modifier = Modifier.padding(top = if (d.startsWith("#")) 8.dp else 1.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { mo = false }) { Text(t("Đóng", "Close")) } },
    )
}

/** Lưu kịch bản khôi phục cho máy tính (24/10/2026): mất điện thoại hay gỡ CleanTV vẫn trả tivi về như cũ bằng adb. */
@Composable
private fun TheKichBan(vm: PhienLamViec) {
    val sl = vm.saoLuu ?: return
    The(t("Kịch bản khôi phục cho máy tính", "Restore script for a computer"), Icons.Rounded.Save) {
        Phu(t("Lưu một file lệnh adb để đưa tivi về như cũ từ máy tính, kể cả khi mất điện thoại hay đã gỡ CleanTV. Windows bấm đúp file, Mac / Linux chạy sh tên-file.bat.",
            "Save a file of adb commands that puts the TV back from a computer, even without this phone or CleanTV. Windows: double-click it; Mac / Linux: sh file-name.bat."))
        val luu = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.xuatKichBan(it) } }
        NutPhu(t("Lưu kịch bản khôi phục", "Save restore script"), Icons.Rounded.Save) {
            if (zone.vgs.tvsach.ManHinh.laTv) vm.xuatKichBanTv() else runCatching { luu.launch(vm.tenFileKichBan) }.onFailure { vm.xuatKichBanTv() }
        }
        if (vm.cauHinhBao.isNotEmpty()) Phu(vm.cauHinhBao, Xanh)
    }
}

/** Bật lại từng app (issue vgs-cleantv#3): danh sách gói đang tắt đọc thẳng từ tivi, tích cái nào bật cái đó. */
@Composable
private fun TheBatTungApp(vm: PhienLamViec) {
    The(t("Bật lại từng app", "Re-enable single apps"), Icons.Rounded.Apps) {
        Phu(t("Chỉ muốn bật lại vài app lỡ tắt? App đọc từ tivi mọi app đang tắt (kể cả khi đã cài lại CleanTV), bạn tích cái cần bật.",
            "Only need a few apps back? The app reads every disabled app from the TV (even after reinstalling CleanTV); tick the ones to turn on."))
        val ds = vm.dsTatTv
        if (ds == null) {
            NutPhu(t("Xem các app đang tắt", "See disabled apps"), Icons.Rounded.Search) { vm.docDsTat() }
            return@The
        }
        if (ds.isEmpty()) Phu(t("Tivi không có app nào đang tắt.", "No apps are disabled on the TV."))
        val cuaCleanTv = vm.saoLuu?.daTatBoiApp.orEmpty()
        ds.forEach { p ->
            val chon = vm.chonBatLai[p] == true
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.chonBatLai[p] = !chon }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(chon, { vm.chonBatLai[p] = it })
                Column(Modifier.weight(1f)) {
                    Text(tenDep(p), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(p + if (p in cuaCleanTv) t(" · do CleanTV tắt", " · disabled by CleanTV") else "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ChuPhu, maxLines = 1)
                }
            }
        }
        if (vm.batLaiBao.isNotEmpty()) Phu(vm.batLaiBao, Xanh)
        val so = vm.chonBatLai.count { it.value }
        if (ds.isNotEmpty()) NutPhu(t("Bật lại $so app đã chọn", "Re-enable $so selected"), Icons.Rounded.Restore, enabled = so > 0 && !vm.dangBan) { vm.batLaiDaChon() }
    }
}

// ---------------- Nhắc + hướng dẫn chế độ nhà phát triển ----------------

/** Thẻ nổi bật ở màn chính: việc BẮT BUỘC làm trên tivi trước khi dùng app. */
@Composable
private fun TheNhacNpt(vm: PhienLamViec) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A1F0C))
        .border(1.5.dp, Cam.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
            Text(t("Trước tiên: bật chế độ nhà phát triển trên tivi", "First: turn on developer mode on the TV"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Phu(t("Không có bước này app không kết nối được tivi. Làm một lần, khoảng 1 phút: Cài đặt → Giới thiệu → bấm Bản dựng 7 lần → bật Gỡ lỗi qua USB.",
            "The app can't reach the TV without this. One time, about a minute: Settings → About → press Build 7 times → turn on USB debugging."), Color.White.copy(alpha = 0.85f))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Cam).clickable { vm.moHuongDan() }, contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MenuBook, null, tint = Color.Black); Spacer(Modifier.width(8.dp))
                Text(t("Xem hướng dẫn có hình (7 bước)", "Picture guide (7 steps)"), fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        Phu(t("Chỉ Android TV / Google TV. Samsung (Tizen), LG (webOS) không làm được.", "Android TV / Google TV only. Samsung (Tizen) and LG (webOS) are not supported."))
    }
}

@Composable
private fun ManHuongDan(vm: PhienLamViec) {
    val ctx = LocalContext.current
    val thuMuc = if (NgonNgu.tiengViet) "vi" else "en"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Bật chế độ nhà phát triển", "Turn on developer mode"), fontWeight = FontWeight.Black, fontSize = 24.sp)
                Phu(t("Cầm remote, làm lần lượt 7 bước. Ảnh chụp từ Android TV, tên mục có thể khác chút tuỳ hãng (Hệ thống / Giới thiệu / Thông tin…).",
                    "Grab the remote and follow the 7 steps. Pictures are from Android TV — names may differ slightly by brand (System / About / Information…)."))
            }
        }
        itemsIndexed(HUONG_DAN) { i, b ->
            val anh = remember(thuMuc, b.anh) {
                runCatching { ctx.assets.open("huongdan/$thuMuc/${b.anh}.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(The1).border(1.dp, Vien, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Gradient), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(t(b.tieuDeVi, b.tieuDeEn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (anh != null) Image(anh, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Vien, RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                Text(t(b.chuVi, b.chuEn), fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        item {
            The(t("Muốn đổi giao diện sau này", "Changing the look later"), Icons.Rounded.Palette) {
                BuocSo(1, t("Trên tivi bật lại \"Gỡ lỗi qua USB\" (Tùy chọn nhà phát triển vẫn còn, không phải bấm Bản dựng nữa).",
                    "On the TV turn \"USB debugging\" back on (Developer options stays, no need to press Build again)."))
                BuocSo(2, t("Mở app → Dọn một tivi → kết nối lại tivi đó. Đã tích \"Luôn cho phép\" thì tivi không hỏi lại.",
                    "Open the app → Clean a TV → connect to that TV again. If you ticked \"Always allow\" the TV won't ask again."))
                BuocSo(3, t("Bấm \"Đổi giao diện Home sạch\", chọn theme, dock, thời tiết, tên chào → Áp dụng giao diện. Các gói đã tắt giữ nguyên, không phải dọn lại.",
                    "Tap \"Change the clean Home theme\", pick a theme, dock, weather and greeting → Apply theme. Disabled packages stay as they are."))
                BuocSo(4, t("Xong thì bấm Tắt ADB như lần đầu.", "When done, press Turn off ADB as before."))
                Phu(t("Chỉnh nhanh không cần điện thoại: trên tivi giữ OK ở nút Cài đặt của Home sạch để đổi số cột, hiệu ứng, sắp xếp app (theme và hình nền vẫn phải đổi qua app).",
                    "Quick tweaks without the phone: on the TV hold OK on the clean Home's Settings button to change columns, motion and app order (themes and wallpapers still need the app)."))
            }
        }
        item {
            The(t("Dọn xong thì tắt lại", "Turn it off when you're done"), Icons.Rounded.Lock) {
                Phu(t("Bước cuối trong app sẽ tắt ADB. Cho chắc, vào lại \"Tùy chọn nhà phát triển\": tắt \"Gỡ lỗi qua USB\" và chọn \"Thu hồi các lượt ủy quyền gỡ lỗi qua USB\".",
                    "The app's last step turns ADB off. To be sure, go back to \"Developer options\": turn off \"USB debugging\" and choose \"Revoke USB debugging authorizations\"."))
            }
        }
        item {
            NutChinh(t("Bật xong rồi, kết nối tivi", "Done — connect to the TV"), icon = Icons.Rounded.WifiFind) { vm.man = Man.KET_NOI }
        }
    }
}

@Composable
private fun ManGiayPhep() {
    val ctx = LocalContext.current
    val chu = remember { runCatching { ctx.assets.open("giay-phep.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Giấy phép mã nguồn mở", "Open-source licenses"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(chu, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp, color = ChuPhu)
    }
}

// ---------------- Máy chiếu ----------------

/**
 * Dò màn lấy nét / chỉnh hình của hãng, mở thử trên máy chiếu, ghim vào nút "Máy chiếu" trên Home sạch.
 * Tivi thường không hiện thẻ này, trừ khi bấm dòng "Máy chiếu?" nhỏ.
 */
@Composable
private fun TheMayChieu(vm: PhienLamViec) {
    var moTay by rememberSaveable { mutableStateOf(false) }
    if (!vm.laMayChieu && vm.mayChieu == null && !moTay) {
        TextButton(onClick = { moTay = true }) {
            Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Đây là máy chiếu? Thêm nút lấy nét, chỉnh hình", "Is this a projector? Add focus / keystone buttons"))
        }
        return
    }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    The(t("Máy chiếu: lấy nét, chỉnh hình", "Projector: focus & keystone"), Icons.Rounded.Videocam) {
        Phu(t("App dò các màn chỉnh của hãng trên máy chiếu. Bấm Mở thử để xem đúng màn chưa, rồi Ghim: Home sạch sẽ có nút \"Máy chiếu\" mở thẳng các màn đó. Gói lo lấy nét, chỉnh hình không bao giờ bị tắt.",
            "The app looks for the maker's adjustment screens. Tap Try to check one, then Pin it: the clean Home gets a \"Projector\" button that opens them. Focus / keystone packages are never disabled."))
        val ds = vm.mayChieu
        if (ds == null) {
            NutPhu(if (vm.dangDoMayChieu) t("Đang dò… (khoảng 30 giây)", "Scanning… (about 30 seconds)") else t("Dò màn lấy nét / chỉnh hình", "Find focus / keystone screens"),
                Icons.Rounded.Search, enabled = !vm.dangDoMayChieu) { vm.doMayChieu() }
        } else {
            ds.forEach { comp ->
                val ghim = comp in vm.ghimMayChieu
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ghim) Tim.copy(alpha = 0.2f) else The2)
                    .border(1.dp, if (ghim) Tim else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(vm.ghimMayChieu[comp] ?: vm.nhanMayChieu(comp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(comp, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = ChuPhu, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = { vm.moThuMayChieu(comp) }, enabled = !vm.dangBan) { Text(t("Mở thử", "Try")) }
                        TextButton(onClick = { vm.doiGhim(comp) }) { Text(if (ghim) t("Bỏ ghim", "Unpin") else t("Ghim", "Pin")) }
                    }
                }
            }
            if (vm.ghimMayChieu.isNotEmpty()) Phu(t("Đã ghim ${vm.ghimMayChieu.size}. Bấm Cài Home sạch / Áp dụng giao diện để đưa lên máy chiếu.",
                "${vm.ghimMayChieu.size} pinned. Install the clean Home / Apply theme to send them to the projector."), Xanh)
        }
        NutPhu(t("Gửi danh sách cho VGS", "Send the list to VGS"), Icons.Rounded.Feedback) {
            clip.setText(androidx.compose.ui.text.AnnotatedString(vm.chuDanhSachMayChieu()))
            val tu = "CleanTV máy chiếu · " + (vm.thongTin?.tenHienThi ?: "")
            uri.openUri("https://vgs.zone/gop-y?tu=" + java.net.URLEncoder.encode(tu, "UTF-8"))
        }
        Phu(t("Đã chép sẵn danh sách, dán vào ô nội dung trong hộp góp ý.", "The list is copied — paste it into the feedback box."))
    }
}

/** Hộp chọn tay một mẫu trong toàn bộ danh sách, có ô tìm (23/10/2026). */
@Composable
private fun HopChonMau(vm: PhienLamViec) {
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val ds = remember(khoa, vm.tatCaMau) {
        if (khoa.isEmpty()) vm.tatCaMau else vm.tatCaMau.filter { m -> khoa.split(" ").all { it in XaPhuong.boDau(m.ten.lowercase()) || it in m.id } }
    }
    AlertDialog(
        onDismissRequest = { vm.moChonMau = false },
        confirmButton = { TextButton(onClick = { vm.moChonMau = false }) { Text(t("Đóng", "Close")) } },
        title = { Text(t("Chọn mẫu tivi", "Pick a TV list")) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(),
                    label = { Text(t("Tìm hãng, model (vd: sony vu3, tcl c7)", "Search brand, model (e.g. sony vu3)")) })
                Text(t("${ds.size} mẫu", "${ds.size} lists"), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(ds.take(120), key = { it.id }) { m ->
                        Text(m.ten.take(110), fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                            .clickable { vm.mauChon = m; vm.moChonMau = false }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        containerColor = The1,
    )
}

/**
 * Mời vào cộng đồng VGS: màn chính và màn xong (22/10/2026; 26/10/2026 anh Alex thêm nhóm Facebook, nhóm Telegram và đưa ra
 * màn chính). ?tu=cleantv-app để vgs.zone ghi nguồn đăng ký từ app.
 */
@Composable
private fun TheCongDong() {
    The(t("Cộng đồng VGS", "The VGS community"), Icons.Rounded.Groups) {
        Phu(t("CleanTV miễn phí, do cộng đồng VGS làm. Vào nhóm để hỏi đáp, khoe góc máy, nhận bản mới sớm và săn quà công nghệ.",
            "CleanTV is free, made by the VGS community. Join us for help, setups, early builds and tech giveaways."))
        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        NutPhu(t("Nhóm Facebook Vietnam Gaming Setup", "Vietnam Gaming Setup on Facebook"), Icons.Rounded.Groups) {
            runCatching { uri.openUri("https://www.facebook.com/groups/vietnamgamingsetup") }
        }
        NutPhu(t("Nhóm Telegram VGS", "VGS on Telegram"), Icons.Rounded.Forum) { runCatching { uri.openUri("https://vgs.zone/telegram") } }
        NutPhu(t("Tham gia VGSZone", "Join VGSZone"), Icons.Rounded.Star) {
            runCatching { uri.openUri("https://vgs.zone/dang-ky?tu=cleantv-app&next=/cleantv") }
        }
    }
}

/** Chọn kênh từ danh sách IPTV (3.1.0), tìm theo tên không dấu. */
@Composable
private fun HopChonIptv(vm: PhienLamViec) {
    val ds = vm.dsIptv ?: return
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val loc = remember(khoa, ds) { if (khoa.isEmpty()) ds else ds.filter { k -> khoa.split(" ").all { it in XaPhuong.boDau(k.ten.lowercase()) } } }
    AlertDialog(
        onDismissRequest = { vm.dsIptv = null },
        confirmButton = { TextButton(onClick = { vm.dsIptv = null }) { Text(t("Xong", "Done")) } },
        title = { Text(t("Kênh IPTV (${ds.size})", "IPTV channels (${ds.size})")) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), label = { Text(t("Tìm kênh", "Search")) })
                Text(t("Đã chọn ${vm.kenhChon.size}/$KENH_TOI_DA", "${vm.kenhChon.size}/$KENH_TOI_DA picked"), fontSize = 12.sp, color = ChuPhu, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(loc.take(500), key = { it.url }) { k ->
                        val chon = vm.coO(k.url)
                        Row(Modifier.fillMaxWidth().clickable { vm.batTatIptv(k) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (chon) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, null, tint = if (chon) Xanh else ChuPhu, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(k.ten, fontSize = 14.sp, maxLines = 1)
                                if (k.nhom.isNotBlank()) Text(k.nhom, fontSize = 11.sp, color = ChuPhu, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        containerColor = The1,
    )
}

/** Chọn kênh VTVgo bất kỳ (3.0.0): danh sách từ API VTVgo, nhóm theo gói, tìm theo tên không dấu. */
@Composable
private fun HopChonKenh(vm: PhienLamViec) {
    var tim by remember { mutableStateOf("") }
    val khoa = XaPhuong.boDau(tim.trim().lowercase())
    val ds = remember(khoa, vm.dsKenhGo) {
        if (khoa.isEmpty()) vm.dsKenhGo else vm.dsKenhGo.filter { k -> khoa.split(" ").all { it in XaPhuong.boDau(k.ten.lowercase()) } }
    }
    AlertDialog(
        onDismissRequest = { vm.moChonKenh = false },
        confirmButton = { TextButton(onClick = { vm.moChonKenh = false }) { Text(t("Xong", "Done")) } },
        title = { Text(t("Kênh trên VTVgo", "Channels on VTVgo")) },
        text = {
            Column {
                OutlinedTextField(tim, { tim = it }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(),
                    label = { Text(t("Tìm kênh (vd: vĩnh long, htv9, quảng ngãi)", "Search (e.g. hanoi, vtv5)")) })
                if (vm.dsKenhGo.isEmpty()) Text(if (vm.dangTaiKenh) t("Đang tải danh sách kênh…", "Loading channels…")
                    else t("Không tải được danh sách kênh. Kiểm tra mạng của điện thoại rồi mở lại.", "Could not load the channel list. Check the phone's internet and try again."),
                    fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Text(t("Đã chọn ${vm.kenhChon.size}/$KENH_TOI_DA ô · kênh có ổ khoá cần đăng nhập VTVgo trên tivi", "${vm.kenhChon.size}/$KENH_TOI_DA picked · locked channels need a VTVgo login on the TV"),
                    fontSize = 12.sp, color = ChuPhu, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    var nhomTruoc = ""
                    ds.forEach { k ->
                        if (k.nhom != nhomTruoc) { nhomTruoc = k.nhom; val n = k.nhom; item(key = "n:" + n + k.ma) { Text(n, fontSize = 12.sp, color = Tim, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) } }
                        item(key = k.nhom + k.ma) {
                            val chon = vm.coKenh(k.ma)
                            Row(Modifier.fillMaxWidth().clickable { vm.batTatKenh(k.tenO, k.ma) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (chon) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, null, tint = if (chon) Xanh else ChuPhu, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(k.ten + if (k.canDangNhap) " 🔒" else "", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        containerColor = The1,
    )
}

/** Gửi danh sách gói cho VGS (23/10/2026): người dùng tự bấm, để làm mẫu cho tivi chưa có trong danh sách. */
@Composable
private fun TheGuiDanhSach(vm: PhienLamViec, baoLoi: Boolean = false) {
    // Thẻ báo lỗi (màn Thông tin trên tivi) mặc định "Có chỗ chưa ổn".
    if (baoLoi) LaunchedEffect(Unit) { if (!vm.dsDaGui) vm.dsDanhGia = "chua-on" }
    The(if (baoLoi) t("Gửi báo lỗi cho VGS", "Report a problem to VGS") else t("Giúp người dùng cùng dòng tivi", "Help owners of the same TV"),
        if (baoLoi) Icons.Rounded.BugReport else Icons.Rounded.Share) {
        if (vm.dsDaGui) {
            Phu(if (baoLoi) t("Đã gửi, cảm ơn bạn! VGS sẽ xem và trả lời qua email nếu bạn có ghi.", "Sent, thank you! VGS will look into it and reply by email if you left one.")
                else t("Đã gửi, cảm ơn bạn! VGS sẽ xem và thêm tivi này vào danh sách.", "Sent, thank you! VGS will review it and add this TV to the lists."), Xanh)
            return@The
        }
        if (baoLoi) Phu(t("Gặp lỗi trên tivi này? Gửi cho VGS model tivi, gói đang tắt và chẩn đoán Home (Home mặc định, khởi động, nút Home) đọc từ chính tivi, không cần điện thoại. Không gửi tên hay danh sách app bạn cài; ghi email nếu muốn VGS trả lời.",
            "Something wrong on this TV? Send VGS the model, the packages that are off and the Home diagnostics (default Home, boot, Home button) read from the TV itself, no phone needed. No name or app list; add an email if you want a reply."))
        else Phu(t("Gửi cho VGS model tivi và danh sách gói đang tắt, để làm mẫu cho người dùng cùng dòng tivi. Không gửi tên, email hay danh sách app bạn cài, trừ khi bạn tự ghi chỗ liên hệ.",
            "Send VGS this TV's model and the packages that are off, to build a list for owners of the same TV. No name, email or app list — unless you add a contact."))
        Text(if (baoLoi) t("Tivi lúc này:", "Right now the TV:") else t("Dùng thử rồi, tivi chạy:", "After trying it, the TV:"), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Ổn hết", "Works fine"), vm.dsDanhGia == "on") { vm.dsDanhGia = "on" }
            ChonChip(t("Có chỗ chưa ổn", "Has issues"), vm.dsDanhGia == "chua-on") { vm.dsDanhGia = "chua-on" }
        }
        OutlinedTextField(vm.dsGhiChu, { if (it.length <= 1000) vm.dsGhiChu = it }, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            label = { Text(if (vm.dsDanhGia == "on") t("Ghi chú (không bắt buộc)", "Note (optional)") else t("Chưa ổn ở đâu? (HDMI, remote, giọng nói…)", "What's wrong? (HDMI, remote, voice…)")) })
        // Chỉ nhận email (anh Alex 25/10/2026): VGS trả lời bằng thư từ cleantv@, không nhắn số điện thoại.
        OutlinedTextField(vm.dsLienHe, { if (it.length <= 120) vm.dsLienHe = it.trim() }, singleLine = true, modifier = Modifier.thoatOTv().fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
            isError = vm.dsLienHe.isNotEmpty() && !laEmail(vm.dsLienHe),
            label = { Text(t("Email để VGS hỏi lại (không bắt buộc)", "Email to reach you (optional)")) })
        NutPhu(if (baoLoi) t("Gửi báo lỗi cho VGS", "Send the report to VGS") else t("Gửi danh sách gói cho VGS", "Send the package list to VGS"),
            if (baoLoi) Icons.Rounded.BugReport else Icons.Rounded.Share, enabled = vm.dsLienHe.isEmpty() || laEmail(vm.dsLienHe)) { if (!vm.dangBan) vm.guiDanhSach() }
    }
}

// ---------------- Khoá trẻ em ----------------

private val TEN_THU = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
private val TEN_THU_EN = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
private fun ManKhoaTre(vm: PhienLamViec) = Cuon {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Khoá trẻ em", "Kids lock"), fontWeight = FontWeight.Black, fontSize = 22.sp)
        Phu(t("Tới giờ khoá, tivi hiện màn khoá che mọi thứ (cả HDMI, YouTube) và chặn phím remote. Nhập đúng mã PIN trên tivi thì mở ${vm.KHOA_MO_TAM} phút. Tắt tivi bật lại vẫn khoá.",
            "During the lock periods the TV shows a lock screen over everything (HDMI and YouTube too) and blocks the remote. The right PIN unlocks it for ${vm.KHOA_MO_TAM} minutes. It stays locked after a restart."))
    }
    vm.khoaTrenTv?.let { j ->
        The(t("Trên tivi", "On the TV"), Icons.Rounded.Lock) {
            val bat = j.optBoolean("bat"); val chay = j.optBoolean("dichVuChay"); val dangKhoa = j.optBoolean("dangKhoa")
            val moDen = j.optLong("moTamDen")
            Phu(if (!bat) t("Khoá đang tắt.", "The lock is off.")
                else if (!chay) t("Khoá đang bật nhưng dịch vụ chưa chạy (tắt tivi bật lại).", "On, but the service isn't running (restart the TV).")
                else if (dangKhoa) t("Tivi đang khoá.", "The TV is locked now.")
                else if (moDen > System.currentTimeMillis()) t("Đang mở tạm tới ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(moDen))}.", "Unlocked until ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(moDen))}.")
                else t("Khoá đang bật, ngoài giờ khoá.", "On, outside the lock periods."), if (bat && chay) Xanh else ChuPhu)
            if (bat) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChonChip(t("Mở tạm ${vm.KHOA_MO_TAM} phút", "Unlock ${vm.KHOA_MO_TAM} min"), false) { vm.moTamKhoaTre(vm.KHOA_MO_TAM) }
                ChonChip(t("Khoá lại ngay", "Lock now"), false) { vm.moTamKhoaTre(0) }
            }
        }
    }
    The(t("Giờ khoá", "Lock periods"), Icons.Rounded.Schedule) {
        Hang(t("Bật khoá theo giờ", "Lock on a schedule"), t("Tắt thì tivi dùng bình thường, giữ nguyên giờ và PIN", "Off: the TV works normally; schedule and PIN are kept"), vm.khoaBat) { vm.khoaBat = it }
        vm.khoaKhung.forEachIndexed { i, k ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${k.tu} – ${k.den}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (k.ngay.size == 7) t("Mọi ngày", "Every day") else k.ngay.sorted().joinToString(" ") { t(TEN_THU[it - 1], TEN_THU_EN[it - 1]) }, fontSize = 12.sp, color = ChuPhu)
                }
                IconButton(onClick = { vm.khoaKhung.removeAt(i) }) { Icon(Icons.Rounded.Close, t("Xoá", "Remove")) }
            }
        }
        Phu(t("Thêm nhanh:", "Quick add:"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChonChip(t("Tối 21:00–06:00", "Night 21:00–06:00"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("21:00", "06:00", (1..7).toSet()) }
            ChonChip(t("Giờ học 14:00–16:30 (T2–T6)", "Study 14:00–16:30 (Mon–Fri)"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("14:00", "16:30", (1..5).toSet()) }
            ChonChip(t("Trưa 12:00–13:30", "Nap 12:00–13:30"), false) { vm.khoaKhung += PhienLamViec.KhungKhoa("12:00", "13:30", (1..7).toSet()) }
        }
        var tu by remember { mutableStateOf("") }
        var den by remember { mutableStateOf("") }
        var ngay by remember { mutableStateOf((1..7).toSet()) }
        Text(t("Hoặc tự đặt:", "Or set your own:"), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(tu, { tu = it.take(5) }, label = { Text(t("Từ (21:00)", "From")) }, singleLine = true, modifier = Modifier.thoatOTv().weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(den, { den = it.take(5) }, label = { Text(t("Đến (06:00)", "To")) }, singleLine = true, modifier = Modifier.thoatOTv().weight(1f), shape = RoundedCornerShape(12.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { d -> ChonChip(t(TEN_THU[d - 1], TEN_THU_EN[d - 1]), d in ngay) { ngay = if (d in ngay) ngay - d else ngay + d } }
        }
        NutPhu(t("Thêm khung này", "Add this period"), Icons.Rounded.Add) {
            fun chuan(s: String) = s.trim().replace('.', ':').replace('h', ':').let { if (Regex("^[0-9]:").containsMatchIn(it)) "0$it" else it }.let { if (Regex("^[0-9]{2}$").matches(it)) "$it:00" else it }
            vm.khoaKhung += PhienLamViec.KhungKhoa(chuan(tu), chuan(den), ngay); tu = ""; den = ""
        }
        Phu(t("Khung qua đêm (21:00–06:00) tính theo ngày bắt đầu: chọn T2 là khoá từ tối thứ Hai tới sáng thứ Ba.", "Overnight periods count from the starting day."))
    }
    The(t("Mã PIN", "PIN"), Icons.Rounded.Key) {
        if (vm.khoaTrenTv?.optBoolean("coPin") == true) Phu(t("Tivi đã có PIN. Để trống nếu không đổi.", "The TV already has a PIN. Leave empty to keep it."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(vm.khoaPin, { vm.khoaPin = it.filter(Char::isDigit).take(4) }, label = { Text(t("PIN 4 số", "4-digit PIN")) }, singleLine = true, modifier = Modifier.thoatOTv().weight(1f), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            OutlinedTextField(vm.khoaPin2, { vm.khoaPin2 = it.filter(Char::isDigit).take(4) }, label = { Text(t("Nhập lại", "Repeat")) }, singleLine = true, modifier = Modifier.thoatOTv().weight(1f), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        }
        Phu(t("Sai 5 lần thì tivi bắt chờ 1 phút. Quên PIN: kết nối lại tivi ở đây và đặt PIN mới. Tivi chỉ giữ dạng mã hoá của PIN.", "5 wrong tries: wait 1 minute. Forgot it: reconnect here and set a new one. The TV only keeps a hash."))
    }
    if (vm.khoaBao.isNotEmpty()) Phu(vm.khoaBao, Xanh)
    NutChinh(t("Lưu & áp dụng lên tivi", "Save & apply to the TV"), enabled = !vm.dangBan, icon = Icons.Rounded.Lock) { vm.luuKhoaTre() }
    if (vm.khoaTrenTv != null) NutPhu(t("Tắt và gỡ Khoá trẻ em", "Turn off and remove"), Icons.Rounded.LockOpen) { vm.tatKhoaTre() }
    Phu(t("Lưu ý: khoá không chống được khôi phục cài đặt gốc hay đổi sang thiết bị khác cắm HDMI. Với hộp Android cắm vào tivi, trẻ vẫn đổi nguồn tivi được. Bấm Khôi phục tivi này cũng gỡ khoá.",
        "Note: it can't stop a factory reset or another device on HDMI; with a box plugged into a TV, the TV's own input switch still works. Restore this TV also removes the lock."))
}

/** Ô liên hệ chỉ nhận email (góp ý trả lời qua thư). */
private fun laEmail(s: String) = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""").matches(s.trim())

/** Màu chữ đồng hồ cho sẵn: dễ đọc trên ảnh nền sáng / tối. */
private val MAU_CHU = listOf(Triple("Trắng", "White", "#FFFFFF"), Triple("Vàng", "Yellow", "#FFD54F"), Triple("Xanh ngọc", "Cyan", "#4DD0E1"),
    Triple("Xanh lá", "Green", "#81C784"), Triple("Hồng", "Pink", "#F48FB1"), Triple("Cam", "Orange", "#FFB74D"), Triple("Đen", "Black", "#111111"))
