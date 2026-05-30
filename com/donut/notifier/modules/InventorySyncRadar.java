package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2649;
import net.minecraft.class_2708;
import net.minecraft.class_2885;
import net.minecraft.class_3944;

public class InventorySyncRadar
extends Module {
    private static final Set<class_1792> SPAWNER_LOOT = Set.of(class_1802.field_8606, class_1802.field_8107, class_1802.field_8276, class_1802.field_8511, class_1802.field_8054, class_1802.field_8183, class_1802.field_8620, class_1802.field_8397, class_1802.field_8680, class_1802.field_8634, class_1802.field_8894, class_1802.field_8729);
    private final SettingGroup sgGeneral;
    private final SettingGroup sgFilter;
    private final SettingGroup sgRender;
    private final Setting<Integer> confirmThreshold;
    private final Setting<Integer> decaySeconds;
    private final Setting<Boolean> chatNotify;
    private final Setting<Integer> minLootMatches;
    private final Setting<String> screenKeyword;
    private final Setting<Boolean> renderEnabled;
    private final Setting<SettingColor> fillColor;
    private final Setting<SettingColor> lineColor;
    private final Setting<Boolean> showTracers;
    private final Setting<SettingColor> tracerColor;
    private final ConcurrentHashMap<class_1923, Detection> detections;
    private volatile class_2338 lastClickPos;
    private volatile boolean spawnerScreenOpen;
    private volatile long screenOpenMs;
    private int tickCounter;

    public InventorySyncRadar() {
        super(DonutAddon.CATEGORY, "inventory-sync-radar", "Detects underground spawners by correlating block interactions, screen opens, and inventory loot patterns.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgFilter = this.settings.createGroup("Screen Filter");
        this.sgRender = this.settings.createGroup("Render");
        this.confirmThreshold = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("confirm-threshold")).description("Score required to confirm a chunk as a spawner location.")).defaultValue((Object)3)).range(2, 30).sliderMax(15).build());
        this.decaySeconds = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("decay-seconds")).description("Idle seconds before a detection is dropped.")).defaultValue((Object)600)).range(30, 1800).sliderMax(600).build());
        this.chatNotify = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-notifications")).defaultValue((Object)true)).build());
        this.minLootMatches = this.sgFilter.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("min-loot-matches")).description("Minimum number of spawner-loot item types needed to flag an inventory.")).defaultValue((Object)2)).range(1, SPAWNER_LOOT.size()).sliderMax(8).build());
        this.screenKeyword = this.sgFilter.add((Setting)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)new StringSetting.Builder().name("screen-keyword")).description("Screen title substring to treat as a spawner screen (case-insensitive).")).defaultValue((Object)"spawner")).build());
        this.renderEnabled = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render")).defaultValue((Object)true)).build());
        this.fillColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("fill")).defaultValue(new SettingColor(0, 220, 80, 40)).visible(() -> this.renderEnabled.get())).build());
        this.lineColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("line")).defaultValue(new SettingColor(0, 220, 80, 200)).visible(() -> this.renderEnabled.get())).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-tracers")).defaultValue((Object)true)).visible(() -> this.renderEnabled.get())).build());
        this.tracerColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("tracer-color")).defaultValue(new SettingColor(0, 220, 80, 150)).visible(() -> this.renderEnabled.get())).build());
        this.detections = new ConcurrentHashMap();
        this.lastClickPos = null;
        this.spawnerScreenOpen = false;
        this.screenOpenMs = 0L;
        this.tickCounter = 0;
    }

    public void onActivate() {
        this.detections.clear();
        this.lastClickPos = null;
        this.spawnerScreenOpen = false;
    }

    public void onDeactivate() {
        this.detections.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (++this.tickCounter % 100 == 0) {
            long cutoff = System.currentTimeMillis() - (long)((Integer)this.decaySeconds.get()).intValue() * 1000L;
            this.detections.entrySet().removeIf(e -> ((Detection)e.getValue()).lastMs < cutoff);
        }
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_2885) {
            class_2885 pkt = (class_2885)class_25962;
            this.lastClickPos = pkt.method_12543().method_17777();
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        class_2596 class_25962 = event.packet;
        if (class_25962 instanceof class_3944) {
            class_3944 pkt = (class_3944)class_25962;
            title = pkt.method_17594().getString().toLowerCase(Locale.ROOT);
            if (((String)title).contains(((String)this.screenKeyword.get()).toLowerCase(Locale.ROOT))) {
                this.spawnerScreenOpen = true;
                this.screenOpenMs = System.currentTimeMillis();
                class_2338 lcp = this.lastClickPos;
                if (lcp != null && lcp.method_10264() < 0) {
                    class_1923 cp = new class_1923(lcp);
                    this.addScore(cp, 2, "SCREEN_OPEN_Y<0");
                    if (((Boolean)this.chatNotify.get()).booleanValue()) {
                        ChatUtils.info((String)("[ISR] \u00a7eSpawner screen\u00a7r opened at Y<0 chunk \u00a7f" + cp.field_9181 + "," + cp.field_9180), (Object[])new Object[0]);
                    }
                }
            } else {
                this.spawnerScreenOpen = false;
            }
        } else {
            title = event.packet;
            if (title instanceof class_2649) {
                class_2338 lcp;
                class_2649 pkt = (class_2649)title;
                if (!this.spawnerScreenOpen) {
                    return;
                }
                if (System.currentTimeMillis() - this.screenOpenMs > 5000L) {
                    this.spawnerScreenOpen = false;
                    return;
                }
                int lootMatches = 0;
                HashSet<class_1792> seenItems = new HashSet<class_1792>();
                List contents = pkt.method_11441();
                for (class_1799 stack : contents) {
                    class_1792 item;
                    if (stack.method_7960() || !SPAWNER_LOOT.contains(item = stack.method_7909()) || !seenItems.add(item)) continue;
                    ++lootMatches;
                }
                if (lootMatches >= (Integer)this.minLootMatches.get() && (lcp = this.lastClickPos) != null && lcp.method_10264() < 0) {
                    class_1923 cp = new class_1923(lcp);
                    this.addScore(cp, 5, "SCREEN+LOOT_Y<0");
                    if (((Boolean)this.chatNotify.get()).booleanValue()) {
                        ChatUtils.info((String)("[ISR] \u00a7a\u00a7lSPAWNER LOOT MATCH\u00a7r chunk \u00a7f" + cp.field_9181 + "," + cp.field_9180 + " \u00a77loot=" + lootMatches + " matches"), (Object[])new Object[0]);
                    }
                }
                this.spawnerScreenOpen = false;
            } else if (event.packet instanceof class_2708 && this.mc.field_1724.method_23318() < 0.0) {
                class_1923 cp = this.mc.field_1724.method_31476();
                this.addScore(cp, 3, "RUBBERBAND");
            }
        }
    }

    private void addScore(class_1923 cp, int delta, String reason) {
        Detection d = this.detections.computeIfAbsent(cp, Detection::new);
        boolean wasConfirmed = d.score >= (Integer)this.confirmThreshold.get();
        d.addScore(delta, reason);
        if (!wasConfirmed && d.score >= (Integer)this.confirmThreshold.get()) {
            d.confirmed = true;
            if (((Boolean)this.chatNotify.get()).booleanValue()) {
                ChatUtils.info((String)("[ISR] \u00a7a\u00a7lCONFIRMED SPAWNER CHUNK\u00a7r \u00a7f" + cp.field_9181 + "," + cp.field_9180 + " \u00a77via " + reason), (Object[])new Object[0]);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!((Boolean)this.renderEnabled.get()).booleanValue() || this.mc.field_1724 == null) {
            return;
        }
        class_243 eye = this.mc.field_1724.method_5836(event.tickDelta);
        for (Detection d : this.detections.values()) {
            double x0 = d.cp.method_8326();
            double z0 = d.cp.method_8328();
            double x1 = x0 + 16.0;
            double z1 = z0 + 16.0;
            double cx = d.cp.method_33940();
            double cz = d.cp.method_33942();
            float ratio = Math.min(1.0f, (float)d.score / (float)((Integer)this.confirmThreshold.get()).intValue());
            SettingColor fill = (SettingColor)this.fillColor.get();
            SettingColor line = (SettingColor)this.lineColor.get();
            SettingColor scaledFill = new SettingColor(fill.r, fill.g, fill.b, (int)((float)fill.a * ratio));
            event.renderer.box(x0, 64.0, z0, x1, 64.2, z1, (Color)scaledFill, (Color)line, ShapeMode.Both, 0);
            if (!((Boolean)this.showTracers.get()).booleanValue()) continue;
            event.renderer.line(eye.field_1352, eye.field_1351, eye.field_1350, cx, 64.1, cz, (Color)this.tracerColor.get());
        }
    }

    private static class Detection {
        final class_1923 cp;
        int score;
        long lastMs;
        boolean confirmed;
        String reason = "";

        Detection(class_1923 cp) {
            this.cp = cp;
            this.lastMs = System.currentTimeMillis();
        }

        void addScore(int delta, String reason) {
            this.score += delta;
            this.lastMs = System.currentTimeMillis();
            this.reason = reason;
        }
    }
}
