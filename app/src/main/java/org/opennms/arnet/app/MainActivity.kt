package org.opennms.arnet.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.SceneView
import org.opennms.arnet.app.mock.MockConsumerService
import org.opennms.arnet.WebSocketConsumerService

class MainActivity : AppCompatActivity() {

    lateinit var sceneView : SceneView
    lateinit var graphManager : GraphManager
    private val consumerService = WebSocketConsumerService().apply { start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sceneView = findViewById(R.id.sceneView)

        // Create the graph manager
        graphManager = GraphManager(this, sceneView.scene)

        // Register the graph manager with the consumer service
        val svc = consumerService
        svc.accept(graphManager)
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
