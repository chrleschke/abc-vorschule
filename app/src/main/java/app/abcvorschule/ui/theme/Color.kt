package app.abcvorschule.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Warmer Tag" palette — the light theme. Semantic roles, not raw hues: callers
 * pick by meaning (StarGold for stars, LeafGreen for correct, SkyBlue for
 * progress, SunCoral for CTAs) rather than reaching for `primary` directly.
 *
 * Contrast against Cream (background): WarmInk ~9.9:1, WarmMuted ~4.6:1.
 *
 * Contrast rule for the accent surfaces below (WCAG-differentiated, not a flat
 * 4.5:1 everywhere): Cream text/glyphs drawn *on* an accent fill only need to
 * clear 3:1 (large text / icons / UI components), since these fills carry short
 * labels, icons, or the progress track — never small body copy. ClayRed is the
 * exception: it also renders as error *text* on the Cream background for
 * adults, so it is tuned to clear the small-text bar of 4.5:1 there instead.
 */
val Cream = Color(0xFFFBF3E4)
val CreamPanel = Color(0xFFF4E8D0)
val CreamElevated = Color(0xFFE9DBBD)
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

/** Cream on SunCoral ≈ 3.6:1 (large text / icons / UI components). */
val SunCoral = Color(0xFFD25B2D)

/** ClayRed on Cream ≈ 5.2:1 — small-text-safe, since it also serves as error text for adults. */
val ClayRed = Color(0xFFB0402C)

val NightInk = Color(0xFF0E1624)
val NightPanel = Color(0xFF172334)
val NightElevated = Color(0xFF223247)
val SoftMint = Color(0xFF7EC8A3)
val SoftCoral = Color(0xFFE08E79)
val SoftSand = Color(0xFFF2E8CF)
val SoftSky = Color(0xFF8FB8D9)

/** Collectible-star yellow. Warm enough to read as "star" on the dark road. */
val SoftGold = Color(0xFFF2C14E)
val MutedText = Color(0xFFB7C2D0)

/** Night sky gradient: deepest at the top, warmer towards the horizon. */
val NightDeep = Color(0xFF080E18)
val NightHorizon = Color(0xFF16283A)

/**
 * Signpost boards. Kept dark enough that SoftSand lettering stays above 4.5:1 on
 * every one of them — the lettering sits on the board, not on the sky behind it,
 * so this contrast pairing holds regardless of the surrounding theme. Measured
 * against SoftSand: WoodDark 13.06:1, WoodMid 9.22:1, WoodWarm 6.23:1.
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
