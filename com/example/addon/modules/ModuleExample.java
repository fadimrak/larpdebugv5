package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2338;
import net.minecraft.class_238;

public class ModuleExample
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final Setting<Double> scale;
    private final Setting<SettingColor> color;

    public ModuleExample() {
        super(AddonTemplate.CATEGORY, "world-origin", "An example module that highlights the center of the world.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.scale = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("scale")).description("The size of the marker.")).defaultValue(2.0).range(0.5, 10.0).build());
        this.color = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("color")).description("The color of the marker.")).defaultValue(Color.MAGENTA).build());
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        class_238 marker = new class_238(class_2338.field_10980);
        marker = marker.method_1012((Double)this.scale.get() * marker.method_17939(), (Double)this.scale.get() * marker.method_17940(), (Double)this.scale.get() * marker.method_17941());
        event.renderer.box(marker, (Color)this.color.get(), (Color)this.color.get(), ShapeMode.Both, 0);
    }
}
