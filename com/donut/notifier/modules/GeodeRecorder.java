package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2382;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;
import org.joml.Vector3d;

public class GeodeRecorder
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final SettingGroup sgScanning;
    private final SettingGroup sgWaypoints;
    private final SettingGroup sgRender;
    private final SettingGroup sgText;
    private final SettingGroup sgTracers;
    private final Setting<Integer> playerProximity;
    private final Setting<Boolean> useThreading;
    private final Setting<Integer> minBuddingForGeode;
    private final Setting<Boolean> autoWaypoint;
    private final Setting<Boolean> renderClusters;
    private final Setting<Boolean> renderBlocks;
    private final Setting<SettingColor> clusterColor;
    private final Setting<SettingColor> blockColor;
    private final Setting<Boolean> renderText;
    private final Setting<Double> textScale;
    private final Setting<SettingColor> textColor;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerColor;
    private final ConcurrentHashMap<class_1923, Set<class_2338>> rawBuddingBlocks;
    private final List<GeodeCluster> geodeClusters;
    private boolean clustersNeedRebuild;
    private final Path savePath;
    private ExecutorService threadPool;
    private final Set<Integer> notifiedClusterHashes;

    public GeodeRecorder() {
        super(DonutAddon.CATEGORY, "geode-recorder", "Advanced Geode logger. Maps, clusters, and saves Budding Amethyst.");
        this.sgScanning = this.settings.createGroup("Scanning");
        this.sgWaypoints = this.settings.createGroup("Waypoints");
        this.sgRender = this.settings.createGroup("Render - ESP");
        this.sgText = this.settings.createGroup("Render - Text");
        this.sgTracers = this.settings.createGroup("Render - Tracers");
        this.playerProximity = this.sgScanning.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("player-proximity")).description("Max chunks from a real player to trigger the unmasking trust.")).defaultValue((Object)6)).min(1).sliderMax(16).build());
        this.useThreading = this.sgScanning.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("async-scanning")).description("Prevents lag spikes by scanning chunks on a background thread.")).defaultValue((Object)true)).build());
        this.minBuddingForGeode = this.sgScanning.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-budding-blocks")).description("Minimum number of budding amethyst blocks to be considered a real geode.")).defaultValue((Object)10)).min(1).sliderMax(50).build());
        this.autoWaypoint = this.sgWaypoints.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("auto-waypoint")).description("Automatically create Meteor waypoints for newly discovered geodes.")).defaultValue((Object)false)).build());
        this.renderClusters = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-clusters")).description("Draw a large bounding box around the entire geode.")).defaultValue((Object)true)).build());
        this.renderBlocks = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-budding-blocks")).description("Draw boxes around individual budding amethyst blocks.")).defaultValue((Object)false)).build());
        this.clusterColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("cluster-color")).description("Color for the geode bounding box.")).defaultValue(new SettingColor(180, 50, 255, 60)).build());
        this.blockColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("block-color")).description("Color for individual budding blocks.")).defaultValue(new SettingColor(200, 100, 255, 120)).build());
        this.renderText = this.sgText.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-nametags")).description("Show floating text with geode stats.")).defaultValue((Object)true)).build());
        this.textScale = this.sgText.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("text-scale")).description("Scale of the floating text.")).defaultValue(1.5).min(0.5).sliderMax(3.0).visible(() -> this.renderText.get())).build());
        this.textColor = this.sgText.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("text-color")).description("Color of the text.")).defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> this.renderText.get())).build());
        this.showTracers = this.sgTracers.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to geode centers.")).defaultValue((Object)true)).build());
        this.tracerColor = this.sgTracers.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).description("Color for tracers.")).defaultValue(new SettingColor(180, 50, 255, 200)).visible(() -> this.showTracers.get())).build());
        this.rawBuddingBlocks = new ConcurrentHashMap();
        this.geodeClusters = new ArrayList<GeodeCluster>();
        this.clustersNeedRebuild = false;
        this.savePath = Paths.get("meteor-client", "geode_database.csv");
        this.notifiedClusterHashes = new HashSet<Integer>();
    }

    public void onActivate() {
        this.threadPool = Executors.newFixedThreadPool(2);
        this.rawBuddingBlocks.clear();
        this.geodeClusters.clear();
        this.clustersNeedRebuild = false;
        this.loadDatabase();
        this.rebuildClusters();
        int totalBlocks = this.rawBuddingBlocks.values().stream().mapToInt(Set::size).sum();
        this.info("Loaded " + this.geodeClusters.size() + " Geodes containing " + totalBlocks + " budding blocks.", new Object[0]);
    }

    public void onDeactivate() {
        this.saveDatabase();
        if (this.threadPool != null && !this.threadPool.isShutdown()) {
            this.threadPool.shutdown();
        }
        this.info("Saved Geode database.", new Object[0]);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        class_2818 chunk;
        class_1923 chunkPos;
        if (GeodeRecorder.MC.field_1687 != null && GeodeRecorder.MC.field_1724 != null && this.isPlayerNearby(chunkPos = (chunk = event.chunk()).method_12004())) {
            if (((Boolean)this.useThreading.get()).booleanValue()) {
                this.threadPool.execute(() -> this.scanChunk(chunk, chunkPos));
            } else {
                this.scanChunk(chunk, chunkPos);
            }
        }
    }

    private boolean isPlayerNearby(class_1923 chunkPos) {
        boolean nearby = GeodeRecorder.MC.field_1687.method_18456().stream().filter(p -> p != GeodeRecorder.MC.field_1724).anyMatch(p -> p.method_31476().method_24022(chunkPos) <= (Integer)this.playerProximity.get());
        if (!nearby && !GeodeRecorder.MC.field_1724.method_7325()) {
            nearby = GeodeRecorder.MC.field_1724.method_31476().method_24022(chunkPos) <= (Integer)this.playerProximity.get();
        }
        return nearby;
    }

    private void scanChunk(class_2818 chunk, class_1923 chunkPos) {
        HashSet<class_2338> newlyFound = new HashSet<class_2338>();
        class_2826[] sections = chunk.method_12006();
        int bottomY = GeodeRecorder.MC.field_1687.method_31607();
        int startX = chunkPos.method_8326();
        int startZ = chunkPos.method_8328();
        for (int i = 0; i < sections.length; ++i) {
            boolean hasTarget;
            class_2826 section = sections[i];
            if (section == null || section.method_38292() || !(hasTarget = section.method_12265().method_19526(statex -> statex.method_27852(class_2246.field_27160)))) continue;
            int sectionBaseY = bottomY + i * 16;
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        class_2680 state = section.method_12254(x, y, z);
                        class_2248 block = state.method_26204();
                        if (block != class_2246.field_27160) continue;
                        class_2338 pos = new class_2338(startX + x, sectionBaseY + y, startZ + z);
                        newlyFound.add(pos);
                    }
                }
            }
        }
        if (!newlyFound.isEmpty()) {
            boolean isNewData = false;
            Set existing = this.rawBuddingBlocks.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet());
            for (class_2338 pos : newlyFound) {
                if (!existing.add(pos)) continue;
                isNewData = true;
            }
            if (isNewData) {
                this.clustersNeedRebuild = true;
                this.saveDatabase();
                MC.execute(this::rebuildClusters);
            }
        }
    }

    private synchronized void rebuildClusters() {
        if (this.clustersNeedRebuild) {
            this.clustersNeedRebuild = false;
            ArrayList<class_2338> allBlocks = new ArrayList<class_2338>();
            for (Set<class_2338> chunkBlocks : this.rawBuddingBlocks.values()) {
                allBlocks.addAll(chunkBlocks);
            }
            ArrayList<GeodeCluster> newClusters = new ArrayList<GeodeCluster>();
            HashSet<class_2338> visited = new HashSet<class_2338>();
            for (class_2338 startBlock : allBlocks) {
                if (visited.contains(startBlock)) continue;
                ArrayList<class_2338> currentClusterBlocks = new ArrayList<class_2338>();
                LinkedList<class_2338> queue = new LinkedList<class_2338>();
                queue.add(startBlock);
                visited.add(startBlock);
                while (!queue.isEmpty()) {
                    class_2338 current = (class_2338)queue.poll();
                    currentClusterBlocks.add(current);
                    for (class_2338 other : allBlocks) {
                        if (visited.contains(other) || !(current.method_10262((class_2382)other) <= 576.0)) continue;
                        visited.add(other);
                        queue.add(other);
                    }
                }
                if (currentClusterBlocks.size() < (Integer)this.minBuddingForGeode.get()) continue;
                GeodeCluster cluster = new GeodeCluster(currentClusterBlocks);
                newClusters.add(cluster);
                int hash = cluster.hashCode();
                if (this.notifiedClusterHashes.contains(hash)) continue;
                this.notifiedClusterHashes.add(hash);
                this.onNewGeodeFound(cluster);
            }
            this.geodeClusters.clear();
            this.geodeClusters.addAll(newClusters);
        }
    }

    private void onNewGeodeFound(GeodeCluster cluster) {
        String msg = String.format("Found Geode with %d Budding Amethyst at [%d, %d, %d]", cluster.blocks.size(), (int)cluster.center.field_1352, (int)cluster.center.field_1351, (int)cluster.center.field_1350);
        this.info(msg, new Object[0]);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (GeodeRecorder.MC.field_1724 != null) {
            if (((Boolean)this.renderBlocks.get()).booleanValue()) {
                for (Set set : this.rawBuddingBlocks.values()) {
                    for (class_2338 pos : set) {
                        event.renderer.box((double)pos.method_10263(), (double)pos.method_10264(), (double)pos.method_10260(), (double)(pos.method_10263() + 1), (double)(pos.method_10264() + 1), (double)(pos.method_10260() + 1), (Color)this.blockColor.get(), (Color)this.blockColor.get(), ShapeMode.Lines, 0);
                    }
                }
            }
            for (GeodeCluster geodeCluster : this.geodeClusters) {
                if (((Boolean)this.renderClusters.get()).booleanValue()) {
                    event.renderer.box((double)geodeCluster.minX, (double)geodeCluster.minY, (double)geodeCluster.minZ, (double)(geodeCluster.maxX + 1), (double)(geodeCluster.maxY + 1), (double)(geodeCluster.maxZ + 1), (Color)this.clusterColor.get(), (Color)this.clusterColor.get(), ShapeMode.Lines, 0);
                }
                if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                event.renderer.line(event.offsetX, event.offsetY, event.offsetZ, geodeCluster.center.field_1352, geodeCluster.center.field_1351, geodeCluster.center.field_1350, (Color)this.tracerColor.get());
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (((Boolean)this.renderText.get()).booleanValue() && GeodeRecorder.MC.field_1724 != null) {
            for (GeodeCluster cluster : this.geodeClusters) {
                class_243 pos = cluster.center.method_1031(0.0, (double)cluster.maxY - cluster.center.field_1351 + 1.5, 0.0);
                Vector3d v3d = new Vector3d(pos.field_1352, pos.field_1351, pos.field_1350);
                if (!NametagUtils.to2D((Vector3d)v3d, (double)((Double)this.textScale.get()))) continue;
                NametagUtils.begin((Vector3d)v3d);
                TextRenderer.get().begin(1.0, false, true);
                String text = "Geode";
                String yield = cluster.blocks.size() + " Budding Blocks";
                double w1 = TextRenderer.get().getWidth(text);
                double w2 = TextRenderer.get().getWidth(yield);
                TextRenderer.get().render(text, -w1 / 2.0, -10.0, (Color)this.textColor.get(), true);
                TextRenderer.get().render(yield, -w2 / 2.0, 0.0, new Color(200, 150, 255), true);
                TextRenderer.get().end();
                NametagUtils.end();
            }
        }
    }

    private void loadDatabase() {
        block13: {
            if (Files.exists(this.savePath, new LinkOption[0])) {
                try {
                    BufferedReader r = Files.newBufferedReader(this.savePath);
                    block9: while (true) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            String[] parts = line.split(",");
                            if (parts.length != 3) continue;
                            try {
                                int x = Integer.parseInt(parts[0].trim());
                                int y = Integer.parseInt(parts[1].trim());
                                int z = Integer.parseInt(parts[2].trim());
                                class_2338 pos = new class_2338(x, y, z);
                                class_1923 cPos = new class_1923(pos);
                                this.rawBuddingBlocks.computeIfAbsent(cPos, k -> ConcurrentHashMap.newKeySet()).add(pos);
                                continue block9;
                            }
                            catch (NumberFormatException numberFormatException) {
                            }
                        }
                        break block13;
                        {
                            continue block9;
                            break;
                        }
                        break;
                    }
                    finally {
                        if (r != null) {
                            r.close();
                        }
                    }
                }
                catch (IOException var12) {
                    this.error("Failed to load Geode database.", new Object[0]);
                }
            }
        }
    }

    private synchronized void saveDatabase() {
        try {
            Files.createDirectories(this.savePath.getParent(), new FileAttribute[0]);
            try (BufferedWriter w = Files.newBufferedWriter(this.savePath, new OpenOption[0]);){
                for (Set<class_2338> blocks : this.rawBuddingBlocks.values()) {
                    for (class_2338 pos : blocks) {
                        w.write(pos.method_10263() + "," + pos.method_10264() + "," + pos.method_10260());
                        w.newLine();
                    }
                }
            }
        }
        catch (IOException var8) {
            this.error("Failed to save Geode database.", new Object[0]);
        }
    }

    private static class GeodeCluster {
        public final List<class_2338> blocks;
        public final class_243 center;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;

        public GeodeCluster(List<class_2338> blocks) {
            this.blocks = new ArrayList<class_2338>(blocks);
            long sumX = 0L;
            long sumY = 0L;
            long sumZ = 0L;
            int miX = Integer.MAX_VALUE;
            int miY = Integer.MAX_VALUE;
            int miZ = Integer.MAX_VALUE;
            int maX = Integer.MIN_VALUE;
            int maY = Integer.MIN_VALUE;
            int maZ = Integer.MIN_VALUE;
            for (class_2338 p : blocks) {
                sumX += (long)p.method_10263();
                sumY += (long)p.method_10264();
                sumZ += (long)p.method_10260();
                if (p.method_10263() < miX) {
                    miX = p.method_10263();
                }
                if (p.method_10264() < miY) {
                    miY = p.method_10264();
                }
                if (p.method_10260() < miZ) {
                    miZ = p.method_10260();
                }
                if (p.method_10263() > maX) {
                    maX = p.method_10263();
                }
                if (p.method_10264() > maY) {
                    maY = p.method_10264();
                }
                if (p.method_10260() <= maZ) continue;
                maZ = p.method_10260();
            }
            this.minX = miX;
            this.minY = miY;
            this.minZ = miZ;
            this.maxX = maX;
            this.maxY = maY;
            this.maxZ = maZ;
            this.center = new class_243((double)sumX / (double)blocks.size() + 0.5, (double)sumY / (double)blocks.size() + 0.5, (double)sumZ / (double)blocks.size() + 0.5);
        }

        public int hashCode() {
            return Objects.hash((int)this.center.field_1352, (int)this.center.field_1351, (int)this.center.field_1350, this.blocks.size());
        }
    }
}
