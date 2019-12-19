package org.opennms.arnet.app.domain;

import org.junit.Ignore;
import org.junit.Test;
import org.opennms.arnet.app.mock.MockConsumerService;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.opennms.arnet.app.domain.NetworkManagerTest.hasPosition;

public class ForceBasedLayoutStrategyTest implements NetworkListener {

    @Ignore("Coordinates change...")
    @Test
    public void canComputeLayout() {
        NetworkManager networkManager = new NetworkManager(this);
        networkManager.setLayoutStrategy(new ForceBasedLayoutStrategy());

        MockConsumerService mockConsumerService = new MockConsumerService();
        mockConsumerService.accept(networkManager);

        List<InventoryVertex> vertices = networkManager.getInventoryVertices();
        assertThat(vertices, hasSize(3));
        assertThat(vertices.get(0), hasPosition(0.61063f, 0.1119333f));
        assertThat(vertices.get(1), hasPosition(-0.8403397f, 0.6453804f));
        assertThat(vertices.get(2), hasPosition(-0.27969894f, -0.7817006f));

        List<InventoryEdge> edges = networkManager.getInventoryEdges();
        assertThat(edges, hasSize(2));
    }
}
