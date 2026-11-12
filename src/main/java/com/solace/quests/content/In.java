package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads one JSON object, recording every problem with its JSON path instead of throwing. */
public final class In {
    /** Registry existence check; null in unit tests. */
    public interface Lookup {
        boolean exists(String registry, ResourceLocation id);
    }

    /** A reference to another content entry, resolved after all files are parsed. */
    public record Ref(String kind, ResourceLocation id, String path) {
    }

    /** Per-file results shared by the root reader and all its children. */
    public static final class Out {
        public final List<Problem> problems = new ArrayList<>();
        public final List<Ref> refs = new ArrayList<>();
        final String kind;
        final String id;
        final Lookup lookup;

        public Out(String kind, ResourceLocation id, Lookup lookup) {
            this.kind = kind;
            this.id = id.toString();
            this.lookup = lookup;
        }

        public boolean failed() {
            return problems.stream().anyMatch(Problem::error);
        }
    }

    private final JsonObject json;
    private final String path;
    private final Out out;
    private final Set<String> seen = new HashSet<>();

    public In(JsonObject json, String path, Out out) {
        this.json = json;
        this.path = path;
        this.out = out;
    }

    public void err(String key, String message) {
        out.problems.add(new Problem(out.kind, out.id, at(key), true, message));
    }

    public void warn(String key, String message) {
        out.problems.add(new Problem(out.kind, out.id, at(key), false, message));
    }

    private String at(String key) {
        return key == null || key.isEmpty() ? path : path + "." + key;
    }

    public java.util.Set<String> keys() {
        return json.keySet();
    }

    public String path() {
        return path;
    }

    /** A reader for an element that is not a direct field (e.g. a list entry); null unless it is an object. */
    public In child(String key, JsonElement element) {
        return element != null && element.isJsonObject() ? new In(element.getAsJsonObject(), at(key), out) : null;
    }

    public boolean has(String key) {
        return json.has(key);
    }

    public JsonElement raw(String key) {
        seen.add(key);
        return json.get(key);
    }

    /** Marks fields that are valid but only used by later phases, so they are not reported as unknown. */
    public void skip(String... keys) {
        seen.addAll(List.of(keys));
    }

    public String str(String key, String def) {
        JsonElement element = raw(key);
        if (element == null) {
            return def;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        err(key, "expected a string, got " + element);
        return def;
    }

    public String req(String key) {
        if (!json.has(key)) {
            err(key, "missing required field");
            return "";
        }
        return str(key, "");
    }

    public int integer(String key, int def, int min) {
        JsonElement element = raw(key);
        if (element == null) {
            return def;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            if (value == Math.rint(value) && value >= min && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        err(key, "expected integer >= " + min + ", got " + element);
        return def;
    }

    public boolean bool(String key, boolean def) {
        JsonElement element = raw(key);
        if (element == null) {
            return def;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        err(key, "expected true or false, got " + element);
        return def;
    }

    public ResourceLocation id(String key, boolean required) {
        String text = required ? req(key) : str(key, null);
        if (text == null || text.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            err(key, "not a valid id: \"" + text + "\"");
        }
        return id;
    }

    /** An id that must name another content entry of the given kind. */
    public ResourceLocation ref(String key, String kind, boolean required) {
        ResourceLocation id = id(key, required);
        if (id != null) {
            out.refs.add(new Ref(kind, id, at(key)));
        }
        return id;
    }

    public void refTo(String key, String kind, ResourceLocation id) {
        out.refs.add(new Ref(kind, id, at(key)));
    }

    /** A registry id, or a tag when written as "#namespace:path". Unknown ids are warnings only. */
    public Target target(String key, String registry, boolean required) {
        String text = required ? req(key) : str(key, null);
        if (text == null || text.isEmpty()) {
            return null;
        }
        boolean tag = text.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
        if (id == null) {
            err(key, "not a valid id: \"" + text + "\"");
            return null;
        }
        if (!tag) {
            known(key, registry, id);
        }
        return new Target(tag, id);
    }

    public void known(String key, String registry, ResourceLocation id) {
        if (out.lookup != null && !out.lookup.exists(registry, id)) {
            warn(key, "unknown " + registry + " \"" + id + "\" (is the mod installed?)");
        }
    }

    /** Vanilla text component JSON: a plain string is literal, {"translate": ...} is a key. */
    public Component text(String key, boolean required) {
        JsonElement element = raw(key);
        if (element == null) {
            if (required) {
                err(key, "missing required field");
            }
            return null;
        }
        try {
            Component component = Component.Serializer.fromJson(element);
            if (component != null) {
                return component;
            }
        } catch (RuntimeException ignored) {
        }
        err(key, "not a valid text component");
        return null;
    }

    public In obj(String key) {
        JsonElement element = raw(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonObject()) {
            err(key, "expected an object, got " + element);
            return null;
        }
        return new In(element.getAsJsonObject(), at(key), out);
    }

    public List<In> objs(String key) {
        List<In> list = new ArrayList<>();
        JsonElement element = raw(key);
        if (element == null) {
            return list;
        }
        if (!element.isJsonArray()) {
            err(key, "expected a list, got " + element);
            return list;
        }
        for (int i = 0; i < element.getAsJsonArray().size(); i++) {
            JsonElement item = element.getAsJsonArray().get(i);
            String itemPath = at(key) + "[" + i + "]";
            if (item.isJsonObject()) {
                list.add(new In(item.getAsJsonObject(), itemPath, out));
            } else {
                out.problems.add(new Problem(out.kind, out.id, itemPath, true, "expected an object, got " + item));
            }
        }
        return list;
    }

    public List<String> strs(String key) {
        List<String> list = new ArrayList<>();
        JsonElement element = raw(key);
        if (element == null) {
            return list;
        }
        if (!element.isJsonArray()) {
            err(key, "expected a list, got " + element);
            return list;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                list.add(item.getAsString());
            } else {
                err(key, "expected a list of strings");
                break;
            }
        }
        return list;
    }

    /** Call after reading every expected field: reports typos as unknown-field warnings. */
    public void done() {
        for (String key : json.keySet()) {
            if (!seen.contains(key)) {
                warn(key, "unknown field (ignored)");
            }
        }
    }
}
