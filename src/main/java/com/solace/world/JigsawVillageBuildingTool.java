package com.solace.world;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.solace.mixin.SinglePoolElementAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public final class JigsawVillageBuildingTool {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VANILLA_PLAINS_TOWN_CENTER_PREFIX = "village/plains/town_centers/";
    private static final List<ResourceLocation> UNIQUE_PLAINS_BUILDINGS = List.of(
            id("villagersplus", "village/plains/plains_alchemist"),
            id("villagersplus", "village/plains/plains_horticulturist"),
            id("villagersplus", "village/plains/plains_occultist"),
            id("villagersplus", "village/plains/plains_oceanographer"),
            id("chefsdelight", "plains_chef_house"),
            id("chefsdelight", "plains_cook_house")
    );
    private static final Set<ResourceLocation> UNIQUE_PLAINS_BUILDING_SET = new HashSet<>(UNIQUE_PLAINS_BUILDINGS);
    private static final int MAX_RECENT_REPORTS = 8;
    private static final Deque<String> RECENT_REPORTS = new ArrayDeque<>();
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("solace.villageJigsawDiagnostics");
    private static final int MAX_DIAGNOSTIC_STARTS = 80;
    private static final int MAX_DIAGNOSTIC_PIECES = 200;
    private static final ResourceLocation AMBIENT_CANDIDATE_SESSION = id("solace", "ambient_plains_village_candidates");
    private static final AtomicInteger DIAGNOSTIC_STARTS = new AtomicInteger();
    private static final AtomicInteger DIAGNOSTIC_PIECES = new AtomicInteger();

    private static final ThreadLocal<Session> ACTIVE_SESSION = new ThreadLocal<>();

    private JigsawVillageBuildingTool() {
    }

    public static void begin(PoolElementStructurePiece startPiece) {
        Optional<ResourceLocation> startTemplate = templateId(startPiece.getElement());
        logObservedStart(startTemplate, startPiece);
        Session activeSession = ACTIVE_SESSION.get();
        if (activeSession != null) {
            activeSession.record(startPiece.getElement(), startPiece.getPosition(), startPiece.getBoundingBox());
            return;
        }

        if (startTemplate.isEmpty() || !isPlainsVillageStart(startTemplate.get())) {
            return;
        }

        Session session = new Session(startPiece.getPosition(), startTemplate.get());
        ACTIVE_SESSION.set(session);
        session.record(startPiece.getElement(), startPiece.getPosition(), startPiece.getBoundingBox());
        LOGGER.info("[Solace] Tracking plains village jigsaw at {} from {}; unique targets: {}", session.startPos, session.startTemplate, UNIQUE_PLAINS_BUILDINGS);
    }

    public static void end() {
        Session session = ACTIVE_SESSION.get();
        if (session == null) {
            return;
        }

        session.logSummary();
        ACTIVE_SESSION.remove();
    }

    public static List<StructurePoolElement> filterCandidates(List<StructurePoolElement> candidates) {
        Session session = ACTIVE_SESSION.get();
        if (candidates.isEmpty()) {
            return candidates;
        }

        if (session == null && hasUniqueCandidate(candidates)) {
            session = new Session(BlockPos.ZERO, AMBIENT_CANDIDATE_SESSION, true);
            ACTIVE_SESSION.set(session);
            LOGGER.info("[Solace] Tracking plains village jigsaw from ambient unique candidate pool; unique targets: {}", UNIQUE_PLAINS_BUILDINGS);
        }

        if (session == null) {
            return candidates;
        }

        List<StructurePoolElement> allowedNonUnique = new ArrayList<>(candidates.size());
        Map<ResourceLocation, StructurePoolElement> uniqueCandidates = new LinkedHashMap<>();
        Set<ResourceLocation> queuedUniqueIds = new LinkedHashSet<>();

        for (StructurePoolElement candidate : candidates) {
            Optional<ResourceLocation> templateId = templateId(candidate);
            if (templateId.isEmpty() || !UNIQUE_PLAINS_BUILDING_SET.contains(templateId.get())) {
                allowedNonUnique.add(candidate);
                continue;
            }

            ResourceLocation uniqueId = templateId.get();
            if (session.hasPlaced(uniqueId) || !queuedUniqueIds.add(uniqueId)) {
                session.recordSuppressed(uniqueId);
                continue;
            }

            uniqueCandidates.put(uniqueId, candidate);
        }

        if (uniqueCandidates.isEmpty()) {
            return allowedNonUnique;
        }

        ResourceLocation priorityId = session.selectPriorityCandidate(uniqueCandidates.keySet());
        List<StructurePoolElement> priorityFirstList = new ArrayList<>(allowedNonUnique.size() + uniqueCandidates.size());
        if (priorityId != null) {
            priorityFirstList.add(uniqueCandidates.get(priorityId));
            session.recordOffered(priorityId);
        }
        for (Map.Entry<ResourceLocation, StructurePoolElement> entry : uniqueCandidates.entrySet()) {
            if (!entry.getKey().equals(priorityId)) {
                priorityFirstList.add(entry.getValue());
            }
        }
        priorityFirstList.addAll(allowedNonUnique);
        if (DIAGNOSTICS) {
            LOGGER.info("[Solace] Prioritizing plains village unique building candidate: {}", priorityId);
        }
        return priorityFirstList;
    }

    public static StructurePoolElement filterRandomCandidate(StructurePoolElement selected, List<StructurePoolElement> candidates) {
        Optional<ResourceLocation> selectedId = templateId(selected);
        if (selectedId.isEmpty() || !UNIQUE_PLAINS_BUILDING_SET.contains(selectedId.get())) {
            return selected;
        }

        Session session = ACTIVE_SESSION.get();
        if (session == null) {
            session = new Session(BlockPos.ZERO, AMBIENT_CANDIDATE_SESSION, true);
            ACTIVE_SESSION.set(session);
            LOGGER.info("[Solace] Tracking plains village jigsaw from ambient random candidate pool; unique targets: {}", UNIQUE_PLAINS_BUILDINGS);
        }

        if (session.hasPlaced(selectedId.get()) || session.hasPending(selectedId.get())) {
            StructurePoolElement replacement = session.selectRandomReplacement(candidates);
            if (replacement != null) {
                Optional<ResourceLocation> replacementId = templateId(replacement);
                replacementId.ifPresent(id -> LOGGER.info("[Solace] Replacing duplicate plains village random candidate {} with {}", selectedId.get(), id));
                return replacement;
            }
        }

        session.recordOffered(selectedId.get());
        session.markPending(selectedId.get());
        return selected;
    }

    public static StructurePoolElement preparePieceElement(StructurePoolElement element) {
        Optional<ResourceLocation> elementId = templateId(element);
        Session session = ACTIVE_SESSION.get();

        if (session == null) {
            if (elementId.isPresent() && isPlainsVillageStart(elementId.get())) {
                Session ambientSession = new Session(BlockPos.ZERO, AMBIENT_CANDIDATE_SESSION, true);
                ACTIVE_SESSION.set(ambientSession);
                return element;
            }
            return element;
        }

        if (elementId.isPresent() && UNIQUE_PLAINS_BUILDING_SET.contains(elementId.get())) {
            ResourceLocation id = elementId.get();
            if (!session.hasPlaced(id) && !session.hasPending(id)) {
                session.markPending(id);
                return element;
            }

            ResourceLocation missingTarget = session.selectMissingTarget();
            if (missingTarget != null) {
                session.recordOffered(missingTarget);
                session.markPending(missingTarget);
                LOGGER.info("[Solace] Replacing duplicate plains village piece {} with missing unique building {}", id, missingTarget);
                return replacementElement(missingTarget, element);
            }

            session.recordSuppressed(id);
            LOGGER.info("[Solace] Replacing duplicate plains village piece {} with empty element", id);
            return emptyElement(element);
        }

        if (elementId.isPresent() && session.isReplaceableHouse(elementId.get())) {
            ResourceLocation missingTarget = session.selectMissingTarget();
            if (missingTarget != null) {
                session.recordOffered(missingTarget);
                session.markPending(missingTarget);
                LOGGER.info("[Solace] Replacing plains village house {} with required unique building {}", elementId.get(), missingTarget);
                return replacementElement(missingTarget, element);
            }
        }

        return element;
    }

    public static void recordPiece(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
        Session session = ACTIVE_SESSION.get();
        if (session != null) {
            Optional<ResourceLocation> activeTemplateId = templateId(element);
            if (activeTemplateId.isPresent() && isPlainsVillageStart(activeTemplateId.get()) && !session.matchesStart(position, activeTemplateId.get())) {
                session.logSummary();
                session.startNew(position, activeTemplateId.get());
                LOGGER.info("[Solace] Tracking plains village jigsaw at {} from observed start piece {}; unique targets: {}",
                        session.startPos, session.startTemplate, UNIQUE_PLAINS_BUILDINGS);
            }
            session.record(element, position, boundingBox);
            return;
        }

        Optional<ResourceLocation> templateId = templateId(element);
        if (templateId.isPresent() && isPlainsVillageStart(templateId.get())) {
            Session ambientSession = new Session(position, templateId.get(), true);
            ACTIVE_SESSION.set(ambientSession);
            ambientSession.record(element, position, boundingBox);
            LOGGER.info("[Solace] Tracking plains village jigsaw at {} from observed start piece {}; unique targets: {}",
                    ambientSession.startPos, ambientSession.startTemplate, UNIQUE_PLAINS_BUILDINGS);
            return;
        }

        logObservedPiece(element, position, boundingBox);
    }

    public static int activeSessionCount() {
        return ACTIVE_SESSION.get() == null ? 0 : 1;
    }

    public static boolean isDiagnosticsEnabled() {
        return DIAGNOSTICS;
    }

    public static boolean isUniqueTarget(ResourceLocation id) {
        return UNIQUE_PLAINS_BUILDING_SET.contains(id);
    }

    private static void logObservedPiece(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
        if (!DIAGNOSTICS || DIAGNOSTIC_PIECES.getAndIncrement() >= MAX_DIAGNOSTIC_PIECES) {
            return;
        }

        Optional<ResourceLocation> templateId = templateId(element);
        if (templateId.isEmpty()) {
            return;
        }

        ResourceLocation id = templateId.get();
        if (!isInterestingDiagnosticPiece(id)) {
            return;
        }

        LOGGER.info("[Solace] Observed jigsaw piece outside tracked plains village session: {} at {} with box {}",
                id, position, boundingBox);
    }

    public static List<String> describeRecentReports() {
        synchronized (RECENT_REPORTS) {
            return List.copyOf(RECENT_REPORTS);
        }
    }

    public static List<String> describeUniqueTargets() {
        List<String> targets = new ArrayList<>(UNIQUE_PLAINS_BUILDINGS.size());
        for (ResourceLocation target : UNIQUE_PLAINS_BUILDINGS) {
            targets.add(target.toString());
        }
        return Collections.unmodifiableList(targets);
    }

    private static Optional<ResourceLocation> templateId(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement)) {
            return Optional.empty();
        }

        Either<ResourceLocation, StructureTemplate> template = ((SinglePoolElementAccessor) element).solace$getTemplate();
        return template.left();
    }

    private static boolean hasUniqueCandidate(List<StructurePoolElement> candidates) {
        for (StructurePoolElement candidate : candidates) {
            Optional<ResourceLocation> templateId = templateId(candidate);
            if (templateId.isPresent() && UNIQUE_PLAINS_BUILDING_SET.contains(templateId.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlainsVillageStart(ResourceLocation templateId) {
        String path = templateId.getPath();
        if ("minecraft".equals(templateId.getNamespace()) && path.startsWith(VANILLA_PLAINS_TOWN_CENTER_PREFIX)) {
            return true;
        }

        return path.contains("village")
                && path.contains("plains")
                && path.contains("town_center");
    }

    private static boolean isInterestingDiagnosticPiece(ResourceLocation templateId) {
        if (UNIQUE_PLAINS_BUILDING_SET.contains(templateId)) {
            return true;
        }

        String namespace = templateId.getNamespace();
        String path = templateId.getPath();
        return path.contains("village")
                || path.contains("chef")
                || path.contains("cook")
                || path.contains("alchemist")
                || path.contains("horticulturist")
                || path.contains("occultist")
                || path.contains("oceanographer")
                || "villagersplus".equals(namespace)
                || "chefsdelight".equals(namespace);
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private static StructurePoolElement replacementElement(ResourceLocation target, StructurePoolElement original) {
        return StructurePoolElement.single(target.toString()).apply(original.getProjection());
    }

    private static StructurePoolElement emptyElement(StructurePoolElement original) {
        return StructurePoolElement.empty().apply(original.getProjection());
    }

    private static void logObservedStart(Optional<ResourceLocation> startTemplate, PoolElementStructurePiece startPiece) {
        if (!DIAGNOSTICS || DIAGNOSTIC_STARTS.getAndIncrement() >= MAX_DIAGNOSTIC_STARTS) {
            return;
        }

        LOGGER.info("[Solace] Observed jigsaw start template {} at {} using element {}",
                startTemplate.map(ResourceLocation::toString).orElse("<unknown>"),
                startPiece.getPosition(),
                startPiece.getElement().getClass().getName());
    }

    private static final class Session {
        private BlockPos startPos;
        private ResourceLocation startTemplate;
        private final boolean ambient;
        private final Map<ResourceLocation, Integer> spawnedCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> suppressedCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> offeredCounts = new LinkedHashMap<>();
        private final Set<ResourceLocation> pendingUniqueIds = new LinkedHashSet<>();
        private String lastLoggedUniqueSignal = "";

        private Session(BlockPos startPos, ResourceLocation startTemplate) {
            this(startPos, startTemplate, false);
        }

        private Session(BlockPos startPos, ResourceLocation startTemplate, boolean ambient) {
            this.startPos = startPos;
            this.startTemplate = startTemplate;
            this.ambient = ambient;
        }

        private void record(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
            Optional<ResourceLocation> templateId = templateId(element);
            if (templateId.isEmpty()) {
                return;
            }

            ResourceLocation id = templateId.get();
            spawnedCounts.merge(id, 1, Integer::sum);
            pendingUniqueIds.remove(id);
            if (UNIQUE_PLAINS_BUILDING_SET.contains(id)) {
                LOGGER.debug("[Solace] Plains village unique building placed: {} at {} with box {}", id, position, boundingBox);
            }

            if (ambient && id.getPath().contains("/terminators/")) {
                logSummary();
            }
        }

        private boolean isAmbient() {
            return ambient;
        }

        private boolean matchesStart(BlockPos position, ResourceLocation id) {
            return id.equals(startTemplate) && position.equals(startPos);
        }

        private void startNew(BlockPos startPos, ResourceLocation startTemplate) {
            this.startPos = startPos;
            this.startTemplate = startTemplate;
            this.spawnedCounts.clear();
            this.suppressedCounts.clear();
            this.offeredCounts.clear();
            this.pendingUniqueIds.clear();
            this.lastLoggedUniqueSignal = "";
        }

        private boolean hasPlaced(ResourceLocation id) {
            return spawnedCounts.getOrDefault(id, 0) > 0;
        }

        private void recordOffered(ResourceLocation id) {
            offeredCounts.merge(id, 1, Integer::sum);
        }

        private boolean hasPending(ResourceLocation id) {
            return pendingUniqueIds.contains(id);
        }

        private void markPending(ResourceLocation id) {
            pendingUniqueIds.add(id);
        }

        private void recordSuppressed(ResourceLocation id) {
            suppressedCounts.merge(id, 1, Integer::sum);
        }

        private StructurePoolElement selectRandomReplacement(List<StructurePoolElement> candidates) {
            StructurePoolElement nonUniqueFallback = null;
            for (ResourceLocation target : UNIQUE_PLAINS_BUILDINGS) {
                if (hasPlaced(target) || hasPending(target)) {
                    continue;
                }

                for (StructurePoolElement candidate : candidates) {
                    Optional<ResourceLocation> candidateId = templateId(candidate);
                    if (candidateId.isPresent() && candidateId.get().equals(target)) {
                        recordOffered(target);
                        markPending(target);
                        return candidate;
                    }
                }
            }

            for (StructurePoolElement candidate : candidates) {
                Optional<ResourceLocation> candidateId = templateId(candidate);
                if (candidateId.isEmpty() || !UNIQUE_PLAINS_BUILDING_SET.contains(candidateId.get())) {
                    nonUniqueFallback = candidate;
                    break;
                }
            }

            return nonUniqueFallback;
        }

        private ResourceLocation selectPriorityCandidate(Set<ResourceLocation> candidateIds) {
            ResourceLocation selected = null;
            int selectedOffers = Integer.MAX_VALUE;
            for (ResourceLocation target : UNIQUE_PLAINS_BUILDINGS) {
                if (hasPlaced(target) || !candidateIds.contains(target)) {
                    continue;
                }

                int offers = offeredCounts.getOrDefault(target, 0);
                if (selected == null || offers < selectedOffers) {
                    selected = target;
                    selectedOffers = offers;
                }
            }
            return selected;
        }

        private ResourceLocation selectMissingTarget() {
            for (ResourceLocation target : UNIQUE_PLAINS_BUILDINGS) {
                if (!hasPlaced(target) && !hasPending(target)) {
                    return target;
                }
            }
            return null;
        }

        private boolean isReplaceableHouse(ResourceLocation id) {
            if (UNIQUE_PLAINS_BUILDING_SET.contains(id)) {
                return false;
            }

            String path = id.getPath();
            return path.contains("/houses/")
                    || path.endsWith("_house")
                    || path.contains("_house_")
                    || path.contains("priest_house");
        }

        private void logSummary() {
            if (spawnedCounts.isEmpty()) {
                return;
            }
            if (!hasUniqueTargetSignal()) {
                return;
            }
            String uniqueSignal = uniqueSignal();
            if (uniqueSignal.equals(lastLoggedUniqueSignal)) {
                return;
            }
            lastLoggedUniqueSignal = uniqueSignal;

            String summary = "Plains village jigsaw at " + startPos + " from " + startTemplate + ": " + spawnedCounts;
            rememberReport(summary);
            LOGGER.info("[Solace] {}", summary);
            for (ResourceLocation uniqueId : UNIQUE_PLAINS_BUILDINGS) {
                int count = spawnedCounts.getOrDefault(uniqueId, 0);
                if (count == 0) {
                    LOGGER.warn("[Solace] Plains village did not place required unique building: {}", uniqueId);
                } else if (count > 1) {
                    LOGGER.warn("[Solace] Plains village placed duplicate unique building {} times: {}", count, uniqueId);
                }
            }

            if (!suppressedCounts.isEmpty()) {
                LOGGER.info("[Solace] Suppressed duplicate plains village jigsaw candidates: {}", suppressedCounts);
            }

            Set<ResourceLocation> offeredButMissing = new LinkedHashSet<>(offeredCounts.keySet());
            offeredButMissing.removeAll(spawnedCounts.keySet());
            if (!offeredButMissing.isEmpty()) {
                LOGGER.warn("[Solace] Plains village offered unique candidates that did not become pieces: {}", offeredButMissing);
            }
        }

        private boolean hasUniqueTargetSignal() {
            for (ResourceLocation uniqueId : UNIQUE_PLAINS_BUILDINGS) {
                if (spawnedCounts.containsKey(uniqueId)
                        || suppressedCounts.containsKey(uniqueId)
                        || offeredCounts.containsKey(uniqueId)
                        || pendingUniqueIds.contains(uniqueId)) {
                    return true;
                }
            }
            return false;
        }

        private String uniqueSignal() {
            StringBuilder signal = new StringBuilder();
            for (ResourceLocation uniqueId : UNIQUE_PLAINS_BUILDINGS) {
                signal.append(uniqueId)
                        .append('=')
                        .append(spawnedCounts.getOrDefault(uniqueId, 0))
                        .append('/')
                        .append(suppressedCounts.getOrDefault(uniqueId, 0))
                        .append('/')
                        .append(offeredCounts.getOrDefault(uniqueId, 0))
                        .append('/')
                        .append(pendingUniqueIds.contains(uniqueId))
                        .append(';');
            }
            return signal.toString();
        }
    }

    private static void rememberReport(String report) {
        synchronized (RECENT_REPORTS) {
            RECENT_REPORTS.addFirst(report);
            while (RECENT_REPORTS.size() > MAX_RECENT_REPORTS) {
                RECENT_REPORTS.removeLast();
            }
        }
    }
}
