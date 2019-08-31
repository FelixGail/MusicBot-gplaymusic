package com.github.felixgail.musicbot.gplaymusic

import com.github.felixgail.gplaymusic.api.GPlayMusic
import com.github.felixgail.gplaymusic.exceptions.NetworkException
import com.github.felixgail.gplaymusic.model.Track
import com.github.felixgail.gplaymusic.model.enums.StreamQuality
import com.github.felixgail.gplaymusic.util.TokenProvider
import com.google.common.cache.CacheBuilder
import com.google.common.cache.LoadingCache
import com.google.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.cache.AsyncLoader
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ExperimentalConfigDsl
import net.bjoernpetersen.musicbot.api.config.IntSerializer
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.api.config.NumberBox
import net.bjoernpetersen.musicbot.api.config.PasswordBox
import net.bjoernpetersen.musicbot.api.config.SerializationException
import net.bjoernpetersen.musicbot.api.config.TextBox
import net.bjoernpetersen.musicbot.api.config.boolean
import net.bjoernpetersen.musicbot.api.config.choiceBox
import net.bjoernpetersen.musicbot.api.config.serialization
import net.bjoernpetersen.musicbot.api.config.serialized
import net.bjoernpetersen.musicbot.api.config.string
import net.bjoernpetersen.musicbot.api.loader.FileResource
import net.bjoernpetersen.musicbot.api.loader.SongLoadingException
import net.bjoernpetersen.musicbot.api.player.Song
import net.bjoernpetersen.musicbot.api.plugin.NamedPlugin
import net.bjoernpetersen.musicbot.api.plugin.PluginScope
import net.bjoernpetersen.musicbot.spi.loader.Resource
import net.bjoernpetersen.musicbot.spi.plugin.InitializationException
import net.bjoernpetersen.musicbot.spi.plugin.NoSuchSongException
import net.bjoernpetersen.musicbot.spi.plugin.Playback
import net.bjoernpetersen.musicbot.spi.plugin.Plugin
import net.bjoernpetersen.musicbot.spi.plugin.UserFacing
import net.bjoernpetersen.musicbot.spi.plugin.id
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.musicbot.spi.plugin.predefined.Mp3PlaybackFactory
import net.bjoernpetersen.musicbot.spi.plugin.predefined.UnsupportedAudioFileException
import net.bjoernpetersen.musicbot.spi.util.FileStorage
import net.bjoernpetersen.musicbot.youtube.provider.YouTubeProvider
import svarzee.gps.gpsoauth.AuthToken
import svarzee.gps.gpsoauth.Gpsoauth
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@UseExperimental(ExperimentalConfigDsl::class)
class GPlayMusicProviderImpl : GPlayMusicProvider, CoroutineScope by PluginScope(Dispatchers.IO) {

    private val logger = KotlinLogging.logger { }

    private lateinit var username: Config.StringEntry
    private lateinit var password: Config.StringEntry
    private lateinit var androidID: Config.StringEntry
    private lateinit var token: Config.StringEntry
    private lateinit var streamQuality: Config.SerializedEntry<StreamQuality>
    private lateinit var cacheTime: Config.SerializedEntry<Int>
    private lateinit var showVideos: Config.BooleanEntry
    @Inject
    private lateinit var fileStorage: FileStorage
    private var fileDir: File? = null
    @Inject
    private lateinit var playbackFactory: Mp3PlaybackFactory
    override lateinit var api: GPlayMusic
        private set

    @Inject(optional = true)
    private var youtubeProvider: YouTubeProvider? = null

    private lateinit var cachedSongs: LoadingCache<String, Deferred<Song>>

    override val name: String
        get() = "GPlayMusic"

    override val description: String
        get() = "Provides songs from Google Play Music"

    override val subject: String
        get() = "Google Play Music"

    private fun isYoutubeEnabled() = showVideos.get() && youtubeProvider != null

    override fun createStateEntries(state: Config) {}

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> {
        username = config.string("Username") {
            description = "Username or Email of your Google account with AllAccess subscription"
            check(NonnullConfigChecker)
            uiNode = TextBox
        }

        streamQuality = config.serialized("Quality") {
            description = "Sets the quality in which the songs are streamed"
            check { null }
            serialization {
                serialize { it.name }
                deserialize {
                    try {
                        StreamQuality.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        throw SerializationException()
                    }
                }
            }
            choiceBox {
                describe { it.name }
                refresh { StreamQuality.values().toList() }
            }
            default(StreamQuality.HIGH)
        }

        cacheTime = config.serialized("Cache Time") {
            description = "Duration in Minutes until cached songs will be deleted."
            serializer = IntSerializer
            check { null }
            uiNode = NumberBox(1, 3600)
            default(60)
        }

        showVideos = config.boolean("Show YouTube videos") {
            description = "Use YouTube video, if available"
            default = false
        }

        return listOf(username, streamQuality, cacheTime, showVideos)
    }

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> {
        password = secrets.string("Password") {
            description = "Password/App password of your Google account"
            check(NonnullConfigChecker)
            uiNode = PasswordBox
        }

        androidID = secrets.string("Android ID") {
            description = "IMEI or GoogleID of your smartphone with GooglePlayMusic installed"
            check(NonnullConfigChecker)
            uiNode = TextBox
        }

        token = secrets.string("Token") {
            description = "Authtoken"
            check(NonnullConfigChecker)
            uiNode = TextBox
        }

        return listOf(password, androidID, token)
    }

    @Throws(InitializationException::class)
    override suspend fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Obtaining storage dir")
        fileDir = File(fileStorage.forPlugin(this, true), "songs/")

