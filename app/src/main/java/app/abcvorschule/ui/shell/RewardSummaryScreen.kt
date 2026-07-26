package app.abcvorschule.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.IconStar

@Composable
fun RewardSummaryScreen(
    sessionPoints: Int,
    totalPoints: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popped = true }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.7f,
        animationSpec = tween(500),
        label = "reward-scale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.35f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconStar(
                tint = MaterialTheme.colorScheme.primary,
                size = 64.dp,
                modifier = Modifier.scale(scale),
            )
            Text(
                text = stringResource(R.string.reward_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "+$sessionPoints  ·  Gesamt $totalPoints",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.weight(0.65f))
        AbcContinueButton(
            onClick = onContinue,
            centered = true,
        )
        Spacer(Modifier.height(12.dp))
    }
}
