package org.opennms.oia.streaming.client;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opennms.integration.api.v1.config.events.AlarmType;
import org.opennms.integration.api.v1.model.*;
import org.opennms.oia.streaming.client.api.Consumer;
import org.opennms.oia.streaming.client.api.model.*;
import org.opennms.oia.streaming.client.api.model.Alarm;
import org.opennms.oia.streaming.model.AlarmDelete;
import org.opennms.oia.streaming.model.MessageType;
import org.opennms.oia.streaming.model.StreamMessage;
import org.opennms.oia.streaming.model.Topology;

import java.util.*;

import edu.uci.ics.jung.graph.Graph;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

public class ConsumerServiceTest {

    private WebSocketConsumerService wsConsumer;

    private Consumer consumer = Mockito.mock(Consumer.class);

    @Before
    public void setUp() {
        wsConsumer = new WebSocketConsumerService("ws://localhost:8080");
    }

    @Test
    public void testTopology() {
        ArgumentCaptor<Graph<Vertex, Edge>> graphCap = ArgumentCaptor.forClass((Class) Graph.class);
        ArgumentCaptor<List<Alarm>> alarmCap = ArgumentCaptor.forClass((Class) List.class);
        ArgumentCaptor<List<Situation>> situationCap = ArgumentCaptor.forClass((Class) List.class);

        wsConsumer.accept(consumer);
        wsConsumer.processTopology(generateTopology());

        verify(consumer, times(1)).accept(
                graphCap.capture(), alarmCap.capture(), situationCap.capture());

        Graph<Vertex, Edge> graph = graphCap.getValue();
        Collection<Alarm> alarms = alarmCap.getValue();
        Collection<Situation> situations = situationCap.getValue();

        assertThat(alarms, hasSize(1));
        assertThat(situations, hasSize(1));

        assertEquals(5, graph.getEdgeCount());
        assertEquals(8, graph.getVertexCount());

        wsConsumer.dismiss(consumer);
    }

    @Test
    public void testAlarm() {
        ArgumentCaptor<Alarm> alarmCap = ArgumentCaptor.forClass((Class) Alarm.class);

        wsConsumer.accept(consumer);

        org.opennms.integration.api.v1.model.Alarm alarm =
                generateAlarm(101, "reduc-key-101", false);

        wsConsumer.processAlarm(new StreamMessage(MessageType.Alarm, alarm));

        verify(consumer, times(1)).acceptAlarm(alarmCap.capture());

        assertEquals(alarm.getReductionKey(), alarmCap.getValue().getReductionKey());
    }

    @Test
    public void testAlarmDelete() {
        ArgumentCaptor<String> alarmCap = ArgumentCaptor.forClass((Class) String.class);

        wsConsumer.accept(consumer);

        org.opennms.integration.api.v1.model.Alarm alarm =
                generateAlarm(101, "reduc-key-1", false);

        wsConsumer.processAlarmDelete(new StreamMessage(MessageType.AlarmDelete,
                new AlarmDelete(alarm.getReductionKey(), alarm.isSituation())));

        verify(consumer, times(1)).acceptDeletedAlarm(alarmCap.capture());

        assertEquals(alarm.getReductionKey(), alarmCap.getValue());
    }

    @Test
    public void testNodeAdd() {
        ArgumentCaptor<Vertex> vertexCap = ArgumentCaptor.forClass((Class) Vertex.class);

        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        Node existingNode = generateNode(10, "node-10-label");
        Node newNode = generateNode(50, "node-50-label");

        wsConsumer.processNode(new StreamMessage(MessageType.Node, existingNode));
        wsConsumer.processNode(new StreamMessage(MessageType.Node, newNode));

        // Ensure that existing nodes don't result invoking the consumer... only new ones should.
        verify(consumer, times(1)).acceptVertex(vertexCap.capture());

        assertEquals(newNode.getId().toString(), vertexCap.getValue().getId());

        assertEquals(numEdgesOrig, wsConsumer.numEdges());
        assertEquals(numVerticesOrig + 1, wsConsumer.numVertices());
    }

