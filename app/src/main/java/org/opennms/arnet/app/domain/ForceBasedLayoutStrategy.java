package org.opennms.arnet.app.domain;

import org.opennms.arnet.api.model.Vertex;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Pair;

public class ForceBasedLayoutStrategy implements LayoutStrategy  {

    @Override
    public void apply(Graph<InventoryVertex, InventoryEdge> g, Collection<InventoryAlarm> alarms, Collection<InventorySituation> situations) {
        Dimension size = new Dimension(1,1);
        D3TopoLayout<InventoryVertex, ?> layout = new D3TopoLayout<>(g, size);
        layout.initialize();
        while(!layout.done()) {
            layout.step();
        }

        float scale = 1/100f;
        Point2D offset = new Point2D.Float(0.0f, 0.0f);

        // Determine which vertices have the most links
        Set<InventoryVertex> verticesWithMostNeighbors = new HashSet<>();
        int maxNumNeighbors = -1;
        for (InventoryVertex v : g.getVertices()) {
            int numNeighbors = g.getNeighborCount(v);
            if (numNeighbors > maxNumNeighbors) {
                maxNumNeighbors = numNeighbors;
                verticesWithMostNeighbors.clear();
                verticesWithMostNeighbors.add(v);
            } else if (numNeighbors == maxNumNeighbors) {
                verticesWithMostNeighbors.add(v);
            }
        }

        // Sort by ID for deterministic results
        verticesWithMostNeighbors.stream()
                .min(Comparator.comparing(InventoryVertex::getId))
                // Shift the offset by the *new* position of this vertex to map it to the origin
                .ifPresent(v -> {
                    final Point2D newPosition = layout.getPosition(v);
                    offset.setLocation(offset.getX() - newPosition.getX(), offset.getY() - newPosition.getY());
                });

        // X = ( x + offset ) * scale
        // Y = ( Y + offset ) * scale
        //
        // Store the new positions in the layout
        for(InventoryVertex v : g.getVertices()) {
            Point2D pos = layout.getPosition(v);
            v.setX(((float)(pos.getX() + offset.getX()) * scale));
            v.setY(((float)(pos.getY() + offset.getY()) * scale));
        }

        // No support for alarms or situations here - set them all to 0
        final List<XY> xys = new ArrayList<>();
        xys.addAll(alarms);
        xys.addAll(situations);
        for (XY xy : xys) {
            xy.setX(0);
            xy.setY(0);
        }
    }

    /**
     * Adapted from https://raw.githubusercontent.com/OpenNMS/opennms/373928acc9be38f5c7c5eccea307aa49aca04c40/features/topology-map/org.opennms.features.topology.app/src/main/java/org/opennms/features/topology/app/internal/jung/D3TopoLayout.java
     * @param <V>
     * @param <E>
     */
    private static class D3TopoLayout<V extends XY, E> {

        private static final double LINK_DISTANCE = 3.0;
        private static final double LINK_STRENGTH = 2.0;
        private static final int DEFAULT_CHARGE = -1200;
        private double EPSILON = 0.00000000001D;
        private int m_charge = -30;
        private double m_thetaSquared = .64;

        private double m_alpha = 0.1;
        private Map<V , VertexData> m_vertexData = new HashMap<>();
        private Map<E, EdgeData> m_edgeData = new HashMap<>();

        private final Graph<V, E> graph;

        private final Random random = new Random(0);

        private Dimension size;

        public D3TopoLayout(Graph<V, E> graph, Dimension size) {
            this.graph = Objects.requireNonNull(graph);
            this.size = Objects.requireNonNull(size);
        }

        public void initialize() {
            //initialize the weights
            for(V v : graph.getVertices()) {
                VertexData vData = getVertexData(v);
                vData.setWeight(1);
                Point2D location = new Point2D.Float(v.getX(), v.getY());
                vData.setLocation(location);
                vData.setPrevious(location);
            }

            //initialize the vertices that have edges with weight
            for (E e : graph.getEdges()) {
                Pair<V> endPoints = graph.getEndpoints(e);
                V v1 = endPoints.getFirst();
                V v2 = endPoints.getSecond();
                VertexData vData1 = getVertexData(v1);
                vData1.setWeight(vData1.getWeight() + 1);

                VertexData vData2 = getVertexData(v2);
                vData2.setWeight(vData2.getWeight() + 1);
            }

            //Do we need to do an initial layout, we can rely on the initialized position
        }

