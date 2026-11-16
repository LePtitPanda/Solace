package com.solace.quests.content;

import com.google.gson.JsonParser;
import com.solace.quests.condition.Ctx;
import com.solace.quests.content.QuestContent.Key;
import com.solace.quests.content.QuestContent.Raw;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.QuestLogic.View;
import com.solace.quests.state.QuestSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestCoreTest {
    private static final String CHAPTER = "{\"title\":\"Village\"}";
    private static final String HUNT = """
            {"chapter":"t:village","title":"Hunt",
             "phases":[
               {"id":"hunt","objectives":[{"id":"zombies","type":"solace:kill","entity":"minecraft:zombie","count":3}]},
               {"id":"report","objectives":[{"id":"tick","type":"solace:checkmark"}],
                "transitions":[
                  {"to":"@fail","when":{"type":"solace:flag","flag":"betrayed"},"label":"betrayal"},
                  {"to":"@complete","when":{"type":"solace:objectives_complete"}}]}],
             "rewards":[{"id":"xp","type":"solace:xp","levels":1},{"id":"flag","type":"solace:flag","flag":"hunter"}]}""";
    private static final String SEQUEL = """
            {"chapter":"t:village","title":"Sequel","requires":{"quests":["t:hunt"]},
             "objectives":[{"id":"rich","type":"solace:condition",
               "when":{"type":"solace:variable","name":"gold","op":">=","value":10}}]}""";

    private static ResourceLocation id(String path) {
        return new ResourceLocation("t", path);
    }

    private static Map<Key, Raw> files(String... kindIdJson) {
        Map<Key, Raw> files = new LinkedHashMap<>();
        for (int i = 0; i < kindIdJson.length; i += 3) {
            files.put(new Key(kindIdJson[i], id(kindIdJson[i + 1])),
                    new Raw(JsonParser.parseString(kindIdJson[i + 2]), null));
        }
        return files;
    }

    private static QuestContent valid() {
        QuestContent content = QuestContent.build(
                files("chapters", "village", CHAPTER, "quests", "hunt", HUNT, "quests", "sequel", SEQUEL),
                QuestContent.EMPTY, null);
        assertEquals(List.of(), content.problems);
        return content;
    }

    private static final class Fake extends Ctx {
        final QuestContent content;
        final PlayerQuestData data = new PlayerQuestData();
        long time;

        Fake(QuestContent content) {
            this.content = content;
        }

        @Override
        public QuestContent content() {
            return content;
        }

        final java.util.Set<String> areas = new java.util.HashSet<>();

        @Override
        public boolean inArea(String area) {
            return areas.contains(area);
        }

        @Override
        public boolean nearNpc(ResourceLocation binding, int radius) {
            return false;
        }

        @Override
        public PlayerQuestData data() {
            return data;
        }

        @Override
        public long gameTime() {
            return time;
        }

        @Override
        public String dimension() {
            return "minecraft:overworld";
        }

        @Override
        public long dayTime() {
            return 6000;
        }

        @Override
        public int countItem(Target item, boolean heldOnly) {
            return 0;
        }

        @Override
        public boolean predicate(ResourceLocation id) {
            return false;
        }

        @Override
        public int reputation(ResourceLocation faction) {
            return data.reputation.getOrDefault(faction, 0);
        }

        @Override
        public boolean inBiome(Target biome) {
            return false;
        }

        @Override
        public boolean inStructure(Target structure, String piece) {
            return false;
        }

        @Override
        public boolean hasAdvancement(ResourceLocation advancement) {
            return false;
        }

        @Override
        public boolean custom(String handler, String data) {
            return false;
        }

        @Override
        public void changed() {
        }
    }

    private static final QuestLogic.Events QUIET = new QuestLogic.Events() {
    };

    @Test
    void parsesValidContent() {
        QuestContent content = valid();
        Quest hunt = content.quests.get(id("hunt"));
        assertEquals(2, hunt.phases().size());
        assertEquals(3, hunt.phases().get(0).objectives().get(0).count());
        assertEquals("main", content.quests.get(id("sequel")).phases().get(0).id());
        assertTrue(content.objectiveTypes.contains("solace:kill"));
    }

    @Test
    void errorsNameFileAndJsonPath() {
        QuestContent content = QuestContent.build(files("chapters", "village", CHAPTER, "quests", "hunt",
                HUNT.replace("\"count\":3", "\"count\":\"three\"")), QuestContent.EMPTY, null);
        Problem problem = content.problems.stream().filter(Problem::error).findFirst().orElseThrow();
        assertEquals("t:hunt", problem.id());
        assertEquals("$.phases[0].objectives[0].count", problem.path());
        assertTrue(problem.message().contains("expected integer >= 1"), problem.message());
        assertNull(content.quests.get(id("hunt")));
        assertNotNull(content.chapters.get(id("village")), "other files are unaffected");
    }

    @Test
    void unknownFieldIsOnlyAWarning() {
        QuestContent content = QuestContent.build(files("chapters", "village",
                "{\"title\":\"Village\",\"titel\":\"x\"}"), QuestContent.EMPTY, null);
        assertEquals(0, content.errorCount());
        assertEquals("$.titel", content.problems.get(0).path());
        assertNotNull(content.chapters.get(id("village")));
    }

    @Test
    void brokenFileKeepsItsPreviousVersionOnly() {
        QuestContent previous = valid();
        QuestContent next = QuestContent.build(files("chapters", "village", CHAPTER,
                "quests", "hunt", "{\"chapter\":\"t:village\"}",
                "quests", "sequel", SEQUEL.replace("Sequel", "Sequel II")), previous, null);
        assertTrue(next.errorCount() > 0);
        assertEquals("Hunt", next.quests.get(id("hunt")).title().getString(), "previous version kept");
        assertEquals("Sequel II", next.quests.get(id("sequel")).title().getString(), "valid file still updated");
    }

    @Test
    void unreadableFileAndDanglingReferenceAreDropped() {
        Map<Key, Raw> files = files("quests", "hunt", HUNT, "quests", "sequel", SEQUEL);
        files.put(new Key("chapters", id("village")), new Raw(null, "invalid JSON: boom"));
        QuestContent content = QuestContent.build(files, QuestContent.EMPTY, null);
        // No chapter -> hunt is dangling -> sequel (requires hunt) is dangling too.
        assertTrue(content.quests.isEmpty());
        assertTrue(content.problems.stream().anyMatch(p -> p.message().contains("unknown chapters entry \"t:village\"")));
        assertTrue(content.problems.stream().anyMatch(p -> p.message().contains("unknown quests entry \"t:hunt\"")));
    }

    @Test
    void streamLimitsNeedNoFilePath() throws Exception {
        long[] budget = {QuestContentLoader.MAX_TOTAL_BYTES};
        byte[] big = new byte[QuestContentLoader.MAX_FILE_BYTES + 1];
        assertTrue(QuestContentLoader.parse(new ByteArrayInputStream(big), budget).error().contains("larger than"));
        String deep = "[".repeat(100_000);
        assertTrue(QuestContentLoader.parse(new ByteArrayInputStream(deep.getBytes(StandardCharsets.UTF_8)), budget)
                .error().contains("nested deeper"));
        assertTrue(QuestContentLoader.parse(new ByteArrayInputStream("{oops".getBytes(StandardCharsets.UTF_8)), budget)
                .error().contains("invalid JSON"));
        Raw ok = QuestContentLoader.parse(new ByteArrayInputStream("{\"a\":\"[[{\"}".getBytes(StandardCharsets.UTF_8)),
                budget);
        assertNull(ok.error());
        long[] empty = {1};
        assertTrue(QuestContentLoader.parse(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), empty)
                .error().contains("total content size"));
    }

    @Test
    void questRunsThroughPhasesAndClaimsOnce() {
        Fake ctx = new Fake(valid());
        Quest hunt = ctx.content.quests.get(id("hunt"));
        Quest sequel = ctx.content.quests.get(id("sequel"));
        List<String> log = new ArrayList<>();
        QuestLogic.Events events = new QuestLogic.Events() {
            @Override
            public void phase(Quest quest, Quest.Phase phase) {
                log.add(phase.id());
            }

            @Override
            public void completed(Quest quest) {
                log.add("done:" + quest.id().getPath());
            }
        };

        assertEquals(View.AVAILABLE, QuestLogic.view(ctx, hunt));
        assertEquals(View.LOCKED, QuestLogic.view(ctx, sequel));
        assertFalse(QuestLogic.start(ctx, sequel, false, events), "locked quests do not start");
        assertTrue(QuestLogic.start(ctx, hunt, false, events));
        assertFalse(QuestLogic.start(ctx, hunt, false, events), "already active");

        assertFalse(QuestLogic.addProgress(ctx, hunt, "tick", 1, events), "objective of a later phase");
        QuestLogic.addProgress(ctx, hunt, "zombies", 2, events);
        assertEquals("hunt", ctx.data.quests.get(id("hunt")).phase);
        QuestLogic.addProgress(ctx, hunt, "zombies", 5, events);
        assertEquals(3, ctx.data.quests.get(id("hunt")).progress.get("zombies"), "clamped to count");
        assertEquals("report", ctx.data.quests.get(id("hunt")).phase, "implicit move to the next phase");

        assertTrue(QuestLogic.claim(ctx, hunt, null).isEmpty(), "nothing to claim before completion");
        QuestLogic.setProgress(ctx, hunt, "tick", 1, events);
        assertEquals(List.of("report", "done:hunt"), log);
        assertEquals(View.CLAIMABLE, QuestLogic.view(ctx, hunt));
        assertTrue(QuestLogic.is(ctx, id("hunt"), View.COMPLETED));

        assertEquals(1, QuestLogic.claim(ctx, hunt, "xp").size());
        assertEquals(1, QuestLogic.claim(ctx, hunt, null).size(), "only the unclaimed reward is left");
        assertTrue(QuestLogic.claim(ctx, hunt, null).isEmpty(), "never twice");
        assertEquals(View.COMPLETED, QuestLogic.view(ctx, hunt));

        assertEquals(View.AVAILABLE, QuestLogic.view(ctx, sequel));
        QuestLogic.start(ctx, sequel, false, events);
        assertEquals(View.ACTIVE, QuestLogic.view(ctx, sequel));
        ctx.data.variables.put("gold", 10);
        QuestLogic.evaluate(ctx, events);
        assertEquals(View.COMPLETED, QuestLogic.view(ctx, sequel), "condition objective completed the quest");
    }

    @Test
    void transitionsAreOrderedAndRecordTheBranch() {
        Fake ctx = new Fake(valid());
        Quest hunt = ctx.content.quests.get(id("hunt"));
        QuestLogic.start(ctx, hunt, false, QUIET);
        QuestLogic.setProgress(ctx, hunt, "zombies", 3, QUIET);
        ctx.data.flags.add("betrayed");
        QuestLogic.setProgress(ctx, hunt, "tick", 1, QUIET);
        assertEquals(View.FAILED, QuestLogic.view(ctx, hunt), "first matching transition wins");
        List<PlayerQuestData.History> history = ctx.data.quests.get(id("hunt")).history;
        assertEquals("betrayal", history.get(history.size() - 1).via());
    }

    @Test
    void stateSurvivesSaveAndContentRemoval() {
        Fake ctx = new Fake(valid());
        Quest hunt = ctx.content.quests.get(id("hunt"));
        QuestLogic.start(ctx, hunt, false, QUIET);
        QuestLogic.addProgress(ctx, hunt, "zombies", 2, QUIET);
        ctx.data.flags.add("met_cleric");
        ctx.data.variables.put("gold", 7);

        PlayerQuestData loaded = PlayerQuestData.load(ctx.data.save());
        assertEquals(ctx.data.save(), loaded.save());
        assertEquals(2, loaded.quests.get(id("hunt")).progress.get("zombies"));

        // The quest disappears from the content: its state is orphaned, not deleted.
        Fake empty = new Fake(QuestContent.EMPTY);
        empty.data.quests.putAll(loaded.quests);
        QuestLogic.evaluate(empty, QUIET);
        assertEquals(2, empty.data.quests.get(id("hunt")).progress.get("zombies"));
    }

    @Test
    void newerSaveSchemaIsNeverOverwritten() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema", QuestSavedData.SCHEMA + 1);
        future.putString("somethingNew", "keep me");
        QuestSavedData data = QuestSavedData.load(future);
        assertTrue(data.frozen());
        assertEquals(future, data.save(new CompoundTag()));
    }

    /** Keeps docs/quests/samples loadable through the real loose-folder reader. */
    @Test
    void samplePackLoadsCleanAndBrokenSampleReportsEverything() {
        Path samples = Path.of("docs/quests/samples");
        QuestContent content = QuestContent.build(
                QuestContentLoader.read(ResourceManager.Empty.INSTANCE, samples), QuestContent.EMPTY, null);
        assertEquals(List.of(), content.problems);
        assertEquals(6, content.quests.size());
        assertEquals(1, content.dialogues.size());
        assertEquals(1, content.bindings.size());
        assertEquals(3, content.factions.size());
        assertEquals(1, content.markers.size());

        QuestContent broken = QuestContent.build(
                QuestContentLoader.read(ResourceManager.Empty.INSTANCE, samples.resolve("broken")), content, null);
        List<String> paths = broken.problems.stream().map(Problem::path).toList();
        assertTrue(paths.containsAll(List.of("$.title", "$.titel", "$.objectives[0].count", "$.objectives[1].type")),
                paths.toString());
        assertTrue(broken.quests.isEmpty());
    }

    /** The instance's own quest content (../config/solace/quests) parses without errors. Skipped outside the pack. */
    @Test
    void instanceQuestContentHasNoErrors() {
        Path pack = Path.of("../config/solace/quests");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(pack));
        QuestContent content = QuestContent.build(
                QuestContentLoader.read(ResourceManager.Empty.INSTANCE, pack), QuestContent.EMPTY, null);
        content.problems.forEach(problem -> System.out.println("[pack] " + problem));
        assertEquals(List.of(), content.problems.stream().filter(Problem::error).toList());
    }

    @Test
    void dialogueRejectsUnknownNodesAndReferences() {
        String dialogue = """
                {"start":"a","nodes":{
                  "a":{"text":"Hi","choices":[{"text":"Go","to":"nowhere"}]},
                  "b":{"text":[{"when":{"type":"solace:flag","flag":"x"},"text":"X"},{"text":"Y"}],"to":"@close"}}}""";
        QuestContent content = QuestContent.build(files("dialogues", "talk", dialogue), QuestContent.EMPTY, null);
        Problem problem = content.problems.stream().filter(Problem::error).findFirst().orElseThrow();
        assertEquals("$.nodes.a.choices[0].to", problem.path());
        assertTrue(content.dialogues.isEmpty());

        QuestContent fixed = QuestContent.build(files("dialogues", "talk", dialogue.replace("nowhere", "b"),
                "npcs", "guide", "{\"entity\":\"minecraft:villager\",\"dialogue\":\"t:missing\"}"),
                QuestContent.EMPTY, null);
        assertEquals(2, fixed.dialogues.get(id("talk")).nodes().get("b").text().size());
        assertTrue(fixed.npcs.isEmpty(), "binding to an unknown dialogue is dropped");
    }

    @Test
    void factionRanksAndReputationConditions() {
        String faction = """
                {"name":"Wardens","ranks":[{"id":"friendly","name":"Friendly","at":50},
                  {"id":"neutral","name":"Neutral","at":0},{"id":"hated","name":"Hated","at":-100}]}""";
        String quest = """
                {"chapter":"t:village","title":"Trusted","objectives":[
                  {"id":"rank","type":"solace:faction_rank","faction":"t:wardens","rank":"friendly"}]}""";
        QuestContent content = QuestContent.build(files("chapters", "village", CHAPTER, "factions", "wardens",
                faction, "quests", "trusted", quest), QuestContent.EMPTY, null);
        assertEquals(List.of(), content.problems);
        Faction wardens = content.factions.get(id("wardens"));
        assertEquals("hated", wardens.ranks().get(0).id(), "ranks are sorted by threshold");
        assertEquals(1, wardens.rankIndex(49));
        assertEquals(2, wardens.rankIndex(50));

        Fake ctx = new Fake(content);
        Quest trusted = content.quests.get(id("trusted"));
        QuestLogic.start(ctx, trusted, false, QUIET);
        ctx.data.reputation.put(id("wardens"), 49);
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(View.ACTIVE, QuestLogic.view(ctx, trusted));
        ctx.data.reputation.put(id("wardens"), 80);
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(View.COMPLETED, QuestLogic.view(ctx, trusted), "rank objective completes by itself");
    }

    @Test
    void timeLimitFailsAndResetAllowsRetry() {
        String timed = """
                {"chapter":"t:village","title":"Timed","time_limit_seconds":10,"on_fail":"reset",
                 "objectives":[{"id":"tick","type":"solace:checkmark"}]}""";
        Fake ctx = new Fake(QuestContent.build(files("chapters", "village", CHAPTER, "quests", "timed", timed),
                QuestContent.EMPTY, null));
        Quest quest = ctx.content.quests.get(id("timed"));
        QuestLogic.start(ctx, quest, false, QUIET);
        ctx.time = 199;
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(View.ACTIVE, QuestLogic.view(ctx, quest));
        ctx.time = 200;
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(PlayerQuestData.Status.FAILED, ctx.data.quests.get(id("timed")).status);
        assertEquals(View.AVAILABLE, QuestLogic.view(ctx, quest), "on_fail reset lets the player try again");
        assertTrue(ctx.data.tracked.isEmpty(), "a finished quest leaves the tracker");
    }

    @Test
    void transitionAndPhaseActionsAreReported() {
        String quest = """
                {"chapter":"t:village","title":"Acts","phases":[
                  {"id":"one","objectives":[{"id":"tick","type":"solace:checkmark"}],
                   "transitions":[{"to":"two","when":{"type":"solace:objectives_complete"},
                     "actions":[{"type":"solace:set_flag","flag":"left_one"}]}]},
                  {"id":"two","on_enter":[{"type":"solace:give_item","item":"minecraft:apple"}],
                   "objectives":[{"id":"never","type":"solace:checkmark"}]}]}""";
        Fake ctx = new Fake(QuestContent.build(files("chapters", "village", CHAPTER, "quests", "acts", quest),
                QuestContent.EMPTY, null));
        assertEquals(List.of(), ctx.content.problems);
        List<String> ran = new ArrayList<>();
        QuestLogic.Events events = new QuestLogic.Events() {
            @Override
            public void actions(Quest q, List<Quest.Reward> actions) {
                actions.forEach(action -> ran.add(action.type()));
            }
        };
        Quest acts = ctx.content.quests.get(id("acts"));
        QuestLogic.start(ctx, acts, false, events);
        QuestLogic.setProgress(ctx, acts, "tick", 1, events);
        assertEquals(List.of("solace:flag", "solace:item"), ran, "aliases are normalised; transition runs before on_enter");
    }

    @Test
    void autoStartRunsOnceAndHiddenUntilRevealsTheQuest() {
        String auto = """
                {"chapter":"t:village","title":"Auto","auto_start":true,"repeatable":{"cooldown_seconds":0},
                 "hidden_until":"one_objective",
                 "objectives":[{"id":"a","type":"solace:checkmark"},{"id":"b","type":"solace:checkmark"}]}""";
        String secret = """
                {"chapter":"t:village","title":"Secret","requires":{"quests":["t:auto"]},
                 "hidden_until":"all_dependencies","objectives":[{"id":"a","type":"solace:checkmark"}]}""";
        Fake ctx = new Fake(QuestContent.build(
                files("chapters", "village", CHAPTER, "quests", "auto", auto, "quests", "secret", secret),
                QuestContent.EMPTY, null));
        assertEquals(List.of(), ctx.content.problems);
        Quest quest = ctx.content.quests.get(id("auto"));
        Quest dependent = ctx.content.quests.get(id("secret"));
        int[] starts = {0};
        QuestLogic.Events events = new QuestLogic.Events() {
            @Override
            public void started(Quest q) {
                starts[0]++;
            }
        };
        QuestLogic.evaluate(ctx, events);
        assertTrue(QuestLogic.hidden(ctx, quest), "no objective done yet");
        assertTrue(QuestLogic.hidden(ctx, dependent));
        QuestLogic.setProgress(ctx, quest, "a", 1, events);
        assertFalse(QuestLogic.hidden(ctx, quest));
        QuestLogic.setProgress(ctx, quest, "b", 1, events);
        assertFalse(QuestLogic.hidden(ctx, dependent), "its dependency is completed");
        QuestLogic.evaluate(ctx, events);
        assertEquals(View.AVAILABLE, QuestLogic.view(ctx, quest), "repeatable, so it can be started again by hand");
        assertEquals(1, starts[0], "but it never restarts itself");
    }

    // --- while / needs / on_lost / group / distinct ---

    private static String crows(String onLost) {
        return """
                {"chapter":"t:village","title":"Crows","objectives":[
                  {"id":"in_village","type":"solace:while","when":{"type":"solace:area","area":"bellhaven"}},
                  {"id":"crows","type":"solace:kill","entity":"minecraft:zombie","count":3,
                   "needs":["in_village"],"on_lost":"%s"}]}""".formatted(onLost);
    }

    private static Fake started(String questJson) {
        QuestContent content = QuestContent.build(files("chapters", "village", CHAPTER, "quests", "q", questJson),
                QuestContent.EMPTY, null);
        assertEquals(List.of(), content.problems.stream().filter(Problem::error).toList());
        Fake ctx = new Fake(content);
        assertTrue(QuestLogic.start(ctx, content.quests.get(id("q")), false, QUIET));
        return ctx;
    }

    private static int progress(Fake ctx, String objective) {
        return ctx.data.quests.get(id("q")).progress.getOrDefault(objective, 0);
    }

    @Test
    void needsBlockProgressUntilTheWhileHolds() {
        Fake ctx = started(crows("keep"));
        Quest quest = ctx.content.quests.get(id("q"));
        QuestLogic.addProgress(ctx, quest, "crows", 1, QUIET);
        assertEquals(0, progress(ctx, "crows"), "outside the area nothing counts");
        ctx.areas.add("bellhaven");
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(1, progress(ctx, "in_village"));
        QuestLogic.addProgress(ctx, quest, "crows", 1, QUIET);
        assertEquals(1, progress(ctx, "crows"));
        ctx.areas.clear();
        QuestLogic.evaluate(ctx, QUIET);
        assertEquals(0, progress(ctx, "in_village"), "a while objective un-ticks");
        assertEquals(1, progress(ctx, "crows"), "keep");
        ctx.areas.add("bellhaven");
        QuestLogic.addProgress(ctx, quest, "crows", 2, QUIET);
        assertEquals(View.COMPLETED, QuestLogic.view(ctx, quest));
    }

    @Test
    void onLostResetAndFail() {
        Fake reset = started(crows("reset"));
        reset.areas.add("bellhaven");
        QuestLogic.evaluate(reset, QUIET);
        QuestLogic.addProgress(reset, reset.content.quests.get(id("q")), "crows", 2, QUIET);
        reset.areas.clear();
        QuestLogic.evaluate(reset, QUIET);
        assertEquals(0, progress(reset, "crows"));
        assertEquals(View.ACTIVE, QuestLogic.view(reset, reset.content.quests.get(id("q"))));

        Fake fail = started(crows("fail"));
        fail.areas.add("bellhaven");
        QuestLogic.evaluate(fail, QUIET);
        QuestLogic.addProgress(fail, fail.content.quests.get(id("q")), "crows", 1, QUIET);
        fail.areas.clear();
        QuestLogic.evaluate(fail, QUIET);
        assertEquals(View.FAILED, QuestLogic.view(fail, fail.content.quests.get(id("q"))));
    }

    @Test
    void groupCompletesWithEnoughChildren() {
        Fake ctx = started("""
                {"chapter":"t:village","title":"Album","objectives":[
                  {"id":"album","type":"solace:group","of":["a","b","c"],"count":2},
                  {"id":"a","type":"solace:checkmark","optional":true},
                  {"id":"b","type":"solace:checkmark","optional":true},
                  {"id":"c","type":"solace:checkmark","optional":true}]}""");
        Quest quest = ctx.content.quests.get(id("q"));
        QuestLogic.setProgress(ctx, quest, "a", 1, QUIET);
        assertEquals(1, progress(ctx, "album"));
        assertEquals(View.ACTIVE, QuestLogic.view(ctx, quest));
        QuestLogic.setProgress(ctx, quest, "c", 1, QUIET);
        assertEquals(View.COMPLETED, QuestLogic.view(ctx, quest));
    }

    @Test
    void distinctCountsEachKeyOnceAndIsSaved() {
        Fake ctx = started("""
                {"chapter":"t:village","title":"Zoo","objectives":[
                  {"id":"kinds","type":"solace:kill","entity":"#t:animals","count":3,"distinct":"entity_type"}]}""");
        Quest quest = ctx.content.quests.get(id("q"));
        QuestLogic.addDistinct(ctx, quest, "kinds", "minecraft:cow", QUIET);
        QuestLogic.addDistinct(ctx, quest, "kinds", "minecraft:cow", QUIET);
        QuestLogic.addDistinct(ctx, quest, "kinds", "minecraft:pig", QUIET);
        assertEquals(2, progress(ctx, "kinds"));
        PlayerQuestData loaded = PlayerQuestData.load(ctx.data.save());
        assertEquals(java.util.Set.of("minecraft:cow", "minecraft:pig"), loaded.quests.get(id("q")).seen.get("kinds"));
    }

    @Test
    void structurePiecesParseAndMatch() {
        Fake ctx = started("""
                {"chapter":"t:village","title":"Maze","objectives":[
                  {"id":"rooms","type":"solace:find_structure","structure":"dungeonnowloading:labyrinth",
                   "piece":"dungeonnowloading:labyrinth/rooms/*","distinct":"piece","count":10},
                  {"id":"hall","type":"solace:find_structure","structure":"dungeonnowloading:labyrinth",
                   "piece":"dungeonnowloading:labyrinth/boss_room"}]}""");
        List<Quest.Objective> objectives = ctx.content.quests.get(id("q")).phases().get(0).objectives();
        assertEquals(10, objectives.get(0).count());
        assertEquals("dungeonnowloading:labyrinth/rooms/*", objectives.get(0).name());
        assertNull(objectives.get(0).when(), "distinct pieces are counted by the engine");
        assertNotNull(objectives.get(1).when());
        assertTrue(com.solace.quests.state.ServerCtx.templateMatches("dungeonnowloading:labyrinth/rooms/maze",
                "dungeonnowloading:labyrinth/rooms/*"));
        assertTrue(com.solace.quests.state.ServerCtx.templateMatches("a:b", "*"));
        assertFalse(com.solace.quests.state.ServerCtx.templateMatches(
                "dungeonnowloading:labyrinth/boss_room/outer_ring/wall", "dungeonnowloading:labyrinth/boss_room"));
    }

    @Test
    void badNeedsAndDistinctAreErrors() {
        QuestContent content = QuestContent.build(files("chapters", "village", CHAPTER, "quests", "q", """
                {"chapter":"t:village","title":"Bad","objectives":[
                  {"id":"a","type":"solace:checkmark","needs":["nope"]},
                  {"id":"b","type":"solace:item","item":"minecraft:stick","distinct":"item"}]}"""),
                QuestContent.EMPTY, null);
        List<String> errors = content.problems.stream().filter(Problem::error).map(Problem::message).toList();
        assertTrue(errors.stream().anyMatch(message -> message.contains("unknown objective \"nope\"")), errors.toString());
        assertTrue(errors.stream().anyMatch(message -> message.contains("not supported by solace:item")), errors.toString());
    }

    @Test
    void villageDefendersRank() {
        String villagers = """
                {"name":"Villagers","ranks":[{"id":"hated","name":"Hated","at":-400},
                  {"id":"unwelcome","name":"Unwelcome","at":-200},{"id":"distrusted","name":"Distrusted","at":-60}],
                 "source":{"type":"solace:villager_gossip","defenders_attack_at":"unwelcome"}}""";
        QuestContent content = QuestContent.build(files("factions", "villagers", villagers,
                "factions", "bad", villagers.replace("\"unwelcome\"}", "\"nope\"}")), QuestContent.EMPTY, null);
        Faction faction = content.factions.get(id("villagers"));
        assertEquals("unwelcome", faction.defendersAttackAt());
        assertEquals(-60, faction.defendersSafeFrom(), "hostile below the start of Distrusted");
        assertTrue(content.problems.stream().anyMatch(problem -> problem.error()
                && problem.path().contains("defenders_attack_at")), content.problems.toString());
    }

    @Test
    void locksAndLootParse() {
        QuestContent content = QuestContent.build(files(
                "recipe_locks", "rotation", """
                        {"outputs":["create:shaft","#forge:gears"],"item":"kubejs:technique_rotation",
                         "learn_text":"You learned Rotation."}""",
                "trades", "distrusted", """
                        {"offers":"all","mode":"hide","unlock_when":{"type":"solace:flag","flag":"trusted"}}""",
                "loot", "shift_board", """
                        {"tables":["minecraft:chests/*"],"once":true,
                         "items":[{"item":"minecraft:paper","min":1,"max":2,"chance":0.5}]}""",
                "block_locks", "notice_board", """
                        {"block":"#minecraft:signs","message":"Notices for villagers only.",
                         "unlock_when":{"type":"solace:flag","flag":"c1.trading_open"}}""",
                "block_locks", "no_unlock", """
                        {"block":"minecraft:lectern"}"""),
                QuestContent.EMPTY, null);
        List<Problem> errors = content.problems.stream().filter(Problem::error).toList();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).path().contains("unlock_when"), errors.toString());
        assertTrue(content.blockLocks.get(id("notice_board")).block().tag());
        assertTrue(content.blockLocks.get(id("notice_board")).message() != null);
        assertTrue(content.tradeLocks.get(id("distrusted")).all());
        assertEquals(2, content.recipeLocks.get(id("rotation")).outputs().size());
        assertEquals(2, content.loot.get(id("shift_board")).items().get(0).max());
    }
}
