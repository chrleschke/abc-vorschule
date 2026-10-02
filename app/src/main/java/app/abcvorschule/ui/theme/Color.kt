package app.abcvorschule.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Die Papierfläche der App — `paper green` aus dem Babbel GDS
 * (`lessonnine/design-tokens.lib`, Set `semantic/paper green`).
 *
 * Der Trainer-Grund ist keine Fläche mehr, sondern ein radialer Verlauf von
 * [PaperCenter] in der Mitte nach [PaperEdge] an den Rändern; gezeichnet wird er
 * in `TaskShell`. Die Namen mit `Cream`-Präfix sind geblieben, weil sie quer
 * durch die App importiert werden — sie tragen jetzt Papiertöne, keine Cremetöne.
 *
 * Warum ein Verlauf und nicht die flache GDS-Fläche: die Kacheln der
 * Buchstaben-Jagd liegen dann im aufgehellten Mittelfeld statt auf dem vollen
 * `app-background`. Der schwächste Ring gemessen am Ort seiner Kachel steigt
 * dadurch von 3.04:1 (flach) auf 3.83:1 — der Verlauf kauft die Reserve, die die
 * flache Fläche nicht hatte.
 *
 * Die Amplitude begrenzt die **Ecke**, nicht die Mitte: Kacheln streuen bis an
 * die Ränder, also muss der dunkelste Punkt des Verlaufs noch die 3:1 für
 * UI-Komponenten tragen. Für paper green ist dort bei `green 400` Schluss
 * (3.04:1 gegen den schwächsten Ring), eine Stufe tiefer wären es 2.85:1.
 * [PaperEdge] darf also **nicht** weiter abgedunkelt werden.
 *
 * Zwei Alternativen, gemessen und verworfen — falls wir es später drehen wollen:
 *
 * `paper blue` (dieselbe Bauart, eine Spur mehr Luft, weil blue bis `500` tragen
 * darf; wirkt kühler und im Wort-Bauer etwas flacher als green):
 * ```
 * val PaperCenter    = Color(0xFFFAFBFC)  // paper blue 50
 * val PaperEdge      = Color(0xFFC7CDD6)  // paper blue 500 — Ring am Ort 3.89:1
 * val Cream          = Color(0xFFFAFBFC)
 * val CreamPanel     = Color(0xFFF1F3F5)  // paper blue 150
 * val CreamElevated  = Color(0xFFF6F7F8)  // paper blue 100
 * ```
 *
 * Die ursprüngliche „Warmer Tag"-Fassung ohne Verlauf (flaches Cream; dort lag
 * der schwächste Jagd-Ring bei 3.29:1, WarmInk bei 11.1:1, WarmMuted bei 4.45:1):
 * ```
 * val Cream          = Color(0xFFFBF3E4)
 * val CreamPanel     = Color(0xFFF4E8D0)
 * val CreamElevated  = Color(0xFFE9DBBD)
 * ```
 * Beim Zurückdrehen auf eine dieser Fassungen muss auch [TilePalette] in
 * `SymbolHuntTrainer` mitwandern (siehe dort) — die Ringe sind gegen den
 * jeweiligen Grund kalibriert.
 */
val PaperCenter = Color(0xFFF8F9F8)
val PaperEdge = Color(0xFFC5CDC9)

/**
 * Doppelrolle, geerbt aus der Cream-Fassung: heller Grundton **und** die helle
 * Schrift/Glyphe AUF den Akzentflächen (`onPrimary` & Co., der Chevron auf
 * SunCoral, die Ziffer auf der richtigen Menge). Hex-gleich mit [PaperCenter] —
 * der Verlauf startet in genau diesem Ton.
 *
 * Kontraste auf [PaperCenter]: WarmInk 11.58:1, WarmMuted 4.65:1, ClayRed 5.50:1.
 * Auf [PaperEdge], dem dunkelsten Punkt: WarmInk 7.53:1, ClayRed 3.58:1 —
 * und WarmMuted nur noch 3.03:1. Für Glyphen und UI-Bauteile reicht das, für
 * **kleinen Fließtext** nicht mehr: sekundärer Text in WarmMuted gehört damit in
 * die Bildmitte, nicht an den Rand. Betroffen wäre in erster Linie der
 * Eltern-Bereich; die Kind-Screens tragen dort keinen Kleintext.
 */
val Cream = Color(0xFFF8F9F8)

/** paper green 100 — Panels, Kacheln, die Bausteine des Wort-Bauers. */
val CreamPanel = Color(0xFFF1F3F2)

