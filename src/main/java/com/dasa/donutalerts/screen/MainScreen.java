package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class MainScreen extends BaseScreen {
    private EditBox minField;

    public MainScreen() {
        super(Component.literal("DonutAlerts"));
    }

    private static String toggleLabel() {
        return "Alerts: " + (Alerts.cfg.enabled ? "ON" : "OFF");
    }

    private static String followLabel() {
        return "Alerts only during auctions: " + (Alerts.cfg.autoWithAuction ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        Config c = Alerts.cfg;
        int cx = width / 2;
        int y = 26;

        addRenderableWidget(Button.builder(Component.literal(toggleLabel()), b -> {
            c.enabled = !c.enabled;
            b.setMessage(Component.literal(toggleLabel()));
        }).bounds(cx - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal(followLabel()), b -> {
            c.autoWithAuction = !c.autoWithAuction;
            b.setMessage(Component.literal(followLabel()));
        }).bounds(cx - 100, y, 200, 20).build());

        // global minimum
        y += 42;
        minField = new EditBox(this.font, cx - 100, y, 70, 18, Component.literal("min"));
        minField.setMaxLength(12);
        minField.setValue(Fmt.shortNum(c.globalMin));
        minField.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v >= 0) c.globalMin = v;
        });
        addRenderableWidget(minField);

        String[] presets = {"1M", "10M", "50M", "100M"};
        int[] widths = {24, 28, 28, 34};
        int px = cx - 26;
        for (int i = 0; i < presets.length; i++) {
            String p = presets[i];
            addRenderableWidget(Button.builder(Component.literal(p), b -> minField.setValue(p))
                    .bounds(px, y - 1, widths[i], 20).build());
            px += widths[i] + 2;
        }

        y += 28;
        addRenderableWidget(new AbstractSliderButton(cx - 100, y, 200, 20,
                Component.literal("Volume: " + (int) (c.volume * 100) + "%"), c.volume) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Volume: " + (int) (this.value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                Alerts.cfg.volume = (float) this.value;
            }
        });

        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Notification tiers..."),
                b -> this.minecraft.gui.setScreen(new TierScreen())).bounds(cx - 100, y, 200, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Auction..."),
                b -> this.minecraft.gui.setScreen(new AuctionScreen())).bounds(cx - 100, y, 200, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Gamble queue..."),
                b -> this.minecraft.gui.setScreen(new QueueScreen())).bounds(cx - 100, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move / resize..."),
                b -> this.minecraft.gui.setScreen(new HudEditScreen())).bounds(cx + 2, y, 98, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, y, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = width / 2;
        Alerts.centered(g, this.font, "DonutAlerts", cx, 12, 0xFFFFD700);
        g.text(this.font, "Only alert for payments of at least:", cx - 100, 80, 0xFFFFFFFF, true);
    }
}
