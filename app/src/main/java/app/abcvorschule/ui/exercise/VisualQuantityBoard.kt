package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftSand

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualQuantityBoard(
    emoji: String,
    left: Int,
    right: Int,
    choices: List<Int>,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = List(left) { emoji }.joinToString(" ") + "  +  " + List(right) { emoji }.joinToString(" "),
            style = MaterialTheme.typography.headlineMedium,
            color = SoftSand,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            choices.forEach { value ->
                Box(
                    modifier = Modifier
                        .background(NightElevated, RoundedCornerShape(18.dp))
                        .clickable { onChoose(value) }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("math_choice_$value"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = List(value) { emoji }.joinToString(""),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}
