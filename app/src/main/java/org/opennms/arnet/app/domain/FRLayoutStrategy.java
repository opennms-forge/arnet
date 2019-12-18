package org.opennms.arnet.app.domain;

import java.awt.Dimension;
import java.awt.geom.Point2D;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.util.RandomLocationTransformer;
import edu.uci.ics.jung.graph.Graph;

public class FRLayoutStrategy implements LayoutStrategy {
    @Override
    public void apply(Graph<InventoryVertex, ?> g) {
        Dimension size = new Dimension(1,1);
        FRLayout<InventoryVertex, ?> layout = new FRLayout<>(g);
        // Manually create the initializer so that we can used a fixed seed
        layout.setInitializer(new RandomLocationTransformer<>(size, 0));
        layout.setSize(new Dimension(1,1));
        layout.initialize();

        for (InventoryVertex v : g.getVertices()) {
            Point2D point = layout.apply(v);
            if (point == null) {
                continue;
            }
            v.setX((float)point.getX());
            v.setY((float)point.getY());
        }
    }
}
