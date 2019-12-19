package org.opennms.arnet.app.domain;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.util.RandomLocationTransformer;
import edu.uci.ics.jung.graph.Graph;

public class FRLayoutStrategy implements LayoutStrategy {

    @Override
    public void apply(Graph<InventoryVertex, InventoryEdge> g, Collection<InventoryAlarm> alarms, Collection<InventorySituation> situations) {
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

        // No support for alarms or situations here - set them all to 0
        final List<XY> xys = new ArrayList<>();
        xys.addAll(alarms);
        xys.addAll(situations);
        for (XY xy : xys) {
            xy.setX(0);
            xy.setY(0);
        }
    }
}
