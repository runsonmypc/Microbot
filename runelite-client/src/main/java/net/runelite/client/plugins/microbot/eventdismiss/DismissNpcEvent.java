package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class DismissNpcEvent implements BlockingEvent {

    private final EventDismissConfig config;

    public DismissNpcEvent(EventDismissConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        return Rs2Npc.hasLineOfSight(randomEventNPC);
    }

    @Override
    public boolean execute() {
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        String npcName = randomEventNPC.getName();
        
        // Add human-like reaction delay
        Global.sleep(Rs2Random.between(1200, 3000));
        
        // Check if this is a lamp event and should be accepted
        if (isLampEvent(npcName) && shouldAcceptLamp(npcName)) {
            return handleLampEvent(randomEventNPC);
        }
        
        // Handle normal dismiss logic
        boolean shouldDismiss = shouldDismissNpc(randomEventNPC);
        if (shouldDismiss) {
            Rs2Npc.interact(randomEventNPC, "Dismiss");
            Global.sleepUntil(() -> Rs2Npc.getRandomEventNPC() == null);
            return true;
        } else if (!Rs2Inventory.isFull()) {
            Rs2Npc.interact(randomEventNPC, "Talk-to");
            Rs2Dialogue.sleepUntilHasContinue();
            Rs2Dialogue.clickContinue();
            return true;
        }
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
        
        // Handle dialogue
        while (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                Global.sleep(600, 1200);
            } else {
                Global.sleep(300, 600);
            }
        }
        
        // Wait for lamp to appear in inventory
        Global.sleepUntil(() -> Rs2Inventory.contains(ItemID.LAMP), 5000);
        
        // Use the lamp
        if (Rs2Inventory.contains(ItemID.LAMP)) {
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
        
        return true;
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
