package com.solace.world;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.solace.config.VillageBuildingSpawnConfig;
import com.solace.config.PerformanceConfig;
import com.solace.mixin.SinglePoolElementAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public final class JigsawVillageBuildingTool {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VANILLA_PLAINS_TOWN_CENTER_PREFIX = "village/plains/town_centers/";
    private static final int MAX_RECENT_REPORTS = 8;
    private static final Deque<String> RECENT_REPORTS = new ArrayDeque<>();
    private static final boolean SYSTEM_DIAGNOSTICS = Boolean.getBoolean("solace.villageJigsawDiagnostics");
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
        if (diagnostics()) {
            LOGGER.info("[Solace] Tracking plains village jigsaw at {} from {}; configured buildings: {}", session.startPos, session.startTemplate, VillageBuildingSpawnConfig.describeEntries());
        }
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

        if (session == null && hasCustomCandidate(candidates)) {
            session = new Session(BlockPos.ZERO, AMBIENT_CANDIDATE_SESSION, true);
            ACTIVE_SESSION.set(session);
            if (diagnostics()) {
                LOGGER.info("[Solace] Tracking plains village jigsaw from ambient custom candidate pool; configured buildings: {}", VillageBuildingSpawnConfig.describeEntries());
            }
        }

        if (session == null) {
            List<StructurePoolElement> filtered = new ArrayList<>();
            for (StructurePoolElement candidate : candidates) {
                Optional<ResourceLocation> templateId = templateId(candidate);
                if (templateId.isPresent() && isZombiePiece(templateId.get())) {
                    continue;
                }
                filtered.add(candidate);
            }
            return filtered;
        }

        List<StructurePoolElement> allowedNonUnique = new ArrayList<>(candidates.size());
        List<StructurePoolElement> customCandidates = new ArrayList<>();
        Set<ResourceLocation> customCandidateIds = new LinkedHashSet<>();
        Map<ResourceLocation, Integer> queuedCustomCounts = new LinkedHashMap<>();
        boolean hasReplaceableHouseCandidate = false;

        for (StructurePoolElement candidate : candidates) {
            Optional<ResourceLocation> templateId = templateId(candidate);
            templateId.ifPresent(JigsawVillageBuildingTool::discoverBuilding);
            if (templateId.isEmpty()) {
                allowedNonUnique.add(candidate);
                continue;
            }
            if (isZombiePiece(templateId.get())) {
                continue;
            }
            if (!isCustomBuilding(templateId.get())) {
                allowedNonUnique.add(candidate);
                if (session.isReplaceableHouse(templateId.get())) {
                    hasReplaceableHouseCandidate = true;
                }
                continue;
            }

            ResourceLocation customId = templateId.get();
            int queued = queuedCustomCounts.getOrDefault(customId, 0);
            if (!session.needsMore(customId, queued)) {
                session.recordSuppressed(customId);
                continue;
            }

            customCandidates.add(candidate);
            customCandidateIds.add(customId);
            queuedCustomCounts.merge(customId, 1, Integer::sum);
        }

        if (hasReplaceableHouseCandidate) {
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (customCandidateIds.contains(target)) {
                    continue;
                }
                int queued = queuedCustomCounts.getOrDefault(target, 0);
                if (!session.needsMore(target, queued)) {
                    continue;
                }

                customCandidates.add(StructurePoolElement.single(target.toString()).apply(StructureTemplatePool.Projection.RIGID));
                customCandidateIds.add(target);
                queuedCustomCounts.merge(target, 1, Integer::sum);
            }
        }

        if (customCandidates.isEmpty()) {
            return allowedNonUnique;
        }

        ResourceLocation priorityId = session.selectPriorityCandidate(customCandidateIds);
        List<StructurePoolElement> priorityFirstList = new ArrayList<>(allowedNonUnique.size() + 1);
        if (priorityId != null) {
            for (StructurePoolElement candidate : customCandidates) {
                Optional<ResourceLocation> candidateId = templateId(candidate);
                if (candidateId.isPresent() && candidateId.get().equals(priorityId)) {
                    priorityFirstList.add(candidate);
                    break;
                }
            }
            session.recordOffered(priorityId);
            session.markPending(priorityId);
        }
        priorityFirstList.addAll(allowedNonUnique);
        if (diagnostics()) {
            LOGGER.info("[Solace] Prioritizing plains village custom building candidate: {}", priorityId);
        }
        return priorityFirstList;
    }

    public static StructurePoolElement filterRandomCandidate(StructurePoolElement selected, List<StructurePoolElement> candidates) {
        Optional<ResourceLocation> selectedId = templateId(selected);
        selectedId.ifPresent(JigsawVillageBuildingTool::discoverBuilding);
        
        if (selectedId.isPresent() && isZombiePiece(selectedId.get())) {
            for (StructurePoolElement candidate : candidates) {
                Optional<ResourceLocation> candidateId = templateId(candidate);
                if (candidateId.isPresent() && !isZombiePiece(candidateId.get())) {
                    return filterRandomCandidate(candidate, candidates);
                }
            }
        }

        if (selectedId.isEmpty() || !isCustomBuilding(selectedId.get())) {
            return selected;
        }

        Session session = ACTIVE_SESSION.get();
        if (session == null) {
            session = new Session(BlockPos.ZERO, AMBIENT_CANDIDATE_SESSION, true);
            ACTIVE_SESSION.set(session);
            if (diagnostics()) {
                LOGGER.info("[Solace] Tracking plains village jigsaw from ambient random candidate pool; configured buildings: {}", VillageBuildingSpawnConfig.describeEntries());
            }
        }

        if (!session.needsMore(selectedId.get(), 0)) {
            StructurePoolElement replacement = session.selectRandomReplacement(candidates);
            if (replacement != null) {
                Optional<ResourceLocation> replacementId = templateId(replacement);
                if (diagnostics()) {
                    replacementId.ifPresent(id -> LOGGER.info("[Solace] Replacing excess plains village random candidate {} with {}", selectedId.get(), id));
                }
                return replacement;
            }
        }

        session.recordOffered(selectedId.get());
        session.markPending(selectedId.get());
        return selected;
    }

    public static void recordPiece(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
        Session session = ACTIVE_SESSION.get();
        if (session != null) {
            Optional<ResourceLocation> activeTemplateId = templateId(element);
            if (activeTemplateId.isPresent() && isPlainsVillageStart(activeTemplateId.get()) && !session.matchesStart(position, activeTemplateId.get())) {
                session.logSummary();
                session.startNew(position, activeTemplateId.get());
                if (diagnostics()) {
                    LOGGER.info("[Solace] Tracking plains village jigsaw at {} from observed start piece {}; configured buildings: {}",
                            session.startPos, session.startTemplate, VillageBuildingSpawnConfig.describeEntries());
                }
            }
            session.record(element, position, boundingBox);
            return;
        }

        Optional<ResourceLocation> templateId = templateId(element);
        if (templateId.isPresent() && isPlainsVillageStart(templateId.get())) {
            Session ambientSession = new Session(position, templateId.get(), true);
            ACTIVE_SESSION.set(ambientSession);
            ambientSession.record(element, position, boundingBox);
            if (diagnostics()) {
                LOGGER.info("[Solace] Tracking plains village jigsaw at {} from observed start piece {}; configured buildings: {}",
                        ambientSession.startPos, ambientSession.startTemplate, VillageBuildingSpawnConfig.describeEntries());
            }
            return;
        }

        logObservedPiece(element, position, boundingBox);
    }

    public static int activeSessionCount() {
        return ACTIVE_SESSION.get() == null ? 0 : 1;
    }

    public static boolean isDiagnosticsEnabled() {
        return diagnostics();
    }

    public static boolean isUniqueTarget(ResourceLocation id) {
        return isCustomBuilding(id);
    }

    private static void logObservedPiece(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
        if (!diagnostics() || DIAGNOSTIC_PIECES.getAndIncrement() >= MAX_DIAGNOSTIC_PIECES) {
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
        return VillageBuildingSpawnConfig.describeEntries();
    }

    public static Optional<ResourceLocation> templateId(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement)) {
            return Optional.empty();
        }

        Either<ResourceLocation, StructureTemplate> template = ((SinglePoolElementAccessor) element).solace$getTemplate();
        return template.left();
    }

    public static boolean isZombiePiece(ResourceLocation id) {
        String path = id.getPath();
        return path.contains("/zombie/") || path.contains("_zombie_") || path.contains("zombie/");
    }

    private static boolean hasCustomCandidate(List<StructurePoolElement> candidates) {
        for (StructurePoolElement candidate : candidates) {
            Optional<ResourceLocation> templateId = templateId(candidate);
            if (templateId.isPresent()) {
                discoverBuilding(templateId.get());
            }
            if (templateId.isPresent() && isCustomBuilding(templateId.get())) {
                return true;
            }
        }
        return false;
    }

    private static void discoverBuilding(ResourceLocation id) {
        if (isConfigurableBuilding(id)) {
            VillageBuildingSpawnConfig.discover(id);
        }
    }

    private static boolean isCustomBuilding(ResourceLocation id) {
        if (!isConfigurableBuilding(id)) {
            return false;
        }
        VillageBuildingSpawnConfig.Mode mode = VillageBuildingSpawnConfig.discover(id).mode;
        return mode == VillageBuildingSpawnConfig.Mode.ONCE || mode == VillageBuildingSpawnConfig.Mode.RANGE;
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
        if (isConfigurableBuilding(templateId)) {
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

    private static boolean isConfigurableBuilding(ResourceLocation templateId) {
        if (isPlainsVillageStart(templateId)) {
            return false;
        }

        String path = templateId.getPath();
        if (path.contains("/streets/")
                || path.contains("/terminators/")
                || path.contains("/villagers/")
                || path.contains("/zombie/")
                || path.contains("/decor/")
                || path.contains("/lamp")
                || path.contains("plains_lamp")) {
            return false;
        }

        if (path.contains("village/") && path.contains("plains")) {
            return true;
        }

        return path.startsWith("plains_")
                && (path.endsWith("_house") || path.contains("_house_") || path.contains("chef") || path.contains("cook"));
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private static void logObservedStart(Optional<ResourceLocation> startTemplate, PoolElementStructurePiece startPiece) {
        if (!diagnostics() || DIAGNOSTIC_STARTS.getAndIncrement() >= MAX_DIAGNOSTIC_STARTS) {
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
        private final Map<ResourceLocation, Integer> pendingCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> targetCounts = new LinkedHashMap<>();
        private String lastLoggedCustomSignal = "";

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
            decrementPending(id);
            if (isCustomBuilding(id) && diagnostics()) {
                LOGGER.debug("[Solace] Plains village custom building placed: {} at {} with box {}", id, position, boundingBox);
            }

            if (ambient && id.getPath().contains("/terminators/")) {
                logSummary();
            }
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
            this.pendingCounts.clear();
            this.targetCounts.clear();
            this.lastLoggedCustomSignal = "";
        }

        private void recordOffered(ResourceLocation id) {
            offeredCounts.merge(id, 1, Integer::sum);
        }

        private void markPending(ResourceLocation id) {
            pendingCounts.merge(id, 1, Integer::sum);
        }

        private void decrementPending(ResourceLocation id) {
            int pending = pendingCounts.getOrDefault(id, 0);
            if (pending <= 1) {
                pendingCounts.remove(id);
            } else {
                pendingCounts.put(id, pending - 1);
            }
        }

        private boolean needsMore(ResourceLocation id, int queued) {
            int target = targetCount(id);
            if (target < 0) {
                return false;
            }
            return spawnedCounts.getOrDefault(id, 0) + pendingCounts.getOrDefault(id, 0) + queued < target;
        }

        private int targetCount(ResourceLocation id) {
            return targetCounts.computeIfAbsent(id, target -> VillageBuildingSpawnConfig.chooseTargetCount(target, startPos, startTemplate));
        }

        private void recordSuppressed(ResourceLocation id) {
            suppressedCounts.merge(id, 1, Integer::sum);
        }

        private StructurePoolElement selectRandomReplacement(List<StructurePoolElement> candidates) {
            StructurePoolElement nonUniqueFallback = null;
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (!needsMore(target, 0)) {
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
                if (candidateId.isEmpty() || !isCustomBuilding(candidateId.get())) {
                    nonUniqueFallback = candidate;
                    break;
                }
            }

            return nonUniqueFallback;
        }

        private ResourceLocation selectPriorityCandidate(Set<ResourceLocation> candidateIds) {
            ResourceLocation selected = null;
            int selectedOffers = Integer.MAX_VALUE;
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (!needsMore(target, 0) || !candidateIds.contains(target)) {
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

        private boolean isReplaceableHouse(ResourceLocation id) {
            if (isCustomBuilding(id)) {
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
            if (!hasCustomTargetSignal()) {
                return;
            }
            String customSignal = customSignal();
            if (customSignal.equals(lastLoggedCustomSignal)) {
                return;
            }
            lastLoggedCustomSignal = customSignal;

            String summary = "Plains village jigsaw at " + startPos + " from " + startTemplate + ": " + spawnedCounts;
            rememberReport(summary);
            if (!diagnostics()) {
                return;
            }
            LOGGER.info("[Solace] {}", summary);
            for (ResourceLocation customId : VillageBuildingSpawnConfig.customBuildingIds()) {
                int target = targetCount(customId);
                int count = spawnedCounts.getOrDefault(customId, 0);
                if (count < target) {
                    LOGGER.warn("[Solace] Plains village placed {} of required {} custom building {}", count, target, customId);
                } else if (count > target) {
                    LOGGER.warn("[Solace] Plains village placed {} above target {} for custom building {}", count, target, customId);
                }
            }

            if (!suppressedCounts.isEmpty()) {
                LOGGER.info("[Solace] Suppressed duplicate plains village jigsaw candidates: {}", suppressedCounts);
            }

            Set<ResourceLocation> offeredButMissing = new LinkedHashSet<>(offeredCounts.keySet());
            offeredButMissing.removeAll(spawnedCounts.keySet());
            if (!offeredButMissing.isEmpty()) {
                LOGGER.warn("[Solace] Plains village offered custom candidates that did not become pieces: {}", offeredButMissing);
            }
        }

        private boolean hasCustomTargetSignal() {
            for (ResourceLocation customId : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (spawnedCounts.containsKey(customId)
                        || suppressedCounts.containsKey(customId)
                        || offeredCounts.containsKey(customId)
                        || pendingCounts.containsKey(customId)) {
                    return true;
                }
            }
            return false;
        }

        private String customSignal() {
            StringBuilder signal = new StringBuilder();
            for (ResourceLocation customId : VillageBuildingSpawnConfig.customBuildingIds()) {
                signal.append(customId)
                        .append('=')
                        .append(spawnedCounts.getOrDefault(customId, 0))
                        .append('/')
                        .append(suppressedCounts.getOrDefault(customId, 0))
                        .append('/')
                        .append(offeredCounts.getOrDefault(customId, 0))
                        .append('/')
                        .append(pendingCounts.getOrDefault(customId, 0))
                        .append('/')
                        .append(targetCount(customId))
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

    private static boolean diagnostics() {
        return SYSTEM_DIAGNOSTICS || PerformanceConfig.isVillageDiagnosticsEnabled();
    }
}
