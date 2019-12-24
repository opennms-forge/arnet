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
import org.opennms.integration.api.v1.model.*;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyEdge;
import org.opennms.oia.streaming.OiaWebSocketServer;
import org.opennms.oia.streaming.client.WebSocketConsumerService;
import org.opennms.oia.streaming.client.api.ConsumerService;
import org.opennms.oia.streaming.client.api.model.*;
import org.springframework.util.SocketUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
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

    private static final List<Node> initialNodes = Arrays.asList(initialNodeA, initialNodeZ);

    private static final Set<org.opennms.integration.api.v1.model.Alarm> initialAlarms = new HashSet<>(
            Arrays.asList(
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
            )
    );

    private static final Set<org.opennms.integration.api.v1.model.Alarm> initialSituations = Collections.singleton(
            ImmutableAlarm.newBuilder()
                    .setId(3)
                    .setReductionKey("situation1")
                    .setRelatedAlarms(new ArrayList<>(initialAlarms))
                    .build()
    );

    private static final Set<TopologyEdge> initialEdges = Collections.singleton(
            ImmutableTopologyEdge.newBuilder()
                    .setId("a-z")
                    .setProtocol(TopologyProtocol.USERDEFINED)
                    .setSource(initialNodeA)
                    .setTarget(initialNodeZ)
                    .build()
    );

    @BeforeAll
    public static void setupMocks() {
        List<org.opennms.integration.api.v1.model.Alarm> alarmsAndSituations = new ArrayList<>();
        alarmsAndSituations.addAll(initialAlarms);
        alarmsAndSituations.addAll(initialSituations);
        when(mockedAlarmDao.getAlarms()).thenReturn(alarmsAndSituations);

        when(mockedNodeDao.getNodes()).thenReturn(initialNodes);
        when(mockedEdgeDao.getEdges()).thenReturn(initialEdges);
    }

    @Test
    public void canReceiveInitialTopology() throws InterruptedException, IOException {
        int port = SocketUtils.findAvailableTcpPort();

        OiaWebSocketServer server = new OiaWebSocketServer(port, mockedAlarmDao, mockedNodeDao, mockedEdgeDao,
                mockedEventSubscriptionService);
        server.start();

        ConsumerService consumerService = new WebSocketConsumerService("ws://localhost:" + port);
        consumerService.start();

        AtomicBoolean received = new AtomicBoolean(false);
        AtomicReference<Graph<Vertex, Edge>> receivedGraph = new AtomicReference<>(null);
        AtomicReference<Collection<org.opennms.oia.streaming.client.api.model.Alarm>> receivedAlarms =
                new AtomicReference<>(null);
        AtomicReference<Collection<Situation>> receivedSituations = new AtomicReference<>(null);
        consumerService.accept(new NoOpConsumer() {
            @Override
            public void accept(Graph<Vertex, Edge> graph,
                               Collection<org.opennms.oia.streaming.client.api.model.Alarm> alarms,
                               Collection<Situation> situations) {
                receivedGraph.set(graph);
                receivedAlarms.set(alarms);
                receivedSituations.set(situations);
                received.set(true);
            }
        });

        try {
            await().atMost(1, TimeUnit.SECONDS).until(received::get);

            // Just containing the initial alarms
            Set<String> receivedAlarmReductionKeys = receivedAlarms.get()
                    .stream()
                    .map(org.opennms.oia.streaming.client.api.model.Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            Set<String> initialAlarmReductionKeys = initialAlarms.stream()
                    .map(org.opennms.integration.api.v1.model.Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            assertThat(initialAlarmReductionKeys, equalTo(receivedAlarmReductionKeys));

            // One situation containing all the initial alarms
            assertThat(receivedSituations.get(), hasSize(1));
            Set<String> receivedFirstSituationRelatedReductionKeys = receivedSituations.get()
                    .iterator()
                    .next()
                    .getRelatedAlarms()
                    .stream()
                    .map(org.opennms.oia.streaming.client.api.model.Alarm::getReductionKey)
                    .collect(Collectors.toSet());
            assertThat(receivedFirstSituationRelatedReductionKeys, equalTo(initialAlarmReductionKeys));

            Collection<Vertex> vertices = receivedGraph.get().getVertices();
            assertThat(vertices, hasSize(2));
            Set<Integer> vertexIds = vertices.stream()
                    .map(v -> Integer.parseInt(v.getId()))
                    .collect(Collectors.toSet());
            assertThat(vertexIds, hasItems(initialNodeA.getId(), initialNodeZ.getId()));

            // One edge connecting a to z
            Collection<Edge> edges = receivedGraph.get().getEdges();
            assertThat(edges, hasSize(1));
            Edge edge = edges.iterator().next();
            assertThat(Integer.parseInt(edge.getSourceVertex().getId()), equalTo(initialNodeA.getId()));
            assertThat(Integer.parseInt(edge.getTargetVertex().getId()), equalTo(initialNodeZ.getId()));
        } finally {
            consumerService.stop();
            server.stop();
        }
    }

    @Test
    public void canHandleAlarm() throws InterruptedException, IOException {
        int port = SocketUtils.findAvailableTcpPort();

        OiaWebSocketServer server = new OiaWebSocketServer(port, mockedAlarmDao, mockedNodeDao, mockedEdgeDao,
                mockedEventSubscriptionService);
        server.start();

        ConsumerService consumerService = new WebSocketConsumerService("ws://localhost:" + port);
        consumerService.start();

        AtomicReference<org.opennms.oia.streaming.client.api.model.Alarm> receivedAlarm = new AtomicReference<>(null);

        AtomicBoolean received = new AtomicBoolean(false);
        AtomicBoolean gotAlarm = new AtomicBoolean(false);
        AtomicBoolean gotDeletedAlarm = new AtomicBoolean(false);
        AtomicReference<String> deletedAlarm = new AtomicReference<>(null);

        consumerService.accept(new NoOpConsumer() {
            @Override
            public void accept(Graph<Vertex, Edge> graph,
                               Collection<org.opennms.oia.streaming.client.api.model.Alarm> alarms,
                               Collection<Situation> situations) {
                // Don't care about the initial stuff...
                received.set(true);
            }

            @Override
            public void acceptAlarm(org.opennms.oia.streaming.client.api.model.Alarm alarm) {
                receivedAlarm.set(alarm);
                gotAlarm.set(true);
            }

            @Override
            public void acceptDeletedAlarm(String reductionKey) {
                deletedAlarm.set(reductionKey);
                gotDeletedAlarm.set(true);
            }
        });

        try {
            await().atMost(1, TimeUnit.SECONDS).until(received::get);

            Node node = ImmutableNode.newBuilder()
                    .setId(10).setLabel(TEST_LABEL).setLocation(TEST_LOCATION).build();
            Alarm alarm = ImmutableAlarm.newBuilder().
                    setId(100).setReductionKey("alarm-1234").setNode(node).build();

            // Add the alarm...
            server.handleNewOrUpdatedAlarm(alarm);

            await().atMost(1, TimeUnit.SECONDS).until(gotAlarm::get);

            assertNotNull(receivedAlarm.get());
            assertEquals(alarm.getReductionKey(), receivedAlarm.get().getReductionKey());

            // TODO: deserialization error occurs for alarm delete.
            //  AlarmDelete member 'isSituation' is serialized as 'situation' instead of 'isSituation'.
            //  Need to revise serialization of AlarmDelete.
//            server.handleDeletedAlarm(alarm.getId(), alarm.getReductionKey());
//
//            await().atMost(1, TimeUnit.SECONDS).until(gotDeletedAlarm::get);
//
//            assertNotNull(deletedAlarm.get());
//            assertEquals(alarm.getReductionKey(), deletedAlarm.get());
        }
        finally {
            consumerService.stop();
            server.stop();
        }
    }

//    @Test
    public void canHandleEvent() throws InterruptedException, IOException {
        // TODO: there is an event handling issue (server side).
        //  In method 'onEvent()', the node is handled prior to the event.
        //  In handling the node, the method 'generateNodeReceivers()' is invoked.
        //  When that occurs, each subscriber (client) that has "seen" the node is recorded in a Map.
        //  Then, to handle the event, the method 'generateNodeReceivers()' is invoked again.
        //  The event is not processed since the subscriber (client) has already "seen" the node.
        //  Swapping 'generateNodeReceivers()' for 'generateReceivers()' works (e.g. this test passes)...

        Node nodeB = ImmutableNode.newBuilder()
                .setId(8)
                .setLocation(TEST_LOCATION)
                .setLabel(TEST_LABEL + "-b")
                .build();

        when(mockedNodeDao.getNodeById(nodeB.getId())).thenReturn(nodeB);

        int port = SocketUtils.findAvailableTcpPort();

        OiaWebSocketServer server = new OiaWebSocketServer(port, mockedAlarmDao, mockedNodeDao, mockedEdgeDao,
                mockedEventSubscriptionService);
        server.start();

        ConsumerService consumerService = new WebSocketConsumerService("ws://localhost:" + port);
        consumerService.start();

        AtomicBoolean received = new AtomicBoolean(false);
        AtomicReference<Event> receivedEvent = new AtomicReference<>(null);
        AtomicBoolean gotEvent = new AtomicBoolean(false);

        consumerService.accept(new NoOpConsumer() {
            @Override
            public void accept(Graph<Vertex, Edge> graph,
                               Collection<org.opennms.oia.streaming.client.api.model.Alarm> alarms,
                               Collection<Situation> situations) {
                // Don't care about the initial stuff...
                received.set(true);
            }

            @Override
            public void acceptEvent(Event e) {
                receivedEvent.set(e);
                gotEvent.set(true);
            }
        });

        try {
            await().atMost(1, TimeUnit.SECONDS).until(received::get);

            InMemoryEvent event = ImmutableInMemoryEvent.newBuilder().setUei("test-uei")
                    .setSource("test-source").setNodeId(nodeB.getId()).build();

            server.onEvent(event);

            await().atMost(1, TimeUnit.SECONDS).until(gotEvent::get);

            assertNotNull(receivedEvent.get());
            assertEquals(receivedEvent.get().getUEI(), event.getUei());
        }
        finally {
            consumerService.stop();
            server.stop();
        }
    }

    @Test
    public void canHandleEdge() throws InterruptedException, IOException {
        int port = SocketUtils.findAvailableTcpPort();

        OiaWebSocketServer server = new OiaWebSocketServer(port, mockedAlarmDao, mockedNodeDao, mockedEdgeDao,
                mockedEventSubscriptionService);
        server.start();

        ConsumerService consumerService = new WebSocketConsumerService("ws://localhost:" + port);
        consumerService.start();

        AtomicBoolean received = new AtomicBoolean(false);
        AtomicBoolean gotEdge = new AtomicBoolean(false);
        AtomicReference<Edge> receivedEdge = new AtomicReference<>(null);
        AtomicReference<Collection<Vertex>> receivedVertices = new AtomicReference<>(new HashSet<>());

        AtomicBoolean gotDeletedEdge = new AtomicBoolean(false);
        AtomicReference<String> receivedDeletedEdge = new AtomicReference<>(null);

        consumerService.accept(new NoOpConsumer() {
            @Override
            public void accept(Graph<Vertex, Edge> graph,
                               Collection<org.opennms.oia.streaming.client.api.model.Alarm> alarms,
                               Collection<Situation> situations) {
                // Don't care about the initial stuff...
                received.set(true);
            }

            @Override
            public void acceptVertex(Vertex vertex) {
                receivedVertices.get().add(vertex);
            }

            @Override
            public void acceptEdge(Edge edge) {
                receivedEdge.set(edge);
                gotEdge.set(true);
            }

            @Override
            public void acceptDeletedEdge(String edgeId) {
                receivedDeletedEdge.set(edgeId);
                gotDeletedEdge.set(true);
            }
        });

        try {
            await().atMost(1, TimeUnit.SECONDS).until(received::get);

            Node srcNode = ImmutableNode.newBuilder()
                    .setId(5).setLocation(TEST_LOCATION).setLabel(TEST_LABEL + "-c").build();
            Node dstNode = ImmutableNode.newBuilder()
                    .setId(6).setLocation(TEST_LOCATION).setLabel(TEST_LABEL + "-d").build();

            TopologyEdge edge = ImmutableTopologyEdge.newBuilder().setId("c-d")
                    .setSource(srcNode).setTarget(dstNode).setProtocol(TopologyProtocol.ALL).build();

            // Add the edge...
            server.onEdgeAddedOrUpdated(edge);

            await().atMost(1, TimeUnit.SECONDS).until(gotEdge::get);

            assertNotNull(receivedEdge.get());
            assertEquals(receivedEdge.get().getProtocol(), edge.getProtocol().name());
            assertEquals(receivedEdge.get().getSourceVertex().getId(), srcNode.getId().toString());
            assertEquals(receivedEdge.get().getTargetVertex().getId(), dstNode.getId().toString());

            assertNotNull(receivedVertices.get());

            Set<String> receivedVertexIds = receivedVertices.get()
                    .stream().map(Vertex::getId).collect(Collectors.toSet());

            Set<String> expectedVertexIds = new HashSet<>(
                    Arrays.asList(srcNode.getId().toString(), dstNode.getId().toString()));

            assertThat(receivedVertexIds, equalTo(expectedVertexIds));

            // Delete the edge...
            server.onEdgeDeleted(edge);

            await().atMost(1, TimeUnit.SECONDS).until(gotDeletedEdge::get);

            assertNotNull(receivedDeletedEdge.get());
            assertEquals(receivedDeletedEdge.get(), edge.getId() + "-" + edge.getProtocol().name());
        }
        finally {
            consumerService.stop();
            server.stop();
        }
    }
}
