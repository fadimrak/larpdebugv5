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
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2580;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2596;
import net.minecraft.class_2601;
import net.minecraft.class_2604;
import net.minecraft.class_2605;
import net.minecraft.class_2608;
import net.minecraft.class_2614;
import net.minecraft.class_2636;
import net.minecraft.class_2646;
import net.minecraft.class_2767;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2902;
import net.minecraft.class_310;
import net.minecraft.class_3419;
import net.minecraft.class_3719;
import net.minecraft.class_742;

public class QuantumRadar
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final ConcurrentHashMap<class_1923, QData> data = new ConcurrentHashMap();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgQ1 = this.settings.createGroup("Q1 Block-Entity Scan");
    private final SettingGroup sgQ2 = this.settings.createGroup("Q2 Sound Triangulation");
    private final SettingGroup sgQ3 = this.settings.createGroup("Q3 Light Forensics");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Double> confirmThreshold = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("confirm-threshold")).description("Minimum suspicion score to flag a chunk as confirmed.")).defaultValue(60.0).min(10.0).sliderMax(400.0).build());
    private final Setting<Boolean> requireMultiFlag = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("require-multi-signal")).description("Chunk must have signals from 2+ independent vectors to confirm.")).defaultValue((Object)true)).build());
    private final Setting<Double> playerBonus = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("player-bonus")).description("Score added when a live player is found at Y<0 (Q5).")).defaultValue(55.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Double> itemDropBonus = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("item-drop-bonus")).description("Score added when an item entity spawns below Y=0 (Q4).")).defaultValue(18.0).min(1.0).sliderMax(100.0).build());
    private final Setting<Boolean> chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat when a chunk is first confirmed.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> debugMode = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-mode")).description("Print every score increment to chat for calibration.")).defaultValue((Object)false)).build());
    private final Setting<Boolean> q1Enabled = this.sgQ1.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("enabled")).description("Enable client-side block-entity graph scanning (Q1).")).defaultValue((Object)true)).build());
    private final Setting<Integer> scanRadius = this.sgQ1.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius")).description("Chunk radius to scan for block entities below Y=0.")).defaultValue((Object)8)).min(1).sliderMax(16).build());
    private final Setting<Integer> scanInterval = this.sgQ1.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-interval-ticks")).description("Ticks between Q1 scan passes.")).defaultValue((Object)60)).min(10).sliderMax(400).build());
    private final Setting<Integer> scanYCeiling = this.sgQ1.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-y-ceiling")).description("Only count block entities strictly below this Y level.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(16).build());
    private final Setting<Double> weightSpawner = this.sgQ1.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-spawner-be")).description("Score per MobSpawnerBlockEntity found below Y ceiling.")).defaultValue(80.0).min(1.0).sliderMax(300.0).build());
    private final Setting<Double> weightOtherBE = this.sgQ1.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-other-be")).description("Score per other notable block entity (Chest, Hopper, Beacon \u2026).")).defaultValue(5.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Boolean> q2Enabled = this.sgQ2.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("enabled")).description("Enable sound triangulation (Q2).")).defaultValue((Object)true)).build());
    private final Setting<Double> soundBonus = this.sgQ2.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("sound-bonus")).description("Score per HOSTILE/NEUTRAL sound packet below the Y threshold.")).defaultValue(38.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Integer> soundYThreshold = this.sgQ2.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("sound-y-threshold")).description("Only register sounds originating below this Y level.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(0).build());
    private final Setting<Boolean> q3Enabled = this.sgQ3.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("enabled")).description("Enable light propagation forensics (Q3).")).defaultValue((Object)true)).build());
    private final Setting<Double> lightBonus = this.sgQ3.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("light-bonus-per-block")).description("Score per artificially lit enclosed air block found below Y=0.")).defaultValue(6.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Integer> lightScanInterval = this.sgQ3.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-scan-interval-ticks")).description("Ticks between Q3 light scan passes.")).defaultValue((Object)220)).min(40).sliderMax(600).build());
    private final Setting<Integer> lightMinLevel = this.sgQ3.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-min-level")).description("Minimum block-light to count as artificial (1\u201315).")).defaultValue((Object)1)).min(1).sliderMax(15).build());
    private final Setting<SettingColor> plateColorBase = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-base-color")).description("Plate fill for a multi-signal base detection.")).defaultValue(new SettingColor(0, 255, 100, 40)).build());
    private final Setting<SettingColor> plateColorSpawner = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-spawner-color")).description("Plate fill when a Spawner block entity is directly confirmed.")).defaultValue(new SettingColor(180, 0, 255, 55)).build());
    private final Setting<SettingColor> plateColorPlayer = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-player-color")).description("Plate fill when a live underground player is detected.")).defaultValue(new SettingColor(0, 150, 255, 55)).build());
    private final Setting<SettingColor> plateOutline = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-outline-color")).description("Outline color for all detection plates.")).defaultValue(new SettingColor(0, 255, 120, 220)).build());
    private final Setting<Boolean> showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Render a tall alert pillar over each confirmed chunk.")).defaultValue((Object)true)).build());
    private final Setting<Integer> pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Height of the alert pillar in blocks.")).defaultValue((Object)120)).min(16).sliderMax(320).build());
    private final Setting<SettingColor> pillarFill = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("pillar-fill-color")).description("Fill color of the alert pillar.")).defaultValue(new SettingColor(255, 0, 0, 20)).build());
    private final Setting<SettingColor> pillarLine = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("pillar-line-color")).description("Outline color of the alert pillar.")).defaultValue(new SettingColor(255, 0, 0, 200)).build());
    private final Setting<SettingColor> spawnerEspFill = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-esp-fill")).description("Fill color for per-spawner ESP boxes.")).defaultValue(new SettingColor(255, 215, 0, 40)).build());
    private final Setting<SettingColor> spawnerEspLine = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-esp-line")).description("Outline color for per-spawner ESP boxes.")).defaultValue(new SettingColor(255, 215, 0, 255)).build());
    private final Setting<Boolean> showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines from camera to confirmed chunks.")).defaultValue((Object)true)).build());
    private final Setting<SettingColor> tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).description("Color for tracer lines.")).defaultValue(new SettingColor(255, 50, 50, 160)).build());
    private int q1Tick = 0;
    private int q3Tick = 0;

    public QuantumRadar() {
        super(DonutAddon.CATEGORY, "quantum-radar", "Client-rendering-forensics base detector. Reads block entities from the client's own WorldChunk object graph \u2014 data the server cannot un-send after loading.");
    }

    public void onActivate() {
        this.data.clear();
        this.q1Tick = 0;
        this.q3Tick = 0;
        ChatUtils.info((String)"\u00a7b[QuantumRadar] \u00a77Online. Reading client chunk graph\u2026", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.data.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (QuantumRadar.MC.field_1687 == null || QuantumRadar.MC.field_1724 == null) {
            return;
        }
        if (((Boolean)this.q1Enabled.get()).booleanValue() && ++this.q1Tick >= (Integer)this.scanInterval.get()) {
            this.q1Tick = 0;
            this.runQ1BlockEntityScan();
            this.runQ5PlayerScan();
        }
        if (((Boolean)this.q3Enabled.get()).booleanValue() && ++this.q3Tick >= (Integer)this.lightScanInterval.get()) {
            this.q3Tick = 0;
            this.runQ3LightForensics();
        }
    }

    private void runQ1BlockEntityScan() {
        int pCx = QuantumRadar.MC.field_1724.method_31476().field_9181;
        int pCz = QuantumRadar.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int yCeil = (Integer)this.scanYCeiling.get();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = QuantumRadar.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                double chunkPts = 0.0;
                ArrayList<class_2338> spawnerPositions = new ArrayList<class_2338>();
                for (Map.Entry be : chunk.method_12214().entrySet()) {
                    class_2338 bp = (class_2338)be.getKey();
                    if (bp.method_10264() >= yCeil) continue;
                    class_2586 entity = (class_2586)be.getValue();
                    if (entity instanceof class_2636) {
                        chunkPts += ((Double)this.weightSpawner.get()).doubleValue();
                        spawnerPositions.add(bp);
                        continue;
                    }
                    if (!(entity instanceof class_2595) && !(entity instanceof class_2646) && !(entity instanceof class_3719) && !(entity instanceof class_2614) && !(entity instanceof class_2601) && !(entity instanceof class_2608) && !(entity instanceof class_2580) && !(entity instanceof class_2605)) continue;
                    chunkPts += ((Double)this.weightOtherBE.get()).doubleValue();
                }
                if (!(chunkPts > 0.0)) continue;
                QData d = this.data.computeIfAbsent(cp, QData::new);
                d.add(QFlag.BLOCK_ENTITY, chunkPts);
                d.surfaceY = this.computeSurfaceY(chunk);
                if (!spawnerPositions.isEmpty()) {
                    d.confirmedSpawners.addAll(spawnerPositions);
                    if (d.labelType < 1) {
                        d.labelType = 1;
                    }
                }
                if (((Boolean)this.debugMode.get()).booleanValue()) {
                    ChatUtils.info((String)("[QR] \u00a7eQ1\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " BE+" + String.format("%.0f", chunkPts) + " spawners=" + spawnerPositions.size()), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, d);
            }
        }
    }

    private void runQ3LightForensics() {
        int pCx = QuantumRadar.MC.field_1724.method_31476().field_9181;
        int pCz = QuantumRadar.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int yCeil = (Integer)this.scanYCeiling.get();
        int minLvl = (Integer)this.lightMinLevel.get();
        int bottomY = QuantumRadar.MC.field_1687.method_31607();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = QuantumRadar.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                class_2826[] sections = chunk.method_12006();
                int lit = 0;
                for (int i = 0; i < sections.length; ++i) {
                    int secBase;
                    if (sections[i] == null || sections[i].method_38292() || (secBase = bottomY + i * 16) + 16 > yCeil) continue;
                    for (int bx = 1; bx < 15; ++bx) {
                        for (int by = 1; by < 15; ++by) {
                            for (int bz = 1; bz < 15; ++bz) {
                                class_2338 bp;
                                if (!sections[i].method_12254(bx, by, bz).method_26215() || QuantumRadar.MC.field_1687.method_8314(class_1944.field_9282, bp = new class_2338(cp.method_8326() + bx, secBase + by, cp.method_8328() + bz)) < minLvl || !this.allNeighborsSolid(bp)) continue;
                                ++lit;
                            }
                        }
                    }
                }
                if (lit <= 0) continue;
                double pts = (double)lit * (Double)this.lightBonus.get();
                QData d = this.data.computeIfAbsent(cp, QData::new);
                d.add(QFlag.LIGHT, pts);
                d.surfaceY = this.computeSurfaceY(chunk);
                if (((Boolean)this.debugMode.get()).booleanValue()) {
                    ChatUtils.info((String)("[QR] \u00a7bQ3\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " lit=" + lit + " +" + String.format("%.1f", pts)), (Object[])new Object[0]);
                }
                this.tryConfirm(cp, d);
            }
        }
    }

    private void runQ5PlayerScan() {
        if (QuantumRadar.MC.field_1687 == null || QuantumRadar.MC.field_1724 == null) {
            return;
        }
        for (class_742 e : QuantumRadar.MC.field_1687.method_18456()) {
            if (e.method_5667().equals(QuantumRadar.MC.field_1724.method_5667()) || !(e.method_23318() < 0.0)) continue;
            this.recordPlayer(e);
        }
    }

    private void recordPlayer(class_742 p) {
        class_1923 cp = p.method_31476();
        QData d = this.data.computeIfAbsent(cp, QData::new);
        d.add(QFlag.PLAYER, (Double)this.playerBonus.get());
        if (d.labelType < 2) {
            d.labelType = 2;
        }
        if (((Boolean)this.debugMode.get()).booleanValue()) {
            ChatUtils.info((String)("[QR] \u00a79Q5\u00a7r player " + p.method_5477().getString() + " Y=" + String.format("%.1f", p.method_23318())), (Object[])new Object[0]);
        }
        this.tryConfirm(cp, d);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        class_2596 cat;
        class_2767 pkt;
        class_2596 class_25962;
        if (QuantumRadar.MC.field_1687 == null || QuantumRadar.MC.field_1724 == null) {
            return;
        }
        if (((Boolean)this.q2Enabled.get()).booleanValue() && (class_25962 = event.packet) instanceof class_2767) {
            pkt = (class_2767)class_25962;
            cat = pkt.method_11888();
            if (cat != class_3419.field_15251 && cat != class_3419.field_15254) {
                return;
            }
            double sy = pkt.method_11889();
            if (sy >= (double)((Integer)this.soundYThreshold.get()).intValue()) {
                return;
            }
            class_1923 cp = new class_1923(class_2338.method_49637((double)pkt.method_11890(), (double)sy, (double)pkt.method_11893()));
            QData d = this.data.computeIfAbsent(cp, QData::new);
            d.add(QFlag.SOUND, (Double)this.soundBonus.get());
            class_2818 chunk = QuantumRadar.MC.field_1687.method_8497(cp.field_9181, cp.field_9180);
            if (chunk != null) {
                d.surfaceY = this.computeSurfaceY(chunk);
            }
            if (((Boolean)this.debugMode.get()).booleanValue()) {
                ChatUtils.info((String)("[QR] \u00a7cQ2\u00a7r sound " + cat.method_14840() + " Y=" + String.format("%.1f", sy) + " +" + String.format("%.0f", this.soundBonus.get())), (Object[])new Object[0]);
            }
            this.tryConfirm(cp, d);
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
            class_1923 cp = new class_1923(class_2338.method_49637((double)pkt.method_11175(), (double)ey, (double)pkt.method_11176()));
            QData d = this.data.computeIfAbsent(cp, QData::new);
            d.add(QFlag.ITEM_DROP, (Double)this.itemDropBonus.get());
            class_2818 chunk = QuantumRadar.MC.field_1687.method_8497(cp.field_9181, cp.field_9180);
            if (chunk != null) {
                d.surfaceY = this.computeSurfaceY(chunk);
            }
            if (((Boolean)this.debugMode.get()).booleanValue()) {
                ChatUtils.info((String)("[QR] \u00a76Q4\u00a7r item drop Y=" + String.format("%.1f", ey) + " +" + String.format("%.0f", this.itemDropBonus.get())), (Object[])new Object[0]);
            }
            this.tryConfirm(cp, d);
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        class_742 p;
        if (QuantumRadar.MC.field_1687 == null || QuantumRadar.MC.field_1724 == null) {
            return;
        }
        class_1297 class_12972 = event.entity;
        if (class_12972 instanceof class_742 && !(p = (class_742)class_12972).method_5667().equals(QuantumRadar.MC.field_1724.method_5667()) && p.method_23318() < 0.0) {
            this.recordPlayer(p);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (QuantumRadar.MC.field_1724 == null || QuantumRadar.MC.field_1687 == null) {
            return;
        }
        class_243 eye = QuantumRadar.MC.field_1724.method_5836(event.tickDelta);
        for (Map.Entry<class_1923, QData> entry : this.data.entrySet()) {
            QData d = entry.getValue();
            if (!d.confirmed) continue;
            class_1923 cp = entry.getKey();
            double plateY = (double)d.surfaceY + 0.05;
            double x0 = cp.method_8326();
            double z0 = cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            SettingColor fill = switch (d.labelType) {
                case 1 -> (SettingColor)this.plateColorSpawner.get();
                case 2 -> (SettingColor)this.plateColorPlayer.get();
                default -> (SettingColor)this.plateColorBase.get();
            };
            event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.15, z1, (Color)fill, (Color)this.plateOutline.get(), ShapeMode.Both, 0);
            if (((Boolean)this.showPillar.get()).booleanValue()) {
                int h = (Integer)this.pillarHeight.get();
                event.renderer.box(x0, plateY, z0, x1, plateY + (double)h, z1, (Color)this.pillarFill.get(), (Color)this.pillarLine.get(), ShapeMode.Both, 0);
            }
            if (((Boolean)this.showTracers.get()).booleanValue()) {
                event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, (double)cp.method_33940(), plateY, (double)cp.method_33942(), (Color)this.tracerColor.get());
            }
            if (d.confirmedSpawners.isEmpty()) continue;
            SettingColor ef = (SettingColor)this.spawnerEspFill.get();
            SettingColor el = (SettingColor)this.spawnerEspLine.get();
            for (class_2338 sp : d.confirmedSpawners) {
                event.renderer.box((double)sp.method_10263(), (double)sp.method_10264(), (double)sp.method_10260(), (double)(sp.method_10263() + 1), (double)(sp.method_10264() + 1), (double)(sp.method_10260() + 1), (Color)ef, (Color)el, ShapeMode.Both, 0);
            }
        }
    }

    private void tryConfirm(class_1923 cp, QData d) {
        boolean flagsOk;
        if (d.confirmed) {
            return;
        }
        boolean scoreOk = d.score >= (Double)this.confirmThreshold.get();
        boolean bl = flagsOk = (Boolean)this.requireMultiFlag.get() == false || d.flags.size() >= 2;
        if (scoreOk && flagsOk) {
            d.confirmed = true;
            if (((Boolean)this.chatNotify.get()).booleanValue()) {
                String type = switch (d.labelType) {
                    case 1 -> "\u00a75SPAWNER";
                    case 2 -> "\u00a79PLAYER";
                    default -> "\u00a7aBASE";
                };
                ChatUtils.info((String)("[QuantumRadar] " + type + "\u00a7r confirmed chunk " + cp.field_9181 + "," + cp.field_9180 + " \u00a77score=\u00a7f" + String.format("%.0f", d.score) + " \u00a77signals=\u00a7f" + d.flags.size()), (Object[])new Object[0]);
            }
        }
    }

    private boolean allNeighborsSolid(class_2338 pos) {
        if (QuantumRadar.MC.field_1687 == null) {
            return false;
        }
        class_2338 n = pos.method_10095();
        class_2338 s = pos.method_10072();
        class_2338 e = pos.method_10078();
        class_2338 w = pos.method_10067();
        class_2338 u = pos.method_10084();
        class_2338 dn = pos.method_10074();
        return QuantumRadar.MC.field_1687.method_8320(n).method_26212((class_1922)QuantumRadar.MC.field_1687, n) && QuantumRadar.MC.field_1687.method_8320(s).method_26212((class_1922)QuantumRadar.MC.field_1687, s) && QuantumRadar.MC.field_1687.method_8320(e).method_26212((class_1922)QuantumRadar.MC.field_1687, e) && QuantumRadar.MC.field_1687.method_8320(w).method_26212((class_1922)QuantumRadar.MC.field_1687, w) && QuantumRadar.MC.field_1687.method_8320(u).method_26212((class_1922)QuantumRadar.MC.field_1687, u) && QuantumRadar.MC.field_1687.method_8320(dn).method_26212((class_1922)QuantumRadar.MC.field_1687, dn);
    }

    private int computeSurfaceY(class_2818 chunk) {
        if (QuantumRadar.MC.field_1687 == null) {
            return 64;
        }
        int high = QuantumRadar.MC.field_1687.method_31607();
        for (int bx = 0; bx < 16; ++bx) {
            for (int bz = 0; bz < 16; ++bz) {
                int y = chunk.method_12005(class_2902.class_2903.field_13197, bx, bz);
                if (y <= high) continue;
                high = y;
            }
        }
        return high;
    }

    private static class QData {
        final class_1923 cp;
        final Set<QFlag> flags = ConcurrentHashMap.newKeySet();
        final Set<class_2338> confirmedSpawners = ConcurrentHashMap.newKeySet();
        double score = 0.0;
        boolean confirmed = false;
        int surfaceY = 64;
        int labelType = 0;

        QData(class_1923 cp) {
            this.cp = cp;
        }

        void add(QFlag flag, double pts) {
            this.flags.add(flag);
            this.score += pts;
        }
    }

    private static enum QFlag {
        BLOCK_ENTITY,
        SOUND,
        LIGHT,
        ITEM_DROP,
        PLAYER;

    }
}
