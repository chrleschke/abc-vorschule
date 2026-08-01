# Visuelles Konzept-Redesign: Warmer Tag, Farbrollen, Haptik, Erfolgsmomente

**Status:** approved (Nutzerauftrag in Session, autonome Ausführung)
**Datum:** 2026-08-01

## Auftrag

Das visuelle Konzept der App überarbeiten: mehr visuell und kinderfreundlich.

1. Farbkonzept fixen — aktuell türkiser Erfolgs-Stern über türkiser Progress-Bar (beides `SoftMint`).
2. Vom „dark mode only" abweichen, aber augenfreundlich bleiben.
3. Mehr Haptik — u. a. spürbares Feedback bei jedem eingesammelten Trace-Stern.
4. Mehr Erfolgsmomente mit Feedback.

Dieses Redesign revidiert bewusst die bisherigen Prinzipien „Dunkles, ruhiges UI"
(PRODUCT_PRINCIPLES §2) und „dark-only bleibt Prinzip" (§5) — per ausdrücklicher
Nutzerentscheidung. Die Doku wird mitgezogen.

## Ist-Zustand (Kurzdiagnose)

- `primary` = `SoftMint` trägt vier Bedeutungen zugleich: Erfolg/richtig, Sterne/Punkte,
  CTA-Fläche, Rechen-Prompt. Der Erfolgs-Stern (`SuccessEffects.kt`) und die
  Progress-Bar-Füllung (`AbcButtons.kt:181`, hartkodiertes Mint-Hex) sind identisch türkis.
- Zwei konkurrierende Stern-Farben: Chrome-Punktestern mint, Trace-/Schild-Sterne `SoftGold`.
- Dark-only durchgängig: `darkColorScheme`, NightInk-Hintergründe, Nachtlandschaft,
  Dutzende auf dunklen Grund kalibrierte Alpha-Overlays (vollständiges Inventar siehe
  Implementierungsplan).
- Haptik: nur LongPress-Nudges (off-road, Fehltipp) und ein kaum spürbares
  `TextHandleMove` pro Trace-Stern. Kein Erfolgs-Haptik-Vokabular.
- Erfolgsmomente: ein mintfarbener Stern-Pop + Chime pro richtiger Antwort; End-Screen
  statisch mit gedämpftem Hintergrundstern.

## Design

### 1. Farbsystem: semantische Rollen

Neue Palette „Warmer Tag" in `ui/theme/Color.kt`. Grundsatz: **kein reines Weiß, kein
reines Schwarz** — Creme-Grund und warme Tinte halten die App augenfreundlich.

Basistöne:

| Rolle | Name | Richtwert | Verwendung |
| --- | --- | --- | --- |
| background | `Cream` | `#FBF3E4` | App-Grund |
| surface | `CreamPanel` | `#F4E8D0` | Panels, Sheets |
| surfaceVariant | `CreamElevated` | `#EADCBE` | Chrome-Buttons, leere Slots |
| onBackground/onSurface | `WarmInk` | `#3D3427` | Primärtext (Kontrast ≥ 7:1 auf Cream) |
| onSurfaceVariant | `WarmMuted` | `#7C6F5A` | Sekundärtext (≥ 4.5:1 auf Cream) |

Semantische Akzentrollen — **eine Bedeutung pro Farbe**:

| Rolle | Name | Richtwert | Verwendung |
| --- | --- | --- | --- |
| Belohnung/Sterne/Punkte | `StarGold` | `#F0A818` | Punktestern im Chrome, Erfolgs-Stern, Trace-Sterne, Schild-Sterne, End-Screen-Stern |
| Erfolg/richtig | `LeafGreen` | `#4E9B5E` | Gefüllte Slots, korrekte Kacheln, „richtig"-Bestätigung |
| Fortschritt/aktiv | `SkyBlue` | `#4E8FC7` | Progress-Bar-Füllung, InProgress-Ring, neutrale Aktiv-Zustände |
| CTA | `SunCoral` | `#E8794A` | Weiter-Button, Absenden, Lokomotive |
| Fehlertext (Erwachsene) | `ClayRed` | `#C4553F` | Fehlermeldungen im Chrome (Kind-Feedback bleibt Audio) |

