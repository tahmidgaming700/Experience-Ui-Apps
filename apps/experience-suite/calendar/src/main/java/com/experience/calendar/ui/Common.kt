package com.experience.calendar.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.calendar.R
import com.experience.calendar.data.EventInstance
import com.experience.calendar.logic.EventDays
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Calendar colours are stored opaque or with alpha 0; force opaque. */
fun calendarColor(argb: Int) = Color(argb or 0xFF000000.toInt())

fun monthTitle(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun longDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
fun mediumDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

/** "10:00 – 11:00", "All day", or with dates when the span crosses days. */
fun timeRange(context: Context, e: EventInstance): String {
    if (e.allDay) {
        if (e.endDay > e.startDay) {
            return DateUtils.formatDateRange(context, e.begin, e.end, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_UTC or DateUtils.FORMAT_ABBREV_MONTH)
        }
        return context.getString(R.string.all_day)
    }
    val zone = ZoneId.systemDefault()
    val span = EventDays.span(false, e.begin, e.end, e.startDay, e.endDay, zone)
    val flags = DateUtils.FORMAT_SHOW_TIME or if (span.start != span.endInclusive) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH else 0
    return DateUtils.formatDateRange(context, e.begin, e.end, flags)
}

/** List row for an occurrence: colour bar, title, time and location. */
@Composable
fun EventRow(e: EventInstance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(6.dp).height(40.dp).background(calendarColor(e.color), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title.ifBlank { stringResource(R.string.untitled_event) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOf(timeRange(context, e), e.location).filter { it.isNotBlank() }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.recurring) Icon(Icons.Outlined.Repeat, stringResource(R.string.repeating), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun FillHeight(content: @Composable () -> Unit) = Box(Modifier.fillMaxHeight()) { content() }
