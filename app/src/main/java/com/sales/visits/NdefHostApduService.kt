package com.sales.visits

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Emulates an NFC Forum Type 4 NDEF tag (Host Card Emulation) that serves the rep's vCard,
 * so tapping the phone against an NFC reader (or a phone in NFC-reading mode) hands over the
 * contact card — the NFC counterpart to the QR flow.
 *
 * The card content is the vCard string persisted under "nfc_vcard" whenever the card screen opens.
 */
class NdefHostApduService : HostApduService() {
    private var selectedFile: ByteArray? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_ERROR

        // SELECT application by AID (the NDEF Type 4 app) → acknowledge.
        if (apdu.size >= 12 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
            apdu[2] == 0x04.toByte() && apdu[3] == 0x00.toByte()
        ) return SW_OK

        // SELECT file by identifier (P1P2 = 00 0C, Lc = 02, data = 2-byte file id).
        if (apdu.size >= 7 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte() &&
            apdu[2] == 0x00.toByte() && apdu[3] == 0x0C.toByte() && apdu[4] == 0x02.toByte()
        ) {
            val fid = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
            selectedFile = when (fid) {
                0xE103 -> CC_FILE
                0xE104 -> ndefFile()
                else -> null
            }
            return if (selectedFile != null) SW_OK else SW_FILE_NOT_FOUND
        }

        // READ BINARY: return the requested slice of the selected file + status.
        if (apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB0.toByte()) {
            val file = selectedFile ?: return SW_ERROR
            val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
            var le = apdu[4].toInt() and 0xFF
            if (le == 0) le = 256
            if (offset >= file.size) return SW_ERROR
            val end = minOf(offset + le, file.size)
            return file.copyOfRange(offset, end) + SW_OK
        }
        return SW_ERROR
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = null
    }

    /** NDEF file = 2-byte length prefix + the NDEF message. */
    private fun ndefFile(): ByteArray {
        val message = ndefMessage(currentVCard())
        return byteArrayOf((message.size ushr 8).toByte(), (message.size and 0xFF).toByte()) + message
    }

    private fun currentVCard(): String =
        getSharedPreferences("sales_visits", Context.MODE_PRIVATE).getString("nfc_vcard", "").orEmpty()

    /** A single NDEF MIME ("text/vcard") record wrapping the vCard text. */
    private fun ndefMessage(vcard: String): ByteArray {
        val payload = vcard.toByteArray(Charsets.UTF_8)
        val type = "text/vcard".toByteArray(Charsets.US_ASCII)
        val short = payload.size < 256
        val out = ArrayList<Byte>()
        // Header: MB=1, ME=1, SR=short, TNF=0x02 (MIME) → 0xD2 (short) / 0xC2 (long).
        out.add((0xC0 or (if (short) 0x10 else 0x00) or 0x02).toByte())
        out.add(type.size.toByte())
        if (short) {
            out.add(payload.size.toByte())
        } else {
            out.add((payload.size ushr 24).toByte())
            out.add((payload.size ushr 16).toByte())
            out.add((payload.size ushr 8).toByte())
            out.add((payload.size and 0xFF).toByte())
        }
        type.forEach { out.add(it) }
        payload.forEach { out.add(it) }
        return out.toByteArray()
    }

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_ERROR = byteArrayOf(0x6F, 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())

        /** Capability Container: points readers at the NDEF file (id E104, read-only). */
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,          // CCLEN = 15
            0x20,                // mapping version 2.0
            0x00, 0x3B,          // MLe (max bytes in a response)
            0x00, 0x34,          // MLc (max bytes in a command)
            0x04, 0x06,          // NDEF File Control TLV (T=04, L=06)
            0xE1.toByte(), 0x04, // NDEF file id
            0x7F, 0xFF.toByte(), // max NDEF file size (32767)
            0x00,                // read access: granted
            0xFF.toByte(),       // write access: denied
        )
    }
}
