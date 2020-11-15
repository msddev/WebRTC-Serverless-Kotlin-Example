package cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver

import android.util.Log
import cz.sazel.android.serverlesswebrtcandroid.JitsiCallback
import cz.sazel.android.serverlesswebrtcandroid.console.IConsole
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

class JingleServer : WebSocketClient {

    private var xmlParser: MyXmlParser = MyXmlParser()
    private lateinit var jitsiCallback: JitsiCallback

    constructor(serverUri: URI?, draft: Draft?, jitsiCallback: JitsiCallback) : super(
        serverUri,
        draft
    ) {
        this.jitsiCallback = jitsiCallback
    }

    constructor(serverURI: URI?) : super(serverURI)

    override fun onOpen(handshakedata: ServerHandshake) {
        println("new connection opened")
        sendMessage(JingleMessages.getXmlMessage(1))
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        println("closed with exit code $code additional info: $reason")
    }

    override fun onMessage(message: String) {
        Log.d("Send_Received_Messages", "received_message : $message")

        xmlParser.parseXML(message, object : XmlParserListener {
            override fun callBack(message: String) {
                sendMessage(message)
            }

            override fun receiveTurns(turns: MutableList<JistiServiceModel>) {
                jitsiCallback.receiveTurn(turns)
            }
        })
    }

    override fun onMessage(message: ByteBuffer) {
        println("received ByteBuffer")
    }

    override fun onError(ex: Exception) {
        System.err.println("an error occurred:$ex")
    }

    private fun sendMessage(message: String) {
        Log.d("Send_Received_Messages", "send_message : $message")

        send(message)
    }
}

interface XmlParserListener {
    fun callBack(message: String)
    fun receiveTurns(turns: MutableList<JistiServiceModel>)
}