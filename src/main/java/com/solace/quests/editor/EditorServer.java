package com.solace.quests.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.solace.quests.content.Problem;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.QuestContent.Key;
import com.solace.quests.content.QuestContentLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The live link between the web editor and the game: a tiny HTTP server bound to 127.0.0.1 only,
 * started by "/solacequests editor" for an operator, guarded by a random per-start token.
 * Written on plain sockets so it needs no library and no optional JDK module.
 */
public final class EditorServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_BODY = QuestContentLoader.MAX_FILE_BYTES;
    private static ServerSocket socket;
    private static String token;
    private static MinecraftServer game;

    private EditorServer() {
    }

    private record Response(int status, String type, byte[] body) {
        static Response json(int status, JsonElement json) {
            return new Response(status, "application/json; charset=utf-8", GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        }

        static Response error(int status, String message) {
            JsonObject json = new JsonObject();
            json.addProperty("error", message);
            return json(status, json);
        }
    }

    /** "/solacequests editor": starts the server if needed and prints the link to the sender only. */
    public static synchronized int open(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server.isDedicatedServer() && !allowDedicated()) {
            source.sendFailure(Component.literal("The quest editor is disabled on dedicated servers. Set "
                    + "\"editorAllowDedicated\": true in config/solace-quests.json to allow it."));
            return 0;
        }
        try {
            if (socket == null || socket.isClosed()) {
                byte[] random = new byte[16];
                new SecureRandom().nextBytes(random);
                token = HexFormat.of().formatHex(random);
                game = server;
                socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
                ServerSocket listening = socket;
                Thread thread = new Thread(() -> accept(listening), "Solace Quest Editor");
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException exception) {
            LOGGER.error("[Solace Quests] Could not start the editor server", exception);
            source.sendFailure(Component.literal("Could not start the editor server: " + exception.getMessage()));
            return 0;
        }
        // The token rides in the URL fragment: browsers never send it in a request line or Referer.
        String url = "http://127.0.0.1:" + socket.getLocalPort() + "/#token=" + token;
        source.sendSuccess(() -> Component.literal("Quest editor: ").append(Component.literal("[open in browser]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)))), false);
        return 1;
    }

    public static synchronized void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            token = null;
            game = null;
        }
    }

    private static boolean allowDedicated() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("solace-quests.json");
        try {
            return Files.isRegularFile(config) && JsonParser.parseString(Files.readString(config)).getAsJsonObject()
                    .get("editorAllowDedicated").getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }

    private static void accept(ServerSocket listening) {
        while (!listening.isClosed()) {
            try (Socket client = listening.accept()) {
                client.setSoTimeout(10_000);
                serve(client, listening.getLocalPort());
            } catch (IOException exception) {
                if (!listening.isClosed()) {
                    LOGGER.debug("[Solace Quests] Editor connection failed", exception);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("[Solace Quests] Editor request failed", exception);
            }
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int b; (b = in.read()) != -1 && b != '\n'; ) {
            if (b != '\r') {
                bytes.write(b);
            }
            if (bytes.size() > 8192) {
                throw new IOException("header line too long");
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void serve(Socket client, int port) throws IOException {
        InputStream in = client.getInputStream();
        String[] request = line(in).split(" ");
        Map<String, String> headers = new HashMap<>();
        for (String header; !(header = line(in)).isEmpty() && headers.size() < 64; ) {
            int colon = header.indexOf(':');
            if (colon > 0) {
                headers.put(header.substring(0, colon).trim().toLowerCase(Locale.ROOT), header.substring(colon + 1).trim());
            }
        }
        Response response;
        if (request.length < 2) {
            response = Response.error(400, "bad request");
        } else {
            int length;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException exception) {
                length = -1;
            }
            if (length < 0 || length > MAX_BODY) {
                response = Response.error(413, "body larger than " + MAX_BODY / 1024 + " KiB");
            } else {
                byte[] body = in.readNBytes(length);
                response = route(request[0], request[1], headers, new String(body, StandardCharsets.UTF_8), port);
            }
        }
        OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.1 " + response.status + " X\r\nContent-Type: " + response.type + "\r\nContent-Length: "
                + response.body.length + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(response.body);
        out.flush();
    }

    private static Response route(String method, String target, Map<String, String> headers, String body, int port) {
        String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        // DNS rebinding: a hostile page can point its own name at 127.0.0.1, but cannot fake Host.
        String host = headers.getOrDefault("host", "");
        if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) {
            return Response.error(403, "bad host");
        }
        if (!path.startsWith("/api/")) {
            if (!method.equals("GET")) {
                return Response.error(405, "method not allowed");
            }
            try (InputStream page = EditorServer.class.getResourceAsStream("/assets/solace/quest_editor/index.html")) {
                if (page == null) {
                    return new Response(404, "text/plain; charset=utf-8",
                            "The editor page is not in this build of Solace. Build Solace/editor first.".getBytes(StandardCharsets.UTF_8));
                }
                return new Response(200, "text/html; charset=utf-8", page.readAllBytes());
            } catch (IOException exception) {
                return Response.error(500, "could not read the editor page");
            }
        }
        String origin = headers.get("origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) {
            return Response.error(403, "bad origin");
        }
        String expected = "Bearer " + token;
        if (token == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                headers.getOrDefault("authorization", "").getBytes(StandardCharsets.UTF_8))) {
            return Response.error(401, "missing or wrong token");
        }
        MinecraftServer server = game;
        if (server == null) {
            return Response.error(503, "the game is not running");
        }
        try {
            // Everything that touches the game runs on the server thread.
            return server.submit(() -> api(server, method, path.substring("/api/".length()), body)).join();
        } catch (RuntimeException exception) {
            LOGGER.error("[Solace Quests] Editor API {} {} failed", method, path, exception);
            return Response.error(500, String.valueOf(exception.getMessage()));
        }
    }

    private static Response api(MinecraftServer server, String method, String path, String body) {
        if (method.equals("GET") && path.equals("session")) {
            JsonObject json = new JsonObject();
            json.addProperty("protocol", 1);
            json.add("kinds", GSON.toJsonTree(QuestContent.KINDS));
            return Response.json(200, json);
        }
        if (method.equals("GET") && path.equals("registries")) {
            return Response.json(200, registries(server));
        }
        if (method.equals("GET") && path.equals("content")) {
            return Response.json(200, content());
        }
        if (method.equals("POST") && path.equals("reload")) {
            QuestContentLoader.reloadNow(server.getResourceManager());
            return Response.json(200, content());
        }
        if (path.startsWith("content/") && (method.equals("PUT") || method.equals("DELETE") || method.equals("POST"))) {
            // content/<kind>/<namespace>/<path...>
            String[] parts = path.split("/", 4);
            ResourceLocation id = parts.length == 4 ? ResourceLocation.tryBuild(parts[2], parts[3]) : null;
            if (id == null || !QuestContent.KINDS.contains(parts[1]) || List.of(parts[3].split("/")).stream()
                    .anyMatch(segment -> segment.isEmpty() || segment.startsWith("."))) {
                return Response.error(400, "expected content/<kind>/<namespace>/<path>");
            }
            Key key = new Key(parts[1], id);
            Path root = QuestContentLoader.looseRoot().toAbsolutePath().normalize();
            Path file = root.resolve(id.getNamespace()).resolve(key.kind()).resolve(id.getPath() + ".json").normalize();
            if (!file.startsWith(root)) {
                return Response.error(400, "path escapes the content folder");
            }
            try {
                if (method.equals("DELETE")) {
                    Files.deleteIfExists(file);
                    // Shipped by a datapack: a tombstone hides it, since the pack itself is never edited.
                    if (server.getResourceManager().getResource(new ResourceLocation(id.getNamespace(),
                            "solace_quests/" + key.kind() + "/" + id.getPath() + ".json")).isPresent()) {
                        write(file, "{\"removed\": true}\n");
                    }
                    return Response.json(200, new JsonObject());
                }
                JsonElement json;
                try {
                    json = JsonParser.parseString(body);
                } catch (RuntimeException exception) {
                    return Response.error(400, "invalid JSON: " + exception.getMessage());
                }
                List<Problem> problems = QuestContent.check(key, json, QuestContentLoader::exists);
                JsonObject result = new JsonObject();
                result.add("problems", problems(problems));
                // POST = validate only. PUT = save, but never write a file the game would reject.
                boolean failed = problems.stream().anyMatch(Problem::error);
                if (method.equals("PUT") && !failed) {
                    write(file, GSON.toJson(json) + "\n");
                }
                result.addProperty("saved", method.equals("PUT") && !failed);
                return Response.json(failed && method.equals("PUT") ? 422 : 200, result);
            } catch (IOException exception) {
                return Response.error(500, "could not write " + file.getFileName() + ": " + exception.getMessage());
            }
        }
        return Response.error(404, "unknown endpoint");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonArray problems(List<Problem> problems) {
        JsonArray array = new JsonArray();
        for (Problem problem : problems) {
            JsonObject json = new JsonObject();
            json.addProperty("kind", problem.kind());
            json.addProperty("id", problem.id());
            json.addProperty("path", problem.path());
            json.addProperty("severity", problem.error() ? "error" : "warning");
            json.addProperty("message", problem.message());
            array.add(json);
        }
        return array;
    }

    private static JsonObject content() {
        QuestContent content = QuestContent.current;
        Path root = QuestContentLoader.looseRoot();
        JsonArray entries = new JsonArray();
        content.sources().forEach((key, json) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", key.kind());
            entry.addProperty("id", key.id().toString());
            entry.addProperty("source", Files.isRegularFile(root.resolve(key.id().getNamespace()).resolve(key.kind())
                    .resolve(key.id().getPath() + ".json")) ? "loose" : "datapack");
            entry.add("json", json);
            entries.add(entry);
        });
        JsonObject result = new JsonObject();
        result.add("entries", entries);
        result.add("problems", problems(content.problems));
        return result;
    }

    private static <T> JsonArray ids(MinecraftServer server, ResourceKey<Registry<T>> registry, boolean tags) {
        TreeSet<String> ids = new TreeSet<>();
        server.registryAccess().registry(registry).ifPresent(values -> {
            values.keySet().forEach(id -> ids.add(id.toString()));
            if (tags) {
                values.getTagNames().forEach(tag -> ids.add("#" + tag.location()));
            }
        });
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        return array;
    }

    private static JsonObject registries(MinecraftServer server) {
        JsonObject json = new JsonObject();
        json.add("items", ids(server, Registries.ITEM, true));
        json.add("blocks", ids(server, Registries.BLOCK, true));
        json.add("entities", ids(server, Registries.ENTITY_TYPE, true));
        json.add("structures", ids(server, Registries.STRUCTURE, true));
        json.add("biomes", ids(server, Registries.BIOME, true));
        json.add("sounds", ids(server, Registries.SOUND_EVENT, false));
        json.add("professions", ids(server, Registries.VILLAGER_PROFESSION, false));
        JsonArray dimensions = new JsonArray();
        server.levelKeys().stream().map(key -> key.location().toString()).sorted().forEach(dimensions::add);
        json.add("dimensions", dimensions);
        JsonArray advancements = new JsonArray();
        server.getAdvancements().getAllAdvancements().stream().map(a -> a.getId().toString()).sorted()
                .forEach(advancements::add);
        json.add("advancements", advancements);
        return json;
    }
}
