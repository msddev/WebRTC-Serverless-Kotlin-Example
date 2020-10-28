package cz.sazel.android.serverlesswebrtcandroid.webrtc

import android.content.Context
import cz.sazel.android.serverlesswebrtcandroid.console.IConsole
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*

/**
 * This class handles all around WebRTC peer connections.
 */
class ServerlessRTCClient(val console: IConsole, val context: Context, val listener: IStateChangeListener) {

    lateinit var pc: PeerConnection
    private var pcInitialized: Boolean = false

    var channel: DataChannel? = null

    /**
     * List of servers that will be used to establish the direct connection, STUN/TURN should be supported.
     */

    private fun getIceServer(): List<PeerConnection.IceServer> {

        val iceServerStunBuilder = PeerConnection.IceServer.builder("stun://stun.l.google.com:19302")
        iceServerStunBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE)

        val iceServerTurnBuilder = PeerConnection.IceServer.builder("turn:meet-jit-si-turnrelay.jitsi.net:443?transport=tcp")
        iceServerTurnBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE)
        iceServerTurnBuilder.setUsername("1603956123")
        iceServerTurnBuilder.setPassword("ZglMMZtl1u/lvqVbTz3HDpTFwso=")

        val iceServers: MutableList<PeerConnection.IceServer> = ArrayList()
        iceServers.add(iceServerStunBuilder.createIceServer())
        iceServers.add(iceServerTurnBuilder.createIceServer())
        return iceServers
    }

    enum class State {
        /**
         * Initialization in progress.
         */
        INITIALIZING,

        /**
         * App is waiting for offer, fill in the offer into the edit text.
         */
        WAITING_FOR_OFFER,

        /**
         * App is creating the offer.
         */
        CREATING_OFFER,

        /**
         * App is creating answer to offer.
         */
        CREATING_ANSWER,

        /**
         * App created the offer and is now waiting for answer
         */
        WAITING_FOR_ANSWER,

        /**
         * Waiting for establishing the connection.
         */
        WAITING_TO_CONNECT,

        /**
         * Connection was established. You can chat now.
         */
        CHAT_ESTABLISHED,

        /**
         * Connection is terminated chat ended.
         */
        CHAT_ENDED
    }

    lateinit var pcf: PeerConnectionFactory
    val pcConstraints = object : MediaConstraints() {
        init {
            optional.add(KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
    }

    var state: State = State.INITIALIZING
        private set(value) {
            field = value
            listener.onStateChanged(value)
        }


    interface IStateChangeListener {
        /**
         * Called when status of client is changed.
         */
        fun onStateChanged(state: State)
    }

    abstract inner class DefaultObserver : PeerConnection.Observer {

        override fun onDataChannel(p0: DataChannel?) {
            console.d("data channel ${p0?.label()} established")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            console.d("ice connection receiving change:{$p0}")
        }

        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            console.d("ice connection state change:${p0?.name}")
            if (p0 == PeerConnection.IceConnectionState.DISCONNECTED) {
                console.d("closing channel")
                channel?.close()
            }
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
            console.d("ice gathering state change:${p0?.name}")
        }

        override fun onAddStream(p0: MediaStream?) {

        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
            console.d("signaling state change:${p0?.name}")
        }

        override fun onRemoveStream(p0: MediaStream?) {

        }

        override fun onRenegotiationNeeded() {
            console.d("renegotiation needed")
        }
    }

    open inner class DefaultSdpObserver : SdpObserver {

        override fun onCreateSuccess(p0: SessionDescription?) {

        }

        override fun onCreateFailure(p0: String?) {
            console.e("failed to create offer:$p0")
        }

        override fun onSetFailure(p0: String?) {
            console.e("set failure:$p0")
        }

        override fun onSetSuccess() {
            console.i("set success")
        }

    }


    private val UTF_8 = Charset.forName("UTF-8")

    open inner class DefaultDataChannelObserver(val channel: DataChannel) : DataChannel.Observer {


        //TODO I'm not sure if this would handle really long messages
        override fun onMessage(p0: DataChannel.Buffer?) {
            val buf = p0?.data
            if (buf != null) {
                val byteArray = ByteArray(buf.remaining())
                buf.get(byteArray)
                val received = kotlin.text.String(byteArray, UTF_8)
                try {
                    val message = JSONObject(received).getString(JSON_MESSAGE)
                    console.bluef("&gt;$message")
                } catch (e: JSONException) {
                    console.redf("Malformed message received")
                }


            }
        }

        override fun onBufferedAmountChange(p0: Long) {
            console.d("channel buffered amount change:{$p0}")
        }

        override fun onStateChange() {
            console.d("Channel state changed:${channel.state()?.name}}")
            if (channel.state() == DataChannel.State.OPEN) {
                state = State.CHAT_ESTABLISHED
                console.bluef("Chat established.")
            } else {
                state = State.CHAT_ENDED
                console.redf("Chat ended.")
            }
        }
    }

    private val JSON_TYPE = "type"
    private val JSON_MESSAGE = "message"
    private val JSON_SDP = "sdp"


    fun sessionDescriptionToJSON(sessDesc: SessionDescription): JSONObject {
        val json = JSONObject()
        json.put(JSON_TYPE, sessDesc.type.canonicalForm())
        json.put(JSON_SDP, sessDesc.description)
        return json
    }


    /**
     * Wait for an offer to be entered by user.
     */
    fun waitForOffer() {
        state = State.WAITING_FOR_OFFER
    }


    /**
     * Process offer that was entered by user (this is called getOffer() in JavaScript example)
     */
    fun processOffer(sdpJSON: String) {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.CREATING_ANSWER
            if (type != null && sdp != null && type == "offer") {
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pcInitialized = true

                val iceServers = getIceServer()

                val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
                rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY

                pc = pcf.createPeerConnection(rtcConfig, object : DefaultObserver() {
                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                        p0?.forEach { console.d("ice candidatesremoved: {${it.serverUrl}") }
                    }

                    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                        console.d("onAddTrack")
                    }

                    override fun onIceCandidate(p0: IceCandidate?) {
                        console.d("ice candidate:{${p0?.sdp}}")
                    }

                    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                        super.onIceGatheringChange(p0)
                        //ICE gathering complete, we should have answer now
                        if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                            doShowAnswer(pc.localDescription)
                            state = State.WAITING_TO_CONNECT
                        }
                    }

                    override fun onDataChannel(p0: DataChannel?) {
                        super.onDataChannel(p0)
                        channel = p0
                        p0?.registerObserver(DefaultDataChannelObserver(p0))
                    }


                })!!

                //we have remote offer, let's create answer for that
                pc.setRemoteDescription(object : DefaultSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        console.d("Remote description set.")
                        pc.createAnswer(object : DefaultSdpObserver() {
                            override fun onCreateSuccess(p0: SessionDescription?) {
                                //answer is ready, set it
                                console.d("Local description set.")
                                pc.setLocalDescription(DefaultSdpObserver(), p0)
                            }
                        }, pcConstraints)
                    }
                }, offer)
            } else {
                console.redf("Invalid or unsupported offer.")
                state = State.WAITING_FOR_OFFER
            }
        } catch (e: JSONException) {
            console.redf("bad json")
            state = State.WAITING_FOR_OFFER
        }
    }


    /**
     * Process answer that was entered by user (this is called getAnswer() in JavaScript example)
     */
    fun processAnswer(sdpJSON: String) {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.WAITING_TO_CONNECT
            if (type != null && sdp != null && type == "answer") {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                pc.setRemoteDescription(DefaultSdpObserver(), answer)
            } else {
                console.redf("Invalid or unsupported answer.")
                state = State.WAITING_FOR_ANSWER
            }
        } catch (e: JSONException) {
            console.redf("bad json")
            state = State.WAITING_FOR_ANSWER
        }
    }

    private fun doShowAnswer(sdp: SessionDescription) {
        console.printf("Here is your answer:")
        console.greenf("${sessionDescriptionToJSON(sdp)}")
    }

    /**
     * App creates the offer.
     */
    fun makeOffer() {
        state = State.CREATING_OFFER
        pcInitialized = true

        val rtcConfig = PeerConnection.RTCConfiguration(getIceServer())
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY

        pc = pcf.createPeerConnection(rtcConfig, object : DefaultObserver() {
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onIceCandidate(p0: IceCandidate?) {
                console.d("ice candidate:{${p0?.sdp}}")
            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                super.onIceGatheringChange(p0)
                if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                    console.printf("Your offer is:")
                    console.greenf("${sessionDescriptionToJSON(pc.localDescription)}")
                    state = State.WAITING_FOR_ANSWER
                }
            }
        })!!
        makeDataChannel()
        pc.createOffer(object : DefaultSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 != null) {
                    console.d("offer updated")
                    pc.setLocalDescription(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                        }
                    }, p0)
                }
            }
        }, pcConstraints)
    }

    /**
     * Sends message to other party.
     */
    fun sendMessage(message: String) {
        if (channel == null || state == State.CHAT_ESTABLISHED) {
            val sendJSON = JSONObject()
            sendJSON.put(JSON_MESSAGE, message)
            val buf = ByteBuffer.wrap(sendJSON.toString().toByteArray(UTF_8))
            channel?.send(DataChannel.Buffer(buf, false))
        } else {
            console.redf("Error. Chat is not established.")
        }
    }

    /**
     * Creates data channel for use when offer is created on this machine.
     */
    fun makeDataChannel() {
        val init = DataChannel.Init()
        channel = pc.createDataChannel("test", init)
        channel!!.registerObserver(DefaultDataChannelObserver(channel!!))
    }

    /**
     * Call this before using anything else from PeerConnection.
     */
    fun init() {
        val initializeOptions = PeerConnectionFactory.InitializationOptions.builder(context).setEnableVideoHwAcceleration(false).setEnableInternalTracer(false).createInitializationOptions()
        PeerConnectionFactory.initialize(initializeOptions)
        val options = PeerConnectionFactory.Options()
        pcf = PeerConnectionFactory.builder().setOptions(options).createPeerConnectionFactory()
        state = State.INITIALIZING
    }


    /**
     * Clean up some resources.
     */
    fun destroy() {
        channel?.close()
        if (pcInitialized) {
            pc.close()
        }
    }
}