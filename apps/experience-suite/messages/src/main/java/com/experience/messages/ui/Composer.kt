package com.experience.messages.ui

import android.telephony.SmsMessage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.experience.design.theme.ExperienceShape
import com.experience.messages.SimInfo

/**
 * Message composer: text field, SIM selector (multi-SIM) and an SMS segment counter
 * from SmsMessage.calculateLength (GSM 7-bit vs UCS-2 aware).
 */
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    sims: List<SimInfo>,
    subId: Int,
    onSubId: (Int) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (sims.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    sims.forEach { sim ->
                        FilterChip(
                            selected = sim.subId == subId, onClick = { onSubId(sim.subId) },
                            label = { Text(sim.label) }, leadingIcon = { Icon(Icons.Filled.SimCard, contentDescription = null, Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text, onValueChange = onText, enabled = enabled,
                    placeholder = { Text(if (enabled) "Text message" else "Set as default SMS app to send") },
                    shape = ExperienceShape.listSingle, maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = if (text.isNotEmpty()) ({ SegmentCounter(text) }) else null,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend, enabled = enabled && text.isNotBlank(),
                    modifier = Modifier.padding(bottom = if (text.isNotEmpty()) 24.dp else 4.dp).size(56.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

@Composable
private fun SegmentCounter(text: String) {
    // [message count, code units used, code units remaining, code unit size]
    val counts = remember(text) { runCatching { SmsMessage.calculateLength(text, false) }.getOrNull() }
    if (counts != null) {
        val label = "${counts[2]} left · ${counts[0]} SMS"
        Text(label, modifier = Modifier.semantics { contentDescription = "${counts[2]} characters left in this part, ${counts[0]} text messages" })
    }
}