    @Test
    public void testEdgeAdd() {
        ArgumentCaptor<Edge> edgeCap = ArgumentCaptor.forClass((Class) Edge.class);

        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        // Add edge that contains one vertex already in graph and one that is new.
        TopologyEdge edge = generateTopologyEdge("edge-6", TopologyProtocol.ALL,
                generateNode(25, "node-25-label"), TopologyEdge.EndpointType.NODE,
                generatePort(26), TopologyEdge.EndpointType.PORT);

        wsConsumer.processEdge(new StreamMessage(MessageType.Edge, edge));

        verify(consumer, times(1)).acceptEdge(edgeCap.capture());

        assertEquals(edge.getId() + "-" + edge.getProtocol(), edgeCap.getValue().getId());
        assertEquals(edge.getProtocol().name(), edgeCap.getValue().getProtocol());
        assertEquals("25", edgeCap.getValue().getSourceVertex().getId());
        assertEquals("26", edgeCap.getValue().getTargetVertex().getId());

        assertEquals(numEdgesOrig + 1, wsConsumer.numEdges());
        assertEquals(numVerticesOrig + 1, wsConsumer.numVertices());
    }

    @Test
    public void testEdgeAddAllNew() {
        ArgumentCaptor<Edge> edgeCap = ArgumentCaptor.forClass((Class) Edge.class);

        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        // Add edge where both vertices don't already exist in graph
        TopologyEdge edge = generateTopologyEdge("edge-50", TopologyProtocol.ALL,
                generateNode(50, "node-50-label"), TopologyEdge.EndpointType.NODE,
                generatePort(51), TopologyEdge.EndpointType.PORT);

        wsConsumer.processEdge(new StreamMessage(MessageType.Edge, edge));

        verify(consumer, times(1)).acceptEdge(edgeCap.capture());

        assertEquals(edge.getId() + "-" + edge.getProtocol(), edgeCap.getValue().getId());
        assertEquals(edge.getProtocol().name(), edgeCap.getValue().getProtocol());
        assertEquals("50", edgeCap.getValue().getSourceVertex().getId());
        assertEquals("51", edgeCap.getValue().getTargetVertex().getId());

        assertEquals(numEdgesOrig + 1, wsConsumer.numEdges());
        assertEquals(numVerticesOrig + 2, wsConsumer.numVertices());
    }

    @Test
    public void testEdgeAddDuplicate() {
        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        // Add edge that is a duplicate.
        TopologyEdge edge = generateTopologyEdge("edge-5", TopologyProtocol.ALL,
                generatePort(24), TopologyEdge.EndpointType.PORT,
                generateNode(25, "node-25-label"), TopologyEdge.EndpointType.NODE);

        wsConsumer.processEdge(new StreamMessage(MessageType.Edge, edge));

        verify(consumer, times(0)).acceptEdge(anyObject());
        assertEquals(numEdgesOrig, wsConsumer.numEdges());
        assertEquals(numVerticesOrig, wsConsumer.numVertices());
    }

    @Test
    public void testEdgeDelete() {
        ArgumentCaptor<String> edgeIdCap = ArgumentCaptor.forClass((Class) String.class);

        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        // Remove an existing edge
        TopologyEdge edge = generateTopologyEdge("edge-5", TopologyProtocol.ALL,
                generatePort(24), TopologyEdge.EndpointType.PORT,
                generateNode(25, "node-25-label"), TopologyEdge.EndpointType.NODE);

        wsConsumer.processEdgeDelete(new StreamMessage(MessageType.EdgeDelete, edge));

        verify(consumer, times(1)).acceptDeletedEdge(edgeIdCap.capture());
        assertEquals(edge.getId() + "-" + edge.getProtocol().name(), edgeIdCap.getValue());
        assertEquals(numEdgesOrig - 1, wsConsumer.numEdges());
        assertEquals(numVerticesOrig, wsConsumer.numVertices());
    }

