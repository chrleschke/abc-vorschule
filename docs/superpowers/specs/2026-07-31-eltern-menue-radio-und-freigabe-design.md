# Eltern-Menü: echte Radio Buttons und freie Lektionsreihenfolge

Status: implementation-ready
Datum: 2026-07-31
Basis: `8aa3f8c` (Pfad-Screen-Feature enthalten)

## 1. Problem

Das Eltern-Sheet (`DifficultySheet.kt`) zeigt die Auswahl der Hilfestufe über
interpolierte Text-Glyphen: `[~] Auto` für ausgewählt, ` ~  Auto` für nicht ausgewählt.
Das ist keine Auswahl-Semantik, die das Framework kennt — TalkBack liest drei Buttons
ohne Gruppen- oder Auswahlzustand, und die Klammer als Auswahlanzeige muss man erst
lernen.

Zweitens fehlt Eltern ein Weg, eine spätere Lektion zu öffnen. Das Gating in
`LessonGating` gibt eine Lektion erst frei, wenn die vorherige gemeistert ist. Wer mit
seinem Kind gezielt Lektion 12 üben will, muss elf Lektionen durchspielen.

## 2. Entscheidungen

| Frage | Entscheidung |
| --- | --- |
| Gültigkeit der Freigabe | Persistent in `LearnerProgress` — überlebt App-Neustart, wie `parentMode` |
| Verankerung im Gating | Zusätzlicher Parameter an `isPlayable`; `states()` bleibt unverändert |
| Sheet-Aufbau | Titel „Eltern", Abschnitt „Hilfestufe" + Radios, Trennlinie, Checkbox |
| Glyphen `~ + =` | Entfallen ersatzlos |
| Checkbox-Text | „Reihenfolge frei wählbar" |
| Sheet nach Auswahl | Bleibt offen (bisher: schloss sofort) |
| TalkBack für freigegebene Knoten | `lesson_available` — der Knoten ist spielbar |
| Optik freigegebener Knoten | Unverändert abgedunkelt; nur das Schloss verschwindet |

Verworfene Alternativen:

- **`states()` mappt `Locked` → `Available`.** Weniger Diff an Aufrufern, aber
  `Available` rendert `WoodMid` mit Mint-Kontur. Um trotzdem abzudunkeln, bräuchte das
  Schild eine zweite Information „war eigentlich gesperrt" — der Zustand wäre doppelt
  geführt.
- **Vierter `ParentMode`-Wert.** Vermischt zwei orthogonale Achsen; „Ohne Hilfe +
  freigeschaltet" wäre nicht ausdrückbar.

## 3. Persistenz

`LearnerProgress` bekommt ein Feld:

```kotlin
val unlockAllLessons: Boolean = false,
```

Bestehende gespeicherte JSON lädt unverändert: ein fehlender Key fällt auf den Default
zurück, `ignoreUnknownKeys` deckt den Rückweg auf eine älteren App-Version ab.

`ProgressRepository` bekommt analog zu `setParentMode`:

```kotlin
suspend fun setUnlockAllLessons(enabled: Boolean): LearnerProgress =
    update { it.copy(unlockAllLessons = enabled) }
```

## 4. Gating

```kotlin
fun isPlayable(state: LessonState, unlockAll: Boolean = false): Boolean =
    state == LessonState.Available || state == LessonState.InProgress ||
        state == LessonState.Mastered || (unlockAll && state == LessonState.Locked)
```

**`Planned` bleibt in jedem Fall gesperrt.** Eine geplante Lektion hat keine `taskIds`;
`openLesson` würde in eine leere Trainer-Liste laufen. Die Checkbox hebt die
Fortschrittssperre auf, nicht das Fehlen von Inhalt.

`nextPlayable` bleibt unverändert: das pulsierende Schild folgt weiter dem echten
Fortschritt, nicht dem Override. Sonst verliert das Kind den „hier bist du"-Anker.

Der Default-Parameter hält bestehende Aufrufer verhaltenstreu. Es gibt genau drei
Aufrufstellen, die das Flag durchreichen müssen — eine vergessene lässt einen
freigegebenen Tap auf den Pfad zurückprallen:

1. `SessionViewModel` — Prüfung des Resume-Snapshots beim Laden
2. `SessionViewModel.openLesson`
3. `PathScreen` — `onClick` des Schildes

## 5. Eltern-Sheet

`DifficultySheet.kt` → `ParentSheet.kt`, Composable `ParentSheet`:

```kotlin
@Composable
fun ParentSheet(
    currentMode: ParentMode,
    unlockAllLessons: Boolean,
    onSelectMode: (ParentMode) -> Unit,
    onToggleUnlockAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
)
```

Aufbau von oben nach unten:

1. Titel „Eltern" (`headlineMedium`)
2. Abschnittslabel „Hilfestufe" (`titleMedium`, `MutedText`)
3. Drei Radio-Zeilen in einer `Column` mit `Modifier.selectableGroup()`
4. `HorizontalDivider`
5. Checkbox-Zeile „Reihenfolge frei wählbar"

Jede Radio-Zeile ist eine `Row` mit `Modifier.selectable(selected, onClick = …,
role = Role.RadioButton)` und `heightIn(min = 56.dp)`; der `RadioButton` selbst bekommt
`onClick = null`. Sonst hat die Zeile zwei Klickziele und TalkBack liest sie doppelt.
Die Checkbox-Zeile analog mit `Modifier.toggleable(value, role = Role.Checkbox)` und
`Checkbox(onCheckedChange = null)`.

