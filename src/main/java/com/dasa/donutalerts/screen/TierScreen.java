package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TierScreen extends BaseScreen {
    public TierScreen() {
        super(Component.literal("Notification tiers"));
    }

    private static Component colorLabel(int color) {
        String name = Config.COLOR_NAMES[0];
        for (int i = 0; i < Config.COLORS.length; i++) {
            if (Config.COLORS[i] == color) name = Config.COLOR_NAMES[i];
        }
        return Component.literal(name).withStyle(s -> s.withColor(color));
    }

    private static int nextColor(int color) {
        for (int i = 0; i < Config.COLORS.length; i++) {
            if (Config.COLORS[i] == color) return Config.COLORS[(i + 1) % Config.COLORS.length];
        }
        return Config.COLORS[0];
    }

    private static String nextSound(String current) {
        List<String> keys = new ArrayList<>(Config.SOUNDS.keySet());
        int i = keys.indexOf(current);
        return keys.get((i + 1) % keys.size());
    }

    @Override
    protected void init() {
        Config c = Alerts.cfg;
        c.sortTiers();
        int x0 = width / 2 - 200;
        int y = 44;

        for (Config.Tier t : new ArrayList<>(c.tiers)) {
            addRow(t, x0, y);
            y += 22;
        }

        int by = height - 28;
        if (c.tiers.size() < 8) {
            addRenderableWidget(Button.builder(Component.literal("+ Add tier"), b -> {
                double last = c.tiers.isEmpty() ? 500_000 : c.tiers.get(c.tiers.size() - 1).min;
                c.tiers.add(new Config.Tier(last * 2, Config.Display.CENTER, Config.Effect.CONFETTI, "XP Orb", 0x55FFFF));
                rebuildWidgets();
            }).bounds(width / 2 - 105, by, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.minecraft.gui.setScreen(new MainScreen()))
                .bounds(width / 2 + 5, by, 100, 20).build());
    }

    private void addRow(Config.Tier t, int x0, int y) {
        Config c = Alerts.cfg;

        EditBox f = new EditBox(this.font, x0, y, 60, 18, Component.literal("min"));
        f.setMaxLength(12);
        f.setValue(Fmt.shortNum(t.min));
        f.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v > 0) t.min = v;
        });
        addRenderableWidget(f);

        addRenderableWidget(Button.builder(Component.literal(Fmt.cap(t.display.name())), b -> {
            t.display = t.display == Config.Display.CORNER ? Config.Display.CENTER : Config.Display.CORNER;
            b.setMessage(Component.literal(Fmt.cap(t.display.name())));
        }).bounds(x0 + 64, y - 1, 54, 20).build());

        addRenderableWidget(Button.builder(Component.literal(Fmt.cap(t.effect.name())), b -> {
            Config.Effect[] all = Config.Effect.values();
            t.effect = all[(t.effect.ordinal() + 1) % all.length];
            b.setMessage(Component.literal(Fmt.cap(t.effect.name())));
        }).bounds(x0 + 122, y - 1, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal(t.sound), b -> {
            t.sound = nextSound(t.sound);
            b.setMessage(Component.literal(t.sound));
            Alerts.playSound(t.sound);
        }).bounds(x0 + 196, y - 1, 80, 20).build());

        addRenderableWidget(Button.builder(colorLabel(t.color), b -> {
            t.color = nextColor(t.color);
            b.setMessage(colorLabel(t.color));
        }).bounds(x0 + 280, y - 1, 56, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Test"),
                b -> Alerts.trigger(Alerts.sampleName(), t.min, t)).bounds(x0 + 340, y - 1, 32, 20).build());

        addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            c.tiers.remove(t);
            rebuildWidgets();
        }).bounds(x0 + 376, y - 1, 22, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x0 = width / 2 - 200;
        Alerts.centered(g, this.font, "Notification tiers", width / 2, 8, 0xFFFFD700);
        Alerts.centered(g, this.font, "A tier covers payments from its minimum up to the next tier's minimum.",
                width / 2, 20, 0xFFAAAAAA);
        int hy = 34;
        g.text(this.font, "Min", x0, hy, 0xFFFFFFFF, true);
        g.text(this.font, "Where", x0 + 64, hy, 0xFFFFFFFF, true);
        g.text(this.font, "Effect", x0 + 122, hy, 0xFFFFFFFF, true);
        g.text(this.font, "Sound", x0 + 196, hy, 0xFFFFFFFF, true);
        g.text(this.font, "Color", x0 + 280, hy, 0xFFFFFFFF, true);
    }
}
