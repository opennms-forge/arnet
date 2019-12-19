package org.opennms.arnet.app.domain;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.opennms.arnet.app.mock.MockConsumerService;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class NetworkManagerTest implements NetworkListener {

    @Test
    public void canComputeLayout() {
        NetworkManager networkManager = new NetworkManager(this);
        networkManager.setLayoutStrategy(new DiagonalLayoutStrategy());

        MockConsumerService mockConsumerService = new MockConsumerService();
        mockConsumerService.accept(networkManager);

        List<InventoryVertex> vertices = networkManager.getInventoryVertices();
        assertThat(vertices, hasSize(5));
        assertThat(vertices.get(0), hasPosition(0.0f, 0.0f));
        assertThat(vertices.get(1), hasPosition(1.0f, 1.0f));
        assertThat(vertices.get(2), hasPosition(2.0f, 2.0f));
        assertThat(vertices.get(3), hasPosition(3.0f, 3.0f));
        assertThat(vertices.get(4), hasPosition(4.0f, 4.0f));

        List<InventoryEdge> edges = networkManager.getInventoryEdges();
        assertThat(edges, hasSize(4));
    }

    public static Matcher<InventoryVertex> hasPosition(float x, float y) {
        final float delta = 0.001f;
        return new BaseMatcher<InventoryVertex>() {

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("x=%.2f, y=%.2f", x, y));
            }

            @Override
            public boolean matches(Object item) {
                InventoryVertex v = (InventoryVertex)item;
                return Math.abs(v.getX() - x) < delta && Math.abs(v.getY() - y) < delta;
            }
        };
    }
}
