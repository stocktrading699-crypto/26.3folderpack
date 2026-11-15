package com.dasa.donutalerts;

import com.dasa.donutalerts.screen.AuctionScreen;
import com.dasa.donutalerts.screen.BaseScreen;
import com.dasa.donutalerts.screen.HudEditScreen;
import com.dasa.donutalerts.screen.MainScreen;
import com.dasa.donutalerts.screen.QueueScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class DonutAlertsClient implements ClientModInitializer {
    // Commands run while the chat box is open, so the screen is opened on the next tick.
    private static Supplier<Screen> pendingScreen;

    private static String nowText() {
        if (Alerts.queue.isEmpty()) return "Queue is empty";
        Alerts.QEntry e = Alerts.queue.get(0);
        return "Now: " + e.name + " (" + Fmt.money(e.amount) + ")";
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, code, category));
    }

    @Override
    public void onInitializeClient() {
        // On launch: alerts are ON (or OFF until an auction starts, if "follow auction" is enabled)
        Alerts.cfg.enabled = !Alerts.cfg.autoWithAuction;

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("donutalerts", "main"));
        int none = InputConstants.UNKNOWN.getValue(); // unbound by default

        KeyMapping menuKey = key("key.donutalerts.menu", InputConstants.KEY_K, category);
        KeyMapping moveKey = key("key.donutalerts.move", InputConstants.KEY_J, category);
        KeyMapping auctionKey = key("key.donutalerts.auction", InputConstants.KEY_L, category);
        KeyMapping queueMenuKey = key("key.donutalerts.queue_menu", none, category);
        KeyMapping queueWinKey = key("key.donutalerts.queue_win", none, category);
        KeyMapping queueNextKey = key("key.donutalerts.queue_next", none, category);
        KeyMapping queueLoseKey = key("key.donutalerts.queue_lose", none, category);

        // "X paid you $1M" arrives as a server (game) message
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> Alerts.onMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> Alerts.onMessage(message));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            // /donutalerts                    -> main menu
            // /donutalerts move               -> move / resize boxes
            // /donutalerts auction            -> auction menu
            // /donutalerts queue              -> gamble queue menu (on/off + settings)
            // /donutalerts win / lose / next  -> same as the three queue keybinds
            // /donutalerts clear              -> empty the queue
            dispatcher.register(ClientCommands.literal("donutalerts")
                    .executes(c -> {
                        pendingScreen = MainScreen::new;
                        return 1;
                    })
                    .then(ClientCommands.literal("move").executes(c -> {
                        pendingScreen = HudEditScreen::new;
                        return 1;
                    }))
                    .then(ClientCommands.literal("auction").executes(c -> {
                        pendingScreen = AuctionScreen::new;
                        return 1;
                    }))
                    .then(ClientCommands.literal("queue").executes(c -> {
                        pendingScreen = QueueScreen::new;
                        return 1;
                    }))
                    .then(ClientCommands.literal("next").executes(c -> {
                        Alerts.queueNext();
                        c.getSource().sendFeedback(Component.literal(nowText()));
                        return 1;
                    }))
                    .then(ClientCommands.literal("win").executes(c -> {
                        Alerts.queueWin();
                        return 1;
                    }))
                    .then(ClientCommands.literal("lose").executes(c -> {
                        Alerts.queueLose();
                        return 1;
                    }))
                    .then(ClientCommands.literal("clear").executes(c -> {
                        Alerts.queueClear();
                        c.getSource().sendFeedback(Component.literal("Queue cleared"));
                        return 1;
                    })));

            // /bidding        -> auction menu (auto-fills the item you're holding)
            // /bidding stop   -> stop / clear the auction
            dispatcher.register(ClientCommands.literal("bidding")
                    .executes(c -> {
                        pendingScreen = AuctionScreen::new;
                        return 1;
                    })
                    .then(ClientCommands.literal("stop").executes(c -> {
                        Alerts.stopAuction();
                        c.getSource().sendFeedback(Component.literal("Auction stopped"));
                        return 1;
                    })));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) client.gui.setScreen(new MainScreen());
            while (moveKey.consumeClick()) client.gui.setScreen(new HudEditScreen());
            while (auctionKey.consumeClick()) client.gui.setScreen(new AuctionScreen());
            while (queueMenuKey.consumeClick()) client.gui.setScreen(new QueueScreen());
            while (queueWinKey.consumeClick()) Alerts.queueWin();
            while (queueNextKey.consumeClick()) Alerts.queueNext();
            while (queueLoseKey.consumeClick()) Alerts.queueLose();
            if (pendingScreen != null) {
                Supplier<Screen> s = pendingScreen;
                pendingScreen = null;
                client.gui.setScreen(s.get());
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("donutalerts", "alerts"), (graphics, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof BaseScreen) return; // the screen draws it itself
                    try {
                        Alerts.renderAll(graphics);
                    } catch (Throwable t) {
                        System.err.println("[DonutAlerts] render error: " + t);
                    }
                });
    }
}
