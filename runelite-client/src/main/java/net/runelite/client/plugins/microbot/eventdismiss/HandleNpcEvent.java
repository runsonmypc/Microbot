package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class HandleNpcEvent implements BlockingEvent {

    private final EventHandlerConfig config;
    private boolean hasLoggedInventoryFull = false;
    private boolean waitingForLamp = false;
    private int lampWaitCounter = 0;
    private static final int MAX_LAMP_WAIT_TICKS = 50; // ~30 seconds

    public HandleNpcEvent(EventHandlerConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        // Keep validating if we're waiting for a lamp to appear
        if (waitingForLamp) {
            return true;
        }
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        return Rs2Npc.hasLineOfSight(randomEventNPC);
    }

    @Override
    public boolean execute() {
        // If we're waiting for a lamp, try to use it
        if (waitingForLamp) {
            lampWaitCounter++;
            
            // Check for timeout
            if (lampWaitCounter > MAX_LAMP_WAIT_TICKS) {
                Microbot.log("Lamp wait timeout - giving up");
                waitingForLamp = false;
                lampWaitCounter = 0;
                return true; // Mark as handled to stop checking
            }
            
            // Check if lamp appeared
            if (Rs2Inventory.contains(ItemID.LAMP)) {
                Microbot.log("Lamp found in inventory - using it");
                useLamp();
                waitingForLamp = false;
                lampWaitCounter = 0;
                return true; // Successfully handled
            }
            
            // Still waiting for lamp
            return false;
        }
        
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        String npcName = randomEventNPC.getName();
        
        // Add human-like reaction delay
        Global.sleep(Rs2Random.between(1200, 3000));
        
        // Check if this is a lamp event and should be accepted
        if (isLampEvent(npcName) && shouldAcceptLamp(npcName)) {
            // Check if inventory is full - if so, wait for space
            if (Rs2Inventory.isFull()) {
                if (!hasLoggedInventoryFull) {
                    Microbot.log("Inventory full - waiting for space to accept lamp from " + npcName);
                    hasLoggedInventoryFull = true;
                }
                // Return false to keep checking - event will remain active
                return false;
            }
            // Reset the flag when we can proceed
            hasLoggedInventoryFull = false;
            return handleLampEvent(randomEventNPC);
        }
        
        // Handle normal dismiss logic
        boolean shouldDismiss = shouldDismissNpc(randomEventNPC);
        if (shouldDismiss) {
            Rs2Npc.interact(randomEventNPC, "Dismiss");
            Global.sleepUntil(() -> Rs2Npc.getRandomEventNPC() == null);
            return true;
        }
        
        // If we shouldn't dismiss and it's not a lamp event, ignore it
        return false;
    }
    
    private boolean isLampEvent(String npcName) {
        return "Genie".equals(npcName) || "Count Check".equals(npcName);
    }
    
    private boolean shouldAcceptLamp(String npcName) {
        if ("Genie".equals(npcName)) {
            return config.genieAction() == EventAction.ACCEPT;
        } else if ("Count Check".equals(npcName)) {
            return config.countCheckAction() == EventAction.ACCEPT;
        }
        return false;
    }
    
    private boolean handleLampEvent(Rs2NpcModel npc) {
        // Talk to the NPC
        Rs2Npc.interact(npc, "Talk-to");
        
        // Continue all dialogue until it fully closes
        continueDialogueUntilClosed();
        
        // Add small delay after dialogue closes
        Global.sleep(600, 1200);
        
        // Set flag that we're waiting for lamp
        Microbot.log("Dialogue complete - waiting for lamp to appear");
        waitingForLamp = true;
        lampWaitCounter = 0;
        
        // Don't mark as complete yet - we still need to use the lamp
        return false;
    }
    
    private void useLamp() {
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
        }
    }
    
    private void continueDialogueUntilClosed() {
        // Wait for dialogue to start
        Rs2Dialogue.sleepUntilInDialogue();
        
        // Continue all dialogue until fully closed
        while (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                Global.sleep(600, 1200);
            } else if (Rs2Dialogue.hasSelectAnOption()) {
                // Handle any dialogue options if they appear
                Global.sleep(300, 600);
            } else {
                // Small sleep to prevent tight loop
                Global.sleep(100, 200);
            }
        }
        
        // Final wait to ensure dialogue is completely closed
        Rs2Dialogue.sleepUntilNotInDialogue();
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

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.LOWEST;
    }

    private boolean shouldDismissNpc(Rs2NpcModel npc) {
        String npcName = npc.getName();
        if (npcName == null) return false;
        switch (npcName) {
            case "Bee keeper":
                return config.dismissBeekeeper();
            case "Capt' Arnav":
                return config.dismissArnav();
            case "Niles":
            case "Miles":
            case "Giles":
                return config.dismissCerters();
            case "Count Check":
                return config.countCheckAction() == EventAction.DISMISS;
            case "Sergeant Damien":
                return config.dismissDrillDemon();
            case "Drunken Dwarf":
                return config.dismissDrunkenDwarf();
            case "Evil Bob":
                return config.dismissEvilBob();
            case "Postie Pete":
                return config.dismissEvilTwin();
            case "Freaky Forester":
                return config.dismissFreakyForester();
            case "Genie":
                return config.genieAction() == EventAction.DISMISS;
            case "Leo":
                return config.dismissGravedigger();
            case "Dr Jekyll":
                return config.dismissJekyllAndHyde();
            case "Frog":
                return config.dismissKissTheFrog();
            case "Mysterious Old Man":
                return config.dismissMysteriousOldMan();
            case "Pillory Guard":
                return config.dismissPillory();
            case "Flippa":
            case "Tilt":
                return config.dismissPinball();
            case "Quiz Master":
                return config.dismissQuizMaster();
            case "Rick Turpentine":
                return config.dismissRickTurpentine();
            case "Sandwich lady":
                return config.dismissSandwichLady();
            case "Strange plant":
                return config.dismissStrangePlant();
            case "Dunce":
                return config.dismissSurpriseExam();
            default:
                return false;
        }
    }
}
