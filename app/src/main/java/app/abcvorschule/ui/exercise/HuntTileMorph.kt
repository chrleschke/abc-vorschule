package app.abcvorschule.ui.exercise

/**
 * Druck-Morph der Jagd-Kacheln: eine weiche Kugel, die sich unter dem Finger
 * aufbläht, beim Loslassen zusammenfällt und — wenn sie eingesammelt wurde —
 * wegploppt. Material-3-Expressive-Idee „Form folgt Zustand", mit Bordmitteln,
 * gleiche Bauart wie der Squish-Settle des Satz-Architekten
 * (PRODUCT_PRINCIPLES §10).
 *
 * Alles hängt an genau zwei Werten: `inflate` (0 = Ruhe, Anteil über dem
 * Ruhedurchmesser, darf für den Kollaps negativ werden) und `exit` (0 = da,
 * 1 = weg). Vier Phasen:
 *
 * 1. **Anfassen** — Feder auf [PressPuff] (+6 %), kurz und mit leichtem
 *    Nachwippen: die Kachel antwortet, bevor das Kind loslässt.
 * 2. **Halten** — Tween von [PressPuff] auf [MaxInflate] über [HoldMs] mit
 *    verzögernder Easing: die Kugel wächst immer langsamer und steht bei +10 %
 *    endgültig still. Der Deckel ist Absicht — eine Kachel, die weiterwächst,
 *    verdeckt ihre Nachbarn (der Streuabstand ist nur
 *    [SymbolHuntLayout.MinCenterDistanceFraction] der kurzen Feldseite) und
 *    belohnt Draufhalten statt Suchen.
 * 3. **Loslassen** — Kollaps in [CollapseMs] auf −[CollapseUndershoot], dann
 *    eine harte Feder zurück auf 0: das „Plopp".
 * 4. **Weg** — nur für die eingesammelte Kachel: `exit` läuft in [PopAwayMs]
 *    beschleunigend auf 1, Maßstab und Deckkraft gehen gemeinsam auf null.
 *    Bisher verschwand ein Treffer schlagartig aus dem Feld.
 *
 * Die Schattierung im Kreis hängt allein an [pressProgress], läuft also über
 * dieselbe Kurve wie das Wachsen: heller Kern oben links, satter Rand, ein
 * Glanzpunkt und ein Innenschatten am Rand, der mit dem Druck zunimmt. Damit
 * liest die Bewegung als *weiche Kugel unter dem Finger* und nicht als Zoom —
 * die gleiche Pflicht wie das gegenläufige `scaleY` beim Peg-Morph.
 *
 * Die Deckkraft der Grundwäsche bleibt dabei im Mittel bei den bisherigen 0,22
 * ([CoreAlphaRest]…[RimAlphaRest]), und der schlimmste Fall (Rand bei vollem
 * Druck, 0,40) hält den Glyphen-Kontrast über 6:1 gegen WarmInk. Der Rand der
 * Kachel wird vom Morph *nicht* angefasst: seine 3dp in Volldeckkraft sind das,
 * was die 3:1-Untergrenze für UI-Bauteile trägt (siehe TilePalette).
 */
object HuntTileMorph {
    /** Anfass-Puff: sofortige, kleine Antwort auf den Finger. */
    const val PressPuff = 0.06f

    /** Harte Obergrenze des Wachsens — +10 %, nicht mehr. */
    const val MaxInflate = 0.10f

    /** Wie weit der Kollaps unter den Ruhedurchmesser durchschießt. */
    const val CollapseUndershoot = 0.06f

    /** Dauer der Halte-Phase [PressPuff] → [MaxInflate]. */
    const val HoldMs = 1500

    /** Dauer des Kollaps 0 → −[CollapseUndershoot] beim Loslassen. */
    const val CollapseMs = 90

    /** Dauer des Wegploppens der eingesammelten Kachel. */
    const val PopAwayMs = 170

    /** Mittelpunkt von Kern-Aufhellung und Glanzpunkt, als Anteil der Kachel. */
    const val GlossCenterX = 0.35f
    const val GlossCenterY = 0.30f

    /** Radius der Grundwäsche als Vielfaches des Kachelradius (weicher Verlauf). */
    const val WashRadiusFactor = 1.5f

    /** Ab hier setzt der Innenschatten ein; innen bleibt die Kugel frei. */
    const val ShadeInnerStop = 0.55f

    private const val CoreAlphaRest = 0.14f
    private const val CoreAlphaPressed = 0.11f
    private const val RimAlphaRest = 0.28f
    private const val RimAlphaPressed = 0.40f
    private const val ShadeAlphaRest = 0.10f
    private const val ShadeAlphaPressed = 0.30f
    private const val GlossAlphaRest = 0.46f
    private const val GlossAlphaPressed = 0.30f
    private const val GlossRadiusRest = 0.56f
    private const val GlossRadiusPressed = 0.46f

    /**
     * Druckfortschritt 0…1 aus dem Aufblähen — die Schattierung folgt damit
     * automatisch der verzögernden Halte-Kurve. Der Unterschwinger des Kollaps
     * (negatives `inflate`) liest als 0, also als Ruhe.
     */
    fun pressProgress(inflate: Float): Float = (inflate / MaxInflate).coerceIn(0f, 1f)

    /** Maßstab der Kachel; nie negativ, sonst spiegelt die Kachel beim Wegploppen. */
    fun scale(inflate: Float, exit: Float): Float =
        ((1f + inflate) * (1f - exit.coerceIn(0f, 1f))).coerceAtLeast(0f)

    /** Deckkraft der Kachel — nur das Wegploppen blendet aus. */
    fun alpha(exit: Float): Float = 1f - exit.coerceIn(0f, 1f)

    /** Heller Kern der Grundwäsche (oben links). */
    fun coreAlpha(pressProgress: Float): Float = mix(CoreAlphaRest, CoreAlphaPressed, pressProgress)

    /** Satter Rand der Grundwäsche. */
    fun rimAlpha(pressProgress: Float): Float = mix(RimAlphaRest, RimAlphaPressed, pressProgress)

    /** Innenschatten am Rand — nimmt mit dem Druck zu (Tiefe statt Zoom). */
    fun shadeAlpha(pressProgress: Float): Float = mix(ShadeAlphaRest, ShadeAlphaPressed, pressProgress)

    /** Glanzpunkt: wird beim Drücken schwächer … */
    fun glossAlpha(pressProgress: Float): Float = mix(GlossAlphaRest, GlossAlphaPressed, pressProgress)

    /** … und zieht sich zusammen, wie ein Licht auf gespannter Haut. */
    fun glossRadiusFactor(pressProgress: Float): Float =
        mix(GlossRadiusRest, GlossRadiusPressed, pressProgress)

    private fun mix(rest: Float, pressed: Float, t: Float): Float =
        rest + (pressed - rest) * t.coerceIn(0f, 1f)
}
