package app.abcvorschule.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.abcvorschule.content.ContentPack
import app.abcvorschule.debug.TtsDebugEntry
import app.abcvorschule.debug.TtsDebugGroup
import app.abcvorschule.debug.TtsDebugRepository
import app.abcvorschule.debug.ttsDebugEntries
import app.abcvorschule.ui.components.AbcCloseButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlinx.coroutines.launch

@Composable
fun TtsDebugScreen(
    pack: ContentPack,
    repository: TtsDebugRepository,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val entries = remember(pack) { pack.ttsDebugEntries() }
    val overrides by repository.overridesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var query by remember { mutableStateOf("") }

    val filtered = remember(entries, query) {
        if (query.isBlank()) {
            entries
        } else {
            entries.filter {
                it.id.contains(query, ignoreCase = true) ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.originalText.contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(horizontal = AbcDimens.screenHorizontal, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "TTS Debug", style = MaterialTheme.typography.titleLarge, color = SoftSand)
            AbcCloseButton(onClick = onClose)
        }

        Spacer(Modifier.height(AbcDimens.chromeGap))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tts_debug_search"),
            singleLine = true,
            placeholder = { Text("Suche…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SoftSky,
                unfocusedBorderColor = SoftSky.copy(alpha = 0.5f),
                focusedTextColor = SoftSand,
                unfocusedTextColor = SoftSand,
            ),
        )

        Spacer(Modifier.height(AbcDimens.chromeGap))

        TextButton(onClick = { scope.launch { repository.clearAll() } }) {
            Text("Alles zurücksetzen", color = MutedText)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AbcDimens.screenBottomExtra),
        ) {
            TtsDebugGroup.entries.forEach { group ->
                val groupEntries = grouped[group].orEmpty()
                if (groupEntries.isEmpty()) return@forEach
                item(key = "header_$group") {
                    Text(
                        text = groupTitle(group),
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedText,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(groupEntries, key = { it.id }) { entry ->
                    TtsDebugRow(
                        entry = entry,
                        overrideText = overrides[entry.id],
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeak = onSpeak,
                        onEdit = { newText ->
                            scope.launch { repository.setOverride(entry.id, newText, entries) }
                        },
                        onReset = {
                            scope.launch { repository.clearOverride(entry.id, entries) }
                        },
                    )
                }
            }
        }
    }
}

private fun groupTitle(group: TtsDebugGroup): String = when (group) {
    TtsDebugGroup.Atom -> "Atome"
    TtsDebugGroup.Sentence -> "Sätze"
    TtsDebugGroup.Task -> "Aufgaben"
}

@Composable
private fun TtsDebugRow(
    entry: TtsDebugEntry,
    overrideText: String?,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onEdit: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentText = overrideText ?: entry.originalText
    var editing by remember(entry.id) { mutableStateOf(false) }
    var draft by remember(entry.id, currentText) { mutableStateOf(currentText) }
    val focusRequester = remember(entry.id) { FocusRequester() }
    var wasFocused by remember(entry.id) { mutableStateOf(false) }

    LaunchedEffect(editing) {
        // Only steal focus when entering edit mode — never on first row composition.
        if (editing) focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = NightPanel,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.label, style = MaterialTheme.typography.labelMedium, color = MutedText)
                    if (overrideText != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(text = "bearbeitet", style = MaterialTheme.typography.labelSmall, color = SoftGold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focus ->
                                // onFocusChanged fires immediately when this field first enters
                                // composition, reporting isFocused = false even though nothing
                                // real happened yet. Only commit on an actual true -> false
                                // transition, tracked via wasFocused.
                                if (wasFocused && !focus.isFocused) {
                                    editing = false
                                    if (draft != currentText) onEdit(draft)
                                }
                                wasFocused = focus.isFocused
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftSky,
                            unfocusedBorderColor = SoftSky.copy(alpha = 0.5f),
                            focusedTextColor = SoftSand,
                            unfocusedTextColor = SoftSand,
                        ),
                    )
                } else {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SoftSand,
                        modifier = Modifier.clickable {
                            draft = currentText
                            editing = true
                        },
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (overrideText != null) {
                FilledTonalIconButton(
                    onClick = onReset,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text("↺", color = SoftSand)
                }
                Spacer(Modifier.width(4.dp))
            }

            AbcSpeakerButton(
                enabled = ttsAvailable,
                speaking = speaking,
                onClick = { onSpeak(currentText) },
            )
        }
    }
}
