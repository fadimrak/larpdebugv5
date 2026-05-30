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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;

public class VaultScanner
extends Module {
    private static final class_310 MC = class_310.method_1551();
    private final SettingGroup sgGeneral;
    private final SettingGroup sgTargets;
    private final SettingGroup sgRender;
    private final Setting<Integer> playerProximity;
    private final Setting<Boolean> useThreading;
    private final Setting<Boolean> findAmethyst;
    private final Setting<Boolean> findSpawners;
    private final Setting<Boolean> findChests;
    private final Setting<Boolean> render;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> amethystColor;
    private final Setting<SettingColor> spawnerColor;
    private final Setting<SettingColor> chestColor;
    private final ConcurrentHashMap<class_1923, Set<SavedBlock>> database;
    private final Path savePath;
    private ExecutorService threadPool;
    private final Set<class_2248> targetBlocks;

    public VaultScanner() {
        super(DonutAddon.CATEGORY, "vault-scanner", "Passively logs and permanently maps hidden blocks using optimized palette scanning.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgTargets = this.settings.createGroup("Target Blocks");
        this.sgRender = this.settings.createGroup("Render");
        this.playerProximity = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("player-proximity")).description("Max chunks from a real player to trigger the unmasking trust.")).defaultValue((Object)8)).min(1).sliderMax(16).build());
        this.useThreading = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("async-scanning")).description("Prevents lag spikes by scanning chunks on a background thread.")).defaultValue((Object)true)).build());
        this.findAmethyst = this.sgTargets.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("amethyst")).description("Log Budding Amethyst.")).defaultValue((Object)true)).build());
        this.findSpawners = this.sgTargets.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("spawners")).description("Log Monster Spawners.")).defaultValue((Object)true)).build());
        this.findChests = this.sgTargets.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chests")).description("Log Chests, Trapped Chests, and Barrels.")).defaultValue((Object)true)).build());
        this.render = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-esp")).description("Render boxes around recorded blocks.")).defaultValue((Object)true)).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to the blocks.")).defaultValue((Object)false)).visible(() -> this.render.get())).build());
        this.amethystColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("amethyst-color")).description("Color for Amethyst.")).defaultValue(new SettingColor(160, 32, 240, 100)).visible(() -> this.findAmethyst.get())).build());
        this.spawnerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-color")).description("Color for Spawners.")).defaultValue(new SettingColor(255, 69, 0, 100)).visible(() -> this.findSpawners.get())).build());
        this.chestColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("chest-color")).description("Color for Chests.")).defaultValue(new SettingColor(255, 215, 0, 100)).visible(() -> this.findChests.get())).build());
        this.database = new ConcurrentHashMap();
        this.savePath = Paths.get("meteor-client", "vault_scanner_data.csv");
        this.targetBlocks = new HashSet<class_2248>();
    }

    public void onActivate() {
        this.threadPool = Executors.newFixedThreadPool(2);
        this.updateTargetBlocks();
        this.loadDatabase();
        this.info("Loaded " + this.countTotalBlocks() + " saved blocks across " + this.database.size() + " chunks.", new Object[0]);
    }

    public void onDeactivate() {
        this.saveDatabase();
        if (this.threadPool != null && !this.threadPool.isShutdown()) {
            this.threadPool.shutdown();
        }
        this.info("Saved database to disk.", new Object[0]);
    }

    private void updateTargetBlocks() {
        this.targetBlocks.clear();
        if (((Boolean)this.findAmethyst.get()).booleanValue()) {
            this.targetBlocks.add(class_2246.field_27160);
        }
        if (((Boolean)this.findSpawners.get()).booleanValue()) {
            this.targetBlocks.add(class_2246.field_10260);
        }
        if (((Boolean)this.findChests.get()).booleanValue()) {
            this.targetBlocks.add(class_2246.field_10034);
            this.targetBlocks.add(class_2246.field_10380);
            this.targetBlocks.add(class_2246.field_16328);
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (VaultScanner.MC.field_1687 == null || VaultScanner.MC.field_1724 == null) {
            return;
        }
        class_2818 chunk = event.chunk();
        class_1923 chunkPos = chunk.method_12004();
        this.updateTargetBlocks();
        if (this.targetBlocks.isEmpty() || !this.isPlayerNearby(chunkPos)) {
            return;
        }
        if (((Boolean)this.useThreading.get()).booleanValue()) {
            this.threadPool.execute(() -> this.scanChunk(chunk, chunkPos));
        } else {
            this.scanChunk(chunk, chunkPos);
        }
    }

    private boolean isPlayerNearby(class_1923 chunkPos) {
        boolean nearby = VaultScanner.MC.field_1687.method_18456().stream().filter(p -> p != VaultScanner.MC.field_1724).anyMatch(p -> p.method_31476().method_24022(chunkPos) <= (Integer)this.playerProximity.get());
        if (!nearby && !VaultScanner.MC.field_1724.method_7325()) {
            nearby = VaultScanner.MC.field_1724.method_31476().method_24022(chunkPos) <= (Integer)this.playerProximity.get();
        }
        return nearby;
    }

    private void scanChunk(class_2818 chunk, class_1923 chunkPos) {
        HashSet<SavedBlock> foundBlocks = new HashSet<SavedBlock>();
        class_2826[] sections = chunk.method_12006();
        int bottomY = VaultScanner.MC.field_1687.method_31607();
        int startX = chunkPos.method_8326();
        int startZ = chunkPos.method_8328();
        for (int i = 0; i < sections.length; ++i) {
            class_2826 section = sections[i];
            if (section == null || section.method_38292() || !section.method_12265().method_19526(s -> this.targetBlocks.contains(s.method_26204()))) continue;
            int sectionBaseY = bottomY + i * 16;
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        class_2680 state = section.method_12254(x, y, z);
                        class_2248 block = state.method_26204();
                        if (!this.targetBlocks.contains(block)) continue;
                        class_2338 pos = new class_2338(startX + x, sectionBaseY + y, startZ + z);
                        foundBlocks.add(new SavedBlock(pos, this.getBlockIdentifier(block)));
                    }
                }
            }
        }
        if (!foundBlocks.isEmpty()) {
            this.database.merge(chunkPos, foundBlocks, (oldSet, newSet) -> {
                ConcurrentHashMap.KeySetView merged = ConcurrentHashMap.newKeySet();
                if (oldSet != null) {
                    merged.addAll(oldSet);
                }
                merged.addAll(newSet);
                return merged;
            });
            this.saveDatabase();
        }
    }

    private String getBlockIdentifier(class_2248 block) {
        if (block == class_2246.field_27160) {
            return "AMETHYST";
        }
        if (block == class_2246.field_10260) {
            return "SPAWNER";
        }
        if (block == class_2246.field_10034 || block == class_2246.field_10380 || block == class_2246.field_16328) {
            return "CHEST";
        }
        return "UNKNOWN";
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.render.get()).booleanValue() || this.database.isEmpty() || VaultScanner.MC.field_1724 == null) {
            return;
        }
        for (Set<SavedBlock> blocksInChunk : this.database.values()) {
            for (SavedBlock block : blocksInChunk) {
                Color c = this.getColorForType(block.type);
                if (c == null) continue;
                double x = block.pos.method_10263();
                double y = block.pos.method_10264();
                double z = block.pos.method_10260();
                event.renderer.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, c, c, ShapeMode.Lines, 0);
                if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
                event.renderer.line(event.offsetX, event.offsetY, event.offsetZ, x + 0.5, y + 0.5, z + 0.5, c);
            }
        }
    }

    private Color getColorForType(String type) {
        return switch (type) {
            case "AMETHYST" -> {
                if (((Boolean)this.findAmethyst.get()).booleanValue()) {
                    yield (Color)this.amethystColor.get();
                }
                yield null;
            }
            case "SPAWNER" -> {
                if (((Boolean)this.findSpawners.get()).booleanValue()) {
                    yield (Color)this.spawnerColor.get();
                }
                yield null;
            }
            case "CHEST" -> {
                if (((Boolean)this.findChests.get()).booleanValue()) {
                    yield (Color)this.chestColor.get();
                }
                yield null;
            }
            default -> null;
        };
    }

    private void loadDatabase() {
        block13: {
            if (!Files.exists(this.savePath, new LinkOption[0])) {
                return;
            }
            try {
                BufferedReader r = Files.newBufferedReader(this.savePath);
                block9: while (true) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length != 4) continue;
                        try {
                            int x = Integer.parseInt(parts[0].trim());
                            int y = Integer.parseInt(parts[1].trim());
                            int z = Integer.parseInt(parts[2].trim());
                            String type = parts[3].trim();
                            class_2338 pos = new class_2338(x, y, z);
                            class_1923 cPos = new class_1923(pos);
                            this.database.computeIfAbsent(cPos, k -> ConcurrentHashMap.newKeySet()).add(new SavedBlock(pos, type));
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
            catch (IOException e) {
                this.error("Failed to load vault database.", new Object[0]);
            }
        }
    }

    private synchronized void saveDatabase() {
        try {
            Files.createDirectories(this.savePath.getParent(), new FileAttribute[0]);
            try (BufferedWriter w = Files.newBufferedWriter(this.savePath, new OpenOption[0]);){
                for (Set<SavedBlock> blocks : this.database.values()) {
                    for (SavedBlock b : blocks) {
                        w.write(b.pos.method_10263() + "," + b.pos.method_10264() + "," + b.pos.method_10260() + "," + b.type);
                        w.newLine();
                    }
                }
            }
        }
        catch (IOException e) {
            this.error("Failed to save vault database.", new Object[0]);
        }
    }

    private int countTotalBlocks() {
        return this.database.values().stream().mapToInt(Set::size).sum();
    }

    private static class SavedBlock {
        public final class_2338 pos;
        public final String type;

        SavedBlock(class_2338 pos, String type) {
            this.pos = pos;
            this.type = type;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SavedBlock)) {
                return false;
            }
            SavedBlock that = (SavedBlock)o;
            return this.pos.equals((Object)that.pos) && this.type.equals(that.type);
        }

        public int hashCode() {
            return this.pos.hashCode();
        }
    }
}
