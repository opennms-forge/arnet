package org.opennms.arnet.app.domain;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.arnet.app.mock.MockConsumerService;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.opennms.arnet.app.domain.NetworkManagerTest.hasPosition;

public class ForceBasedLayoutStrategyTest implements NetworkListener {

    @Test
    public void vertexWithMostEdgesShouldBeCenteredAtOrigin() {
        NetworkManager networkManager = new NetworkManager(this);
        networkManager.setLayoutStrategy(new ForceBasedLayoutStrategy());

        MockConsumerService mockConsumerService = new MockConsumerService();
        mockConsumerService.accept(networkManager);

        // Graph should have known number of vertices and edges
        // We'll need to update these when we update the mock
        List<InventoryVertex> vertices = networkManager.getInventoryVertices();
        assertThat(vertices, hasSize(5));

        List<InventoryEdge> edges = networkManager.getInventoryEdges();
        assertThat(edges, hasSize(4));

        float epsilon = 0.001f;
        InventoryVertex vertexAtOrigin = vertices.stream()
                .filter(v -> Math.abs(v.getX()) < epsilon && Math.abs(v.getY()) < epsilon).findAny()
                // this will throw if not present, and that's what we want
                .get();
        // Let's be sure about this
        assertThat((double)vertexAtOrigin.getX(), closeTo(0.0d, epsilon));
        assertThat((double)vertexAtOrigin.getY(), closeTo(0.0d, epsilon));
    }

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

    public static <T> LambdaMatcher<T> matching(Predicate<T> predicate, String description) {
        return new LambdaMatcher<T>(predicate, description);
    }

   public static class LambdaMatcher<T> extends BaseMatcher<T> {
        private final Predicate<T> predicate;
        private final String description;

         public LambdaMatcher(Predicate<T> predicate, String description) {
            this.predicate = predicate;
            this.description = description;
        }

        @Override
        public boolean matches(Object argument) {
            return predicate.test((T) argument);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(this.description);
        }
    }
}
