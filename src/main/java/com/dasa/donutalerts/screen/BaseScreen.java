package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Alerts.renderAll(graphics); // so test alerts show while the menu is open
    }

    @Override
    public void removed() {
        Alerts.cfg.save();
    }
}
