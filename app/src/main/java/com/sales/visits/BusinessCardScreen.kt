package com.sales.visits

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Full-screen shareable contact card: shows a QR of the rep's vCard for a customer to scan,
 * plus a Share button that sends the vCard file so they can save the contact remotely too.
 */
@Composable
fun BusinessCardScreen(profile: UserProfile, loginEmail: String, onBack: () -> Unit) {
    val c = LocalSales.current
    val t = LocalL.current
    val ctx = LocalContext.current
    val vcard = remember(profile, loginEmail) { vCardFor(profile, loginEmail) }
    val qr = remember(vcard) { qrBitmap(vcard).asImageBitmap() }
    val subtitle = listOf(profile.jobTitle, profile.company).filter { it.isNotBlank() }.joinToString(" · ")

    // Publish the card so the NFC (HCE) service can hand it over on a tap.
    LaunchedEffect(vcard) {
        ctx.getSharedPreferences("sales_visits", Context.MODE_PRIVATE)
            .edit().putString("nfc_vcard", vcard).apply()
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(if (t.en) AppIcons.ArrowBack else AppIcons.ArrowForward, t["done"]) { onBack() }
            Spacer(Modifier.width(12.dp))
            Text(
                t["share_card"], color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalDisplayFont.current, modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                t["share_card_desc"], color = c.muted, fontSize = 13.5.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            // The QR always sits on a white card so any scanner can read it, even in dark mode.
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap = qr, contentDescription = null, modifier = Modifier.size(240.dp))
                    if (profile.name.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(profile.name, color = Color(0xFF0B0B0B), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, color = Color(0xFF6B6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                t["nfc_hint"], color = c.muted, fontSize = 12.5.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = { shareCard(ctx, vcard, t["share_card"]) },
                shape = RoundedCornerShape(16.dp), color = c.ink,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Share, null, tint = c.onInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t["share"], color = c.onInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun shareCard(ctx: android.content.Context, vcard: String, chooserTitle: String) {
    runCatching {
        val file = File(ctx.filesDir, "visitflow-card.vcf").apply { writeText(vcard) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
