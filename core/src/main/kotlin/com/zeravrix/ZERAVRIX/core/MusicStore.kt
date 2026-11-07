package com.zeravrix.zeravrix.core

import javazoom.jl.player.Player
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

/** Persistent imported local music and named playlists. */
data class MusicTrack(
    val id: String,
    val title: String,
    val path: Path,
)

data class MusicPlaylist(
    val name: String,
    val trackIds: List<String>,
)

class MusicLibraryStore(private val root: Path) {
    private val musicRoot = root.resolve("music")
    private val metadataFile = musicRoot.resolve("music.properties")

    init { Files.createDirectories(musicRoot) }

    @Synchronized
    fun tracks(): List<MusicTrack> {
        val props = load()
        return props.stringPropertyNames()
            .filter { it.startsWith("track.") && it.endsWith(".path") }
            .mapNotNull { key ->
                val id = key.removePrefix("track.").removeSuffix(".path")
                val path = Path.of(props.getProperty(key))
                if (!Files.isRegularFile(path)) return@mapNotNull null
                MusicTrack(id, props.getProperty("track.$id.title", path.fileName.toString()), path)
            }
            .sortedBy { it.title.lowercase() }
    }

    @Synchronized
    fun importFile(file: Path): MusicTrack {
        require(Files.isRegularFile(file)) { "Music file does not exist: $file" }
        val allowed = setOf("mp3", "wav", "aif", "aiff", "au")
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in allowed) { "Unsupported local music format .$extension. Use MP3, WAV, AIFF, or AU." }
        val id = UUID.randomUUID().toString()
        val safeName = file.fileName.toString().replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val target = musicRoot.resolve("$id-$safeName")
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        val track = MusicTrack(id, file.fileName.toString().substringBeforeLast('.'), target)
        val props = load()
        props.setProperty("track.$id.title", track.title)
        props.setProperty("track.$id.path", target.toString())
        save(props)
        return track
    }

    @Synchronized
    fun removeTrack(trackId: String) {
        val props = load()
        val path = props.getProperty("track.$trackId.path")?.let(Path::of)
        if (path != null) Files.deleteIfExists(path)
        props.remove("track.$trackId.title")
        props.remove("track.$trackId.path")
        playlists().forEach { playlist ->
            props.setProperty("playlist.${playlist.name}.tracks", playlist.trackIds.filterNot { it == trackId }.joinToString(","))
        }
        save(props)
    }

    @Synchronized
    fun playlists(): List<MusicPlaylist> {
        val props = load()
        return props.stringPropertyNames()
            .filter { it.startsWith("playlist.") && it.endsWith(".tracks") }
            .map { key ->
                val name = key.removePrefix("playlist.").removeSuffix(".tracks")
                MusicPlaylist(name, props.getProperty(key).split(',').filter { it.isNotBlank() })
            }
            .sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun savePlaylist(playlist: MusicPlaylist) {
        val props = load()
        val safeName = playlist.name.trim().ifBlank { "My playlist" }
        props.setProperty("playlist.$safeName.tracks", playlist.trackIds.joinToString(","))
        save(props)
    }

    private fun load(): Properties = Properties().also { props ->
        if (Files.isRegularFile(metadataFile)) Files.newInputStream(metadataFile).use(props::load)
    }

    private fun save(props: Properties) {
        Files.newOutputStream(metadataFile).use { props.store(it, "ZERAVRIX local music") }
    }
}

/** Small native player. MP3 is decoded by JLayer; PCM formats use Java Sound. */
class LocalMusicPlayer {
    private val lock = Any()
    private var mp3: Player? = null
    private var clip: Clip? = null
    private var worker: Thread? = null
    private var finished: (() -> Unit)? = null

    fun play(track: MusicTrack, onFinished: () -> Unit = {}) {
        stop()
        finished = onFinished
        val extension = track.path.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension == "mp3") {
            worker = Thread {
                runCatching {
                    BufferedInputStream(Files.newInputStream(track.path)).use { input ->
                        synchronized(lock) { mp3 = Player(input) }
                        mp3?.play()
                    }
                }
                synchronized(lock) { mp3 = null }
                onFinished()
            }.apply { isDaemon = true; name = "zeravrix-music"; start() }
        } else {
            worker = Thread {
                runCatching {
                    val audio = AudioSystem.getAudioInputStream(track.path.toFile())
                    val data = AudioSystem.getClip()
                    data.open(audio)
                    synchronized(lock) { clip = data }
                    data.addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP && data.microsecondPosition >= data.microsecondLength - 50_000) onFinished()
                    }
                    data.start()
                }
            }.apply { isDaemon = true; name = "zeravrix-music"; start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            runCatching { mp3?.close() }
            mp3 = null
            runCatching { clip?.stop(); clip?.close() }
            clip = null
            worker?.interrupt()
            worker = null
        }
    }
}
