package com.stpplay.android.parser

import android.util.Log
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.data.PlaylistCredentials
import com.stpplay.android.data.UserInfo
import com.stpplay.android.database.EpgProgramEntity
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.io.InputStream

object XtreamParser {
    private const val TAG = "XtreamParser"

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    suspend fun loadFromXtream(creds: PlaylistCredentials): Pair<List<Channel>, UserInfo?> = withContext(Dispatchers.IO) {
        val sanitizedCreds = sanitize(creds)
        val userInfo = fetchUserInfo(sanitizedCreds) ?: return@withContext Pair(emptyList(), null)

        coroutineScope {
            // Buscamos todas as categorias em paralelo
            val liveCatsDef = async { fetchCategories(sanitizedCreds, "get_live_categories") }
            val movieCatsDef = async { fetchCategories(sanitizedCreds, "get_vod_categories") }
            val seriesCatsDef = async { fetchCategories(sanitizedCreds, "get_series_categories") }
            
            val liveCats = liveCatsDef.await()
            val movieCats = movieCatsDef.await()
            val seriesCats = seriesCatsDef.await()

            Log.d(TAG, "Categorias obtidas: ${liveCats.size} Live, ${movieCats.size} VOD, ${seriesCats.size} Séries")

            // Fazemos 3 grandes pedidos em paralelo para trazer tudo de uma vez
            val liveDef = async { fetchStream(sanitizedCreds, "get_live_streams", ContentType.LIVE, liveCats) }
            val moviesDef = async { fetchStream(sanitizedCreds, "get_vod_streams", ContentType.MOVIE, movieCats) }
            val seriesDef = async { fetchStream(sanitizedCreds, "get_series", ContentType.SERIES, seriesCats) }

            val live = try { liveDef.await() } catch (e: Exception) { 
                Log.e(TAG, "Erro na carga total de Live: ${e.message}"); emptyList() 
            }
            val movies = try { moviesDef.await() } catch (e: Exception) { 
                Log.e(TAG, "Erro na carga total de Filmes: ${e.message}"); emptyList() 
            }
            val series = try { seriesDef.await() } catch (e: Exception) { 
                Log.e(TAG, "Erro na carga total de Séries: ${e.message}"); emptyList() 
            }

            // FALLBACK FILMES
            val finalMovies = if (movies.isEmpty() && movieCats.isNotEmpty()) {
                Log.w(TAG, "Carga total de filmes vazia. Tentando carregar por pastas...")
                val list = mutableListOf<Channel>()
                movieCats.keys.chunked(15).forEach { ids ->
                    val deferred = ids.map { catId ->
                        async { fetchStream(sanitizedCreds, "get_vod_streams&category_id=$catId", ContentType.MOVIE, movieCats) }
                    }
                    list.addAll(deferred.awaitAll().flatten())
                }
                list
            } else movies

            // FALLBACK SÉRIES
            val finalSeries = if (series.isEmpty() && seriesCats.isNotEmpty()) {
                Log.w(TAG, "Carga total de séries falhou ou vazia. Tentando carregar por blocos...")
                val list = mutableListOf<Channel>()
                seriesCats.keys.chunked(10).forEach { ids ->
                    val deferred = ids.map { catId ->
                        async { fetchStream(sanitizedCreds, "get_series&category_id=$catId", ContentType.SERIES, seriesCats) }
                    }
                    list.addAll(deferred.awaitAll().flatten())
                }
                list
            } else series

            Log.d(TAG, "Sincronização Finalizada: ${live.size} live, ${finalMovies.size} filmes, ${finalSeries.size} séries.")
            val total = live + finalMovies + finalSeries
            
            Log.d(TAG, "Total Real no Banco: ${total.size}")
            Pair(total, userInfo)
        }
    }

