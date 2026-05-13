package com.solace.compat;

import com.github.mim1q.minecells.dimension.MineCellsDimension;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MineCellsDoorwayCompat {
    private static final String UNDERGARDEN_DIMENSION = "undergarden:undergarden";
    private static final String OVERWORLD_DIMENSION = "minecraft:overworld";
    private static final ResourceKey<Level> UNDERGARDEN_KEY = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation("undergarden", "undergarden")
    );

    private MineCellsDoorwayCompat() {
    }

    public static MineCellsDimension sourceDimensionFor(Object world) {
        if (isDimension(world, UNDERGARDEN_DIMENSION)) {
            return MineCellsDimension.OVERWORLD;
        }
        if (isDimension(world, OVERWORLD_DIMENSION)) {
            return MineCellsDimension.OVERWORLD;
        }
        return mineCellsDimensionOf(world);
    }

    public static MineCellsDimension strictSourceDimensionFor(Object world) {
        if (isDimension(world, UNDERGARDEN_DIMENSION)) {
            return MineCellsDimension.OVERWORLD;
        }
        if (isDimension(world, OVERWORLD_DIMENSION)) {
            return null;
        }
        return mineCellsDimensionOf(world);
    }

    public static String translationKeyFor(MineCellsDimension dimension) {
        if (dimension == MineCellsDimension.OVERWORLD) {
            return "dimension.undergarden.undergarden";
        }
        return dimension == null ? "" : dimension.translationKey;
    }

    public static Object sourceWorldFor(Object server) {
        if (server instanceof MinecraftServer minecraftServer) {
            ServerLevel undergarden = minecraftServer.getLevel(UNDERGARDEN_KEY);
            if (undergarden != null) {
                return undergarden;
            }
        }

        return invokeNoArg(server, "m_129783_", "method_30002", "overworld", "getOverworld");
    }

    public static Object worldForMineCellsDimension(MineCellsDimension dimension, Object currentWorld) {
        if (dimension == MineCellsDimension.OVERWORLD) {
            Object server = invokeNoArg(currentWorld, "m_7654_", "method_8503", "getServer");
            Object undergarden = sourceWorldFor(server);
            if (undergarden != null) {
                return undergarden;
            }
        }
        return invoke(dimension, new Object[]{currentWorld}, "getWorld");
    }

    public static Object worldForKey(Object server, Object key) {
        if (isDimensionKey(key, OVERWORLD_DIMENSION)) {
            Object undergarden = sourceWorldFor(server);
            if (undergarden != null) {
                return undergarden;
            }
        }
        return invoke(server, new Object[]{key}, "m_129880_", "method_3847", "getWorld", "getLevel");
    }

    public static void writePortalDataNbt(Object portalData, Object nbt) {
        Object fromDimension = invokeNoArg(portalData, "fromDimension");
        Object toDimension = invokeNoArg(portalData, "toDimension");
        Object fromPos = invokeNoArg(portalData, "fromPos");
        Object toPos = invokeNoArg(portalData, "toPos");

        putString(nbt, "FromDimension", dimensionIdOrHub(fromDimension));
        putString(nbt, "ToDimension", dimensionIdOrHub(toDimension));
        putLong(nbt, "FromPos", blockPosLong(fromPos));
        putLong(nbt, "ToPos", blockPosLong(toPos));
    }

    private static MineCellsDimension mineCellsDimensionOf(Object world) {
        Object result = invokeStatic(
                MineCellsDimension.class,
                new Object[]{world},
                "of"
        );
        return result instanceof MineCellsDimension dimension ? dimension : null;
    }

    private static boolean isDimension(Object world, String expected) {
        String dimension = dimensionId(world);
        return expected.equals(dimension);
    }

    private static boolean isDimensionKey(Object key, String expected) {
        Object location = invokeNoArg(key, "m_135782_", "method_29177", "location", "getValue", "getLocation");
        return location != null && expected.equals(location.toString());
    }

    private static String dimensionIdOrHub(Object dimension) {
        Object safeDimension = dimension == null ? MineCellsDimension.OVERWORLD : dimension;
        Object key = fieldValue(safeDimension, "key");
        Object location = invokeNoArg(key, "m_135782_", "method_29177", "location", "getValue", "getLocation");
        return location == null ? "overworld" : location.toString();
    }

    private static String dimensionId(Object world) {
        if (world == null) {
            return "";
        }

        Object key = invokeNoArg(world, "m_46472_", "method_27983", "dimension", "getRegistryKey", "getDimensionKey");
        Object location = invokeNoArg(key, "m_135782_", "method_29177", "location", "getValue", "getLocation");
        return location == null ? "" : location.toString();
    }

    private static long blockPosLong(Object pos) {
        Object value = invokeNoArg(pos, "m_121878_", "asLong", "asLong");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) {
            return null;
        }
        return invoke(target, new Object[0], names);
    }

    private static Object invoke(Object target, Object[] args, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                        return invokeMethod(target, method, args);
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object invokeStatic(Class<?> owner, Object[] args, String... names) {
        for (String name : names) {
            for (Method method : owner.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    return invokeMethod(null, method, args);
                }
            }
        }
        return null;
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void putString(Object nbt, String key, String value) {
        invoke(nbt, new Object[]{key, value}, "m_128359_", "putString");
    }

    private static void putLong(Object nbt, String key, long value) {
        invoke(nbt, new Object[]{key, value}, "m_128356_", "putLong");
    }

    private static Object invokeMethod(Object target, Method method, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
