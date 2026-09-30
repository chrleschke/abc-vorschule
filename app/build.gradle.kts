import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.play.publisher)
}

// Upload-Schlüssel für den Play Store. Die Datei `keystore.properties` liegt im
// Projektstamm, ist per .gitignore ausgeschlossen und hat vier Zeilen:
//   storeFile=/absoluter/pfad/silbo-upload.jks
//   storePassword=...
//   keyAlias=upload
//   keyPassword=...
// Fehlt sie, fällt der ganze Buildtyp auf den **Debug-Schlüssel** zurück
// (`signingConfig` unten steht am Buildtyp, gilt also für APK *und* Bundle).
// Gewollt ist das nur für `assembleRelease`: so lässt sich das Release auf einem
// Gerät installieren und durchspielen. Ein so gebautes `.aab` ist dagegen
// wertlos für den Store — die Play Console lehnt Debug-Signaturen ab. Der Build
// bricht deshalb nicht ab (er bleibt prüfbar), aber wer hochladen will, braucht
// `keystore.properties`; ein Blick in die Datei sagt, was drin ist:
//   unzip -p app/build/outputs/bundle/release/app-release.aab META-INF/*.RSA \
//     | keytool -printcert | head -2
// „CN=Android Debug" heißt: nicht hochladen. Erzeugen des Schlüssels und
// Ablauf: README → „Release-Signierung".
//
// Bewusst außerhalb von `android { }`: dort ist `java` die Gradle-Erweiterung,
// und `java.util.Properties` löst sich nicht mehr auf.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasUploadKey = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasUploadKey) FileInputStream(keystorePropertiesFile).use { load(it) }
}

android {
    namespace = "app.abcvorschule"
    compileSdk = 36

    defaultConfig {
        // Die Store-Identität der App: nach dem ersten Upload in die Play Console
        // unveränderlich. Bewusst nur App-Name plus Store-Zusatz, keine Person,
        // kein GitHub-Konto; die Konvention „umgekehrte Domain" ist damit nicht
        // erfüllt (silbo.app gehört jemand anderem), Play verlangt sie aber nicht —
        // dort zählt nur die Eindeutigkeit. Der Kotlin-Namespace bleibt
        // `app.abcvorschule`; die beiden müssen nicht übereinstimmen.
        applicationId = "app.silbo.abcvorschule"
        minSdk = 26
        targetSdk = 36
        // versionCode muss bei jedem Upload in die Play Console steigen (ganzzahlig,
        // monoton); versionName ist der sichtbare Text im Store und in den
        // App-Infos. Play App Signing verwaltet den App-Signaturschlüssel, lokal
        // wird nur mit dem Upload-Schlüssel signiert (siehe signingConfigs unten).
        versionCode = 4
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // Unit tests read the *shipped* content pack from src/main/assets instead of a
    // second copy under src/test/resources. The copy had silently drifted — the whole
    // math progression (Multiplikations-Matrix included) never reached it, so
    // `ContentValidatorTest.shippedPackIsValid` was green on stale content.
    sourceSets {
        getByName("test") {
            resources.srcDir("src/main/assets")
        }
    }

    buildTypes {
        release {
            // R8 an: ohne ihn liegen rund 23 MB DEX im Release, fast alles
            // ungenutzter Compose-, Lifecycle- und Coroutines-Code. Mit Shrinking
            // bleiben davon 2,6 MB, das APK fällt von 16,0 auf 8,9 MB. Was beim
            // Anfassen dieser Zeile zu prüfen ist, steht in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Play Console per Gradle (gradle-play-publisher, spricht die Google Play
// Developer API). Zugang über ein Service-Konto; dessen JSON-Schlüssel liegt nie
// im Repo, sondern standardmäßig unter ~/.config/silbo/play-sa.json — anderer Ort
// per `-PplayCredentials=/pfad/key.json` oder `playCredentials=...` in
// ~/.gradle/gradle.properties. Das Service-Konto muss in der Play Console unter
// „Nutzer und Berechtigungen" für die App freigeschaltet sein.
// Store-Texte, Grafiken und Versionshinweise liegen unter app/src/main/play/
// (`./gradlew bootstrapReleaseListing --app-details --listings --release-notes`
// holt den aktuellen Stand aus der Console; ohne die Flags bricht der Task ab,
// weil er auch In-App-Produkte über die von Google abgeschaltete alte
// inappproducts-API lädt — Silbo verkauft nichts, also weglassen).
// Standard ist der interne Track: `./gradlew publishReleaseBundle` lädt dorthin
// hoch, `promoteReleaseArtifact --track internal --promote-track production`
// hebt ein geprüftes Release weiter. Hochladen braucht keystore.properties, sonst
// ist das Bundle debug-signiert (siehe oben).
play {
    serviceAccountCredentials.set(
        file(
            providers.gradleProperty("playCredentials").getOrElse(
                "${System.getProperty("user.home")}/.config/silbo/play-sa.json",
            ),
        ),
    )
    defaultToAppBundles.set(true)
    track.set("internal")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Nur für das native Overflow-Icon (drei senkrechte Punkte) der Elterntür.
    // Kommt transitiv schon über material3 herein — ausdrücklich deklariert,
    // damit ein Versionssprung, der die Transitive fallen lässt, den Build
    // bricht statt still das Icon zu verlieren.
    implementation(libs.androidx.compose.material.icons.core)
    // Die Material-Formen des Laut-Fressers (Geisterkörper). material3 1.4.0 hat
    // `MaterialShapes` noch nicht (erst 1.5.0-alpha), und graphics-shapes kommt in
    // dieser BOM auch nicht transitiv herein — also ausdrücklich, mit fester Version.
    implementation(libs.androidx.graphics.shapes)
    // Backportet den Android-12-Splash bis API 21 herunter und macht ihn
    // überhaupt erst steuerbar: ohne definierten Splash malt jedes System
    // selbst, was auf einem Motorola edge 60 pro im Dark Mode ein schwarzer
    // Screen war.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    // Espresso ausdrücklich und aktuell, obwohl kein Test es direkt benutzt: es
    // kommt transitiv über androidx.test.ext:junit herein (3.5.0), und sobald es im
    // Klassenpfad liegt, ruft ComposeTestRule.waitForIdle() Espresso.onIdle() auf.
    // Bis 3.6.x sucht dessen InputManagerEventInjectionStrategy
    // android.hardware.input.InputManager.getInstance, die es ab API 36 nicht mehr
    // gibt — jeder Compose-Test stirbt dann beim ersten waitForIdle.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
