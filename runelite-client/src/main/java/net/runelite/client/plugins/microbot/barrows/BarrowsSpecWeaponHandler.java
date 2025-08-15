package net.runelite.client.plugins.microbot.barrows;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.HashMap;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/**
 * Handles special attack weapon switching for the Barrows plugin.
 * This includes equipping spec weapons, defenders for one-handed weapons,
 * and restoring original equipment after special attacks.
 */
@Slf4j
public class BarrowsSpecWeaponHandler {
    
    private final Map<EquipmentInventorySlot, String> preSpecGear = new HashMap<>();
    private boolean specWeaponEquipped = false;
    private long lastSpecTime = 0;
    
    /**
     * Check if we should use special attack on the current brother
     */
    public boolean shouldUseSpecOnBrother(String brotherName, BarrowsConfig config) {
        if (!config.useSpecWeapon()) {
            return false;
        }
        
        if (brotherName == null) {
            return false;
        }
        
        String targetBrother = config.specWeaponTargetBrother().getFullName();
        // Extract just the brother's first name for comparison
        String targetName = targetBrother.split(" ")[0];
        return brotherName.contains(targetName);
    }
    
    /**
     * Main method to handle spec weapon usage during combat
     */
    public void handleSpecWeaponUsage(String brotherName, BarrowsConfig config) {
        // Check if we should use spec on this brother
        if (!shouldUseSpecOnBrother(brotherName, config)) {
            return;
        }
        
        // Only proceed if we're in combat and interacting
        if (!Rs2Player.isInteracting() || !Rs2Combat.inCombat()) {
            return;
        }
        
        // Check if SpecialAttackConfigs is enabled and ready
        if (!Microbot.getSpecialAttackConfigs().isUseSpecialAttack()) {
            return;
        }
        
        SpecialAttackWeaponEnum specWeapon = config.specWeapon();
        
        // Check if we have enough spec energy to use the spec weapon
        int specEnergyRequired = specWeapon.getEnergyRequired() / 10; // Convert to percentage
        boolean hasEnoughSpec = Rs2Combat.getSpecEnergy() >= specEnergyRequired;
        
        // Only equip defender when we actually have spec energy to use
        if (!specWeapon.is2H() && config.equipDefender() && hasEnoughSpec && !specWeaponEquipped) {
            equipDefenderIfAvailable();
            specWeaponEquipped = true;
        }
        
        // Use spec weapon through the existing SpecialAttackConfigs system
        boolean specUsed = Microbot.getSpecialAttackConfigs().useSpecWeapon();
        
        if (specUsed) {
            lastSpecTime = System.currentTimeMillis();
            log.info("Used special attack on {}", brotherName);
        }
        
        // Only restore offhand if we no longer have spec energy and are still equipped with defender
        // This prevents the constant swapping back and forth
        if (specWeaponEquipped && !hasEnoughSpec) {
            Rs2ItemModel currentOffhand = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
            if (currentOffhand != null && currentOffhand.getName().toLowerCase().contains("defender")) {
                // We're out of spec energy and still have defender equipped, restore original offhand
                restoreOffhandGear();
            }
        }
    }
    
    /**
     * Equips a defender if available for one-handed spec weapons
     */
    private void equipDefenderIfAvailable() {
        // Check if we already have a defender equipped
        Rs2ItemModel currentOffhand = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (currentOffhand != null && currentOffhand.getName().toLowerCase().contains("defender")) {
            return; // Already have a defender equipped
        }
        
        // Search inventory for any defender (bronze through dragon)
        Rs2ItemModel defender = Rs2Inventory.get(item -> 
            item != null && 
            item.getName() != null && 
            item.getName().toLowerCase().contains("defender"));
        
        if (defender != null) {
            // Store current offhand before equipping defender
            if (currentOffhand != null && !preSpecGear.containsKey(EquipmentInventorySlot.SHIELD)) {
                preSpecGear.put(EquipmentInventorySlot.SHIELD, currentOffhand.getName());
                log.info("Stored offhand for restoration: {}", currentOffhand.getName());
            }
            
            // Equip the defender
            if (Rs2Inventory.wield(defender.getName())) {
                log.info("Equipped defender: {}", defender.getName());
                sleep(300, 600);
            }
        } else {
            log.debug("No defender found in inventory for one-handed spec weapon");
        }
    }
    
    /**
     * Restores only the offhand gear after spec weapon is switched back
     */
    private void restoreOffhandGear() {
        if (!preSpecGear.containsKey(EquipmentInventorySlot.SHIELD)) {
            return;
        }
        
        String offhandItem = preSpecGear.get(EquipmentInventorySlot.SHIELD);
        
        // Check if we need to restore this item
        Rs2ItemModel currentlyEquipped = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (currentlyEquipped != null && currentlyEquipped.getName().equals(offhandItem)) {
            return; // Already wearing the original item
        }
        
        // Re-equip the original offhand if in inventory
        if (Rs2Inventory.hasItem(offhandItem)) {
            if (Rs2Inventory.wield(offhandItem)) {
                log.info("Restored offhand: {}", offhandItem);
                sleep(300, 600);
            }
        } else {
            log.warn("Cannot restore offhand {} - not in inventory", offhandItem);
        }
        
        preSpecGear.clear();
        specWeaponEquipped = false;
    }
    
    /**
     * Restores original equipment after special attack usage
     */
    public void restorePreSpecGear() {
        if (preSpecGear.isEmpty()) {
            return;
        }
        
        log.info("Restoring pre-spec gear");
        
        // Restore any gear we stored before spec weapon switching
        for (Map.Entry<EquipmentInventorySlot, String> entry : preSpecGear.entrySet()) {
            String itemName = entry.getValue();
            
            // Check if we need to restore this item
            Rs2ItemModel currentlyEquipped = Rs2Equipment.get(entry.getKey());
            if (currentlyEquipped != null && currentlyEquipped.getName().equals(itemName)) {
                continue; // Already wearing the original item
            }
            
            // Re-equip the original item if in inventory
            if (Rs2Inventory.hasItem(itemName)) {
                if (Rs2Inventory.wield(itemName)) {
                    log.info("Restored: {}", itemName);
                    sleep(300, 600);
                }
            } else {
                log.warn("Cannot restore {} - not in inventory", itemName);
            }
        }
        
        preSpecGear.clear();
        specWeaponEquipped = false;
    }
    
    /**
     * Reset the handler state
     */
    public void reset() {
        preSpecGear.clear();
        specWeaponEquipped = false;
        lastSpecTime = 0;
    }
    
    /**
     * Check if spec was recently used (within last 5 seconds)
     */
    public boolean wasSpecRecentlyUsed() {
        return System.currentTimeMillis() - lastSpecTime < 5000;
    }
    
    /**
     * Check if spec weapon is currently equipped
     */
    public boolean isSpecWeaponEquipped() {
        return specWeaponEquipped;
    }
}