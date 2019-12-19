package org.opennms.arnet.app.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import edu.uci.ics.jung.graph.Graph;

public class DiagonalLayoutStrategy implements LayoutStrategy {

    @Override
    public void apply(Graph<InventoryVertex, InventoryEdge> graph,
                      Collection<InventoryAlarm> alarms,
                      Collection<InventorySituation> situations) {
        float nextX = 0.0f;
        float nextY = 0.0f;

        final List<InventoryVertex> sortedVertices = graph.getVertices().stream()
                .sorted(Comparator.comparing(InventoryVertex::getId))
                .collect(Collectors.toList());

        for (InventoryVertex v : sortedVertices) {
            v.setX(nextX);
            v.setY(nextY);

            nextX += 1.0f;
            nextY += 1.0f;
        }

        final List<InventoryAlarm> sortedAlarms =  alarms.stream()
                .sorted(Comparator.comparing(InventoryAlarm::getReductionKey))
                .collect(Collectors.toList());
        for (InventoryAlarm a : sortedAlarms) {
            a.setX(nextX);
            a.setY(nextY);

            nextX += 1.0f;
            nextY += 1.0f;
        }

        final List<InventorySituation> sortedSituations =  situations.stream()
                .sorted(Comparator.comparing(InventorySituation::getReductionKey))
                .collect(Collectors.toList());
        for (InventorySituation s : sortedSituations) {
            s.setX(nextX);
            s.setY(nextY);

            nextX += 1.0f;
            nextY += 1.0f;
        }
    }
}
