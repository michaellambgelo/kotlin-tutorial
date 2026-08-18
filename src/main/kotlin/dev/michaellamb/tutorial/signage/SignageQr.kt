/*
 * QR rendering for the signage view.
 *
 * Pedagogical focus: turning a bit matrix into vector output by hand. zxing hands back a
 * BitMatrix of modules; walking it into an <svg> path keeps the dependency to zxing:core —
 * no zxing:javase, so no AWT, no ImageIO, no temp file, and no second HTTP fetch from the TV.
 * One subpath per dark module ("M x y h1 v1 h-1 z") is far smaller in the HTML than one
 * <rect> element each, and `shape-rendering=crispEdges` keeps the modules hard-edged when the
 * browser scales the 1-unit-per-module viewBox up to display size.
 */
package dev.michaellamb.tutorial.signage

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private val QR_HINTS = mapOf(
    // M tolerates ~15% damage — plenty for a screen, and it keeps the module count (and so the
    // module size at a fixed pixel width) lower than Q/H would.
    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    EncodeHintType.MARGIN to 2,
    EncodeHintType.CHARACTER_SET to "UTF-8",
)

/**
 * Renders [data] as a self-contained `<svg>` QR code [pixels] wide, or null if zxing can't
 * encode it (empty string, or content too long for a single symbol).
 *
 * Dark modules on a light tile, deliberately *not* themed to the dark page: plenty of scanners
 * refuse an inverted code, and a signage screen gets one shot at being scanned.
 */
internal fun qrSvg(data: String, pixels: Int): String? {
    // Passing 0x0 asks zxing for one matrix cell per module (plus the quiet zone from MARGIN)
    // rather than a pre-scaled bitmap — the SVG viewBox does the scaling instead.
    val matrix = runCatching {
        QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, QR_HINTS)
    }.getOrNull() ?: return null

    val modules = matrix.width
    val path = buildString {
        for (y in 0 until matrix.height) {
            for (x in 0 until modules) {
                if (matrix.get(x, y)) append("M${x} ${y}h1v1h-1z")
            }
        }
    }
    val label = xmlAttr("QR code linking to $data")
    return """<svg class="qr" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $modules ${matrix.height}" """ +
        """width="$pixels" height="$pixels" shape-rendering="crispEdges" role="img" aria-label="$label">""" +
        """<rect width="$modules" height="${matrix.height}" fill="#f5f5f5"/>""" +
        """<path d="$path" fill="#111111"/></svg>"""
}

/** The SVG is inlined via kotlinx.html's `unsafe`, so attribute values are escaped by hand. */
private fun xmlAttr(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
