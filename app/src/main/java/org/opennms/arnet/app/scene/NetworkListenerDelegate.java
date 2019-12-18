package org.opennms.arnet.app.scene;

import org.opennms.arnet.app.domain.InventoryEdge;
import org.opennms.arnet.app.domain.InventoryVertex;
import org.opennms.arnet.app.domain.NetworkListener;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

public class NetworkListenerDelegate implements NetworkListener {

    private final LinkedBlockingDeque<Task> tasks = new LinkedBlockingDeque<>();

    public interface Task {

        int getPriority();

        void visit(Visitor visitor);
    }

    public static class VertexAddedOrUpdated implements Task {
        private final InventoryVertex v;

        private VertexAddedOrUpdated(InventoryVertex v) {
            this.v = Objects.requireNonNull(v);
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.onVertexAddedOrUpdated(v);
        }
    }

    public static class VertexRemoved implements Task {
        private final InventoryVertex v;

        private VertexRemoved(InventoryVertex v) {
            this.v = Objects.requireNonNull(v);
        }

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.onVertexRemoved(v);
        }
    }

    public static class EdgeAddedOrUpdated implements Task {
        private final InventoryEdge e;

        private EdgeAddedOrUpdated(InventoryEdge e) {
            this.e = Objects.requireNonNull(e);
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.onEdgeAddedOrUpdated(e);
        }
    }

    public static class EdgeRemoved implements Task {
        private final InventoryEdge e;

        private EdgeRemoved(InventoryEdge e) {
            this.e = Objects.requireNonNull(e);
        }

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.onEdgeRemoved(e);
        }
    }

    public static class LayoutRecalculated implements Task {

        private LayoutRecalculated() { }

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.onLayoutRecalculated();
        }
    }

    public interface Visitor {

        void onVertexAddedOrUpdated(InventoryVertex v);

        void onVertexRemoved(InventoryVertex v);

        void onEdgeAddedOrUpdated(InventoryEdge e);

        void onEdgeRemoved(InventoryEdge e);

        void onLayoutRecalculated();

    }

    @Override
    public void onVertexAddedOrUpdated(InventoryVertex v) {
        tasks.add(new VertexAddedOrUpdated(v));
    }

    @Override
    public void onVertexRemoved(InventoryVertex v) {
        tasks.add(new VertexRemoved(v));
    }

    @Override
    public void onEdgeAddedOrUpdated(InventoryEdge e) {
        tasks.add(new EdgeAddedOrUpdated(e));
    }

    @Override
    public void onEdgeRemoved(InventoryEdge e) {
        tasks.add(new EdgeRemoved(e));
    }

    @Override
    public void onLayoutRecalculated() {
        tasks.add(new LayoutRecalculated());
    }

    public List<Task> getTasks() {
        final LinkedList<Task> currentTasks = new LinkedList<>();
        tasks.drainTo(currentTasks);
        currentTasks.sort(Comparator.comparingInt(Task::getPriority));
        return currentTasks;
    }
}