M3-Mapping: `lightColorScheme` mit primary=`LeafGreen`, secondary=`SkyBlue`,
tertiary=`SunCoral`, error=`ClayRed`, plus explizite outline/scrim-Werte. Sterne
und Progress-Bar greifen **nie** auf `primary` zu, sondern auf die benannten
Konstanten — damit kann keine Rollen-Überladung zurückkehren. Exakte Hex-Werte
dürfen bei der Umsetzung feinjustiert werden, die Kontrast-Anforderungen sind
bindend: Fließtext ≥ 4.5:1; großer Text, Icons und UI-Komponenten ≥ 3:1
(WCAG 1.4.3/1.4.11). Die Akzentflächen werden dafür so weit nachgedunkelt,
dass Cream-Glyphen darauf ≥ 3.5:1 erreichen.

Die Trainer-Paletten (Hunt/Detektiv-Segmente, Waggonfarben) werden auf die neuen
Akzente umgestellt und dürfen dabei fröhlicher/gesättigter werden als bisher —
kinderfreundlich heißt: klare, warme Farben, keine Neonwerte.

### 2. Helles Theme statt dark-only

- Ein einziges helles Theme, kein Switcher, kein System-Following (YAGNI; Zielgruppe
  4–7, die App soll überall gleich aussehen).
- `themes.xml`: windowBackground/statusBar/navigationBar auf Cream,
  `forceDarkAllowed=false`, `windowLightStatusBar=true`;
  `MainActivity.enableEdgeToEdge` mit expliziten Light-Styles.
- Launcher-Icon: Hintergrund Cream, Vordergrund auf neue Akzente (Koralle/Gold).
- Alle auf dunklen Grund kalibrierten Alpha-Overlays werden neu kalibriert
  (helle Overlays → dunkle/warme Pendants; Mint-Washes → Grün/Blau-Washes mit
  angepassten Alphas). Dampf der Lok wird warm-grau statt weiß.
- Trace-Straße: Band und Innenfüllung neu auf hellem Grund (Straße als warmer
  dunklerer Ton, Füllung läuft nach `LeafGreen`); Sterne bleiben Gold.

### 3. Pfad: freundliche Taglandschaft

Die Nachtlandschaft wird eine Tag-Szene mit derselben Komposition (Gradient, drei
Hügelbänder mit Parallaxe, Bäume, Trittspuren, Holzschilder):

- Himmel: Verlauf von hellem Blau oben zu warmem Licht am Horizont.
- Statt Nachtsterne: weiche Wolken und eine Sonne (dezent, kein Glare).
- Hügel: drei Grünbänder (hinten hell/blaustichig, vorn satter — Tiefenstaffelung
  wie bisher über Alpha/Tonwert, neu gerechnet).
- Bäume: satte grüne Kronen mit dunklem Stamm statt Silhouetten.
- Trittspuren: begangener Weg warm-golden, kommender Weg gedämpft warm-grau.
- Holzschilder bleiben Holz (WoodDark/Mid/Warm): dunkles Holz auf hellem Grund
  trägt gut; die dokumentierten Schrift-auf-Brett-Kontraste bleiben gültig, weil
  die Schrift auf dem Brett steht, nicht auf dem Himmel. Silhouetten-/Dim-Alphas
  der gesperrten Schilder werden auf den hellen Grund nachjustiert.

### 4. Haptik-Vokabular `AbcHaptics`

Neues Modul `ui/rewards/AbcHaptics.kt`: dünner Wrapper über `Vibrator` /
`VibrationEffect.createPredefined` mit Fallback auf Compose-`HapticFeedback`
(und No-Op, wenn kein Vibrator). Vier Verben:

