package org.opennms.arnet.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.SceneView
import org.opennms.arnet.WebSocketConsumerService
import org.opennms.arnet.app.mock.MockConsumerService
import org.opennms.arnet.app.scene.NetworkNode
import org.opennms.arnet.app.scene.RenderableRegistry

class MainActivity : AppCompatActivity() {

    lateinit var sceneView : SceneView
    private val consumerService = WebSocketConsumerService().apply { start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sceneView = findViewById(R.id.sceneView)

        // Load our renderables (3D assets)
        val renderables = RenderableRegistry(this)

        // Register the network manager with the consumer service
        //val svc = consumerService
        val svc = MockConsumerService()
        svc.updateGraphOnBackgroundThread()

        // Create the network
        NetworkNode(sceneView.scene, renderables, svc)
    }

    override fun onPause() {
        super.onPause()
        sceneView.pause()
    }

    override fun onResume() {
        super.onResume()
        sceneView.resume()
    }

    override fun onDestroy() {
        consumerService.stop()
        super.onDestroy()
    }

    companion object {
        private val TAG = "MainActivity"
    }
}
