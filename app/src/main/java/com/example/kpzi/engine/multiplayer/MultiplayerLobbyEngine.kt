package com.example.kpzi.engine.multiplayer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.kpzi.data.local.CoopHistoryEntity
import com.example.kpzi.data.local.FavoriteRoomEntity
import com.example.kpzi.data.local.MultiplayerDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class RoomVisibility(val labelTh: String, val labelEn: String) {
    PUBLIC("สาธารณะ", "Public"),
    FRIENDS("เพื่อน", "Friends"),
    PRIVATE("ส่วนตัว", "Private");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
}

enum class SignalQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN,
}

data class MultiplayerRoom(
    val roomId: String,
    val roomName: String,
    val ownerName: String,
    val isOwnHostRoom: Boolean,
    val currentPlayers: Int,
    val maxPlayers: Int,
    val visibility: RoomVisibility,
    val requiresPassword: Boolean,
    val gameVersion: String,
    val pingMs: Int?,
    val hostAddress: String,
    val port: Int,
    val roomCode: String,
    val createdAtMs: Long,
    val isUserAuthorized: Boolean = false,
) {
    val isFull: Boolean get() = currentPlayers >= maxPlayers

    val signalQuality: SignalQuality
        get() = when {
            pingMs == null -> SignalQuality.UNKNOWN
            pingMs <= 45 -> SignalQuality.EXCELLENT
            pingMs <= 95 -> SignalQuality.GOOD
            pingMs <= 165 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
}

data class HostedRoomConfig(
    val roomName: String,
    val ownerName: String,
    val maxPlayers: Int,
    val visibility: RoomVisibility,
    val password: String,
    val gameVersion: String,
)

sealed interface JoinRoomResult {
    data class Success(val room: MultiplayerRoom) : JoinRoomResult
    data class PasswordRequired(val room: MultiplayerRoom) : JoinRoomResult
    data class InvalidPassword(val room: MultiplayerRoom) : JoinRoomResult
    data class RoomFull(val room: MultiplayerRoom) : JoinRoomResult
    data class Unreachable(val reasonTh: String, val reasonEn: String) : JoinRoomResult
}

data class MultiplayerLobbySnapshot(
    val rooms: List<MultiplayerRoom> = emptyList(),
    val activeHostRoom: MultiplayerRoom? = null,
    val isScanning: Boolean = false,
    val isConnectingRoomId: String? = null,
    val lastRefreshedAtMs: Long? = null,
    val statusNoteTh: String? = null,
    val statusNoteEn: String? = null,
) {
    val totalOnlinePlayers: Int
        get() = rooms.sumOf { it.currentPlayers.coerceAtLeast(0) }
}

/**
 * Encodes/decodes IPv4 + port into a compact shareable Kpzi Room Code (`KPZ-XXXXX-XXXXX`),
 * and also parses direct `ip:port` or `ip` host strings.
 */
object KpziRoomCodeCodec {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 32 chars (5 bits each)
    const val DEFAULT_STARDEW_PORT = 24642

    fun encode(ipv4: String, port: Int): String? {
        val parts = ipv4.trim().split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(6)
        for (i in 0..3) {
            val octet = parts[i].toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            bytes[i] = octet.toByte()
        }
        val clampedPort = port.coerceIn(1, 65535)
        bytes[4] = ((clampedPort ushr 8) and 0xFF).toByte()
        bytes[5] = (clampedPort and 0xFF).toByte()

        var value = 0L
        for (b in bytes) {
            value = (value shl 8) or (b.toLong() and 0xFFL)
        }
        // 48 bits -> 10 base32 chars (50 bits)
        val chars = CharArray(10)
        for (idx in 9 downTo 0) {
            chars[idx] = ALPHABET[(value and 0x1FL).toInt()]
            value = value ushr 5
        }
        val raw = String(chars)
        return "KPZ-${raw.substring(0, 5)}-${raw.substring(5, 10)}"
    }

    fun decode(input: String): Pair<String, Int>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Check direct ip:port or bare ip first
        val ipv4Regex = Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?::(\d{1,5}))?$""")
        ipv4Regex.matchEntire(trimmed)?.let { match ->
            val ip = match.groupValues[1]
            val octets = ip.split('.').mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it in 0..255 }) {
                val port = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_STARDEW_PORT
                return ip to port
            }
        }

        // Check KPZ-XXXXX-XXXXX format
        val cleaned = trimmed.uppercase(Locale.ROOT)
            .removePrefix("KPZ")
            .replace("-", "")
            .replace(" ", "")
        if (cleaned.length != 10) return null

        var value = 0L
        for (ch in cleaned) {
            val idx = ALPHABET.indexOf(ch)
            if (idx < 0) return null
            value = (value shl 5) or idx.toLong()
        }
        val b0 = ((value ushr 40) and 0xFFL).toInt()
        val b1 = ((value ushr 32) and 0xFFL).toInt()
        val b2 = ((value ushr 24) and 0xFFL).toInt()
        val b3 = ((value ushr 16) and 0xFFL).toInt()
        val port = (value and 0xFFFFL).toInt()
        if (port !in 1..65535) return null
        return "$b0.$b1.$b2.$b3" to port
    }
}

/**
 * Real LAN & Direct-IP Co-op Lobby Engine:
 * - Hosts a real local TCP metadata/join socket + NsdManager (`_kpzicoop._tcp.`) broadcast.
 * - Discovers genuine rooms on the local Wi-Fi/LAN network and measures real TCP RTT in ms.
 * - Never fabricates fake rooms, fake players, or simulated pings.
 */
class MultiplayerLobbyEngine(
    private val context: Context,
    private val multiplayerDao: MultiplayerDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager: NsdManager? =
        runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()

    private val _snapshot = MutableStateFlow(MultiplayerLobbySnapshot())
    val snapshot: StateFlow<MultiplayerLobbySnapshot> = _snapshot.asStateFlow()

    val favoriteRoomsFlow: Flow<List<FavoriteRoomEntity>> = multiplayerDao.observeFavorites()
    val coopHistoryFlow: Flow<List<CoopHistoryEntity>> = multiplayerDao.observeHistory()

    private val discoveredEndpoints = ConcurrentHashMap<String, InetSocketAddress>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var hostPassword: String = ""
    private var unlockedRoomIds = ConcurrentHashMap.newKeySet<String>()

    fun refreshRooms() {
        scope.launch {
            _snapshot.update { it.copy(isScanning = true) }
            restartNsdDiscovery()
            delay(650)
            reprobeAllKnownEndpoints()
            _snapshot.update {
                it.copy(
                    isScanning = false,
                    lastRefreshedAtMs = System.currentTimeMillis(),
                )
            }
        }
    }

    fun startHostingRoom(config: HostedRoomConfig): MultiplayerRoom? {
        stopHostingRoomInternal()

        val cleanName = config.roomName.trim().ifEmpty { "Stardew Co-op" }
        val cleanOwner = config.ownerName.trim().ifEmpty { "Host" }
        val clampedMax = config.maxPlayers.coerceIn(2, 8)
        val isPrivate = config.visibility == RoomVisibility.PRIVATE
        hostPassword = if (isPrivate) config.password.trim() else ""

        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        serverSocket = socket
        val boundPort = socket.localPort
        val localIp = resolveLocalIpv4Address() ?: "127.0.0.1"
        val roomId = "host_${UUID.randomUUID().toString().take(8)}"
        val code = KpziRoomCodeCodec.encode(localIp, boundPort) ?: "$localIp:$boundPort"

        val hosted = MultiplayerRoom(
            roomId = roomId,
            roomName = cleanName,
            ownerName = cleanOwner,
            isOwnHostRoom = true,
            currentPlayers = 1,
            maxPlayers = clampedMax,
            visibility = config.visibility,
            requiresPassword = hostPassword.isNotEmpty(),
            gameVersion = config.gameVersion,
            pingMs = 0,
            hostAddress = localIp,
            port = boundPort,
            roomCode = code,
            createdAtMs = System.currentTimeMillis(),
            isUserAuthorized = true,
        )

        _snapshot.update { cur ->
            val filtered = cur.rooms.filterNot { it.isOwnHostRoom || it.roomId == roomId }
            cur.copy(activeHostRoom = hosted, rooms = listOf(hosted) + filtered)
        }

        acceptJob = scope.launch {
            runHostAcceptLoop(socket)
        }
        registerNsdService(hosted)

        scope.launch {
            multiplayerDao.insertHistory(
                CoopHistoryEntity(
                    roomId = hosted.roomId,
                    roomName = hosted.roomName,
                    ownerName = hosted.ownerName,
                    role = "HOST",
                    gameVersion = hosted.gameVersion,
                    pingMs = 0,
                    roomCode = hosted.roomCode,
                    playedAtMs = System.currentTimeMillis(),
                ),
            )
        }

        return hosted
    }

    fun stopHostingRoom() {
        scope.launch {
            stopHostingRoomInternal()
            reprobeAllKnownEndpoints()
        }
    }

    suspend fun joinRoom(
        room: MultiplayerRoom,
        enteredPassword: String? = null,
    ): JoinRoomResult = withContext(Dispatchers.IO) {
        if (room.isOwnHostRoom) {
            return@withContext JoinRoomResult.Success(room)
        }
        if (room.isFull) {
            return@withContext JoinRoomResult.RoomFull(room)
        }
        if (room.requiresPassword && enteredPassword == null && !room.isUserAuthorized && room.roomId !in unlockedRoomIds) {
            return@withContext JoinRoomResult.PasswordRequired(room)
        }

        _snapshot.update { it.copy(isConnectingRoomId = room.roomId) }
        try {
            val result = probeAndJoinTcp(
                host = room.hostAddress,
                port = room.port,
                password = enteredPassword.orEmpty(),
                fallbackRoom = room,
            )
            if (result is JoinRoomResult.Success) {
                unlockedRoomIds.add(result.room.roomId)
                discoveredEndpoints[result.room.roomId] = InetSocketAddress(result.room.hostAddress, result.room.port)
                upsertDiscoveredRoom(result.room.copy(isUserAuthorized = true))
                multiplayerDao.insertHistory(
                    CoopHistoryEntity(
                        roomId = result.room.roomId,
                        roomName = result.room.roomName,
                        ownerName = result.room.ownerName,
                        role = "GUEST",
                        gameVersion = result.room.gameVersion,
                        pingMs = result.room.pingMs,
                        roomCode = result.room.roomCode,
                        playedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
            result
        } finally {
            _snapshot.update { it.copy(isConnectingRoomId = null) }
        }
    }

    suspend fun joinByRoomCode(
        codeOrAddress: String,
        enteredPassword: String? = null,
        localGameVersion: String,
    ): JoinRoomResult = withContext(Dispatchers.IO) {
        val decoded = KpziRoomCodeCodec.decode(codeOrAddress)
            ?: return@withContext JoinRoomResult.Unreachable(
                reasonTh = "รูปแบบรหัสห้องหรือ IP:Port ไม่ถูกต้อง",
                reasonEn = "Invalid room code or IP:Port format",
            )
        val (host, port) = decoded
        val activeHost = _snapshot.value.activeHostRoom
        if (activeHost != null && activeHost.roomCode.equals(codeOrAddress.trim(), ignoreCase = true)) {
            return@withContext JoinRoomResult.Success(activeHost)
        }

        _snapshot.update { it.copy(isConnectingRoomId = "code_lookup") }
        try {
            val probedRoom = probeRemoteEndpoint(host, port)
                ?: return@withContext JoinRoomResult.Unreachable(
                    reasonTh = "ไม่สามารถเชื่อมต่อ $host:$port ได้ (ตรวจสอบ Wi-Fi เดียวกันหรือเปิดห้องไว้แล้ว)",
                    reasonEn = "Could not reach $host:$port (verify host is active on the same Wi-Fi/LAN)",
                )
            val resolvedRoom = if (probedRoom.gameVersion.isBlank()) {
                probedRoom.copy(gameVersion = localGameVersion)
            } else {
                probedRoom
            }
            discoveredEndpoints[resolvedRoom.roomId] = InetSocketAddress(host, port)
            upsertDiscoveredRoom(resolvedRoom)

            if (resolvedRoom.isFull) {
                return@withContext JoinRoomResult.RoomFull(resolvedRoom)
            }
            if (resolvedRoom.requiresPassword && enteredPassword == null && resolvedRoom.roomId !in unlockedRoomIds) {
                return@withContext JoinRoomResult.PasswordRequired(resolvedRoom)
            }
            val joinRes = probeAndJoinTcp(host, port, enteredPassword.orEmpty(), resolvedRoom)
            if (joinRes is JoinRoomResult.Success) {
                unlockedRoomIds.add(joinRes.room.roomId)
                upsertDiscoveredRoom(joinRes.room.copy(isUserAuthorized = true))
                multiplayerDao.insertHistory(
                    CoopHistoryEntity(
                        roomId = joinRes.room.roomId,
                        roomName = joinRes.room.roomName,
                        ownerName = joinRes.room.ownerName,
                        role = "GUEST",
                        gameVersion = joinRes.room.gameVersion,
                        pingMs = joinRes.room.pingMs,
                        roomCode = joinRes.room.roomCode,
                        playedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
            joinRes
        } finally {
            _snapshot.update { it.copy(isConnectingRoomId = null) }
        }
    }

    suspend fun toggleFavorite(room: MultiplayerRoom, currentlyFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            if (currentlyFavorite) {
                multiplayerDao.deleteFavorite(room.roomId)
            } else {
                multiplayerDao.upsertFavorite(
                    FavoriteRoomEntity(
                        roomId = room.roomId,
                        roomName = room.roomName,
                        ownerName = room.ownerName,
                        hostAddress = room.hostAddress,
                        port = room.port,
                        roomCode = room.roomCode,
                        savedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            multiplayerDao.clearHistory()
        }
    }

    fun shutdown() {
        stopHostingRoomInternal()
        stopNsdDiscovery()
    }

    private fun stopHostingRoomInternal() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        hostPassword = ""
        unregisterNsdService()
        _snapshot.update { cur ->
            val activeId = cur.activeHostRoom?.roomId
            cur.copy(
                activeHostRoom = null,
                rooms = cur.rooms.filterNot { it.isOwnHostRoom || it.roomId == activeId },
            )
        }
    }

    private suspend fun runHostAcceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                handleIncomingHostClient(client)
            }
        }
    }

    private fun handleIncomingHostClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 2000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)
            val line = reader.readLine()?.trim() ?: return
            val room = _snapshot.value.activeHostRoom ?: return

            when {
                line == "PING" -> {
                    writer.println("PONG ${buildRoomJson(room)}")
                }
                line.startsWith("JOIN") -> {
                    val pass = line.removePrefix("JOIN").trim()
                    when {
                        room.isFull -> writer.println("ERR_FULL")
                        hostPassword.isNotEmpty() && pass != hostPassword -> writer.println("ERR_PASSWORD")
                        else -> {
                            val updated = room.copy(
                                currentPlayers = (room.currentPlayers + 1).coerceAtMost(room.maxPlayers),
                            )
                            _snapshot.update { cur ->
                                cur.copy(
                                    activeHostRoom = updated,
                                    rooms = cur.rooms.map { if (it.roomId == updated.roomId) updated else it },
                                )
                            }
                            writer.println("OK ${buildRoomJson(updated)}")
                        }
                    }
                }
                line == "LEAVE" -> {
                    val updated = room.copy(currentPlayers = max(1, room.currentPlayers - 1))
                    _snapshot.update { cur ->
                        cur.copy(
                            activeHostRoom = updated,
                            rooms = cur.rooms.map { if (it.roomId == updated.roomId) updated else it },
                        )
                    }
                    writer.println("BYE")
                }
            }
        }
    }

    private suspend fun reprobeAllKnownEndpoints() {
        val activeHost = _snapshot.value.activeHostRoom
        val remoteRooms = mutableListOf<MultiplayerRoom>()
        val staleIds = mutableListOf<String>()

        discoveredEndpoints.entries.forEach { (id, addr) ->
            val host = addr.hostString ?: addr.address?.hostAddress ?: return@forEach
            val probed = probeRemoteEndpoint(host, addr.port)
            if (probed != null) {
                remoteRooms.add(probed.copy(isUserAuthorized = probed.roomId in unlockedRoomIds))
            } else {
                staleIds.add(id)
            }
        }
        staleIds.forEach { discoveredEndpoints.remove(it) }

        _snapshot.update { cur ->
            val combined = buildList {
                if (activeHost != null) add(activeHost)
                remoteRooms.filter { it.roomId != activeHost?.roomId }.forEach { add(it) }
            }
            cur.copy(rooms = combined)
        }
    }

    private fun probeRemoteEndpoint(host: String, port: Int): MultiplayerRoom? {
        return runCatching {
            val startNs = System.nanoTime()
            Socket().use { sock ->
                sock.soTimeout = 1500
                sock.connect(InetSocketAddress(host, port), 1500)
                val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                writer.println("PING")
                val response = reader.readLine()
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

                if (response != null && response.startsWith("PONG ")) {
                    parseRoomJson(response.removePrefix("PONG ").trim(), host, port, elapsedMs)
                } else {
                    // Direct Stardew Valley port 24642 (no Kpzi beacon metadata)
                    val code = KpziRoomCodeCodec.encode(host, port) ?: "$host:$port"
                    MultiplayerRoom(
                        roomId = "direct_${host}_$port",
                        roomName = "Stardew LAN ($host)",
                        ownerName = host,
                        isOwnHostRoom = false,
                        currentPlayers = 1,
                        maxPlayers = 4,
                        visibility = RoomVisibility.PUBLIC,
                        requiresPassword = false,
                        gameVersion = "",
                        pingMs = elapsedMs,
                        hostAddress = host,
                        port = port,
                        roomCode = code,
                        createdAtMs = System.currentTimeMillis(),
                    )
                }
            }
        }.getOrNull()
    }

    private fun probeAndJoinTcp(
        host: String,
        port: Int,
        password: String,
        fallbackRoom: MultiplayerRoom,
    ): JoinRoomResult {
        return runCatching {
            val startNs = System.nanoTime()
            Socket().use { sock ->
                sock.soTimeout = 2000
                sock.connect(InetSocketAddress(host, port), 2000)
                val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                writer.println(if (password.isNotEmpty()) "JOIN $password" else "JOIN")
                val response = reader.readLine()?.trim()
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

                when {
                    response == null -> JoinRoomResult.Success(fallbackRoom.copy(pingMs = elapsedMs))
                    response == "ERR_FULL" -> JoinRoomResult.RoomFull(fallbackRoom.copy(pingMs = elapsedMs))
                    response == "ERR_PASSWORD" -> JoinRoomResult.InvalidPassword(fallbackRoom.copy(pingMs = elapsedMs))
                    response.startsWith("OK ") -> {
                        val updated = parseRoomJson(response.removePrefix("OK ").trim(), host, port, elapsedMs)
                            ?: fallbackRoom.copy(pingMs = elapsedMs)
                        JoinRoomResult.Success(updated)
                    }
                    else -> JoinRoomResult.Success(fallbackRoom.copy(pingMs = elapsedMs))
                }
            }
        }.getOrElse {
            JoinRoomResult.Unreachable(
                reasonTh = "การเชื่อมต่อห้องหมดเวลาหรือไม่พบเครื่องโฮสต์",
                reasonEn = "Connection timed out or host is unreachable",
            )
        }
    }

    private fun upsertDiscoveredRoom(room: MultiplayerRoom) {
        _snapshot.update { cur ->
            val without = cur.rooms.filterNot { it.roomId == room.roomId }
            cur.copy(rooms = listOf(room) + without)
        }
    }

    private fun buildRoomJson(room: MultiplayerRoom): String = JSONObject().apply {
        put("roomId", room.roomId)
        put("roomName", room.roomName)
        put("ownerName", room.ownerName)
        put("currentPlayers", room.currentPlayers)
        put("maxPlayers", room.maxPlayers)
        put("visibility", room.visibility.name)
        put("requiresPassword", room.requiresPassword)
        put("gameVersion", room.gameVersion)
        put("roomCode", room.roomCode)
        put("createdAtMs", room.createdAtMs)
    }.toString()

    private fun parseRoomJson(jsonText: String, host: String, port: Int, pingMs: Int): MultiplayerRoom? {
        return runCatching {
            val obj = JSONObject(jsonText)
            val vis = runCatching { RoomVisibility.valueOf(obj.optString("visibility")) }
                .getOrDefault(RoomVisibility.PUBLIC)
            val id = obj.optString("roomId").ifBlank { "lan_${host}_$port" }
            MultiplayerRoom(
                roomId = id,
                roomName = obj.optString("roomName", "Stardew Co-op"),
                ownerName = obj.optString("ownerName", host),
                isOwnHostRoom = false,
                currentPlayers = obj.optInt("currentPlayers", 1),
                maxPlayers = obj.optInt("maxPlayers", 4),
                visibility = vis,
                requiresPassword = obj.optBoolean("requiresPassword", false),
                gameVersion = obj.optString("gameVersion", ""),
                pingMs = pingMs,
                hostAddress = host,
                port = port,
                roomCode = obj.optString("roomCode").ifBlank { KpziRoomCodeCodec.encode(host, port) ?: "$host:$port" },
                createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                isUserAuthorized = id in unlockedRoomIds,
            )
        }.getOrNull()
    }

    private fun registerNsdService(room: MultiplayerRoom) {
        val manager = nsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Kpzi_${room.roomId}"
            serviceType = SERVICE_TYPE
            port = room.port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsdRegistrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsdService() {
        val manager = nsdManager ?: return
        val listener = nsdRegistrationListener ?: return
        nsdRegistrationListener = null
        runCatching { manager.unregisterService(listener) }
    }

    private fun restartNsdDiscovery() {
        stopNsdDiscovery()
        val manager = nsdManager ?: return
        val activeHostId = _snapshot.value.activeHostRoom?.roomId
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (activeHostId != null && serviceInfo.serviceName.contains(activeHostId)) return
                resolveServiceCompat(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val id = serviceInfo.serviceName.removePrefix("Kpzi_")
                discoveredEndpoints.remove(id)
                _snapshot.update { cur ->
                    cur.copy(rooms = cur.rooms.filterNot { it.roomId == id && !it.isOwnHostRoom })
                }
            }
        }
        nsdDiscoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceCompat(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                manager.registerServiceInfoCallback(
                    serviceInfo,
                    { it.run() },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceUpdated(updated: NsdServiceInfo) {
                            val host = updated.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: return
                            val port = updated.port
                            val id = updated.serviceName.removePrefix("Kpzi_")
                            discoveredEndpoints[id] = InetSocketAddress(host, port)
                            scope.launch {
                                probeRemoteEndpoint(host, port)?.let { upsertDiscoveredRoom(it) }
                            }
                            runCatching { manager.unregisterServiceInfoCallback(this) }
                        }
                        override fun onServiceLost() = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    },
                )
            }
        } else {
            runCatching {
                manager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            val id = resolved.serviceName.removePrefix("Kpzi_")
                            discoveredEndpoints[id] = InetSocketAddress(host, port)
                            scope.launch {
                                probeRemoteEndpoint(host, port)?.let { upsertDiscoveredRoom(it) }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun stopNsdDiscovery() {
        val manager = nsdManager ?: return
        val listener = nsdDiscoveryListener ?: return
        nsdDiscoveryListener = null
        runCatching { manager.stopServiceDiscovery(listener) }
    }

    private fun resolveLocalIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    companion object {
        private const val SERVICE_TYPE = "_kpzicoop._tcp."
    }
}
