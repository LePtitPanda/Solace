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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final AtomicInteger DIAGNOSTIC_STARTS = new AtomicInteger();
    private static final Pattern ELEMENT_TEMPLATE_ID = Pattern.compile("([a-z0-9_.-]+):([a-z0-9_./-]+)");

    private static final ThreadLocal<Session> ACTIVE_SESSION = new ThreadLocal<>();

    private JigsawVillageBuildingTool() {
    }

    public static void begin(PoolElementStructurePiece startPiece) {
        Optional<ResourceLocation> startTemplate = templateId(startPiece.getElement());
        logObservedStart(startTemplate, startPiece);
        if (startTemplate.isEmpty() || !isPlainsVillageStart(startTemplate.get())) {
            clearSession();
            return;
        }

        Session session = activateSession(startPiece, startTemplate.get());
        session.record(startPiece.getElement(), startPiece.getPosition(), startPiece.getBoundingBox());
    }

    public static void end(List<PoolElementStructurePiece> pieces) {
        Session session = ACTIVE_SESSION.get();
        if (session == null) {
            return;
        }

        session.reconcile(pieces);
        session.logSummary();
        ACTIVE_SESSION.remove();
    }

    public static List<StructurePoolElement> filterCandidates(List<StructurePoolElement> candidates) {
        Session session = ACTIVE_SESSION.get();
        if (candidates.isEmpty()) {
            return candidates;
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

        List<StructurePoolElement> allowedCandidates = new ArrayList<>(candidates.size());
        Map<ResourceLocation, StructurePoolElement> requiredCandidates = new LinkedHashMap<>();
        boolean hasBuildingAttachmentCandidate = false;
        boolean hasUnmetMinimum = session.hasUnmetMinimum();

        for (StructurePoolElement candidate : candidates) {
            Optional<ResourceLocation> templateId = templateId(candidate);
            templateId.ifPresent(JigsawVillageBuildingTool::discoverBuilding);
            if (templateId.isEmpty()) {
                allowedCandidates.add(candidate);
                continue;
            }
            if (isZombiePiece(templateId.get())) {
                continue;
            }
            if (!isCustomBuilding(templateId.get())) {
                allowedCandidates.add(candidate);
                if (session.isReplaceableHouse(templateId.get()) || isPlainsTerminator(templateId.get())) {
                    hasBuildingAttachmentCandidate = true;
                }
                continue;
            }

            ResourceLocation customId = templateId.get();
            if (!session.canPlace(customId)) {
                session.recordSuppressed(customId);
                continue;
            }
            if (hasUnmetMinimum && !session.needsMinimum(customId)) {
                continue;
            }

            allowedCandidates.add(candidate);
            if (session.needsMinimum(customId)) {
                requiredCandidates.putIfAbsent(customId, candidate);
            }
        }

        if (hasBuildingAttachmentCandidate) {
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (requiredCandidates.containsKey(target)) {
                    continue;
                }
                if (!session.needsMinimum(target)) {
                    continue;
                }

                requiredCandidates.put(target, StructurePoolElement.single(target.toString()).apply(StructureTemplatePool.Projection.RIGID));
            }
        }

        if (requiredCandidates.isEmpty()) {
            return allowedCandidates;
        }

        // Fixed priority, not round-robin: a village has fewer house slots than the config's minimums add up to,
        // so the buildings listed first (ONCE before RANGE) must win the slots and only the last-listed ones miss.
        ResourceLocation priorityId = null;
        List<StructurePoolElement> priorityFirstList = new ArrayList<>(allowedCandidates.size() + requiredCandidates.size());
        for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
            StructurePoolElement required = requiredCandidates.get(target);
            if (required == null) {
                continue;
            }
            if (priorityId == null) {
                priorityId = target;
                session.recordPriority(target);
            }
            priorityFirstList.add(required);
            session.recordOffered(target);
        }
        for (StructurePoolElement candidate : allowedCandidates) {
            Optional<ResourceLocation> candidateId = templateId(candidate);
            if (candidateId.isPresent() && requiredCandidates.containsKey(candidateId.get())) {
                continue;
            }
            priorityFirstList.add(candidate);
        }
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
            return selected;
        }

        if (!session.canPlace(selectedId.get())) {
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
        session.recordPriority(selectedId.get());
        return selected;
    }

    public static void enterParentElement(StructurePoolElement parentElement, BlockPos position) {
        Optional<ResourceLocation> parentId = templateId(parentElement);
        if (parentId.isEmpty()) {
            return;
        }

        ResourceLocation id = parentId.get();
        if (isPlainsVillageStart(id)) {
            activateSession(position, id);
            return;
        }

        if (isPlainsVillagePiece(id)) {
            if (ACTIVE_SESSION.get() == null) {
                activateSession(position, id);
            }
            return;
        }

        if (ACTIVE_SESSION.get() != null && isConfigurableBuilding(id)) {
            return;
        }

        if (id.getPath().contains("village/")) {
            return;
        }

        clearSession();
    }

    public static void recordAcceptedPiece(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
        Session session = ACTIVE_SESSION.get();
        if (session != null) {
            session.record(element, position, boundingBox);
        }
    }

    public static boolean isUniqueTarget(ResourceLocation id) {
        return isCustomBuilding(id);
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
        if (element instanceof SinglePoolElement) {
            Either<ResourceLocation, StructureTemplate> template = ((SinglePoolElementAccessor) element).solace$getTemplate();
            Optional<ResourceLocation> directId = template.left();
            if (directId.isPresent()) {
                return directId;
            }
        }

        Matcher matcher = ELEMENT_TEMPLATE_ID.matcher(element.toString());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new ResourceLocation(matcher.group(1), matcher.group(2)));
    }

    public static boolean isZombiePiece(ResourceLocation id) {
        String path = id.getPath();
        return path.contains("/zombie/") || path.contains("_zombie_") || path.contains("zombie/");
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

    private static boolean isPlainsVillagePiece(ResourceLocation templateId) {
        return templateId.getPath().contains("village/plains/");
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

    private static boolean isPlainsTerminator(ResourceLocation id) {
        return id.getPath().contains("village/plains/terminators/");
    }

    private static Session activateSession(PoolElementStructurePiece startPiece, ResourceLocation startTemplate) {
        return activateSession(startPiece.getPosition(), startTemplate);
    }

    private static Session activateSession(BlockPos startPosition, ResourceLocation startTemplate) {
        Session activeSession = ACTIVE_SESSION.get();
        if (activeSession != null && activeSession.matchesStart(startPosition, startTemplate)) {
            return activeSession;
        }
        if (activeSession != null) {
            activeSession.logSummary();
        }

        Session session = new Session(startPosition, startTemplate);
        ACTIVE_SESSION.set(session);
        LOGGER.info("[Solace] Tracking plains village jigsaw at {} from {} with {} controlled building rules",
                session.startPos, session.startTemplate, VillageBuildingSpawnConfig.customBuildingIds().size());
        if (diagnostics()) {
            LOGGER.info("[Solace] Configured plains village buildings: {}", VillageBuildingSpawnConfig.describeEntries());
        }
        return session;
    }

    private static void clearSession() {
        Session session = ACTIVE_SESSION.get();
        if (session != null) {
            session.logSummary();
            ACTIVE_SESSION.remove();
        }
    }

    private record PiecePlacement(
            ResourceLocation templateId,
            BlockPos position,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        private static PiecePlacement of(ResourceLocation templateId, BlockPos position, BoundingBox box) {
            return new PiecePlacement(
                    templateId,
                    position.immutable(),
                    box.minX(),
                    box.minY(),
                    box.minZ(),
                    box.maxX(),
                    box.maxY(),
                    box.maxZ()
            );
        }
    }

    private static final class Session {
        private final BlockPos startPos;
        private final ResourceLocation startTemplate;
        private final Map<ResourceLocation, Integer> spawnedCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> suppressedCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> offeredCounts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Integer> priorityCounts = new LinkedHashMap<>();
        private final Set<PiecePlacement> recordedPieces = new LinkedHashSet<>();
        private String lastLoggedCustomSignal = "";

        private Session(BlockPos startPos, ResourceLocation startTemplate) {
            this.startPos = startPos.immutable();
            this.startTemplate = startTemplate;
        }

        private void record(StructurePoolElement element, BlockPos position, BoundingBox boundingBox) {
            record(element, position, boundingBox, true);
        }

        private void record(StructurePoolElement element, BlockPos position, BoundingBox boundingBox, boolean logPlacement) {
            Optional<ResourceLocation> templateId = templateId(element);
            if (templateId.isEmpty()) {
                return;
            }

            ResourceLocation id = templateId.get();
            if (!recordedPieces.add(PiecePlacement.of(id, position, boundingBox))) {
                return;
            }
            spawnedCounts.merge(id, 1, Integer::sum);
            if (logPlacement && isCustomBuilding(id) && diagnostics()) {
                LOGGER.debug("[Solace] Plains village custom building placed: {} at {} with box {}", id, position, boundingBox);
            }
        }

        private void reconcile(List<?> pieces) {
            spawnedCounts.clear();
            recordedPieces.clear();
            for (Object value : pieces) {
                if (value instanceof PoolElementStructurePiece piece) {
                    record(piece.getElement(), piece.getPosition(), piece.getBoundingBox(), false);
                }
            }
        }

        private boolean matchesStart(BlockPos position, ResourceLocation id) {
            return id.equals(startTemplate) && position.equals(startPos);
        }

        private void recordOffered(ResourceLocation id) {
            offeredCounts.merge(id, 1, Integer::sum);
        }

        private void recordPriority(ResourceLocation id) {
            priorityCounts.merge(id, 1, Integer::sum);
        }

        private boolean needsMinimum(ResourceLocation id) {
            int minimum = VillageBuildingSpawnConfig.minimumCount(id);
            if (minimum < 0) {
                return false;
            }
            return spawnedCounts.getOrDefault(id, 0) < minimum;
        }

        private boolean canPlace(ResourceLocation id) {
            int maximum = VillageBuildingSpawnConfig.maximumCount(id);
            if (maximum < 0) {
                return false;
            }
            return spawnedCounts.getOrDefault(id, 0) < maximum;
        }

        private boolean hasUnmetMinimum() {
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (needsMinimum(target)) {
                    return true;
                }
            }
            return false;
        }

        private void recordSuppressed(ResourceLocation id) {
            suppressedCounts.merge(id, 1, Integer::sum);
        }

        private StructurePoolElement selectRandomReplacement(List<StructurePoolElement> candidates) {
            StructurePoolElement nonUniqueFallback = null;
            for (ResourceLocation target : VillageBuildingSpawnConfig.customBuildingIds()) {
                if (!needsMinimum(target)) {
                    continue;
                }

                for (StructurePoolElement candidate : candidates) {
                    Optional<ResourceLocation> candidateId = templateId(candidate);
                    if (candidateId.isPresent() && candidateId.get().equals(target)) {
                        recordOffered(target);
                        recordPriority(target);
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
            String customSignal = customSignal();
            if (customSignal.equals(lastLoggedCustomSignal)) {
                return;
            }
            lastLoggedCustomSignal = customSignal;

            Map<ResourceLocation, Integer> controlledCounts = new LinkedHashMap<>();
            for (ResourceLocation customId : VillageBuildingSpawnConfig.customBuildingIds()) {
                controlledCounts.put(customId, spawnedCounts.getOrDefault(customId, 0));
            }
            String summary = "Plains village jigsaw at " + startPos + " from " + startTemplate + ": " + controlledCounts;
            rememberReport(summary);
            LOGGER.info("[Solace] {}", summary);
            for (ResourceLocation customId : VillageBuildingSpawnConfig.customBuildingIds()) {
                int minimum = VillageBuildingSpawnConfig.minimumCount(customId);
                int maximum = VillageBuildingSpawnConfig.maximumCount(customId);
                int count = spawnedCounts.getOrDefault(customId, 0);
                if (count < minimum) {
                    LOGGER.debug("[Solace] Plains village placed {} below minimum {} for controlled building {}", count, minimum, customId);
                } else if (count > maximum) {
                    LOGGER.warn("[Solace] Plains village placed {} above maximum {} for controlled building {}", count, maximum, customId);
                }
            }

            if (!suppressedCounts.isEmpty()) {
                LOGGER.info("[Solace] Suppressed duplicate plains village jigsaw candidates: {}", suppressedCounts);
            }

            Map<ResourceLocation, Integer> offeredButMissing = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Integer> entry : offeredCounts.entrySet()) {
                int missingOffers = entry.getValue() - spawnedCounts.getOrDefault(entry.getKey(), 0);
                if (missingOffers > 0) {
                    offeredButMissing.put(entry.getKey(), missingOffers);
                }
            }

            if (!diagnostics()) {
                return;
            }
            LOGGER.info("[Solace] Full plains village piece counts: {}", spawnedCounts);
            if (!offeredButMissing.isEmpty()) {
                LOGGER.warn("[Solace] Plains village listed custom candidates that did not become pieces: {}", offeredButMissing);
            }
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
                        .append(priorityCounts.getOrDefault(customId, 0))
                        .append('/')
                        .append(VillageBuildingSpawnConfig.minimumCount(customId))
                        .append('-')
                        .append(VillageBuildingSpawnConfig.maximumCount(customId))
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
