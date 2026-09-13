package com.sales.visits

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A shareable digital business card (like NameDrop): the rep's own contact details encoded as a
 * vCard, so a customer can scan the QR with their phone camera and save the contact instantly.
 */

/** Escapes a value for a vCard property line per RFC 6350. */
private fun esc(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

/** Builds a vCard 3.0 payload from the profile plus the account (login) email. */
fun vCardFor(profile: UserProfile, loginEmail: String): String = buildString {
    append("BEGIN:VCARD\r\n")
    append("VERSION:3.0\r\n")
    append("FN:${esc(profile.name)}\r\n")
    if (profile.company.isNotBlank()) append("ORG:${esc(profile.company)}\r\n")
    if (profile.jobTitle.isNotBlank()) append("TITLE:${esc(profile.jobTitle)}\r\n")
    if (profile.phone.isNotBlank()) append("TEL;TYPE=CELL:${esc(profile.phone)}\r\n")
    if (profile.companyEmail.isNotBlank()) append("EMAIL;TYPE=WORK:${esc(profile.companyEmail)}\r\n")
    if (loginEmail.isNotBlank() && profile.shareLoginEmail) append("EMAIL;TYPE=HOME:${esc(loginEmail)}\r\n")
    append("END:VCARD")
}

/** Renders [content] as a QR code bitmap (black modules on white, so any scanner reads it). */
fun qrBitmap(content: String, size: Int = 720): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
