package org.opennms.arnet.app.domain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import edu.uci.ics.jung.graph.Graph;

public class DiagonalLayoutStrategy implements LayoutStrategy {

    @Override
    public void apply(Graph<InventoryVertex, ?> g) {
        float nextX = 0.0f;
        float nextY = 0.0f;

        final List<InventoryVertex> sortedVertices = g.getVertices().stream()
                .sorted(Comparator.comparing(InventoryVertex::getId))
                .collect(Collectors.toList());

        for (InventoryVertex v : sortedVertices) {
            v.setX(nextX);
            v.setY(nextY);

            nextX += 1.0f;
            nextY += 1.0f;
        }
    }
}