`ModeRow` mit seiner String-Interpolation verschwindet ersatzlos.

Neue String-Ressourcen: `parent_title` („Eltern"), `parent_section_difficulty`
(„Hilfestufe"), `parent_unlock_all` („Reihenfolge frei wählbar"). `difficulty_title`
entfällt.

### 5.1 Sheet bleibt offen — und warum das eine State-Spiegelung erzwingt

Beide Bedienelemente lassen das Sheet offen; `showDifficultySheet = false` fällt aus
`setParentMode` heraus. Geschlossen wird per Wisch oder Scrim.

Das Sheet liest heute `viewModel.parentMode()` direkt, nicht aus dem State. Es
recomposed nur, weil `setParentMode` nebenbei `_ui` anfasst. Steht das Kind auf dem
Pfad, ist `trainers` leer, das `copy` erzeugt ein **gleichwertiges** `SessionUiState`,
und `MutableStateFlow` konflatiert gleiche Werte weg. Bisher fiel das nicht auf, weil
das Sheet sofort zuging. Bleibt es offen, würde die Auswahl sichtbar hängen.

Deshalb spiegelt `SessionUiState` beide Werte — dem Muster folgend, mit dem `points`
schon aus `LearnerProgress` gespiegelt wird:

```kotlin
val parentMode: ParentMode = ParentMode.Auto,
val unlockAllLessons: Boolean = false,
```

Zu setzen an **allen** Stellen, die ein `SessionUiState` neu konstruieren (drei im
ViewModel plus der Default im `MutableStateFlow`), sonst fällt die Anzeige beim
Screen-Wechsel auf den Default zurück. `viewModel.parentMode()` wird damit als
Sheet-Quelle überflüssig.

## 6. Pfad-Darstellung

`PathScreen` bekommt `unlockAllLessons: Boolean` und reicht es an die Knoten durch.
In `PathSignNode` steuert `playable` heute zwei Dinge, die jetzt auseinanderfallen:

| Aspekt | hängt künftig an |
| --- | --- |
| Tap öffnet Lektion statt `onLockedTap` | `isPlayable(state, unlockAll)` |
| 🔒-Glyph in der Ecke | `isPlayable(state, unlockAll)` |
| `stateDesc` für TalkBack | `isPlayable(state, unlockAll)` → `lesson_available` |
| Brett-, Schatten-, Kontur-, Labelfarbe | `LessonState` (unverändert) |
| Emoji-Silhouette (Alpha 0.18) | `LessonState` (unverändert) |

Ein freigegebener Knoten bleibt also `LessonState.Locked`, bleibt `WoodDark` und behält
seine Emoji-Silhouetten — abgedunkelt wie heute. Es verschwindet nur das Schloss, und
statt des Schlosses steht dort wieder der Nagel wie bei jedem anderen offenen Schild.

`onLockedTap` mit Blip, Haptik und „Das üben wir später." bleibt unverändert — greift
aber nur noch bei `Planned` und bei ausgeschalteter Checkbox.

Konkret ersetzt in `PathSignNode`: `val playable = LessonGating.isPlayable(state)` wird
zu einem Parameter des Composables (der Aufrufer kennt das Flag), plus ein lokales
`val dimmed = state == LessonState.Locked || state == LessonState.Planned` für Emoji-
Alpha. `stateDesc` wählt `lesson_available` sobald `playable`, sonst wie bisher nach
`state`.

## 7. Tests

Das Projekt fährt reine JVM-Unit-Tests (`app/src/test`); Compose-UI-Tests existieren
nicht und werden hier keine eingeführt. Abgedeckt wird also die Logik:

`LessonGatingTest`:

- `isPlayable(Locked, unlockAll = true)` ist `true`
- `isPlayable(Planned, unlockAll = true)` bleibt `false`
- `isPlayable(Locked)` ohne Argument bleibt `false` (Default-Verhalten)
- `states()` liefert mit gesetztem Flag **unverändert** `Locked` — die Optik hängt daran
- `nextPlayable` zeigt mit gesetztem Flag auf dieselbe Lektion wie ohne

`ProgressRepositoryTest`:

- `setUnlockAllLessons(true)` persistiert und lässt `parentMode` unberührt
- Ein gespeicherter JSON-Stand ohne den Key lädt mit `unlockAllLessons = false`

## 8. Doku

- `docs/PRODUCT_PRINCIPLES.md` Abschnitt 6 (Hilfestufen): das Eltern-Sheet trägt neben
  der Hilfestufe die Freigabe der Lektionsreihenfolge; Abschnitt 5: gesperrte Schilder
  bleiben abgedunkelt, verlieren bei aktiver Freigabe aber Schloss und Sperr-Hinweis.
- `docs/PRODUCT_PRINCIPLES.md` Zeile „Eltern steuern nur selten (Hilfestufe hinter
  Kindersicherung)" um die Freigabe erweitern.

## 9. Nicht in diesem Umfang

- Eltern-Dashboard oder Fortschritts-Rücksetzung
- PIN oder stärkere Kindersicherung als das bestehende Long-Press-Gate
- Freigabe einzelner Lektionen statt aller
