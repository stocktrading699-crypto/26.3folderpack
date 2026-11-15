package com.dasa.donutalerts.screen;

import com.dasa.donutalerts.Alerts;
import com.dasa.donutalerts.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.List;

public class HudEditScreen extends BaseScreen {
    // 0 = payment alerts, 1 = auction box, 2 = gamble queue
    private int drag = -1;
    private int offX, offY;

    public HudEditScreen() {
        super(Component.literal("Move boxes"));
    }

    private List<Alerts.QEntry> sampleQueue() {
        String me = Alerts.sampleName();
        return List.of(new Alerts.QEntry(me, 50_000_000),
                new Alerts.QEntry("PlayerTwo", 75_000_000),
                new Alerts.QEntry("PlayerThree", 60_000_000));
    }

    private int[] pos(int id) {
        return id == 0 ? Alerts.toastPos(width, height) : id == 1 ? Alerts.auctionPos(width, height) : Alerts.queuePos(width, height);
    }

    private int bw(int id) {
        return id == 0 ? Alerts.toastW() : id == 1 ? Alerts.aucW() : Alerts.queueW();
    }

    private int bh(int id) {
        return id == 0 ? Alerts.toastH() : id == 1 ? Alerts.aucH()
                : Math.round(Alerts.queueBoxH(sampleQueue().size()) * Alerts.cfg.queueScale);
    }

    private int hit(double mx, double my) {
        int[] order = {2, 1, 0};
        for (int id : order) {
            int[] p = pos(id);
            if (mx >= p[0] && mx <= p[0] + bw(id) && my >= p[1] && my <= p[1] + bh(id)) return id;
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Config c = Alerts.cfg;

        // dragging: follow the mouse while the button is held
        if (drag >= 0) {
            double fx = Math.max(0, Math.min(1, (mouseX - offX) / (double) width));
            double fy = Math.max(0, Math.min(1, (mouseY - offY) / (double) height));
            if (drag == 0) {
                c.toastX = fx;
                c.toastY = fy;
            } else if (drag == 1) {
                c.auctionX = fx;
                c.auctionY = fy;
            } else {
                c.queueX = fx;
                c.queueY = fy;
            }
        }

        g.fill(0, 0, width, height, 0x55000000);
        Matrix3x2fStack m = g.pose();

        Alerts.drawBannerSample(g, width, height);
        Alerts.centered(g, this.font, "Big center alerts (scroll anywhere else to resize)",
                width / 2, (int) (height * 0.2f) - 12, 0xFFAAAAAA);

        int[] t = pos(0);
        m.pushMatrix();
        m.translate(t[0], t[1]);
        m.scale(c.toastScale, c.toastScale);
        Alerts.drawToast(g, 0, 0, Alerts.sampleName(), 5_000_000, 0x55FF55);
        m.popMatrix();
        g.text(this.font, "Payment alerts", t[0], t[1] - 10, 0xFFFFFFFF, true);

        int[] a = pos(1);
        m.pushMatrix();
        m.translate(a[0], a[1]);
        m.scale(c.auctionScale, c.auctionScale);
        Alerts.drawAuctionBox(g, 0, 0, true, 30_000, 10_000_000, 1, "Elytra", Alerts.sampleName(), 12_000_000);
        m.popMatrix();
        g.text(this.font, "Auction box", a[0], a[1] - 10, 0xFFFFFFFF, true);

        int[] q = pos(2);
        m.pushMatrix();
        m.translate(q[0], q[1]);
        m.scale(c.queueScale, c.queueScale);
        Alerts.drawQueueBox(g, 0, 0, sampleQueue());
        m.popMatrix();
        g.text(this.font, "Gamble queue" + (c.queueEnabled ? "" : " (OFF)"), q[0], q[1] - 10, 0xFFFFFFFF, true);

        Alerts.centered(g, this.font, "Drag boxes to move. Scroll wheel over a box to resize it. Esc when done.",
                width / 2, 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int id = hit(event.x(), event.y());
            if (id >= 0) {
                int[] p = pos(id);
                drag = id;
                offX = (int) event.x() - p[0];
                offY = (int) event.y() - p[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        drag = -1;
        Alerts.cfg.save();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Config c = Alerts.cfg;
        float d = (float) (scrollY * 0.1);
        int id = hit(mx, my);
        if (id == 0) c.toastScale = Alerts.clampScale(c.toastScale + d);
        else if (id == 1) c.auctionScale = Alerts.clampScale(c.auctionScale + d);
        else if (id == 2) c.queueScale = Alerts.clampScale(c.queueScale + d);
        else c.bannerScale = Alerts.clampScale(c.bannerScale + d);
        c.save();
        return true;
    }
}
