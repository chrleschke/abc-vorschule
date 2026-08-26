# --- Warum diese Datei überhaupt existiert -----------------------------------
#
# `isMinifyEnabled = false` (app/build.gradle.kts, Buildtyp `release`): R8 läuft
# derzeit **nicht**, keine dieser Regeln wird also heute angewendet. Die App ist
# klein, offline und ohne Netz-Permission — der Shrinker bringt zu wenig, um das
# Risiko einer stillen Laufzeit-Panne wert zu sein.
#
# Die Regeln stehen trotzdem hier, statt nur eine Notiz „Minification ist aus":
# der gefährliche Moment ist der Tag, an dem jemand `isMinifyEnabled = true`
# setzt. Ohne Keep-Regeln wirft R8 dann die von kotlinx.serialization erzeugten
# `$$serializer`-Klassen und die `Companion.serializer()`-Methoden weg. Der Build
# bleibt grün, die Unit-Tests bleiben grün (die laufen ohne R8), und erst das
# installierte Release stirbt beim Laden des Content-Packs — genau die Falle, die
# ein „ist ja sowieso aus" hinterlassen würde.
#
# Wer Minification einschaltet, prüft danach mindestens: Pack lädt
# (ContentRepository), Fortschritt lädt und schreibt (ProgressRepository),
# Sprach-Clips werden gefunden (ClipIndex).

# --- kotlinx.serialization ---------------------------------------------------
#
# Annotationen sind der Träger von @SerialName und dem `trainer`-Diskriminator
# der TaskSpec-Hierarchie; ohne sie zerfällt die polymorphe Deserialisierung.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Die generierten Serializer der eigenen Modelle — content/**, progress/**,
# speech/** (ClipIndex) und debug/**. `includedescriptorclasses`, damit die in
# den Signaturen referenzierten Modellklassen ihre Namen behalten.
-keep,includedescriptorclasses class app.abcvorschule.**$$serializer { *; }

# Die folgenden drei Blöcke sind die offiziellen Regeln aus der
# kotlinx.serialization-Doku, auf das eigene Package eingegrenzt. Sie hängen an
# der @Serializable-Annotation, nicht an Package-Namen: ein neues serialisierbares
# Modell in einem neuen Package ist damit automatisch mit abgedeckt, statt beim
# nächsten Refactoring durchs Raster zu fallen.

# Companion-Feld serialisierbarer Klassen.
-if @kotlinx.serialization.Serializable class app.abcvorschule.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# `serializer()` auf dem Companion serialisierbarer Klassen.
-if @kotlinx.serialization.Serializable class app.abcvorschule.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# `INSTANCE.serializer()` serialisierbarer `object`s.
-if @kotlinx.serialization.Serializable class app.abcvorschule.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
