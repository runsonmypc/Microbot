package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

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
        // Use the lamp
        Rs2Inventory.interact(ItemID.LAMP, "Rub");
        
        // Wait for lamp interface to open
        Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(240, 0), 3000);
        
        // Select skill and confirm
        if (Rs2Widget.isWidgetVisible(240, 0)) {
            int skillWidgetId = getSkillWidgetId(config.lampSkill());
            if (skillWidgetId != -1) {
                Rs2Widget.clickWidget(240, skillWidgetId);
                Global.sleep(600, 1200);
                Rs2Widget.clickWidget(240, 26); // Confirm button
                Global.sleep(600, 1200);
            }
            return true;
        }
        
        return false;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private int getSkillWidgetId(Skill skill) {
        switch (skill) {
            case ATTACK:
                return 2;
            case STRENGTH:
                return 3;
            case RANGED:
                return 4;
            case MAGIC:
                return 5;
            case DEFENCE:
                return 6;
            case HITPOINTS:
                return 7;
            case PRAYER:
                return 8;
            case AGILITY:
                return 9;
            case HERBLORE:
                return 10;
            case THIEVING:
                return 11;
            case CRAFTING:
                return 12;
            case RUNECRAFT:
                return 13;
            case SLAYER:
                return 14;
            case FARMING:
                return 15;
            case MINING:
                return 16;
            case SMITHING:
                return 17;
            case FISHING:
                return 18;
            case COOKING:
                return 19;
            case FIREMAKING:
                return 20;
            case WOODCUTTING:
                return 21;
            case FLETCHING:
                return 22;
            case CONSTRUCTION:
                return 23;
            case HUNTER:
                return 24;
            default:
                return -1;
        }
    }
}