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
 * dunklen Flächen, in der Praxis die Ringe der Holzschilder auf dem Pfad.
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

/**
 * Ladebalken der Jagd-Batterie (`HuntBatteryDesign`, PRODUCT_PRINCIPLES §10).
 * Ein Verlauf statt einer Farbe: Balken 1 trägt den tiefsten, der letzte den
 * hellsten Ton, Zwischenbalken werden interpoliert — die Batterie wird beim
 * Laden sichtbar heller, nicht nur voller.
 *
 * Bewusst durchgehend Grün und ohne den früheren Gold-/Ockerton: Gold gehört in
 * dieser App zur Sternbelohnung, und der volle Zustand ist hier ein Ladezustand
 * („aufgeladen"), kein Preis. Grün ist außerdem app-weit die Farbe für richtig
 * (LeafGreen), und die Batterie ist genau das: die Summe richtiger Treffer.
 *
 * Gemessen gegen [WarmInk], die Innenfläche der Batterie, in der die Balken
 * liegen (nicht gegen Cream — auf die Seite sehen sie nie): ChargeLow 3.21:1,
 * ChargeMid 5.83:1, ChargeHigh 9.24:1, alle über der 3:1-Schwelle für
 * UI-Komponenten. Die Balken werden im Verlauf nur nach oben aufgehellt, nie
 * abgedunkelt, damit diese Werte die untere Grenze bleiben. Nachbarschritte
 * liegen bei 1.25:1 bis 1.36:1 (fünf Balken) bzw. 1.59:1 bis 1.81:1 (drei) —
 * jede Stufe sichtbar, erster gegen letzten Balken 2.88:1.
 *
 * ChargeHigh ist zugleich der Vollzustand: dann tragen alle Balken diesen einen
 * Ton, und der Blitz darauf liegt bei 9.24:1 (WarmInk).
 */
val ChargeLow = Color(0xFF389451)
val ChargeMid = Color(0xFF5BC96D)
val ChargeHigh = Color(0xFFA6F2A8)

/** Cream on SunCoral ≈ 3.6:1 (large text / icons / UI components). */
val SunCoral = Color(0xFFD25B2D)

/** ClayRed on Cream ≈ 5.2:1 — small-text-safe, since it also serves as error text for adults. */
val ClayRed = Color(0xFFB0402C)

/**
 * Die Taglandschaft des Pfad-Screens. Keine UI-Rollen, sondern Landschafts-
 * flächen — deshalb ein eigener Block und keine Aufnahme ins ColorScheme.
 *
 * Tiefe kommt hier aus Tonwerten statt aus Transparenz: die drei Hügelbänder
 * werden mit Alpha 1f gezeichnet und trennen sich über ihre relative Luminanz
 * (HillFar 0.566, HillMid 0.445, HillNear 0.322 — Nachbarkontraste 1.24:1 und
 * 1.33:1, eine sichtbare Stufe ohne harte Kante). Über dem dunklen Nachthimmel
 * war Alpha nötig, um Bänder auseinanderzuhalten; auf hellem Grund würde es sie
 * nur ausbleichen.
 *
 * Die Landschaft trägt weder Text noch UI-Komponente, ist also dekorativ im
 * Sinne von WCAG 1.4.11. Der eine Kontrast, der die Silhouette trägt, ist die
 * Baumkrone gegen den Himmel, in den sie ragt: TreeCrown auf DayHorizon =
 * 3.49:1. Der Stammstumpf auf HillNear liegt bei 2.69:1 — als reine Deko
 * ausreichend und deutlich über der 1.23:1-Silhouette der Nachtfassung.
 */
val DaySkyTop = Color(0xFF9CCAEE)
val DaySkyMid = Color(0xFFBFDDF2)

/** Warmes Licht am Horizont — dort, wo die Hügel den Himmel treffen. */
val DayHorizon = Color(0xFFF7E7C3)

val HillFar = Color(0xFFB5CF9F)
val HillMid = Color(0xFF93BE7E)
val HillNear = Color(0xFF6FA85E)

val TreeCrown = Color(0xFF4E8747)

/** Bewusst der WoodWarm-Ton: Stamm und warmes Schildbrett sind dasselbe Holz. */
val TreeTrunk = Color(0xFF6B4E34)

/** Wolken — fast-weißes Creme und bewusst die hellste Fläche der App. */
val CloudWhite = Color(0xFFFDF9EF)

val SunGlow = Color(0xFFF7CE73)

/**
 * Last hold-over from the retired night palette — kept solely because it still
 * has a live caller: the label lettering on PathSignNode's wood boards (see the
 * Signpost boards contrast notes below, measured against WoodDark/WoodMid/
 * WoodWarm). Every sibling constant from that palette (NightInk, NightPanel,
 * NightElevated, NightDeep, NightHorizon, SoftMint, SoftCoral, SoftSky,
 * SoftGold, MutedText) has been removed as unreferenced.
 */
val SoftSand = Color(0xFFF2E8CF)

/**
 * Signpost boards. Kept dark enough that SoftSand lettering stays above 4.5:1 on
 * every one of them — the lettering sits on the board, not on the sky behind it,
 * so this contrast pairing holds regardless of the surrounding theme. Measured
 * against SoftSand: WoodDark 13.06:1, WoodMid 9.22:1, WoodWarm 6.23:1.
 *
 * The boards now stand against a bright day sky rather than a night one. That
 * changes nothing about the numbers above — but it does mean the board is the
 * dark shape on a light field instead of the other way round, so a sign's
 * outline is separated from its surroundings more strongly than before, not
 * less.
 */
val WoodDark = Color(0xFF2A2018)
val WoodMid = Color(0xFF4A3728)
val WoodWarm = Color(0xFF6B4E34)

/**
 * The shaded wood of each board: the post the board is nailed to, which is
 * behind it, and the nail heads, which are sunk into it. Both must stay darker
 * than the board they belong to or the depth inverts — a post that is lighter
 * than its board reads as standing in front of it, and a nail head lighter than
 * the wood around it reads as a bead rather than a dent.
 *
 * One shade per board, not one global post tone: a single tone can only be
 * darker than the darkest board, and WoodDark is the board for Locked *and*
 * Planned, so most of the 26 signs are the darkest ones while the child is at
 * the start of the path.
 *
 * Each shade is its own board pushed down by ~4 L*, which is a visible step
 * everywhere but never a colour change. Relative luminance, board -> shade:
 *   WoodDark  0.01591 -> 0.01004 (L* 13.18 -> 9.02)
 *   WoodMid   0.04341 -> 0.03126 (L* 24.77 -> 20.54)
 *   WoodWarm  0.08823 -> 0.07010 (L* 35.64 -> 31.83)
 * Fixed hex rather than a factor applied to the board at draw time: an equal
 * perceptual step is not an equal sRGB factor (it takes 0.766 / 0.846 / 0.891
 * here), so a computed shade would either flatten the dark board or overshoot
 * the warm one, and the luminances above could not be stated and checked in the
 * one file that holds every colour in the app.
 */
val WoodDarkShade = Color(0xFF201812)
val WoodMidShade = Color(0xFF3F2E22)
val WoodWarmShade = Color(0xFF5F462E)
