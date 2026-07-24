package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PersistenceManager {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val adapter = moshi.adapter(BrowserDatabase::class.java)
    private const val DB_FILE_NAME = "malachite_database.json"

    fun save(context: Context) {
        try {
            val db = BrowserDatabase(
                domainsList = BrowserState.domainsList,
                globalSettings = BrowserState.globalSettings,
                history = BrowserState.history.toList(),
                swipeRightNext = BrowserState.swipeRightNext,
                swipeLeftNext = BrowserState.swipeLeftNext,
                doubleTapNext = BrowserState.doubleTapNext,
                groupSettings = BrowserState.groupSettings
            )
            val json = adapter.indent("  ").toJson(db)
            val file = File(context.filesDir, DB_FILE_NAME)
            file.writeText(json)
            Log.d("Persistence", "Database saved to internal storage")
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to save database", e)
        }
    }

    fun load(context: Context) {
        try {
            val file = File(context.filesDir, DB_FILE_NAME)
            if (file.exists()) {
                val json = file.readText()
                val db = adapter.fromJson(json)
                if (db != null) {
                    BrowserState.domainsList = db.domainsList
                    BrowserState.globalSettings = db.globalSettings
                    BrowserState.history.clear()
                    BrowserState.history.addAll(db.history)
                    BrowserState.swipeRightNext = db.swipeRightNext
                    BrowserState.swipeLeftNext = db.swipeLeftNext
                    BrowserState.doubleTapNext = db.doubleTapNext
                    BrowserState.groupSettings = db.groupSettings
                    Log.d("Persistence", "Database loaded from internal storage")
                }
            }
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to load database", e)
        }
    }

    fun exportToJson(context: Context, uri: Uri) {
        try {
            val db = BrowserDatabase(
                domainsList = BrowserState.domainsList,
                globalSettings = BrowserState.globalSettings,
                history = BrowserState.history.toList(),
                swipeRightNext = BrowserState.swipeRightNext,
                swipeLeftNext = BrowserState.swipeLeftNext,
                doubleTapNext = BrowserState.doubleTapNext,
                groupSettings = BrowserState.groupSettings
            )
            val json = adapter.indent("  ").toJson(db)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            Log.d("Persistence", "Database exported to: $uri")
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to export database", e)
        }
    }

    fun importFromJson(context: Context, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = inputStream.bufferedReader().use { it.readText() }
                val db = adapter.fromJson(json)
                if (db != null) {
                    BrowserState.domainsList = db.domainsList
                    BrowserState.globalSettings = db.globalSettings
                    BrowserState.history.clear()
                    BrowserState.history.addAll(db.history)
                    BrowserState.swipeRightNext = db.swipeRightNext
                    BrowserState.swipeLeftNext = db.swipeLeftNext
                    BrowserState.doubleTapNext = db.doubleTapNext
                    BrowserState.groupSettings = db.groupSettings
                    save(context) // Mirror to internal storage
                    Log.d("Persistence", "Database imported from: $uri")
                }
            }
        } catch (e: Exception) {
            Log.e("Persistence", "Failed to import database", e)
        }
    }
}
