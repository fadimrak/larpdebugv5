package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.class_243;
import net.minecraft.class_2708;
import net.minecraft.class_2818;
import net.minecraft.class_2902;
import net.minecraft.class_742;

public class RubberbandRadar
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Integer> undergroundY;
    private final Setting<Integer> cooldownSeconds;
    private final Setting<Integer> confirmHits;
    private final Setting<Integer> decayMinutes;
    private final Setting<Boolean> chatNotify;
    private final Setting<Boolean> debugMode;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> fillNew;
    private final Setting<SettingColor> lineNew;
    private final Setting<SettingColor> fillWarm;
    private final Setting<SettingColor> lineWarm;
    private final Setting<SettingColor> fillConfirmed;
    private final Setting<SettingColor> lineConfirmed;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerColor;
    private final Setting<Boolean> showPillar;
    private final Setting<Integer> pillarHeight;
    private final ConcurrentHashMap<class_1923, Detection> detections;
    private final ConcurrentHashMap<UUID, Long> cooldowns;
    private static final UUID SELF_UUID = new UUID(0L, 0L);
    private int tickCounter;

    public RubberbandRadar() {
        super(DonutAddon.CATEGORY, "rubberband-radar", "Detects players at Y<0 via entity scan and our own position corrections. No chunk data required \u2014 Goliath-immune.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.undergroundY = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("underground-y")).description("Y level below which a position is considered underground.")).defaultValue((Object)0)).range(-64, 32).sliderRange(-64, 32).build());
        this.cooldownSeconds = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("cooldown-seconds")).description("Seconds before the same player UUID can update a detection again.")).defaultValue((Object)10)).range(5, 120).sliderMax(60).build());
        this.confirmHits = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-hits")).description("Number of separate cooldown-gated observations to mark a chunk CONFIRMED.")).defaultValue((Object)2)).range(1, 20).sliderMax(10).build());
        this.decayMinutes = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("decay-minutes")).description("Minutes before an idle detection is discarded.")).defaultValue((Object)30)).range(1, 60).sliderMax(30).build());
        this.chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat on new detections and confirmations.")).defaultValue((Object)true)).build());
        this.debugMode = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-mode")).description("Verbose chat output on every detected underground position.")).defaultValue((Object)false)).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render chunk plates and tracers.")).defaultValue((Object)true)).build());
        this.fillNew = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-new")).description("Plate fill \u2014 new detection (1 hit).")).defaultValue(new SettingColor(0, 100, 255, 25)).visible(() -> this.renderEnabled.get())).build());
        this.lineNew = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-new")).description("Plate outline \u2014 new detection.")).defaultValue(new SettingColor(0, 120, 255, 160)).visible(() -> this.renderEnabled.get())).build());
        this.fillWarm = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-warm")).description("Plate fill \u2014 warm detection (2+ hits, not confirmed).")).defaultValue(new SettingColor(255, 140, 0, 35)).visible(() -> this.renderEnabled.get())).build());
        this.lineWarm = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-warm")).description("Plate outline \u2014 warm detection.")).defaultValue(new SettingColor(255, 140, 0, 180)).visible(() -> this.renderEnabled.get())).build());
        this.fillConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-confirmed")).description("Plate fill \u2014 confirmed base chunk.")).defaultValue(new SettingColor(255, 30, 30, 50)).visible(() -> this.renderEnabled.get())).build());
        this.lineConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-confirmed")).description("Plate outline \u2014 confirmed base chunk.")).defaultValue(new SettingColor(255, 30, 30, 220)).visible(() -> this.renderEnabled.get())).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to each detected chunk.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).defaultValue(new SettingColor(255, 50, 50, 160)).visible(() -> this.renderEnabled.get())).build());
        this.showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Tall pillar over CONFIRMED chunks.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Pillar height in blocks.")).defaultValue((Object)160)).min(16).sliderMax(256).visible(() -> this.showPillar.get())).build());
        this.detections = new ConcurrentHashMap();
        this.cooldowns = new ConcurrentHashMap();
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.detections.clear();
        this.cooldowns.clear();
        this.tickCounter = 0;
        ChatUtils.info((String)"[RubberbandRadar] Active. Monitoring for underground players\u2026", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.detections.clear();
        this.cooldowns.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof class_2708) {
            if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
                return;
            }
            double corrY = this.mc.field_1724.method_23318();
            if (corrY >= (double)((Integer)this.undergroundY.get()).intValue()) {
                return;
            }
            class_1923 cp = this.mc.field_1724.method_31476();
            if (((Boolean)this.debugMode.get()).booleanValue()) {
                ChatUtils.info((String)("[RBR] SELF corrected to Y=" + String.format("%.1f", corrY) + " at chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
            }
            this.record(cp, SELF_UUID, null);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        ++this.tickCounter;
        if (this.tickCounter % 20 != 0) {
            return;
        }
        for (class_742 entity : this.mc.field_1687.method_18456()) {
            if (entity.method_5667().equals(this.mc.field_1724.method_5667()) || entity.method_23318() >= (double)((Integer)this.undergroundY.get()).intValue()) continue;
            class_1923 cp = entity.method_31476();
            if (((Boolean)this.debugMode.get()).booleanValue()) {
                ChatUtils.info((String)("[RBR] PLAYER " + entity.method_5477().getString() + " at Y=" + String.format("%.1f", entity.method_23318()) + " chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
            }
            this.record(cp, entity.method_5667(), entity.method_5477().getString());
        }
        if (this.tickCounter % 100 == 0) {
            long cutoff = System.currentTimeMillis() - (long)((Integer)this.decayMinutes.get()).intValue() * 60000L;
            this.detections.entrySet().removeIf(e -> ((Detection)e.getValue()).lastMs < cutoff);
            long cooldownMs = (long)((Integer)this.cooldownSeconds.get()).intValue() * 1000L;
            this.cooldowns.entrySet().removeIf(e -> (Long)e.getValue() < System.currentTimeMillis() - cooldownMs * 2L);
        }
    }

    private void record(class_1923 cp, UUID uuid, String name) {
        long now = System.currentTimeMillis();
        long cooldownMs = (long)((Integer)this.cooldownSeconds.get()).intValue() * 1000L;
        Long lastHit = this.cooldowns.get(uuid);
        if (lastHit != null && now - lastHit < cooldownMs) {
            return;
        }
        this.cooldowns.put(uuid, now);
        Detection existing = this.detections.get(cp);
        if (existing == null) {
            int surfaceY = this.getSurfaceY(cp);
            Detection d = new Detection(cp, uuid.equals(SELF_UUID) ? null : uuid, name, surfaceY);
            this.detections.put(cp, d);
            if (((Boolean)this.chatNotify.get()).booleanValue()) {
                Object src = name != null ? "PLAYER " + name : "SELF correction";
                ChatUtils.info((String)("[RBR] New underground detection at chunk " + cp.field_9181 + "," + cp.field_9180 + " (" + (String)src + ")"), (Object[])new Object[0]);
            }
        } else {
            boolean wasConfirmed = existing.confirmed;
            existing.addHit(uuid.equals(SELF_UUID) ? null : uuid, name);
            boolean bl = existing.confirmed = existing.hits >= (Integer)this.confirmHits.get();
            if (!wasConfirmed && existing.confirmed && ((Boolean)this.chatNotify.get()).booleanValue()) {
                ChatUtils.info((String)("[RBR] BASE CONFIRMED chunk " + cp.field_9181 + "," + cp.field_9180 + " player=" + existing.label() + " hits=" + existing.hits), (Object[])new Object[0]);
            }
        }
    }

    private int getSurfaceY(class_1923 cp) {
        if (this.mc.field_1687 == null) {
            return 64;
        }
        class_2818 chunk = this.mc.field_1687.method_8497(cp.field_9181, cp.field_9180);
        if (chunk == null) {
            return 64;
        }
        int highest = this.mc.field_1687.method_31607();
        for (int bx = 0; bx < 16; ++bx) {
            for (int bz = 0; bz < 16; ++bz) {
                int y = chunk.method_12005(class_2902.class_2903.field_13197, bx, bz);
                if (y <= highest) continue;
                highest = y;
            }
        }
        return highest;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue() || this.mc.field_1724 == null) {
            return;
        }
        class_243 eye = this.mc.field_1724.method_5836(event.tickDelta);
        for (Detection d : this.detections.values()) {
            SettingColor line;
            SettingColor fill;
            double plateY = (double)d.surfaceY + 0.05;
            double x0 = d.cp.method_8326();
            double z0 = d.cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            double cx = d.cp.method_33940();
            double cz = d.cp.method_33942();
            if (d.confirmed) {
                fill = (SettingColor)this.fillConfirmed.get();
                line = (SettingColor)this.lineConfirmed.get();
            } else if (d.hits >= 2) {
                fill = (SettingColor)this.fillWarm.get();
                line = (SettingColor)this.lineWarm.get();
            } else {
                fill = (SettingColor)this.fillNew.get();
                line = (SettingColor)this.lineNew.get();
            }
            event.renderer.box(x0, plateY, z0, x1, plateY + 0.2, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            if (d.confirmed && ((Boolean)this.showPillar.get()).booleanValue()) {
                int h = (Integer)this.pillarHeight.get();
                event.renderer.box(x0, plateY + 0.2, z0, x1, plateY + (double)h, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            }
            if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
            event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY + 0.1, cz, (Color)this.tracerColor.get());
        }
    }

    private static class Detection {
        final class_1923 cp;
        UUID playerUuid;
        String playerName;
        int hits;
        long lastMs;
        int surfaceY;
        boolean confirmed;

        Detection(class_1923 cp, UUID uuid, String name, int surfaceY) {
            this.cp = cp;
            this.playerUuid = uuid;
            this.playerName = name;
            this.hits = 1;
            this.lastMs = System.currentTimeMillis();
            this.surfaceY = surfaceY;
            this.confirmed = false;
        }

        void addHit(UUID uuid, String name) {
            ++this.hits;
            this.lastMs = System.currentTimeMillis();
            if (uuid != null && this.playerUuid == null) {
                this.playerUuid = uuid;
                this.playerName = name;
            }
        }

        String label() {
            return this.playerName != null ? this.playerName : "SELF";
        }
    }
}
