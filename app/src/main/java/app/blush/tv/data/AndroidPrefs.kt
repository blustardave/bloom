package app.blush.tv.data

import android.content.SharedPreferences

/** The TV app's settings file (Android SharedPreferences "blush"), seen through the shared [Prefs] interface. */
class AndroidPrefs(private val sp: SharedPreferences) : Prefs {
    override fun getString(key: String, default: String?): String? = sp.getString(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = sp.getInt(key, default)
    override fun getFloat(key: String, default: Float): Float = sp.getFloat(key, default)
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = sp.getStringSet(key, default)
    override fun contains(key: String): Boolean = sp.contains(key)
    override fun edit(): Prefs.Editor = Editor(sp.edit())

    private class Editor(private val e: SharedPreferences.Editor) : Prefs.Editor {
        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }
        override fun putFloat(key: String, value: Float) = apply { e.putFloat(key, value) }
        override fun putStringSet(key: String, value: Set<String>?) = apply { e.putStringSet(key, value) }
        override fun remove(key: String) = apply { e.remove(key) }
        override fun apply() = e.apply()
    }
}
