package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

public class QueueScreen extends BaseScreen {
    public QueueScreen() {
        super(Component.literal("Gamble queue"));
    }

    private static String toggleLabel() {
        return "Queue: " + (Alerts.cfg.queueEnabled ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        Config c = Alerts.cfg;
        int cx = width / 2;

        addRenderableWidget(Button.builder(Component.literal(toggleLabel()), b -> {
            c.queueEnabled = !c.queueEnabled;
            b.setMessage(Component.literal(toggleLabel()));
        }).bounds(cx - 100, 26, 200, 20).build());

        EditBox min = new EditBox(this.font, cx - 100, 62, 64, 16, Component.literal("min"));
        min.setMaxLength(12);
        min.setValue(Fmt.shortNum(c.queueMin));
        min.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v >= 0) c.queueMin = v;
        });
        addRenderableWidget(min);

        EditBox max = new EditBox(this.font, cx - 32, 62, 64, 16, Component.literal("max"));
        max.setMaxLength(12);
        max.setValue(Fmt.shortNum(c.queueMax));
        max.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v > 0) c.queueMax = v;
        });
        addRenderableWidget(max);

        EditBox mult = new EditBox(this.font, cx + 36, 62, 64, 16, Component.literal("multiplier"));
        mult.setMaxLength(6);
        mult.setValue(Fmt.shortNum(c.queueMultiplier));
        mult.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v > 0 && v <= 100) c.queueMultiplier = v;
        });
        addRenderableWidget(mult);

        addRenderableWidget(Button.builder(Component.literal("They WIN"), b -> Alerts.queueWin())
                .bounds(cx - 100, 86, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("They LOSE"), b -> Alerts.queueLose())
                .bounds(cx + 2, 86, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> Alerts.queueNext())
                .bounds(cx - 100, 110, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> Alerts.queueClear())
                .bounds(cx + 2, 110, 98, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(new MainScreen()))
                .bounds(cx - 100, height - 28, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = width / 2;
        Alerts.centered(g, this.font, "Gamble queue", cx, 12, 0xFF5599FF);
        g.text(this.font, "Min", cx - 100, 52, 0xFF5599FF, true);
        g.text(this.font, "Max", cx - 32, 52, 0xFF5599FF, true);
        g.text(this.font, "Win x", cx + 36, 52, 0xFF5599FF, true);
        g.text(this.font, "Win = green + payout. Lose = red 1s, then next.", cx - 100, 136, 0xFF888888, false);

        List<Alerts.QEntry> q = Alerts.queue;
        int y = 152;
        if (q.isEmpty()) {
            g.text(this.font, "Queue is empty", cx - 100, y, 0xFFAAAAAA, true);
            return;
        }
        for (int i = 0; i < q.size() && i < 6; i++) {
            Alerts.QEntry e = q.get(i);
            int color = 0xFFFFFFFF;
            if (i == 0 && Alerts.curState == 1) color = 0xFF55FF55;
            else if (i == 0 && Alerts.curState == 2) color = 0xFFFF5555;
            String line = (i == 0 ? "Now: " : (i + 1) + ". ") + e.name + " - " + Fmt.money(e.amount);
            g.text(this.font, line, cx - 100, y, color, true);
            y += 11;
        }
        if (q.size() > 6) g.text(this.font, "+" + (q.size() - 6) + " more", cx - 100, y, 0xFF888888, false);
    }
}
