package com.experience.converter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.experience.converter.model.Category
import com.experience.converter.ui.BaseScreen
import com.experience.converter.ui.ConvertScreen
import com.experience.converter.ui.HomeScreen
import com.experience.design.ExperienceActivity
import com.experience.design.components.WidthClass
import com.experience.design.components.currentWidthClass
import com.experience.design.theme.ExperienceMotion

class MainActivity : ExperienceActivity() {
    @Composable
    override fun Content() {
        val vm: ConverterViewModel = viewModel()
        val wide = currentWidthClass() != WidthClass.COMPACT
        if (wide) {
            // Tablets: category list on the left, converter on the right.
            val category = vm.selected ?: Category.LENGTH
            Row {
                Box(Modifier.width(380.dp).fillMaxHeight()) { HomeScreen(vm, selected = category, compactGrid = true) }
                VerticalDivider()
                Box(Modifier.weight(1f)) { Detail(vm, category, onBack = null) }
            }
        } else {
            BackHandler(enabled = vm.selected != null) { vm.open(null) }
            AnimatedContent(
                targetState = vm.selected,
                transitionSpec = {
                    (slideInHorizontally(ExperienceMotion.defaultSpatial()) { if (targetState != null) it / 4 else -it / 4 } +
                        fadeIn(ExperienceMotion.defaultEffects())) togetherWith fadeOut(ExperienceMotion.fastEffects())
                },
                label = "screen",
            ) { selected ->
                if (selected == null) HomeScreen(vm, selected = null, compactGrid = false)
                else Detail(vm, selected, onBack = { vm.open(null) })
            }
        }
    }

    @Composable
    private fun Detail(vm: ConverterViewModel, category: Category, onBack: (() -> Unit)?) {
        if (category == Category.NUMBER_BASE) BaseScreen(vm, onBack) else ConvertScreen(vm, category, onBack)
    }
}
