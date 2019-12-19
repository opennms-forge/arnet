package org.opennms.arnet.app.scene;

import android.animation.ObjectAnimator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.math.Vector3Evaluator;

import org.opennms.arnet.app.domain.InventoryVertex;

import java.util.Objects;

public class InventoryNode extends Node {
    // We'll use Property Animation to make this node rotate.
    @Nullable
    private ObjectAnimator animation = null;

    private InventoryVertex v;

    private Vector3 targetPosition;
    private Vector3 lastTargetPosition;


    public InventoryNode(InventoryVertex v) {
        this.v = Objects.requireNonNull(v);
        // When adding a new node, go directly to where it's expected to be
        inheritPositionFromLayout();
        //lastTargetPosition = targetPosition;
        //setLocalPosition(targetPosition);
    }

    public void inheritPositionFromLayout() {
        targetPosition = new Vector3((v.getX() / 2) - 0.25f, 0.1f, (v.getY() / 2) - 0.25f);
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        super.onUpdate(frameTime);

        // Animation hasn't been set up.
        if (animation == null) {
            return;
        }

        // If nothing has changed, continue moving to the same target location
        if (Objects.equals(targetPosition, lastTargetPosition)) {
            return;
        }

        animation.setObjectValues(getLocalPosition(), targetPosition);
        lastTargetPosition = targetPosition;
    }


    @Override
    public void onActivate() {
        startAnimation();
    }

    @Override
    public void onDeactivate() {
        stopAnimation();
    }

    private void startAnimation() {
        if (animation != null) {
            return;
        }

        animation = createAnimator(getLocalPosition(), lastTargetPosition);
        animation.setTarget(this);
        animation.start();
    }

    private void stopAnimation() {
        if (animation == null) {
            return;
        }
        animation.cancel();
        animation = null;
    }

    private static ObjectAnimator createAnimator(Vector3 startPosition, Vector3 endPosition) {
        ObjectAnimator animation = new ObjectAnimator();
        animation.setObjectValues(startPosition, endPosition);
        animation.setPropertyName("localPosition");
        animation.setEvaluator(new Vector3Evaluator());
        animation.setRepeatCount(0);
        animation.setInterpolator(new OvershootInterpolator());
        animation.setAutoCancel(true);
        animation.setDuration(600);
        return animation;
    }
}
