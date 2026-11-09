package com.solace.worldgen;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/** Independent implementation; idea similar to Feature Recycler. No code from it. */
public final class FeatureCycleResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> IN_ORIGINAL = new ThreadLocal<>();
    private static final Comparator<Node> VANILLA_ORDER = Comparator.comparingInt((Node node) -> node.step)
            .thenComparingInt(node -> node.featureIndex);
    private static final Comparator<Edge> REMOVAL_ORDER = Comparator.comparingInt((Edge edge) -> edge.sources.size())
            .thenComparingInt(edge -> edge.sources.first())
            .thenComparing(edge -> edge.from, VANILLA_ORDER)
            .thenComparing(edge -> edge.to, VANILLA_ORDER);

    private FeatureCycleResolver() { }

    public static <T> List<FeatureSorter.StepFeatureData> withFallback(
            List<T> sources, Function<T, List<HolderSet<PlacedFeature>>> featureListGetter,
            Supplier<List<FeatureSorter.StepFeatureData>> original) {
        // Vanilla recursively calls itself to reduce the biome witness. Let that finish unchanged.
        if (Boolean.TRUE.equals(IN_ORIGINAL.get())) return original.get();
        IN_ORIGINAL.set(true);
        try {
            return original.get();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() == null
                    || !exception.getMessage().startsWith("Feature order cycle found")) throw exception;
        } finally {
            IN_ORIGINAL.remove();
        }
        return resolve(sources, featureListGetter);
    }

    public static <T> List<FeatureSorter.StepFeatureData> resolve(
            List<T> sources, Function<T, List<HolderSet<PlacedFeature>>> featureListGetter) {
        // Vanilla assigns a global first-seen index using PlacedFeature.equals, then sorts by step/index.
        Map<PlacedFeature, Integer> featureIndices = new HashMap<>();
        Map<Node, Node> graph = new TreeMap<>(VANILLA_ORDER);
        int stepCount = 0;
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            List<HolderSet<PlacedFeature>> steps = featureListGetter.apply(sources.get(sourceIndex));
            stepCount = Math.max(stepCount, steps.size());
            Node previous = null;
            for (int step = 0; step < steps.size(); step++) {
                for (Holder<PlacedFeature> holder : steps.get(step)) {
                    PlacedFeature feature = holder.value();
                    int index = featureIndices.computeIfAbsent(feature, ignored -> featureIndices.size());
                    Node candidate = new Node(step, index, feature,
                            holder.unwrapKey().map(key -> key.location().toString()).orElse("inline#" + index));
                    Node node = graph.computeIfAbsent(candidate, ignored -> candidate);
                    if (previous != null) {
                        Node from = previous;
                        from.outgoing.computeIfAbsent(node, ignored -> new Edge(from, node, feature)).sources.add(sourceIndex);
                    }
                    previous = node;
                }
            }
        }

        List<Node> nodes = new ArrayList<>(graph.values());
        List<Edge> removed = new ArrayList<>();
        Edge conflict;
        while ((conflict = weakestCyclicEdge(nodes)) != null) {
            conflict.from.outgoing.remove(conflict.to);
            removed.add(conflict);
        }

        // Revisit deletions in reverse order so every final deletion is necessary for acyclicity.
        for (int index = removed.size() - 1; index >= 0; index--) {
            Edge edge = removed.get(index);
            if (!reaches(edge.to, edge.from)) {
                edge.from.outgoing.put(edge.to, edge);
                removed.remove(index);
            }
        }

        List<Node> sorted = new ArrayList<>();
        for (Node node : nodes) node.visited = false;
        for (Node node : nodes) visitInVanillaOrder(node, node.feature, sorted);
        Collections.reverse(sorted);
        List<List<PlacedFeature>> steps = new ArrayList<>();
        for (int step = 0; step < stepCount; step++) steps.add(new ArrayList<>());
        for (Node node : sorted) steps.get(node.step).add(node.sortedFeature);
        ImmutableList.Builder<FeatureSorter.StepFeatureData> result = ImmutableList.builder();
        for (List<PlacedFeature> features : steps) {
            result.add(new FeatureSorter.StepFeatureData(features, Util.createIndexLookup(features,
                    size -> new Object2IntOpenCustomHashMap<>(size, Util.identityStrategy()))));
        }
        for (Edge edge : removed) {
            List<String> overridden = edge.sources.stream().map(index -> sourceName(sources.get(index), index)).toList();
            LOGGER.warn("[Solace] Resolved feature order cycle: step={}, removed={} -> {}, overriddenBiomes={}",
                    edge.from.step, edge.from.name, edge.to.name, overridden);
        }
        return result.build();
    }

    private static String sourceName(Object source, int index) {
        if (source instanceof Holder<?> holder) {
            return holder.unwrapKey().map(key -> key.location().toString()).orElse("inline-biome#" + index);
        }
        // FeatureSorter is generic. Keep diagnostic output deterministic for non-holder sources too.
        return source instanceof CharSequence ? source.toString() : "source#" + index;
    }

    private static Edge weakestCyclicEdge(List<Node> nodes) {
        for (Node node : nodes) {
            node.dfsIndex = -1;
            node.onStack = false;
        }
        Components components = new Components();
        for (Node node : nodes) {
            if (node.dfsIndex < 0) components.visit(node);
        }
        Edge weakest = null;
        for (Node node : nodes) {
            for (Edge edge : node.outgoing.values()) {
                if (node.component == edge.to.component
                        && (node.componentSize > 1 || node == edge.to)
                        && (weakest == null || REMOVAL_ORDER.compare(edge, weakest) < 0)) {
                    weakest = edge;
                }
            }
        }
        return weakest;
    }

    private static boolean reaches(Node start, Node target) {
        Deque<Node> pending = new ArrayDeque<>();
        TreeSet<Node> visited = new TreeSet<>(VANILLA_ORDER);
        pending.push(start);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node == target) return true;
            if (visited.add(node)) pending.addAll(node.outgoing.keySet());
        }
        return false;
    }

    private static void visitInVanillaOrder(Node node, PlacedFeature encounteredFeature, List<Node> sorted) {
        if (node.visited) return;
        node.visited = true;
        // Vanilla's comparator coalesces equal features but DFS retains the first visited occurrence's identity.
        node.sortedFeature = encounteredFeature;
        for (Edge edge : node.outgoing.values()) visitInVanillaOrder(edge.to, edge.destinationFeature, sorted);
        sorted.add(node);
    }

    /** Tarjan SCCs. All traversal state belongs to this invocation. */
    private static final class Components {
        private final Deque<Node> stack = new ArrayDeque<>();
        private int nextIndex;
        private int nextComponent;

        private void visit(Node node) {
            node.dfsIndex = nextIndex++;
            node.lowLink = node.dfsIndex;
            stack.push(node);
            node.onStack = true;
            for (Node next : node.outgoing.keySet()) {
                if (next.dfsIndex < 0) {
                    visit(next);
                    node.lowLink = Math.min(node.lowLink, next.lowLink);
                } else if (next.onStack) {
                    node.lowLink = Math.min(node.lowLink, next.dfsIndex);
                }
            }
            if (node.lowLink == node.dfsIndex) {
                List<Node> members = new ArrayList<>();
                Node member;
                do {
                    member = stack.pop();
                    member.onStack = false;
                    member.component = nextComponent;
                    members.add(member);
                } while (member != node);
                for (Node item : members) item.componentSize = members.size();
                nextComponent++;
            }
        }
    }

    private static final class Node {
        private final int step;
        private final int featureIndex;
        private final PlacedFeature feature;
        private final String name;
        private final Map<Node, Edge> outgoing = new TreeMap<>(VANILLA_ORDER);
        private PlacedFeature sortedFeature;
        private int dfsIndex;
        private int lowLink;
        private int component;
        private int componentSize;
        private boolean onStack;
        private boolean visited;

        private Node(int step, int featureIndex, PlacedFeature feature, String name) {
            this.step = step;
            this.featureIndex = featureIndex;
            this.feature = feature;
            this.name = name;
        }
    }

    private static final class Edge {
        private final Node from;
        private final Node to;
        private final PlacedFeature destinationFeature;
        private final TreeSet<Integer> sources = new TreeSet<>();

        private Edge(Node from, Node to, PlacedFeature destinationFeature) {
            this.from = from;
            this.to = to;
            this.destinationFeature = destinationFeature;
        }
    }
}
