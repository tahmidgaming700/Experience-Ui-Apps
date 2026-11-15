package com.experience.converter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.experience.converter.model.Category

val Category.icon: ImageVector
    get() = when (this) {
        Category.LENGTH -> Icons.Outlined.Straighten
        Category.AREA -> Icons.Outlined.SquareFoot
        Category.VOLUME -> Icons.Outlined.WaterDrop
        Category.MASS -> Icons.Outlined.Scale
        Category.TEMPERATURE -> Icons.Outlined.Thermostat
        Category.SPEED -> Icons.Outlined.Speed
        Category.TIME -> Icons.Outlined.Schedule
        Category.DIGITAL -> Icons.Outlined.SdStorage
        Category.ENERGY -> Icons.Outlined.Bolt
        Category.PRESSURE -> Icons.Outlined.Compress
        Category.ANGLE -> Icons.Outlined.Explore
        Category.FUEL -> Icons.Outlined.LocalGasStation
        Category.FREQUENCY -> Icons.Outlined.GraphicEq
        Category.NUMBER_BASE -> Icons.Outlined.Tag
    }

/** Short subtitle for the category grid. */
val Category.example: String
    get() = when (this) {
        Category.NUMBER_BASE -> "BIN · OCT · DEC · HEX"
        else -> units.take(3).joinToString(" · ") { it.symbol }
    }
