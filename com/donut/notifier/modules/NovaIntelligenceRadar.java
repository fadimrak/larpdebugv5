package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.class_1311;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2374;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2604;
import net.minecraft.class_2672;
import net.minecraft.class_2708;
import net.minecraft.class_2767;

public class NovaIntelligenceRadar
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Integer> confirmThreshold;
    private final Setting<Integer> soundBelowY;
    private final Setting<Double> soundClusterRadius;
    private final Setting<Integer> soundConfirmSecs;
    private final Setting<Double> entitySoundLinkRadius;
    private final Setting<Integer> decaySeconds;
    private final Setting<Boolean> chatNotify;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> fillCandidate;
    private final Setting<SettingColor> lineCandidate;
    private final Setting<SettingColor> fillConfirmed;
    private final Setting<SettingColor> lineConfirmed;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerColor;
    private final Setting<Boolean> showPillar;
    private final Setting<Integer> pillarHeight;
    private final ConcurrentHashMap<class_1923, ChunkRecord> records;
    private final List<long[]> globalSoundHits;
    private final ConcurrentHashMap<class_1923, Long> chunkSeen;
    private int tickCounter;

    public NovaIntelligenceRadar() {
        super(DonutAddon.CATEGORY, "nova-intelligence-radar", "Multi-vector confidence scoring: sound + entity + rubberband + chunk anomaly.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.confirmThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-threshold")).description("Score required (plus \u22652 different vectors) to mark a chunk CONFIRMED.")).defaultValue((Object)5)).range(2, 40).sliderMax(20).build());
        this.soundBelowY = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-below-y")).description("Only score sounds below this Y level.")).defaultValue((Object)0)).range(-64, 64).sliderRange(-64, 64).build());
        this.soundClusterRadius = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("sound-cluster-radius")).description("Blocks radius to cluster repeated sounds.")).defaultValue(8.0).min(1.0).sliderMax(16.0).build());
        this.soundConfirmSecs = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-confirm-window-secs")).description("Window (seconds) in which 3 sound hits from same cluster earn a bonus.")).defaultValue((Object)30)).range(5, 60).sliderMax(30).build());
        this.entitySoundLinkRadius = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("entity-sound-link-radius")).description("Blocks: entity spawn within this of a sound hit earns bonus score.")).defaultValue(16.0).min(2.0).sliderMax(24.0).build());
        this.decaySeconds = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("decay-seconds")).description("Idle seconds before a chunk record is dropped.")).defaultValue((Object)600)).range(60, 1800).sliderMax(600).build());
        this.chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print promotions to chat.")).defaultValue((Object)true)).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render chunk plates.")).defaultValue((Object)true)).build());
        this.fillCandidate = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-candidate")).defaultValue(new SettingColor(255, 140, 0, 30)).visible(() -> this.renderEnabled.get())).build());
        this.lineCandidate = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-candidate")).defaultValue(new SettingColor(255, 140, 0, 160)).visible(() -> this.renderEnabled.get())).build());
        this.fillConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-confirmed")).defaultValue(new SettingColor(50, 255, 80, 50)).visible(() -> this.renderEnabled.get())).build());
        this.lineConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-confirmed")).defaultValue(new SettingColor(50, 255, 80, 220)).visible(() -> this.renderEnabled.get())).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).defaultValue(new SettingColor(80, 255, 80, 160)).visible(() -> this.renderEnabled.get())).build());
        this.showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).defaultValue((Object)160)).min(16).sliderMax(256).visible(() -> this.showPillar.get())).build());
        this.records = new ConcurrentHashMap();
        this.globalSoundHits = Collections.synchronizedList(new ArrayList());
        this.chunkSeen = new ConcurrentHashMap();
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.records.clear();
        this.globalSoundHits.clear();
        this.chunkSeen.clear();
        this.tickCounter = 0;
    }

    public void onDeactivate() {
        this.records.clear();
        this.globalSoundHits.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++this.tickCounter % 100 == 0) {
            long cutoff = System.currentTimeMillis() - (long)((Integer)this.decaySeconds.get()).intValue() * 1000L;
            this.records.entrySet().removeIf(e -> ((ChunkRecord)e.getValue()).lastMs < cutoff);
            this.globalSoundHits.removeIf(h -> h[2] < cutoff);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2767) {
            class_2767 pkt = (class_2767)class_25962;
            if (pkt.method_11889() < (double)((Integer)this.soundBelowY.get()).intValue()) {
                soundPos = new class_243(pkt.method_11890(), pkt.method_11889(), pkt.method_11893());
                class_1923 cp = new class_1923(class_2338.method_49638((class_2374)soundPos));
                ChunkRecord r = this.getOrCreate(cp);
                r.addScore(1, "SOUND");
                long now = System.currentTimeMillis();
                this.globalSoundHits.add(new long[]{Double.doubleToLongBits(pkt.method_11890()), Double.doubleToLongBits(pkt.method_11893()), now});
                int clusterHits = this.countRecentSoundCluster((class_243)soundPos, now);
                if (clusterHits >= 3) {
                    r.addScore(3, "SOUND_CLUSTER");
                    if (((Boolean)this.chatNotify.get()).booleanValue() && !r.isConfirmed((Integer)this.confirmThreshold.get())) {
                        ChatUtils.info((String)("[Nova] \u00a7eSound cluster\u00a7r chunk \u00a7f" + cp.field_9181 + "," + cp.field_9180 + " \u00a77hits=" + clusterHits), (Object[])new Object[0]);
                    }
                }
                this.maybeConfirm(r);
            }
        } else {
            soundPos = event.packet;
            if (soundPos instanceof class_2604) {
                class_2604 pkt = (class_2604)soundPos;
                if (pkt.method_11174() < 0.0 && pkt.method_11169().method_5891() == class_1311.field_6302) {
                    class_243 pos = new class_243(pkt.method_11175(), pkt.method_11174(), pkt.method_11176());
                    class_1923 cp = new class_1923(class_2338.method_49638((class_2374)pos));
                    ChunkRecord r = this.getOrCreate(cp);
                    r.addScore(2, "ENTITY");
                    if (this.isNearRecentSound(pos)) {
                        r.addScore(3, "ENTITY_SOUND_LINK");
                        if (((Boolean)this.chatNotify.get()).booleanValue()) {
                            ChatUtils.info((String)("[Nova] \u00a7dEntity+Sound link\u00a7r chunk \u00a7f" + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
                        }
                    }
                    this.maybeConfirm(r);
                }
            } else if (event.packet instanceof class_2708) {
                if (this.mc.field_1724.method_23318() < 0.0) {
                    cp = this.mc.field_1724.method_31476();
                    ChunkRecord r = this.getOrCreate((class_1923)cp);
                    r.addScore(3, "RUBBERBAND");
                    this.maybeConfirm(r);
                }
            } else {
                class_2672 pkt;
                Long prev;
                cp = event.packet;
                if (cp instanceof class_2672 && (prev = this.chunkSeen.put((class_1923)(cp = new class_1923((pkt = (class_2672)cp).method_11523(), pkt.method_11524())), System.currentTimeMillis())) != null && System.currentTimeMillis() - prev < 30000L) {
                    ChunkRecord r = this.getOrCreate((class_1923)cp);
                    r.addScore(2, "CHUNK_ANOMALY");
                    this.maybeConfirm(r);
                }
            }
        }
    }

    private ChunkRecord getOrCreate(class_1923 cp) {
        return this.records.computeIfAbsent(cp, ChunkRecord::new);
    }

    private void maybeConfirm(ChunkRecord r) {
        if (r.isConfirmed((Integer)this.confirmThreshold.get()) && ((Boolean)this.chatNotify.get()).booleanValue() && r.score - r.vectors.size() < (Integer)this.confirmThreshold.get()) {
            ChatUtils.info((String)("[Nova] \u00a7a\u00a7lCONFIRMED\u00a7r chunk \u00a7f" + r.cp.field_9181 + "," + r.cp.field_9180 + " \u00a77score=" + r.score + " vectors=" + String.valueOf(r.vectors)), (Object[])new Object[0]);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private int countRecentSoundCluster(class_243 pos, long now) {
        long windowMs = (long)((Integer)this.soundConfirmSecs.get()).intValue() * 1000L;
        double radius = (Double)this.soundClusterRadius.get();
        int count = 0;
        List<long[]> list = this.globalSoundHits;
        synchronized (list) {
            for (long[] h : this.globalSoundHits) {
                double hz;
                double hx;
                double dist;
                if (now - h[2] > windowMs || !((dist = Math.sqrt(((hx = Double.longBitsToDouble(h[0])) - pos.field_1352) * (hx - pos.field_1352) + ((hz = Double.longBitsToDouble(h[1])) - pos.field_1350) * (hz - pos.field_1350))) <= radius)) continue;
                ++count;
            }
        }
        return count;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean isNearRecentSound(class_243 pos) {
        long cutoff = System.currentTimeMillis() - 60000L;
        double radius = (Double)this.entitySoundLinkRadius.get();
        List<long[]> list = this.globalSoundHits;
        synchronized (list) {
            for (long[] h : this.globalSoundHits) {
                double hz;
                double hx;
                double dist;
                if (h[2] < cutoff || !((dist = Math.sqrt(((hx = Double.longBitsToDouble(h[0])) - pos.field_1352) * (hx - pos.field_1352) + ((hz = Double.longBitsToDouble(h[1])) - pos.field_1350) * (hz - pos.field_1350))) <= radius)) continue;
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue() || this.mc.field_1724 == null) {
            return;
        }
        class_243 eye = this.mc.field_1724.method_5836(event.tickDelta);
        int thresh = (Integer)this.confirmThreshold.get();
        for (ChunkRecord r : this.records.values()) {
            boolean confirmed = r.isConfirmed(thresh);
            SettingColor fill = confirmed ? (SettingColor)this.fillConfirmed.get() : (SettingColor)this.fillCandidate.get();
            SettingColor line = confirmed ? (SettingColor)this.lineConfirmed.get() : (SettingColor)this.lineCandidate.get();
            double x0 = r.cp.method_8326();
            double z0 = r.cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            double cx = r.cp.method_33940();
            double cz = r.cp.method_33942();
            double plateY = (double)r.surfaceY + 0.05;
            event.renderer.box(x0, plateY, z0, x1, plateY + 0.2, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            if (confirmed && ((Boolean)this.showPillar.get()).booleanValue()) {
                int h = (Integer)this.pillarHeight.get();
                event.renderer.box(x0, plateY + 0.2, z0, x1, plateY + (double)h, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
            }
            if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
            event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, (Color)this.tracerColor.get());
        }
    }

    private static class ChunkRecord {
        final class_1923 cp;
        int score;
        final Set<String> vectors = new HashSet<String>();
        long firstMs;
        long lastMs;
        int surfaceY = 64;
        final List<long[]> soundHits = new ArrayList<long[]>();

        ChunkRecord(class_1923 cp) {
            this.cp = cp;
            this.lastMs = this.firstMs = System.currentTimeMillis();
        }

        void addScore(int delta, String vector) {
            this.score += delta;
            this.lastMs = System.currentTimeMillis();
            this.vectors.add(vector);
        }

        boolean isConfirmed(int threshold) {
            return this.score >= threshold && this.vectors.size() >= 2;
        }
    }
}
