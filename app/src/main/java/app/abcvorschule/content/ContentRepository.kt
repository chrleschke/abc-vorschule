package app.abcvorschule.content

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.InputStream

class ContentRepository(
    private val openAsset: (String) -> InputStream,
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Trainer name is the discriminator so tasks.json reads like the curriculum.
        classDiscriminator = "trainer"
    }

    @Volatile
    private var cached: ContentPack? = null

    fun load(): ContentPack {
        cached?.let { return it }
        val pack = ContentValidator.requireValid(parsePack())
        cached = pack
        return pack
    }

    fun clearCache() {
        cached = null
    }

    private fun parsePack(): ContentPack {
        val manifest = json.decodeFromString<PackManifest>(read("content/pack.manifest.json"))
        val atoms = json.decodeFromString<AtomsFile>(read("content/atoms.json")).atoms
            .associateBy { it.id }
        val sentences = json.decodeFromString<SentencesFile>(read("content/sentences.json")).sentences
            .associateBy { it.id }
        val tasks = json.decodeFromString<TasksFile>(read("content/tasks.json")).tasks
            .associateBy { it.id }
        val finales = json.decodeFromString<FinalesFile>(read("content/finales.json")).finales
            .associateBy { it.id }
        val lessons = json.decodeFromString<LessonsFile>(read("content/lessons.json")).lessons
            .sortedBy { it.index }
        return ContentPack(manifest, atoms, sentences, tasks, finales, lessons)
    }

    private fun read(path: String): String =
        openAsset(path).bufferedReader().use { it.readText() }

    companion object {
        fun fromContext(context: Context): ContentRepository =
            ContentRepository { path -> context.assets.open(path) }

        fun fromClasspath(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader!!,
        ): ContentRepository = ContentRepository { path ->
            classLoader.getResourceAsStream(path)
                ?: error("Missing classpath resource: $path")
        }
    }
}
