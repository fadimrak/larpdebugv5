package com.donut.notifier;

import com.donut.notifier.modules.AmethystLodeFinder;
import com.donut.notifier.modules.ArchonRadar;
import com.donut.notifier.modules.BlockDropFinder;
import com.donut.notifier.modules.DeepScanExploitHunter;
import com.donut.notifier.modules.EntityClusterScanner;
import com.donut.notifier.modules.PlayerESP;
import com.donut.notifier.modules.PlayerSightingHeatmap;
import com.donut.notifier.modules.SpawnerSoundRadar;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class DonutAddon
extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Larp Debug V5");

    public void onInitialize() {
        LOG.info("Initializing Donut Notifier Addon");
        Modules.get().add((Module)new PlayerESP());
        Modules.get().add((Module)new BlockDropFinder());
        Modules.get().add((Module)new AmethystLodeFinder());
        Modules.get().add((Module)new ArchonRadar());
        Modules.get().add((Module)new PlayerSightingHeatmap());
        Modules.get().add((Module)new SpawnerSoundRadar());
        Modules.get().add((Module)new EntityClusterScanner());
        Modules.get().add((Module)new DeepScanExploitHunter());
    }

    public void onRegisterCategories() {
        Modules.registerCategory((Category)CATEGORY);
    }

    public String getPackage() {
        return "com.donut.notifier";
    }
}
