package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UseLampEvent implements BlockingEvent {

    private final EventHandlerConfig config;

    public UseLampEvent(EventHandlerConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        return config.checkForLamps() && Rs2Inventory.contains(ItemID.LAMP);
    }

    @Override
    public boolean execute() {
        log.debug("Attempting to use lamp with skill: {}", config.lampSkill());
        
        // Use the centralized lamp utility
        boolean success = LampUtility.useLamp(config.lampSkill());
        
        if (!success) {
            log.warn("Failed to use lamp with skill: {}", config.lampSkill());
        }
        
        return success;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    // Skill widget ID mapping moved to LampUtility class
}