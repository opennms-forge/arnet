package org.opennms.arnet.app

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.google.common.util.concurrent.AtomicDouble
import org.opennms.arnet.WebSocketConsumerService
import org.opennms.arnet.app.mock.MockConsumerService
import org.opennms.arnet.app.scene.NetworkNode
import org.opennms.arnet.app.scene.RenderableRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min


class MainActivity : AppCompatActivity() {

    lateinit var sceneView : SceneView
    lateinit var arFragment: ArFragment
    lateinit var fitToScanView: ImageView
    lateinit var renderables: RenderableRegistry

   // private val consumerService = WebSocketConsumerService().apply { start() }
    private val consumerService = MockConsumerService().apply { updateGraphOnBackgroundThread() }

    private val augmentedImageMap: MutableMap<AugmentedImage, NetworkNode> = HashMap()

    private val resetView = AtomicBoolean(false)
    private val applyTransforms = AtomicBoolean(false)
    private var scaleFactor: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment
        fitToScanView = findViewById<View>(R.id.image_view_fit_to_scan) as ImageView
        sceneView = findViewById(R.id.sceneView)


        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                scaleFactor *= detector?.scaleFactor ?: 1.0f
                // Don't let the object get too small or too large
                scaleFactor = max(0.1f, min(scaleFactor, 5.0f))
                applyTransforms.set(true)
                return true;
            }
        })

        val simpleGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                Log.d(TAG, "Got double tap! Requesting a view reset.")
                resetView.set(true)
                return true
            }
        })

        arFragment.getArSceneView().setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
                simpleGestureDetector.onTouchEvent(ev)
                scaleGestureDetector.onTouchEvent(ev)
                return true
            }
        })

        // Load our renderables (3D assets)
        renderables = RenderableRegistry(this)

        // Start listening for tracked images
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
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

    /**
     * Called when a new image is detected *after* the
     * network has been added to the scene.
     */
    fun onNewImageDetected(image: AugmentedImage) {
        // pass
    }

    fun onUpdateFrame(frameTime: FrameTime) {
        // Grab the frame, or return if there is none
        val frame: Frame = arFragment.arSceneView.arFrame ?: return

        if (resetView.get()) {
            for ((_,network) in augmentedImageMap) {
                arFragment.arSceneView.scene.removeChild(network)
                network.destroy()
            }
            augmentedImageMap.clear()
            resetView.set(false)
        }

        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
        for (augmentedImage in updatedAugmentedImages) {
            when (augmentedImage.trackingState) {
                TrackingState.PAUSED -> {
                    // pass
                }
                TrackingState.TRACKING -> {
                    fitToScanView.visibility = View.GONE
                    // Create a new anchor for newly found images.
                    var node = augmentedImageMap.get(augmentedImage)
                    if (node == null) {
                        // Create the network
                        node = NetworkNode(sceneView.scene, renderables, consumerService)
                        node.setImage(augmentedImage)
                        node.worldScale = Vector3(0.1f * scaleFactor, 0.1f * scaleFactor, 0.1f * scaleFactor)
                        augmentedImageMap[augmentedImage] = node
                        arFragment.arSceneView.scene.addChild(node)
                        // For additional hooks
                        onNewImageDetected(augmentedImage)
                    } else if (applyTransforms.get()) {
                        node.worldScale = Vector3(0.1f * scaleFactor, 0.1f * scaleFactor, 0.1f * scaleFactor)
                        applyTransforms.set(false)
                    }
                }
                TrackingState.STOPPED -> {
                    val node = augmentedImageMap.remove(augmentedImage)
                    if (node != null) {
                        arFragment.arSceneView.scene.removeChild(node)
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = "MainActivity"
    }
}
