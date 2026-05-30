package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
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

public class AmethystLodeFinder
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final ConcurrentHashMap<class_1923, ChunkData> chunkData = new ConcurrentHashMap();
    private final SettingGroup sgScanner = this.settings.createGroup("Block Scanner");
    private final SettingGroup sgSound = this.settings.createGroup("Sound Triangulation");
    private final SettingGroup sgLight = this.settings.createGroup("Light Forensics");
    private final SettingGroup sgMob = this.settings.createGroup("Mob Spawn");
    private final SettingGroup sgThreshold = this.settings.createGroup("Threshold");
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgNotify = this.settings.getDefaultGroup();
    private final Setting<Integer> scanRadius = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius")).description("Chunk radius to scan for suspicious blocks.")).defaultValue((Object)8)).min(1).sliderMax(16).build());
    private final Setting<Integer> scanInterval = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-interval-ticks")).description("Ticks between each block scan pass.")).defaultValue((Object)100)).min(20).sliderMax(400).build());
    private final Setting<Double> weightSpawner = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-spawner")).description("Score per Spawner block found below Y=0.")).defaultValue(50.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Double> weightObsidian = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-obsidian")).description("Score per Obsidian block found below Y=0.")).defaultValue(3.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Double> weightEndStone = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-end-stone")).description("Score per End Stone block found below Y=0.")).defaultValue(2.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Double> weightAmethystCluster = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-amethyst-cluster")).description("Score per Amethyst Cluster/Bud found below Y=0.")).defaultValue(8.0).min(0.5).sliderMax(100.0).build());
    private final Setting<Double> weightAmethystBlock = this.sgScanner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("weight-amethyst-block")).description("Score per Amethyst Block found below Y=0.")).defaultValue(6.0).min(0.5).sliderMax(100.0).build());
    private final Setting<Integer> maxBlocksPerSection = this.sgScanner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("max-blocks-per-section")).description("Cap per block type per section to avoid natural cluster inflation.")).defaultValue((Object)16)).min(1).sliderMax(64).build());
    private final Setting<Double> soundBonus = this.sgSound.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("sound-bonus")).description("Score bonus for a spawner/fire sound detected near a chunk.")).defaultValue(40.0).min(1.0).sliderMax(200.0).build());
    private final Setting<Double> soundAttrRadius = this.sgSound.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("sound-attribution-radius")).description("Block radius around sound origin to spread bonus to adjacent chunks.")).defaultValue(8.0).min(2.0).sliderMax(32.0).build());
    private final Setting<Double> lightBonus = this.sgLight.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("light-bonus")).description("Score bonus per artificially lit enclosed air block found.")).defaultValue(5.0).min(0.5).sliderMax(50.0).build());
    private final Setting<Integer> lightScanInterval = this.sgLight.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-scan-interval-ticks")).description("Ticks between light forensic passes.")).defaultValue((Object)200)).min(40).sliderMax(600).build());
    private final Setting<Integer> lightYMax = this.sgLight.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-y-max")).description("Only scan for artificial light below this Y.")).defaultValue((Object)0)).min(-64).sliderMin(-64).sliderMax(10).build());
    private final Setting<Integer> lightMinLevel = this.sgLight.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("light-min-level")).description("Minimum block-light level to flag as artificial.")).defaultValue((Object)1)).min(1).sliderMax(15).build());
    private final Setting<Double> mobBonus = this.sgMob.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("mob-spawn-bonus")).description("Score bonus when a hostile mob spawns below Y=0 near a scored chunk.")).defaultValue(20.0).min(1.0).sliderMax(100.0).build());
    private final Setting<Double> flagThreshold = this.sgThreshold.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("flag-threshold")).description("Minimum suspicion score before a chunk is rendered as a Base.")).defaultValue(80.0).min(10.0).sliderMax(500.0).build());
    private final Setting<Boolean> requireSecondary = this.sgThreshold.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("require-secondary-signal")).description("Chunk must have at least one secondary signal (sound/light/mob) to be rendered.")).defaultValue((Object)true)).build());
    private final Setting<SettingColor> plateColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-fill-color")).description("Fill color of the base detection plate.")).defaultValue(new SettingColor(0, 255, 100, 40)).build());
    private final Setting<SettingColor> plateOutlineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("plate-outline-color")).description("Outline color of the base detection plate.")).defaultValue(new SettingColor(0, 255, 120, 200)).build());
    private final Setting<Boolean> showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines from camera to flagged chunks.")).defaultValue((Object)true)).build());
    private final Setting<SettingColor> tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).description("Color of tracer lines.")).defaultValue(new SettingColor(0, 255, 120, 180)).build());
    private final Setting<Boolean> chatNotify = this.sgNotify.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print to chat when a chunk is first flagged as a Base.")).defaultValue((Object)true)).build());
    private final Setting<Boolean> debugMode = this.sgNotify.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("debug-mode")).description("Print score increments to chat for tuning.")).defaultValue((Object)false)).build());
    private int blockScanTick = 0;
    private int lightScanTick = 0;
    private static final Set<class_1299<?>> HOSTILE_MOBS = new HashSet<class_1299>(Arrays.asList(class_1299.field_6051, class_1299.field_6137, class_1299.field_6079, class_1299.field_6084, class_1299.field_6046, class_1299.field_6076, class_1299.field_6099, class_1299.field_6091, class_1299.field_6071, class_1299.field_6098, class_1299.field_6123, class_1299.field_6054, class_1299.field_6105, class_1299.field_6117, class_1299.field_6145, class_1299.field_6125, class_1299.field_6069, class_1299.field_6102));

    public AmethystLodeFinder() {
        super(DonutAddon.CATEGORY, "amethyst-lode-finder", "DonutSMP multi-vector base detector. Combines block scan, sound, light & mob signals to locate hidden bases below Y=0.");
    }

    public void onActivate() {
        this.chunkData.clear();
        this.blockScanTick = 0;
        this.lightScanTick = 0;
        ChatUtils.info((String)"\u00a7a[AmethystLodeFinder] \u00a77Activated. Scanning for hidden bases...", (Object[])new Object[0]);
    }

    public void onDeactivate() {
        this.chunkData.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (AmethystLodeFinder.MC.field_1687 != null && AmethystLodeFinder.MC.field_1724 != null) {
            if (++this.blockScanTick >= (Integer)this.scanInterval.get()) {
                this.blockScanTick = 0;
                this.runBlockScan();
            }
            if (++this.lightScanTick >= (Integer)this.lightScanInterval.get()) {
                this.lightScanTick = 0;
                this.runLightForensics();
            }
        }
    }

    private void runBlockScan() {
        int pCx = AmethystLodeFinder.MC.field_1724.method_31476().field_9181;
        int pCz = AmethystLodeFinder.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int bottomY = AmethystLodeFinder.MC.field_1687.method_31607();
        int cap = (Integer)this.maxBlocksPerSection.get();
        int yMax = (Integer)this.lightYMax.get();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = AmethystLodeFinder.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                class_2826[] sections = chunk.method_12006();
                double chunkScore = 0.0;
                for (int i = 0; i < sections.length; ++i) {
                    int secBaseY;
                    if (sections[i] == null || sections[i].method_38292() || (secBaseY = bottomY + i * 16) + 16 > yMax) continue;
                    int spawners = 0;
                    int obsidian = 0;
                    int endstone = 0;
                    int clusters = 0;
                    int ablocks = 0;
                    for (int bx = 0; bx < 16; ++bx) {
                        for (int by = 0; by < 16; ++by) {
                            for (int bz = 0; bz < 16; ++bz) {
                                class_2248 b = sections[i].method_12254(bx, by, bz).method_26204();
                                if (b == class_2246.field_10260) {
                                    if (spawners >= cap) continue;
                                    ++spawners;
                                    chunkScore += ((Double)this.weightSpawner.get()).doubleValue();
                                    continue;
                                }
                                if (b != class_2246.field_10540 && b != class_2246.field_22423) {
                                    if (b != class_2246.field_10471 && b != class_2246.field_10462) {
                                        if (b != class_2246.field_27161 && b != class_2246.field_27162 && b != class_2246.field_27163 && b != class_2246.field_27164 && b != class_2246.field_27160) {
                                            if (b != class_2246.field_27159 || ablocks >= cap) continue;
                                            ++ablocks;
                                            chunkScore += ((Double)this.weightAmethystBlock.get()).doubleValue();
                                            continue;
                                        }
                                        if (clusters >= cap) continue;
                                        ++clusters;
                                        chunkScore += ((Double)this.weightAmethystCluster.get()).doubleValue();
                                        continue;
                                    }
                                    if (endstone >= cap) continue;
                                    ++endstone;
                                    chunkScore += ((Double)this.weightEndStone.get()).doubleValue();
                                    continue;
                                }
                                if (obsidian >= cap) continue;
                                ++obsidian;
                                chunkScore += ((Double)this.weightObsidian.get()).doubleValue();
                            }
                        }
                    }
                }
                if (!(chunkScore > 0.0)) continue;
                ChunkData data = this.chunkData.computeIfAbsent(cp, k -> new ChunkData());
                data.score += chunkScore;
                data.secondarySources.add(SuspicionSource.BLOCK_SCAN);
                data.surfaceY = this.computeSurfaceY(chunk);
                if (((Boolean)this.debugMode.get()).booleanValue()) {
                    ChatUtils.info((String)("[ALF] \u00a7eScan\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " +" + String.format("%.1f", chunkScore) + " total=" + String.format("%.1f", data.score)), (Object[])new Object[0]);
                }
                this.checkAndFlag(cp, data);
            }
        }
    }

    private void runLightForensics() {
        int pCx = AmethystLodeFinder.MC.field_1724.method_31476().field_9181;
        int pCz = AmethystLodeFinder.MC.field_1724.method_31476().field_9180;
        int rad = (Integer)this.scanRadius.get();
        int bottomY = AmethystLodeFinder.MC.field_1687.method_31607();
        int yMax = (Integer)this.lightYMax.get();
        int minLight = (Integer)this.lightMinLevel.get();
        for (int dx = -rad; dx <= rad; ++dx) {
            for (int dz = -rad; dz <= rad; ++dz) {
                class_2818 chunk = AmethystLodeFinder.MC.field_1687.method_8497(pCx + dx, pCz + dz);
                if (chunk == null) continue;
                class_1923 cp = chunk.method_12004();
                class_2826[] sections = chunk.method_12006();
                int litEnclosed = 0;
                for (int i = 0; i < sections.length; ++i) {
                    int secBaseY;
                    if (sections[i] == null || sections[i].method_38292() || (secBaseY = bottomY + i * 16) + 16 > yMax) continue;
                    for (int bx = 1; bx < 15; ++bx) {
                        for (int by = 1; by < 15; ++by) {
                            for (int bz = 1; bz < 15; ++bz) {
                                int worldZ;
                                int worldY;
                                int worldX;
                                class_2338 bp;
                                int light;
                                if (!sections[i].method_12254(bx, by, bz).method_26215() || (light = AmethystLodeFinder.MC.field_1687.method_8314(class_1944.field_9282, bp = new class_2338(worldX = cp.method_8326() + bx, worldY = secBaseY + by, worldZ = cp.method_8328() + bz))) < minLight || !this.areNeighborsSolid(bp)) continue;
                                ++litEnclosed;
                            }
                        }
                    }
                }
                if (litEnclosed <= 0) continue;
                double bonus = (double)litEnclosed * (Double)this.lightBonus.get();
                ChunkData data = this.chunkData.computeIfAbsent(cp, k -> new ChunkData());
                data.score += bonus;
                data.secondarySources.add(SuspicionSource.LIGHT);
                data.surfaceY = this.computeSurfaceY(chunk);
                if (((Boolean)this.debugMode.get()).booleanValue()) {
                    ChatUtils.info((String)("[ALF] \u00a7bLight\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " litBlocks=" + litEnclosed + " +" + String.format("%.1f", bonus)), (Object[])new Object[0]);
                }
                this.checkAndFlag(cp, data);
            }
        }
    }

    private boolean areNeighborsSolid(class_2338 pos) {
        return AmethystLodeFinder.MC.field_1687 == null ? false : AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10095()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10095()) && AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10072()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10072()) && AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10078()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10078()) && AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10067()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10067()) && AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10084()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10084()) && AmethystLodeFinder.MC.field_1687.method_8320(pos.method_10074()).method_26212((class_1922)AmethystLodeFinder.MC.field_1687, pos.method_10074());
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (AmethystLodeFinder.MC.field_1687 != null && AmethystLodeFinder.MC.field_1724 != null) {
            class_1923 cp;
            class_2596 cat;
            class_2767 pkt;
            class_2596 class_25962 = event.packet;
            if (class_25962 instanceof class_2767) {
                pkt = (class_2767)class_25962;
                cat = pkt.method_11888();
                if (cat != class_3419.field_15251 && cat != class_3419.field_15254) {
                    return;
                }
                double sx = pkt.method_11890();
                double sy = pkt.method_11889();
                double sz = pkt.method_11893();
                if (sy >= 0.0) {
                    return;
                }
                cp = new class_1923(new class_2338((int)sx, (int)sy, (int)sz));
                this.addSoundBonus(cp, sx, sz, cat.method_14840());
            }
            if ((cat = event.packet) instanceof class_2604) {
                double ez;
                pkt = (class_2604)cat;
                class_1299 type = pkt.method_11169();
                if (!HOSTILE_MOBS.contains(type)) {
                    return;
                }
                double ey = pkt.method_11174();
                if (ey >= 0.0) {
                    return;
                }
                double ex = pkt.method_11175();
                cp = new class_1923(new class_2338((int)ex, (int)ey, (int)(ez = pkt.method_11176())));
                ChunkData data = this.chunkData.get(cp);
                if (data != null && data.score > 0.0) {
                    double bonus = (Double)this.mobBonus.get();
                    data.score += bonus;
                    data.secondarySources.add(SuspicionSource.MOB_SPAWN);
                    if (((Boolean)this.debugMode.get()).booleanValue()) {
                        ChatUtils.info((String)("[ALF] \u00a75Mob\u00a7r " + type.method_5897().getString() + " chunk " + cp.field_9181 + "," + cp.field_9180 + " +" + String.format("%.1f", bonus)), (Object[])new Object[0]);
                    }
                    this.checkAndFlag(cp, data);
                }
            }
        }
    }

    private void addSoundBonus(class_1923 origin, double sx, double sz, String soundId) {
        double bonus = (Double)this.soundBonus.get();
        double attrR = (Double)this.soundAttrRadius.get();
        int chunkR = (int)Math.ceil(attrR / 16.0);
        ChunkData data = this.chunkData.computeIfAbsent(origin, k -> new ChunkData());
        data.score += bonus;
        data.secondarySources.add(SuspicionSource.SOUND);
        if (((Boolean)this.debugMode.get()).booleanValue()) {
            ChatUtils.info((String)("[ALF] \u00a7cSound\u00a7r " + soundId + " chunk " + origin.field_9181 + "," + origin.field_9180 + " +" + String.format("%.1f", bonus)), (Object[])new Object[0]);
        }
        this.checkAndFlag(origin, data);
        for (int ddx = -chunkR; ddx <= chunkR; ++ddx) {
            for (int ddz = -chunkR; ddz <= chunkR; ++ddz) {
                double adjCz;
                class_1923 adj;
                double adjCx;
                double dist;
                if (ddx == 0 && ddz == 0 || !((dist = Math.sqrt((sx - (adjCx = (double)(adj = new class_1923(origin.field_9181 + ddx, origin.field_9180 + ddz)).method_33940())) * (sx - adjCx) + (sz - (adjCz = (double)adj.method_33942())) * (sz - adjCz))) <= attrR)) continue;
                double adjBonus = bonus * (1.0 - dist / attrR) * 0.5;
                ChunkData adjData = this.chunkData.computeIfAbsent(adj, k -> new ChunkData());
                adjData.score += adjBonus;
                adjData.secondarySources.add(SuspicionSource.SOUND);
                this.checkAndFlag(adj, adjData);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (AmethystLodeFinder.MC.field_1724 != null && AmethystLodeFinder.MC.field_1687 != null) {
            class_243 eye = AmethystLodeFinder.MC.field_1724.method_5836(event.tickDelta);
            Color fill = (Color)this.plateColor.get();
            Color outline = (Color)this.plateOutlineColor.get();
            Color tracer = (Color)this.tracerColor.get();
            for (Map.Entry<class_1923, ChunkData> entry : this.chunkData.entrySet()) {
                ChunkData data = entry.getValue();
                if (!data.flagged) continue;
                class_1923 cp = entry.getKey();
                double plateY = (double)data.surfaceY + 0.05;
                double x0 = cp.method_8326();
                double z0 = cp.method_8328();
                double x1 = x0 + 16.0;
                double z1 = z0 + 16.0;
                event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.15, z1, fill, outline, ShapeMode.Both, 0);
                if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                double cx = cp.method_33940();
                double cz = cp.method_33942();
                event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, tracer);
            }
        }
    }

    private void checkAndFlag(class_1923 cp, ChunkData data) {
        if (!data.flagged) {
            boolean secondaryOk;
            boolean scoreOk = data.score >= (Double)this.flagThreshold.get();
            boolean bl = secondaryOk = (Boolean)this.requireSecondary.get() == false || !data.secondarySources.isEmpty();
            if (scoreOk && secondaryOk) {
                data.flagged = true;
                if (((Boolean)this.chatNotify.get()).booleanValue()) {
                    ChatUtils.info((String)("[ALF] \u00a7a\u00a7lBASE DETECTED\u00a7r chunk " + cp.field_9181 + "," + cp.field_9180 + " score=" + String.format("%.1f", data.score) + " signals=" + String.valueOf(data.secondarySources)), (Object[])new Object[0]);
                }
            }
        }
    }

    private int computeSurfaceY(class_2818 chunk) {
        if (AmethystLodeFinder.MC.field_1687 == null) {
            return 64;
        }
        int cx = chunk.method_12004().method_8326();
        int cz = chunk.method_12004().method_8328();
        int highest = AmethystLodeFinder.MC.field_1687.method_31607();
        for (int bx = 0; bx < 16; ++bx) {
            for (int bz = 0; bz < 16; ++bz) {
                int colY = chunk.method_12005(class_2902.class_2903.field_13197, bx, bz);
                if (colY <= highest) continue;
                highest = colY;
            }
        }
        return highest;
    }

    private static class ChunkData {
        double score = 0.0;
        final Set<SuspicionSource> secondarySources = ConcurrentHashMap.newKeySet();
        boolean flagged = false;
        int surfaceY = 64;

        private ChunkData() {
        }
    }

    private static enum SuspicionSource {
        BLOCK_SCAN,
        SOUND,
        LIGHT,
        MOB_SPAWN;

    }
}
