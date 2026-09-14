# --- Warum diese Datei (fast) leer ist ---------------------------------------
#
# `isMinifyEnabled = true` (app/build.gradle.kts, Buildtyp `release`): R8 läuft,
# und er braucht für diese App keine einzige eigene Keep-Regel.
#
# Hier standen früher sechs Regeln für kotlinx.serialization — die generierten
# `$$serializer`, die `Companion.serializer()`-Methoden, das `INSTANCE`-Feld
# serialisierbarer `object`s und `-keepattributes *Annotation*`. Alle sechs waren
# redundant: `kotlinx-serialization-core` bringt sie seit 1.6 selbst mit, in
# `META-INF/com.android.tools/r8/` der eigenen JAR, und zwar auf `class **` statt
# auf ein Package eingegrenzt — die Bibliotheksregel ist also die weitere von
# beiden. Die Attribut-Regel deckt schon `proguard-android-optimize.txt` ab
# (`AnnotationDefault, InnerClasses, RuntimeVisibleAnnotations, …`).
#
# Die eigene `-keep,includedescriptorclasses ... $$serializer { *; }` war dabei
# nicht nur überflüssig, sondern die wirkungsstärkste Keep-Regel im gesamten
# Build: 76 Klassen, 108 Felder und 288 Methoden hat sie festgehalten, mehr als
# jede Regel aller Bibliotheken zusammen. `includedescriptorclasses` zieht dabei
# jede Modellklasse mit hinein, die in einer Serializer-Signatur vorkommt.
# Nachzurechnen mit `./gradlew :app:analyzeReleaseR8Config`.
#
# --- Der gefährliche Moment --------------------------------------------------
#
# Serialisierung bricht unter R8 still: Build grün, Unit-Tests grün (die laufen
# ohne R8), und erst das installierte Release stirbt oder zeigt eine leere App.
# Wer hier etwas ändert — oder eine Bibliothek hebt, die ihre eigenen Regeln
# mitbringt —, prüft deshalb im *Release*-Build auf einem Gerät, nicht im Debug:
#
#   1. Pfad-Screen zeigt die Lektionsschilder mit Emojis  (ContentRepository,
#      inkl. polymorpher TaskSpec-Hierarchie über den `trainer`-Diskriminator)
#   2. Lektion öffnen, App per `am force-stop` killen, neu starten: sie muss in
#      die Lektion zurückkehren                            (ProgressRepository)
#   3. Sprachausgabe kommt aus den Clips, nicht aus Android-TTS — im logcat an
#      `MediaPlayer` unter der eigenen PID erkennbar        (ClipIndex)
#
# Erst wenn eine dieser drei Prüfungen fehlschlägt, gehört hier wieder eine
# Regel hin — und dann die engste, die das Problem behebt, nicht `**`.
