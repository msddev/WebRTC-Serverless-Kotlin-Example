package cz.sazel.android.serverlesswebrtcandroid

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ayush.imagesteganographylibrary.Text.AsyncTaskCallback.TextDecodingCallback
import com.ayush.imagesteganographylibrary.Text.AsyncTaskCallback.TextEncodingCallback
import com.ayush.imagesteganographylibrary.Text.ImageSteganography
import cz.sazel.android.serverlesswebrtcandroid.R.layout.activity_main
import cz.sazel.android.serverlesswebrtcandroid.console.RecyclerViewConsole
import cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver.JingleServer
import cz.sazel.android.serverlesswebrtcandroid.jingleTurnReceiver.JistiServiceModel
import cz.sazel.android.serverlesswebrtcandroid.steganography.TextDecoding
import cz.sazel.android.serverlesswebrtcandroid.steganography.TextEncoding
import cz.sazel.android.serverlesswebrtcandroid.webrtc.ServerlessRTCClient
import cz.sazel.android.serverlesswebrtcandroid.webrtc.ServerlessRTCClient.State.*
import kotlinx.android.synthetic.main.activity_main.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.protocols.IProtocol
import org.java_websocket.protocols.Protocol
import org.webrtc.EglBase
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), ServerlessRTCClient.IStateChangeListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    lateinit var console: RecyclerViewConsole

    private lateinit var client: ServerlessRTCClient
    private lateinit var webSocketClient: WebSocketClient
    private var mnuCreateOffer: MenuItem? = null

    private var retainInstance: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity_main)

        initRecyclerView(savedInstanceState)

        initJingleServer()

        btSubmit.setOnClickListener { sendMessage() }
        edEnterArea.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun initJingleServer() {
        val host = "wss://meet.jit.si/xmpp-websocket"

        val protocols = ArrayList<IProtocol>()
        protocols.add(Protocol("xmpp"))
        val protocolDraft = Draft_6455(Collections.emptyList(), protocols)

        val thread = Thread {
            webSocketClient = JingleServer(URI(host), protocolDraft, object : JitsiCallback {
                override fun receiveTurn(turns: MutableList<JistiServiceModel>) {
                    runOnUiThread {
                        initServerLessRtc(turns)
                        start()
                    }
                }
            })
            webSocketClient.connect()
        }
        thread.start()
    }

    @AfterPermissionGranted(RC_CALL)
    private fun start() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (EasyPermissions.hasPermissions(this, *perms)) {

            client.initializeSurfaceViews(surfaceViewLocal, surfaceViewRemote)

            client.createVideoTrackFromCameraAndShowIt()

            client.initializePeerConnections()

            client.startStreamingVideo()


        } else {
            EasyPermissions.requestPermissions(
                this,
                "Need some permissions",
                RC_CALL,
                *perms
            )
        }
    }

    private fun initRecyclerView(savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        layoutManager.stackFromEnd = true
        console = RecyclerViewConsole(recyclerView)
        console.initialize(savedInstanceState, object : IceCandidateCallback {
            override fun receiveIce(iceCandidate: String) {
                encodedImageWithSteganography(iceCandidate)
            }
        })
        recyclerView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom < oldBottom) {
                recyclerView.postDelayed(
                    { recyclerView.smoothScrollToPosition(console.lines.size) },
                    100
                )
            }
        }
    }

    private fun initServerLessRtc(turns: MutableList<JistiServiceModel>) {
        val retainedClient = lastCustomNonConfigurationInstance as ServerlessRTCClient?
        if (retainedClient == null) {

            client = ServerlessRTCClient(
                turns,
                console,
                applicationContext,
                EglBase.create(),
                this
            )
            try {
                client.initializePeerConnectionFactory()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            client = retainedClient
            onStateChanged(client.state)
        }
    }

    private fun sendMessage() {
        val newText = edEnterArea.text.toString().trim()
        when (client.state) {
            WAITING_FOR_OFFER -> client.processOffer(newText)
            WAITING_FOR_ANSWER -> client.processAnswer(newText)
            CHAT_ESTABLISHED -> {
                Log.d("ggggg", "sendMessage: ggggggggggggggggggg")
            }
            else -> if (newText.isNotBlank()) console.printf(newText)
        }
        edEnterArea.setText("")
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        retainInstance = true
        return client
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        mnuCreateOffer = menu?.findItem(R.id.mnuCreateOffer)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mnuCreateOffer -> client.makeOffer()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        console.onSaveInstanceState(outState)
    }

    override fun onStateChanged(state: ServerlessRTCClient.State) {
        //it could be in different thread
        runOnUiThread {
            edEnterArea.isEnabled = true
            progressBar.visibility = GONE
            mnuCreateOffer?.isVisible = false
            when (state) {
                CHAT_ENDED, INITIALIZING -> client.waitForOffer()
                WAITING_FOR_OFFER -> {
                    mnuCreateOffer?.isVisible = true
                    edEnterArea.hint = getString(R.string.hint_paste_offer)
                }
                WAITING_FOR_ANSWER -> edEnterArea.hint = getString(R.string.hint_paste_answer)
                CHAT_ESTABLISHED -> edEnterArea.hint = getString(R.string.enter_message)
                WAITING_TO_CONNECT, CREATING_OFFER, CREATING_ANSWER -> {
                    progressBar.visibility = VISIBLE
                    if (BuildConfig.DEBUG) edEnterArea.hint = state.name
                    edEnterArea.isEnabled = false
                }
            }
        }
    }

    // encode and decode
    private fun encodedImageWithSteganography(iceCandidate: String) {
        val image = BitmapFactory.decodeResource(resources, R.drawable.mahsan_logo)

        val imageSteganography = ImageSteganography(
            iceCandidate,
            "123456789",
            image
        )

        val textEncoding = TextEncoding(object : TextEncodingCallback {
            override fun onStartTextEncoding() {

            }

            override fun onCompleteTextEncoding(result: ImageSteganography?) {
                Log.d("TAG", "onCompleteTextEncoding")
                result?.let {
                    if (result.isEncoded) {
                        //encrypted image bitmap is extracted from result object
                        val encodedImage = it.encoded_image

                        sharedImage(encodedImage)
                    }
                }
            }
        }
        )

        textEncoding.execute(imageSteganography);
    }

    private fun decodedImageWithSteganography(encodedImage: Bitmap) {
        val imageSteganography = ImageSteganography(
            "123456789",
            encodedImage
        )

        val textDecoding = TextDecoding(object : TextDecodingCallback {
            override fun onStartTextEncoding() {

            }

            override fun onCompleteTextEncoding(result: ImageSteganography?) {
                Log.d("TAG", "onCompleteTextEncoding")
                result?.let {
                    if (result.isDecoded) {
                        if (!result.isSecretKeyWrong) {
                            Log.d("TAG", result.message)
                        } else {
                            Log.d("TAG", "Wrong secret key")
                        }
                    }
                }
            }
        }
        )

        textDecoding.execute(imageSteganography);
    }

    private fun sharedImage(bitmap: Bitmap) {
        val file = File(
            Environment.getExternalStorageDirectory()
                .toString() + File.separator + "my_image_${System.currentTimeMillis()}.png"
        )
        file.createNewFile()
        kotlin.runCatching {
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
        }.onSuccess {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/jpeg*"
            intent.putExtra(Intent.EXTRA_STREAM, getUriFormFile(file))
            ContextCompat.startActivity(this, Intent.createChooser(intent, "Share Image"), null)


            /*val bMap = BitmapFactory.decodeFile(file.absolutePath)
            decodedImageWithSteganography(bMap)*/

        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun getUriFormFile(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID + ".fileProvider",
                file
            )
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        outState.clear()
    }

    /*private fun getFileText(): String {
        return resources.openRawResource(R.raw.testfile).bufferedReader().use { it.readText() }
    }*/

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onDestroy() {
        webSocketClient.close()
        super.onDestroy()
    }

    companion object {
        private const val RC_CALL = 111
    }
}

interface JitsiCallback {
    fun receiveTurn(turns: MutableList<JistiServiceModel>)
}

interface IceCandidateCallback {
    fun receiveIce(iceCandidate: String)
}
