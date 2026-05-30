package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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

public class OreChunkRadar
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final ConcurrentHashMap<class_1923, Detection> detections = new ConcurrentHashMap();
    private final ConcurrentLinkedQueue<class_1923> pendingScan = new ConcurrentLinkedQueue();
    private int tickCounter = 0;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgScan = this.settings.createGroup("Scoring");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Integer> marginChunks = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("margin-chunks")).description("Chunks to add beyond your render distance before flagging as 'other player'.")).defaultValue((Object)2)).range(0, 8).sliderMax(8).build());
    private final Setting<Integer> confirmThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-threshold")).description("Total score required to mark a detection as confirmed (green plate).")).defaultValue((Object)10)).range(1, 200).sliderMax(100).build());
    private final Setting<Integer> clusterRadius = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("cluster-radius-chunks")).description("Chunks within this distance share their confidence with each other.")).defaultValue((Object)5)).range(1, 16).sliderMax(16).build());
    private final Setting<Integer> decaySeconds = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("decay-seconds")).description("Seconds before an un-refreshed detection is discarded.")).defaultValue((Object)120)).range(10, 600).sliderMax(300).build());
    private final Setting<Boolean> chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat on new detections and confirmations.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> debugMode = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-mode")).description("Print full detection details to chat.")).defaultValue((Object)false)).build());
    private final Setting<Integer> scorePerOre = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("score-per-ore")).description("Confidence points per ore block found in a flagged chunk.")).defaultValue((Object)1)).range(1, 10).sliderMax(10).build());
    private final Setting<Integer> scorePerSpawner = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("score-per-spawner")).description("Confidence points per Spawner block found (strong signal).")).defaultValue((Object)8)).range(1, 30).sliderMax(30).build());
    private final Setting<Integer> adjacentBonus = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("cluster-neighbor-bonus")).description("Bonus score applied per other detection within cluster-radius.")).defaultValue((Object)3)).range(0, 20).sliderMax(20).build());
    private final Setting<SettingColor> fillUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-unconfirmed")).description("Plate fill for unconfirmed detections.")).defaultValue(new SettingColor(255, 50, 50, 35)).build());
    private final Setting<SettingColor> lineUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-unconfirmed")).description("Plate outline for unconfirmed detections.")).defaultValue(new SettingColor(255, 50, 50, 180)).build());
    private final Setting<SettingColor> fillConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-confirmed")).description("Plate fill for confirmed detections.")).defaultValue(new SettingColor(50, 255, 80, 50)).build());
    private final Setting<SettingColor> lineConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-confirmed")).description("Plate outline for confirmed detections.")).defaultValue(new SettingColor(50, 255, 80, 220)).build());
    private final Setting<Boolean> showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to each detected chunk.")).defaultValue((Object)true)).build());
    private final Setting<SettingColor> tracerUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-unconfirmed")).description("Tracer color for unconfirmed detections.")).defaultValue(new SettingColor(255, 80, 80, 130)).build());
    private final Setting<SettingColor> tracerConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-confirmed")).description("Tracer color for confirmed detections.")).defaultValue(new SettingColor(80, 255, 80, 180)).build());
    private final Setting<Boolean> showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Render a tall alert pillar over confirmed detections.")).defaultValue((Object)true)).build());
    private final Setting<Integer> pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Height of the confirmation pillar in blocks.")).defaultValue((Object)80)).min(16).sliderMax(256).build());

    public OreChunkRadar() {
        super(DonutAddon.CATEGORY, "ore-chunk-radar", "Detects other players by identifying chunks loaded outside your render distance containing ores or spawners. Server cannot patch without breaking chunk loading.");
    }

    public void onActivate() {
        this.detections.clear();
        this.pendingScan.clear();
        this.tickCounter = 0;
        ChatUtils.info((String)"\u00a7a[OreChunkRadar] \u00a77Active. Monitoring chunk loading overspill\u2026", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.detections.clear();
        this.pendingScan.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (OreChunkRadar.MC.field_1687 == null || OreChunkRadar.MC.field_1724 == null) {
            return;
        }
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2672) {
            class_2672 pkt = (class_2672)class_25962;
            int cx = pkt.method_11523();
            int cz = pkt.method_11524();
            int playerCx = OreChunkRadar.MC.field_1724.method_31476().field_9181;
            int playerCz = OreChunkRadar.MC.field_1724.method_31476().field_9180;
            int viewDist = (Integer)OreChunkRadar.MC.field_1690.method_42503().method_41753() + (Integer)this.marginChunks.get();
            if (Math.abs(cx - playerCx) > viewDist || Math.abs(cz - playerCz) > viewDist) {
                class_1923 cp = new class_1923(cx, cz);
                this.pendingScan.add(cp);
                if (((Boolean)this.debugMode.get()).booleanValue()) {
                    ChatUtils.info((String)("[OCR] \u00a77Overspill chunk \u00a7f" + cx + "," + cz + " \u00a77dist=\u00a7f" + Math.max(Math.abs(cx - playerCx), Math.abs(cz - playerCz))), (Object[])new Object[0]);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (OreChunkRadar.MC.field_1687 == null || OreChunkRadar.MC.field_1724 == null) {
            return;
        }
        ++this.tickCounter;
        if (this.tickCounter % 3 == 0) {
            int limit = 8;
            while (!this.pendingScan.isEmpty() && limit-- > 0) {
                class_2818 chunk;
                class_1923 cp = this.pendingScan.poll();
                if (cp == null || (chunk = OreChunkRadar.MC.field_1687.method_8497(cp.field_9181, cp.field_9180)) == null) continue;
                this.runOreScan(cp, chunk);
            }
        }
        if (this.tickCounter % 100 == 0) {
            long cutoff = System.currentTimeMillis() - (long)((Integer)this.decaySeconds.get()).intValue() * 1000L;
            this.detections.entrySet().removeIf(e -> ((Detection)e.getValue()).lastUpdated < cutoff);
        }
        if (this.tickCounter % 40 == 0) {
            this.recalculateClusterScores();
        }
    }

    private void runOreScan(class_1923 cp, class_2818 chunk) {
        class_2826[] sections = chunk.method_12006();
        int oreScore = 0;
        int spawnerScore = 0;
        for (class_2826 section : sections) {
            boolean hasTarget;
            if (section == null || section.method_38292() || !(hasTarget = section.method_12265().method_19526(s -> this.isTargetBlock(s.method_26204())))) continue;
            for (int bx = 0; bx < 16; ++bx) {
                for (int by = 0; by < 16; ++by) {
                    for (int bz = 0; bz < 16; ++bz) {
                        class_2248 b = section.method_12254(bx, by, bz).method_26204();
                        if (b == class_2246.field_10260) {
                            spawnerScore += ((Integer)this.scorePerSpawner.get()).intValue();
                            continue;
                        }
                        if (!this.isOreBlock(b)) continue;
                        oreScore += ((Integer)this.scorePerOre.get()).intValue();
                    }
                }
            }
        }
        int totalScore = oreScore + spawnerScore;
        if (totalScore > 0) {
            int surfaceY = this.computeSurfaceY(chunk);
            Detection existing = this.detections.get(cp);
            if (existing != null) {
                existing.baseScore += totalScore;
                existing.lastUpdated = System.currentTimeMillis();
            } else {
                this.detections.put(cp, new Detection(cp, totalScore, surfaceY));
                if (((Boolean)this.chatNotify.get()).booleanValue()) {
                    ChatUtils.info((String)("[OCR] \u00a7eDetection\u00a7r chunk \u00a7f" + cp.field_9181 + "," + cp.field_9180 + " \u00a77ore=" + oreScore + " \u00a75spawner=" + spawnerScore), (Object[])new Object[0]);
                }
            }
        }
    }

    private void recalculateClusterScores() {
        int radius = (Integer)this.clusterRadius.get();
        int bonus = (Integer)this.adjacentBonus.get();
        int threshold = (Integer)this.confirmThreshold.get();
        for (Detection d : this.detections.values()) {
            int neighborBonus = 0;
            for (Detection other : this.detections.values()) {
                if (other == d || Math.abs(other.cp.field_9181 - d.cp.field_9181) > radius || Math.abs(other.cp.field_9180 - d.cp.field_9180) > radius) continue;
                neighborBonus += bonus;
            }
            d.totalScore = d.baseScore + neighborBonus;
            boolean wasConfirmed = d.confirmed;
            boolean bl = d.confirmed = d.totalScore >= threshold;
            if (wasConfirmed || !d.confirmed || !((Boolean)this.chatNotify.get()).booleanValue()) continue;
            ChatUtils.info((String)("[OCR] \u00a7a\u00a7lPLAYER CONFIRMED\u00a7r near chunk \u00a7f" + d.cp.field_9181 + "," + d.cp.field_9180 + " \u00a77confidence=\u00a7f" + d.totalScore), (Object[])new Object[0]);
        }
    }

    private boolean isTargetBlock(class_2248 b) {
        return this.isOreBlock(b) || b == class_2246.field_10260;
    }

    private boolean isOreBlock(class_2248 b) {
        return b == class_2246.field_10442 || b == class_2246.field_29029 || b == class_2246.field_10013 || b == class_2246.field_29220 || b == class_2246.field_22109 || b == class_2246.field_10571 || b == class_2246.field_29026 || b == class_2246.field_10212 || b == class_2246.field_29027 || b == class_2246.field_23077 || b == class_2246.field_10213 || b == class_2246.field_10090 || b == class_2246.field_29028 || b == class_2246.field_10418 || b == class_2246.field_29219 || b == class_2246.field_27120 || b == class_2246.field_29221;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (OreChunkRadar.MC.field_1724 == null || OreChunkRadar.MC.field_1687 == null) {
            return;
        }
        class_243 eye = OreChunkRadar.MC.field_1724.method_5836(event.tickDelta);
        for (Detection d : this.detections.values()) {
            double plateY = (double)d.surfaceY + 0.05;
            double x0 = d.cp.method_8326();
            double z0 = d.cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            double cx = d.cp.method_33940();
            double cz = d.cp.method_33942();
            SettingColor fill = d.confirmed ? (SettingColor)this.fillConfirmed.get() : (SettingColor)this.fillUnconfirmed.get();
            SettingColor line = d.confirmed ? (SettingColor)this.lineConfirmed.get() : (SettingColor)this.lineUnconfirmed.get();
            event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.15, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            if (d.confirmed && ((Boolean)this.showPillar.get()).booleanValue()) {
                int h = (Integer)this.pillarHeight.get();
                event.renderer.box(x0, plateY, z0, x1, plateY + (double)h, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            }
            if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
            SettingColor tc = d.confirmed ? (SettingColor)this.tracerConfirmed.get() : (SettingColor)this.tracerUnconfirmed.get();
            event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, (Color)tc);
        }
    }

    private int computeSurfaceY(class_2818 chunk) {
        if (OreChunkRadar.MC.field_1687 == null) {
            return 64;
        }
        int highest = OreChunkRadar.MC.field_1687.method_31607();
        for (int bx = 0; bx < 16; ++bx) {
            for (int bz = 0; bz < 16; ++bz) {
                int y = chunk.method_12005(class_2902.class_2903.field_13197, bx, bz);
                if (y <= highest) continue;
                highest = y;
            }
        }
        return highest;
    }

    private static class Detection {
        final class_1923 cp;
        int baseScore;
        int totalScore;
        boolean confirmed;
        int surfaceY;
        long lastUpdated;

        Detection(class_1923 cp, int baseScore, int surfaceY) {
            this.cp = cp;
            this.baseScore = baseScore;
            this.totalScore = baseScore;
            this.confirmed = false;
            this.surfaceY = surfaceY;
            this.lastUpdated = System.currentTimeMillis();
        }
    }
}
