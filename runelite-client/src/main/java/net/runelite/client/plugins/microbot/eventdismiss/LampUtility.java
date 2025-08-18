package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utility class for lamp-related functionality in the Event Handler plugin.
 * Centralizes lamp usage logic and skill widget mapping to eliminate code duplication.
 */
@Slf4j
public class LampUtility {
    
    // Widget constants
    public static final int LAMP_WIDGET_GROUP = 240;
    public static final int LAMP_WIDGET_ROOT = 0;
    public static final int LAMP_CONFIRM_BUTTON = 26;
    
    // Skill to widget ID mapping using EnumMap for better performance
    private static final Map<Skill, Integer> SKILL_WIDGET_MAP = new EnumMap<>(Skill.class);
    
    static {
        // Initialize the skill to widget ID mapping
        SKILL_WIDGET_MAP.put(Skill.ATTACK, 2);
        SKILL_WIDGET_MAP.put(Skill.STRENGTH, 3);
        SKILL_WIDGET_MAP.put(Skill.RANGED, 4);
        SKILL_WIDGET_MAP.put(Skill.MAGIC, 5);
        SKILL_WIDGET_MAP.put(Skill.DEFENCE, 6);
        SKILL_WIDGET_MAP.put(Skill.HITPOINTS, 7);
        SKILL_WIDGET_MAP.put(Skill.PRAYER, 8);
        SKILL_WIDGET_MAP.put(Skill.AGILITY, 9);
        SKILL_WIDGET_MAP.put(Skill.HERBLORE, 10);
        SKILL_WIDGET_MAP.put(Skill.THIEVING, 11);
        SKILL_WIDGET_MAP.put(Skill.CRAFTING, 12);
        SKILL_WIDGET_MAP.put(Skill.RUNECRAFT, 13);
        SKILL_WIDGET_MAP.put(Skill.SLAYER, 14);
        SKILL_WIDGET_MAP.put(Skill.FARMING, 15);
        SKILL_WIDGET_MAP.put(Skill.MINING, 16);
        SKILL_WIDGET_MAP.put(Skill.SMITHING, 17);
        SKILL_WIDGET_MAP.put(Skill.FISHING, 18);
        SKILL_WIDGET_MAP.put(Skill.COOKING, 19);
        SKILL_WIDGET_MAP.put(Skill.FIREMAKING, 20);
        SKILL_WIDGET_MAP.put(Skill.WOODCUTTING, 21);
        SKILL_WIDGET_MAP.put(Skill.FLETCHING, 22);
        SKILL_WIDGET_MAP.put(Skill.CONSTRUCTION, 23);
        SKILL_WIDGET_MAP.put(Skill.HUNTER, 24);
    }
    
    /**
     * Gets the widget ID for a specific skill.
     * Uses EnumMap for O(1) lookup instead of switch statement.
     * 
     * @param skill The skill to get the widget ID for
     * @return The widget ID for the skill, or -1 if skill is not supported
     */
    public static int getSkillWidgetId(Skill skill) {
        if (skill == null) {
            log.warn("Null skill provided to getSkillWidgetId");
            return -1;
        }
        return SKILL_WIDGET_MAP.getOrDefault(skill, -1);
    }
    
    /**
     * Uses a lamp with the specified skill.
     * Handles the complete lamp usage flow including interface interaction.
     * 
     * @param skill The skill to use the lamp on
     * @return true if the lamp was successfully used, false otherwise
     */
    public static boolean useLamp(Skill skill) {
        if (skill == null) {
            log.error("Cannot use lamp: skill is null");
            return false;
        }
        
        if (!Rs2Inventory.contains(ItemID.LAMP)) {
            log.error("Cannot use lamp: no lamp in inventory");
            return false;
        }
        
        // Interact with the lamp
        if (!Rs2Inventory.interact(ItemID.LAMP, "Rub")) {
            log.error("Failed to interact with lamp");
            return false;
        }
        
        // Wait for lamp interface to open
        if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(LAMP_WIDGET_GROUP, LAMP_WIDGET_ROOT), 3000)) {
            log.error("Lamp interface did not open within timeout");
            return false;
        }
        
        // Select skill and confirm
        if (Rs2Widget.isWidgetVisible(LAMP_WIDGET_GROUP, LAMP_WIDGET_ROOT)) {
            int skillWidgetId = getSkillWidgetId(skill);
            if (skillWidgetId == -1) {
                log.error("Invalid skill selected: {}", skill);
                return false;
            }
            
            // Click the skill
            Rs2Widget.clickWidget(LAMP_WIDGET_GROUP, skillWidgetId);
            Global.sleep(600, 1200);
            
            // Click confirm button
            Rs2Widget.clickWidget(LAMP_WIDGET_GROUP, LAMP_CONFIRM_BUTTON);
            Global.sleep(600, 1200);
            
            log.info("Successfully used lamp on {} skill", skill);
            return true;
        }
        
        log.error("Lamp interface not visible after opening");
        return false;
    }
    
    /**
     * Checks if there is a lamp in the inventory.
     * 
     * @return true if there is a lamp in inventory, false otherwise
     */
    public static boolean hasLampInInventory() {
        return Rs2Inventory.contains(ItemID.LAMP);
    }
    
}