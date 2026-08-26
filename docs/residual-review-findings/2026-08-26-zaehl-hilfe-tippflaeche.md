# Zähl-Hilfe: Tippfläche bei großen Mengen

**Datum:** 2026-08-26 · **Status:** erledigt am 2026-08-26
**Betrifft:** `ui/exercise/CountingField.emojiSizeSp`

## Befund (ursprünglich)

In den dichtesten Runden des authorierten Packs („30 − 17", „27 − 18") deckelte
die Feldbreite das Emoji auf **21sp**, bei font_scale 1.3 also rund **27dp**
Tippfläche — deutlich unter den 48dp, die Android als Minimum empfiehlt. 21 der
37 Tipp-Runden im Pack landeten dort.

Ursache waren zwei Dinge, die die Zeilenzahl aufblähten und damit die Größe
drückten: die separate **Weg-Zone** bei Minus (verdoppelte die Zeilen) und die
**zwei getrennten Operanden-Blöcke** bei Plus (bis zu sieben Zeilen).

## Erledigt durch

Beides ist aus anderen Gründen weggefallen — am Gerät war die Weg-Zone
„overwhelming" und stapelte zu viele Zahlen. Seitdem teilen sich beide Operanden
**ein** Fünfer-Feld und der zweite Operand ist nur noch gerahmt statt räumlich
getrennt. Damit sinkt die Zeilenzahl auf höchstens sechs.

Gemessen über alle 930 Runden, die der Validator zulässt: kleinste Emoji-Größe
**24sp**, mit dem Zellpolster eine Tippfläche von **~37dp** statt 27dp. Der
Testfall `everyRoundTheCurriculumCanProduceFitsTheTaskBlockAndStaysReadable`
prüft das jetzt als Zusicherung, nicht als Schätzung.

Die 48dp werden weiterhin nicht erreicht — 30 abzählbare Objekte auf einem
Telefon geben das nicht her. Gemildert bleibt es dadurch, dass ein Fehltipp
folgenlos ist (zweiter Tipp nimmt ihn zurück, keine Strafe, kein Zeitdruck) und
dass der Puls-Hinweis auf das nächste Objekt zeigt.
