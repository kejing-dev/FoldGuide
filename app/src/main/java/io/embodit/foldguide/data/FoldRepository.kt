package io.embodit.foldguide.data

import android.content.Context
import io.embodit.foldguide.domain.model.CurrentFold
import io.embodit.foldguide.domain.model.Hand
import io.embodit.foldguide.domain.model.LanguagePref
import io.embodit.foldguide.domain.model.Progress
import io.embodit.foldguide.domain.model.Settings
import org.json.JSONArray
import org.json.JSONObject

/** What the app remembers between launches (design `dataModel`): progress and settings. Offline, no anchors. */
interface FoldRepository {
    fun loadSettings(defaultDwell: Boolean): Settings

    fun saveSettings(settings: Settings)

    fun loadProgress(): Progress

    fun saveProgress(progress: Progress)
}

/** SharedPreferences-backed store; two small JSON documents, written synchronously (a few hundred bytes). */
class PrefsFoldRepository(context: Context) : FoldRepository {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadSettings(defaultDwell: Boolean): Settings {
        val text = prefs.getString(KEY_SETTINGS, null) ?: return Settings(dwell = defaultDwell)
        return runCatching {
            val o = JSONObject(text)
            Settings(
                language = LanguagePref.entries.firstOrNull { it.name == o.optString("lang") } ?: LanguagePref.SYSTEM,
                hand = Hand.entries.firstOrNull { it.name == o.optString("hand") } ?: Hand.RIGHT,
                dwell = o.optBoolean("dwell", defaultDwell),
                welcomeSeen = o.optBoolean("welcomeSeen", false),
            )
        }.getOrDefault(Settings(dwell = defaultDwell))
    }

    override fun saveSettings(settings: Settings) {
        val o = JSONObject()
        o.put("lang", settings.language.name)
        o.put("hand", settings.hand.name)
        o.put("dwell", settings.dwell)
        o.put("welcomeSeen", settings.welcomeSeen)
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    override fun loadProgress(): Progress {
        val text = prefs.getString(KEY_PROGRESS, null) ?: return Progress()
        return runCatching {
            val o = JSONObject(text)
            val done = LinkedHashSet<String>()
            val arr = o.optJSONArray("done") ?: JSONArray()
            for (i in 0 until arr.length()) done += arr.getString(i)
            val cur = o.optJSONObject("current")
            Progress(done, cur?.let { CurrentFold(it.getString("model"), it.getInt("step")) })
        }.getOrDefault(Progress())
    }

    override fun saveProgress(progress: Progress) {
        val o = JSONObject()
        o.put("done", JSONArray(progress.done.toList()))
        progress.current?.let { c -> o.put("current", JSONObject().put("model", c.modelId).put("step", c.step)) }
        prefs.edit().putString(KEY_PROGRESS, o.toString()).apply()
    }

    private companion object {
        const val FILE = "fold_guide"
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_PROGRESS = "progress_v1"
    }
}
