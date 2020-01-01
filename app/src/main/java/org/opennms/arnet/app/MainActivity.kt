package org.opennms.arnet.app

import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.MotionEvent.INVALID_POINTER_ID
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
import androidx.core.view.MotionEventCompat




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


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.drawer_menu, menu)
        val actionBar = supportActionBar
        actionBar?.title = "SOMMMMMEEE TITLE"
        actionBar?.setDisplayShowTitleEnabled(true)
        Log.e("CHEEEEECKKKKKED::::", "checked")
        return true;
    }

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
            private var mLastTouchX: Float = 0.0f
            private var mLastTouchY: Float = 0.0f
            private var mPosX: Float = 0.0f
            private var mPosY: Float = 0.0f

//            override fun onTouch(v: View?, ev: MotionEvent?): Boolean {
//                simpleGestureDetector.onTouchEvent(ev)
//                scaleGestureDetector.onTouchEvent(ev)
//
//
//
//                return true
//            }

            // The ‘active pointer’ is the one currently moving our object.
            private var mActivePointerId = INVALID_POINTER_ID

            override fun onTouch(v: View?, ev: MotionEvent?): Boolean  {
                // Let the ScaleGestureDetector inspect all events.
                scaleGestureDetector.onTouchEvent(ev)

                val action = MotionEventCompat.getActionMasked(ev)

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        MotionEventCompat.getActionIndex(ev).also { pointerIndex ->
                            // Remember where we started (for dragging)
                            mLastTouchX = MotionEventCompat.getX(ev, pointerIndex)
                            mLastTouchY = MotionEventCompat.getY(ev, pointerIndex)
                            Log.e("dowwn: ", "doyn")
                        }

                        // Save the ID of this pointer (for dragging)
                        mActivePointerId = MotionEventCompat.getPointerId(ev, 0)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Find the index of the active pointer and fetch its position
                        val (x: Float, y: Float) =
                            MotionEventCompat.findPointerIndex(ev, mActivePointerId).let { pointerIndex ->
                                // Calculate the distance moved
                                MotionEventCompat.getX(ev, pointerIndex) to
                                        MotionEventCompat.getY(ev, pointerIndex)
                            }

                        mPosX += x - mLastTouchX
                        mPosY += y - mLastTouchY

//                        invalidate()

                        // Remember this touch position for the next move event
                        mLastTouchX = x
                        mLastTouchY = y
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mActivePointerId = INVALID_POINTER_ID
                    }
                    MotionEvent.ACTION_POINTER_UP -> {

                        MotionEventCompat.getActionIndex(ev).also { pointerIndex ->
                            MotionEventCompat.getPointerId(ev, pointerIndex)
                                .takeIf { it == mActivePointerId }
                                ?.run {
                                    // This was our active pointer going up. Choose a new
                                    // active pointer and adjust accordingly.
                                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                                    mLastTouchX = MotionEventCompat.getX(ev, newPointerIndex)
                                    mLastTouchY = MotionEventCompat.getY(ev, newPointerIndex)
                                    mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex)
                                }
                        }
                    }
                }
                return true
            }
        })

        // Load our renderables (3D assets)
        renderables = RenderableRegistry(this)

        // Start listening for tracked images
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        val actionBar = supportActionBar
        actionBar!!.hide()

        actionBar.show()
        actionBar.subtitle = "subtitle"
        actionBar.title = "title"
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

        val actionBar = supportActionBar
        actionBar?.title = "SOMMMMMEEE TITLE"
        actionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.show()
        supportActionBar?.setDisplayShowCustomEnabled(true);
        supportActionBar?.setDisplayShowHomeEnabled(true);
        Log.e("CHEEEEECKKKKKED AGAIN 2::::", "checked")
    }

    companion object {
        private val TAG = "MainActivity"
    }
}
