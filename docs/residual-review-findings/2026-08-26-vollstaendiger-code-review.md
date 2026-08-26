# Vollständiger Code-Review 2026-08-26 — bewusst offene Reste

**Datum:** 2026-08-26 · **Status:** offen
**Betrifft:** Content-Pack, Audio-Paket, `SoundPositionTrainer`, `SessionViewModel`

Sieben Review-Agenten haben App, Content und Build durchgesehen; der Großteil ist
in den Commits vom selben Tag behoben (Jagd-Batterie, Freischaltkette,
Ä/Eu-Glyphen, Schild-Bilder, Audio-Kanäle, Trainer-Zustände, Kontraste,
Zeichenphase, README, R8-Regeln). Was bewusst liegen bleibt, steht hier — mit dem
Grund, warum es nicht im selben Zug erledigt wurde.

## 1. 41 fehlende Sprachclips

**18 Artikel-Erfolgsclips**: `der Hut` (L05), `das Nest`, `die Rose` (L07),
`die Dose`, `der Keks` (L08), `das Haus`, `der Baum` (L11/22), `die Bäume`,
`die Rübe` (L12/25), `der Schuh` (L13/23), `das Zebra`, `die Eule` (L14),
`der Sack`, `der Apfel` (L16), `die Spinne` (L17/24), `die Qualle`, `das Taxi`
(L18/26), `das Ei` (L22). `SuccessSpeech` spricht bei `word_build` den Artikel
mit; die nackten Wörter liegen im Index, die Artikel-Fassung nicht. Auf einem
Gerät ohne deutsches TTS bleibt der Erfolgs-Vorsprech dort still.

**23 Silbenschnipsel** des Wort-Bauers: `Ha`, `Ro`, `Da`, `Hä`, `Bä`, `Rü`, `le`,
`ra`, `Fu`, `gel`, `Va`, `se`, `Sa`, `pfe`, `Pfe`, `Ster`, `Spin`, `ne`, `Ta`,
`Po`, `ny`. Seit dem Fix an `SpeechClipText.forWordBlock` spricht die Kachel, was
auf ihr steht — ohne Clip über Android-TTS statt in kuratierter Stimme.

Beides braucht einen Lauf von `tools/tts/` (lokales Qwen3-TTS, eigene venv, siehe
`tools/tts/README.md`) und danach ein Durchhören — deshalb nicht nebenbei erledigt.

## 2. Auditiver Finder: die Zugreihe ist breiter als die Bühne

`SoundPositionTrainer.kt:136-156`. `LocomotiveHead` 72dp + 3 × `WagonSize` 96dp +
3 × 6dp Abstand = **378dp**, verfügbar sind auf einem 360dp-Gerät 296dp. Die `Row`
bricht nicht um und scrollt nicht, der letzte Waggon bekommt die Restbreite und
fällt unter den 56dp-Boden — seine Drop-Zone schrumpft mit.

Zur Laufzeit unerreichbar, weil `sound_position` in `PausedTrainerKinds` steht.
**Vor einer Reaktivierung zu beheben**: Waggon-Breite aus `BoxWithConstraints`
ableiten, Muster `WordFrameSizing.frameWidthDp`.

## 3. Aufgaben, die nicht fehlschlagen können

`l22-t7` („Ei") und `l19-t8` („am") haben einen Baustein, einen Rahmen und keinen
Distraktor — jeder Tipp ist richtig. Dasselbe bei den Einwort-`sentence_order`-
Runden `l01-t8`, `l02-t9`, `l19-t9`, `l20-t9`. Die Prinzipien verlangen, dass eine
Aufgabe fehlschlagen kann; der `ContentValidator` prüft nur auf leere Listen.

Nicht im Vorbeigehen zu beheben: der Fix ist Content-Autorierung (ein bekannter
Distraktor je Runde) plus eine Validator-Regel, die dann für den ganzen Pack gilt.
Für `l22-t7` kollidiert er zusätzlich mit „erste Begegnung distraktorfrei" — in
einer Wiederholungslektion ist das aber keine erste Begegnung.

## 4. `difficultyBand` ist toter Content-Vertrag

Alle 52 `count_add`-Runden haben `difficultyBand: null`; `ProgressionEngine`
rechnet ohnehin über `bandFor(answer)`. Entweder autorieren oder aus
`CountAddRound` streichen — beides eine eigene Entscheidung.

## 5. `SessionViewModel` ist ungetestet

661 Zeilen, die als einzige den Fortschritt des Kindes schreiben, ohne einen Test.
Alles darum herum ist gut abgedeckt (`LessonGating`, `ProgressionEngine`,
`SessionProgression`, `SessionTrainers`, `ProgressRepository`), aber diese Tests
bauen `LearnerProgress` von Hand — die Stelle, die ihn wirklich erzeugt, prüft
niemand. Die Infrastruktur steht bereit (injizierbarer DataStore, In-Memory-Fake in
`ProgressRepositoryTest`, `kotlinx-coroutines-test` auf dem Klassenpfad).

Lohnend wären: Buchung unter der id des geplanten Trainers (inkl. synthetischer
Jagd-ids), die Resume-Matrix aus `bootstrap()`, das Löschen des Snapshots vor dem
`RewardSummary`, der `stillAt()`-Abbruch und `openLesson()` auf eine gesperrte
Lektion.

## 6. Kein `rememberSaveable` in den Trainern

Rundeninterner Zustand (gesetzte Bausteine, Miss-Zähler) überlebt keinen
Activity-Neuaufbau — auf dem Testgerät am ehesten durch einen Wechsel der
Systemschriftgröße im laufenden Betrieb ausgelöst. Position und Statistik
überleben (ViewModel + DataStore), die Runde beginnt aber von vorn. Kein
Fortschrittsverlust, deshalb nicht dringend; der Fix wäre ein `Saver` je Trainer.

## 7. Shot-Tests laufen im normalen `connectedDebugAndroidTest` mit

Fünf der zehn instrumentierten Tests behaupten nichts, sie rendern. Sie kosten
damit Emulatorzeit und Kollisionsfenster in einem Lauf, dessen „grün" nach
Assertions klingt. Ein eigener Task oder eine filterbare Annotation wäre sauberer.
