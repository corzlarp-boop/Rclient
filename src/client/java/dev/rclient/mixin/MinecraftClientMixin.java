package dev.rclient.mixin;

import dev.rclient.gui.MainMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swap the vanilla title screen for the RClient main menu. Purely a UI replacement. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen rclient$replaceTitle(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class) return new MainMenuScreen();
        return screen;
    }
}
