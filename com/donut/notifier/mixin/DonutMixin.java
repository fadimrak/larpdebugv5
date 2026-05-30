package com.donut.notifier.mixin;

import com.donut.notifier.DonutAddon;
import net.minecraft.class_310;
import net.minecraft.class_542;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={class_310.class})
public abstract class DonutMixin {
    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void onGameLoaded(class_542 args, CallbackInfo ci) {
        DonutAddon.LOG.info("Hello from DonutMixin!");
    }
}