/**
 * paper green 50 — die erhabenen Bauteile, in der Praxis der Lautsprecher-Knopf.
 *
 * Hex-gleich mit [Cream] und damit mit der hellsten Stelle des Verlaufs: der
 * Knopf sitzt oben im Bild, wo der Grund schon abgefallen ist, und liest sich
 * dort als heller Chip. Wandert je ein Bauteil in die Bildmitte, braucht es
 * einen eigenen Ton — die Familie hat darüber nichts mehr, dann muss der Grund
 * eine Stufe tiefer statt das Bauteil eine höher.
 */
val CreamElevated = Color(0xFFF8F9F8)

val WarmInk = Color(0xFF3D3427)
val WarmMuted = Color(0xFF7C6F5A)
val StarGold = Color(0xFFF0A818)

/**
 * Kontur-/Tiefton des Belohnungsgolds: ≈3.25:1 auf Cream, gibt dem Stern-Glyph
 * auf hellen Flächen eine ≥3:1-Grenze (StarGold selbst liegt auf Cream nur bei
 * ~1.85:1 und reicht als reine Füllung nicht für ein UI-Komponenten-Glyph).
 */
val StarGoldDeep = Color(0xFFB07D0A)

/** Cream on LeafGreen ≈ 3.5:1 (large text / icons / UI components). */
val LeafGreen = Color(0xFF43904F)

/** Cream on SkyBlue ≈ 3.8:1 (large text / icons / UI components). */
val SkyBlue = Color(0xFF3F7FB5)

/**
 * Helle Varianten von LeafGreen und SkyBlue — ausschließlich für Akzente AUF
 * dunklen Flächen: die Abzeichen der Pfad-Schilder (↻, „angefangen"), die Ringe
 * der Jagd-Kacheln. Bis Oktober 2026 auch die Kontur der Holzbretter.
 *
 * LeafGreen und SkyBlue sind gegen Cream kalibriert: sie sind die dunkle Hälfte
 * eines hellen Paars. Auf einem dunklen Brett kehrt sich das um und sie fallen
 * auf 2.86:1 bzw. 2.63:1 gegen WoodMid — ein dunkler Akzent auf dunklem Holz.
 * Diese beiden sind dasselbe Grün und Blau, nur auf die andere Seite gedreht:
 *   LeafGreenLight auf WoodMid 5.65:1, auf WoodWarm 3.82:1
 *   SkyBlueLight   auf WoodMid 5.37:1
 *
 * Umgekehrt gilt hier dieselbe Falle: NIE als Fläche unter Cream- oder
 * SoftSand-Text und nie als Akzent auf Cream — gegen Cream liegen sie bei
 * 1.79:1 bzw. 1.88:1. Wer eine helle Fläche einfärben will, nimmt LeafGreen
 * bzw. SkyBlue.
 *
 * Hex-gleich mit SoftMint und SoftSky aus der Nachtpalette: die beiden lagen
 * aus demselben Grund auf demselben Holz und sind hier unter warmem Namen
 * geerbt statt neu erfunden. Wer den Soft*-Block aufräumt, löscht dort nur die
 * alten Namen — diese hier sind der Ersatz.
 */
val LeafGreenLight = Color(0xFF7EC8A3)
val SkyBlueLight = Color(0xFF8FB8D9)

/** Cream on SunCoral ≈ 3.6:1 (large text / icons / UI components). */
val SunCoral = Color(0xFFD25B2D)

/** ClayRed on Cream ≈ 5.2:1 — small-text-safe, since it also serves as error text for adults. */
val ClayRed = Color(0xFFB0402C)

/**
 * Die Abendlandschaft des Pfad-Screens (PRODUCT_PRINCIPLES §5/§10, „Nachtwelten"). Keine
 * UI-Rollen, sondern Landschaftsflächen — deshalb ein eigener Block und keine Aufnahme
 * ins ColorScheme.
 *
 * Bis September 2026 war der Pfad ein heller Tag. Mit den Nachtwelten der Trainer
 * dämmert es auch hier: der Himmel läuft von Nachtblau über Pflaume in ein warmes
 * Orange am Horizont, dort, wo die Hügel beginnen und die Laternen aufsteigen.
 *
 * Tiefe kommt weiter aus Tonwerten statt aus Transparenz: die drei Hügelbänder werden
 * mit Alpha 1f gezeichnet und werden nach vorn dunkler (relative Luminanz 0.046 → 0.028
 * → 0.016, Nachbarstufen 1.25:1 und 1.3:1). Die Landschaft trägt weder Text noch
 * UI-Komponente — Schilder, Trittspuren und Kopfzeile tragen ihre Kontraste selbst.
 * Das Launcher-Icon zeigt weiter den Tag; es ist ein eigenes Motiv mit eigenen Hexwerten
 * (`ic_launcher_background.xml`).
 */
