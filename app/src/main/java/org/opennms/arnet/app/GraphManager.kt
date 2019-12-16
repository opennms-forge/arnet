package org.opennms.arnet.app

import android.content.Context
import com.google.ar.sceneform.Scene
import edu.uci.ics.jung.graph.Graph
import org.opennms.arnet.api.Consumer
import org.opennms.arnet.api.model.*

class GraphManager(private val context: Context,private val scene: Scene) : Consumer {

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

    override fun accept(
        graph: Graph<Vertex, Edge>?,
        alarms: MutableCollection<Alarm>?,
        situations: MutableCollection<Situation>?
    ) {
        val node = GraphNode(context, graph);
        node.render(scene)
        scene.addChild(node)
    }

    override fun acceptEdge(e: Edge?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptVertex(v: Vertex?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptDeletedVertex(vertexId: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptAlarm(a: Alarm?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}