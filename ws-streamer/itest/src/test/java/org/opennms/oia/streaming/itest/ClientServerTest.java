/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oia.streaming.itest;

import edu.uci.ics.jung.graph.Graph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opennms.integration.api.v1.dao.AlarmDao;
import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.events.EventSubscriptionService;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.TopologyEdge;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyEdge;
import org.opennms.oia.streaming.OiaWebSocketServer;
import org.opennms.oia.streaming.client.WebSocketConsumerService;
import org.opennms.oia.streaming.client.api.model.Alarm;
import org.opennms.oia.streaming.client.api.model.Edge;
import org.opennms.oia.streaming.client.api.model.Situation;
import org.opennms.oia.streaming.client.api.model.Vertex;
import org.springframework.util.SocketUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientServerTest {
    private static final String TEST_LOCATION = "Ottawa";
    private static final String TEST_LABEL = "Test Label";

    private static final AlarmDao mockedAlarmDao = mock(AlarmDao.class);
    private static final NodeDao mockedNodeDao = mock(NodeDao.class);
    private static final EdgeDao mockedEdgeDao = mock(EdgeDao.class);
    private static final EventSubscriptionService mockedEventSubscriptionService = mock(EventSubscriptionService.class);

    private static final Node initialNodeA = ImmutableNode.newBuilder()
            .setId(1)
            .setLocation(TEST_LOCATION)
            .setLabel(TEST_LABEL + "-a")
            .build();

    private static final Node initialNodeZ = ImmutableNode.newBuilder()
            .setId(2)
            .setLocation(TEST_LOCATION)
            .setLabel(TEST_LABEL + "-z")
            .build();

    private static final List<Node> initialNodes = List.of(initialNodeA, initialNodeZ);

    private static final Set<org.opennms.integration.api.v1.model.Alarm> initialAlarms = Set.of(
            ImmutableAlarm.newBuilder()
                    .setId(1)
                    .setReductionKey("alarm1")
                    .setNode(initialNodeA)
                    .build(),
            ImmutableAlarm.newBuilder()
                    .setId(2)
                    .setReductionKey("alarm2")
                    .setNode(initialNodeZ)
                    .build()
    );

    private static final Set<org.opennms.integration.api.v1.model.Alarm> initialSituations = Set.of(
            ImmutableAlarm.newBuilder()
                    .setId(3)
                    .setReductionKey("situation1")
                    .setRelatedAlarms(new ArrayList<>(initialAlarms))
                    .build()
    );

    private static final Set<TopologyEdge> initialEdges = Set.of(
            ImmutableTopologyEdge.newBuilder()
                    .setId("a-z")
                    .setProtocol(TopologyProtocol.USERDEFINED)
                    .setSource(initialNodeA)
                    .setTarget(initialNodeZ)
                    .build()
    );

    @BeforeAll
    public static void setupMocks() {
        var alarmsAndSituations = new ArrayList<org.opennms.integration.api.v1.model.Alarm>();
        alarmsAndSituations.addAll(initialAlarms);
        alarmsAndSituations.addAll(initialSituations);
        when(mockedAlarmDao.getAlarms()).thenReturn(alarmsAndSituations);

        when(mockedNodeDao.getNodes()).thenReturn(initialNodes);
        when(mockedEdgeDao.getEdges()).thenReturn(initialEdges);
    }

    @Test
    public void canReceiveInitialTopology() throws InterruptedException, IOException {
        var port = SocketUtils.findAvailableTcpPort();

        var server = new OiaWebSocketServer(port, mockedAlarmDao, mockedNodeDao, mockedEdgeDao,
                mockedEventSubscriptionService);
        server.start();

        var consumerService = new WebSocketConsumerService("ws://localhost:" + port);
        consumerService.start();

        var received = new AtomicBoolean(false);
        var receivedGraph = new AtomicReference<Graph<Vertex, Edge>>(null);
        var receivedAlarms = new AtomicReference<Collection<Alarm>>(null);
        var receivedSituations = new AtomicReference<Collection<Situation>>(null);
        consumerService.accept(new NoOpConsumer() {
            @Override
            public void accept(Graph<Vertex, Edge> graph, Collection<Alarm> alarms, Collection<Situation> situations) {
                receivedGraph.set(graph);
                receivedAlarms.set(alarms);
                receivedSituations.set(situations);
                received.set(true);
            }
        });

        try {
            await().atMost(1, TimeUnit.SECONDS).until(received::get);

            // Just containing the initial alarms
            var receivedAlarmReductionKeys = receivedAlarms.get()
                    .stream()
                    .map(Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            var initialAlarmReductionKeys = initialAlarms.stream()
                    .map(org.opennms.integration.api.v1.model.Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            assertThat(initialAlarmReductionKeys, equalTo(receivedAlarmReductionKeys));

            // One situation containing all the initial alarms
            assertThat(receivedSituations.get(), hasSize(1));
            var receivedFirstSituationRelatedReductionKeys = receivedSituations.get()
                    .iterator()
                    .next()
                    .getRelatedAlarms()
                    .stream()
                    .map(Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            assertThat(receivedFirstSituationRelatedReductionKeys, equalTo(initialAlarmReductionKeys));

            var vertices = receivedGraph.get().getVertices();
            assertThat(vertices, hasSize(2));
            var vertexIds = vertices.stream()
                    .map(v -> Integer.parseInt(v.getId()))
                    .collect(Collectors.toSet());
            assertThat(vertexIds, hasItems(initialNodeA.getId(), initialNodeZ.getId()));

            // One edge connecting a to z
            var edges = receivedGraph.get().getEdges();
            assertThat(edges, hasSize(1));
            var edge = edges.iterator().next();
            assertThat(Integer.parseInt(edge.getSourceVertex().getId()), equalTo(initialNodeA.getId()));
            assertThat(Integer.parseInt(edge.getTargetVertex().getId()), equalTo(initialNodeZ.getId()));
        } finally {
            consumerService.stop();
            server.stop();
        }
    }
}
