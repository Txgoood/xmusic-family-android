package com.example.data.drive

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.text.Normalizer
import java.util.Locale

/** İndeks yalnızca seçili hesap ve köke aittir. Başarısız tarama eski kaydı silmez. */
class DriveArchiveIndex(context: Context) : Closeable {
    private class Database(context: Context) : SQLiteOpenHelper(context, "xmusic_drive_index.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tracks(scope TEXT NOT NULL,id TEXT NOT NULL,name TEXT,path TEXT,modified TEXT,source TEXT,title TEXT,artist TEXT,album TEXT,norm TEXT,nt TEXT,na TEXT,tags TEXT,artParents TEXT NOT NULL DEFAULT '',generation TEXT,PRIMARY KEY(scope,id))")
            db.execSQL("CREATE INDEX exact_track ON tracks(scope,nt,na)")
            db.execSQL("CREATE TABLE scans(scope TEXT PRIMARY KEY,stamp INTEGER,error TEXT)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE tracks ADD COLUMN artParents TEXT NOT NULL DEFAULT ''")
        }
    }
    private val helper = Database(context.applicationContext).apply { setWriteAheadLoggingEnabled(true) }
    override fun close() = helper.close()
    data class Metadata(val title: String, val artist: String = "", val album: String = "")
    companion object {
        fun scope(account: String, folder: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest((account + "\u0000" + folder).toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
        private fun clean(value: String): String = value.replace(Regex("(?i)\\.(mp3|m4a|aac|flac|ogg|opus|wav)$"), "")
            .replace(Regex("(?i)\\b(official music video|official video|official audio|official lyric video|official|music video|lyrics?|hd|hq|4k|1080p|720p)\\b"), " ")
            .replace(Regex("[()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()
        fun infer(name: String, path: String): Metadata {
            val base = clean(name).replace(Regex("^\\d{1,3}\\s*[-._) ]+"), "").trim()
            val split = base.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            val folders = path.split('/').filter { it.isNotBlank() && !Regex("(?i)^(disc|disk|cd)\\s*\\d+$").matches(it) }
            val last = folders.lastOrNull().orEmpty()
            val folderPair = clean(last).replace(Regex("^\\d{4}\\s+"), "").split(Regex("\\s+[-–—]\\s+"), limit = 2)
            val album = if (folderPair.size == 2) folderPair[1] else last
            val artist = if (split.size == 2) split[0] else if (folderPair.size == 2) folderPair[0]
                else if (folders.size >= 2) clean(folders[folders.size - 2]).replace(Regex("(?i)\\s*[-–—]\\s*alb[uü]mleri.*$"), "") else ""
            return Metadata(if (split.size == 2) split[1] else base, artist, album)
        }
        private fun version(value: String): Set<String> = normalize(value).split(' ').filter { it in setOf("live", "canli", "konser", "akustik", "acoustic", "remix", "mix", "cover", "instrumental", "karaoke", "sped", "slowed") }.toSet()
        fun requested(title: String, artist: String): Metadata {
            val parsed = infer(title, "")
            return Metadata(parsed.title, if (parsed.artist.isNotBlank()) parsed.artist else clean(artist))
        }
    }
    fun replace(scope: String, tracks: List<DriveFolderClient.Track>) {
        val db = helper.writableDatabase; val generation = java.util.UUID.randomUUID().toString()
        val old = mutableMapOf<String, Pair<String, String>>()
        db.query("tracks", arrayOf("id", "modified", "tags"), "scope=?", arrayOf(scope), null, null, null).use { c ->
            while (c.moveToNext()) old[c.getString(0)] = c.getString(1).orEmpty() to c.getString(2).orEmpty()
        }
        db.beginTransaction()
        try {
            for (track in tracks) {
                val fallback = infer(track.name, track.path)
                val tags = old[track.id]?.takeIf { it.first == track.modified }?.second.orEmpty()
                val tagged = try { JSONObject(tags) } catch (_: Exception) { JSONObject() }
                val title = tagged.optString("title").ifBlank { fallback.title }
                val artist = tagged.optString("artist").ifBlank { fallback.artist }
                val album = tagged.optString("album").ifBlank { fallback.album }
                val values = ContentValues().apply {
                    put("scope", scope); put("id", track.id); put("name", track.name); put("path", track.path); put("modified", track.modified); put("source", track.sourceId)
                    put("title", title); put("artist", artist); put("album", album); put("norm", normalize("$title $artist $album ${track.path}")); put("nt", normalize(title)); put("na", normalize(artist))
                    put("artParents", track.artParents); put("tags", tags); put("generation", generation)
                }
                check(db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0) { "Arşiv indeksi kaydedilemedi" }
            }
            db.delete("tracks", "scope=? AND generation!=?", arrayOf(scope, generation))
            db.insertWithOnConflict("scans", null, ContentValues().apply { put("scope", scope); put("stamp", System.currentTimeMillis()); put("error", "") }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun markError(scope: String) { helper.writableDatabase.execSQL("INSERT OR IGNORE INTO scans(scope,stamp,error) VALUES(?,0,?)", arrayOf(scope, "")); helper.writableDatabase.execSQL("UPDATE scans SET error=? WHERE scope=?", arrayOf("Tarama tamamlanamadı; önceki arşiv gösteriliyor.", scope)) }
    fun status(scope: String): JSONObject {
        val result = JSONObject().put("count", 0).put("stamp", 0).put("error", "")
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE scope=?", arrayOf(scope)).use { if (it.moveToFirst()) result.put("count", it.getInt(0)) }
        helper.readableDatabase.query("scans", arrayOf("stamp", "error"), "scope=?", arrayOf(scope), null, null, null).use { if (it.moveToFirst()) result.put("stamp", it.getLong(0)).put("error", it.getString(1)) }
        return result
    }
    private fun rows(selection: String, args: Array<String>, limit: Int = 30, order: String = "title COLLATE NOCASE,id"): JSONArray {
        val result = JSONArray()
        helper.readableDatabase.query("tracks", arrayOf("id", "title", "artist", "album", "path", "name", "source", "artParents", "modified"), selection, args, null, null, order, limit.toString()).use { c ->
            while (c.moveToNext()) result.put(JSONObject().put("id", c.getString(0)).put("title", c.getString(1)).put("artist", c.getString(2)).put("album", c.getString(3)).put("path", c.getString(4)).put("name", c.getString(5)).put("sourceId", c.getString(6)).put("artParents", c.getString(7)).put("modified", c.getString(8)))
        }
        return result
    }
    fun search(scope: String, query: String, limit: Int = 30): JSONArray {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }.take(12)
        if (tokens.isEmpty()) return JSONArray()
        val args = mutableListOf(scope)
        val where = "scope=?" + tokens.joinToString("") { token -> args.add("%" + token.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"); " AND norm LIKE ? ESCAPE '!'" }
        val term = android.database.DatabaseUtils.sqlEscapeString(normalize(query))
        val order = "CASE WHEN nt=$term THEN 0 WHEN nt!='' AND na!='' AND instr($term,nt)>0 AND instr($term,na)>0 THEN 1 ELSE 2 END,title COLLATE NOCASE,id"
        return rows(where, args.toTypedArray(), limit.coerceIn(1, 100), order)
    }
    fun exact(scope: String, title: String, artist: String): JSONObject? {
        val wanted = requested(title, artist)
        if (wanted.title.isBlank() || wanted.artist.isBlank()) return null
        val candidates = rows("scope=? AND nt=? AND na=?", arrayOf(scope, normalize(wanted.title), normalize(wanted.artist)), 10)
        val versions = version(title)
        val matches = (0 until candidates.length()).map { candidates.getJSONObject(it) }.filter { version(it.getString("title") + " " + it.getString("name") + " " + it.getString("path")) == versions }
        // Birden çok sürüm varsa otomatik karar vermek yerine arama sonucunu kullanıcı seçer.
        return matches.singleOrNull()
    }
    fun get(scope: String, id: String): JSONObject? = rows("scope=? AND id=?", arrayOf(scope, id), 1).optJSONObject(0)
    fun selections(scope: String): JSONArray = rows("scope=?", arrayOf(scope), 5000)
    fun untaggedMp3(scope: String, limit: Int = 8): List<Triple<String, String, String>> {
        val rows = mutableListOf<Triple<String, String, String>>()
        helper.readableDatabase.query("tracks", arrayOf("id", "name", "modified"), "scope=? AND tags='' AND name LIKE '%.mp3'", arrayOf(scope), null, null, "id", limit.toString()).use { c ->
            while (c.moveToNext()) rows.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
        }
        return rows
    }
    fun saveTags(scope: String, id: String, modified: String, tags: Metadata) {
        val row = get(scope, id) ?: return
        val inferred = infer(row.optString("name"), row.optString("path"))
        val title = tags.title.ifBlank { inferred.title }; val artist = tags.artist.ifBlank { inferred.artist }; val album = tags.album.ifBlank { inferred.album }
        val values = ContentValues().apply {
            put("title", title); put("artist", artist); put("album", album); put("nt", normalize(title)); put("na", normalize(artist))
            put("norm", normalize("$title $artist $album ${row.optString("path")}"))
            put("tags", JSONObject().put("title", tags.title).put("artist", tags.artist).put("album", tags.album).toString())
        }
        helper.writableDatabase.update("tracks", values, "scope=? AND id=? AND modified=?", arrayOf(scope, id, modified))
    }
}


