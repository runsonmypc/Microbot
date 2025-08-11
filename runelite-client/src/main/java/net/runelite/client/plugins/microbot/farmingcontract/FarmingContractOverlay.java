package net.runelite.client.plugins.microbot.farmingcontract;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.farming.Produce;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class FarmingContractOverlay extends OverlayPanel {
    
    private final FarmingContractPlugin plugin;
    private final FarmingContractConfig config;
    private FarmingContractManager contractManager;
    
    @Inject
    public FarmingContractOverlay(FarmingContractPlugin plugin, FarmingContractConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Farming Contract")
            .color(Color.CYAN)
            .build());
        
        // Get contract manager
        if (contractManager == null) {
            try {
                contractManager = Microbot.getInjector().getInstance(FarmingContractManager.class);
            } catch (Exception e) {
                // Contract manager not available yet
            }
        }
        
        // Display current contract
        if (contractManager != null && contractManager.hasContract()) {
            Produce contract = contractManager.getContract();
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Contract:")
                .right(contract != null ? contract.getName() : "None")
                .build());
            
            // Display contract state
            String stateText = "Unknown";
            Color stateColor = Color.GRAY;
            
            switch (contractManager.getSummary()) {
                case EMPTY:
                    stateText = "Empty patch";
                    stateColor = Color.YELLOW;
                    break;
                case IN_PROGRESS:
                    stateText = "Growing";
                    stateColor = Color.ORANGE;
                    
                    // Show time remaining if available
                    long completionTime = contractManager.getCompletionTime();
                    if (completionTime > 0 && completionTime != Long.MAX_VALUE) {
                        long remaining = completionTime - Instant.now().getEpochSecond();
                        if (remaining > 0) {
                            Duration duration = Duration.ofSeconds(remaining);
                            long minutes = duration.toMinutes();
                            stateText = String.format("Growing (%d min)", minutes);
                        }
                    }
                    break;
                case COMPLETED:
                    stateText = "Ready to harvest";
                    stateColor = Color.GREEN;
                    break;
                case OCCUPIED:
                    stateText = "Wrong crop";
                    stateColor = Color.RED;
                    break;
                default:
                    break;
            }
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(stateText)
                .rightColor(stateColor)
                .build());
            
            // Check for diseased/dead crops
            if (contractManager.getContractCropState() != null) {
                switch (contractManager.getContractCropState()) {
                    case DISEASED:
                        panelComponent.getChildren().add(LineComponent.builder()
                            .left("⚠ Crop diseased!")
                            .leftColor(Color.ORANGE)
                            .build());
                        break;
                    case DEAD:
                        panelComponent.getChildren().add(LineComponent.builder()
                            .left("☠ Crop dead!")
                            .leftColor(Color.RED)
                            .build());
                        break;
                    default:
                        break;
                }
            }
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Contract:")
                .right("None")
                .build());
        }
        
        // Display plugin status
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Status:")
            .right(plugin.getStatus())
            .build());
        
        // Display farming level and available tier
        int farmingLevel = Microbot.getClient().getRealSkillLevel(net.runelite.api.Skill.FARMING);
        String availableTier = farmingLevel >= 85 ? "Hard" : 
                              farmingLevel >= 65 ? "Medium" : 
                              farmingLevel >= 45 ? "Easy" : "Too Low";
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Farming Lvl:")
            .right(farmingLevel + " (" + availableTier + ")")
            .build());
        
        if (config.autoDowngrade()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Auto downgrade:")
                .right("Enabled")
                .rightColor(Color.GREEN)
                .build());
        }
        
        return super.render(graphics);
    }
}