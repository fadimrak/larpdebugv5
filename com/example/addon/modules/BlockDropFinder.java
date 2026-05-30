package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
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
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2680;
import net.minecraft.class_2767;
import net.minecraft.class_2818;
import net.minecraft.class_310;
import net.minecraft.class_3419;

public class BlockDropFinder
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Integer> scanRadius;
    private final Setting<Double> activityThreshold;
    private final Setting<Integer> deepYThreshold;
    private final Setting<Integer> soundYThreshold;
    private final Setting<Integer> soundCountFlag;
    private final Setting<Boolean> chatDebug;
    private final Setting<Boolean> showTracers;
    private final Setting<Integer> plateY;
    private static final Color C_ACT_FILL = new Color(0, 210, 60, 60);
    private static final Color C_ACT_LINE = new Color(0, 230, 70, 210);
    private static final Color C_ACT_TRACE = new Color(0, 230, 70, 220);
    private static final Color C_RES_FILL = new Color(50, 120, 255, 60);
    private static final Color C_RES_LINE = new Color(80, 150, 255, 210);
    private static final Color C_RES_TRACE = new Color(80, 150, 255, 220);
    private static final Color C_SND_FILL = new Color(200, 40, 220, 60);
    private static final Color C_SND_LINE = new Color(210, 60, 240, 210);
    private static final Color C_SND_TRACE = new Color(210, 60, 240, 220);
    private static final Color C_BOTH_FILL = new Color(255, 155, 20, 80);
    private static final Color C_BOTH_LINE = new Color(255, 175, 30, 220);
    private static final Color C_BOTH_TRACE = new Color(255, 175, 30, 230);
    private final ConcurrentHashMap<class_1923, ConcurrentLinkedDeque<Long>> updateTimestamps;
    private final ConcurrentHashMap<class_1923, Integer> firstBeCount;
    private final ConcurrentHashMap<class_1923, Integer> soundCounts;
    private final ConcurrentHashMap<class_1923, Set<DetectionFlag>> detectedChunks;
    private static final long WINDOW_MS = 30000L;

    public BlockDropFinder() {
        super(AddonTemplate.CATEGORY, "block-drop-finder", "Detects hidden underground bases via update anomalies, resync BE-count changes and spawner-tick sounds.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.scanRadius = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius")).description("Chunk radius around the player considered for anomaly statistics.")).defaultValue((Object)8)).min(1).sliderMax(16).build());
        this.activityThreshold = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("activity-threshold")).description("Standard deviations above mean update rate to flag a chunk (Method 1).")).defaultValue(2.0).min(0.5).sliderMax(5.0).build());
        this.deepYThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("deep-y")).description("Any block update below this Y is immediately flagged. Default -3.")).defaultValue((Object)-3)).min(-64).sliderMin(-64).sliderMax(0).build());
        this.soundYThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-y-threshold")).description("Hostile/neutral sounds below this Y are counted as spawner ticks. Default 0.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(32).build());
        this.soundCountFlag = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-count-flag")).description("Number of underground hostile/neutral sounds in a chunk before it is flagged.")).defaultValue((Object)5)).min(1).sliderMax(20).build());
        this.chatDebug = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-debug")).description("Print debug info to chat whenever a chunk is newly flagged.")).defaultValue((Object)false)).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines from your eye to flagged chunk centers.")).defaultValue((Object)true)).build());
        this.plateY = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("plate-y")).description("Y level at which the flat detection plate is rendered.")).defaultValue((Object)60)).min(-64).sliderMin(-64).sliderMax(320).build());
        this.updateTimestamps = new ConcurrentHashMap();
        this.firstBeCount = new ConcurrentHashMap();
        this.soundCounts = new ConcurrentHashMap();
        this.detectedChunks = new ConcurrentHashMap();
    }

    public void onActivate() {
        this.updateTimestamps.clear();
        this.firstBeCount.clear();
        this.soundCounts.clear();
        this.detectedChunks.clear();
    }

    public void onDeactivate() {
        this.updateTimestamps.clear();
        this.firstBeCount.clear();
        this.soundCounts.clear();
        this.detectedChunks.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (BlockDropFinder.MC.field_1687 == null || BlockDropFinder.MC.field_1724 == null) {
            return;
        }
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2626) {
            boolean isNew;
            class_2626 pkt = (class_2626)class_25962;
            pos = pkt.method_11309();
            class_1923 cp = new class_1923((class_2338)pos);
            this.recordBlockUpdate(cp);
            if (pos.method_10264() < (Integer)this.deepYThreshold.get() && (isNew = this.addFlag(cp, DetectionFlag.ACTIVITY)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                ChatUtils.info((String)("[BDF] Deep update at " + pos.method_10263() + "," + pos.method_10264() + "," + pos.method_10260() + " chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
            }
        } else {
            pos = event.packet;
            if (pos instanceof class_2637) {
                class_2637 pkt = (class_2637)pos;
                done = new boolean[]{false};
                int[] minY = new int[]{Integer.MAX_VALUE};
                class_1923[] cpRef = new class_1923[]{null};
                pkt.method_30621((arg_0, arg_1) -> BlockDropFinder.lambda$onPacketReceive$0((boolean[])done, cpRef, minY, arg_0, arg_1));
                if (cpRef[0] != null) {
                    boolean isNew;
                    this.recordBlockUpdate(cpRef[0]);
                    if (minY[0] < (Integer)this.deepYThreshold.get() && (isNew = this.addFlag(cpRef[0], DetectionFlag.ACTIVITY)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                        ChatUtils.info((String)("[BDF] Deep delta update at Y=" + minY[0] + " chunk " + cpRef[0].field_9181 + "," + cpRef[0].field_9180), (Object[])new Object[0]);
                    }
                }
            } else {
                done = event.packet;
                if (done instanceof class_2767) {
                    boolean isNew;
                    class_2767 pkt = (class_2767)done;
                    class_3419 cat = pkt.method_11888();
                    if (cat != class_3419.field_15251 && cat != class_3419.field_15254) {
                        return;
                    }
                    double soundY = pkt.method_11889();
                    if (soundY >= (double)((Integer)this.soundYThreshold.get()).intValue()) {
                        return;
                    }
                    double soundX = pkt.method_11890();
                    double soundZ = pkt.method_11893();
                    class_1923 cp = new class_1923((int)Math.floor(soundX) >> 4, (int)Math.floor(soundZ) >> 4);
                    if (!this.isInScanRadius(cp)) {
                        return;
                    }
                    int newCount = this.soundCounts.merge(cp, 1, Integer::sum);
                    if (newCount >= (Integer)this.soundCountFlag.get() && (isNew = this.addFlag(cp, DetectionFlag.SOUND_TICK)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                        ChatUtils.info((String)("[BDF] Spawner-tick sound chunk " + cp.field_9181 + "," + cp.field_9180 + " Y=" + String.format("%.1f", soundY) + " count=" + newCount), (Object[])new Object[0]);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (BlockDropFinder.MC.field_1687 == null || BlockDropFinder.MC.field_1724 == null) {
            return;
        }
        class_2818 chunk = event.chunk();
        class_1923 cp = chunk.method_12004();
        if (!this.isInScanRadius(cp)) {
            return;
        }
        int incomingCount = chunk.method_12214().size();
        Integer previous = this.firstBeCount.putIfAbsent(cp, incomingCount);
        if (previous != null) {
            boolean isNew;
            this.firstBeCount.put(cp, incomingCount);
            if (incomingCount > previous && (isNew = this.addFlag(cp, DetectionFlag.RESYNC)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
                ChatUtils.info((String)("[BDF] Resync anomaly chunk " + cp.field_9181 + "," + cp.field_9180 + " before=" + previous + " after=" + incomingCount), (Object[])new Object[0]);
            }
        }
    }

    private void recordBlockUpdate(class_1923 cp) {
        Long oldest;
        if (!this.isInScanRadius(cp)) {
            return;
        }
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque times = this.updateTimestamps.computeIfAbsent(cp, k -> new ConcurrentLinkedDeque());
        times.addLast(now);
        while ((oldest = (Long)times.peekFirst()) != null && now - oldest > 30000L) {
            times.pollFirst();
        }
        this.evaluateActivityAnomaly(cp);
    }

    private void evaluateActivityAnomaly(class_1923 target) {
        boolean isNew;
        double cutoff;
        if (BlockDropFinder.MC.field_1724 == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int pCx = BlockDropFinder.MC.field_1724.method_31476().field_9181;
        int pCz = BlockDropFinder.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        ArrayList<Double> counts = new ArrayList<Double>();
        for (Map.Entry<class_1923, ConcurrentLinkedDeque<Long>> e : this.updateTimestamps.entrySet()) {
            Long h;
            class_1923 cp = e.getKey();
            if (Math.abs(cp.field_9181 - pCx) > rad || Math.abs(cp.field_9180 - pCz) > rad) continue;
            ConcurrentLinkedDeque<Long> times = e.getValue();
            while ((h = times.peekFirst()) != null && now - h > 30000L) {
                times.pollFirst();
            }
            counts.add(Double.valueOf(times.size()));
        }
        if (counts.size() < 3) {
            return;
        }
        double sum = counts.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / (double)counts.size();
        double varSum = counts.stream().mapToDouble(c -> (c - mean) * (c - mean)).sum();
        double stdDev = Math.sqrt(varSum / (double)counts.size());
        if (stdDev < 0.5) {
            return;
        }
        ConcurrentLinkedDeque<Long> targetTimes = this.updateTimestamps.get(target);
        if (targetTimes == null) {
            return;
        }
        double rate = targetTimes.size();
        if (rate >= (cutoff = mean + (Double)this.activityThreshold.get() * stdDev) && (isNew = this.addFlag(target, DetectionFlag.ACTIVITY)) && ((Boolean)this.chatDebug.get()).booleanValue()) {
            ChatUtils.info((String)("[BDF] Activity anomaly chunk " + target.field_9181 + "," + target.field_9180 + " rate=" + (int)rate + " cutoff=" + String.format("%.1f", cutoff)), (Object[])new Object[0]);
        }
    }

    private boolean addFlag(class_1923 cp, DetectionFlag flag) {
        return this.detectedChunks.computeIfAbsent(cp, k -> ConcurrentHashMap.newKeySet()).add(flag);
    }

    private boolean isInScanRadius(class_1923 cp) {
        if (BlockDropFinder.MC.field_1724 == null) {
            return false;
        }
        int dx = Math.abs(cp.field_9181 - BlockDropFinder.MC.field_1724.method_31476().field_9181);
        int dz = Math.abs(cp.field_9180 - BlockDropFinder.MC.field_1724.method_31476().field_9180);
        return dx <= (Integer)this.scanRadius.get() && dz <= (Integer)this.scanRadius.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (BlockDropFinder.MC.field_1724 == null || BlockDropFinder.MC.field_1687 == null || this.detectedChunks.isEmpty()) {
            return;
        }
        class_243 eye = BlockDropFinder.MC.field_1724.method_5836(event.tickDelta);
        int py = (Integer)this.plateY.get();
        for (Map.Entry<class_1923, Set<DetectionFlag>> entry : this.detectedChunks.entrySet()) {
            class_1923 cp = entry.getKey();
            Set<DetectionFlag> flags = entry.getValue();
            if (flags.isEmpty()) continue;
            boolean multi = flags.size() > 1;
            DetectionFlag primary = flags.iterator().next();
            Color fill = multi ? C_BOTH_FILL : BlockDropFinder.fillColor(primary);
            Color outline = multi ? C_BOTH_LINE : BlockDropFinder.lineColor(primary);
            Color tracer = multi ? C_BOTH_TRACE : BlockDropFinder.traceColor(primary);
            double x0 = cp.method_8326();
            double z0 = cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            event.renderer.box(x0, (double)py, z0, x1, (double)py + 0.15, z1, fill, outline, ShapeMode.Both, 0);
            if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
            event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, x0 + 8.0, (double)py + 0.075, z0 + 8.0, tracer);
        }
    }

    private static Color fillColor(DetectionFlag f) {
        return switch (f.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> C_ACT_FILL;
            case 1 -> C_RES_FILL;
            case 2 -> C_SND_FILL;
        };
    }

    private static Color lineColor(DetectionFlag f) {
        return switch (f.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> C_ACT_LINE;
            case 1 -> C_RES_LINE;
            case 2 -> C_SND_LINE;
        };
    }

    private static Color traceColor(DetectionFlag f) {
        return switch (f.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> C_ACT_TRACE;
            case 1 -> C_RES_TRACE;
            case 2 -> C_SND_TRACE;
        };
    }

    private static /* synthetic */ void lambda$onPacketReceive$0(boolean[] done, class_1923[] cpRef, int[] minY, class_2338 blockPos, class_2680 blockState) {
        if (!done[0]) {
            cpRef[0] = new class_1923(blockPos);
            done[0] = true;
        }
        if (blockPos.method_10264() < minY[0]) {
            minY[0] = blockPos.method_10264();
        }
    }

    private static enum DetectionFlag {
        ACTIVITY,
        RESYNC,
        SOUND_TICK;

    }
}
