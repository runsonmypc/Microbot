package net.runelite.client.plugins.microbot.farmingcontract;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
    name = "<html><font color=\"#32C8CD\">[ ▢ ]</font> Farming Contract</html>",
    description = "Automates Farming Guild contracts",
    tags = {"microbot", "farming", "contract", "guild"},
    enabledByDefault = false
)
@Slf4j
public class FarmingContractPlugin extends Plugin {
    
    @Inject
    private FarmingContractConfig config;
    
    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private FarmingContractOverlay overlay;
    
    private FarmingContractScript script;
    
    @Getter
    @Setter
    private String status = "Idle";
    
    @Provides
    FarmingContractConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FarmingContractConfig.class);
    }
    
    @Override
    protected void startUp() {
        log.info("Starting Farming Contract plugin");
        
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        
        script = new FarmingContractScript(this, config);
        script.run();
    }
    
    @Override
    protected void shutDown() {
        log.info("Stopping Farming Contract plugin");
        
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        
        if (script != null) {
            script.shutdown();
            script = null;
        }
        
        status = "Stopped";
    }
}