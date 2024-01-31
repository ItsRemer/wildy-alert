package com.wildyalert;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

@Getter
@Setter
public class WildernessOverlay extends Overlay {

    private boolean showAlert = false;

    @Inject
    private WildernessIndicatorConfig config;

    public WildernessOverlay() {
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (showAlert) {
            graphics.setColor(config.alertColor());
            graphics.fillRect(0, 0, graphics.getClipBounds().width, graphics.getClipBounds().height);
        }
        return null;
    }
}