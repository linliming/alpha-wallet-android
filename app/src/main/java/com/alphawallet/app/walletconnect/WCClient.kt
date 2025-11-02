
import com.alphawallet.app.C
import com.alphawallet.app.walletconnect.WCSession
import com.alphawallet.app.walletconnect.entity.InvalidJsonRpcParamsException
import com.alphawallet.app.walletconnect.entity.JsonRpcError
import com.alphawallet.app.walletconnect.entity.JsonRpcErrorResponse
import com.alphawallet.app.walletconnect.entity.JsonRpcRequest
import com.alphawallet.app.walletconnect.entity.MessageType
import com.alphawallet.app.walletconnect.entity.WCEncryptionPayload
import com.alphawallet.app.walletconnect.entity.WCEthereumSignMessage
import com.alphawallet.app.walletconnect.entity.WCEthereumTransaction
import com.alphawallet.app.walletconnect.entity.WCMethod
import com.alphawallet.app.walletconnect.entity.WCPeerMeta
import com.alphawallet.app.walletconnect.entity.WCSessionUpdate
import com.alphawallet.app.walletconnect.entity.WCSocketMessage
import com.alphawallet.app.walletconnect.entity.ethTransactionSerializer
import com.alphawallet.app.walletconnect.util.WCCipher
import com.alphawallet.app.walletconnect.util.toByteArray
import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonSyntaxException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface WCClientListener {
    fun onSessionRequest(id: Long, peer: WCPeerMeta)
    fun onEthSign(id: Long, message: WCEthereumSignMessage)
    fun onEthSignTransaction(id: Long, transaction: WCEthereumTransaction)
    fun onEthSendTransaction(id: Long, transaction: WCEthereumTransaction)
    fun onCustomRequest(id: Long, payload: String)
    fun onGetAccounts(id: Long)
    fun onWCOpen(peerId: String)
    fun onPong(peerId: String)
    fun onSwitchEthereumChain(requestId: Long, chainId: Long)
    fun onAddEthereumChain(requestId: Long, chainObj: WalletAddEthereumChainObject)
    fun onDisconnect(code: Int, reason: String)
    fun onFailure(t: Throwable)
}

open class WCClient : WebSocketListener() {

    private val TAG = WCClient::class.java.simpleName

    private var socket: WebSocket? = null
    private val nextId = AtomicLong(0)

    private val listeners: MutableSet<WebSocketListener> = mutableSetOf()
    var listener: WCClientListener? = null

    var session: WCSession? = null
        private set

    var peerMeta: WCPeerMeta? = null
        private set

    var peerId: String? = null
        private set

    var remotePeerId: String? = null
        private set

    var isConnected: Boolean = false
        private set

    fun sessionId(): String? = session?.topic

    var accounts: List<String>? = null
        private set

