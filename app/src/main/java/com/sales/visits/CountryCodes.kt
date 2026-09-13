package com.sales.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A country dial code with a flag and ISO code, for the phone-number picker. */
data class Country(val flag: String, val name: String, val dial: String, val iso: String)

val COUNTRIES: List<Country> = listOf(
    Country("🇸🇦", "Saudi Arabia", "+966", "SA"),
    Country("🇪🇬", "Egypt", "+20", "EG"),
    Country("🇦🇪", "United Arab Emirates", "+971", "AE"),
    Country("🇶🇦", "Qatar", "+974", "QA"),
    Country("🇰🇼", "Kuwait", "+965", "KW"),
    Country("🇧🇭", "Bahrain", "+973", "BH"),
    Country("🇴🇲", "Oman", "+968", "OM"),
    Country("🇯🇴", "Jordan", "+962", "JO"),
    Country("🇱🇧", "Lebanon", "+961", "LB"),
    Country("🇮🇶", "Iraq", "+964", "IQ"),
    Country("🇸🇾", "Syria", "+963", "SY"),
    Country("🇵🇸", "Palestine", "+970", "PS"),
    Country("🇾🇪", "Yemen", "+967", "YE"),
    Country("🇱🇾", "Libya", "+218", "LY"),
    Country("🇹🇳", "Tunisia", "+216", "TN"),
    Country("🇩🇿", "Algeria", "+213", "DZ"),
    Country("🇲🇦", "Morocco", "+212", "MA"),
    Country("🇸🇩", "Sudan", "+249", "SD"),
    Country("🇹🇷", "Türkiye", "+90", "TR"),
    Country("🇺🇸", "United States", "+1", "US"),
    Country("🇬🇧", "United Kingdom", "+44", "GB"),
    Country("🇩🇪", "Germany", "+49", "DE"),
    Country("🇫🇷", "France", "+33", "FR"),
    Country("🇮🇹", "Italy", "+39", "IT"),
    Country("🇪🇸", "Spain", "+34", "ES"),
    Country("🇳🇱", "Netherlands", "+31", "NL"),
    Country("🇸🇪", "Sweden", "+46", "SE"),
    Country("🇨🇭", "Switzerland", "+41", "CH"),
    Country("🇷🇺", "Russia", "+7", "RU"),
    Country("🇮🇳", "India", "+91", "IN"),
    Country("🇵🇰", "Pakistan", "+92", "PK"),
    Country("🇧🇩", "Bangladesh", "+880", "BD"),
    Country("🇮🇩", "Indonesia", "+62", "ID"),
    Country("🇲🇾", "Malaysia", "+60", "MY"),
    Country("🇨🇳", "China", "+86", "CN"),
    Country("🇯🇵", "Japan", "+81", "JP"),
    Country("🇰🇷", "South Korea", "+82", "KR"),
    Country("🇨🇦", "Canada", "+1", "CA"),
    Country("🇧🇷", "Brazil", "+55", "BR"),
    Country("🇿🇦", "South Africa", "+27", "ZA"),
    Country("🇳🇬", "Nigeria", "+234", "NG"),
    Country("🇰🇪", "Kenya", "+254", "KE"),
    Country("🇦🇺", "Australia", "+61", "AU"),
)

/** The default country for a blank phone field — set from the device locale at launch. */
var appDefaultCountry: Country = COUNTRIES.first()
    private set

fun setDefaultCountryFromIso(iso: String) {
    COUNTRIES.firstOrNull { it.iso.equals(iso, ignoreCase = true) }?.let { appDefaultCountry = it }
}

/** Splits a stored full number back into a country + local part for editing. */
fun splitPhone(full: String): Pair<Country, String> {
    val trimmed = full.trim()
    if (trimmed.startsWith("+")) {
        val match = COUNTRIES.filter { trimmed.startsWith(it.dial) }.maxByOrNull { it.dial.length }
        if (match != null) return match to trimmed.removePrefix(match.dial).trim()
    }
    return appDefaultCountry to trimmed
}

/** Combines a country dial code and a local number into a single stored value (empty when no number). */
fun joinPhone(country: Country, number: String): String =
    if (number.isBlank()) "" else "${country.dial}${number.trim()}"

/**
 * Turns raw number-field text into the stored value (dial code + local digits). If the text
 * carries a country code (pasted `+966…` or `00966…`), that country is detected and its code
 * moved to the dropdown instead of staying in the number.
 */
private fun applyNumberInput(currentPhone: String, raw: String): String {
    val normalized = when {
        raw.trim().startsWith("+") -> raw.trim()
        raw.trim().startsWith("00") -> "+" + raw.trim().drop(2)
        else -> null
    }
    if (normalized != null) {
        val match = COUNTRIES.filter { normalized.startsWith(it.dial) }.maxByOrNull { it.dial.length }
        if (match != null) return match.dial + normalized.removePrefix(match.dial).filter { it.isDigit() }
    }
    return splitPhone(currentPhone).first.dial + raw
}

/** Phone input: a country-code dropdown followed by the local number field, over one stored value. */
@Composable
fun PhoneField(phone: String, onPhone: (String) -> Unit) {
    val c = LocalSales.current
    var open by remember { mutableStateOf(false) }
    val split = splitPhone(phone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Surface(shape = RoundedCornerShape(12.dp), color = c.sunk, onClick = { open = true }) {
                Text(
                    "${split.first.flag}  ${split.first.dial}  ▾",
                    Modifier.padding(horizontal = 14.dp, vertical = 17.dp),
                    color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                COUNTRIES.forEach { co ->
                    DropdownMenuItem(
                        text = { Text("${co.flag}  ${co.name}  ${co.dial}", color = c.ink) },
                        onClick = { onPhone(co.dial + splitPhone(phone).second); open = false },
                    )
                }
            }
        }
        TextField(
            value = split.second, onValueChange = { onPhone(applyNumberInput(phone, it)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = c.surface, unfocusedContainerColor = c.sunk,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
            ),
        )
    }
}
