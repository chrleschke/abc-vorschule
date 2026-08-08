# Residual-Findings: umfassender UX- & Code-Review (2026-08-07)

Ein Multi-Agent-Review (6 UX-Reviewer + Emulator-Sichtprüfung bei font_scale 1.3 auf
360×640 dp, danach 5 Code-Reviewer) hat eine größere Fix-Welle ausgelöst (siehe
Commits dieses Branches). Die folgenden Punkte wurden **bewusst nicht** mitgefixt —
mit Begründung, damit ein späterer Review sie nicht neu „entdecken" muss oder gezielt
aufnehmen kann.

## Bewusst offen gelassen

- **Cream-Streifen hinter der transparenten Statusleiste über dem Pfad-Himmel**
  (`TaskShell.kt` zeichnet `background(Cream)` full-bleed vor dem Insets-Padding;
  `PathBackground` beginnt erst darunter). Kosmetisch; ein Fix verschiebt die
  Inset-Behandlung in die Screens und berührt jede Route. Für Übungs-/End-Screen
  (Cream-Grund) ist der Ist-Zustand korrekt.
- **Jagd-Layout: Mindestabstand ignoriert die Kachelgröße** (`SymbolHuntLayout`
  arbeitet mit 0.22 × Feldseite Zentrumsabstand; zwei Scale-1.3-Kacheln können
  sich um ~25 dp überlappen, Randkacheln ragen bis 16 dp über das Feld). Der
  `clip(CircleShape)` verhindert Fehltipps im Überlappungsbereich; skalenbewusster
  Abstand wäre der saubere Fix.
- **Jagd/Detektiv: Chevron-Tap während der 900-ms-Batteriefeier verwirft das
  verdiente Ergebnis** (Round-Key-Reset cancelt den LaunchedEffect vor
  `onResult`). Sichere Richtung (keine Fehl-Attribution); Fix hieße synchron
  melden und nur die Anzeige verzögern.
- **`ClipIndex.lookup` normalisiert keine Satzzeichen** — die `missTts` des
  (pausierten) Auditiven Finders enden mit „." und treffen deshalb keinen Clip.
  Erst relevant, wenn der Trainer reaktiviert wird; dann Clips produzieren oder
  Lookup-Normalisierung ergänzen (Vorsicht: Kuratierung unterscheidet bewusst
  nach Satzzeichen).
- **Kein AudioFocus** im gesamten Audio-Pfad (Ansagen ducken keine Hintergrund-
  Musik). Plattform-Erwartungsbruch, für die Zielnutzung verschmerzbar.
- **Kein `rememberSaveable` für In-Runden-Zustand** — Recreation (Schriftgröße
  geändert, Split-Screen) resettet das Brett der aktuellen Runde; der
  Runden-Index selbst überlebt im ViewModel. Bewusste Einfachheit, jetzt hier
  dokumentiert.
- **Composition-Phase-Reads einiger Animationswerte** (SyllableMerge-Glow/-Track,
  SymbolInWord-Flight/-Rotation, SuccessBurst, Konfetti) — endliche, kleine
  Bäume; Draw-Phase-Umbau wäre reine Perf-Politur. Der Pfad-Screen macht es
  überall vorbildlich vor.
- **`TtsDebugEntry` deckt nicht alle Pipeline-IDs** (fehlend: `finale:{id}:tts`,
  `spokenAnswer`-Strings, meiste extra-strings) — Debug-Werkzeug, kein
  Kind-Feature.
- **`SpeechController` bleibt ohne injizierbare Engine-Naht** (und damit weitgehend
  ungetestet) — ein Interface-Seam wäre der Weg zu Unit-Tests der Waiter-Maschine.
- **Sound-Position-Trainer (pausiert):** Zug (378 dp) passt nicht in 296 dp nutzbare
  Breite; kein `nudge` bei Fehlplatzierung; stummer No-Op-Tap auf unbewaffnete
  Waggons; `phonemeTts` dient zugleich als Display-Glyph. Alles erst bei
  Reaktivierung relevant — dann zusammen mit den `missTts`-Clips angehen.
- **`haeusser`-Atom-ID (Tippfehler, korrekt `haeuser`)** — rein intern, Lemma
  stimmt; Umbenennung müsste `sentences.json`/`lessons.json` mitziehen.
- **Finale-Redaktion:** „Deine Nase ist rot wie eine Rose" (f-l06) ist der einzige
  Finale ohne Handlung; „klaut" trägt 3 von 18 Finales. Geschmacksfragen — der
  redaktionelle Eingriff braucht neue kuratierte Aufnahmen und eine bewusste
  Autorenentscheidung.
- **`wespe` nutzt 🐝 (Bienen-Glyph)** — es gibt kein Wespen-Emoji; als Bildwort
  fragwürdig, aber ersatzlos streichen würde L17/L24-Content reißen.
- **`Atom.display` als Sprech-Trick bei `letter-eu` („Oy") / `letter-ae` („Äh")**:
  klassifiziert die Jagd-Ansage in l12 als „Laut" statt „Buchstabe" und legt tote
  Einträge in die Graphem-Tabelle. Eine Validator-Regel `letter_trace.glyph ==
  atom.display` würde die Entscheidung erzwingen, schlägt aber auf dem heutigen
  Pack an — braucht erst eine Content-Entscheidung.
- **Rechnen-Progressions-Staffelung (§8) ist unvalidiert** (Zahlenraum je Lektion,
  Malnehmen ab L6, `difficultyBand`-Bänder) — Validator prüft nur den globalen
  Deckel 30 und die 5×6-Matrix.
- **7 verwaiste Sätze in `sentences.json`** — harmlos; ein Verwaisten-Check würde
  sie sofort rot machen, also erst nach einer Content-Entscheidung (löschen oder
  behalten) einführen.
- **Ohne-TTS-Auflösen im Rechnen zeigt nichts** (stumme 1,4-s-Pause statt neutraler
  Markierung der richtigen Kachel). Betrifft nur Geräte ohne deutsche Stimme UND
  ohne Clip-Abdeckung — nach dem Clip-Availability-Fix praktisch leer.

## Hinweis Audio-Clips

Redaktionelle Textänderungen im Content-Pack (Satz-Versteher-Zeitformen,
Rechnen-Ikonen L17/L25, Xylofon-Runde) verlieren ihre kuratierten Clips und
laufen bis zum nächsten `tools/tts`-Lauf über System-TTS. Die Liste der neu zu
produzierenden Strings steht im Merge-Commit bzw. im Review-Bericht der Session.
