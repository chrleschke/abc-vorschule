# Zähl-Hilfe: Tippfläche bei großen Mengen

**Datum:** 2026-08-26 · **Status:** offen, am Gerät zu entscheiden
**Betrifft:** `ui/exercise/CountingField.emojiSizeSp`

## Befund

In den dichtesten Runden des authorierten Packs („30 − 17", „27 − 18") deckelt
die Feldbreite das Emoji auf **21sp**, bei font_scale 1.3 also rund **27dp**
Tippfläche — deutlich unter den 48dp, die Android als Minimum empfiehlt. 21 der
37 Tipp-Runden im Pack landen dort.

Gemildert dadurch, dass ein Fehltipp folgenlos ist: ein zweiter Tipp nimmt ihn
zurück, es gibt keine Strafe und keinen Zeitdruck. Die
Multiplikationsmatrix versendet mit `MultiplicationMatrix.emojiSizeSp` bereits
26sp (34dp) und wurde so akzeptiert.

## Warum nicht direkt behoben

Gemessen über die 28 echten Plus/Minus-Tipp-Runden ergibt eine Zeilenbreite von
**8** die beste Untergrenze (24sp ≈ 31dp) gegenüber **10** (21sp ≈ 27dp) — ein
Gewinn von 4dp. Der Preis wäre die 5+5-Struktur: Achterzeilen zerlegen sich in
5+3 und verlieren die Zehnerbündelung, die PRODUCT_PRINCIPLES §8 für den
Zahlenraum 20/30 ausdrücklich als Lernstruktur führt. 4dp sind das nicht wert,
solange nicht am Gerät belegt ist, dass 27dp tatsächlich zu klein sind.

Die Höhe bindet nicht (237dp von 300dp im schlimmsten Fall) — der Engpass ist
allein die Breite.

## Zu prüfen

Am Testgerät (font_scale 1.3) eine Runde mit Ergebnis über 20 bis zur Zähl-Hilfe
spielen und ein Kind zählen lassen. Trifft es die Objekte zuverlässig, ist der
Befund erledigt. Sonst ist Zeilenbreite 8 die belegte Alternative.
