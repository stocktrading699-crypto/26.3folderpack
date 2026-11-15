package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import com.dasa.donutalerts.Fmt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Centered menu. Two pages (tabs at the top): Start auction / Settings. */
public class AuctionScreen extends BaseScreen {
    private static final int COLS = 11;

    private static final class Entry {
        final Item item;
        final String lname;

        Entry(Item item) {
            this.item = item;
            this.lname = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
        }
    }

    private static List<Entry> ALL;

    private static List<Entry> all() {
        if (ALL == null) {
            ALL = new ArrayList<>();
            for (Item i : BuiltInRegistries.ITEM) {
                if (i != Items.AIR) ALL.add(new Entry(i));
            }
        }
        return ALL;
    }

    private final List<Entry> view = new ArrayList<>();
    private final List<AbstractWidget> startW = new ArrayList<>();
    private final List<AbstractWidget> settingsW = new ArrayList<>();
    private String searchText = "";
    private int tab = 0; // 0 = start, 1 = settings
    private int page = 0;
    private int rows, per, L, gridY, navY;

    public AuctionScreen() {
        super(Component.literal("Auction"));
        // Opening the menu auto-picks the item (and stack size) you're holding.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
            ItemStack s = mc.player.getMainHandItem();
            Alerts.cfg.auctionItemId = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            Alerts.cfg.auctionQty = s.getCount();
        }
    }

    private void refreshView(String q) {
        view.clear();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        for (Entry e : all()) {
            if (needle.isEmpty() || e.lname.contains(needle)) view.add(e);
        }
        page = 0;
    }

    private int pages() {
        return Math.max(1, (view.size() + per - 1) / per);
    }

    private static String sumLabel() {
        return "Add up bids: " + (Alerts.cfg.sumBids ? "ON" : "OFF");
    }

    private static String followLabel() {
        return "Alerts only during auctions: " + (Alerts.cfg.autoWithAuction ? "ON" : "OFF");
    }

    private void applyTab() {
        for (AbstractWidget w : startW) w.visible = tab == 0;
        for (AbstractWidget w : settingsW) w.visible = tab == 1;
        setFocused(null);
    }

    @Override
    protected void init() {
        startW.clear();
        settingsW.clear();
        Config c = Alerts.cfg;

        rows = Math.max(2, Math.min(4, c.itemRows));
        per = COLS * rows;
        L = width / 2 - 110;
        gridY = 66;
        navY = gridY + rows * 20 + 2;

        refreshView(searchText);

        // ---- tabs at the top
        addRenderableWidget(Button.builder(Component.literal("Start auction"), b -> {
            tab = 0;
            applyTab();
        }).bounds(L, 6, 106, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> {
            tab = 1;
            applyTab();
        }).bounds(L + 114, 6, 106, 16).build());

        // =============== PAGE 1: start auction ===============
        EditBox search = new EditBox(this.font, L, 46, 220, 16, Component.literal("search"));
        search.setHint(Component.literal("Search items..."));
        search.setValue(searchText);
        search.setResponder(s -> {
            searchText = s;
            refreshView(s);
        });
        startW.add(addRenderableWidget(search));

        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < COLS; col++) {
                int idx = r * COLS + col;
                startW.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                    int i = page * per + idx;
                    if (i < view.size()) c.auctionItemId = BuiltInRegistries.ITEM.getKey(view.get(i).item).toString();
                }).bounds(L + col * 20, gridY + r * 20, 20, 20).build()));
            }
        }

        startW.add(addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (page > 0) page--;
        }).bounds(L, navY, 30, 16).build()));
        startW.add(addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (page + 1 < pages()) page++;
        }).bounds(L + 190, navY, 30, 16).build()));

        int fy = navY + 30;
        EditBox qty = new EditBox(this.font, L, fy, 64, 16, Component.literal("amount"));
        qty.setMaxLength(5);
        qty.setValue(String.valueOf(c.auctionQty));
        qty.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v > 0) c.auctionQty = v;
            } catch (NumberFormatException ignored) {}
        });
        startW.add(addRenderableWidget(qty));

        EditBox time = new EditBox(this.font, L + 72, fy, 70, 16, Component.literal("time"));
        time.setMaxLength(8);
        time.setValue(c.auctionTime);
        time.setResponder(s -> {
            if (Fmt.parseDuration(s) > 0) c.auctionTime = s.trim();
        });
        startW.add(addRenderableWidget(time));

        EditBox min = new EditBox(this.font, L + 150, fy, 70, 16, Component.literal("min"));
        min.setMaxLength(12);
        min.setValue(Fmt.shortNum(c.auctionMin));
        min.setResponder(s -> {
            Double v = Fmt.parseLoose(s);
            if (v != null && v >= 0) c.auctionMin = v;
        });
        startW.add(addRenderableWidget(min));

        int by = navY + 52;
        startW.add(addRenderableWidget(Button.builder(Component.literal("START"), b -> {
            Alerts.startAuction();
            onClose();
        }).bounds(L, by, 108, 20).build()));
        startW.add(addRenderableWidget(Button.builder(Component.literal("STOP"), b -> Alerts.stopAuction())
                .bounds(L + 112, by, 108, 20).build()));

        // =============== PAGE 2: settings ===============
        settingsW.add(addRenderableWidget(Button.builder(Component.literal(sumLabel()), b -> {
            c.sumBids = !c.sumBids;
            b.setMessage(Component.literal(sumLabel()));
        }).bounds(L, 36, 220, 20).build()));

        settingsW.add(addRenderableWidget(Button.builder(Component.literal(followLabel()), b -> {
            c.autoWithAuction = !c.autoWithAuction;
            b.setMessage(Component.literal(followLabel()));
        }).bounds(L, 60, 220, 20).build()));

        settingsW.add(addRenderableWidget(Button.builder(Component.literal("Item rows in picker: " + rows), b -> {
            c.itemRows = c.itemRows >= 4 ? 2 : c.itemRows + 1;
            rebuildWidgets();
        }).bounds(L, 84, 220, 20).build()));

        EditBox late = new EditBox(this.font, L, 124, 60, 16, Component.literal("late"));
        late.setMaxLength(2);
        late.setValue(String.valueOf(c.lateBidSeconds));
        late.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 0 && v <= 30) c.lateBidSeconds = v;
            } catch (NumberFormatException ignored) {}
        });
        settingsW.add(addRenderableWidget(late));

        // ---- back (both pages)
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(new MainScreen()))
                .bounds(L, height - 26, 220, 20).build());

        applyTab();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Alerts.cfg;

        // active tab underline
        g.fill(L + tab * 114, 22, L + tab * 114 + 106, 24, 0xFFFFAA00);

        if (tab == 0) {
            g.item(Alerts.itemStack(c.auctionItemId), L + 2, 26);
            g.text(this.font, Alerts.itemName(c.auctionItemId), L + 24, 30, 0xFFFFFFFF, true);

            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < COLS; col++) {
                    int i = page * per + r * COLS + col;
                    if (i >= view.size()) continue;
                    g.item(new ItemStack(view.get(i).item), L + col * 20 + 2, gridY + r * 20 + 2);
                }
            }
            Alerts.centered(g, this.font, "Page " + (page + 1) + " / " + pages(), L + 110, navY + 4, 0xFFAAAAAA);

            int ly = navY + 20;
            g.text(this.font, "Amount", L, ly, 0xFF5599FF, true);
            g.text(this.font, "Time (5m,1h)", L + 72, ly, 0xFF5599FF, true);
            g.text(this.font, "Min bid (1m)", L + 150, ly, 0xFF5599FF, true);
            g.text(this.font, Alerts.statusLine(), L, navY + 76, 0xFFAAAAAA, false);
        } else {
            g.text(this.font, "Late bid window (seconds)", L, 112, 0xFF5599FF, true);
            g.text(this.font, "Bids this long after the timer ends still count", L, 146, 0xFF888888, false);
            g.text(this.font, "(stream delay). Alerts stay on for this window too.", L, 158, 0xFF888888, false);
        }
    }
}
