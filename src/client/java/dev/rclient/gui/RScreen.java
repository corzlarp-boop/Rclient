package dev.rclient.gui;

import dev.rclient.anim.Animator;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Base for all RClient screens. Vanilla input method signatures live ONLY here (they changed in 1.21.9
 * to MouseButtonEvent/KeyEvent/CharacterEvent objects); everything else uses the plain-number callbacks below, so a future
 * signature change is a one-file fix.
 */
public abstract class RScreen extends Screen {
    protected RScreen(String title) { super(Component.literal(title)); }

    // ---- simple callbacks for subclasses ----
    protected void frame(GuiGraphics ctx, int mx, int my, float partial) {}
    protected boolean onMouseDown(double x, double y, int button) { return false; }
    protected boolean onMouseUp(double x, double y, int button) { return false; }
    protected boolean onMouseDrag(double x, double y, int button, double dx, double dy) { return false; }
    protected boolean onScroll(double x, double y, double amount) { return false; }
    protected boolean onKey(int key, int scancode, int mods) { return false; }
    protected boolean onChar(int codepoint) { return false; }

    // ---- vanilla plumbing ----
    @Override public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        Animator.frame(System.nanoTime());
        frame(ctx, mouseX, mouseY, delta);
    }

    /** We draw our own background. */
    @Override public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {}

    /** Vanilla's blurred menu background (respects the player's blur setting). */
    protected final void vanillaBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return onMouseDown(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        return onMouseUp(click.x(), click.y(), click.button()) || super.mouseReleased(click);
    }

    @Override public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        return onMouseDrag(click.x(), click.y(), click.button(), dx, dy) || super.mouseDragged(click, dx, dy);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return onScroll(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent input) {
        return onKey(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    @Override public boolean charTyped(CharacterEvent input) {
        return onChar(input.codepoint()) || super.charTyped(input);
    }
}
