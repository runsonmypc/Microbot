package net.runelite.client.plugins.microbot.farmingcontract;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("farmingcontract")
public interface FarmingContractConfig extends Config {
    
    @ConfigSection(
        name = "Contract Settings",
        description = "Contract preferences",
        position = 0
    )
    String contractSection = "contractSection";
    
    @ConfigItem(
        keyName = "autoDowngrade",
        name = "Auto Downgrade",
        description = "If you don't have seeds for current contract, automatically request an easier one",
        position = 1,
        section = contractSection
    )
    default boolean autoDowngrade() {
        return true;  // Changed to true by default - makes more sense
    }
    
    @ConfigSection(
        name = "Farming Settings",
        description = "Farming preferences",
        position = 10
    )
    String farmingSection = "farmingSection";
    
    @ConfigItem(
        keyName = "useCompost",
        name = "Use Compost",
        description = "Apply compost to patches",
        position = 11,
        section = farmingSection
    )
    default boolean useCompost() {
        return true;
    }
    
    @ConfigItem(
        keyName = "compostType",
        name = "Compost Type",
        description = "Type of compost to use",
        position = 12,
        section = farmingSection
    )
    default CompostType compostType() {
        return CompostType.ULTRACOMPOST;
    }
    
    
    enum CompostType {
        COMPOST("Compost"),
        SUPERCOMPOST("Supercompost"),
        ULTRACOMPOST("Ultracompost");
        
        private final String name;
        
        CompostType(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}