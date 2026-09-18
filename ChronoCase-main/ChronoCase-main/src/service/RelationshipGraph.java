package service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Graph of the relationships inside one case, built on plain JDK
 * collections (no external graph library).
 *
 * Nodes are the five entity kinds of the model: evidence,
 * characters, locations, events and timelines. Edges are typed
 * (SUPPORTS, CONTRADICTS, INVOLVES, ...). This class is deliberately
 * dumb plumbing: it stores nodes and edges and answers "who is
 * connected to whom, and how". The meaning of a relationship is
 * decided by its callers, e.g. the ContradictionDetector.
 *
 * Data structures, each with a real job:
 * - HashMap: node key -> adjacency set, the classic adjacency-list
 *   representation; O(1) node access.
 * - LinkedHashSet per node: that node's edges; a set because the
 *   same relationship must never be stored twice, and linked because
 *   insertion order makes reports deterministic.
 */
public class RelationshipGraph {

    /** The five entity kinds that can appear as nodes. */
    public enum NodeType {
        EVIDENCE, CHARACTER, LOCATION, EVENT, TIMELINE
    }

    /** Kinds of relationships the case knows about. */
    public enum Relation {
        SUPPORTS,      // evidence -> event it corroborates
        CONTRADICTS,   // evidence -> event it rules out
        INVOLVES,      // event/evidence -> character
        OCCURRED_AT,   // event/evidence -> location
        CONTAINS,      // timeline -> event
        LINKED_TO      // generic, any pair
    }

    /** Immutable (type, id) pair identifying one node. */
    public static final class NodeKey {
        private final NodeType type;
        private final int id;

        NodeKey(NodeType type, int id) {
            this.type = Objects.requireNonNull(type, "node type must not be null");
            this.id = id;
        }

        public NodeType getType() {
            return type;
        }

        public int getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NodeKey)) {
                return false;
            }
            NodeKey other = (NodeKey) o;
            return id == other.id && type == other.type;
        }

        @Override
        public int hashCode() {
            return 31 * type.hashCode() + id;
        }

        @Override
        public String toString() {
            return type.name().toLowerCase() + "#" + id;
        }
    }

    /** One typed, directed edge between two nodes. */
    public static final class Edge {
        private final NodeKey from;
        private final Relation relation;
        private final NodeKey to;

        Edge(NodeKey from, Relation relation, NodeKey to) {
            this.from = from;
            this.relation = relation;
            this.to = to;
        }

        public NodeKey getFrom() {
            return from;
        }

        public Relation getRelation() {
            return relation;
        }

        public NodeKey getTo() {
            return to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Edge)) {
                return false;
            }
            Edge other = (Edge) o;
            return from.equals(other.from) && to.equals(other.to) && relation == other.relation;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * from.hashCode() + relation.hashCode()) + to.hashCode();
        }

        @Override
        public String toString() {
            return from + " -" + relation + "-> " + to;
        }
    }

    private final Map<NodeKey, Set<Edge>> adjacency = new HashMap<>();

    /** Adds a node if absent; adding an existing node is a no-op. */
    public void addNode(NodeType type, int id) {
        adjacency.computeIfAbsent(new NodeKey(type, id), k -> new LinkedHashSet<>());
    }

    public boolean hasNode(NodeType type, int id) {
        return adjacency.containsKey(new NodeKey(type, id));
    }

    /**
     * Adds a typed edge, registering both endpoints as nodes and both
     * directions for traversal. Adding an identical edge twice stores
     * it once (edges live in sets).
     */
    public void addEdge(NodeType fromType, int fromId, Relation relation, NodeType toType, int toId) {
        NodeKey from = new NodeKey(fromType, fromId);
        NodeKey to = new NodeKey(toType, toId);
        Edge edge = new Edge(from, relation, to);
        adjacency.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(edge);
        adjacency.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(edge);
    }

    /** All nodes, read-only. */
    public Set<NodeKey> getNodes() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /**
     * Edges touching the given node (either direction). Empty set for
     * unknown nodes.
     */
    public Set<Edge> getEdgesTouching(NodeType type, int id) {
        Set<Edge> edges = adjacency.get(new NodeKey(type, id));
        return edges == null ? Collections.emptySet() : Collections.unmodifiableSet(edges);
    }

    /** True if the two nodes are connected by at least one edge. */
    public boolean areConnected(NodeType aType, int aId, NodeType bType, int bId) {
        return findPath(aType, aId, bType, bId).size() >= 2;
    }

    /**
     * Shortest path (BFS, undirected traversal) between two nodes,
     * inclusive of both endpoints. Empty when no path or a node is
     * unknown.
     */
    public List<NodeKey> findPath(NodeType fromType, int fromId, NodeType toType, int toId) {
        NodeKey start = new NodeKey(fromType, fromId);
        NodeKey goal = new NodeKey(toType, toId);
        if (!adjacency.containsKey(start) || !adjacency.containsKey(goal)) {
            return Collections.emptyList();
        }
        if (start.equals(goal)) {
            return Collections.singletonList(start);
        }
        Map<NodeKey, NodeKey> cameFrom = new HashMap<>();
        Set<NodeKey> visited = new HashSet<>();
        Deque<NodeKey> frontier = new ArrayDeque<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty()) {
            NodeKey current = frontier.poll();
            for (Edge edge : adjacency.get(current)) {
                NodeKey next = edge.from.equals(current) ? edge.to : edge.from;
                if (!visited.add(next)) {
                    continue;
                }
                cameFrom.put(next, current);
                if (next.equals(goal)) {
                    return reconstructPath(cameFrom, goal);
                }
                frontier.add(next);
            }
        }
        return Collections.emptyList();
    }

    private List<NodeKey> reconstructPath(Map<NodeKey, NodeKey> cameFrom, NodeKey goal) {
        List<NodeKey> path = new ArrayList<>();
        for (NodeKey n = goal; n != null; n = cameFrom.get(n)) {
            path.add(n);
        }
        Collections.reverse(path);
        return Collections.unmodifiableList(path);
    }

    public int getNodeCount() {
        return adjacency.size();
    }

    /** Total distinct edges (each edge touches two nodes; count once). */
    public int getEdgeCount() {
        int half = 0;
        for (Set<Edge> edges : adjacency.values()) {
            half += edges.size();
        }
        return half / 2;
    }
}
