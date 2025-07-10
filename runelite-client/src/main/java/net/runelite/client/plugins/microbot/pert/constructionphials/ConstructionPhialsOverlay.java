package net.runelite.client.plugins.microbot.pert.constructionphials;

import com.google.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Singleton
public class ConstructionPhialsOverlay extends Overlay {

    private final PanelComponent panel = new PanelComponent();
    private final Client client;
    private final ConstructionPhialsScript script;

    @Inject
    public ConstructionPhialsOverlay(Client client, ConstructionPhialsScript script) {
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        this.client = client;
        this.script = script;
    }

    @Override
    public Dimension render(Graphics2D g) {
        panel.getChildren().clear();
        panel.getChildren().add(LineComponent.builder()
                .left("Construction-Phials")
                .right(String.valueOf(ConstructionPhialsScript.VERSION))
                .build());
        panel.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(String.valueOf(script.getState()))
                .build());
        return panel.render(g);
    }
}