    private var chainId: String? = null

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(C.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .connectTimeout(C.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(C.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .pingInterval(C.PING_INTERVAL, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.d("<< websocket opened >>")
        isConnected = true
        listeners.forEach { it.onOpen(webSocket, response) }

        // Safely handle session and peerId
        val currentSession = this.session
        val currentPeerId = this.peerId

        if (currentSession == null || currentPeerId == null) {
            Timber.e("onOpen called but session or peerId is null. Disconnecting.")
            disconnect()
            return
        }

        // The Session.topic channel is used to listen session request messages only.
        subscribe(currentSession.topic)
        // The peerId channel is used to listen to all messages sent to this httpClient.
        subscribe(currentPeerId)

        listener?.onWCOpen(currentPeerId)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (text.startsWith("Missing or invalid")) {
            return
        }
        var decrypted: String? = null
        try {
            Timber.d("<== message $text")
            decrypted = decryptMessage(text)
            if (decrypted != null) {
                Timber.d("<== decrypted $decrypted")
                handleMessage(decrypted)
            } else {
                Timber.w("Failed to decrypt message, session key might be missing.")
            }
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Error parsing JSON")
        } catch (e: Exception) {
            listener?.onFailure(e)
        } finally {
            listeners.forEach { it.onMessage(webSocket, decrypted ?: text) }
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e(t, "<< websocket failure >>")
        listener?.onFailure(t)
        listeners.forEach { it.onFailure(webSocket, t, response) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Timber.d("<< websocket closed >>")
        listeners.forEach { it.onClosed(webSocket, code, reason) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Timber.d("<== Received: $bytes")
        listeners.forEach { it.onMessage(webSocket, bytes) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.d("<< closing socket >>")
        listener?.onDisconnect(code, reason)
        listeners.forEach { it.onClosing(webSocket, code, reason) }
    }

    fun connect(
        session: WCSession,
        peerMeta: WCPeerMeta,
        peerId: String = UUID.randomUUID().toString(),
        remotePeerId: String? = null
    ) {
        if (this.session != null && this.session?.topic != session.topic) {
            killSession()
        }

        this.session = session
        this.peerMeta = peerMeta
        this.peerId = peerId
        this.remotePeerId = remotePeerId

        val request = Request.Builder()
            .url(session.bridge)
            .build()

        socket = httpClient.newWebSocket(request, this)
    }

    fun updateSession(
        accounts: List<String>? = null,
        chainId: Long? = null,
        approved: Boolean = true
    ): Boolean {
        // Use a reliable, unique ID for requests
        val requestId = System.currentTimeMillis() + nextId.incrementAndGet()
        val request = JsonRpcRequest(
            id = requestId,
            method = WCMethod.SESSION_UPDATE,
            params = listOf(
                WCSessionUpdate(
                    approved = approved,
                    chainId = this.chainId?.toLongOrNull() ?: chainId,
                    accounts = accounts
                )
            )
        )
        return encryptAndSend(gson.toJson(request))
    }

    fun killSession(): Boolean {
        updateSession(approved = false)
        return disconnect()
    }

    private fun decryptMessage(text: String): String? {
        val currentSession = this.session ?: return null
        val message = gson.fromJson<WCSocketMessage>(text)
        val encrypted = gson.fromJson<WCEncryptionPayload>(message.payload)
        return String(WCCipher.decrypt(encrypted, currentSession.key.toByteArray()), Charsets.UTF_8)
    }

    private fun invalidParams(id: Long): Boolean {
        val response = JsonRpcErrorResponse(
            id = id,
            error = JsonRpcError.invalidParams(
                message = "Invalid parameters"
            )
        )
        return encryptAndSend(gson.toJson(response))
    }

    private fun handleMessage(payload: String) {
        try {
            val request = gson.fromJson<JsonRpcRequest<JsonArray>>(
                payload,
                typeToken<JsonRpcRequest<JsonArray>>()
            )
            val method = request.method
            if (method != null) {
                handleRequest(request)
            } else {
                listener?.onCustomRequest(request.id, payload)
            }
        } catch (e: InvalidJsonRpcParamsException) {
            Timber.d("handleMessage: InvalidJsonRpcParamsException")
            invalidParams(e.requestId)
        }
    }

    private fun handleRequest(request: JsonRpcRequest<JsonArray>) {
        Timber.tag(TAG).d("handleRequest: %s", request.toString())
        // TODO: Replace with listener calls based on request.method
    }

    private fun subscribe(topic: String): Boolean {
        val message = WCSocketMessage(
            topic = topic,
            type = MessageType.SUB,
            payload = ""
        )
        val json = gson.toJson(message)
        Timber.d("==> Subscribe: $json")

        return socket?.send(json) ?: false
    }

    private fun encryptAndSend(result: String): Boolean {
        Timber.d("==> message $result")
        val currentSession = this.session ?: return false

        val payload = gson.toJson(
            WCCipher.encrypt(
                result.toByteArray(Charsets.UTF_8),
                currentSession.key.toByteArray()
            )
        )
        val message = WCSocketMessage(
            topic = remotePeerId ?: currentSession.topic,
            type = MessageType.PUB,
            payload = payload
        )

        val rpId = remotePeerId ?: currentSession.topic
        Timber.d("E&Send: $rpId")

        val json = gson.toJson(message)
        Timber.d("==> encrypted $json")
        return socket?.send(json) ?: false
    }

    fun disconnect(): Boolean {
        return socket?.close(1000, null) ?: false
    }

    companion object {
        private val gson: Gson by lazy {
            GsonBuilder()
                .serializeNulls()
                .registerTypeAdapter(ethTransactionSerializer)
                .create()
        }
    }
}
