package com.donut.notifier.modules;

import com.donut.notifier.DonutAddon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1923;
import net.minecraft.class_1935;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_368;
import net.minecraft.class_374;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class SpawnerNotifier
extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final SettingGroup sgWebhook;
    private final Setting<Boolean> chatFeedback;
    private final Setting<Boolean> showDistance;
    private final Setting<Boolean> toastNotification;
    private final Setting<Boolean> disconnectOnFind;
    private final Setting<Boolean> showEsp;
    private final Setting<SettingColor> espColor;
    private final Setting<Boolean> showTracers;
    private final Setting<Boolean> tracersClosest;
    private final Setting<Double> maxTracerDistance;
    private final Setting<TracerMode> tracerMode;
    private final Setting<Double> tracerWidth;
    private final Setting<Boolean> enableWebhook;
    private final Setting<Boolean> selfPing;
    private final Set<class_2338> spawnerPositions;
    private final Set<class_1923> processedChunks;
    private String webhookUrl;
    private String discordId;
    private HttpClient httpClient;

    public SpawnerNotifier() {
        super(DonutAddon.CATEGORY, "spawner", "Notifies and highlights spawners with ESP and webhooks");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.sgWebhook = this.settings.createGroup("Webhook");
        this.chatFeedback = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("chat-feedback")).description("Sends a chat message when a spawner is found.")).defaultValue((Object)true)).build());
        this.showDistance = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-distance")).description("Shows distance in chat messages.")).defaultValue((Object)true)).build());
        this.toastNotification = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("toast-notification")).description("Shows a toast notification when a spawner is found.")).defaultValue((Object)true)).build());
        this.disconnectOnFind = this.sgGeneral.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("disconnect-on-find")).description("Disconnects from the server when a spawner is found.")).defaultValue((Object)false)).build());
        this.showEsp = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-esp")).description("Highlights spawners with a box.")).defaultValue((Object)true)).build());
        this.espColor = this.sgRender.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("esp-color")).description("The color of the ESP box.")).defaultValue(new SettingColor(255, 0, 0, 150)).build());
        this.showTracers = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("tracers")).description("Draws tracers to spawners.")).defaultValue((Object)true)).build());
        this.tracersClosest = this.sgRender.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("tracers-closest-only")).description("Only draws tracers to the closest spawner.")).defaultValue((Object)false)).build());
        this.maxTracerDistance = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("max-tracer-distance")).description("Maximum distance to draw tracers.")).defaultValue(256.0).range(1.0, 1024.0).sliderMax(512.0).build());
        this.tracerMode = this.sgRender.add((Setting)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("tracer-mode")).description("The style of tracers to use.")).defaultValue((Object)TracerMode.TwoD)).build());
        this.tracerWidth = this.sgRender.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("tracer-width")).description("The width of the tracer lines.")).defaultValue(1.0).range(0.1, 5.0).sliderMax(3.0).build());
        this.enableWebhook = this.sgWebhook.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("enable-webhook")).description("Sends a Discord webhook when a spawner is found.")).defaultValue((Object)false)).build());
        this.selfPing = this.sgWebhook.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("self-ping")).description("Pings you in the Discord webhook.")).defaultValue((Object)false)).build());
        this.spawnerPositions = ConcurrentHashMap.newKeySet();
        this.processedChunks = ConcurrentHashMap.newKeySet();
        this.webhookUrl = "";
        this.discordId = "";
        DonutAddon.LOG.info("SpawnerNotifier module initialized!");
    }

    private HttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
        }
        return this.httpClient;
    }

    public void onActivate() {
        this.spawnerPositions.clear();
        this.processedChunks.clear();
        this.loadWebhookConfig();
        if (this.mc.field_1687 != null && this.mc.field_1724 != null) {
            int rd = this.mc.field_1690.method_38521();
            int pX = this.mc.field_1724.method_31476().field_9181;
            int pZ = this.mc.field_1724.method_31476().field_9180;
            for (int x = pX - rd; x <= pX + rd; ++x) {
                for (int z = pZ - rd; z <= pZ + rd; ++z) {
                    class_2818 chunk = this.mc.field_1687.method_8497(x, z);
                    if (chunk == null) continue;
                    this.checkChunk(chunk);
                }
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        this.checkChunk(event.chunk());
    }

    private void checkChunk(class_2818 chunk) {
        if (chunk == null || this.mc.field_1687 == null) {
            return;
        }
        class_1923 cpos = chunk.method_12004();
        if (this.processedChunks.contains(cpos)) {
            return;
        }
        MeteorExecutor.execute(() -> {
            int found = 0;
            class_2826[] sections = chunk.method_12006();
            for (int i = 0; i < sections.length; ++i) {
                class_2826 section = sections[i];
                if (section == null || section.method_38292() || !section.method_12265().method_19526(s -> s.method_27852(class_2246.field_10260))) continue;
                int sectionY = this.mc.field_1687.method_31607() + i * 16;
                for (int x = 0; x < 16; ++x) {
                    for (int y = 0; y < 16; ++y) {
                        for (int z = 0; z < 16; ++z) {
                            if (!section.method_12254(x, y, z).method_27852(class_2246.field_10260) || !this.spawnerPositions.add(new class_2338(cpos.method_8326() + x, sectionY + y, cpos.method_8328() + z))) continue;
                            ++found;
                        }
                    }
                }
            }
            if (found > 0) {
                this.processedChunks.add(cpos);
                int finalFound = found;
                this.mc.execute(() -> this.handleDetection(cpos, finalFound));
            }
        });
    }

    private void handleDetection(class_1923 cpos, int count) {
        int cx = cpos.field_9181 * 16 + 8;
        int cz = cpos.field_9180 * 16 + 8;
        if (((Boolean)this.chatFeedback.get()).booleanValue()) {
            String msg = "Found " + count + " spawner" + (count > 1 ? "s" : "") + " at " + cx + ", " + cz;
            if (((Boolean)this.showDistance.get()).booleanValue() && this.mc.field_1724 != null) {
                msg = msg + String.format(" (%.0fm)", Math.sqrt(this.mc.field_1724.method_5649((double)cx, this.mc.field_1724.method_23318(), (double)cz)));
            }
            ChatUtils.info((String)msg, (Object[])new Object[0]);
        }
        if (((Boolean)this.toastNotification.get()).booleanValue()) {
            this.mc.method_1566().method_1999((class_368)new SpawnerToast());
        }
        if (((Boolean)this.enableWebhook.get()).booleanValue()) {
            this.sendWebhook(cpos, count);
        }
        if (((Boolean)this.disconnectOnFind.get()).booleanValue()) {
            this.toggle();
            if (this.mc.method_1562() != null) {
                this.mc.method_1562().method_48296().method_10747((class_2561)class_2561.method_43470((String)"Spawner Found"));
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.field_1724 == null || this.spawnerPositions.isEmpty()) {
            return;
        }
        class_243 playerPos = this.mc.field_1724.method_30950(event.tickDelta);
        class_243 eyePos = playerPos.method_1031(0.0, (double)this.mc.field_1724.method_5751(), 0.0);
        double maxDistSq = (Double)this.maxTracerDistance.get() * (Double)this.maxTracerDistance.get();
        class_2338 closest = null;
        if (((Boolean)this.showTracers.get()).booleanValue() && this.tracerMode.get() == TracerMode.ThreeD && ((Boolean)this.tracersClosest.get()).booleanValue()) {
            double minD = Double.MAX_VALUE;
            for (class_2338 pos : this.spawnerPositions) {
                double d = playerPos.method_1028((double)pos.method_10263() + 0.5, (double)pos.method_10264() + 0.5, (double)pos.method_10260() + 0.5);
                if (!(d < minD)) continue;
                minD = d;
                closest = pos;
            }
        }
        for (class_2338 pos : this.spawnerPositions) {
            double z;
            double y;
            double x = pos.method_10263();
            if (playerPos.method_1028(x + 0.5, (y = (double)pos.method_10264()) + 0.5, (z = (double)pos.method_10260()) + 0.5) > maxDistSq) continue;
            if (((Boolean)this.showEsp.get()).booleanValue()) {
                event.renderer.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, (Color)this.espColor.get(), (Color)this.espColor.get(), ShapeMode.Both, 0);
            }
            if (!((Boolean)this.showTracers.get()).booleanValue() || this.tracerMode.get() != TracerMode.ThreeD || ((Boolean)this.tracersClosest.get()).booleanValue() && !pos.equals((Object)closest)) continue;
            event.renderer.line(eyePos.field_1352, eyePos.field_1351, eyePos.field_1350, x + 0.5, y + 0.5, z + 0.5, (Color)this.espColor.get());
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (this.mc.field_1724 == null || this.spawnerPositions.isEmpty()) {
            return;
        }
        if (!((Boolean)this.showTracers.get()).booleanValue() || this.tracerMode.get() != TracerMode.TwoD) {
            return;
        }
        NametagUtils.onRender((Matrix4f)event.drawContext.method_51448().method_23760().method_23761());
        double cx = (double)event.screenWidth / 2.0;
        double cy = (double)event.screenHeight / 2.0;
        double maxDistSq = (Double)this.maxTracerDistance.get() * (Double)this.maxTracerDistance.get();
        class_243 playerPos = this.mc.field_1724.method_30950(event.tickDelta);
        class_2338 closest = null;
        if (((Boolean)this.tracersClosest.get()).booleanValue()) {
            double minD = Double.MAX_VALUE;
            for (class_2338 pos : this.spawnerPositions) {
                double d = playerPos.method_1028((double)pos.method_10263() + 0.5, (double)pos.method_10264() + 0.5, (double)pos.method_10260() + 0.5);
                if (!(d < minD)) continue;
                minD = d;
                closest = pos;
            }
        }
        for (class_2338 pos : this.spawnerPositions) {
            Vector3d screenPos;
            if (((Boolean)this.tracersClosest.get()).booleanValue() && !pos.equals(closest) || playerPos.method_1028((double)pos.method_10263() + 0.5, (double)pos.method_10264() + 0.5, (double)pos.method_10260() + 0.5) > maxDistSq || !NametagUtils.to2D((Vector3d)(screenPos = new Vector3d((double)pos.method_10263() + 0.5, (double)pos.method_10264() + 0.5, (double)pos.method_10260() + 0.5)), (double)1.0)) continue;
            Renderer2D.COLOR.line(cx, cy, screenPos.x, screenPos.y, (Color)this.espColor.get());
        }
    }

    private void sendWebhook(class_1923 cpos, int count) {
        if (this.webhookUrl.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String server = this.mc.method_1558() != null ? this.mc.method_1558().field_3761 : "Singleplayer";
                int cx = cpos.field_9181 * 16 + 8;
                int cz = cpos.field_9180 * 16 + 8;
                String ping = (Boolean)this.selfPing.get() != false && !this.discordId.isEmpty() ? "<@" + this.discordId + ">" : "";
                String body = String.format("{\"content\":\"%s\",\"username\":\"Meteor SpawnerNotifier\",\"embeds\":[{\"title\":\"Spawner Alert\",\"description\":\"%s spawner(s) at %d, %d\",\"color\":15158332,\"fields\":[{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}]}]}", SpawnerNotifier.escape(ping), count, cx, cz, SpawnerNotifier.escape(server));
                this.getHttpClient().sendAsync(HttpRequest.newBuilder().uri(URI.create(this.webhookUrl)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
    }

    private void loadWebhookConfig() {
        try {
            File folder = new File(this.mc.field_1697, "meteor-addon");
            folder.mkdirs();
            File file = new File(folder, "spawner_webhook.txt");
            if (!file.exists()) {
                return;
            }
            try (BufferedReader br = new BufferedReader(new FileReader(file));){
                this.webhookUrl = br.readLine();
                if (this.webhookUrl == null) {
                    this.webhookUrl = "";
                }
                this.discordId = br.readLine();
                if (this.discordId == null) {
                    this.discordId = "";
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

    public static class SpawnerToast
    implements class_368 {
        private static final class_2960 TEXTURE = class_2960.method_60655((String)"minecraft", (String)"toast/advancement");
        private static final class_1799 ICON = new class_1799((class_1935)class_1802.field_8849);

        public class_368.class_369 method_1986(class_332 context, class_374 manager, long startTime) {
            context.method_25294(0, 0, this.method_29049(), this.method_29050(), -1879048192);
            context.method_51427(ICON, 8, 8);
            context.method_51439(manager.method_1995().field_1772, (class_2561)class_2561.method_43470((String)"Spawner Detected!"), 30, 7, -1, false);
            context.method_51439(manager.method_1995().field_1772, (class_2561)class_2561.method_43470((String)"Mob spawner found nearby"), 30, 18, -1, false);
            return startTime >= 5000L ? class_368.class_369.field_2209 : class_368.class_369.field_2210;
        }

        public int method_29049() {
            return 160;
        }

        public int method_29050() {
            return 32;
        }
    }
}
