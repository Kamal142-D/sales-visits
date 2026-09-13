package com.sales.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI

// ------------------------------- customer editor -------------------------------

@Composable
fun CustomerEditorDialog(store: Store, initial: Customer?, onClose: () -> Unit) {
    val t = LocalL.current
    val c = LocalSales.current
    val id = initial?.id
        ?: (System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36))
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var industry by remember { mutableStateOf(initial?.industry ?: "") }
    var contact by remember { mutableStateOf(initial?.contact ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var locationUrl by remember { mutableStateOf(initial?.locationUrl ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AppDialog(widthDp = 560, onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(
                if (initial == null) t["new_customer"] else editLabel(I18n.en),
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink,
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                name, { name = it },
                label = { Text(t["customer_name"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    industry, { industry = it },
                    label = { Text(t["industry_hint"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text(t["phone_hint"]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                contact, { contact = it },
                label = { Text(t["contact_hint"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                address, { address = it },
                label = { Text(t["address_hint"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                locationUrl, { locationUrl = it },
                label = { Text(t["location_url_hint"]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                notes, { notes = it },
                label = { Text(t["customer_notes_hint"]) },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = c.lost, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text(t["cancel"], color = c.muted) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (name.isBlank()) error = t["error_name"]
                    else {
                        store.upsertCustomer(
                            Customer(
                                id = id, name = name.trim(), industry = industry.trim(),
                                contact = contact.trim(), phone = phone.trim(), address = address.trim(),
                                locationUrl = locationUrl.trim(), notes = notes,
                                createdAt = initial?.createdAt ?: "",
                                contacts = initial?.contacts ?: emptyList(),
                            )
                        )
                        onClose()
                    }
                }) { Text(t["done"]) }
            }
        }
    }
}

// ------------------------------- customer detail -------------------------------

@Composable
fun CustomerDetailDialog(store: Store, customerId: String, onClose: () -> Unit) {
    val t = LocalL.current
    val c = LocalSales.current
    val en = I18n.en
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val customer = store.customers.firstOrNull { it.id == customerId } ?: return
    val visits = store.visitsFor(customer)

    if (editing) { CustomerEditorDialog(store, customer, onClose = onClose); return }
    if (deleting) {
        ConfirmDialog(
            title = t["delete_customer_q"], desc = t["delete_customer_desc"],
            confirmText = t["delete"],
            onConfirm = { store.deleteCustomer(customerId); onClose() },
            onDismiss = { deleting = false },
        )
        return
    }

    AppDialog(widthDp = 560, onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(customer.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink,
                    modifier = Modifier.weight(1f))
                if (customer.industry.isNotBlank()) Chip(customer.industry, color = c.ink2, bg = c.sunk)
            }
            Spacer(Modifier.height(4.dp))
            Text("${visits.size} ${t["visit_count"]}", fontSize = 13.sp, color = c.muted)

            if (customer.contact.isNotBlank() || customer.phone.isNotBlank() || customer.address.isNotBlank() || customer.locationUrl.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.sunk).padding(12.dp)) {
                    if (customer.contact.isNotBlank()) rowInfo(customer.contact, c)
                    if (customer.phone.isNotBlank()) rowInfo(customer.phone, c)
                    if (customer.address.isNotBlank()) rowInfo(customer.address, c)
                    if (customer.locationUrl.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t["open_location"], color = c.ink2, fontSize = 13.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    runCatching { Desktop.getDesktop().browse(URI(customer.locationUrl)) }
                                },
                            )
                        }
                    }
                }
            }

            if (customer.contacts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel(t["contacts"])
                Spacer(Modifier.height(4.dp))
                customer.contacts.forEach { p ->
                    Text("${p.name}${if (p.phone.isNotBlank()) "  ·  ${p.phone}" else ""}", color = c.ink2, fontSize = 13.sp)
                }
            }

            if (customer.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                SectionLabel(t["customer_notes"])
                Spacer(Modifier.height(4.dp))
                Text(customer.notes, color = c.ink2, fontSize = 13.sp)
            }

            if (visits.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(t["visit_history"])
                Spacer(Modifier.height(6.dp))
                visits.take(12).forEach { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cardDayShort(v.date), color = c.muted, fontSize = 12.sp, modifier = Modifier.width(90.dp))
                        Text(v.typeEnum().label(en) + " · " + v.outcomeEnum().label(en), color = c.ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (v.notesFor(en).isNotBlank())
                            Icon(AppIcons.Plan, null, tint = c.faint, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editing = true }) { Text(editLabel(en)) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { deleting = true }) { Text(t["delete_customer"]) }
            }
        }
    }
}

@Composable
private fun rowInfo(text: String, c: SalesColors) {
    Text(text, color = c.ink2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
}