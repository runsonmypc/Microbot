package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class HandleNpcEvent implements BlockingEvent {

    private final EventHandlerConfig config;
    private final AtomicBoolean hasLoggedInventoryFull = new AtomicBoolean(false);
    private final AtomicBoolean waitingForLamp = new AtomicBoolean(false);
    private final AtomicInteger lampWaitCounter = new AtomicInteger(0);
    private static final int MAX_LAMP_WAIT_TICKS = 50; // ~30 seconds
    
    // Using LampUtility for all lamp-related functionality

    public HandleNpcEvent(EventHandlerConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        // Keep validating if we're waiting for a lamp to appear
        if (waitingForLamp.get()) {
            return true;
        }
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        return Rs2Npc.hasLineOfSight(randomEventNPC);
    }

    @Override
    public boolean execute() {
        // If we're waiting for a lamp, try to use it
        if (waitingForLamp.get()) {
            int currentCount = lampWaitCounter.incrementAndGet();
            
            // Check for timeout
            if (currentCount > MAX_LAMP_WAIT_TICKS) {
                log.warn("Lamp wait timeout after {} ticks", MAX_LAMP_WAIT_TICKS);
                resetLampWaitState();
                return true; // Mark as handled to stop checking
            }
            
            // Check if lamp appeared
            if (Rs2Inventory.contains(ItemID.LAMP)) {
                log.info("Lamp found in inventory - using it");
                if (LampUtility.useLamp(config.lampSkill())) {
                    resetLampWaitState();
                    return true; // Successfully handled
                }
            }
            
            // Still waiting for lamp
            return false;
        }
        
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        if (randomEventNPC == null) {
            return false;
        }
        
        String npcName = randomEventNPC.getName();
        if (npcName == null) {
            return false;
        }
        
        // Add human-like reaction delay
        Global.sleep(Rs2Random.between(1200, 3000));
        
        // Check if this is a lamp event and should be accepted
        if (isLampEvent(npcName) && shouldAcceptLamp(npcName)) {
            // Check if inventory is full - if so, wait for space
            if (Rs2Inventory.isFull()) {
                if (hasLoggedInventoryFull.compareAndSet(false, true)) {
                    log.info("Inventory full - waiting for space to accept lamp from {}", npcName);
                }
                // Return false to keep checking - event will remain active
                return false;
            }
            // Reset the flag when we can proceed
            hasLoggedInventoryFull.set(false);
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
        
        // Check if lamp appeared immediately after dialogue
        if (Rs2Inventory.contains(ItemID.LAMP)) {
            log.info("Lamp received immediately - using it");
            Global.sleep(600, 1200); // Small delay before using
            if (LampUtility.useLamp(config.lampSkill())) {
                return true; // Mark as complete
            }
        }
        
        // Set flag that we're waiting for lamp BEFORE sleeping
        log.info("Dialogue complete - waiting for lamp to appear");
        waitingForLamp.set(true);
        lampWaitCounter.set(0);
        
        // Add small delay after dialogue closes
        Global.sleep(600, 1200);
        
        // Check again after the delay
        if (Rs2Inventory.contains(ItemID.LAMP)) {
            log.info("Lamp appeared during delay - using it");
            if (LampUtility.useLamp(config.lampSkill())) {
                resetLampWaitState();
                return true; // Mark as complete
            }
        }
        
        // Don't mark as complete yet - we still need to wait for the lamp
        return false;
    }
    
    // Lamp usage functionality moved to LampUtility class
    
    private void resetLampWaitState() {
        waitingForLamp.set(false);
        lampWaitCounter.set(0);
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
    
    // Skill widget ID mapping moved to LampUtility class

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
