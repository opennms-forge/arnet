package org.opennms.arnet.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment
import java.io.IOException


class AugmentedImageFragment : ArFragment() {
    companion object {
        private val TAG = "AugmentedImageFragment"
        private val IMAGE_DATABASE = "kiwi.imgdb"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Turn off the plane discovery since we're only looking for images
        planeDiscoveryController.hide();
        planeDiscoveryController.setInstructionView(null);
        arSceneView.planeRenderer.isEnabled = false;

        return view
    }

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)
        if (!setupAugmentedImageDatabase(config, session)) {
            Log.e(TAG, "Could not setup augmented image database")
        }
        return config
    }

    private fun setupAugmentedImageDatabase(config: Config, session: Session): Boolean {
        var augmentedImageDatabase: AugmentedImageDatabase?
        val assetManager = if (context != null) context!!.assets else null
        if (assetManager == null) {
            Log.e(TAG, "Context is null, cannot intitialize image database.")
            return false
        }

        try {
            context!!.assets.open(IMAGE_DATABASE).use { `is` ->
                augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, `is`)
                config.augmentedImageDatabase = augmentedImageDatabase
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image database.", e)
            return false
        }
        return true
    }
}
