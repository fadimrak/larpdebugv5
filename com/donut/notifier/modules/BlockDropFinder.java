package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;

public class BlockDropFinder
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private static final Set<class_2248> SPAWNER_BLOCKS = ConcurrentHashMap.newKeySet();
    private final SettingGroup sgTickDebug;
    private final SettingGroup sgDetector;
    private final SettingGroup sgRender;
    private final SettingGroup sgPrediction;
    private final Setting<Boolean> tickDebugMode;
    private final Setting<Integer> tickDebugY;
    private final Setting<Integer> heightPerTick;
    private final Setting<Integer> tickMinHeight;
    private final Setting<Integer> tickMaxHeight;
    private final Setting<Integer> tickFadeSeconds;
    private final Setting<Double> tickPillarWidth;
    private final Setting<Integer> scanRadius;
    private final Setting<Double> activityThreshold;
    private final Setting<Boolean> chatDebug;
    private final Setting<Boolean> hideWeak;
    private final Setting<Integer> pillarBaseY;
    private final Setting<Double> detectorWidth;
    private final Setting<Integer> detectorHeight;
    private final Setting<Boolean> showTracers;
    private final Setting<Boolean> showPrediction;
    private final Setting<Integer> minConfirmed;
    private static final Color C_TICK_FILL = new Color(0, 255, 50, 55);
    private static final Color C_TICK_LINE = new Color(30, 255, 80, 235);
    private static final Color C_TICK_TRACE = new Color(30, 255, 80, 200);
    private static final Color C_WEAK_FILL = new Color(0, 180, 60, 30);
    private static final Color C_WEAK_LINE = new Color(0, 200, 70, 150);
    private static final Color C_WEAK_TRACE = new Color(0, 200, 70, 130);
    private static final Color C_CONF_FILL = new Color(155, 20, 255, 90);
    private static final Color C_CONF_LINE = new Color(175, 45, 255, 255);
    private static final Color C_CONF_TRACE = new Color(175, 45, 255, 220);
    private static final Color C_PRED_FILL = new Color(255, 200, 0, 70);
    private static final Color C_PRED_LINE = new Color(255, 220, 20, 255);
    private static final Color C_PRED_TRACE = new Color(255, 215, 10, 215);
    private final ConcurrentHashMap<class_1923, Integer> tickCounts;
    private final ConcurrentHashMap<class_1923, Long> tickLastSeen;
    private final ConcurrentHashMap<class_1923, ConcurrentLinkedDeque<Long>> updateTimestamps;
    private final ConcurrentHashMap<class_1923, Integer> firstBeCount;
    private final ConcurrentHashMap<class_1923, Set<SpawnerFlag>> detectedChunks;
    private static final long WINDOW_MS = 30000L;
    private static final int SCAN_TICKS = 60;
    private int tickCounter;

    public BlockDropFinder() {
        super(DonutAddon.CATEGORY, "block-drop-finder", "DonutSMP custom spawner detector. Finds Spawners & Hoppers beneath Y=0. Purple = Confirmed Spawner.");
        this.sgTickDebug = this.settings.createGroup("Tick Debug");
        this.sgDetector = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.sgPrediction = this.settings.createGroup("Prediction");
        this.tickDebugMode = this.sgTickDebug.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("tick-debug-mode")).description("Draw a green pillar for every block tick received. Great for finding hopper activity under spawners.")).defaultValue((Object)true)).build());
        this.tickDebugY = this.sgTickDebug.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("tick-y-level")).description("Only count block ticks below this Y.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(0).build());
        this.heightPerTick = this.sgTickDebug.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("height-per-tick")).description("Blocks of height added per recorded tick.")).defaultValue((Object)4)).min(1).sliderMax(20).build());
        this.tickMinHeight = this.sgTickDebug.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-height")).description("Starting pillar height on first tick.")).defaultValue((Object)40)).min(5).sliderMax(100).build());
        this.tickMaxHeight = this.sgTickDebug.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("max-height")).description("Maximum pillar height (caps tick growth).")).defaultValue((Object)250)).min(50).sliderMax(400).build());
        this.tickFadeSeconds = this.sgTickDebug.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("tick-fade")).description("Seconds with no new ticks before pillar disappears. 0 = never.")).defaultValue((Object)60)).min(0).sliderMax(300).build());
        this.tickPillarWidth = this.sgTickDebug.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("tick-width")).description("Width of tick-debug pillars in blocks.")).defaultValue(2.5).min(0.5).sliderMax(6.0).build());
        this.scanRadius = this.sgDetector.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius")).description("Chunk radius for active palette scan and resyncs.")).defaultValue((Object)10)).min(1).sliderMax(16).build());
        this.activityThreshold = this.sgDetector.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("activity-threshold")).description("Std-dev threshold for statistical block update anomaly (Hopper activity).")).defaultValue(2.5).min(1.0).sliderMax(6.0).build());
        this.chatDebug = this.sgDetector.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-debug")).description("Print to chat when a chunk is flagged.")).defaultValue((Object)false)).build());
        this.hideWeak = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("hide-weak")).description("Only show PURPLE confirmed spawner pillars. Hides weak green background activity.")).defaultValue((Object)true)).build());
        this.pillarBaseY = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-base-y")).description("Y level all pillars start from.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(100).build());
        this.detectorWidth = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("detector-width")).description("Width of precision detector pillars.")).defaultValue(4.0).min(1.0).sliderMax(10.0).build());
        this.detectorHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("detector-height")).description("Fixed height of precision detector pillars.")).defaultValue((Object)200)).min(20).sliderMax(400).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to pillars.")).defaultValue((Object)true)).build());
        this.showPrediction = this.sgPrediction.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-prediction")).description("Gold pillar at weighted centroid of all confirmed spawner chunks.")).defaultValue((Object)true)).build());
        this.minConfirmed = this.sgPrediction.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-confirmed")).description("Minimum purple chunks before gold prediction pillar appears.")).defaultValue((Object)1)).min(1).sliderMax(10).build());
        this.tickCounts = new ConcurrentHashMap();
        this.tickLastSeen = new ConcurrentHashMap();
        this.updateTimestamps = new ConcurrentHashMap();
        this.firstBeCount = new ConcurrentHashMap();
        this.detectedChunks = new ConcurrentHashMap();
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.clearAll();
        ChatUtils.info((String)"\u00a7a[Block Drop Finder] \u00a77Debug: Made by \u00a7bMilo \u00a77And \u00a7dFlickz", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.clearAll();
    }

    private void clearAll() {
        this.tickCounts.clear();
        this.tickLastSeen.clear();
        this.updateTimestamps.clear();
        this.firstBeCount.clear();
        this.detectedChunks.clear();
        this.tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (BlockDropFinder.MC.field_1687 != null && BlockDropFinder.MC.field_1724 != null && ++this.tickCounter >= 60) {
            this.tickCounter = 0;
            int pCx = BlockDropFinder.MC.field_1724.method_31476().field_9181;
            int pCz = BlockDropFinder.MC.field_1724.method_31476().field_9180;
            int rad = (Integer)this.scanRadius.get();
            int bottomY = BlockDropFinder.MC.field_1687.method_31607();
            for (int dx = -rad; dx <= rad; ++dx) {
                block1: for (int dz = -rad; dz <= rad; ++dz) {
                    class_2818 chunk = BlockDropFinder.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                    if (chunk == null) continue;
                    class_1923 cp = chunk.method_12004();
                    class_2826[] sec = chunk.method_12006();
                    for (int i = 0; i < sec.length; ++i) {
                        boolean found;
                        int secBaseY;
                        if (sec[i] == null || sec[i].method_38292() || (secBaseY = bottomY + i * 16) + 16 > 0 || !(found = sec[i].method_12265().method_19526(s -> SPAWNER_BLOCKS.contains(s.method_26204())))) continue;
                        boolean fresh = this.addFlag(cp, SpawnerFlag.PALETTE_SPAWNER);
                        if (!fresh || !((Boolean)this.chatDebug.get()).booleanValue()) continue block1;
                        ChatUtils.info((String)("[BDF] \u00a75Spawner/Hopper block\u00a7r in palette chunk " + cp.field_9181 + "," + cp.field_9180 + " Y~" + secBaseY), (Object[])new Object[0]);
                        continue block1;
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (BlockDropFinder.MC.field_1687 != null && BlockDropFinder.MC.field_1724 != null) {
            class_2596 class_25962 = event.packet;
            if (class_25962 instanceof class_2626) {
                boolean fresh;
                class_2626 pkt = (class_2626)class_25962;
                pos = pkt.method_11309();
                class_1923 cp = new class_1923((class_2338)pos);
                if (((Boolean)this.tickDebugMode.get()).booleanValue() && pos.method_10264() < (Integer)this.tickDebugY.get()) {
                    this.recordTick(cp);
                }
                this.recordBlockUpdate(cp);
                if (SPAWNER_BLOCKS.contains(pkt.method_11308().method_26204()) && pos.method_10264() < 0 && (fresh = this.addFlag(cp, SpawnerFlag.DIRECT_STATE))) {
                    ChatUtils.info((String)("[BDF] \u00a7c\u00a7lSPAWNER/HOPPER STATE\u00a7r detected! Chunk " + cp.field_9181 + "," + cp.field_9180 + " Y=" + pos.method_10264()), (Object[])new Object[0]);
                }
            } else {
                pos = event.packet;
                if (pos instanceof class_2637) {
                    class_2637 pkt = (class_2637)pos;
                    boolean[] tdDone = new boolean[]{false};
                    class_1923[] cpRef = new class_1923[]{null};
                    pkt.method_30621((bp, bs) -> {
                        class_1923 cpx;
                        boolean freshx;
                        if (cpRef[0] == null) {
                            cpRef[0] = new class_1923(bp);
                        }
                        if (((Boolean)this.tickDebugMode.get()).booleanValue() && !tdDone[0] && bp.method_10264() < (Integer)this.tickDebugY.get()) {
                            tdDone[0] = true;
                        }
                        if (SPAWNER_BLOCKS.contains(bs.method_26204()) && bp.method_10264() < 0 && (freshx = this.addFlag(cpx = new class_1923(bp), SpawnerFlag.DIRECT_STATE))) {
                            ChatUtils.info((String)("[BDF] \u00a7c\u00a7lSPAWNER/HOPPER DELTA\u00a7r detected! Chunk " + cpx.field_9181 + "," + cpx.field_9180 + " Y=" + bp.method_10264()), (Object[])new Object[0]);
                        }
                    });
                    if (cpRef[0] != null) {
                        if (tdDone[0]) {
                            this.recordTick(cpRef[0]);
                        }
                        this.recordBlockUpdate(cpRef[0]);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        int incoming;
        Integer prev;
        class_2818 chunk;
        class_1923 cp;
        if (BlockDropFinder.MC.field_1687 != null && BlockDropFinder.MC.field_1724 != null && this.isInScanRadius(cp = (chunk = event.chunk()).method_12004()) && (prev = this.firstBeCount.putIfAbsent(cp, incoming = chunk.method_12214().size())) != null) {
            boolean fresh;
            this.firstBeCount.put(cp, incoming);
            if (incoming >= prev + 2 && (fresh = this.addFlag(cp, SpawnerFlag.RESYNC)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                ChatUtils.info((String)("[BDF] \u00a79BE Resync\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " BE: " + prev + "\u2192" + incoming), (Object[])new Object[0]);
            }
        }
    }

    private void recordTick(class_1923 cp) {
        this.tickCounts.merge(cp, 1, Integer::sum);
        this.tickLastSeen.put(cp, System.currentTimeMillis());
    }

    private void recordBlockUpdate(class_1923 cp) {
        if (this.isInScanRadius(cp)) {
            Long h;
            long now = System.currentTimeMillis();
            ConcurrentLinkedDeque times = this.updateTimestamps.computeIfAbsent(cp, k -> new ConcurrentLinkedDeque());
            times.addLast(now);
            while ((h = (Long)times.peekFirst()) != null && now - h > 30000L) {
                times.pollFirst();
            }
            this.evaluateActivityAnomaly(cp);
        }
    }

    private void evaluateActivityAnomaly(class_1923 target) {
        if (BlockDropFinder.MC.field_1724 != null) {
            Object cp;
            long now = System.currentTimeMillis();
            int pCx = BlockDropFinder.MC.field_1724.method_31476().field_9181;
            int pCz = BlockDropFinder.MC.field_1724.method_31476().field_9180;
            int rad = (Integer)this.scanRadius.get();
            ArrayList<Double> counts = new ArrayList<Double>();
            for (Map.Entry<class_1923, ConcurrentLinkedDeque<Long>> e : this.updateTimestamps.entrySet()) {
                Long hh;
                cp = e.getKey();
                if (Math.abs(((class_1923)cp).field_9181 - pCx) > rad || Math.abs(((class_1923)cp).field_9180 - pCz) > rad) continue;
                ConcurrentLinkedDeque<Long> t = e.getValue();
                while ((hh = t.peekFirst()) != null && now - hh > 30000L) {
                    t.pollFirst();
                }
                counts.add(Double.valueOf(t.size()));
            }
            if (counts.size() >= 5) {
                boolean fresh;
                ConcurrentLinkedDeque<Long> tt;
                double sum = 0.0;
                cp = counts.iterator();
                while (cp.hasNext()) {
                    double c = (Double)cp.next();
                    sum += c;
                }
                double mean = sum / (double)counts.size();
                double varSum = 0.0;
                Iterator iterator = counts.iterator();
                while (iterator.hasNext()) {
                    double c = (Double)iterator.next();
                    varSum += (c - mean) * (c - mean);
                }
                double stdDev = Math.sqrt(varSum / (double)counts.size());
                if (!(stdDev < 1.0) && (tt = this.updateTimestamps.get(target)) != null && (double)tt.size() >= mean + (Double)this.activityThreshold.get() * stdDev && (fresh = this.addFlag(target, SpawnerFlag.ACTIVITY)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                    ChatUtils.info((String)("[BDF] \u00a7aHigh Hopper Activity\u00a7r chunk " + target.field_9181 + "," + target.field_9180), (Object[])new Object[0]);
                }
            }
        }
    }

    private double[] computePrediction() {
        double[] dArray;
        int confirmed = 0;
        double wX = 0.0;
        double wZ = 0.0;
        double totalW = 0.0;
        for (Map.Entry<class_1923, Set<SpawnerFlag>> e : this.detectedChunks.entrySet()) {
            if (e.getValue().size() < 2) continue;
            ++confirmed;
            double w = e.getValue().size();
            wX += ((double)e.getKey().method_8326() + 8.0) * w;
            wZ += ((double)e.getKey().method_8328() + 8.0) * w;
            totalW += w;
        }
        if (confirmed >= (Integer)this.minConfirmed.get() && totalW != 0.0) {
            double[] dArray2 = new double[2];
            dArray2[0] = wX / totalW;
            dArray = dArray2;
            dArray2[1] = wZ / totalW;
        } else {
            dArray = null;
        }
        return dArray;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (BlockDropFinder.MC.field_1724 != null && BlockDropFinder.MC.field_1687 != null) {
            double[] pred;
            double hw;
            class_243 eye = BlockDropFinder.MC.field_1724.method_5836(event.tickDelta);
            int baseY = (Integer)this.pillarBaseY.get();
            long now = System.currentTimeMillis();
            int fadeS = (Integer)this.tickFadeSeconds.get();
            if (((Boolean)this.tickDebugMode.get()).booleanValue()) {
                hw = (Double)this.tickPillarWidth.get() / 2.0;
                for (Map.Entry<class_1923, Integer> e : this.tickCounts.entrySet()) {
                    Long last;
                    class_1923 cp = e.getKey();
                    int ticks = e.getValue();
                    if (fadeS > 0 && ((last = this.tickLastSeen.get(cp)) == null || now - last > (long)fadeS * 1000L)) continue;
                    int topY = baseY + Math.min((Integer)this.tickMinHeight.get() + ticks * (Integer)this.heightPerTick.get(), (Integer)this.tickMaxHeight.get());
                    double cx = (double)cp.method_8326() + 8.0;
                    double cz = (double)cp.method_8328() + 8.0;
                    event.renderer.box(cx - hw, (double)baseY, cz - hw, cx + hw, (double)topY, cz + hw, C_TICK_FILL, C_TICK_LINE, ShapeMode.Both, 0);
                    if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, (double)baseY + (double)(topY - baseY) * 0.5, cz, C_TICK_TRACE);
                }
            }
            if (!this.detectedChunks.isEmpty()) {
                hw = (Double)this.detectorWidth.get() / 2.0;
                int topY = baseY + (Integer)this.detectorHeight.get();
                boolean skipWeak = (Boolean)this.hideWeak.get();
                for (Map.Entry<class_1923, Set<SpawnerFlag>> e : this.detectedChunks.entrySet()) {
                    boolean confirmed;
                    boolean bl = confirmed = e.getValue().size() >= 2;
                    if (skipWeak && !confirmed) continue;
                    class_1923 cpx = e.getKey();
                    Color fill = confirmed ? C_CONF_FILL : C_WEAK_FILL;
                    Color outline = confirmed ? C_CONF_LINE : C_WEAK_LINE;
                    Color tracer = confirmed ? C_CONF_TRACE : C_WEAK_TRACE;
                    double cx = (double)cpx.method_8326() + 8.0;
                    double cz = (double)cpx.method_8328() + 8.0;
                    event.renderer.box(cx - hw, (double)baseY, cz - hw, cx + hw, (double)topY, cz + hw, fill, outline, ShapeMode.Both, 0);
                    if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, (double)baseY + (double)(topY - baseY) * 0.5, cz, tracer);
                }
            }
            if (((Boolean)this.showPrediction.get()).booleanValue() && (pred = this.computePrediction()) != null) {
                double phw = (Double)this.detectorWidth.get();
                int ptop = baseY + (Integer)this.detectorHeight.get() + 60;
                event.renderer.box(pred[0] - phw, (double)baseY, pred[1] - phw, pred[0] + phw, (double)ptop, pred[1] + phw, C_PRED_FILL, C_PRED_LINE, ShapeMode.Both, 0);
                if (((Boolean)this.showTracers.get()).booleanValue()) {
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, pred[0], (double)baseY + (double)(ptop - baseY) * 0.5, pred[1], C_PRED_TRACE);
                }
            }
        }
    }

    private boolean addFlag(class_1923 cp, SpawnerFlag flag) {
        return this.detectedChunks.computeIfAbsent(cp, k -> ConcurrentHashMap.newKeySet()).add(flag);
    }

    private boolean isInScanRadius(class_1923 cp) {
        return BlockDropFinder.MC.field_1724 == null ? false : Math.abs(cp.field_9181 - BlockDropFinder.MC.field_1724.method_31476().field_9181) <= (Integer)this.scanRadius.get() && Math.abs(cp.field_9180 - BlockDropFinder.MC.field_1724.method_31476().field_9180) <= (Integer)this.scanRadius.get();
    }

    static {
        SPAWNER_BLOCKS.addAll(Arrays.asList(class_2246.field_10260, class_2246.field_10312));
    }

    private static enum SpawnerFlag {
        ACTIVITY,
        RESYNC,
        PALETTE_SPAWNER,
        DIRECT_STATE,
        BE_UPDATE;

    }
}
