package net.runelite.client.plugins.microbot.constructionphials;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "Construction Phials",
        description = "Automates construction training using Phials instead of the butler.",
        tags = {"construction", "phials", "microbot", "planks", "poh"},
        enabledByDefault = false
)
@Slf4j
public class ConstructionPhialsPlugin extends Plugin {

    @Inject private ConstructionPhialsOverlay overlay;
    @Inject private ConstructionPhialsScript script;
    @Inject private OverlayManager overlayManager;
    @Inject private ConstructionPhialsConfig config;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
        log.info("Construction-Phials started");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        log.info("Construction-Phials stopped");
    }

    @Provides
    ConstructionPhialsConfig getConfig(ConfigManager cm) {
        return cm.getConfig(ConstructionPhialsConfig.class);
    }
}