val DuskSkyTop = Color(0xFF1C1A42)
val DuskSkyUpper = Color(0xFF342A60)
val DuskSkyGlow = Color(0xFF7A4670)
val DuskHorizon = Color(0xFFD98A5C)
val DuskHorizonLight = Color(0xFFF0B073)

val HillFar = Color(0xFF2B3F4F)
val HillMid = Color(0xFF1D3035)
val HillNear = Color(0xFF132327)

/** Bäume als dunkle Silhouetten, je eine Stufe dunkler als ihr Hügel. */
val TreeCrown = Color(0xFF0E1B1F)
val TreeTrunk = Color(0xFF0B1518)

/** Die tief stehende Abendsonne hinter den Hügeln. */
val DuskSun = Color(0xFFF7A864)

/**
 * Last hold-over from the retired night palette — kept solely because it still
 * has live callers on the path signs: the dimmed letters of locked blocks
 * ([SignBlockLocked]) and the lock badge. (Until October 2026 it was the label
 * lettering on the wood boards, measured in the notes below.) Every sibling constant from that palette (NightInk, NightPanel,
 * NightElevated, NightDeep, NightHorizon, SoftMint, SoftCoral, SoftSky,
 * SoftGold, MutedText) has been removed as unreferenced.
 */
val SoftSand = Color(0xFFF2E8CF)

/**
 * Dark wood: the lettering on the ABC blocks and the ↻ glyph. Until October 2026
 * also the darkest of three planks (WoodDark / WoodMid #4A3728 / WoodWarm #6B4E34
 * for locked / reachable / mastered) — plank and post are gone, the two lighter
 * tones with them. Against SoftSand 13.06:1.
 */
val WoodDark = Color(0xFF2A2018)

/**
 * WoodDark pushed down by ~4 L* (relative luminance 0.01591 -> 0.01004): the
 * ground of the corner badges on the path signs. Once the shade of the darkest
 * plank's post; the posts of the other two planks (#3F2E22, #5F462E) went with
 * them.
 */
val WoodDarkShade = Color(0xFF201812)

/**
 * ABC-Klötze der Pfad-Schilder (seit Oktober 2026). Fünf Spielzeugtöne; welcher
 * Klotz welchen bekommt, hängt am Laut, nicht am Platz auf dem Schild — `M` ist auf
 * dem ganzen Pfad derselbe Klotz, auch in der Wiederholung. Die Töne tragen keine
 * Bedeutung (kein „Vokale rot"), sie sollen nur unterscheiden.
 *
 * Jeder Ton ist hell genug, dass die Lernschrift in [WoodDark] darauf über 7:1 liegt
 * (gemessen auf der Fläche `face`, dem dunkelsten Punkt des Verlaufs außer der
 * Unterkante): Honig 10.0:1, Himmel 8.7:1, Blatt 9.0:1, Koralle 8.1:1, Flieder 8.3:1.
 * Bewusst nicht die Rollenfarben der Zahlentürme (Honig/Himmelblau dort heißen
 * „erste/zweite Zahl"), sondern weichere Geschwister davon.
 *
 * `hi` ist die Lichtkante oben links, `lo` die Schattenkante unten rechts, `rim` der
 * Umriss — dieselbe Klotz-Grammatik wie in den Zahlentürmen.
 */
data class SignBlockTone(val face: Color, val hi: Color, val lo: Color, val rim: Color)

val SignBlockTones = listOf(
    SignBlockTone(Color(0xFFF4C766), Color(0xFFFFE3A3), Color(0xFFD9A440), Color(0xFFB07D2A)), // Honig
    SignBlockTone(Color(0xFF9CC4E4), Color(0xFFCDE3F4), Color(0xFF6E9FC8), Color(0xFF4F7FA8)), // Himmel
    SignBlockTone(Color(0xFF8ED1AE), Color(0xFFC4EBD5), Color(0xFF5FAE86), Color(0xFF428C67)), // Blatt
    SignBlockTone(Color(0xFFF0A889), Color(0xFFFBD3C1), Color(0xFFD7825F), Color(0xFFB0603F)), // Koralle
    SignBlockTone(Color(0xFFC9B2E6), Color(0xFFE6DAF5), Color(0xFFA287CB), Color(0xFF7F64A8)), // Flieder
)

/** Gesperrte Klötze: dunkles Holz. [SoftSand] bei 0.6 liegt darauf bei 4.97:1. */
val SignBlockLocked = SignBlockTone(Color(0xFF3A2E23), Color(0xFF46382B), Color(0xFF2C221A), Color(0xFF241B14))
