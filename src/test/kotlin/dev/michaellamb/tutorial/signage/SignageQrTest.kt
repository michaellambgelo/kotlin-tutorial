package dev.michaellamb.tutorial.signage

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A QR that renders but doesn't scan is useless on a TV, so these tests check the payload
 * actually decodes back out — not just that some <svg> came back.
 */
class SignageQrTest {

    private val url = "https://jxnfilm.club/events"

    /** zxing core ships no LuminanceSource; this reads a BitMatrix as dark-on-light pixels. */
    private class MatrixLuminanceSource(private val matrix: BitMatrix) :
        LuminanceSource(matrix.width, matrix.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            for (x in 0 until width) out[x] = if (matrix.get(x, y)) 0 else 255.toByte()
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[y * width + x] = if (matrix.get(x, y)) 0 else 255.toByte()
                }
            }
            return out
        }
    }

    @Test
    fun `a rendered code decodes back to the original url`() {
        // Same content and hints qrSvg uses, rendered at a scannable pixel scale so the
        // decoder has more than one pixel per module to work with.
        val matrix = QRCodeWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            300,
            300,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        val result = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(MatrixLuminanceSource(matrix))),
            mapOf(DecodeHintType.TRY_HARDER to true),
        )
        assertEquals(url, result.text)
    }

    @Test
    fun `the svg draws exactly one subpath per dark module`() {
        val svg = requireNotNull(qrSvg(url, 132))
        val matrix = QRCodeWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        var dark = 0
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) if (matrix.get(x, y)) dark++
        }
        // Count moves inside the path data only — the aria-label carries the URL and could
        // otherwise contribute stray 'M's to a naive count over the whole document.
        val pathData = Regex("""<path d="([^"]*)"""").find(svg)!!.groupValues[1]
        assertEquals(dark, pathData.count { it == 'M' })
        assertTrue(svg.contains("""viewBox="0 0 ${matrix.width} ${matrix.height}""""))
        assertTrue(svg.contains("""width="132" height="132""""))
    }

    @Test
    fun `an empty payload yields null rather than throwing`() {
        assertNull(qrSvg("", 132))
    }

    @Test
    fun `ampersands in the url are escaped in the aria label`() {
        val svg = requireNotNull(qrSvg("https://example.com/?a=1&b=2", 96))
        assertTrue(svg.contains("&amp;"))
        assertTrue(!svg.contains("&b=2"))
    }
}
