package cz.sazel.android.serverlesswebrtcandroid

import android.Manifest
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import cz.sazel.android.serverlesswebrtcandroid.R.layout.activity_main
import cz.sazel.android.serverlesswebrtcandroid.console.RecyclerViewConsole
import cz.sazel.android.serverlesswebrtcandroid.webrtc.ServerlessRTCClient
import cz.sazel.android.serverlesswebrtcandroid.webrtc.ServerlessRTCClient.State.*
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), ServerlessRTCClient.IStateChangeListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    lateinit var console: RecyclerViewConsole

    lateinit var client: ServerlessRTCClient
    var mnuCreateOffer: MenuItem? = null

    private var retainInstance: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activity_main)

        initRecyclerView(savedInstanceState)
        initServerLessRtc()

        start()

        btSubmit.setOnClickListener { sendMessage() }
        edEnterArea.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
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
        console.initialize(savedInstanceState)
        recyclerView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom < oldBottom) {
                recyclerView.postDelayed(
                    { recyclerView.smoothScrollToPosition(console.lines.size) },
                    100
                )
            }
        }
    }

    private fun initServerLessRtc() {
        val retainedClient = lastCustomNonConfigurationInstance as ServerlessRTCClient?
        if (retainedClient == null) {

            client = ServerlessRTCClient(
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
                if (newText.isNotBlank()) {
                    client.sendMessage(newText)
                    console.printf("&gt;$newText")
                }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    /*override fun onDestroy() {
        if (!retainInstance)
            client.destroy()
        super.onDestroy()

    }*/

    companion object {
        private const val RC_CALL = 111
    }
}
