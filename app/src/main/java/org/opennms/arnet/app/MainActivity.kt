package org.opennms.arnet.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.Scene
import org.opennms.arnet.app.scenes.GraphNode

class MainActivity : AppCompatActivity() {

    lateinit var scene: Scene
    lateinit var sceneView : SceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sceneView = findViewById(R.id.sceneView)
        scene = sceneView.scene // get current scene
        addGraphToScene();
    }

    /**
     * Builds the graph and adds it to the current scene.
     */
    private fun addGraphToScene() {
        var node = GraphNode(this)
        node.render(scene)
        scene.addChild(node)
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
