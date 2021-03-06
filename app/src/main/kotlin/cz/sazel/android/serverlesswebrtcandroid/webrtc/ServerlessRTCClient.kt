package cz.sazel.android.serverlesswebrtcandroid.webrtc

import android.content.Context
import android.util.Log
import cz.sazel.android.serverlesswebrtcandroid.console.IConsole
import cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver.JistiServiceModel
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*

class ServerlessRTCClient(
    private val turns: MutableList<JistiServiceModel>,
    private val console: IConsole,
    private val context: Context,
    private val rootEglBase: EglBase,
    private val listener: IStateChangeListener,
    private val countDownTimer: ICountDownTimer
) {

    private lateinit var remoteVideoTrack: VideoTrack
    private lateinit var surfaceViewLocal: SurfaceViewRenderer
    private lateinit var surfaceViewRemote: SurfaceViewRenderer

    private var peerConnection: PeerConnection? = null
    private var pcInitialized: Boolean = false

    private val JSON_TYPE = "type"
    private val JSON_SDP = "sdp"

    private var videoCapturer: VideoCapturer? = null
    private lateinit var audioConstraints: MediaConstraints
    private var videoSource: VideoSource? = null
    private lateinit var videoTrackFromCamera: VideoTrack
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private var iceServers: MutableList<PeerConnection.IceServer> = arrayListOf()

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val sdpMediaConstraints = object : MediaConstraints() {
        init {
            mandatory.add(KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(KeyValuePair("OfferToReceiveVideo", "true"))
            optional.add(KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
    }

    var state: State = State.INITIALIZING
        set(value) {
            field = value
            listener.onStateChanged(value)
        }

    /**
     * initialize SurfaceViews for local and remote
     */
    fun initializeSurfaceViews(
        surfaceViewLocal: SurfaceViewRenderer,
        surfaceViewRemote: SurfaceViewRenderer
    ) {
        this.surfaceViewLocal = surfaceViewLocal
        this.surfaceViewRemote = surfaceViewRemote

        this.surfaceViewLocal.init(rootEglBase.eglBaseContext, null)
        this.surfaceViewLocal.setEnableHardwareScaler(true)
        this.surfaceViewLocal.setMirror(true)

        this.surfaceViewRemote.init(rootEglBase.eglBaseContext, null)
        this.surfaceViewRemote.setEnableHardwareScaler(true)
        this.surfaceViewRemote.setMirror(true)
    }

    fun initializePeerConnections() {
        peerConnection = createPeerConnection(peerConnectionFactory)
    }

    /**
     * Call this before using anything else from PeerConnection.
     */
    fun initializePeerConnectionFactory() {
        val initializationOptions =
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()

        PeerConnectionFactory.initialize(initializationOptions)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
            ).createPeerConnectionFactory()

        state = State.INITIALIZING
        Log.d(TAG, "Peer connection factory created.")
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(getIceServer())
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY
        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                console.d("signaling state change:${signalingState.name}")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                console.d("ice connection state change:${iceConnectionState.name}")
                if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    console.d("closing channel")
                }
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                console.d("ice connection receiving change:{$b}")
            }

            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                console.d("ice gathering state change:${iceGatheringState.name}")

                if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                    peerConnection?.localDescription?.let {
                        if (it.type == SessionDescription.Type.OFFER) {
                            console.printf("waiting for register offer(60 second)...")
                            countDownTimer.startTimer("${sessionDescriptionToJSON(it)}")
                        } else if (it.type == SessionDescription.Type.ANSWER) {
                            //ICE gathering complete, we should have answer now
                            doShowAnswer(it)
                            state = State.WAITING_TO_CONNECT
                        }
                    }
                }
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                console.d("ice candidate:{${iceCandidate.sdp}}")
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                iceCandidates.forEach { console.d("ice candidatesremoved: {${it.serverUrl}") }
            }

            override fun onAddStream(mediaStream: MediaStream) {
                console.d("Add Stream: " + mediaStream.videoTracks.size)

                if (mediaStream.videoTracks.isNotEmpty()) {
                    remoteVideoTrack = mediaStream.videoTracks[0]
                    remoteVideoTrack.setEnabled(true)
                    remoteVideoTrack.addSink(surfaceViewRemote)
                }
                if (mediaStream.audioTracks.isNotEmpty()) {
                    val remoteAudioTrack = mediaStream.audioTracks[0]
                    remoteAudioTrack.setEnabled(true)
                }
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                console.d("renegotiation needed")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                console.d("onAddTrack")
            }
        }
        return factory.createPeerConnection(rtcConfig, pcObserver)
    }

    fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        videoCapturer = createVideoCapturer()

        // First create a Video Source, then we can make a Video Track
        videoCapturer?.let {
            val surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(it.isScreencast)
            it.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )

            videoCapturer?.startCapture(
                VIDEO_RESOLUTION_WIDTH,
                VIDEO_RESOLUTION_HEIGHT,
                FPS
            )
            videoTrackFromCamera = peerConnectionFactory.createVideoTrack(
                VIDEO_TRACK_ID,
                videoSource
            )
            videoTrackFromCamera.setEnabled(true)
            videoTrackFromCamera.addSink(surfaceViewLocal)

            // First we create an AudioSource then we can create our AudioTrack
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        } ?: run {
            Log.e(TAG, "videoCapturer is null!")
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(context))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        // Returns the number of cams & front/back face device name

        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(context)
    }

    fun startStreamingVideo() {
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection?.addStream(mediaStream)
    }

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
            if (type == "offer") {
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pcInitialized = true

                //we have remote offer, let's create answer for that
                peerConnection?.setRemoteDescription(object : DefaultSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        console.d("Remote description set.")

                        peerConnection?.createAnswer(object : DefaultSdpObserver() {
                            override fun onCreateSuccess(p0: SessionDescription?) {
                                //answer is ready, set it
                                console.d("Local description set.")
                                peerConnection?.setLocalDescription(DefaultSdpObserver(), p0)
                            }
                        }, MediaConstraints())

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
     * App creates the offer.
     */
    fun makeOffer() {
        state = State.CREATING_OFFER
        pcInitialized = true

        peerConnection?.createOffer(object : DefaultSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 != null) {
                    console.d("offer updated")
                    peerConnection?.setLocalDescription(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                        }
                    }, p0)
                }
            }
        }, sdpMediaConstraints)
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
            if (type == "answer") {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                peerConnection?.setRemoteDescription(DefaultSdpObserver(), answer)
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

    private fun getIceServer(): List<PeerConnection.IceServer> {

        return if (iceServers.isNotEmpty()) {
            iceServers
        } else {
            PeerConnection.IceServer.builder("stun://stun.l.google.com:19302").apply {
                setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                iceServers.add(createIceServer())
            }

            turns.forEach {
                when (it.type) {
                    "turns" -> {
                        val turnsUrl = "${it.type}:${it.host}:${it.port}?transport=udp"
                        PeerConnection.IceServer.builder(turnsUrl)
                            .apply {
                                setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                                setUsername(it.username)
                                setPassword(it.password)

                                console.greenf("URL : $turnsUrl")
                                console.greenf("USER NAME : " + it.username)
                                console.greenf("PASSWORD : " + it.password)

                                iceServers.add(createIceServer())
                            }
                    }
                }
            }

            iceServers
        }

    }

    interface IStateChangeListener {
        /**
         * Called when status of client is changed.
         */
        fun onStateChanged(state: State)
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

    companion object {
        private const val TAG = "ServerLessTags"

        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val VIDEO_RESOLUTION_WIDTH = 640
        private const val VIDEO_RESOLUTION_HEIGHT = 480
        private const val FPS = 30
    }

    interface ICountDownTimer{
        fun startTimer(offer: String)
    }
}