        initStateWriter.state("Creating cache")
        cachedSongs = CacheBuilder.newBuilder()
            .expireAfterAccess(cacheTime.get()!!.toLong(), TimeUnit.MINUTES)
            .initialCapacity(256)
            .maximumSize(1024)
            .build(AsyncLoader(this) { getSongFromTrack(api.trackApi.getTrack(it)) })

        val songDir = fileDir!!
        if (!songDir.exists()) {
            if (!songDir.mkdir()) {
                throw InitializationException("Unable to create song directory")
            }
        }

        initStateWriter.state("Logging into GPlayMusic")
        withContext(coroutineContext) {
            try {
                loginToService(initStateWriter)
            } catch (e: IOException) {
                initStateWriter.warning("Logging into GPlayMusic failed!")
                throw InitializationException(e)
            } catch (e: Gpsoauth.TokenRequestFailed) {
                initStateWriter.warning("Logging into GPlayMusic failed!")
                throw InitializationException(e)
            }
        }
    }

    @Throws(IOException::class, Gpsoauth.TokenRequestFailed::class)
    private fun loginToService(initStateWriter: InitStateWriter) {
        val authToken: AuthToken?
        var existingToken = false
        if (token.get() != null && token.checkError() == null) {
            authToken = TokenProvider.provideToken(token.get())
            existingToken = true
            initStateWriter.state("Trying to login with existing token.")
        } else {
            initStateWriter.state("Fetching new token.")
            authToken = TokenProvider.provideToken(username.get(), password.get(), androidID.get())
            token.set(authToken!!.token)
        }
        try {
            api = GPlayMusic.Builder().setAuthToken(authToken).build()
        } catch (e: com.github.felixgail.gplaymusic.exceptions.InitializationException) {
            if (existingToken) {
                token.set(null)
                loginToService(initStateWriter)
            } else {
                throw e
            }
        }
    }

    override suspend fun close() {
        run { cancel() }
    }

    override fun getSongFromTrack(track: Track): Song {
        val song = super.getSongFromTrack(track)
        if (isYoutubeEnabled()) {
            val video = track.video
            if (video.isPresent) {
                logger.debug { "Delegating song to YouTube: ${song.title} " }
                return song.copy(id = video.get().id, provider = youtubeProvider!!.toNamedPlugin())
            }
        }
        return song
    }

    override suspend fun search(query: String, offset: Int): List<Song> {
        return withContext(coroutineContext) {
            try {
                // We retrieve up to two pages for now and limit them to a size of 30 each
                val max = if (offset < 20) 30 else 60
                val result = api.trackApi.search(query, max).asSequence()
                    .map { getSongFromTrack(it) }
                    .onEach { song -> cachedSongs.put(song.id, CompletableDeferred(song)) }
                    .toList()
                result.subList(min(offset, max(0, result.size - 1)), min(offset + 30, result.size))
            } catch (e: IOException) {
                if (e is NetworkException && e.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    if (generateNewToken()) {
                        search(query, offset)
                    } else {
                        emptyList()
                    }
                } else {
                    logger.warn("Exception while searching with query '$query'", e)
                    emptyList()
                }
            }
        }
    }

    @Throws(NoSuchSongException::class)
    override suspend fun lookup(id: String): Song {
        try {
            return cachedSongs.get(id).await()
        } catch (e: Exception) {
            throw NoSuchSongException(id, GPlayMusicProvider::class, e)
        }
    }

    @Throws(SongLoadingException::class)
    override suspend fun loadSong(song: Song): Resource {
        return withContext(coroutineContext) {
            val songDir = fileDir!!.path
            try {
                val track = api.trackApi.getTrack(song.id)
                val path = Paths.get(songDir, song.id + ".mp3")
                val tmpPath = Paths.get(songDir, song.id + ".mp3.tmp")
                if (!Files.exists(path)) {
                    track.download(streamQuality.get(), tmpPath)
                    Files.move(tmpPath, path)
                }
                FileResource(path.toFile())
            } catch (e: IOException) {
                throw SongLoadingException(e)
            }
        }
    }

    @Throws(IOException::class)
    override suspend fun supplyPlayback(song: Song, resource: Resource): Playback {
        val fileResource = resource as FileResource
        try {
            return playbackFactory.createPlayback(fileResource.file)
        } catch (e: UnsupportedAudioFileException) {
            throw IOException(e)
        }
    }

    private fun generateNewToken(): Boolean {
        val diffLastRequest = System.currentTimeMillis() - TokenProvider.getLastTokenFetched()
        if (diffLastRequest > tokenCooldownMillis) {
            logger.info("Authorization expired. Requesting new token.")
            try {
                api.changeToken(
                    TokenProvider.provideToken(username.get(), password.get(), androidID.get())
                )
                return true
            } catch (e: Gpsoauth.TokenRequestFailed) {
                logger.error(
                    "Exception while trying to generate new token. Unable to authenticate client.",
                    e
                )
            } catch (e: IOException) {
                logger.error(
                    "Exception while trying to generate new token. Unable to authenticate client.",
                    e
                )
            }

        } else {
            logger.info(
                "Token request on cooldown. Please wait %d seconds.",
                diffLastRequest * 1000
            )
        }
        return false
    }

    private companion object {
        const val tokenCooldownMillis: Long = 60000
    }
}

private fun <T> T.toNamedPlugin(): NamedPlugin<T> where T : Plugin, T : UserFacing {
    return NamedPlugin(
        id.qualifiedName!!,
        subject
    )
}
