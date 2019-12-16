package org.opennms.arnet.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.SceneView
import org.opennms.arnet.app.mock.MockConsumerService

class MainActivity : AppCompatActivity() {

    lateinit var sceneView : SceneView
    lateinit var graphManager : GraphManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sceneView = findViewById(R.id.sceneView)

        // Create the graph manager
        graphManager = GraphManager(this, sceneView.scene)

        // Register the graph manager with the consumer service
        val svc = MockConsumerService()
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
}