        public void step() {
            double currentForce;

            //guass-seidel relaxation for links
            for (E e : graph.getEdges()) {
                Pair<V> endPoints = graph.getEndpoints(e);
                VertexData srcVertexData = getVertexData(endPoints.getFirst());
                VertexData targetVertexData = getVertexData(endPoints.getSecond());

                double xDelta = targetVertexData.getX() - srcVertexData.getX();
                double yDelta = targetVertexData.getY() - srcVertexData.getY();
                double l = xDelta * xDelta + yDelta * yDelta;
                if (l != 0) {
                    EdgeData edgeData = getEdgeData(e);
                    double lSqrt = Math.sqrt(l);
                    double distance = m_alpha * edgeData.getStrength() * (lSqrt - edgeData.getDistance()) / lSqrt;

                    xDelta *= distance;
                    yDelta *= distance;

                    currentForce = (double)srcVertexData.getWeight() / (double)(targetVertexData.getWeight() + srcVertexData.getWeight());
                    targetVertexData.offset(-(xDelta * currentForce), -(yDelta * currentForce));

                    currentForce = 1 - currentForce;
                    srcVertexData.offset(xDelta * currentForce, yDelta * currentForce);
                }

            }

            //Apply gravity forces
            currentForce = m_alpha * getGravity();
            if(currentForce != 0){
                double centerX = size.getWidth() / 2;
                double centerY = size.getHeight() / 2;

                for (V v : graph.getVertices()) {
                    VertexData vData = getVertexData(v);
                    vData.offset((centerX - vData.getX()) * currentForce, (centerY - vData.getY()) * currentForce);
                }
            }

            //Compute quad tree center of mass and apply charge force
            if(getDefaultCharge() != 0){
                for(V v1 : graph.getVertices()) {
                    VertexData vData1 = getVertexData(v1);
                    for(V v2 : graph.getVertices()) {
                        VertexData vData2 = getVertexData(v2);

                        double dx = vData2.getX() - vData1.getX();
                        double dy = vData2.getY() - vData1.getY();
                        double d = dx*dx + dy*dy;

                        if (d > 0) {
                            double k = m_alpha * vData2.getCharge() / d;
                            double px = dx*k;
                            double py = dy*k;

                            vData1.offset(px, py);
                        } else {
                            vData1.offset(0.5-random.nextDouble(), 0.5-random.nextDouble());
                        }

                    }
                }
            }

            // position verlet integration
            for(V v : graph.getVertices()) {
                VertexData vData = getVertexData(v);
                double tempX = vData.getX();
                double tempY = vData.getY();
                double x = vData.getX() + (vData.getPrevious().getX() - vData.getX())*getFriction();
                double y = vData.getY() + (vData.getPrevious().getY() - vData.getY())*getFriction();
                vData.setLocation(x, y);
                vData.setPrevious(tempX, tempY);
                Point2D location = new Point2D.Float(v.getX(), v.getY());
                location.setLocation(vData.getX(), vData.getY());
            }

            m_alpha *= 0.99;
        }

        private double getGravity() {
            return 0.1;
        }

        private double getFriction() {
            return 0.9;
        }

        public boolean done() {
            // if we have no objects in our graph to layout we are done:
            return (graph.getEdges().isEmpty() && graph.getVertices().isEmpty()) || m_alpha < 0.005;
        }

        public Point2D getPosition(V v) {
            return m_vertexData.get(v);
        }

        private VertexData getVertexData(V v) {
            return m_vertexData.computeIfAbsent(v, vx -> new VertexData());
        }

        private EdgeData getEdgeData(E e) {
            return m_edgeData.computeIfAbsent(e, eg -> new EdgeData());
        }

        public int getDefaultCharge() {
            return m_charge;
        }

        public void setDefaultCharge(int m_charge) {
            this.m_charge = m_charge;
        }

        protected static class VertexData extends Point2D.Double{

            private int m_weight;
            private double m_distance = LINK_DISTANCE;
            private double m_strength = LINK_STRENGTH;
            private int m_charge = DEFAULT_CHARGE;
            private Point2D m_previous = null;

            protected void offset(double x, double y) {
                this.x += x;
                this.y += y;
            }

            protected void offsetPrevious(double x, double y) {
                if (m_previous == null) {
                    m_previous = new Point2D.Double(this.x, this.y);
                }
                m_previous.setLocation(m_previous.getX()+x, m_previous.getY()+y);
            }

            public void setPrevious(Point2D location) {
                m_previous = (Point2D) location.clone();
            }

            public void setPrevious(double x, double y) {
                m_previous = new Point2D.Double(x, y);
            }

            private void print(String before, String after) {
            }

            protected double norm()
            {
                return Math.sqrt(x*x + y*y);
            }

            protected void setWeight(int weight){
                m_weight = weight;
            }

            protected int getWeight(){
                return m_weight;
            }

            protected void setDistance(int distance) {
                m_distance = distance;
            }

            protected double getDistance() {
                return m_distance;
            }

            protected void setStrength(double strength) {
                m_strength = strength;
            }

            protected double getStrength() {
                return m_strength;
            }

            protected void setCharge(int charge) {
                m_charge = charge;
            }

            protected int getCharge() {
                return m_charge;
            }

            protected Point2D getPrevious() {
                return m_previous;
            }

        }

        protected static class EdgeData {
            private double m_distance = LINK_DISTANCE;
            private double m_strength = LINK_STRENGTH;

            protected void setDistance(double distance) {
                m_distance = distance;
            }

            protected double getDistance() {
                return m_distance;
            }

            public double getStrength() {
                return m_strength;
            }

            public void setStrength(double m_strength) {
                this.m_strength = m_strength;
            }
        }
    }
}
