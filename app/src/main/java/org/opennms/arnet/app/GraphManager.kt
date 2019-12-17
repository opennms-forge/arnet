package org.opennms.arnet.app

import android.content.Context
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import edu.uci.ics.jung.graph.Graph
import edu.uci.ics.jung.graph.SparseMultigraph
import org.opennms.arnet.api.Consumer
import org.opennms.arnet.api.model.*
import java.util.concurrent.atomic.AtomicBoolean

class GraphManager(private val context: Context,private val scene: Scene) : Consumer, Scene.OnUpdateListener {
    var didGraphChange: AtomicBoolean = AtomicBoolean(false)
    lateinit var graphNode : GraphNode
    var graph: Graph<Vertex, Edge>? = SparseMultigraph()


    init {
        scene.addOnUpdateListener(this);
    }

    /**
     * Called when the frame for the scene has been updated.
     */
    override fun onUpdate(frameTime: FrameTime?) {
       if (didGraphChange.get()) {
           didGraphChange.set(false)

           graphNode = GraphNode(context, graph)
           graphNode.render(scene)
           if (!::graphNode.isInitialized) {
               scene.addChild(this.graphNode)
           }
       }
    }

    override fun accept(
        graph: Graph<Vertex, Edge>?,
        alarms: MutableCollection<Alarm>?,
        situations: MutableCollection<Situation>?
    ) {
        this.graph = graph
        didGraphChange.set(true)
    }

    override fun acceptEdge(e: Edge?) {
        didGraphChange.set(true)
    }

    override fun acceptVertex(v: Vertex?) {
        didGraphChange.set(true)
    }

    override fun acceptDeletedAlarm(reductionKey: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptDeleteSituation(reductionKey: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptSituation(s: Situation?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptEvent(e: Event?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptDeletedEdge(edgeId: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptDeletedVertex(vertexId: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptAlarm(a: Alarm?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}