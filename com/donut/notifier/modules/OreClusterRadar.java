package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2672;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2902;
import net.minecraft.class_310;
import net.minecraft.class_742;

public class OreClusterRadar
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final Set<class_1923> redChunks = ConcurrentHashMap.newKeySet();
    private final Set<class_1923> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final Set<class_1923> greenChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> scannedKeys = ConcurrentHashMap.newKeySet();
    private final Deque<class_1923> scanQueue = new ConcurrentLinkedDeque<class_1923>();
    private final List<class_243> clusterCenters = new ArrayList<class_243>();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgScan = this.settings.createGroup("Scanning");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat when a new ore cluster is flagged.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> debugMode = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-mode")).description("Extra scan detail in chat.")).defaultValue((Object)false)).build());
    private final Setting<Integer> redZoneRadius = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("red-zone-radius")).description("Chunk radius around local player marked as exclusion zone (red).")).defaultValue((Object)9)).range(1, 20).sliderMax(16).build());
    private final Setting<Integer> haloRadius = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("halo-radius")).description("Chunk radius of green halo spread from each flagged chunk.")).defaultValue((Object)9)).range(1, 20).sliderMax(16).build());
    private final Setting<Integer> oreYCeiling = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("ore-y-ceiling")).description("Only count ore blocks strictly BELOW this Y level (reduces surface noise).")).defaultValue((Object)100)).range(-64, 320).sliderMax(160).build());
    private final Setting<Integer> chunksPerTick = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("chunks-per-tick")).description("Maximum chunks dequeued and scanned per tick.")).defaultValue((Object)50)).range(1, 200).sliderMax(100).build());
    private final Setting<Integer> viewDistMargin = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("view-dist-margin")).description("Extra chunks beyond render distance to enqueue for scanning.")).defaultValue((Object)2)).range(0, 8).sliderMax(8).build());
    private final Setting<Double> boxHalfSize = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("box-half-size")).description("Half-width of the rendered cluster pillar in blocks.")).defaultValue(8.0).min(1.0).sliderMax(16.0).build());
    private final Setting<Integer> nearPlayerDist = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("near-player-dist")).description("Distance (blocks) at which another player causes the pillar to turn green.")).defaultValue((Object)144)).range(16, 512).sliderMax(300).build());
    private final Setting<SettingColor> redFillColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("red-fill-color")).description("Pillar fill when no nearby player is detected.")).defaultValue(new SettingColor(255, 0, 0, 50)).build());
    private final Setting<SettingColor> redLineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("red-line-color")).description("Pillar outline when no nearby player is detected.")).defaultValue(new SettingColor(255, 0, 0, 220)).build());
    private final Setting<SettingColor> greenFillColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("green-fill-color")).description("Pillar fill when another player is nearby (known player cluster).")).defaultValue(new SettingColor(0, 255, 0, 50)).build());
    private final Setting<SettingColor> greenLineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("green-line-color")).description("Pillar outline when another player is nearby.")).defaultValue(new SettingColor(0, 255, 0, 220)).build());

    public OreClusterRadar() {
        super(DonutAddon.CATEGORY, "ore-cluster-radar", "Finds chunks loaded outside your render distance containing valuable ores below Y=100, clusters nearby detections via BFS, and renders predicted player locations.");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void onActivate() {
        this.redChunks.clear();
        this.flaggedChunks.clear();
        this.greenChunks.clear();
        this.scannedKeys.clear();
        this.scanQueue.clear();
        List<class_243> list = this.clusterCenters;
        synchronized (list) {
            this.clusterCenters.clear();
        }
        ChatUtils.info((String)"\u00a7b[OreClusterRadar] \u00a77Active. Scanning chunks for ore clusters\u2026", (Object[])new Object[0]);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void onDeactivate() {
        this.redChunks.clear();
        this.flaggedChunks.clear();
        this.greenChunks.clear();
        this.scannedKeys.clear();
        this.scanQueue.clear();
        List<class_243> list = this.clusterCenters;
        synchronized (list) {
            this.clusterCenters.clear();
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (OreClusterRadar.MC.field_1687 == null || OreClusterRadar.MC.field_1724 == null) {
            return;
        }
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2672) {
            class_2672 pkt = (class_2672)class_25962;
            this.enqueue(new class_1923(pkt.method_11523(), pkt.method_11524()), true);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        class_1923 cp;
        if (OreClusterRadar.MC.field_1687 == null || OreClusterRadar.MC.field_1724 == null) {
            return;
        }
        class_1923 playerPos = OreClusterRadar.MC.field_1724.method_31476();
        int redRad = (Integer)this.redZoneRadius.get();
        int greenRad = (Integer)this.haloRadius.get();
        for (int x = -redRad; x <= redRad; ++x) {
            for (int z = -redRad; z <= redRad; ++z) {
                this.redChunks.add(new class_1923(playerPos.field_9181 + x, playerPos.field_9180 + z));
            }
        }
        for (class_742 player : OreClusterRadar.MC.field_1687.method_18456()) {
            class_1923 gp;
            if (player.method_5667().equals(OreClusterRadar.MC.field_1724.method_5667()) || this.redChunks.contains(gp = player.method_31476()) || !this.flaggedChunks.add(gp)) continue;
            this.spreadGreenHalo(gp, greenRad);
            if (!((Boolean)this.chatNotify.get()).booleanValue()) continue;
            ChatUtils.info((String)("[OCR] \u00a7aPlayer halo\u00a7r @ chunk " + gp.field_9181 + "," + gp.field_9180), (Object[])new Object[0]);
        }
        int viewDist = (Integer)OreClusterRadar.MC.field_1690.method_42503().method_41753() + (Integer)this.viewDistMargin.get();
        for (int x = -viewDist; x <= viewDist; ++x) {
            for (int z = -viewDist; z <= viewDist; ++z) {
                class_1923 cp2 = new class_1923(playerPos.field_9181 + x, playerPos.field_9180 + z);
                if (this.redChunks.contains(cp2)) continue;
                this.enqueue(cp2, false);
            }
        }
        int limit = (Integer)this.chunksPerTick.get();
        while (!this.scanQueue.isEmpty() && limit-- > 0 && (cp = this.scanQueue.pollFirst()) != null) {
            if (this.redChunks.contains(cp)) continue;
            class_2818 chunk = OreClusterRadar.MC.field_1687.method_8497(cp.field_9181, cp.field_9180);
            if (chunk == null) {
                this.scannedKeys.remove(cp.method_8324());
                continue;
            }
            if (!this.containsTargetOre(chunk) || !this.flaggedChunks.add(cp)) continue;
            this.spreadGreenHalo(cp, greenRad);
            if (!((Boolean)this.chatNotify.get()).booleanValue()) continue;
            ChatUtils.info((String)("[OCR] \u00a7eOre cluster\u00a7r flagged @ chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
        }
        this.updateClusters();
    }

    private boolean containsTargetOre(class_2818 chunk) {
        if (OreClusterRadar.MC.field_1687 == null) {
            return false;
        }
        class_2826[] sections = chunk.method_12006();
        int bottomY = OreClusterRadar.MC.field_1687.method_31607();
        int yCeil = (Integer)this.oreYCeiling.get();
        for (int s = 0; s < sections.length; ++s) {
            if (sections[s] == null || sections[s].method_38292()) continue;
            int sectionBaseY = bottomY + s * 16;
            if (sectionBaseY >= yCeil) break;
            boolean hasTarget = sections[s].method_12265().method_19526(st -> this.isTargetBlock(st.method_26204()));
            if (!hasTarget) continue;
            int byMax = Math.min(16, yCeil - sectionBaseY);
            for (int by = 0; by < byMax; ++by) {
                for (int bx = 0; bx < 16; ++bx) {
                    for (int bz = 0; bz < 16; ++bz) {
                        if (!this.isTargetBlock(sections[s].method_12254(bx, by, bz).method_26204())) continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isTargetBlock(class_2248 b) {
        return b == class_2246.field_10442 || b == class_2246.field_29029 || b == class_2246.field_10013 || b == class_2246.field_29220 || b == class_2246.field_22109 || b == class_2246.field_10571 || b == class_2246.field_29026 || b == class_2246.field_23077 || b == class_2246.field_10212 || b == class_2246.field_29027 || b == class_2246.field_27120 || b == class_2246.field_29221 || b == class_2246.field_10260;
    }

    private void spreadGreenHalo(class_1923 center, int radius) {
        for (int x = -radius; x <= radius; ++x) {
            for (int z = -radius; z <= radius; ++z) {
                class_1923 neighbor = new class_1923(center.field_9181 + x, center.field_9180 + z);
                if (this.redChunks.contains(neighbor)) continue;
                this.greenChunks.add(neighbor);
            }
        }
    }

    private void enqueue(class_1923 cp, boolean priority) {
        if (cp != null && this.scannedKeys.add(cp.method_8324())) {
            if (priority) {
                this.scanQueue.addFirst(cp);
            } else {
                this.scanQueue.addLast(cp);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void updateClusters() {
        if (this.greenChunks.isEmpty()) {
            List<class_243> list = this.clusterCenters;
            synchronized (list) {
                this.clusterCenters.clear();
            }
            return;
        }
        ArrayList<List<class_1923>> clusters = new ArrayList<List<class_1923>>();
        HashSet<class_1923> visited = new HashSet<class_1923>();
        for (class_1923 start : new ArrayList<class_1923>(this.greenChunks)) {
            if (visited.contains(start)) continue;
            List<class_1923> cluster = new ArrayList();
            ArrayDeque<class_1923> bfsQueue = new ArrayDeque<class_1923>();
            bfsQueue.add(start);
            visited.add(start);
            while (!bfsQueue.isEmpty()) {
                class_1923 curr = (class_1923)bfsQueue.poll();
                cluster.add(curr);
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        class_1923 nb;
                        if (dx == 0 && dz == 0 || !this.greenChunks.contains(nb = new class_1923(curr.field_9181 + dx, curr.field_9180 + dz)) || !visited.add(nb)) continue;
                        bfsQueue.add(nb);
                    }
                }
            }
            clusters.add(cluster);
        }
        List<class_243> list = this.clusterCenters;
        synchronized (list) {
            this.clusterCenters.clear();
            for (List<class_1923> cluster : clusters) {
                double sumX = 0.0;
                double sumZ = 0.0;
                for (class_1923 cp : cluster) {
                    sumX += (double)cp.method_33940();
                    sumZ += (double)cp.method_33942();
                }
                this.clusterCenters.add(new class_243(sumX / (double)cluster.size(), 0.0, sumZ / (double)cluster.size()));
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (OreClusterRadar.MC.field_1687 == null || OreClusterRadar.MC.field_1724 == null) {
            return;
        }
        double nearDist = ((Integer)this.nearPlayerDist.get()).intValue();
        double halfSize = (Double)this.boxHalfSize.get();
        List<class_243> list = this.clusterCenters;
        synchronized (list) {
            for (class_243 center : this.clusterCenters) {
                int cz;
                int cx = (int)Math.floor(center.field_1352 / 16.0);
                class_2818 chunk = OreClusterRadar.MC.field_1687.method_8497(cx, cz = (int)Math.floor(center.field_1350 / 16.0));
                if (chunk == null) continue;
                int localX = (int)Math.floor(center.field_1352) & 0xF;
                int localZ = (int)Math.floor(center.field_1350) & 0xF;
                int surfaceY = chunk.method_12005(class_2902.class_2903.field_13197, localX, localZ);
                boolean nearPlayer = false;
                for (class_742 player : OreClusterRadar.MC.field_1687.method_18456()) {
                    if (player.method_5667().equals(OreClusterRadar.MC.field_1724.method_5667())) continue;
                    double distX = Math.abs(player.method_23317() - center.field_1352);
                    double distZ = Math.abs(player.method_23321() - center.field_1350);
                    if (!(distX <= nearDist) || !(distZ <= nearDist)) continue;
                    nearPlayer = true;
                    break;
                }
                SettingColor fill = nearPlayer ? (SettingColor)this.greenFillColor.get() : (SettingColor)this.redFillColor.get();
                SettingColor line = nearPlayer ? (SettingColor)this.greenLineColor.get() : (SettingColor)this.redLineColor.get();
                Color fillC = new Color(fill.r, fill.g, fill.b, fill.a);
                Color lineC = new Color(line.r, line.g, line.b, line.a);
                event.renderer.box(center.field_1352 - halfSize, -63.0, center.field_1350 - halfSize, center.field_1352 + halfSize, (double)surfaceY, center.field_1350 + halfSize, fillC, lineC, ShapeMode.Both, 0);
            }
        }
    }
}
