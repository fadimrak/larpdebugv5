package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
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
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1922;
import net.minecraft.class_1923;
import net.minecraft.class_1944;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2604;
import net.minecraft.class_2767;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2902;
import net.minecraft.class_310;
import net.minecraft.class_3419;
import net.minecraft.class_742;

public class ArchonRadar
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final ConcurrentHashMap<class_1923, RadarData> radar = new ConcurrentHashMap();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgScanner = this.settings.createGroup("Block Scanner");
    private final SettingGroup sgSignals = this.settings.createGroup("Signals");
    private final SettingGroup sgLight = this.settings.createGroup("Light Forensics");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Double> confirmThreshold = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("confirm-threshold")).description("Minimum suspicion score to flag a chunk as a confirmed base.")).defaultValue(60.0).min(10.0).sliderMax(300.0).build());
    private final Setting<Boolean> requireMultiFlag = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("require-multi-flag")).description("Require at least 2 different signal types before confirming a chunk.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat when a chunk is first confirmed.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> debugScores = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-scores")).description("Print every score increment to chat for tuning.")).defaultValue((Object)false)).build());
    private final Setting<Integer> scanRadius = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius")).description("Chunk radius to palette-scan for suspicious blocks.")).defaultValue((Object)8)).min(1).sliderMax(16).build());
    private final Setting<Integer> scanInterval = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-interval-ticks")).description("Ticks between block-scan passes.")).defaultValue((Object)80)).min(20).sliderMax(400).build());
    private final Setting<Integer> scanYCeiling = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-y-ceiling")).description("Only scan chunk sections fully below this Y level.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(16).build());
    private final Setting<Double> weightSpawner = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-spawner")).description("Score per Spawner block found below the Y ceiling.")).defaultValue(60.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Double> weightSuspicious = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-suspicious-block")).description("Score per other suspicious block (Obsidian, Chest, Hopper, Amethyst ...).")).defaultValue(4.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Integer> maxBlocksPerSection = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("max-blocks-per-section")).description("Cap per block type per section to avoid natural cluster inflation.")).defaultValue((Object)16)).min(1).sliderMax(64).build());
    private final Setting<Double> soundBonus = this.sgSignals.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("sound-bonus")).description("Score bonus when a HOSTILE/NEUTRAL sound is heard below Y=0 (V2).")).defaultValue(35.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Integer> soundYThreshold = this.sgSignals.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-y-threshold")).description("Only record sounds originating below this Y level.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(0).build());
    private final Setting<Double> itemDropBonus = this.sgSignals.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("item-drop-bonus")).description("Score bonus when an item entity spawns below Y=0 (block-breaking activity, V3).")).defaultValue(15.0).min(1.0).sliderMax(100.0).build());
    private final Setting<Double> playerBonus = this.sgSignals.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("player-y-bonus")).description("Score bonus when a player entity is detected at Y<0 (V5).")).defaultValue(50.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Double> lightBonusPerBlock = this.sgLight.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("light-bonus-per-block")).description("Score bonus per artificially lit enclosed air block found below Y=0 (V4).")).defaultValue(6.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Integer> lightScanInterval = this.sgLight.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-scan-interval-ticks")).description("Ticks between light forensic passes.")).defaultValue((Object)200)).min(40).sliderMax(600).build());
    private final Setting<Integer> lightMinLevel = this.sgLight.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-min-level")).description("Minimum block-light level to count as artificial (1-15).")).defaultValue((Object)1)).min(1).sliderMax(15).build());
    private final Setting<SettingColor> plateColorBase = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-color-base")).description("Plate fill for a general multi-signal base detection.")).defaultValue(new SettingColor(0, 255, 100, 40)).build());
    private final Setting<SettingColor> plateColorSpawner = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-color-spawner")).description("Plate fill when a Spawner block is directly confirmed.")).defaultValue(new SettingColor(180, 0, 255, 60)).build());
    private final Setting<SettingColor> plateColorPlayer = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-color-player")).description("Plate fill when an underground player is detected.")).defaultValue(new SettingColor(0, 150, 255, 55)).build());
    private final Setting<SettingColor> outlineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("outline-color")).description("Outline color for all detection plates.")).defaultValue(new SettingColor(0, 255, 120, 220)).build());
    private final Setting<SettingColor> spawnerEspFill = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-esp-fill")).description("Fill color for individual Spawner ESP boxes.")).defaultValue(new SettingColor(255, 215, 0, 50)).build());
    private final Setting<SettingColor> spawnerEspLine = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-esp-line")).description("Outline color for individual Spawner ESP boxes.")).defaultValue(new SettingColor(255, 215, 0, 255)).build());
    private final Setting<Boolean> showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines from camera to confirmed chunk centres.")).defaultValue((Object)true)).build());
    private final Setting<SettingColor> tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).description("Color for tracer lines.")).defaultValue(new SettingColor(0, 255, 120, 150)).build());
    private final Setting<Boolean> showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Render a tall red pillar over each confirmed chunk, visible from far away.")).defaultValue((Object)true)).build());
    private final Setting<Integer> pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Height of the red alert pillar in blocks.")).defaultValue((Object)100)).min(16).sliderMax(320).build());
    private final Setting<SettingColor> pillarFillColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("pillar-fill-color")).description("Fill color of the alert pillar.")).defaultValue(new SettingColor(255, 30, 30, 25)).build());
    private final Setting<SettingColor> pillarLineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("pillar-line-color")).description("Outline color of the alert pillar.")).defaultValue(new SettingColor(255, 30, 30, 200)).build());
    private int blockScanTick = 0;
    private int lightScanTick = 0;

    public ArchonRadar() {
        super(DonutAddon.CATEGORY, "archon-radar", "DonutSMP multi-vector passive intelligence radar. Detects Spawners and player bases below Y=0 from high altitude (Y=150+) with 5 independent bypass signals.");
    }

    public void onActivate() {
        this.radar.clear();
        this.blockScanTick = 0;
        this.lightScanTick = 0;
        ChatUtils.info((String)"\u00a7a[ArchonRadar] \u00a77Activated. All 5 detection vectors online.", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.radar.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ArchonRadar.MC.field_1687 != null && ArchonRadar.MC.field_1724 != null) {
            if (++this.blockScanTick >= (Integer)this.scanInterval.get()) {
                this.blockScanTick = 0;
                this.runV1BlockForensics();
                this.runV5PlayerDesync();
            }
            if (++this.lightScanTick >= (Integer)this.lightScanInterval.get()) {
                this.lightScanTick = 0;
                this.runV4LightForensics();
            }
        }
    }

    private void runV1BlockForensics() {
        int pCx = ArchonRadar.MC.field_1724.method_31476().field_9181;
        int pCz = ArchonRadar.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int bottomY = ArchonRadar.MC.field_1687.method_31607();
        int yCeil = (Integer)this.scanYCeiling.get();
        int cap = (Integer)this.maxBlocksPerSection.get();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = ArchonRadar.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                class_2826[] sections = chunk.method_12006();
                double chunkScore = 0.0;
                ArrayList<class_2338> newSpawners = new ArrayList<class_2338>();
                for (int i = 0; i < sections.length; ++i) {
                    boolean sectionHasTarget;
                    int secBaseY;
                    if (sections[i] == null || sections[i].method_38292() || (secBaseY = bottomY + i * 16) + 16 > yCeil || !(sectionHasTarget = sections[i].method_12265().method_19526(s -> s.method_26204() == class_2246.field_10260 || this.isSuspiciousBlock(s.method_26204())))) continue;
                    int spawners = 0;
                    int other = 0;
                    for (int bx = 0; bx < 16; ++bx) {
                        for (int by = 0; by < 16; ++by) {
                            for (int bz = 0; bz < 16; ++bz) {
                                class_2248 b = sections[i].method_12254(bx, by, bz).method_26204();
                                if (b == class_2246.field_10260) {
                                    if (spawners >= cap) continue;
                                    ++spawners;
                                    chunkScore += ((Double)this.weightSpawner.get()).doubleValue();
                                    newSpawners.add(new class_2338(cp.method_8326() + bx, secBaseY + by, cp.method_8328() + bz));
                                    continue;
                                }
                                if (!this.isSuspiciousBlock(b) || other >= cap) continue;
                                ++other;
                                chunkScore += ((Double)this.weightSuspicious.get()).doubleValue();
                            }
                        }
                    }
                }
                if (!(chunkScore > 0.0)) continue;
                RadarData data = this.radar.computeIfAbsent(cp, RadarData::new);
                data.addFlag(RadarFlag.BLOCK_SCAN, chunkScore);
                data.surfaceY = this.computeSurfaceY(chunk);
                if (!newSpawners.isEmpty()) {
                    data.confirmedSpawners.addAll(newSpawners);
                    if (data.labelType < 1) {
                        data.labelType = 1;
                    }
                }
                if (((Boolean)this.debugScores.get()).booleanValue()) {
                    ChatUtils.info((String)("[AR] \u00a7eV1-Block\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " +" + String.format("%.1f", chunkScore)), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, data);
            }
        }
    }

    private boolean isSuspiciousBlock(class_2248 b) {
        return b == class_2246.field_10540 || b == class_2246.field_22423 || b == class_2246.field_10471 || b == class_2246.field_10462 || b == class_2246.field_10034 || b == class_2246.field_10380 || b == class_2246.field_16328 || b == class_2246.field_27159 || b == class_2246.field_27161 || b == class_2246.field_27160 || b == class_2246.field_27162 || b == class_2246.field_27163 || b == class_2246.field_10312 || b == class_2246.field_10228 || b == class_2246.field_10200 || b == class_2246.field_10327 || b == class_2246.field_10485 || b == class_2246.field_10535;
    }

    private void runV4LightForensics() {
        int pCx = ArchonRadar.MC.field_1724.method_31476().field_9181;
        int pCz = ArchonRadar.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int bottomY = ArchonRadar.MC.field_1687.method_31607();
        int yCeil = (Integer)this.scanYCeiling.get();
        int minLvl = (Integer)this.lightMinLevel.get();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = ArchonRadar.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                class_2826[] sections = chunk.method_12006();
                int litCount = 0;
                for (int i = 0; i < sections.length; ++i) {
                    int secBaseY;
                    if (sections[i] == null || sections[i].method_38292() || (secBaseY = bottomY + i * 16) + 16 > yCeil) continue;
                    for (int bx = 1; bx < 15; ++bx) {
                        for (int by = 1; by < 15; ++by) {
                            for (int bz = 1; bz < 15; ++bz) {
                                class_2338 bp;
                                int light;
                                if (!sections[i].method_12254(bx, by, bz).method_26215() || (light = ArchonRadar.MC.field_1687.method_8314(class_1944.field_9282, bp = new class_2338(cp.method_8326() + bx, secBaseY + by, cp.method_8328() + bz))) < minLvl || !this.areNeighborsSolid(bp)) continue;
                                ++litCount;
                            }
                        }
                    }
                }
                if (litCount <= 0) continue;
                double bonus = (double)litCount * (Double)this.lightBonusPerBlock.get();
                RadarData data = this.radar.computeIfAbsent(cp, RadarData::new);
                data.addFlag(RadarFlag.LIGHT, bonus);
                data.surfaceY = this.computeSurfaceY(chunk);
                if (((Boolean)this.debugScores.get()).booleanValue()) {
                    ChatUtils.info((String)("[AR] \u00a7bV4-Light\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " lit=" + litCount + " +" + String.format("%.1f", bonus)), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, data);
            }
        }
    }

    private boolean areNeighborsSolid(class_2338 pos) {
        if (ArchonRadar.MC.field_1687 == null) {
            return false;
        }
        class_2338 n = pos.method_10095();
        class_2338 s = pos.method_10072();
        class_2338 e = pos.method_10078();
        class_2338 w = pos.method_10067();
        class_2338 u = pos.method_10084();
        class_2338 d = pos.method_10074();
        return ArchonRadar.MC.field_1687.method_8320(n).method_26212((class_1922)ArchonRadar.MC.field_1687, n) && ArchonRadar.MC.field_1687.method_8320(s).method_26212((class_1922)ArchonRadar.MC.field_1687, s) && ArchonRadar.MC.field_1687.method_8320(e).method_26212((class_1922)ArchonRadar.MC.field_1687, e) && ArchonRadar.MC.field_1687.method_8320(w).method_26212((class_1922)ArchonRadar.MC.field_1687, w) && ArchonRadar.MC.field_1687.method_8320(u).method_26212((class_1922)ArchonRadar.MC.field_1687, u) && ArchonRadar.MC.field_1687.method_8320(d).method_26212((class_1922)ArchonRadar.MC.field_1687, d);
    }

    private void runV5PlayerDesync() {
        if (ArchonRadar.MC.field_1687 != null && ArchonRadar.MC.field_1724 != null) {
            for (class_742 entity : ArchonRadar.MC.field_1687.method_18456()) {
                if (entity.method_5667().equals(ArchonRadar.MC.field_1724.method_5667()) || entity.method_23318() >= 0.0) continue;
                class_1923 cp = entity.method_31476();
                RadarData data = this.radar.computeIfAbsent(cp, RadarData::new);
                data.addFlag(RadarFlag.PLAYER_SEEN, (Double)this.playerBonus.get());
                if (data.labelType < 2) {
                    data.labelType = 2;
                }
                if (((Boolean)this.debugScores.get()).booleanValue()) {
                    ChatUtils.info((String)("[AR] \u00a79V5-Player\u00a7r " + entity.method_5477().getString() + " Y=" + String.format("%.1f", entity.method_23318()) + " chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, data);
            }
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (ArchonRadar.MC.field_1687 != null && ArchonRadar.MC.field_1724 != null) {
            class_2596 cat;
            class_2767 pkt;
            class_2596 class_25962 = event.packet;
            if (class_25962 instanceof class_2767) {
                pkt = (class_2767)class_25962;
                cat = pkt.method_11888();
                if (cat != class_3419.field_15251 && cat != class_3419.field_15254) {
                    return;
                }
                double sy = pkt.method_11889();
                if (sy >= (double)((Integer)this.soundYThreshold.get()).intValue()) {
                    return;
                }
                double sx = pkt.method_11890();
                double sz = pkt.method_11893();
                class_1923 cp = new class_1923(class_2338.method_49637((double)sx, (double)sy, (double)sz));
                RadarData data = this.radar.computeIfAbsent(cp, RadarData::new);
                data.addFlag(RadarFlag.SOUND, (Double)this.soundBonus.get());
                class_2818 chunk = ArchonRadar.MC.field_1687.method_8497(cp.field_9181, cp.field_9180);
                if (chunk != null) {
                    data.surfaceY = this.computeSurfaceY(chunk);
                }
                if (((Boolean)this.debugScores.get()).booleanValue()) {
                    ChatUtils.info((String)("[AR] \u00a7cV2-Sound\u00a7r " + cat.method_14840() + " chunk " + cp.field_9181 + "," + cp.field_9180 + " Y=" + String.format("%.1f", sy) + " +" + String.format("%.0f", this.soundBonus.get())), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, data);
            }
            if ((cat = event.packet) instanceof class_2604) {
                pkt = (class_2604)cat;
                if (pkt.method_11169() != class_1299.field_6052) {
                    return;
                }
                double ey = pkt.method_11174();
                if (ey >= 0.0) {
                    return;
                }
                double ex = pkt.method_11175();
                double ez = pkt.method_11176();
                class_1923 cpx = new class_1923(class_2338.method_49637((double)ex, (double)ey, (double)ez));
                RadarData datax = this.radar.computeIfAbsent(cpx, RadarData::new);
                datax.addFlag(RadarFlag.ITEM_DROP, (Double)this.itemDropBonus.get());
                class_2818 chunkx = ArchonRadar.MC.field_1687.method_8497(cpx.field_9181, cpx.field_9180);
                if (chunkx != null) {
                    datax.surfaceY = this.computeSurfaceY(chunkx);
                }
                if (((Boolean)this.debugScores.get()).booleanValue()) {
                    ChatUtils.info((String)("[AR] \u00a76V3-Item\u00a7r drop Y=" + String.format("%.1f", ey) + " chunk " + cpx.field_9181 + "," + cpx.field_9180 + " +" + String.format("%.0f", this.itemDropBonus.get())), (Object[])new Object[0]);
                }
                this.tryConfirm(cpx, datax);
            }
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        class_742 player;
        class_1297 class_12972;
        if (ArchonRadar.MC.field_1687 != null && ArchonRadar.MC.field_1724 != null && (class_12972 = event.entity) instanceof class_742 && !(player = (class_742)class_12972).method_5667().equals(ArchonRadar.MC.field_1724.method_5667()) && !(player.method_23318() >= 0.0)) {
            class_1923 cp = player.method_31476();
            RadarData data = this.radar.computeIfAbsent(cp, RadarData::new);
            data.addFlag(RadarFlag.PLAYER_SEEN, (Double)this.playerBonus.get());
            if (data.labelType < 2) {
                data.labelType = 2;
            }
            if (((Boolean)this.debugScores.get()).booleanValue()) {
                ChatUtils.info((String)("[AR] \u00a79V5-EntityAdded\u00a7r player " + player.method_5477().getString() + " Y=" + String.format("%.1f", player.method_23318()) + " chunk " + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
            }
            this.tryConfirm(cp, data);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (ArchonRadar.MC.field_1724 != null && ArchonRadar.MC.field_1687 != null) {
            class_243 eye = ArchonRadar.MC.field_1724.method_5836(event.tickDelta);
            for (Map.Entry<class_1923, RadarData> entry : this.radar.entrySet()) {
                RadarData data = entry.getValue();
                if (!data.confirmed) continue;
                class_1923 cp = entry.getKey();
                double plateY = (double)data.surfaceY + 0.05;
                double x0 = cp.method_8326();
                double z0 = cp.method_8328();
                double x1 = x0 + 16.0;
                double z1 = z0 + 16.0;
                SettingColor fill = switch (data.labelType) {
                    case 1 -> (SettingColor)this.plateColorSpawner.get();
                    case 2 -> (SettingColor)this.plateColorPlayer.get();
                    default -> (SettingColor)this.plateColorBase.get();
                };
                SettingColor ol = (SettingColor)this.outlineColor.get();
                event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.15, z1, (Color)fill, (Color)ol, ShapeMode.Both, 0);
                if (((Boolean)this.showTracers.get()).booleanValue()) {
                    double cx = cp.method_33940();
                    double cz = cp.method_33942();
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, (Color)this.tracerColor.get());
                }
                if (((Boolean)this.showPillar.get()).booleanValue()) {
                    int h = (Integer)this.pillarHeight.get();
                    event.renderer.box(x0, plateY, z0, x1, plateY + (double)h, z1, (Color)this.pillarFillColor.get(), (Color)this.pillarLineColor.get(), ShapeMode.Both, 0);
                }
                if (data.confirmedSpawners.isEmpty()) continue;
                SettingColor espFill = (SettingColor)this.spawnerEspFill.get();
                SettingColor espLine = (SettingColor)this.spawnerEspLine.get();
                for (class_2338 sp : data.confirmedSpawners) {
                    event.renderer.box((double)sp.method_10263(), (double)sp.method_10264(), (double)sp.method_10260(), (double)(sp.method_10263() + 1), (double)(sp.method_10264() + 1), (double)(sp.method_10260() + 1), (Color)espFill, (Color)espLine, ShapeMode.Both, 0);
                }
            }
        }
    }

    private void tryConfirm(class_1923 cp, RadarData data) {
        if (!data.confirmed) {
            boolean flagsOk;
            boolean scoreOk = data.score >= (Double)this.confirmThreshold.get();
            boolean bl = flagsOk = (Boolean)this.requireMultiFlag.get() == false || data.flags.size() >= 2;
            if (scoreOk && flagsOk) {
                data.confirmed = true;
                if (((Boolean)this.chatNotify.get()).booleanValue()) {
                    String type = switch (data.labelType) {
                        case 1 -> "\u00a75SPAWNER";
                        case 2 -> "\u00a79PLAYER";
                        default -> "\u00a7aBASE";
                    };
                    ChatUtils.info((String)("[ArchonRadar] " + type + "\u00a7r confirmed chunk " + cp.field_9181 + "," + cp.field_9180 + " \u00a77score=\u00a7f" + String.format("%.0f", data.score) + " \u00a77flags=\u00a7f" + data.flags.size()), (Object[])new Object[0]);
                }
            }
        }
    }

    private int computeSurfaceY(class_2818 chunk) {
        if (ArchonRadar.MC.field_1687 == null) {
            return 64;
        }
        int highest = ArchonRadar.MC.field_1687.method_31607();
        for (int bx = 0; bx < 16; ++bx) {
            for (int bz = 0; bz < 16; ++bz) {
                int y = chunk.method_12005(class_2902.class_2903.field_13197, bx, bz);
                if (y <= highest) continue;
                highest = y;
            }
        }
        return highest;
    }

    private static class RadarData {
        final class_1923 cp;
        final Set<RadarFlag> flags = ConcurrentHashMap.newKeySet();
        final Set<class_2338> confirmedSpawners = ConcurrentHashMap.newKeySet();
        double score = 0.0;
        boolean confirmed = false;
        int surfaceY = 64;
        int labelType = 0;

        RadarData(class_1923 cp) {
            this.cp = cp;
        }

        void addFlag(RadarFlag flag, double bonus) {
            this.flags.add(flag);
            this.score += bonus;
        }
    }

    private static enum RadarFlag {
        BLOCK_SCAN,
        SOUND,
        ITEM_DROP,
        LIGHT,
        PLAYER_SEEN;

    }
}
