package app.abcvorschule.content

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.InputStream

class ContentRepository(
    private val openAsset: (String) -> InputStream,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
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
        return ContentPack(manifest, atoms, sentences, tasks)
    }

    private fun read(path: String): String =
        openAsset(path).bufferedReader().use { it.readText() }

    companion object {
        fun fromContext(context: Context): ContentRepository =
            ContentRepository { path -> context.assets.open(path) }

        fun fromClasspath(classLoader: ClassLoader = Thread.currentThread().contextClassLoader!!): ContentRepository =
            ContentRepository { path ->
                classLoader.getResourceAsStream(path)
                    ?: error("Missing classpath resource: $path")
            }
    }
}
