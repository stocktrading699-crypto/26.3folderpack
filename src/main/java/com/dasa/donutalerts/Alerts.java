package com.dasa.donutalerts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Alerts {
    private Alerts() {}

    public static Config cfg = Config.load();

    // Matches: "Name paid you $1M", "Name paid you $ 11M", "Name paid you $75K", "$1,000,000"
    private static final Pattern PAY = Pattern.compile(
            "(\\S+)\\s+paid you\\s+\\$\\s*([\\d,]*\\.?\\d+)\\s*([KkMmBbTt]?)\\b");

    public static final int TOAST_W = 180, TOAST_H = 38;
    public static final int AUC_W = 230, AUC_H = 62;
    public static final int QUEUE_W = 200;

    // ---------------------------------------------------------------- state
    public static final class Toast {
        final String name;
        final double amount;
        final Config.Tier tier;
        final long start = System.currentTimeMillis();

        Toast(String name, double amount, Config.Tier tier) {
            this.name = name;
            this.amount = amount;
            this.tier = tier;
        }
    }

    private static final List<Toast> toasts = new ArrayList<>();
    private static Toast banner;
    private static final List<Config.Effect> pending = new ArrayList<>();

    // auction
    private static boolean aActive, aEnded;
    private static long aEnd, aEndedUntil;
    private static double aMin, aBid;
    private static int aQty;
    private static String aItem = "", aItemId = "minecraft:elytra", aBidder;
    private static boolean aSum;
    private static long offAt = 0; // when to auto-switch alerts off after an auction (end + late-bid window)
    private static final Map<String, Double> aTotals = new HashMap<>();

    private static String lastKey = "";
    private static long lastKeyTime = 0;

    // ---------------------------------------------------------------- gamble queue
    public static final class QEntry {
        public final String name;
        public final double amount;

        public QEntry(String name, double amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    public static final List<QEntry> queue = new ArrayList<>();

    // state of the person at the front of the queue
    public static int curState = 0;      // 0 = normal, 1 = they win (green), 2 = they lose (red, about to advance)
    public static double curPayout = 0;
    private static long loseUntil = 0;

    // result of the last finished person, shown at the bottom of the queue box
    public static String lastResult = null;
    public static int lastResultColor = 0xFFFFFFFF;

    private static void advance() {
        if (!queue.isEmpty()) {
            QEntry cur = queue.remove(0);
            if (curState == 1) {
                lastResult = cur.name + " won " + Fmt.money(curPayout);
                lastResultColor = 0xFF55FF55;
            } else if (curState == 2) {
                lastResult = cur.name + " lost " + Fmt.money(cur.amount);
                lastResultColor = 0xFFFF5555;
            }
        }
        curState = 0;
        curPayout = 0;
        loseUntil = 0;
    }

    /** Bind: go to the next person (whether you marked win/lose or not). */
    public static void queueNext() {
        advance();
    }

    public static void queueClear() {
        queue.clear();
        curState = 0;
        curPayout = 0;
        loseUntil = 0;
    }

    /** Bind: they win. Text turns green and shows the payout (stake x multiplier). Stays until you press next. */
    public static void queueWin() {
        if (queue.isEmpty() || curState == 2) return;
        curState = 1;
        curPayout = queue.get(0).amount * cfg.queueMultiplier;
    }

    /** Bind: they lose. Text turns red for 1 second, then it moves to the next person. */
    public static void queueLose() {
        if (queue.isEmpty() || curState == 2) return;
        curState = 2;
        loseUntil = System.currentTimeMillis() + 1000;
    }

    // ---------------------------------------------------------------- input
    public static void onMessage(net.minecraft.network.chat.Component message) {
        try {
            handleMessage(message);
        } catch (Throwable t) {
            System.err.println("[DonutAlerts] message error: " + t);
        }
    }

    private static void handleMessage(net.minecraft.network.chat.Component message) {
        Matcher m = PAY.matcher(message.getString());
        if (!m.find()) return;
        Double parsed = Fmt.parseLoose(m.group(2) + m.group(3));
        if (parsed == null) return;
        double amount = parsed;
        String name = m.group(1);

        // ignore the same message arriving twice
        String key = name + "|" + amount;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastKeyTime < 150) return;
        lastKey = key;
        lastKeyTime = now;

        handlePayment(name, amount);
    }

    public static void handlePayment(String name, double amount) {
        // Bids still count for a few seconds after the end (stream delay)
        boolean late = aEnded && System.currentTimeMillis() <= aEnd + cfg.lateBidSeconds * 1000L;
        if (aActive || late) {
            double total = aSum ? aTotals.merge(name, amount, Double::sum) : amount;
            if (total >= aMin && total > aBid) {
                aBid = total;
                aBidder = name;
                resolveHead(name);
                if (late) playSound("Level Up");
            }
        }

        // gamble queue (not while an auction is collecting bids)
        if (cfg.queueEnabled && !(aActive || late) && amount >= cfg.queueMin && amount <= cfg.queueMax) {
            queue.add(new QEntry(name, amount));
            resolveHead(name);
        }

        if (!cfg.enabled || amount < cfg.globalMin) return;
        Config.Tier t = cfg.tierFor(amount);
        if (t == null) return;
        trigger(name, amount, t);
    }

    public static void trigger(String name, double amount, Config.Tier t) {
        resolveHead(name);
        Toast toast = new Toast(name, amount, t);
        if (t.display == Config.Display.CENTER) {
            banner = toast;
        } else {
            toasts.add(0, toast);
            while (toasts.size() > 5) toasts.remove(toasts.size() - 1);
        }
        if (t.effect != Config.Effect.NONE) pending.add(t.effect);
        playSound(t.sound);
    }

    public static void playSound(String friendlyName) {
        String id = Config.SOUNDS.get(friendlyName);
        if (id == null || id.isEmpty()) return;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(ident), 1.0f, cfg.volume));
    }

    // ---------------------------------------------------------------- auction
    public static void startAuction() {
        aActive = true;
        aEnded = false;
        int secs = Fmt.parseDuration(cfg.auctionTime);
        if (secs <= 0) secs = 30;
        aEnd = System.currentTimeMillis() + secs * 1000L;
        aMin = cfg.auctionMin;
        aQty = cfg.auctionQty;
        aItemId = cfg.auctionItemId;
        aItem = itemName(aItemId);
        aBidder = null;
        aBid = 0;
        aSum = cfg.sumBids;
        offAt = 0;
        aTotals.clear();
        if (cfg.autoWithAuction) cfg.enabled = true;
    }

    private static Item itemOf(String id) {
        Identifier i = Identifier.tryParse(id == null ? "" : id);
        if (i == null) return Items.ELYTRA;
        return BuiltInRegistries.ITEM.getOptional(i).orElse(Items.ELYTRA);
    }

    public static String itemName(String id) {
        return new ItemStack(itemOf(id)).getHoverName().getString();
    }

    public static ItemStack itemStack(String id) {
        return new ItemStack(itemOf(id));
    }

    public static void stopAuction() {
        aActive = false;
        aEnded = false;
        offAt = 0;
        if (cfg.autoWithAuction) cfg.enabled = false;
    }

    public static String statusLine() {
        if (aActive) return "Running - highest: " + (aBidder == null ? "no bids yet" : aBidder + " " + Fmt.money(aBid));
        if (aEnded) return "Ended - " + (aBidder == null ? "no winner" : "winner " + aBidder + " " + Fmt.money(aBid));
        return "No auction running";
    }

    // ---------------------------------------------------------------- positions
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static float clampScale(float v) {
        return Math.round(Math.max(0.5f, Math.min(2.5f, v)) * 100f) / 100f;
    }

    public static int toastW() { return Math.round(TOAST_W * cfg.toastScale); }
    public static int toastH() { return Math.round(TOAST_H * cfg.toastScale); }
    public static int aucW() { return Math.round(AUC_W * cfg.auctionScale); }
    public static int aucH() { return Math.round(AUC_H * cfg.auctionScale); }
    public static int queueW() { return Math.round(QUEUE_W * cfg.queueScale); }

    /** Height of the queue box (unscaled) for a queue of this size. */
    public static int queueBoxH(int size) {
        int extra = lastResult != null ? 12 : 0;
        if (size == 0) return 38 + extra;
        int waiting = size - 1;
        int shown = Math.min(waiting, 4);
        return 52 + shown * 11 + (waiting > shown ? 11 : 0) + extra;
    }

    public static int[] toastPos(int w, int h) {
        return new int[]{
                clamp((int) (cfg.toastX * w), 0, Math.max(0, w - toastW())),
                clamp((int) (cfg.toastY * h), 0, Math.max(0, h - toastH()))};
    }

    public static int[] auctionPos(int w, int h) {
        return new int[]{
                clamp((int) (cfg.auctionX * w), 0, Math.max(0, w - aucW())),
                clamp((int) (cfg.auctionY * h), 0, Math.max(0, h - aucH()))};
    }

    public static int[] queuePos(int w, int h) {
        return new int[]{
                clamp((int) (cfg.queueX * w), 0, Math.max(0, w - queueW())),
                clamp((int) (cfg.queueY * h), 0, Math.max(0, h - Math.round(120 * cfg.queueScale)))};
    }

    // ---------------------------------------------------------------- particles
    private static final class P {
        float x, y, vx, vy, g, drag, age, life, size, delay, fuse;
        int color;
        boolean rocket;
    }

    private static final List<P> parts = new ArrayList<>();
    private static final Random R = new Random();
    private static float flash = 0;
    private static long lastNanos = 0;

    private static final int[] CONFETTI = {0xFF5555, 0x55FF55, 0x5599FF, 0xFFD700, 0xFF66CC, 0x55FFFF};
    private static final int[] FIRE = {0xFFFFAA, 0xFFD700, 0xFF8833, 0xFF4422};

    private static void spawn(Config.Effect e, int w, int h) {
        switch (e) {
            case CONFETTI -> confetti(w, h);
            case ROCKETS -> rockets(w, h);
            case EXPLOSION -> explosion(w, h);
            case EVERYTHING -> {
                confetti(w, h);
                rockets(w, h);
                explosion(w, h);
            }
            default -> {}
        }
    }

    private static void confetti(int w, int h) {
        for (int i = 0; i < 170; i++) {
            P p = new P();
            p.x = R.nextFloat() * w;
            p.y = -R.nextFloat() * h * 0.4f;
            p.vx = (R.nextFloat() - 0.5f) * 50f;
            p.vy = 40f + R.nextFloat() * 90f;
            p.g = 40f;
            p.life = 5f;
            p.size = 2f + R.nextFloat() * 3f;
            p.color = CONFETTI[R.nextInt(CONFETTI.length)];
            parts.add(p);
        }
    }

    private static void rockets(int w, int h) {
        for (int i = 0; i < 6; i++) {
            P p = new P();
            p.rocket = true;
            p.delay = i * 0.25f + R.nextFloat() * 0.2f;
            p.x = w * (0.15f + 0.7f * R.nextFloat());
            p.y = h;
            float burstY = h * (0.2f + 0.3f * R.nextFloat());
            p.fuse = 1.0f + R.nextFloat() * 0.4f;
            p.vy = -(h - burstY) / p.fuse;
            p.vx = (R.nextFloat() - 0.5f) * 20f;
            p.color = 0xFFD700;
            parts.add(p);
        }
    }

    private static void burst(List<P> out, float x, float y) {
        int base = CONFETTI[R.nextInt(CONFETTI.length)];
        for (int i = 0; i < 36; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            float sp = 40f + R.nextFloat() * 90f;
            P p = new P();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(ang) * sp;
            p.vy = (float) Math.sin(ang) * sp;
            p.g = 60f;
            p.drag = 0.5f;
            p.life = 1.0f + R.nextFloat() * 0.6f;
            p.size = 2f;
            p.color = R.nextInt(3) == 0 ? 0xFFFFFF : base;
            out.add(p);
        }
    }

    private static void explosion(int w, int h) {
        flash = 0.35f;
        float cx = w / 2f, cy = h * 0.32f;
        for (int i = 0; i < 150; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            float sp = 30f + (float) Math.pow(R.nextFloat(), 0.6) * 280f;
            P p = new P();
            p.x = cx;
            p.y = cy;
            p.vx = (float) Math.cos(ang) * sp;
            p.vy = (float) Math.sin(ang) * sp;
            p.g = 80f;
            p.drag = 1.2f;
            p.life = 0.9f + R.nextFloat() * 0.8f;
            p.size = 2f + R.nextFloat() * 3f;
            p.color = FIRE[R.nextInt(FIRE.length)];
            parts.add(p);
        }
    }

    private static void update(float dt) {
        List<P> add = new ArrayList<>();
        for (Iterator<P> it = parts.iterator(); it.hasNext(); ) {
            P p = it.next();
            if (p.delay > 0) {
                p.delay -= dt;
                continue;
            }
            p.age += dt;
            if (p.rocket) {
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                P tr = new P();
                tr.x = p.x;
                tr.y = p.y;
                tr.vy = 20f;
                tr.life = 0.35f;
                tr.size = 1.5f;
                tr.color = 0xFF8833;
                add.add(tr);
                if (p.age >= p.fuse) {
                    burst(add, p.x, p.y);
                    it.remove();
                }
            } else {
                float damp = Math.max(0f, 1f - p.drag * dt);
                p.vx *= damp;
                p.vy = p.vy * damp + p.g * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.age >= p.life) it.remove();
            }
        }
        parts.addAll(add);
        if (parts.size() > 1500) parts.subList(0, parts.size() - 1500).clear();
        if (flash > 0) flash -= dt;
    }

    private static void drawParticles(GuiGraphicsExtractor g, int w, int h) {
        if (flash > 0) {
            int a = (int) (flash / 0.35f * 90);
            if (a >= 4) g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }
        for (P p : parts) {
            if (p.delay > 0) continue;
            if (p.rocket) {
                g.fill((int) p.x, (int) p.y, (int) p.x + 2, (int) p.y + 5, 0xFFFFE9A0);
                continue;
            }
            float remain = p.life - p.age;
            int a = (int) (255 * Math.max(0f, Math.min(1f, remain / 0.4f)));
            if (a < 4) continue;
            int s = Math.max(1, Math.round(p.size));
            g.fill((int) p.x, (int) p.y, (int) p.x + s, (int) p.y + s, (a << 24) | p.color);
        }
    }

    // ---------------------------------------------------------------- drawing helpers
    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Draws text centered on centerX (the new GUI API has no one-liner for plain strings). */
    public static void centered(GuiGraphicsExtractor g, Font f, String s, int centerX, int y, int color) {
        g.text(f, s, centerX - f.width(s) / 2, y, color, true);
    }

    public static String sampleName() {
        return Minecraft.getInstance().getUser().getName();
    }

    // ---- player heads: downloaded once from minotar.net (8x8 pixels); letter placeholder while loading / if it fails
    private interface HeadDrawer {
        void draw(GuiGraphicsExtractor g, int x, int y, int size);
    }

    private static final Map<String, HeadDrawer> heads = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();

    public static void resolveHead(String name) {
        if (name == null || name.isEmpty() || heads.containsKey(name) || !loading.add(name)) return;
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://minotar.net/helm/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/32.png";
                URLConnection con = java.net.URI.create(url).toURL().openConnection();
                con.setConnectTimeout(4000);
                con.setReadTimeout(4000);
                con.setRequestProperty("User-Agent", "DonutAlerts");
                BufferedImage img;
                try (InputStream in = con.getInputStream()) {
                    img = ImageIO.read(in);
                }
                if (img == null || img.getWidth() < 8 || img.getHeight() < 8) return;
                int[] px = new int[64];
                int iw = img.getWidth(), ih = img.getHeight();
                for (int py = 0; py < 8; py++) {
                    for (int pxx = 0; pxx < 8; pxx++) {
                        int sx = pxx * iw / 8 + iw / 16;
                        int sy = py * ih / 8 + ih / 16;
                        px[py * 8 + pxx] = 0xFF000000 | (img.getRGB(sx, sy) & 0xFFFFFF);
                    }
                }
                heads.put(name, (g, x, y, s) -> {
                    for (int j = 0; j < 8; j++) {
                        for (int i = 0; i < 8; i++) {
                            int x1 = x + Math.round(i * s / 8f), x2 = x + Math.round((i + 1) * s / 8f);
                            int y1 = y + Math.round(j * s / 8f), y2 = y + Math.round((j + 1) * s / 8f);
                            g.fill(x1, y1, x2, y2, px[j * 8 + i]);
                        }
                    }
                });
            } catch (Throwable ignored) {
                // keep the placeholder
            }
        });
    }

    public static void drawHead(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        Font f = font();
        if (name == null || name.isEmpty() || name.equals("?")) {
            g.fill(x, y, x + size, y + size, 0xFF5A5A70);
            g.text(f, "?", x + size / 2 - 3, y + size / 2 - 4, 0xFFFFFFFF, true);
            return;
        }
        HeadDrawer d = heads.get(name);
        if (d != null) {
            d.draw(g, x, y, size);
            return;
        }
        resolveHead(name);
        int c = Math.abs(name.hashCode());
        int col = 0xFF000000 | ((80 + (c & 0x7F)) << 16) | ((80 + ((c >> 7) & 0x7F)) << 8) | (80 + ((c >> 14) & 0x7F));
        g.fill(x, y, x + size, y + size, col);
        g.text(f, name.substring(0, 1).toUpperCase(), x + size / 2 - 3, y + size / 2 - 4, 0xFFFFFFFF, true);
    }

    private static void drawScaledCentered(GuiGraphicsExtractor g, String text, float cx, float y, float scale, int argb) {
        Font f = font();
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(cx, y);
        m.scale(scale, scale);
        g.text(f, text, -f.width(text) / 2, 0, argb, true);
        m.popMatrix();
    }

    public static void drawToast(GuiGraphicsExtractor g, int x, int y, String name, double amount, int color) {
        Font f = font();
        g.fill(x, y, x + TOAST_W, y + TOAST_H, 0xD00E1420);
        g.outline(x, y, TOAST_W, TOAST_H, 0xFF000000 | color);
        drawHead(g, name, x + 5, y + 5, 28);
        g.text(f, name + " paid you", x + 40, y + 7, 0xFFB8B8C8, false);
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(x + 40, y + 19);
        m.scale(1.6f, 1.6f);
        g.text(f, Fmt.money(amount), 0, 0, 0xFF000000 | color, true);
        m.popMatrix();
    }

    private static String fmtTime(long ms) {
        long s = (long) Math.ceil(ms / 1000.0);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static void drawQueueBox(GuiGraphicsExtractor g, int x, int y, List<QEntry> list) {
        Font t = font();
        boolean live = list == queue;
        int state = live ? curState : 0;
        int h = queueBoxH(list.size());
        int waiting = Math.max(0, list.size() - 1);
        int shown = Math.min(waiting, 4);
        g.fill(x, y, x + QUEUE_W, y + h, 0xD00E1420);
        g.outline(x, y, QUEUE_W, h, 0xFF5599FF);
        String title = "Gamble Queue" + (waiting > 0 ? " (+" + waiting + " waiting)" : "");
        g.text(t, title, x + 8, y + 6, 0xFF5599FF, true);
        if (list.isEmpty()) {
            g.text(t, "Empty - waiting for payments", x + 8, y + 22, 0xFFAAAAAA, false);
        } else {
            QEntry first = list.get(0);
            int c1 = state == 1 ? 0xFF55FF55 : state == 2 ? 0xFFFF5555 : 0xFFFFFFFF;
            int c2 = state == 1 ? 0xFF55FF55 : state == 2 ? 0xFFFF5555 : 0xFF55FF55;
            String l2;
            if (state == 1) l2 = "Payout: " + Fmt.money(curPayout) + " (x" + Fmt.shortNum(cfg.queueMultiplier) + ")";
            else if (state == 2) l2 = "LOST: " + Fmt.money(first.amount);
            else l2 = "Paid: " + Fmt.money(first.amount);
            drawHead(g, first.name, x + 8, y + 18, 28);
            g.text(t, "Turn: " + first.name, x + 42, y + 21, c1, true);
            g.text(t, l2, x + 42, y + 33, c2, true);
            int yy = y + 50;
            for (int i = 1; i <= shown; i++) {
                QEntry e = list.get(i);
                g.text(t, (i + 1) + ". " + e.name, x + 8, yy, 0xFF9DB8FF, false);
                String m = Fmt.money(e.amount);
                g.text(t, m, x + QUEUE_W - 8 - t.width(m), yy, 0xFF55FF55, false);
                yy += 11;
            }
            if (waiting > shown) g.text(t, "+" + (waiting - shown) + " more", x + 8, yy, 0xFF888888, false);
        }
        if (lastResult != null) {
            g.fill(x + 6, y + h - 14, x + QUEUE_W - 6, y + h - 13, 0x55FFFFFF);
            g.text(t, "Last: " + lastResult, x + 8, y + h - 11, lastResultColor, false);
        }
    }

    public static void drawAuctionBox(GuiGraphicsExtractor g, int x, int y, boolean active, long msLeft, double min,
                                      int qty, String item, String bidder, double bid) {
        Font t = font();
        g.fill(x, y, x + AUC_W, y + AUC_H, 0xD00E1420);
        g.outline(x, y, AUC_W, AUC_H, 0xFFFFAA00);
        String title = active ? "Bidding - " + fmtTime(msLeft) + " left" : "Bidding Ended!";
        g.text(t, title, x + 8, y + 6, 0xFFFFAA00, true);
        if (active) {
            String m = "Min " + Fmt.money(min);
            g.text(t, m, x + AUC_W - 8 - t.width(m), y + 6, 0xFFAAAAAA, false);
        } else if (msLeft > 0) {
            String m = "Late bids " + ((msLeft + 999) / 1000) + "s";
            g.text(t, m, x + AUC_W - 8 - t.width(m), y + 6, 0xFFAAAAAA, false);
        }
        drawHead(g, bidder == null ? "?" : bidder, x + 8, y + 22, 32);
        if (bidder == null) {
            g.text(t, active ? "No bids yet" : "No winner", x + 46, y + 34, 0xFFAAAAAA, false);
        } else if (active) {
            g.text(t, bidder, x + 46, y + 26, 0xFF5599FF, false);
            g.text(t, Fmt.money(bid), x + 46, y + 38, 0xFF55FF55, true);
        } else {
            g.text(t, "Winner:", x + 46, y + 24, 0xFFAAAAAA, false);
            g.text(t, bidder + " (" + Fmt.money(bid) + ")", x + 46, y + 36, 0xFF5599FF, false);
        }
        String it = qty + "x " + item;
        g.text(t, it, x + AUC_W - 8 - t.width(it), y + 22, 0xFFFFFFFF, true);
    }

    // ---------------------------------------------------------------- main render
    public static void renderAll(GuiGraphicsExtractor g) {
        var window = Minecraft.getInstance().getWindow();
        int w = window.getGuiScaledWidth(), h = window.getGuiScaledHeight();
        long nn = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (nn - lastNanos) / 1e9f);
        lastNanos = nn;

        if (offAt > 0 && System.currentTimeMillis() >= offAt) {
            offAt = 0;
            if (cfg.autoWithAuction) cfg.enabled = false;
        }
        if (curState == 2 && System.currentTimeMillis() >= loseUntil) advance();

        for (Config.Effect e : pending) spawn(e, w, h);
        pending.clear();
        update(dt);
        drawParticles(g, w, h);
        renderBanner(g, w, h);
        renderToasts(g, w, h);
        renderAuction(g, w, h);
        renderQueue(g, w, h);
    }

    private static void drawBanner(GuiGraphicsExtractor g, int w, int h, String name, double amount, int color,
                                   float pop, int a) {
        float bs = cfg.bannerScale;
        float cx = w / 2f, y = h * 0.2f;
        int hs = Math.round(32 * bs);
        drawHead(g, name, (int) cx - hs / 2, (int) y, hs);
        drawScaledCentered(g, name + " paid you", cx, y + 38 * bs, 1.6f * pop * bs, (a << 24) | 0xFFFFFF);
        drawScaledCentered(g, Fmt.money(amount), cx, y + 54 * bs, 4.5f * pop * bs, (a << 24) | color);
    }

    public static void drawBannerSample(GuiGraphicsExtractor g, int w, int h) {
        drawBanner(g, w, h, sampleName(), 10_000_000, 0xFFAA33, 1f, 255);
    }

    private static void renderBanner(GuiGraphicsExtractor g, int w, int h) {
        if (banner == null) return;
        float t = (System.currentTimeMillis() - banner.start) / 1000f;
        float total = 4.5f;
        if (t > total) {
            banner = null;
            return;
        }
        float fade = Math.min(1f, (total - t) / 0.7f);
        float pop = t < 0.25f ? 0.5f + 0.5f * (t / 0.25f) : 1f;
        int a = Math.max(4, (int) (255 * fade));
        drawBanner(g, w, h, banner.name, banner.amount, banner.tier.color, pop, a);
    }

    private static void renderToasts(GuiGraphicsExtractor g, int w, int h) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.start > 5000);
        if (toasts.isEmpty()) return;
        int[] pos = toastPos(w, h);
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(pos[0], pos[1]);
        m.scale(cfg.toastScale, cfg.toastScale);
        try {
            int i = 0;
            for (Toast t : toasts) {
                drawToast(g, 0, i * (TOAST_H + 4), t.name, t.amount, t.tier.color);
                i++;
            }
        } finally {
            m.popMatrix();
        }
    }

    private static void renderAuction(GuiGraphicsExtractor g, int w, int h) {
        if (!aActive && !aEnded) return;
        long now = System.currentTimeMillis();
        if (aActive && now >= aEnd) {
            aActive = false;
            aEnded = true;
            aEndedUntil = now + 15000;
            if (cfg.autoWithAuction) offAt = aEnd + cfg.lateBidSeconds * 1000L;
            if (aBidder != null) playSound("Level Up");
        }
        if (aEnded && now > aEndedUntil) {
            aEnded = false;
            return;
        }
        int[] pos = auctionPos(w, h);
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(pos[0], pos[1]);
        m.scale(cfg.auctionScale, cfg.auctionScale);
        try {
            drawAuctionBox(g, 0, 0, aActive,
                    aActive ? Math.max(0, aEnd - now) : Math.max(0, aEnd + cfg.lateBidSeconds * 1000L - now),
                    aMin, aQty, aItem, aBidder, aBid);
        } finally {
            m.popMatrix();
        }
    }

    private static void renderQueue(GuiGraphicsExtractor g, int w, int h) {
        if (!cfg.queueEnabled) return;
        int[] pos = queuePos(w, h);
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(pos[0], pos[1]);
        m.scale(cfg.queueScale, cfg.queueScale);
        try {
            drawQueueBox(g, 0, 0, queue);
        } finally {
            m.popMatrix();
        }
    }
}
