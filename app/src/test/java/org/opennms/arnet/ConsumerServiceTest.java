package org.opennms.arnet;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opennms.arnet.api.Consumer;
import org.opennms.arnet.api.model.Alarm;
import org.opennms.arnet.api.model.Edge;
import org.opennms.arnet.api.model.Situation;
import org.opennms.arnet.api.model.Vertex;
import org.opennms.integration.api.v1.config.events.AlarmType;
import org.opennms.integration.api.v1.model.DatabaseEvent;
import org.opennms.integration.api.v1.model.IpInterface;
import org.opennms.integration.api.v1.model.MetaData;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.NodeAssetRecord;
import org.opennms.integration.api.v1.model.NodeCriteria;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.SnmpInterface;
import org.opennms.integration.api.v1.model.TopologyEdge;
import org.opennms.integration.api.v1.model.TopologyPort;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.integration.api.v1.model.TopologySegment;
import org.opennms.oia.streaming.model.MessageType;
import org.opennms.oia.streaming.model.StreamMessage;
import org.opennms.oia.streaming.model.Topology;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.ics.jung.graph.Graph;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ConsumerServiceTest {

    private WebSocketConsumerService wsConsumer = new WebSocketConsumerService();

    private Consumer consumer = Mockito.mock(Consumer.class);

    @Test
    public void testTopology() {
        wsConsumer.accept(consumer);

        ArgumentCaptor<Graph<Vertex, Edge>> graphCap = ArgumentCaptor.forClass((Class) Graph.class);
        ArgumentCaptor<List<Alarm>> alarmCap = ArgumentCaptor.forClass((Class) List.class);
        ArgumentCaptor<List<Situation>> situationCap = ArgumentCaptor.forClass((Class) List.class);

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
        edges.add(generateTopologyEdge("edge-1",
                generateNode(20, "node-20-label"), TopologyEdge.EndpointType.NODE,
                generateNode(21, "node-21-label"), TopologyEdge.EndpointType.NODE));

        edges.add(generateTopologyEdge("edge-2",
                generateNode(20, "node-20-label"), TopologyEdge.EndpointType.NODE,
                generatePort(22), TopologyEdge.EndpointType.PORT));

        edges.add(generateTopologyEdge("edge-3",
                generatePort(22), TopologyEdge.EndpointType.PORT,
                generateSegment(23), TopologyEdge.EndpointType.SEGMENT));

        edges.add(generateTopologyEdge("edge-4",
                generateSegment(23), TopologyEdge.EndpointType.SEGMENT,
                generatePort(24), TopologyEdge.EndpointType.PORT));

        edges.add(generateTopologyEdge("edge-5",
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

    private TopologyEdge generateTopologyEdge(String id,
                                              Object srcNode, TopologyEdge.EndpointType srcType,
                                              Object dstNode, TopologyEdge.EndpointType dstType) {
        return new TopologyEdge() {
            @Override
            public TopologyProtocol getProtocol() {
                return TopologyProtocol.OSPF;
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
}