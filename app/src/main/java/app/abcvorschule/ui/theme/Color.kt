package app.abcvorschule.ui.theme

import androidx.compose.ui.graphics.Color

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
 * every one of them — the path is looked at in a dark room. Measured against
 * SoftSand: WoodDark 13.06:1, WoodMid 9.22:1, WoodWarm 6.23:1.
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
