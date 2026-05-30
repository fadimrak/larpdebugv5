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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2596;
import net.minecraft.class_2960;
import net.minecraft.class_3917;
import net.minecraft.class_3944;
import net.minecraft.class_7923;

public class SpawnerHistory
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Integer> historyDays;
    private final Setting<Boolean> autoSave;
    private final Setting<Boolean> chatLog;
    private final Setting<Boolean> renderEnabled;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideColor;
    private final Setting<SettingColor> lineColor;
    private final Setting<Double> renderY;
    private final Map<class_1923, Instant> spawnerChunks;
    private final Path savePath;
    private Instant lastCleanup;

    public SpawnerHistory() {
        super(DonutAddon.CATEGORY, "spawner-history", "Logs and displays chunks where spawner screens have been opened.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.historyDays = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("history-days")).description("How many days of spawner history to keep and display.")).defaultValue((Object)2)).range(1, 30).sliderMax(30).build());
        this.autoSave = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("auto-save")).description("Automatically save history to file.")).defaultValue((Object)true)).build());
        this.chatLog = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-log")).description("Print to chat when a new spawner location is logged.")).defaultValue((Object)true)).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render a plate over chunks where spawners have been opened.")).defaultValue((Object)true)).build());
        this.shapeMode = this.sgRender.add((Setting)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode")).description("How the chunk highlight is drawn.")).defaultValue((Object)ShapeMode.Both)).visible(() -> this.renderEnabled.get())).build());
        this.sideColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("side-color")).description("Fill color of the chunk highlight.")).defaultValue(new SettingColor(0, 255, 0, 25)).visible(() -> this.renderEnabled.get())).build());
        this.lineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-color")).description("Outline color of the chunk highlight.")).defaultValue(new SettingColor(0, 255, 0, 125)).visible(() -> this.renderEnabled.get())).build());
        this.renderY = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("render-y")).description("Y level to render the plate at.")).defaultValue(64.0).sliderRange(-64.0, 320.0).visible(() -> this.renderEnabled.get())).build());
        this.spawnerChunks = new ConcurrentHashMap<class_1923, Instant>();
        this.savePath = Paths.get("meteor-client", "spawner_history.txt");
        this.lastCleanup = Instant.now();
    }

    public void onActivate() {
        this.loadHistory();
        this.cleanupOldEntries();
        this.info("SpawnerHistory loaded: " + this.spawnerChunks.size() + " chunk(s).", new Object[0]);
    }

    public void onDeactivate() {
        if (((Boolean)this.autoSave.get()).booleanValue()) {
            this.saveHistory();
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        class_2596 class_25962 = event.packet;
        if (!(class_25962 instanceof class_3944)) {
            return;
        }
        class_3944 packet = (class_3944)class_25962;
        if (this.mc.field_1724 == null) {
            return;
        }
        class_3917 screenHandlerType = packet.method_17593();
        if (screenHandlerType == null) {
            return;
        }
        class_2960 typeId = class_7923.field_41187.method_10221((Object)screenHandlerType);
        if (typeId == null || !typeId.method_12832().contains("spawner")) {
            return;
        }
        class_1923 cp = this.mc.field_1724.method_31476();
        Instant existing = this.spawnerChunks.put(cp, Instant.now());
        if (existing == null) {
            if (((Boolean)this.chatLog.get()).booleanValue()) {
                this.info("Spawner logged at chunk " + cp.field_9181 + ", " + cp.field_9180, new Object[0]);
            }
            if (((Boolean)this.autoSave.get()).booleanValue()) {
                this.saveHistory();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue()) {
            return;
        }
        if (Duration.between(this.lastCleanup, Instant.now()).toHours() >= 1L) {
            this.cleanupOldEntries();
            this.lastCleanup = Instant.now();
        }
        double y = (Double)this.renderY.get();
        for (Map.Entry<class_1923, Instant> entry : this.spawnerChunks.entrySet()) {
            class_1923 cp = entry.getKey();
            if (Duration.between(entry.getValue(), Instant.now()).toDays() >= (long)((Integer)this.historyDays.get()).intValue()) continue;
            double x0 = cp.method_8326();
            double z0 = cp.method_8328();
            event.renderer.box(x0, y, z0, x0 + 16.0, y + 0.1, z0 + 16.0, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
        }
    }

    public void sendInfo() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(((Integer)this.historyDays.get()).intValue()));
        int count = 0;
        this.info("Spawner chunks (last " + String.valueOf(this.historyDays.get()) + " days):", new Object[0]);
        for (Map.Entry<class_1923, Instant> entry : this.spawnerChunks.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) continue;
            class_1923 cp = entry.getKey();
            this.info("  Chunk [" + cp.field_9181 + ", " + cp.field_9180 + "]", new Object[0]);
            ++count;
        }
        this.info("Total: " + count, new Object[0]);
    }

    private void loadHistory() {
        block13: {
            if (!Files.exists(this.savePath, new LinkOption[0])) {
                return;
            }
            this.spawnerChunks.clear();
            try {
                BufferedReader reader = Files.newBufferedReader(this.savePath);
                block9: while (true) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length < 3) continue;
                        try {
                            int x = Integer.parseInt(parts[0].trim());
                            int z = Integer.parseInt(parts[1].trim());
                            Instant ts = Instant.parse(parts[2].trim());
                            this.spawnerChunks.put(new class_1923(x, z), ts);
                            continue block9;
                        }
                        catch (Exception exception) {
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
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
            catch (IOException e) {
                this.error("Failed to load spawner history: " + e.getMessage(), new Object[0]);
            }
        }
    }

    private void saveHistory() {
        try {
            Files.createDirectories(this.savePath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.savePath, new OpenOption[0]);){
                for (Map.Entry<class_1923, Instant> entry : this.spawnerChunks.entrySet()) {
                    class_1923 cp = entry.getKey();
                    writer.write(cp.field_9181 + "," + cp.field_9180 + "," + String.valueOf(entry.getValue()));
                    writer.newLine();
                }
            }
        }
        catch (IOException e) {
            this.error("Failed to save spawner history: " + e.getMessage(), new Object[0]);
        }
    }

    private void cleanupOldEntries() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(((Integer)this.historyDays.get()).intValue()));
        this.spawnerChunks.entrySet().removeIf(e -> ((Instant)e.getValue()).isBefore(cutoff));
        if (((Boolean)this.autoSave.get()).booleanValue()) {
            this.saveHistory();
        }
    }
}
