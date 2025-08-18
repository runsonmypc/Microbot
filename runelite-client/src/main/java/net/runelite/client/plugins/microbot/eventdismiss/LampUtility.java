package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.Map;

import static net.runelite.client.plugins.microbot.eventdismiss.LampWidgetConstants.*;

/**
 * Utility class for lamp-related functionality in the Event Handler plugin.
 * Centralizes lamp usage logic and skill widget mapping to eliminate code duplication.
 */
@Slf4j
public class LampUtility {
    
    // Skill to widget ID mapping using EnumMap for better performance
    private static final Map<Skill, Integer> SKILL_WIDGET_MAP = new EnumMap<>(Skill.class);
    
    static {
        // Initialize the skill to widget ID mapping using externalized constants
        SKILL_WIDGET_MAP.put(Skill.ATTACK, WIDGET_ATTACK);
        SKILL_WIDGET_MAP.put(Skill.STRENGTH, WIDGET_STRENGTH);
        SKILL_WIDGET_MAP.put(Skill.RANGED, WIDGET_RANGED);
        SKILL_WIDGET_MAP.put(Skill.MAGIC, WIDGET_MAGIC);
        SKILL_WIDGET_MAP.put(Skill.DEFENCE, WIDGET_DEFENCE);
        SKILL_WIDGET_MAP.put(Skill.HITPOINTS, WIDGET_HITPOINTS);
        SKILL_WIDGET_MAP.put(Skill.PRAYER, WIDGET_PRAYER);
        SKILL_WIDGET_MAP.put(Skill.AGILITY, WIDGET_AGILITY);
        SKILL_WIDGET_MAP.put(Skill.HERBLORE, WIDGET_HERBLORE);
        SKILL_WIDGET_MAP.put(Skill.THIEVING, WIDGET_THIEVING);
        SKILL_WIDGET_MAP.put(Skill.CRAFTING, WIDGET_CRAFTING);
        SKILL_WIDGET_MAP.put(Skill.RUNECRAFT, WIDGET_RUNECRAFT);
        SKILL_WIDGET_MAP.put(Skill.SLAYER, WIDGET_SLAYER);
        SKILL_WIDGET_MAP.put(Skill.FARMING, WIDGET_FARMING);
        SKILL_WIDGET_MAP.put(Skill.MINING, WIDGET_MINING);
        SKILL_WIDGET_MAP.put(Skill.SMITHING, WIDGET_SMITHING);
        SKILL_WIDGET_MAP.put(Skill.FISHING, WIDGET_FISHING);
        SKILL_WIDGET_MAP.put(Skill.COOKING, WIDGET_COOKING);
        SKILL_WIDGET_MAP.put(Skill.FIREMAKING, WIDGET_FIREMAKING);
        SKILL_WIDGET_MAP.put(Skill.WOODCUTTING, WIDGET_WOODCUTTING);
        SKILL_WIDGET_MAP.put(Skill.FLETCHING, WIDGET_FLETCHING);
        SKILL_WIDGET_MAP.put(Skill.CONSTRUCTION, WIDGET_CONSTRUCTION);
        SKILL_WIDGET_MAP.put(Skill.HUNTER, WIDGET_HUNTER);
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