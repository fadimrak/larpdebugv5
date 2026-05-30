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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_243;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_742;

public class PlayerSightingHeatmap
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgScan;
    private final SettingGroup sgTime;
    private final SettingGroup sgRender;
    private final Setting<Boolean> autoSave;
    private final Setting<Boolean> chatNotify;
    private final Setting<Integer> confirmThreshold;
    private final Setting<Integer> scanRadius;
    private final Setting<Integer> scanBelowY;
    private final Setting<Integer> scorePerOre;
    private final Setting<Integer> scorePerSpawner;
    private final Setting<Integer> minOreScore;
    private final Setting<Integer> days;
    private final Setting<Integer> hours;
    private final Setting<Integer> minutes;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> lowColor;
    private final Setting<SettingColor> highColor;
    private final Setting<SettingColor> fillSuspected;
    private final Setting<SettingColor> lineSuspected;
    private final Setting<SettingColor> fillConfirmed;
    private final Setting<SettingColor> lineConfirmed;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerSuspected;
    private final Setting<SettingColor> tracerConfirmed;
    private final Setting<Boolean> showPillar;
    private final Setting<Integer> pillarHeight;
    private final Map<class_1923, Sighting> heatmap;
    private final Map<UUID, Instant> antiSpam;
    private final ConcurrentLinkedQueue<class_1923> oreScanQueue;
    private final ConcurrentHashMap<class_1923, class_1923> scanOrigin;
    private final Path savePath;
    private int tickCounter;

    public PlayerSightingHeatmap() {
        super(DonutAddon.CATEGORY, "player-heatmap", "Heatmap base finder: records player sightings and scans surrounding chunks for underground ores/spawners to locate hidden bases.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgScan = this.settings.createGroup("Underground Scan");
        this.sgTime = this.settings.createGroup("Time Window");
        this.sgRender = this.settings.createGroup("Render");
        this.autoSave = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("auto-save")).description("Automatically save heatmap to file.")).defaultValue((Object)true)).build());
        this.chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).description("Print promotion events (SUSPECTED / CONFIRMED) to chat.")).defaultValue((Object)true)).build());
        this.confirmThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-threshold")).description("Sighting count needed to promote SUSPECTED \u2192 CONFIRMED.")).defaultValue((Object)2)).range(1, 50).sliderMax(20).build());
        this.scanRadius = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-radius-chunks")).description("Chunks around the spotted player to scan for ores/spawners.")).defaultValue((Object)5)).range(0, 8).sliderMax(8).build());
        this.scanBelowY = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-below-y")).description("Only count ore/spawner hits below this Y level (underground filter).")).defaultValue((Object)0)).range(-64, 128).sliderRange(-64, 128).build());
        this.scorePerOre = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("score-per-ore")).description("Points per deepslate/rare ore found during scan.")).defaultValue((Object)1)).range(1, 10).sliderMax(10).build());
        this.scorePerSpawner = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("score-per-spawner")).description("Points per Spawner found during scan (strong base signal).")).defaultValue((Object)8)).range(1, 30).sliderMax(30).build());
        this.minOreScore = this.sgScan.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-ore-score")).description("Minimum ore score to promote a sighting chunk to SUSPECTED.")).defaultValue((Object)1)).range(1, 50).sliderMax(30).build());
        this.days = this.sgTime.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("days")).description("Days to keep sighting data.")).defaultValue((Object)30)).range(0, 30).build());
        this.hours = this.sgTime.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("hours")).description("Hours to keep sighting data.")).defaultValue((Object)0)).range(0, 23).build());
        this.minutes = this.sgTime.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("minutes")).description("Minutes to keep sighting data.")).defaultValue((Object)0)).range(0, 59).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).description("Render the heatmap overlay.")).defaultValue((Object)true)).build());
        this.lowColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("sighting-low-color")).description("Color for low-activity SIGHTING chunks.")).defaultValue(new SettingColor(0, 255, 0, 30)).visible(() -> this.renderEnabled.get())).build());
        this.highColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("sighting-high-color")).description("Color for high-activity SIGHTING chunks.")).defaultValue(new SettingColor(255, 255, 0, 120)).visible(() -> this.renderEnabled.get())).build());
        this.fillSuspected = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-suspected")).description("Plate fill for SUSPECTED base chunks.")).defaultValue(new SettingColor(255, 140, 0, 45)).visible(() -> this.renderEnabled.get())).build());
        this.lineSuspected = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-suspected")).description("Plate outline for SUSPECTED base chunks.")).defaultValue(new SettingColor(255, 140, 0, 200)).visible(() -> this.renderEnabled.get())).build());
        this.fillConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill-confirmed")).description("Plate fill for CONFIRMED bases.")).defaultValue(new SettingColor(50, 255, 80, 55)).visible(() -> this.renderEnabled.get())).build());
        this.lineConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line-confirmed")).description("Plate outline for CONFIRMED bases.")).defaultValue(new SettingColor(50, 255, 80, 220)).visible(() -> this.renderEnabled.get())).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).description("Draw tracer lines to SUSPECTED / CONFIRMED chunks.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.tracerSuspected = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-suspected")).description("Tracer color for SUSPECTED chunks.")).defaultValue(new SettingColor(255, 165, 0, 140)).visible(() -> this.renderEnabled.get())).build());
        this.tracerConfirmed = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-confirmed")).description("Tracer color for CONFIRMED chunks.")).defaultValue(new SettingColor(80, 255, 80, 190)).visible(() -> this.renderEnabled.get())).build());
        this.showPillar = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-pillar")).description("Render a tall pillar over CONFIRMED base chunks.")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.pillarHeight = this.sgRender.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("pillar-height")).description("Height of the confirmation pillar in blocks.")).defaultValue((Object)80)).min(16).sliderMax(256).visible(() -> this.showPillar.get())).build());
        this.heatmap = new ConcurrentHashMap<class_1923, Sighting>();
        this.antiSpam = new ConcurrentHashMap<UUID, Instant>();
        this.oreScanQueue = new ConcurrentLinkedQueue();
        this.scanOrigin = new ConcurrentHashMap();
        this.savePath = Paths.get("meteor-client", "player_heatmap.txt");
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.load();
        this.cleanup();
        this.tickCounter = 0;
        this.antiSpam.clear();
        this.oreScanQueue.clear();
        this.scanOrigin.clear();
    }

    public void onDeactivate() {
        if (((Boolean)this.autoSave.get()).booleanValue()) {
            this.save();
        }
        this.antiSpam.clear();
        this.oreScanQueue.clear();
        this.scanOrigin.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        ++this.tickCounter;
        if (this.tickCounter % 20 == 0) {
            Instant now = Instant.now();
            for (class_742 entity : this.mc.field_1687.method_18456()) {
                if (entity.method_5667().equals(this.mc.field_1724.method_5667())) continue;
                Instant lastSeen = this.antiSpam.get(entity.method_5667());
                boolean spamOk = lastSeen == null || Duration.between(lastSeen, now).getSeconds() >= 30L;
                class_1923 cp = entity.method_31476();
                this.recordSighting(cp);
                if (!spamOk) continue;
                this.antiSpam.put(entity.method_5667(), now);
                this.enqueueSurroundingScan(cp);
            }
        }
        if (this.tickCounter % 3 == 0) {
            class_1923 toScan;
            int limit = 6;
            while (!this.oreScanQueue.isEmpty() && limit-- > 0 && (toScan = this.oreScanQueue.poll()) != null) {
                class_2818 chunk = this.mc.field_1687.method_8497(toScan.field_9181, toScan.field_9180);
                if (chunk == null) {
                    this.oreScanQueue.add(toScan);
                    break;
                }
                this.runOreScan(toScan, chunk, this.scanOrigin.remove(toScan));
            }
        }
    }

    private void recordSighting(class_1923 cp) {
        Sighting s = this.heatmap.computeIfAbsent(cp, Sighting::new);
        s.touch();
        this.maybePromoteConfirmed(s);
        if (((Boolean)this.autoSave.get()).booleanValue()) {
            this.save();
        }
    }

    private void enqueueSurroundingScan(class_1923 origin) {
        int r = (Integer)this.scanRadius.get();
        for (int dx = -r; dx <= r; ++dx) {
            for (int dz = -r; dz <= r; ++dz) {
                class_1923 target = new class_1923(origin.field_9181 + dx, origin.field_9180 + dz);
                if (this.scanOrigin.containsKey(target)) continue;
                this.scanOrigin.put(target, origin);
                this.oreScanQueue.add(target);
            }
        }
    }

    private void runOreScan(class_1923 cp, class_2818 chunk, class_1923 originCp) {
        if (this.mc.field_1687 == null) {
            return;
        }
        int belowY = (Integer)this.scanBelowY.get();
        int bottomY = this.mc.field_1687.method_31607();
        int oreScore = 0;
        int lowestOreY = belowY;
        class_2826[] sections = chunk.method_12006();
        for (int si = 0; si < sections.length; ++si) {
            if (sections[si] == null || sections[si].method_38292()) continue;
            int sectionBottomY = bottomY + si * 16;
            if (sectionBottomY >= belowY) break;
            boolean hasTarget = sections[si].method_12265().method_19526(sx -> this.isTargetBlock(sx.method_26204()));
            if (!hasTarget) continue;
            for (int bx = 0; bx < 16; ++bx) {
                for (int by = 0; by < 16; ++by) {
                    int absoluteY = sectionBottomY + by;
                    if (absoluteY >= belowY) continue;
                    for (int bz = 0; bz < 16; ++bz) {
                        class_2248 b = sections[si].method_12254(bx, by, bz).method_26204();
                        if (b == class_2246.field_10260) {
                            oreScore += ((Integer)this.scorePerSpawner.get()).intValue();
                            if (absoluteY >= lowestOreY) continue;
                            lowestOreY = absoluteY;
                            continue;
                        }
                        if (!this.isOreBlock(b)) continue;
                        oreScore += ((Integer)this.scorePerOre.get()).intValue();
                        if (absoluteY >= lowestOreY) continue;
                        lowestOreY = absoluteY;
                    }
                }
            }
        }
        if (oreScore >= (Integer)this.minOreScore.get()) {
            class_1923 promoteCp = originCp != null ? originCp : cp;
            Sighting s = this.heatmap.computeIfAbsent(promoteCp, Sighting::new);
            s.oreScore += oreScore;
            s.oreFloorY = Math.min(s.oreFloorY, lowestOreY);
            if (s.tier == Tier.SIGHTING) {
                s.tier = Tier.SUSPECTED;
                if (((Boolean)this.chatNotify.get()).booleanValue()) {
                    ChatUtils.info((String)("[Heatmap] \u00a7eSUSPECTED BASE\u00a7r at chunk \u00a7f" + promoteCp.field_9181 + "," + promoteCp.field_9180 + " \u00a77oreScore=" + s.oreScore + " oreFloorY=" + s.oreFloorY), (Object[])new Object[0]);
                }
            }
            this.maybePromoteConfirmed(s);
            if (((Boolean)this.autoSave.get()).booleanValue()) {
                this.save();
            }
        }
    }

    private void maybePromoteConfirmed(Sighting s) {
        if (s.tier == Tier.SUSPECTED && s.count >= (Integer)this.confirmThreshold.get()) {
            s.tier = Tier.CONFIRMED;
            if (((Boolean)this.chatNotify.get()).booleanValue()) {
                ChatUtils.info((String)("[Heatmap] \u00a7a\u00a7lBASE CONFIRMED\u00a7r chunk \u00a7f" + s.cp.field_9181 + "," + s.cp.field_9180 + " \u00a77sightings=" + s.count + " oreScore=" + s.oreScore), (Object[])new Object[0]);
            }
        }
    }

    private boolean isTargetBlock(class_2248 b) {
        return this.isOreBlock(b) || b == class_2246.field_10260;
    }

    private boolean isOreBlock(class_2248 b) {
        return b == class_2246.field_10442 || b == class_2246.field_29029 || b == class_2246.field_10013 || b == class_2246.field_29220 || b == class_2246.field_22109 || b == class_2246.field_10571 || b == class_2246.field_29026 || b == class_2246.field_10212 || b == class_2246.field_29027 || b == class_2246.field_10090 || b == class_2246.field_29028 || b == class_2246.field_10418 || b == class_2246.field_29219 || b == class_2246.field_27120 || b == class_2246.field_29221;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue() || this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        Duration keep = this.window();
        Instant cutoff = Instant.now().minus(keep);
        class_243 eye = this.mc.field_1724.method_5836(event.tickDelta);
        block5: for (Sighting s : this.heatmap.values()) {
            if (s.last.isBefore(cutoff)) continue;
            double x0 = s.cp.method_8326();
            double z0 = s.cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            double cx = s.cp.method_33940();
            double cz = s.cp.method_33942();
            switch (s.tier.ordinal()) {
                case 0: {
                    int score = s.densityScore(keep);
                    float ratio = Math.min(1.0f, Math.max(0.0f, (float)score / 20.0f));
                    SettingColor l = (SettingColor)this.lowColor.get();
                    SettingColor h = (SettingColor)this.highColor.get();
                    int r = (int)((float)l.r + (float)(h.r - l.r) * ratio);
                    int g = (int)((float)l.g + (float)(h.g - l.g) * ratio);
                    int b = (int)((float)l.b + (float)(h.b - l.b) * ratio);
                    int a = (int)((float)l.a + (float)(h.a - l.a) * ratio);
                    SettingColor c = new SettingColor(r, g, b, a);
                    event.renderer.box(x0, 64.0, z0, x1, 64.1, z1, (Color)c, (Color)c, ShapeMode.Both, 0);
                    break;
                }
                case 1: {
                    double plateY = (double)s.oreFloorY + 0.05;
                    event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.25, z1, (Color)this.fillSuspected.get(), (Color)this.lineSuspected.get(), ShapeMode.Both, 0);
                    if (!((Boolean)this.showTracers.get()).booleanValue()) continue block5;
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, (Color)this.tracerSuspected.get());
                    break;
                }
                case 2: {
                    double plateY = (double)s.oreFloorY + 0.05;
                    SettingColor fill = (SettingColor)this.fillConfirmed.get();
                    SettingColor line = (SettingColor)this.lineConfirmed.get();
                    event.renderer.box(x0, plateY - 0.05, z0, x1, plateY + 0.25, z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
                    if (((Boolean)this.showPillar.get()).booleanValue()) {
                        event.renderer.box(x0, plateY, z0, x1, plateY + (double)((Integer)this.pillarHeight.get()).intValue(), z1, (Color)fill, (Color)line, ShapeMode.Both, 0);
                    }
                    if (!((Boolean)this.showTracers.get()).booleanValue()) break;
                    event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, plateY, cz, (Color)this.tracerConfirmed.get());
                }
            }
        }
    }

    private void load() {
        if (!Files.exists(this.savePath, new LinkOption[0])) {
            return;
        }
        this.heatmap.clear();
        try (BufferedReader r = Files.newBufferedReader(this.savePath);){
            String line;
            while ((line = r.readLine()) != null) {
                Sighting s = Sighting.from(line);
                if (s == null) continue;
                this.heatmap.put(s.cp, s);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void save() {
        try {
            Files.createDirectories(this.savePath.getParent(), new FileAttribute[0]);
            try (BufferedWriter w = Files.newBufferedWriter(this.savePath, new OpenOption[0]);){
                for (Sighting s : this.heatmap.values()) {
                    w.write(s.toString());
                    w.newLine();
                }
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(this.window());
        this.heatmap.entrySet().removeIf(e -> ((Sighting)e.getValue()).last.isBefore(cutoff));
    }

    private Duration window() {
        return Duration.ofDays(((Integer)this.days.get()).intValue()).plusHours(((Integer)this.hours.get()).intValue()).plusMinutes(((Integer)this.minutes.get()).intValue());
    }

    private static class Sighting {
        final class_1923 cp;
        Instant first;
        Instant last;
        int count;
        Tier tier;
        int oreScore;
        int oreFloorY;

        Sighting(class_1923 cp) {
            this.cp = cp;
            this.first = this.last = Instant.now();
            this.count = 1;
            this.tier = Tier.SIGHTING;
            this.oreScore = 0;
            this.oreFloorY = 64;
        }

        void touch() {
            this.last = Instant.now();
            ++this.count;
        }

        int densityScore(Duration window) {
            long age = Duration.between(this.last, Instant.now()).getSeconds();
            return age >= window.getSeconds() ? 0 : (int)((double)this.count * (1.0 - (double)age / (double)window.getSeconds())) + 1;
        }

        public String toString() {
            return this.cp.field_9181 + "," + this.cp.field_9180 + "," + String.valueOf(this.first) + "," + String.valueOf(this.last) + "," + this.count + "," + this.tier.name() + "," + this.oreScore + "," + this.oreFloorY;
        }

        static Sighting from(String line) {
            String[] p = line.split(",");
            if (p.length < 5) {
                return null;
            }
            try {
                Sighting s = new Sighting(new class_1923(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
                s.first = Instant.parse(p[2].trim());
                s.last = Instant.parse(p[3].trim());
                s.count = Integer.parseInt(p[4].trim());
                s.tier = p.length > 5 ? Tier.valueOf(p[5].trim()) : Tier.SIGHTING;
                s.oreScore = p.length > 6 ? Integer.parseInt(p[6].trim()) : 0;
                s.oreFloorY = p.length > 7 ? Integer.parseInt(p[7].trim()) : 64;
                return s;
            }
            catch (Exception ignored) {
                return null;
            }
        }
    }

    private static enum Tier {
        SIGHTING,
        SUSPECTED,
        CONFIRMED;

    }
}
