package app.abcvorschule.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.ui.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSheet(
    currentMode: ParentMode,
    unlockAllLessons: Boolean,
    onSelectMode: (ParentMode) -> Unit,
    onToggleUnlockAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.parent_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.parent_section_difficulty),
                style = MaterialTheme.typography.titleMedium,
                color = MutedText,
            )
            Column(modifier = Modifier.selectableGroup()) {
                ModeOption(
                    label = stringResource(R.string.mode_auto),
                    selected = currentMode == ParentMode.Auto,
                    onSelect = { onSelectMode(ParentMode.Auto) },
                )
                ModeOption(
                    label = stringResource(R.string.mode_beginner),
                    selected = currentMode == ParentMode.Beginner,
                    onSelect = { onSelectMode(ParentMode.Beginner) },
                )
                ModeOption(
                    label = stringResource(R.string.mode_advanced),
                    selected = currentMode == ParentMode.Advanced,
                    onSelect = { onSelectMode(ParentMode.Advanced) },
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = unlockAllLessons,
                        onValueChange = onToggleUnlockAll,
                        role = Role.Checkbox,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Nur die Zeile ist Klickziel; ein zweites an der Checkbox lässt TalkBack
                // die Zeile doppelt vorlesen.
                Checkbox(checked = unlockAllLessons, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.parent_unlock_all),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Wie bei der Checkbox-Zeile: zwei Klickziele hieße doppelte TalkBack-Ausgabe.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
