package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_742;
import org.joml.Vector3d;

public class PlayerESP
extends Module {
    private final SettingGroup sgRender;
    private final Setting<Boolean> showEsp;
    private final Setting<SettingColor> espColor;
    private final Setting<Boolean> showTracers;
    private final Setting<TracerMode> tracerMode;
    private final Setting<Double> maxDistance;
    private final Setting<Double> tracerWidth;

    public PlayerESP() {
        super(DonutAddon.CATEGORY, "player-esp", "Highlights nearby players with smooth rendering");
        this.sgRender = this.settings.createGroup("Render");
        this.showEsp = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-box")).description("Highlights players with a box.")).defaultValue((Object)true)).build());
        this.espColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("box-color")).description("The color of the ESP box.")).defaultValue(new SettingColor(0, 255, 200, 150)).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("tracers")).description("Draws tracers to players.")).defaultValue((Object)true)).build());
        this.tracerMode = this.sgRender.add((Setting)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("tracer-mode")).description("The style of tracers to use.")).defaultValue((Object)TracerMode.TwoD)).build());
        this.maxDistance = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("max-distance")).description("Maximum distance to render players.")).defaultValue(512.0).range(1.0, 2048.0).sliderMax(1024.0).build());
        this.tracerWidth = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("tracer-width")).description("The width of the tracer lines.")).defaultValue(1.0).range(0.1, 5.0).sliderMax(3.0).build());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        class_243 eyePos = this.mc.field_1724.method_30950(event.tickDelta).method_1031(0.0, (double)this.mc.field_1724.method_5751(), 0.0);
        double maxDistSq = (Double)this.maxDistance.get() * (Double)this.maxDistance.get();
        for (class_742 player : this.mc.field_1687.method_18456()) {
            if (player == this.mc.field_1724 || !player.method_5805() || this.mc.field_1724.method_5858((class_1297)player) > maxDistSq) continue;
            if (((Boolean)this.showEsp.get()).booleanValue()) {
                class_243 pos = player.method_30950(event.tickDelta);
                double width = (double)player.method_17681() / 2.0;
                double height = player.method_17682();
                Color c = (Color)this.espColor.get();
                event.renderer.box(pos.field_1352 - width, pos.field_1351, pos.field_1350 - width, pos.field_1352 + width, pos.field_1351 + height, pos.field_1350 + width, c, c, ShapeMode.Both, 0);
            }
            if (!((Boolean)this.showTracers.get()).booleanValue() || this.tracerMode.get() != TracerMode.ThreeD) continue;
            class_243 target = player.method_30950(event.tickDelta).method_1031(0.0, (double)player.method_17682() / 2.0, 0.0);
            event.renderer.line(eyePos.field_1352, eyePos.field_1351, eyePos.field_1350, target.field_1352, target.field_1351, target.field_1350, (Color)this.espColor.get());
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        if (!((Boolean)this.showTracers.get()).booleanValue() || this.tracerMode.get() != TracerMode.TwoD) {
            return;
        }
        double centerX = (double)event.screenWidth / 2.0;
        double centerY = (double)event.screenHeight / 2.0;
        double maxDistSq = (Double)this.maxDistance.get() * (Double)this.maxDistance.get();
        for (class_742 player : this.mc.field_1687.method_18456()) {
            if (player == this.mc.field_1724 || !player.method_5805() || this.mc.field_1724.method_5858((class_1297)player) > maxDistSq) continue;
            class_243 target = player.method_30950(event.tickDelta).method_1031(0.0, (double)player.method_17682() / 2.0, 0.0);
            Vector3d screenPos = new Vector3d(target.field_1352, target.field_1351, target.field_1350);
            if (!NametagUtils.to2D((Vector3d)screenPos, (double)1.0)) continue;
            Renderer2D.COLOR.line(centerX, centerY, screenPos.x, screenPos.y, (Color)this.espColor.get());
        }
    }

    public static enum TracerMode {
        ThreeD("3D"),
        TwoD("2D");

        private final String title;

        private TracerMode(String title) {
            this.title = title;
        }

        public String toString() {
            return this.title;
        }
    }
}
