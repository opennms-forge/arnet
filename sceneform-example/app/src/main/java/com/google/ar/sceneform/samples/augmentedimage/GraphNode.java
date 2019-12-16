package com.google.ar.sceneform.samples.augmentedimage;

import android.content.Context;
import android.util.Log;

import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import org.opennms.arnet.api.model.Edge;
import com.google.ar.sceneform.samples.graph.GraphProvider;
import org.opennms.arnet.api.model.Vertex;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.util.RandomLocationTransformer;
import edu.uci.ics.jung.graph.Graph;

public class GraphNode extends AnchorNode {
    private static final String TAG = "GraphNode";

    private final Graph<Vertex, Edge> g;
    private static CompletableFuture<ModelRenderable> redBall;
    private static CompletableFuture<ModelRenderable> cylinder;
    private static CompletableFuture<ModelRenderable> cube;

    // The augmented image represented by this node.
    private AugmentedImage image;

    public GraphNode(Context context) {
        GraphProvider graphProvider = new StaticGraphProviderImpl();
        // TODO: Handle graph changes
        g = graphProvider.getAndSubscribe(newG -> {});

        if (redBall == null) {
            redBall = MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.RED))
                    .thenApply(material -> ShapeFactory.makeCube(new Vector3(0.1f, 0.1f, 0.1f), new Vector3(0.0f, 0.0f, 0.0f), material));
            cylinder = MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.BLUE))
                    .thenApply(material -> ShapeFactory.makeCylinder(0.05f, 0.1f, new Vector3(0.0f, 0.0f, 0.0f), material));
            cube = MaterialFactory.makeOpaqueWithColor(context, new Color(android.graphics.Color.WHITE))
                    .thenApply(
                            material -> ShapeFactory.makeCube(new Vector3(.01f, .01f, .01f),
                                    Vector3.zero(), material));
        }
    }

    public void setImage(Plane plane) {
        //this.image = image;

        // If any of the models are not loaded, then recurse when all are loaded.
        if (!redBall.isDone() || !cube.isDone() || !cylinder.isDone()) {
            Log.i(TAG, "Waiting for renderables...");
            CompletableFuture.allOf(redBall, cube, cylinder)
                    .thenAccept((Void aVoid) -> setImage(image))
                    .exceptionally(
                            throwable -> {
                                Log.e(TAG, "Exception loading", throwable);
                                return null;
                            });
        }

        // Set the anchor based on the center of the image.
        setAnchor(plane.createAnchor(plane.getCenterPose()));

        Dimension size = new Dimension(1,1);
        FRLayout<Vertex, Edge> layout = new FRLayout<>(g);
        // Manually create the initializer so that we can used a fixed seed
        layout.setInitializer(new RandomLocationTransformer<>(size, 0));
        layout.setSize(new Dimension(1,1));
        // layout.setRepulsionMultiplier(0.5f);
        layout.initialize();

        // Upper left corner.
        Vector3 upperLeftCorner = new Vector3(-image.getExtentX(), 0.0f, -image.getExtentZ());

        final Map<Vertex, Node> vertexToSceneNode = new HashMap<>();
        for (Vertex vertex : g.getVertices()) {
            Point2D point = layout.apply(vertex);

            // Center and slightly "out" of the screen.
            Vector3 localPosition = new Vector3();
            localPosition.set(((float)point.getX() / 2) - 0.25f, 0.1f, ((float)point.getY() / 2) - 0.25f);

            Log.i(TAG, String.format("Rendering vertex %s at %s", vertex, localPosition));
            Node testNode = new Node();
            testNode.setParent(this);
            testNode.setLocalPosition(localPosition);
            testNode.setRenderable(redBall.getNow(null));
            // testNode.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
            testNode.setWorldScale(new Vector3(0.1f, 0.1f, 0.1f));

            // Store the node for future lookups
            vertexToSceneNode.put(vertex, testNode);
        }

        for (Edge edge : g.getEdges()) {
            final Collection<Vertex> incidentVertices = g.getIncidentVertices(edge);
            if (incidentVertices.size() != 2) {
                // Only deal with edges between two vertices
                continue;
            }

            final List<Vertex> vertexList = new ArrayList<>(incidentVertices);
            final Node source = vertexToSceneNode.get(vertexList.get(0));
            final Node target = vertexToSceneNode.get(vertexList.get(1));
            if (source == null || target == null) {
                // We're missing either a source of target, skip it
                continue;
            }

            // Create a line between the two points nodes
            lineBetweenPoints(source.getLocalPosition(), target.getLocalPosition());
        }
    }

    /**
     * Called when the AugmentedImage is detected and should be rendered.
     *
     * There is no need to worry about world coordinates since everything is
     * relative to the center of the image, which is the parent node of the corners.
     */
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    public void setImage(AugmentedImage image) {
        this.image = image;

        // If any of the models are not loaded, then recurse when all are loaded.
        if (!redBall.isDone() || !cube.isDone() || !cylinder.isDone()) {
            Log.i(TAG, "Waiting for renderables...");
            CompletableFuture.allOf(redBall, cube, cylinder)
                    .thenAccept((Void aVoid) -> setImage(image))
                    .exceptionally(
                            throwable -> {
                                Log.e(TAG, "Exception loading", throwable);
                                return null;
                            });
        }

        // Set the anchor based on the center of the image.
        setAnchor(image.createAnchor(image.getCenterPose()));

        Dimension size = new Dimension(1,1);
        FRLayout<Vertex, Edge> layout = new FRLayout<>(g);
        // Manually create the initializer so that we can used a fixed seed
        layout.setInitializer(new RandomLocationTransformer<>(size, 0));
        layout.setSize(new Dimension(1,1));
       // layout.setRepulsionMultiplier(0.5f);
        layout.initialize();

        // Upper left corner.
        Vector3 upperLeftCorner = new Vector3(-image.getExtentX(), 0.0f, -image.getExtentZ());

        final Map<Vertex, Node> vertexToSceneNode = new HashMap<>();
        for (Vertex vertex : g.getVertices()) {
            Point2D point = layout.apply(vertex);

            // Center and slightly "out" of the screen.
            Vector3 localPosition = new Vector3();
            localPosition.set(((float)point.getX() / 2) - 0.25f, 0.1f, ((float)point.getY() / 2) - 0.25f);

            Log.i(TAG, String.format("Rendering vertex %s at %s", vertex, localPosition));
            Node testNode = new Node();
            testNode.setParent(this);
            testNode.setLocalPosition(localPosition);
            testNode.setRenderable(redBall.getNow(null));
            // testNode.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
            testNode.setWorldScale(new Vector3(0.1f, 0.1f, 0.1f));

            // Store the node for future lookups
            vertexToSceneNode.put(vertex, testNode);
        }

        for (Edge edge : g.getEdges()) {
            final Collection<Vertex> incidentVertices = g.getIncidentVertices(edge);
            if (incidentVertices.size() != 2) {
                // Only deal with edges between two vertices
                continue;
            }

            final List<Vertex> vertexList = new ArrayList<>(incidentVertices);
            final Node source = vertexToSceneNode.get(vertexList.get(0));
            final Node target = vertexToSceneNode.get(vertexList.get(1));
            if (source == null || target == null) {
                // We're missing either a source of target, skip it
                continue;
            }

            // Create a line between the two points nodes
            lineBetweenPoints(source.getLocalPosition(), target.getLocalPosition());
        }
    }

    public void lineBetweenPoints(Vector3 point1, Vector3 point2) {
        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

        Node lineNode = new Node();
        lineNode.setParent(this);
        lineNode.setRenderable(cube.getNow(null));
        lineNode.setLocalScale(new Vector3( 0.1f, 0.1f, difference.length() * 100));
        lineNode.setLocalPosition(Vector3.add(point1, point2).scaled(.5f));
        lineNode.setLocalRotation(rotationFromAToB);
    }

}