| Verb | Effekt | Einsatz |
| --- | --- | --- |
| `tick()` | EFFECT_CLICK (deutlich, kurz) | jeder Trace-Stern, Hunt-Treffer, Detektiv-Treffer, Drag-Einrasten |
| `success()` | EFFECT_HEAVY_CLICK bzw. Doppel-Klick-Muster | richtige Antwort / SuccessBurst, Silben-Schnapp |
| `celebrate()` | kurzes 3-Puls-Muster (~400 ms) | Lektionsabschluss (End-Screen), Batterie voll |
| `nudge()` | weiches EFFECT_TICK/LongPress | off-road, Fehltipp (bestehende Stellen migrieren) |

Verdrahtung: LetterTrace (Stern → `tick`, statt TextHandleMove), SymbolHunt,
SymbolInWord, Drag-Commits (WordBuild, SentenceOrder, Silben-Snap), Rechnen-richtig,
SuccessBurst-Trigger, RewardSummaryScreen-Erscheinen, Batterie-Feier.
Ton bleibt wie gehabt (Star-Blips, Chime) — Haptik ergänzt, ersetzt nicht.

### 5. Mehr Erfolgsmomente

1. **SuccessBurst neu:** Stern in `StarGold` statt primary, dazu ein radialer
   Burst aus 6–8 Mini-Sternen/Funken (Canvas-Partikel, ~600 ms, einmalig),
   plus `success()`-Haptik. Timing/Ablauf (Audio abwarten, onFinished) unverändert.
2. **Trace-Stern-Funke:** beim Einsammeln ein kurzer Glitzer-Puls am Sammelpunkt
   (expandierender, ausblendender Ring/Funken), zusätzlich zum bestehenden Blip.
3. **Progress-Bar lebt:** Füllung animiert (`animateFloatAsState`), beim
   Trainer-Abschluss ein kurzer Gold-Puls an der Füllkante — der Fortschritt wird
   als Ereignis spürbar, nicht nur als Zustand.
4. **End-Screen-Konfetti:** einmaliger Konfetti-Regen (Canvas, ~2 s, Palette aus
   den neuen Akzentfarben) hinter Bildreihe/Satz, plus `celebrate()`-Haptik.
   Gilt für beide End-Screen-Varianten; Bildreihe/Satz/Speaker unverändert.

Bewusst nicht in diesem Umbau (YAGNI): Theme-Switcher, Tageszeit-Logik,
Pfad-Schild-Feier beim Meistern, Sammelalbum, neue Sounds.

### 6. Dokumentation

- `PRODUCT_PRINCIPLES.md`: §2 „Dunkles, ruhiges UI" → helles, warmes, ruhiges UI;
  §5 Nachtlandschaft → Taglandschaft; §10 Design-System um Farbrollen-Tabelle und
  Haptik-Vokabular ergänzen; Review-Tabelle („Ist das UI ruhig und kindgerecht?")
  bleibt sinngemäß.
- `AGENTS.md`: „dark-only" im Technik-Kurzüberblick ersetzen; Kurzfassung der
  Kind-UI-Regeln anpassen.
- Kontrast-Doku in `Color.kt` neu schreiben (Wood-Begründung bleibt, Umfeld neu).

## Fehlerbehandlung / Risiken

- Geräte ohne Vibrator: `AbcHaptics` degradiert zu No-Op — kein Crash, Ton bleibt.
- Ältere APIs ohne `VibrationEffect.createPredefined` (< API 29): Fallback auf
  Compose-HapticFeedback (LongPress/TextHandleMove).
- Kontrast-Regressionen: jede neu kalibrierte Stelle wird gegen die neuen
  Grundflächen geprüft (mind. rechnerisch, dokumentiert an der Farbe); Font-Scale
  1.3 des Testgeräts beachten (Layouts nicht anfassen, nur Farben/Effekte).
- `forceDarkAllowed` muss auf `false`, sonst invertiert Android das helle Theme.

## Tests

- Bestehende Unit-Tests bleiben grün (`./gradlew :app:testDebugUnitTest`).
- Neue Unit-Tests: Konfetti-/Partikel-Geometrie (deterministische Seeds),
  Progress-Fraction-Verhalten unverändert, AbcHaptics-Verb-Mapping (API-Zweige,
  soweit ohne Instrumentation testbar).
- Build: `./gradlew :app:assembleDebug`.