    @Test
    public void testEdgeDeleteNonExistent() {
        wsConsumer.processTopology(generateTopology());
        wsConsumer.accept(consumer);

        int numEdgesOrig = wsConsumer.numEdges();
        int numVerticesOrig = wsConsumer.numVertices();

        // Remove an edge that does not exist
        TopologyEdge edge = generateTopologyEdge("edge-9", TopologyProtocol.ALL,
                generateNode(25, "node-25-label"), TopologyEdge.EndpointType.NODE,
                generateNode(20, "node-20-label"), TopologyEdge.EndpointType.NODE);

        wsConsumer.processEdgeDelete(new StreamMessage(MessageType.EdgeDelete, edge));

        verify(consumer, times(0)).acceptDeletedEdge(any());
        assertEquals(numEdgesOrig, wsConsumer.numEdges());
        assertEquals(numVerticesOrig, wsConsumer.numVertices());
    }

    @Test
    public void testEvent() {
        ArgumentCaptor<Event> eventCap = ArgumentCaptor.forClass((Class) Event.class);

        wsConsumer.accept(consumer);

        InMemoryEvent event = generateEvent("test-uei", 10);
        wsConsumer.processEvent(new StreamMessage(MessageType.Event, event));

        verify(consumer, times(1)).acceptEvent(eventCap.capture());
        assertEquals(event.getUei(), eventCap.getValue().getUEI());
        assertEquals(event.getNodeId().toString(), eventCap.getValue().getVertexId());
    }

    private StreamMessage generateTopology() {

        Set<org.opennms.integration.api.v1.model.Node> nodes = new HashSet<>();
        Set<org.opennms.integration.api.v1.model.TopologyEdge> edges = new HashSet<>();
        Set<org.opennms.integration.api.v1.model.Alarm> alarms = new HashSet<>();

        alarms.add(generateAlarm(100, "reduc-key-0", false));
        alarms.add(generateAlarm(101, "reduc-key-1", true));

        // Add x2 "floating" nodes (e.g. they have no edges)
        nodes.add(generateNode(10, "node-10-label"));
        nodes.add(generateNode(11, "node-11-label"));

        // Verify that there is no duplication in the graph's vertices when a node is present
        // in both Topology.nodes and in TopologyEdge
        nodes.add(generateNode(20, "node-20-label"));

        // Add x6 nodes with x5 vertices
        edges.add(generateTopologyEdge("edge-1", TopologyProtocol.ALL,
                generateNode(20, "node-20-label"), TopologyEdge.EndpointType.NODE,
                generateNode(21, "node-21-label"), TopologyEdge.EndpointType.NODE));

        edges.add(generateTopologyEdge("edge-2", TopologyProtocol.ALL,
                generateNode(20, "node-20-label"), TopologyEdge.EndpointType.NODE,
                generatePort(22), TopologyEdge.EndpointType.PORT));

        edges.add(generateTopologyEdge("edge-3", TopologyProtocol.ALL,
                generatePort(22), TopologyEdge.EndpointType.PORT,
                generateSegment(23), TopologyEdge.EndpointType.SEGMENT));

        edges.add(generateTopologyEdge("edge-4", TopologyProtocol.ALL,
                generateSegment(23), TopologyEdge.EndpointType.SEGMENT,
                generatePort(24), TopologyEdge.EndpointType.PORT));

        edges.add(generateTopologyEdge("edge-5", TopologyProtocol.ALL,
                generatePort(24), TopologyEdge.EndpointType.PORT,
                generateNode(25, "node-25-label"), TopologyEdge.EndpointType.NODE));

        return new StreamMessage(MessageType.Topology,
                new Topology(nodes, edges, alarms));
    }


