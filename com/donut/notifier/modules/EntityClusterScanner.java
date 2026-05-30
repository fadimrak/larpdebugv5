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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1299;
import net.minecraft.class_1311;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2596;
import net.minecraft.class_2604;

public class EntityClusterScanner
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Integer> clusterRadius;
    private final Setting<Integer> minSpawns;
    private final Setting<Boolean> trackPlayers;
    private final Setting<Boolean> trackHostile;
    private final Setting<Boolean> trackPassive;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> colorLow;
    private final Setting<SettingColor> colorHigh;
    private final Setting<Double> renderY;
    private final Map<class_1923, SpawnRecord> spawnMap;
    private final List<Cluster> activeClusters;
    private final Path savePath;
    private Instant lastClusterUpdate;

    public EntityClusterScanner() {
        super(DonutAddon.CATEGORY, "entity-cluster-scanner", "Clusters entity spawn locations over time to find spawner farms.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.clusterRadius = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("cluster-radius")).description("Chunk radius to group spawns into clusters.")).defaultValue((Object)2)).range(1, 8).sliderMax(8).build());
        this.minSpawns = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-spawns")).description("Minimum spawns in a cluster to render.")).defaultValue((Object)3)).range(2, 50).sliderMax(30).build());
        this.trackPlayers = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("track-players")).description("Track player entity spawns (login/teleport events).")).defaultValue((Object)true)).build());
        this.trackHostile = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("track-hostile")).description("Track hostile mob spawns.")).defaultValue((Object)true)).build());
        this.trackPassive = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("track-passive")).description("Track passive mob spawns (DonutSMP may spawn custom passive mobs from spawners).")).defaultValue((Object)true)).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render cluster boxes.")).defaultValue((Object)true)).build());
        this.colorLow = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("color-low")).description("Color for low-density clusters.")).defaultValue(new SettingColor(255, 50, 50, 80)).visible(() -> this.renderEnabled.get())).build());
        this.colorHigh = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("color-high")).description("Color for high-density clusters.")).defaultValue(new SettingColor(255, 200, 0, 160)).visible(() -> this.renderEnabled.get())).build());
        this.renderY = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("render-y")).description("Y coordinate to render cluster plates at.")).defaultValue(64.0).sliderRange(-64.0, 320.0).visible(() -> this.renderEnabled.get())).build());
        this.spawnMap = new ConcurrentHashMap<class_1923, SpawnRecord>();
        this.activeClusters = Collections.synchronizedList(new ArrayList());
        this.savePath = Paths.get("meteor-client", "entity_clusters.txt");
        this.lastClusterUpdate = Instant.now();
    }

    public void onActivate() {
        this.load();
        this.info("Loaded " + this.spawnMap.size() + " spawn records.", new Object[0]);
    }

    public void onDeactivate() {
        this.save();
        this.activeClusters.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2604) {
            class_2604 pkt = (class_2604)class_25962;
            if (this.mc.field_1687 != null && this.mc.field_1724 != null) {
                class_1299 type = pkt.method_11169();
                double x = pkt.method_11175();
                double y = pkt.method_11174();
                double z = pkt.method_11176();
                class_1923 cp = new class_1923(class_2338.method_49637((double)x, (double)y, (double)z));
                boolean record = false;
                String label = type.method_35050();
                if (((Boolean)this.trackPlayers.get()).booleanValue() && type == class_1299.field_6097) {
                    record = true;
                }
                if (((Boolean)this.trackHostile.get()).booleanValue() && type.method_5891() == class_1311.field_6302) {
                    record = true;
                }
                if (((Boolean)this.trackPassive.get()).booleanValue() && type.method_5891() == class_1311.field_6294) {
                    record = true;
                }
                if (record) {
                    this.spawnMap.computeIfAbsent(cp, SpawnRecord::new).addSpawn(label);
                    if (Duration.between(this.lastClusterUpdate, Instant.now()).getSeconds() >= 5L) {
                        this.updateClusters();
                        this.lastClusterUpdate = Instant.now();
                    }
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void updateClusters() {
        ArrayList<class_1923> allChunks = new ArrayList<class_1923>(this.spawnMap.keySet());
        HashSet<class_1923> visited = new HashSet<class_1923>();
        ArrayList<Cluster> newClusters = new ArrayList<Cluster>();
        for (class_1923 start : allChunks) {
            if (visited.contains(start)) continue;
            ArrayList<class_1923> clusterChunks = new ArrayList<class_1923>();
            ArrayDeque<class_1923> queue = new ArrayDeque<class_1923>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                class_1923 curr = (class_1923)queue.poll();
                clusterChunks.add(curr);
                int r = (Integer)this.clusterRadius.get();
                for (int dx = -r; dx <= r; ++dx) {
                    for (int dz = -r; dz <= r; ++dz) {
                        class_1923 nb = new class_1923(curr.field_9181 + dx, curr.field_9180 + dz);
                        if (!this.spawnMap.containsKey(nb) || visited.contains(nb)) continue;
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }
            int totalSpawns = 0;
            HashSet<String> types = new HashSet<String>();
            for (class_1923 cp : clusterChunks) {
                SpawnRecord sr = this.spawnMap.get(cp);
                totalSpawns += sr.spawnCount;
                types.addAll(sr.entityTypes);
            }
            if (totalSpawns < (Integer)this.minSpawns.get()) continue;
            double sumX = 0.0;
            double sumZ = 0.0;
            for (class_1923 cp : clusterChunks) {
                sumX += (double)cp.method_33940();
                sumZ += (double)cp.method_33942();
            }
            newClusters.add(new Cluster(sumX / (double)clusterChunks.size(), sumZ / (double)clusterChunks.size(), clusterChunks.size(), totalSpawns, types.size()));
        }
        newClusters.sort((a, b) -> Integer.compare(b.totalSpawns, a.totalSpawns));
        List<Cluster> list = this.activeClusters;
        synchronized (list) {
            this.activeClusters.clear();
            this.activeClusters.addAll(newClusters);
        }
        if (!newClusters.isEmpty()) {
            this.info("\u00a7e" + newClusters.size() + "\u00a77 clusters \u2014 top: \u00a7f" + ((Cluster)newClusters.get((int)0)).totalSpawns + " spawns", new Object[0]);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (((Boolean)this.renderEnabled.get()).booleanValue()) {
            double rY = (Double)this.renderY.get();
            List<Cluster> list = this.activeClusters;
            synchronized (list) {
                for (Cluster c : this.activeClusters) {
                    float ratio = Math.min(1.0f, (float)c.totalSpawns / 50.0f);
                    SettingColor lo = (SettingColor)this.colorLow.get();
                    SettingColor hi = (SettingColor)this.colorHigh.get();
                    SettingColor col = new SettingColor((int)((float)lo.r + (float)(hi.r - lo.r) * ratio), (int)((float)lo.g + (float)(hi.g - lo.g) * ratio), (int)((float)lo.b + (float)(hi.b - lo.b) * ratio), (int)((float)lo.a + (float)(hi.a - lo.a) * ratio));
                    double size = (double)c.chunkCount * 8.0;
                    event.renderer.box(c.x - size, rY, c.z - size, c.x + size, rY + 0.3, c.z + size, (Color)col, (Color)col, ShapeMode.Both, 0);
                }
            }
        }
    }

    private void load() {
        if (Files.exists(this.savePath, new LinkOption[0])) {
            try (BufferedReader r = Files.newBufferedReader(this.savePath);){
                String line;
                while ((line = r.readLine()) != null) {
                    SpawnRecord sr = SpawnRecord.fromString(line);
                    if (sr == null) continue;
                    this.spawnMap.put(sr.pos, sr);
                }
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(this.savePath.getParent(), new FileAttribute[0]);
            try (BufferedWriter w = Files.newBufferedWriter(this.savePath, new OpenOption[0]);){
                for (SpawnRecord sr : this.spawnMap.values()) {
                    w.write(sr.toString());
                    w.newLine();
                }
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private static class SpawnRecord {
        final class_1923 pos;
        int spawnCount;
        Instant firstSeen;
        Instant lastSeen;
        final Set<String> entityTypes = new HashSet<String>();

        SpawnRecord(class_1923 pos) {
            this.pos = pos;
            this.firstSeen = Instant.now();
            this.lastSeen = Instant.now();
            this.spawnCount = 1;
        }

        void addSpawn(String type) {
            ++this.spawnCount;
            this.lastSeen = Instant.now();
            this.entityTypes.add(type);
        }

        public String toString() {
            return this.pos.field_9181 + "," + this.pos.field_9180 + "," + this.spawnCount + "," + String.valueOf(this.firstSeen) + "," + String.valueOf(this.lastSeen) + "," + String.join((CharSequence)";", this.entityTypes);
        }

        static SpawnRecord fromString(String s) {
            String[] p = s.split(",", 6);
            if (p.length < 6) {
                return null;
            }
            try {
                SpawnRecord r = new SpawnRecord(new class_1923(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                r.spawnCount = Integer.parseInt(p[2].trim());
                r.firstSeen = Instant.parse(p[3].trim());
                r.lastSeen = Instant.parse(p[4].trim());
                Collections.addAll(r.entityTypes, p[5].split(";"));
                return r;
            }
            catch (Exception var3) {
                return null;
            }
        }
    }

    private static class Cluster {
        final double x;
        final double z;
        final int chunkCount;
        final int totalSpawns;
        final int uniqueTypes;

        Cluster(double x, double z, int chunks, int spawns, int types) {
            this.x = x;
            this.z = z;
            this.chunkCount = chunks;
            this.totalSpawns = spawns;
            this.uniqueTypes = types;
        }
    }
}
