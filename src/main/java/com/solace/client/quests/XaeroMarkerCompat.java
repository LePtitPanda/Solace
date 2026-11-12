package com.solace.client.quests;

import com.solace.Solace;
import com.solace.quests.network.Views.MarkerView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.List;
import java.util.Locale;

/**
 * Quest markers as Xaero "third-party waypoints": a list owned by the id solace:quests that the
 * minimap, the in-world view and Xaero's World Map all draw but never save. The player's own
 * waypoint sets and files are not touched. Only loaded when Xaero's Minimap is installed.
 */
final class XaeroMarkerCompat {
    private static final ResourceLocation ORIGIN = new ResourceLocation(Solace.MOD_ID, "quests");

    private XaeroMarkerCompat() {
    }

    static void update(List<MarkerView> markers) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
        Minecraft minecraft = Minecraft.getInstance();
        if (world == null || minecraft.level == null) {
            return;
        }
        // The list belongs to the current dimension's container, so only this dimension's markers go in.
        ThirdPartyWaypoints waypoints = world.getContainer().getThirdPartyWaypointManager().get(ORIGIN);
        waypoints.clear();
        String dimension = minecraft.level.dimension().location().toString();
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
            waypoints.add(marker.id(), waypoint);
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
