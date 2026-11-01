package zone.vgs.tvsach.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoNhoTest {
    @Test fun docDfToybox() {
        val df = """
            Filesystem       1K-blocks    Used Available Use% Mounted on
            /dev/block/dm-37  10167132 2239572   7780104  23% /data/user/0
        """.trimIndent()
        assertEquals(7780104L * 1024 to 10167132L * 1024, ThaoTacTv.docDf(df))
    }

    @Test fun canTrongTheoLuatAndroid() {
        val mb = 1024L * 1024
        // Máy ảo 9,7 GB: 5% ≈ 496 MB, cộng 30 MB; 240 MB trống là Android từ chối cài.
        assertEquals(10167132L * 1024 / 20 + 30 * mb, ThaoTacTv.canTrong(10167132L * 1024))
        // Bộ nhớ lớn: phần giữ lại tối đa 500 MB.
        assertEquals(530 * mb, ThaoTacTv.canTrong(64L * 1024 * mb))
    }

    @Test fun docDfHong() {
        assertNull(ThaoTacTv.docDf("df: /data: Permission denied"))
        assertNull(ThaoTacTv.docDf(""))
    }
}
