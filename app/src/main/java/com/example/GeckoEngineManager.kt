package com.example

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.io.FileOutputStream

object GeckoEngineManager {
    private var runtime: GeckoRuntime? = null
    
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            runtime = GeckoRuntime.create(context.applicationContext)
            installuBlockOrigin(context)
        }
        return runtime!!
    }

    fun installuBlockOrigin(context: Context) {
        val runtime = runtime ?: return
        // Check if extension file exists before trying to install
        try {
            val assetManager = context.assets
            val extensions = assetManager.list("extensions")
            if (extensions?.contains("ublock_origin.xpi") == true) {
                runtime.webExtensionController.install("resource://android/assets/extensions/ublock_origin.xpi")
                    .accept(
                        { extension -> Log.d("GeckoEngine", "uBlock Origin installed: ${extension?.id}") },
                        { throwable -> Log.e("GeckoEngine", "Failed to install uBlock Origin", throwable) }
                    )
            } else {
                Log.w("GeckoEngine", "uBlock Origin extension not found in assets/extensions/")
            }
        } catch (e: Exception) {
            Log.e("GeckoEngine", "Extension check/install error", e)
        }
    }

    fun installExtensionFromUrl(context: Context, urlString: String, onResult: (Boolean) -> Unit) {
        val runtime = runtime ?: return
        Log.d("GeckoEngine", "Starting extension install from: $urlString")
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection()
                connection.connect()
                
                val tempFile = File(context.cacheDir, "extension_download.xpi")
                Log.d("GeckoEngine", "Downloading to: ${tempFile.absolutePath}")
                
                url.openStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Log.d("GeckoEngine", "Download complete. Size: ${tempFile.length()}")
                
                withContext(Dispatchers.Main) {
                    runtime.webExtensionController.install("file://" + tempFile.absolutePath)
                        .accept(
                            { extension -> 
                                Log.d("GeckoEngine", "Extension installed successfully: ${extension?.id}")
                                onResult(true)
                            },
                            { throwable -> 
                                Log.e("GeckoEngine", "Gecko rejected extension install", throwable)
                                onResult(false)
                            }
                        )
                }
            } catch (e: Exception) {
                Log.e("GeckoEngine", "Critical install error", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun createSession(context: Context): GeckoSession {
        val runtime = getRuntime(context)
        val session = GeckoSession()
        
        val isTablet = (context.resources.configuration.screenLayout and 
                android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= 
                android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE

        // Engine Hardening & Optimization
        session.settings.apply {
            useTrackingProtection = true
            allowJavascript = true
            suspendMediaWhenInactive = true
            
            userAgentMode = if (isTablet) 
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                
            viewportMode = if (isTablet)
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                
            // Apply custom User Agent strings (rv:151.0 for Tablet Browser Key)
            userAgentOverride = if (isTablet) {
                "Mozilla/5.0 (X11; Linux x86_64; rv:151.0) Gecko/20100101 Firefox/151.0"
            } else {
                "Mozilla/5.0 (Android 10; Mobile; rv:153.0) Gecko/153.0 Firefox/153.0 Malachite/1.0"
            }
        }

        session.open(runtime)
        return session
    }

    fun setRemoteDebuggingEnabled(enabled: Boolean) {
        runtime?.settings?.setRemoteDebuggingEnabled(enabled)
    }

    fun getInstalledExtensions(callback: (List<WebExtension>) -> Unit) {
        val controller = runtime?.webExtensionController
        if (controller == null) {
            callback(emptyList())
            return
        }
        controller.list().accept(
            { extensions -> callback(extensions ?: emptyList()) },
            { throwable -> 
                Log.e("GeckoEngine", "Failed to list extensions", throwable)
                callback(emptyList())
            }
        )
    }

    fun clearAllData() {
        runtime?.storageController?.clearData(org.mozilla.geckoview.StorageController.ClearFlags.ALL)
    }
}
