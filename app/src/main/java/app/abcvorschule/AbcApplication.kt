package app.abcvorschule

import android.app.Application
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.debug.TtsDebugRepository
import app.abcvorschule.progress.ProgressRepository

class AbcApplication : Application() {
    lateinit var contentRepository: ContentRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set
    lateinit var ttsDebugRepository: TtsDebugRepository
        private set

    override fun onCreate() {
        super.onCreate()
        contentRepository = ContentRepository.fromContext(this)
        progressRepository = ProgressRepository.fromContext(this)
        ttsDebugRepository = TtsDebugRepository.fromContext(this)
    }
}