    private org.opennms.integration.api.v1.model.Alarm generateAlarm(
            Integer id, String reducKey, boolean isSituation) {

        return new org.opennms.integration.api.v1.model.Alarm() {
            @Override
            public String getReductionKey() {
                return reducKey;
            }

            @Override
            public Integer getId() {
                return id;
            }

            @Override
            public Node getNode() {
                return null;
            }

            @Override
            public AlarmType getType() {
                return null;
            }

            @Override
            public String getManagedObjectInstance() {
                return null;
            }

            @Override
            public String getManagedObjectType() {
                return null;
            }

            @Override
            public Map<String, String> getAttributes() {
                return null;
            }

            @Override
            public Severity getSeverity() {
                return null;
            }

            @Override
            public boolean isSituation() {
                return isSituation;
            }

            @Override
            public List<org.opennms.integration.api.v1.model.Alarm> getRelatedAlarms() {
                return null;
            }

            @Override
            public String getLogMessage() {
                return null;
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public Date getLastEventTime() {
                return null;
            }

            @Override
            public Date getFirstEventTime() {
                return null;
            }

            @Override
            public DatabaseEvent getLastEvent() {
                return null;
            }
        };
    }

    private org.opennms.integration.api.v1.model.Node generateNode(Integer id, String label) {
        return new Node() {
            @Override
            public Integer getId() {
                return id;
            }

            @Override
            public String getForeignSource() {
                return null;
            }

            @Override
            public String getForeignId() {
                return null;
            }

            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getLocation() {
                return null;
            }

            @Override
            public NodeAssetRecord getAssetRecord() {
                return null;
            }

            @Override
            public List<IpInterface> getIpInterfaces() {
                return null;
            }

            @Override
            public List<SnmpInterface> getSnmpInterfaces() {
                return null;
            }

            @Override
            public List<MetaData> getMetaData() {
                return null;
            }
        };
    }

    private TopologyEdge generateTopologyEdge(String id, TopologyProtocol protocol,
                                              Object srcNode, TopologyEdge.EndpointType srcType,
                                              Object dstNode, TopologyEdge.EndpointType dstType) {
        return new TopologyEdge() {
            @Override
            public TopologyProtocol getProtocol() {
                return protocol;
            }

            @Override
            public void visitEndpoints(EndpointVisitor v) {

                switch(srcType) {
                    case NODE:
                        v.visitSource((Node) srcNode);
                        break;
                    case PORT:
                        v.visitSource((TopologyPort) srcNode);
                        break;
                    case SEGMENT:
                        v.visitSource((TopologySegment) srcNode);
                        break;
                }

                switch(dstType) {
                    case NODE:
                        v.visitTarget((Node) dstNode);
                        break;
                    case PORT:
                        v.visitTarget((TopologyPort) dstNode);
                        break;
                    case SEGMENT:
                        v.visitTarget((TopologySegment) dstNode);
                        break;
                }
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getTooltipText() {
                return null;
            }
        };
    }

    private TopologyPort generatePort(Integer id) {
        return new TopologyPort() {
            @Override
            public Integer getIfIndex() {
                return null;
            }

            @Override
            public String getIfName() {
                return null;
            }

            @Override
            public String getIfAddress() {
                return null;
            }

            @Override
            public NodeCriteria getNodeCriteria() {
                return null;
            }

            @Override
            public String getId() {
                return String.valueOf(id);
            }

            @Override
            public String getTooltipText() {
                return null;
            }
        };
    }

    private TopologySegment generateSegment(Integer id) {
        return new TopologySegment() {
            @Override
            public TopologyProtocol getProtocol() {
                return null;
            }

            @Override
            public String getSegmentCriteria() {
                return null;
            }

            @Override
            public String getId() {
                return String.valueOf(id);
            }

            @Override
            public String getTooltipText() {
                return null;
            }
        };
    }

    private InMemoryEvent generateEvent(String uei, Integer nodeId) {
       return new InMemoryEvent() {
           @Override
           public String getUei() {
               return uei;
           }

           @Override
           public String getSource() {
               return null;
           }

           @Override
           public Severity getSeverity() {
               return null;
           }

           @Override
           public Integer getNodeId() {
               return nodeId;
           }

           @Override
           public List<EventParameter> getParameters() {
               return null;
           }

           @Override
           public Optional<String> getParameterValue(String s) {
               return Optional.empty();
           }

           @Override
           public List<EventParameter> getParametersByName(String s) {
               return null;
           }
       };
    }
}
