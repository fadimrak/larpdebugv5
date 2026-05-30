package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2767;
import net.minecraft.class_3414;

public class SpawnerSoundRadar
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgFilter;
    private final SettingGroup sgRender;
    private final Setting<Integer> confirmHits;
    private final Setting<Double> clusterRadius;
    private final Setting<Integer> decaySeconds;
    private final Setting<Boolean> chatNotify;
    private final Setting<Boolean> debugAll;
    private final Setting<Boolean> broadMatch;
    private final Setting<String> exactId;
    private final Setting<Double> maxRange;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> fillUnconfirmed;
    private final Setting<SettingColor> lineUnconfirmed;
    private final Setting<SettingColor> fillConfirmed;
    private final Setting<SettingColor> lineConfirmed;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerUnconfirmed;
    private final Setting<SettingColor> tracerConfirmed;
    private final Setting<Boolean> showPillar;
    private final Setting<Integer> pillarHeight;
    private final List<Detection> detections;
    private int tickCounter;

    public SpawnerSoundRadar() {
        super(DonutAddon.CATEGORY, "spawner-sound-radar", "Triangulates spawner positions from PlaySoundS2CPacket. Works at any altitude \u2014 bypasses Goliath chunk masking entirely.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgFilter = this.settings.createGroup("Sound Filter");
        this.sgRender = this.settings.createGroup("Render");
        this.confirmHits = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-hits")).description("Number of sound packets from the same location to mark as CONFIRMED.")).defaultValue((Object)2)).range(1, 20).sliderMax(10).build());
        this.clusterRadius = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("cluster-radius")).description("Max distance (blocks) between two sounds to be treated as the same spawner.")).defaultValue(8.0).min(1.0).sliderMax(20.0).build());
        this.decaySeconds = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("decay-seconds")).description("Seconds of silence before a detection is discarded.")).defaultValue((Object)600)).range(30, 1800).sliderMax(600).build());
        this.chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat when a spawner is first confirmed.")).defaultValue((Object)true)).build());
        this.debugAll = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-all-hits")).description("Print every matched sound packet to chat (verbose, for tuning).")).defaultValue((Object)false)).build());
        this.broadMatch = this.sgFilter.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("broad-match")).description("Match any sound ID containing 'spawner' (recommended). Disable to use exact-ID whitelist only.")).defaultValue((Object)true)).build());
        this.exactId = this.sgFilter.add((Setting)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)new StringSetting.Builder().name("exact-sound-id")).description("Exact sound ID to match when broad-match is off. Example: minecraft:entity.mob_spawner.ambient")).defaultValue((Object)"minecraft:entity.mob_spawner.ambient")).visible(() -> (Boolean)this.broadMatch.get() == false)).build());
        this.maxRange = this.sgFilter.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("max-range-blocks")).description("Only record sounds within this many blocks of you (server sends up to 64).")).defaultValue(80.0).min(8.0).sliderMax(128.0).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render detected spawner positions.")).defaultValue((Object)true)).build());
        this.fillUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-unconfirmed")).description("Box fill color for unconfirmed hits.")).defaultValue(new SettingColor(180, 0, 255, 30)).visible(() -> this.renderEnabled.get())).build());
        this.lineUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-unconfirmed")).description("Box outline for unconfirmed hits.")).defaultValue(new SettingColor(180, 0, 255, 160)).visible(() -> this.renderEnabled.get())).build());
        this.fillConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-confirmed")).description("Box fill color for confirmed spawners.")).defaultValue(new SettingColor(255, 60, 60, 50)).visible(() -> this.renderEnabled.get())).build());
        this.lineConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-confirmed")).description("Box outline for confirmed spawners.")).defaultValue(new SettingColor(255, 60, 60, 220)).visible(() -> this.renderEnabled.get())).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines from camera to each detection.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.tracerUnconfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-unconfirmed")).defaultValue(new SettingColor(180, 0, 255, 120)).visible(() -> this.renderEnabled.get())).build());
        this.tracerConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-confirmed")).defaultValue(new SettingColor(255, 60, 60, 180)).visible(() -> this.renderEnabled.get())).build());
        this.showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Tall pillar over confirmed spawner locations.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Pillar height in blocks.")).defaultValue((Object)160)).min(16).sliderMax(320).visible(() -> this.showPillar.get())).build());
        this.detections = Collections.synchronizedList(new ArrayList());
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.detections.clear();
        this.tickCounter = 0;
        ChatUtils.info((String)"\u00a7d[SpawnerSoundRadar] \u00a77Active \u2014 listening for spawner sounds\u2026", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.detections.clear();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        boolean matched;
        class_2596 class_25962 = event.packet;
        if (!(class_25962 instanceof class_2767)) {
            return;
        }
        class_2767 pkt = (class_2767)class_25962;
        if (this.mc.field_1724 == null) {
            return;
        }
        class_3414 sound = (class_3414)pkt.method_11894().comp_349();
        String key = sound.method_14833().toString();
        boolean bl = matched = (Boolean)this.broadMatch.get() != false ? key.contains("spawner") : key.equals(((String)this.exactId.get()).trim());
        if (!matched) {
            return;
        }
        double sx = pkt.method_11890();
        double sy = pkt.method_11889();
        double sz = pkt.method_11893();
        class_243 soundPos = new class_243(sx, sy, sz);
        class_243 playerPos = this.mc.field_1724.method_19538();
        double dist = playerPos.method_1022(soundPos);
        if (dist > (Double)this.maxRange.get()) {
            return;
        }
        if (((Boolean)this.debugAll.get()).booleanValue()) {
            ChatUtils.info((String)("[SSR] \u00a77Sound \u00a7f" + key + " \u00a77@ " + SpawnerSoundRadar.fmt(sx) + "," + SpawnerSoundRadar.fmt(sy) + "," + SpawnerSoundRadar.fmt(sz) + " dist=" + SpawnerSoundRadar.fmt(dist)), (Object[])new Object[0]);
        }
        double radius = (Double)this.clusterRadius.get();
        List<Detection> list = this.detections;
        synchronized (list) {
            Detection nearest = null;
            double minDist = Double.MAX_VALUE;
            for (Detection d : this.detections) {
                double dd = d.pos.method_1022(soundPos);
                if (!(dd < radius) || !(dd < minDist)) continue;
                minDist = dd;
                nearest = d;
            }
            if (nearest != null) {
                boolean wasConfirmed = nearest.confirmed;
                nearest.addHit();
                boolean bl2 = nearest.confirmed = nearest.hits >= (Integer)this.confirmHits.get();
                if (!wasConfirmed && nearest.confirmed && ((Boolean)this.chatNotify.get()).booleanValue()) {
                    ChatUtils.info((String)("[SSR] \u00a7c\u00a7lSPAWNER CONFIRMED\u00a7r at \u00a7f" + SpawnerSoundRadar.fmt(nearest.pos.field_1352) + ", " + SpawnerSoundRadar.fmt(nearest.pos.field_1351) + ", " + SpawnerSoundRadar.fmt(nearest.pos.field_1350) + " \u00a77(hits=" + nearest.hits + ")"), (Object[])new Object[0]);
                }
            } else {
                this.detections.add(new Detection(soundPos));
                if (((Boolean)this.debugAll.get()).booleanValue()) {
                    ChatUtils.info((String)("[SSR] \u00a7dNew detection\u00a7r at \u00a7f" + SpawnerSoundRadar.fmt(sx) + "," + SpawnerSoundRadar.fmt(sy) + "," + SpawnerSoundRadar.fmt(sz)), (Object[])new Object[0]);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++this.tickCounter % 100 == 0) {
            long cutoff = System.currentTimeMillis() - (long)((Integer)this.decaySeconds.get()).intValue() * 1000L;
            List<Detection> list = this.detections;
            synchronized (list) {
                this.detections.removeIf(d -> d.lastMs < cutoff);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue() || this.mc.field_1724 == null) {
            return;
        }
        class_243 eye = this.mc.field_1724.method_5836(event.tickDelta);
        List<Detection> list = this.detections;
        synchronized (list) {
            for (Detection d : this.detections) {
                SettingColor fill = d.confirmed ? (SettingColor)this.fillConfirmed.get() : (SettingColor)this.fillUnconfirmed.get();
                SettingColor line = d.confirmed ? (SettingColor)this.lineConfirmed.get() : (SettingColor)this.lineUnconfirmed.get();
                SettingColor tc = d.confirmed ? (SettingColor)this.tracerConfirmed.get() : (SettingColor)this.tracerUnconfirmed.get();
                double x0 = Math.floor(d.pos.field_1352);
                double y0 = Math.floor(d.pos.field_1351);
                double z0 = Math.floor(d.pos.field_1350);
                event.renderer.box(x0, y0, z0, x0 + 1.0, y0 + 1.0, z0 + 1.0, (Color)fill, (Color)line, ShapeMode.Both, 0);
                if (d.confirmed && ((Boolean)this.showPillar.get()).booleanValue()) {
                    int h = (Integer)this.pillarHeight.get();
                    event.renderer.box(x0, y0 + 1.0, z0, x0 + 1.0, y0 + (double)h, z0 + 1.0, (Color)fill, (Color)line, ShapeMode.Both, 0);
                }
                if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, x0 + 0.5, y0 + 0.5, z0 + 0.5, (Color)tc);
            }
        }
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static class Detection {
        final class_243 pos;
        int hits;
        long firstMs;
        long lastMs;
        boolean confirmed;

        Detection(class_243 pos) {
            this.pos = pos;
            this.hits = 1;
            this.lastMs = this.firstMs = System.currentTimeMillis();
            this.confirmed = false;
        }

        void addHit() {
            ++this.hits;
            this.lastMs = System.currentTimeMillis();
        }
    }
}
