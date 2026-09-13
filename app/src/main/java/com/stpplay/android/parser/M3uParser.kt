package com.stpplay.android.parser

import android.util.Log
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream

object M3uParser {
    private const val TAG = "M3uParser"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun loadFromUrl(urlString: String): List<Channel> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL(urlString.trim())
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 20000 // 20s para conectar
            connection.readTimeout = 60000    // 60s para ler (listas M3U podem ser imensas)
            
            if (connection.responseCode != 200) {
                Log.e(TAG, "Erro ao carregar M3U: Código ${connection.responseCode}")
                return@withContext emptyList()
            }

            val channels = mutableListOf<Channel>()
            inputStream = connection.inputStream
            
            inputStream.bufferedReader().useLines { lines ->
                var currentName = ""
                var currentLogo: String? = null
                var currentGroup: String? = null
                var currentYear: String? = null

                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("#EXTINF:") -> {
                            val nameIndex = trimmed.lastIndexOf(',')
                            if (nameIndex != -1) {
                                currentName = trimmed.substring(nameIndex + 1).trim()
                            }
                            
                            val logoRegex = """tvg-logo="([^"]*)"""".toRegex()
                            currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1)
                            
                            val groupRegex = """group-title="([^"]*)"""".toRegex()
                            currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1)
                            
                            val yearRegex = """\((\d{4})\)""".toRegex()
                            currentYear = yearRegex.find(currentName)?.groupValues?.get(1)
                        }
                        trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                            if (currentName.isNotEmpty()) {
                                val type = inferType(currentName, currentGroup, trimmed)
                                channels.add(
                                    Channel(
                                        name = currentName,
                                        url = trimmed,
                                        logo = currentLogo,
                                        group = currentGroup,
                                        type = type,
                                        year = currentYear,
                                        streamId = trimmed 
                                    ),
                                )
                            }
                            currentName = ""
                            currentLogo = null
                            currentGroup = null
                            currentYear = null
                        }
                    }
                }
            }
            Log.d(TAG, "M3U carregada com sucesso: ${channels.size} itens")
            channels
        } catch (e: Exception) {
            Log.e(TAG, "Falha crítica no M3uParser: ${e.message}")
            emptyList()
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    private fun inferType(name: String, group: String?, url: String): ContentType {
        val searchIn = (name + (group ?: "")).lowercase()
        val urlLower = url.lowercase()
        
        if (searchIn.contains("s01") || searchIn.contains("s02") || searchIn.contains("s03") ||
            searchIn.contains("temporada") || searchIn.contains("episodio") || 
            searchIn.contains("ep0") || searchIn.contains("season") ||
            searchIn.contains("series") || searchIn.contains("novela")) {
            return ContentType.SERIES
        }

        val isVodExtension = urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") || 
                             urlLower.endsWith(".avi") || urlLower.endsWith(".mov") ||
                             urlLower.contains("/movie/") || urlLower.contains("/vod/")
                             
        if (searchIn.contains("filme") || searchIn.contains("movie") || 
            searchIn.contains("vod") || searchIn.contains("cinema") || 
            searchIn.contains("2023") || searchIn.contains("2024") || 
            isVodExtension) {
            
            if (searchIn.contains("temporada") || searchIn.contains("s0") || searchIn.contains("episodio")) return ContentType.SERIES
            
            return ContentType.MOVIE
        }

        return ContentType.LIVE
    }
}