    private fun sanitize(creds: PlaylistCredentials): PlaylistCredentials {
        val cleanUrl = creds.url.trim().removeSuffix("/")
        val cleanUser = creds.user.trim().filter { !it.isWhitespace() }
        val cleanPass = creds.pass.trim().filter { !it.isWhitespace() }
        return creds.copy(url = cleanUrl, user = cleanUser, pass = cleanPass)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fetchUserInfo(creds: PlaylistCredentials): UserInfo? = withContext(Dispatchers.IO) {
        val apiUrl = "${creds.url}/player_api.php?username=${creds.user}&password=${creds.pass}"
        var inputStream: InputStream? = null
        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000 // Reduzido para failover mais rápido
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw java.io.IOException("Servidor retornou erro HTTP: $responseCode")
            }

            inputStream = connection.inputStream
            val jsonElement = json.decodeFromStream<JsonElement>(inputStream)
            val userObj = jsonElement.jsonObject["user_info"]?.jsonObject 
                ?: throw java.io.IOException("Resposta do servidor não contém informações de usuário")
            
            // CASO ÚNICO DE RETORNO NULL: Credenciais Rejeitadas (auth=0)
            if (userObj["auth"]?.jsonPrimitive?.content == "0") return@withContext null

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            fun formatTimestamp(ts: String?): String {
                if (ts == null || ts == "null" || ts == "0") return "Vitalício"
                return try {
                    val date = Date(ts.toLong() * 1000)
                    sdf.format(date)
                } catch (e: Exception) { "N/A" }
            }

            fun getRawTimestamp(ts: String?): Long? {
                if (ts == null || ts == "null" || ts == "0") return null
                return try { ts.toLong() * 1000 } catch (e: Exception) { null }
            }

            UserInfo(
                username = userObj["username"]?.jsonPrimitive?.content ?: creds.user,
                status = userObj["status"]?.jsonPrimitive?.content ?: "Ativo",
                expiryDate = formatTimestamp(userObj["exp_date"]?.jsonPrimitive?.content),
                createdAt = formatTimestamp(userObj["created_at"]?.jsonPrimitive?.content),
                isTrial = userObj["is_trial"]?.jsonPrimitive?.content == "1",
                activeConnections = userObj["active_cons"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                maxConnections = userObj["max_connections"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                expiryTimestamp = getRawTimestamp(userObj["exp_date"]?.jsonPrimitive?.content)
            )
        } catch (e: java.io.IOException) {
            // Repassa erro de rede/servidor para o ViewModel tratar o failover
            throw e
        } catch (e: Exception) {
            throw java.io.IOException("Falha ao processar resposta do servidor: ${e.message}")
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fetchCategories(creds: PlaylistCredentials, action: String): Map<String, String> = withContext(Dispatchers.IO) {
        val apiUrl = "${creds.url}/player_api.php?username=${creds.user}&password=${creds.pass}&action=$action"
        var inputStream: InputStream? = null
        
        repeat(3) {
            try {
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                if (connection.responseCode == 200) {
                    inputStream = connection.inputStream
                    return@repeat
                }
            } catch (e: Exception) {
                delay(1000)
            }
        }
        
        val stream = inputStream ?: return@withContext emptyMap()
        
        try {
            val jsonElement = json.decodeFromStream<JsonElement>(stream)
            val jsonArray = when (jsonElement) {
                is JsonArray -> jsonElement
                is JsonObject -> JsonArray(jsonElement.values.filterIsInstance<JsonObject>())
                else -> return@withContext emptyMap()
            }

            jsonArray.associate { 
                val obj = it.jsonObject
                val id = obj["category_id"]?.jsonPrimitive?.content ?: ""
                val name = obj["category_name"]?.jsonPrimitive?.content ?: "Sem Categoria"
                id to name
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao processar categorias de $action: ${e.message}")
            emptyMap() 
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fetchStream(creds: PlaylistCredentials, action: String, type: ContentType, catMap: Map<String, String>): List<Channel> = withContext(Dispatchers.IO) {
        val apiUrl = "${creds.url}/player_api.php?username=${creds.user}&password=${creds.pass}&action=$action"
        var inputStream: InputStream? = null
        
        repeat(3) {
            try {
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000 // 10s para conectar
                connection.readTimeout = 60000 // 60s para ler (listas grandes levam tempo)
                if (connection.responseCode == 200) {
                    inputStream = connection.inputStream
                    return@repeat
                }
            } catch (e: Exception) {
                delay(1000)
            }
        }

        val stream = inputStream ?: return@withContext emptyList()
        
        try {
            // Decodifica diretamente do Stream para evitar alocar uma String gigante desnecessariamente
            val jsonElement = json.decodeFromStream<JsonElement>(stream)
            
            val jsonArray = when (jsonElement) {
                is JsonArray -> jsonElement
                is JsonObject -> {
                    val dataArray = jsonElement["movies"] ?: jsonElement["series"] ?: jsonElement["streams"] ?: jsonElement["vod"]
                    if (dataArray is JsonArray) dataArray
                    else JsonArray(jsonElement.values.filterIsInstance<JsonObject>())
                }
                else -> return@withContext emptyList()
            }
            
            jsonArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val streamId = obj["stream_id"]?.jsonPrimitive?.content 
                        ?: obj["movie_id"]?.jsonPrimitive?.content 
                        ?: obj["series_id"]?.jsonPrimitive?.content 
                        ?: obj["id"]?.jsonPrimitive?.content
                    
                    if (streamId == null) return@mapNotNull null
                        
                    val categoryId = obj["category_id"]?.jsonPrimitive?.content ?: ""
                    val epgId = obj["epg_channel_id"]?.jsonPrimitive?.contentOrNull 
                        ?: obj["epg_id"]?.jsonPrimitive?.contentOrNull
                    
                    val streamUrl = when (type) {
                        ContentType.LIVE -> "${creds.url}/live/${creds.user}/${creds.pass}/$streamId.ts"
                        ContentType.MOVIE -> "${creds.url}/movie/${creds.user}/${creds.pass}/$streamId.${obj["container_extension"]?.jsonPrimitive?.contentOrNull ?: "mp4"}"
                        ContentType.SERIES -> "${creds.url}/series/${creds.user}/${creds.pass}/$streamId.${obj["container_extension"]?.jsonPrimitive?.contentOrNull ?: "mp4"}"
                        else -> ""
                    }
                    
                    Channel(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: obj["title"]?.jsonPrimitive?.contentOrNull ?: "Sem nome",
                        url = streamUrl,
                        logo = if (type == ContentType.LIVE) {
                            obj["stream_icon"]?.jsonPrimitive?.contentOrNull ?: obj["cover"]?.jsonPrimitive?.contentOrNull ?: obj["movie_image"]?.jsonPrimitive?.contentOrNull
                        } else {
                            obj["cover"]?.jsonPrimitive?.contentOrNull ?: obj["movie_image"]?.jsonPrimitive?.contentOrNull ?: obj["stream_icon"]?.jsonPrimitive?.contentOrNull
                        },
                        group = catMap[categoryId] ?: "Outros",
                        type = type,
                        description = obj["plot"]?.jsonPrimitive?.contentOrNull,
                        rating = obj["rating"]?.jsonPrimitive?.contentOrNull,
                        year = obj["releaseDate"]?.jsonPrimitive?.contentOrNull?.take(4) ?: obj["year"]?.jsonPrimitive?.contentOrNull,
                        director = obj["director"]?.jsonPrimitive?.contentOrNull,
                        cast = obj["cast"]?.jsonPrimitive?.contentOrNull,
                        streamId = streamId,
                        categoryId = categoryId,
                        epgChannelId = epgId,
                        genre = obj["genre"]?.jsonPrimitive?.contentOrNull,
                        duration = obj["duration"]?.jsonPrimitive?.contentOrNull ?: obj["duration_secs"]?.jsonPrimitive?.contentOrNull,
                        language = obj["language"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao processar JSON de $action: ${e.message}")
            emptyList() 
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    private fun makeRequest(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            // User-Agent Inteligente: Identifica o App e o Aparelho do Usuário no Painel do Provedor
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".uppercase()
            val userAgent = "STP-Play/1.2 ($deviceModel)"
            
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 15000 
            connection.readTimeout = 30000 
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na requisição HTTP ($url): ${e.message}")
            null
        }
    }

    private fun decodeBase64IfEncoded(text: String?): String? {
        if (text.isNullOrBlank()) return text
        val isBase64 = text.length >= 8 && text.length % 4 == 0 && text.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        if (!isBase64) return text
        
        return try {
            val decodedBytes = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
            val decoded = String(decodedBytes, Charsets.UTF_8)
            if (decoded.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }) text else decoded
        } catch (e: Exception) { text }
    }

    private fun parseEpgDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank() || dateStr == "null") return 0L
        val decoded = decodeBase64IfEncoded(dateStr) ?: dateStr
        val cleanDate = decoded.trim()
        
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss Z", "yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
        for (pattern in formats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                val date = format.parse(cleanDate)
                if (date != null) return date.time
            } catch (e: Exception) {}
        }

        try {
            val ts = cleanDate.split(" ")[0].toLong()
            return if (ts < 1000000000000L) ts * 1000 else ts
        } catch (e: Exception) {}
        return 0L
    }

    suspend fun fetchSeriesEpisodes(creds: PlaylistCredentials, seriesId: String): List<Channel> = withContext(Dispatchers.IO) {
        try {
            val sanitized = sanitize(creds)
            val apiUrl = "${sanitized.url}/player_api.php?username=${sanitized.user}&password=${sanitized.pass}&action=get_series_info&series_id=$seriesId"
            val response = makeRequest(apiUrl) ?: return@withContext emptyList()
            val jsonElement = json.parseToJsonElement(response)
            
            val episodes = mutableListOf<Channel>()
            val episodesJson = jsonElement.jsonObject["episodes"]?.jsonObject ?: return@withContext emptyList()
            
            for ((seasonNum, seasonEpisodes) in episodesJson) {
                for (episode in seasonEpisodes.jsonArray) {
                    try {
                        val obj = episode.jsonObject
                        val streamId = obj["id"]?.jsonPrimitive?.content ?: ""
                        val container = obj["container_extension"]?.jsonPrimitive?.content ?: "mp4"
                        
                        episodes.add(
                            Channel(
                                name = "S$seasonNum:E${obj["episode_num"]} - ${obj["title"]?.jsonPrimitive?.content ?: "Episódio"}",
                                url = "${sanitized.url}/series/${sanitized.user}/${sanitized.pass}/$streamId.$container",
                                type = ContentType.SERIES,
                                group = "Temporada $seasonNum",
                                logo = obj["info"]?.jsonObject?.get("movie_image")?.jsonPrimitive?.content,
                                description = obj["info"]?.jsonObject?.get("plot")?.jsonPrimitive?.content,
                                streamId = streamId,
                                parentId = seriesId
                            )
                        )
                    } catch (e: Exception) {}
                }
            }
            episodes
        } catch (e: Exception) { emptyList() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchShortEpg(creds: PlaylistCredentials, streamId: String, epgChannelId: String? = null): List<EpgProgramEntity> = withContext(Dispatchers.IO) {
        try {
            val sanitized = sanitize(creds)
            fun isEmptyEpg(resp: String?): Boolean {
                if (resp == null) return true
                val clean = resp.replace(" ", "").replace("\n", "").replace("\r", "")
                return clean.contains("\"epg_listings\":[]") || clean.contains("\"epg_listing\":[]") || clean == "[]" || clean == "{}"
            }

            var apiUrl = "${sanitized.url}/player_api.php?username=${sanitized.user}&password=${sanitized.pass}&action=get_short_epg&stream_id=$streamId"
            var response = makeRequest(apiUrl)
            
            if (isEmptyEpg(response) && !epgChannelId.isNullOrBlank()) {
                apiUrl = "${sanitized.url}/player_api.php?username=${sanitized.user}&password=${sanitized.pass}&action=get_short_epg&stream_id=$epgChannelId"
                val fallbackResponse = makeRequest(apiUrl)
                if (!isEmptyEpg(fallbackResponse)) response = fallbackResponse
            }
            
            if (isEmptyEpg(response)) {
                val idToTry = if (!epgChannelId.isNullOrBlank()) epgChannelId else streamId
                apiUrl = "${sanitized.url}/player_api.php?username=${sanitized.user}&password=${sanitized.pass}&action=get_epg&stream_id=$idToTry"
                val fallback2Response = makeRequest(apiUrl)
                if (!isEmptyEpg(fallback2Response)) response = fallback2Response
            }

            if (response == null) return@withContext emptyList()
            
            val jsonElement = json.parseToJsonElement(response)
            val epgList = when {
                jsonElement is JsonArray -> jsonElement
                jsonElement is JsonObject && jsonElement.containsKey("epg_listings") -> jsonElement.jsonObject["epg_listings"]
                jsonElement is JsonObject && jsonElement.containsKey("epg_listing") -> jsonElement.jsonObject["epg_listing"]
                else -> null
            }
            
            val jsonArray = when (epgList) {
                is JsonArray -> epgList
                is JsonObject -> JsonArray(epgList.values.filterIsInstance<JsonObject>())
                else -> return@withContext emptyList()
            }
            
            jsonArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val titleRaw = obj["title"]?.jsonPrimitive?.content ?: ""
                    val descRaw = obj["description"]?.jsonPrimitive?.content ?: ""
                    val startTs = obj["start_timestamp"]?.jsonPrimitive?.content?.toLongOrNull()?.let { it * 1000 } ?: parseEpgDate(obj["start"]?.jsonPrimitive?.content)
                    val endTs = obj["stop_timestamp"]?.jsonPrimitive?.content?.toLongOrNull()?.let { it * 1000 } ?: parseEpgDate(obj["end"]?.jsonPrimitive?.content)

                    EpgProgramEntity(
                        streamId = streamId,
                        title = decodeBase64IfEncoded(titleRaw) ?: titleRaw,
                        description = decodeBase64IfEncoded(descRaw) ?: descRaw,
                        startTimestamp = startTs,
                        stopTimestamp = endTs
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchSimpleDataTable(creds: PlaylistCredentials): Map<String, EpgProgramEntity> = withContext(Dispatchers.IO) {
        val apiUrl = "${creds.url}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_simple_data_table"
        var inputStream: InputStream? = null
        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            if (connection.responseCode == 200) {
                inputStream = connection.inputStream
                val jsonElement = json.decodeFromStream<JsonElement>(inputStream)
                val epgMap = mutableMapOf<String, EpgProgramEntity>()
                
                when (jsonElement) {
                    is JsonObject -> jsonElement.forEach { id, data -> parseAndAddEpgEntry(id, data, epgMap) }
                    is JsonArray -> jsonElement.forEach { element ->
                        val obj = element.jsonObject
                        val id = obj["stream_id"]?.jsonPrimitive?.content ?: obj["epg_channel_id"]?.jsonPrimitive?.content ?: ""
                        if (id.isNotEmpty()) parseAndAddEpgEntry(id, element, epgMap)
                    }
                    else -> {}
                }
                epgMap
            } else emptyMap()
        } catch (e: Exception) { emptyMap() }
        finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchVODInfo(creds: PlaylistCredentials, streamId: String, type: ContentType): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val action = if (type == ContentType.MOVIE) "get_vod_info" else "get_series_info"
        val idParam = if (type == ContentType.MOVIE) "vod_id" else "series_id"
        val apiUrl = "${creds.url}/player_api.php?username=${creds.user}&password=${creds.pass}&action=$action&$idParam=$streamId"
        var inputStream: InputStream? = null
        try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (connection.responseCode == 200) {
                inputStream = connection.inputStream
                val jsonElement = json.decodeFromStream<JsonElement>(inputStream)
                val info = jsonElement.jsonObject["info"]?.jsonObject ?: return@withContext Pair(null, null)
                
                val trailer = info["youtube_trailer"]?.jsonPrimitive?.contentOrNull
                val duration = info["duration"]?.jsonPrimitive?.contentOrNull ?: info["duration_secs"]?.jsonPrimitive?.contentOrNull
                
                val finalTrailer = if (!trailer.isNullOrBlank()) {
                    if (trailer.startsWith("http")) trailer else "https://www.youtube.com/watch?v=$trailer"
                } else null
                
                Pair(finalTrailer, duration)
            } else Pair(null, null)
        } catch (e: Exception) { Pair(null, null) }
        finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun fetchTrailerUrl(creds: PlaylistCredentials, streamId: String, type: ContentType): String? {
        return fetchVODInfo(creds, streamId, type).first
    }

    private fun parseAndAddEpgEntry(id: String, data: JsonElement, map: MutableMap<String, EpgProgramEntity>) {
        try {
            val obj = data.jsonObject
            val titleRaw = obj["title"]?.jsonPrimitive?.content ?: ""
            val descRaw = obj["description"]?.jsonPrimitive?.contentOrNull
            val startTs = obj["start_timestamp"]?.jsonPrimitive?.content?.toLongOrNull()?.let { it * 1000 } ?: parseEpgDate(obj["start"]?.jsonPrimitive?.content)
            val endTs = obj["stop_timestamp"]?.jsonPrimitive?.content?.toLongOrNull()?.let { it * 1000 } ?: parseEpgDate(obj["end"]?.jsonPrimitive?.content)
            val epgId = obj["epg_channel_id"]?.jsonPrimitive?.contentOrNull
            if (startTs > 0 && endTs > 0) {
                map[id] = EpgProgramEntity(id, epgId, decodeBase64IfEncoded(titleRaw) ?: titleRaw, startTs, endTs, decodeBase64IfEncoded(descRaw))
                if (epgId != null && epgId != id) map["$id-$epgId"] = EpgProgramEntity(epgId, id, decodeBase64IfEncoded(titleRaw) ?: titleRaw, startTs, endTs, decodeBase64IfEncoded(descRaw))
            }
        } catch (e: Exception) {}
    }
}
