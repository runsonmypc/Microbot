package net.runelite.client.plugins.microbot.farmingcontract;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.awt.*;
import net.runelite.client.plugins.microbot.Microbot;

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
    
    @Inject
    private FarmingWorld farmingWorld;
    
    @Inject
    private ConfigManager configManager;
    
    private FarmingContractScript script;
    
    @Getter
    @Setter
    private String status = "Idle";
    
    @Getter
    private boolean started = false;
    
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
        
        started = true;
        script = new FarmingContractScript(this, config);
        script.run();
    }
    
    @Override
    protected void shutDown() {
        log.info("Stopping Farming Contract plugin");
        started = false;
        
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        
        if (script != null) {
            script.shutdown();
            script = null;
        }
        
        status = "Stopped";
    }
    
    /**
     * Called by the script when it completes or needs to stop.
     * This will toggle the plugin off in the RuneLite menu.
     */
    public void stopPlugin() {
        log.info("Script requested plugin stop - toggling off in menu");
        Microbot.stopPlugin(this);
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        
        String msg = event.getMessage().toLowerCase();
        
        // Check for contract completion messages
        // Common messages: "Congratulations, you've completed a farming contract!"
        // "You have completed the Farming Guild contract."
        // "Jane will be pleased with your work."
        if (msg.contains("completed") && (msg.contains("contract") || msg.contains("farming"))) {
            log.info("Contract completion detected: {}", msg);
            if (script != null) {
                script.onChatMessage(event);
            }
        }
        // Also check for the reward message
        else if (msg.contains("jane") && msg.contains("pleased")) {
            log.info("Contract completion detected (Jane pleased): {}", msg);
            if (script != null) {
                script.onChatMessage(event);
            }
        }
    }
}