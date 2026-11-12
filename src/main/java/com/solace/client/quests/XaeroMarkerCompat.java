package com.solace.client.quests;

import com.solace.Solace;
import com.solace.quests.network.Views.MarkerView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

import java.util.List;
import java.util.Locale;

/**
 * Quest markers as Xaero's Minimap "custom waypoints": a list owned by the id solace:quests that
 * Xaero draws (minimap, in-world and, through its own bridge, the world map) but never saves.
 * The player's own waypoint sets and files are not touched. Only loaded when Xaero is installed.
 */
final class XaeroMarkerCompat {
    private static final ResourceLocation ORIGIN = new ResourceLocation(Solace.MOD_ID, "quests");

    private XaeroMarkerCompat() {
    }

    static void update(List<MarkerView> markers) {
        {
            MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) {
                return;
            }
            Int2ObjectMap<Waypoint> waypoints = session.getWorldManager().getCustomWaypoints(ORIGIN);
            waypoints.clear();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            // Custom waypoints are not tied to a dimension, so only the current one's are listed.
            String dimension = minecraft.level.dimension().location().toString();
            int index = 0;
            for (MarkerView marker : markers) {
                if (!marker.dimension().equals(dimension)) {
                    continue;
                }
                String name = marker.label().getString();
                boolean noY = marker.y() == MarkerView.NO_Y;
                Waypoint waypoint = new Waypoint(marker.x(), noY ? 64 : marker.y(), marker.z(), name,
                        name.isEmpty() ? "!" : name.substring(0, 1).toUpperCase(Locale.ROOT), color(marker.color()),
                        WaypointPurpose.NORMAL, true);
                waypoint.setYIncluded(!noY);
                waypoints.put(index++, waypoint);
            }
        }
    }

    private static WaypointColor color(String name) {
        try {
            return WaypointColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return WaypointColor.GOLD;
        }
    }
}
