package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class BarrowsScript extends Script {

    public static boolean test = false;
    public static boolean inTunnels = false;
    public static String WhoisTun = "Unknown";
    public String neededRune = "unknown";
    private boolean shouldBank = false;
    private boolean shouldAttackSkeleton = false;
    private boolean varbitCheckEnabled = true;
    private int tunnelLoopCount = 0;
    private boolean walkerNeedsBankingAfterChest = false; // Force Walker method to bank after chest
    private WorldPoint FirstLoopTile;
    private Rs2PrayerEnum NeededPrayer;
    int scriptDelay = Rs2Random.between(300,600);
    public static int ChestsOpened = 0;
    private int minRuneAmt;
    public static List<String> barrowsPieces = new ArrayList<>();
    private ScheduledFuture<?> WalkToTheChestFuture;
    private ScheduledFuture<?> puzzleMonitorFuture;
    private WorldPoint Chest = new WorldPoint(3552,9694,0);
    private int minForgottenBrews = 0;
    public static boolean outOfPoweredStaffCharges = false;
    public static boolean usingPoweredStaffs = false;
    public static boolean chestLooted = false;
    
    // Gear swap management for Ahrim
    private Map<EquipmentInventorySlot, String> originalGear = new HashMap<>();
    private boolean gearSwapped = false;
    
    // Spec weapon handler
    private final BarrowsSpecWeaponHandler specWeaponHandler = new BarrowsSpecWeaponHandler();
    
    public static boolean firstRun = false;
    private BarrowsConfig config;

    public boolean run(BarrowsConfig config, BarrowsPlugin plugin) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (Rs2Player.getQuestState(Quest.HIS_FAITHFUL_SERVANTS) != QuestState.FINISHED) {
                    Microbot.showMessage("Complete the 'His Faithful Servants' quest for the webwalker to function correctly");
                    shutdown();
                    return;
                }

                var inventorySetup = new Rs2InventorySetup(config.inventorySetup().getName(), mainScheduledFuture);

                if(firstRun) {
                    if (!inventorySetup.doesEquipmentMatch()) {
                        long timeout = System.currentTimeMillis() + 30000; // 30 second timeout
                        while(!inventorySetup.doesEquipmentMatch() && System.currentTimeMillis() < timeout) {
                            if(!super.isRunning()){ break; }
                            if (Rs2Bank.getNearestBank().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) > 6) {
                                Rs2Bank.walkToBank();
                            }
                            if (Rs2Bank.getNearestBank().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) <= 6) {
                                inventorySetup.loadEquipment();
                            }
                        }
                    }
                    firstRun = false;
                    
                    // Start high-priority puzzle monitoring thread
                    if (puzzleMonitorFuture == null || puzzleMonitorFuture.isCancelled() || puzzleMonitorFuture.isDone()) {
                        puzzleMonitorFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                            if (!super.isRunning()) return;
                            
                            // Check if puzzle widgets are open
                            int[] puzzleWidgets = {1638413, 1638415, 1638417};
                            for (int widget : puzzleWidgets) {
                                if (Rs2Widget.getWidget(widget) != null) {
                                    Microbot.log("URGENT: Puzzle detected! Solving immediately!");
                                    solvePuzzle();
                                    return;
                                }
                            }
                        }, 0, 100, TimeUnit.MILLISECONDS);
                    }
                    
                    // Check supplies immediately after first run setup
                    suppliesCheck(config);
                    if(shouldBank) {
                        Microbot.log("Initial supplies check: Need to bank before starting");
                        // For Walker method, set the flag to ensure proper banking
                        if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                            walkerNeedsBankingAfterChest = true; // Use same flag to force banking
                            Microbot.log("Walker method: Forcing initial bank run");
                        }
                    }
                }

                if(barrowsPieces.isEmpty()){
                    barrowsPieces.add("Nothing yet.");
                }

                if(Rs2Player.getWorldLocation().getY() > 9600 && Rs2Player.getWorldLocation().getY() < 9730) {
                    inTunnels = true;
                } else {

                    if(tunnelLoopCount != 0){
                        //reset the tunnels loop counter
                        tunnelLoopCount = 0;
                    }

                    inTunnels = false;
                }

                //powered staffs
                if(Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Trident of the") ||
                        Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Tumeken's") ||
                            Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("sceptre") ||
                                Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Sanguinesti") ||
                                    Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Crystal staff")) {
                    usingPoweredStaffs = true;
                } else {
                    usingPoweredStaffs = false;
                    gettheRune(config);
                    minRuneAmt = config.minRuneAmount();
                }

                minForgottenBrews = config.minForgottenBrew();
                shouldAttackSkeleton = config.shouldGainRP();

                if(usingPoweredStaffs) {
                    if (outOfPoweredStaffCharges) {
                        Microbot.log("No charges left on our staff. Stopping...");
                        super.shutdown();
                    }
                }

                // Only check supplies if not in Walker forced banking sequence
                if(!walkerNeedsBankingAfterChest) {
                    outOfSupplies(config);
                } else {
                    // Keep shouldBank true during Walker banking sequence
                    shouldBank = true;
                }

                if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE) {
                    if (!inTunnels && !shouldBank && Rs2Player.getWorldLocation().distanceTo(new WorldPoint(3573, 3296, 0)) > 60) {
                        //needed to intercept the walker
                        if(Rs2GameObject.getGameObject(4525) == null){
                            Rs2Inventory.interact("Teleport to house", "Inside");
                            sleepUntil(() -> Rs2Player.getAnimation() == 4069, Rs2Random.between(2000, 4000));
                            sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                            sleepUntil(() -> Rs2GameObject.getGameObject(4525) != null, Rs2Random.between(6000, 10000));
                        }
                        handlePOH(config);
                        return;
                    }
                }
                
                // Handle Walker method - walk from bank to Barrows
                if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                    // ABSOLUTE BLOCK: If we need to bank after chest, DO NOT go to Barrows
                    if(walkerNeedsBankingAfterChest) {
                        Microbot.log("Walker: Must complete banking first - skipping Barrows walk");
                        // Don't return - let the code continue to the banking section
                    } else if (!inTunnels && !shouldBank && Rs2Player.getWorldLocation().distanceTo(new WorldPoint(3573, 3296, 0)) > 60) {
                        // Only walk to Barrows if NOT in forced banking sequence
                        // Use Rs2Walker to path to Dharok's mound
                        WorldPoint dharokMound = new WorldPoint(3573, 3296, 0);
                        Rs2Walker.walkTo(dharokMound);
                        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(dharokMound) < 30, Rs2Random.between(30000, 60000));
                    }
                }

                if(!inTunnels && shouldBank == false && !walkerNeedsBankingAfterChest) {
                    // Check if 5 brothers are killed - if so, identify the tunnel brother
                    Microbot.log("WhoisTun: " + WhoisTun + ", varbitCheckEnabled: " + varbitCheckEnabled);
                    if(WhoisTun.equals("Unknown") && varbitCheckEnabled) {
                        int killCount = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) + 
                                       Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) + 
                                       Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) + 
                                       Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) + 
                                       Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) + 
                                       Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
                        
                        Microbot.log("Kill count: " + killCount);
                        if(killCount == 5) {
                            // Find which brother isn't killed - that's the tunnel brother
                            if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 0) {
                                WhoisTun = "Dharok the Wretched";
                                Microbot.log("Detected Dharok is the tunnel brother (5 others killed)");
                            } else if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 0) {
                                WhoisTun = "Guthan the Infested";
                                Microbot.log("Detected Guthan is the tunnel brother (5 others killed)");
                            } else if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 0) {
                                WhoisTun = "Karil the Tainted";
                                Microbot.log("Detected Karil is the tunnel brother (5 others killed)");
                            } else if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 0) {
                                WhoisTun = "Torag the Corrupted";
                                Microbot.log("Detected Torag is the tunnel brother (5 others killed)");
                            } else if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 0) {
                                WhoisTun = "Verac the Defiled";
                                Microbot.log("Detected Verac is the tunnel brother (5 others killed)");
                            } else if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 0) {
                                WhoisTun = "Ahrim the Blighted";
                                Microbot.log("Detected Ahrim is the tunnel brother (5 others killed)");
                            }
                        }
                    }
                    
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        Rs2WorldArea mound = brother.getHumpWP();
                        // Don't set NeededPrayer here - wait until we're in the mound
                        outOfSupplies(config);
                        if(shouldBank){
                            return;
                        }

                        stopFutureWalker();
                        closeBank();

                        if(!usingPoweredStaffs){
                            setAutoCast(config);
                        }

                        Microbot.log("Checking mound for: " + brother.getName());

                        if(everyBrotherWasKilled()){
                            if(WhoisTun.equals("Unknown")){
                                Microbot.log("We're not sure who tunnel is, and every brother is dead. Checking all mounds manually");
                                varbitCheckEnabled = false;
                            }
                        } else {
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        if(!WhoisTun.equals("Unknown")){
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        //resume progress from varbits
                        if(varbitCheckEnabled) {
                            if (brother.name.contains("Dharok")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1) {
                                    Microbot.log("We all ready killed Dharok.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Guthan")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1) {
                                    Microbot.log("We all ready killed Guthan.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Karil")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1) {
                                    Microbot.log("We all ready killed Karil.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Torag")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1) {
                                    Microbot.log("We all ready killed Torag.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Verac")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1) {
                                    Microbot.log("We all ready killed Verac.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Ahrim")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1) {
                                    Microbot.log("We all ready killed Ahrim.");
                                    continue;
                                }
                            }
                        }

                        plugin.getLockCondition().lock();

                        //Enter mound
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            Microbot.log("Entering the mound");

                            if (!config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                handlePOH(config);
                            }

                            if (mound == null) {
                                Microbot.log("Error: mound is null for brother " + brother.getName());
                                continue;
                            }
                            goToTheMound(mound);

                            digIntoTheMound(mound);

                        }
                        if (Rs2Player.getWorldLocation().getPlane() == 3) {
                            Microbot.log("We're in the mound");
                            
                            // Disable all prayers first to ensure clean state
                            Rs2Prayer.disableAllPrayers();
                            
                            // Set the prayer for this brother now that we're inside
                            NeededPrayer = brother.whatToPray;

                            // Check if we should pray against this specific brother
                            boolean shouldPray = false;
                            if (brother.getName().contains("Dharok") && config.prayAgainstDharok()) {
                                shouldPray = true;
                            } else if (brother.getName().contains("Torag") && config.prayAgainstTorag()) {
                                shouldPray = true;
                            } else if (brother.getName().contains("Guthan") && config.prayAgainstGuthan()) {
                                shouldPray = true;
                            } else if (brother.getName().contains("Verac") && config.prayAgainstVerac()) {
                                shouldPray = true;
                            } else if (brother.getName().contains("Ahrim") && config.prayAgainstAhrim()) {
                                shouldPray = true;
                            } else if (brother.getName().contains("Karil") && config.prayAgainstKaril()) {
                                shouldPray = true;
                            }

                            if (shouldPray) {
                                // Swap gear when praying against any brother
                                swapGearForPrayer(config);
                                activatePrayer();
                            }

                            // we're in the mound, prayer is active
                            GameObject sarc = Rs2GameObject.get("Sarcophagus");
                            Rs2NpcModel currentBrother = null;
                            Microbot.log("Found the Sarcophagus");
                            long sarcTimeout = System.currentTimeMillis() + 30000; // 30 second timeout
                            while(currentBrother == null && System.currentTimeMillis() < sarcTimeout) {
                                Microbot.log("Searching the Sarcophagus");
                                if (!super.isRunning()) {
                                    break;
                                }

                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                    // the brother could take a second to spawn in.
                                    sleepUntil(() -> Microbot.getClient().getHintArrowNpc()!=null || Rs2Dialogue.isInDialogue(), Rs2Random.between(750, 1500));
                                }
                                if(Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasDialogueText("You've found a hidden")){
                                    WhoisTun = brother.name;
                                    Microbot.log(brother.name+" is our tunnel");
                                    
                                    // Check if we should enter tunnel immediately (killed 5 brothers)
                                    int killCount = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) + 
                                                   Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) + 
                                                   Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) + 
                                                   Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) + 
                                                   Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) + 
                                                   Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
                                    
                                    if (killCount >= 5) {
                                        Microbot.log("Killed 5 brothers, entering tunnel immediately");
                                        dialogueEnterTunnels();
                                        return;
                                    }
                                    
                                    // Restore gear if we swapped for prayer but won't fight here
                                    if(shouldPray) {
                                        disablePrayer();
                                        restoreOriginalGear();
                                    }
                                    // Reset NeededPrayer since we're not fighting this brother here
                                    NeededPrayer = null;
                                    break;
                                }

                                if(Microbot.getClient().getHintArrowNpc() != null) {
                                    NPC hintArrow = Microbot.getClient().getHintArrowNpc();
                                    currentBrother = new Rs2NpcModel(hintArrow);
                                } else {
                                    break;
                                }

                                if (currentBrother != null) {
                                    break;
                                }
                            }
                            //The ghost should be here assuming its not the tunnel.
                            if(currentBrother != null && !Rs2Player.isInCombat()){
                                long attackTimeout = System.currentTimeMillis() + 15000; // 15 second timeout
                                while(!Rs2Player.isInCombat() && System.currentTimeMillis() < attackTimeout){
                                    if (!super.isRunning()) {
                                        break;
                                    }
                                    Microbot.log("Attacking the brother");
                                    Rs2Npc.interact(currentBrother, "Attack");
                                    sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                                }
                            }
                            //fighting
                            if(Rs2Player.isInCombat()){
                                Microbot.log("Fighting the brother.");
                                long combatTimeout = System.currentTimeMillis() + 120000; // 2 minute timeout
                                while(currentBrother != null && !currentBrother.isDead() && Rs2Player.isInCombat() && System.currentTimeMillis() < combatTimeout){
                                    if (!super.isRunning()) {
                                        break;
                                    }

                                    // Re-check if we should pray (using existing shouldPray variable)
                                    if (shouldPray) {
                                        // Ensure we're using the correct prayer for THIS brother
                                        NeededPrayer = brother.whatToPray;
                                        activatePrayer();
                                    }

                                    // Antipattern behavior - only inside crypts during combat
                                    antiPatternEnableWrongPrayer();
                                    antiPatternActivatePrayer();

                                    sleep(500,1500);
                                    eatFood();
                                    outOfSupplies(config);
                                    antiPatternDropVials();
                                    drinkforgottonbrew();
                                    
                                    // Handle special attack weapon usage for crypt brothers
                                    if (currentBrother != null) {
                                        specWeaponHandler.handleSpecWeaponUsage(currentBrother.getName(), config);
                                    }

                                    // Only drink prayer potions if we're using prayer and in combat
                                    if (shouldPray && Rs2Player.isInCombat()) {
                                        drinkPrayerPot();
                                    }

                                    if(Microbot.getClient().getHintArrowNpc() == null && !Rs2Player.isInCombat()){
                                        // Only exit if BOTH hint arrow is gone AND not in combat
                                        break;
                                    }

                                    if(currentBrother.isDead()){
                                        //anti pattern
                                        disablePrayer();
                                        //anti pattern
                                        // Restore gear if we were praying
                                        if(shouldPray) {
                                            restoreOriginalGear();
                                        }
                                        
                                        // Restore spec weapon gear if needed
                                        specWeaponHandler.restorePreSpecGear();
                                        break;
                                    }
                                }
                            }
                            
                            // Simple check: is the brother actually dead before leaving?
                            if (currentBrother != null && !currentBrother.isDead()) {
                                Microbot.log("Brother still alive after combat ended, retrying!");
                                continue; // Restart the loop for this brother
                            }
                            
                            // at this point the brother should be dead and we should be free to leave.
                            // Tunnel entry is now handled immediately when we find the tunnel brother

                            leaveTheMound();
                        }
                    }
                }

                if(!WhoisTun.equals("Unknown") && shouldBank == false && !inTunnels){
                    int howManyBrothersWereKilled = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
                    if(howManyBrothersWereKilled <= 4){
                        Microbot.log("We seem to have missed someone, checking all mounds again.");
                        return;
                    } else {
                        Microbot.log("Going to the tunnels.");
                    }

                    stopFutureWalker();
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        if (brother.name.equals(WhoisTun)) {
                            // Found the tunnel brother's mound
                            Rs2WorldArea tunnelMound = brother.getHumpWP();

                            if (!config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                handlePOH(config);
                            }

                            if (tunnelMound == null) {
                                Microbot.log("Error: tunnelMound is null for brother " + brother.getName());
                                break;
                            }
                            
                            // Walk to the mound
                            goToTheMound(tunnelMound);

                            digIntoTheMound(tunnelMound);

                            long dialogueTimeout = System.currentTimeMillis() + 30000; // 30 second timeout
                            while(!Rs2Dialogue.isInDialogue() && System.currentTimeMillis() < dialogueTimeout) {
                                GameObject sarc = Rs2GameObject.get("Sarcophagus");

                                if (!super.isRunning()) {
                                    break;
                                }

                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                    sleepUntil(() -> Rs2Dialogue.isInDialogue(), Rs2Random.between(3000, 6000));
                                }

                                if(Rs2Dialogue.isInDialogue()){
                                    break;
                                }

                                if (inTunnels) {
                                    break;
                                }

                                if (Rs2Player.getWorldLocation().getPlane() != 3) {
                                    //we're not in the mound
                                    break;
                                }

                                if(!Rs2Dialogue.isInDialogue()){
                                    //Somehow we got tun wrong.
                                    Microbot.log("We're in the wrong tunnel mound. Leaving...");
                                    this.leaveTheMound();
                                    WhoisTun = "Unknown";
                                    return;
                                }

                            }

                            dialogueEnterTunnels();

                            break;
                        }
                    }
                }


                if(inTunnels && !shouldBank) {
                    Microbot.log("In the tunnels");
                    if(!varbitCheckEnabled){
                        varbitCheckEnabled=true;
                    }
                    leaveTheMound();
                    stuckInTunsCheck();
                    
                    // Check if we're in combat with a non-brother and have enough RP
                    if(Rs2Player.isInCombat()) {
                        boolean fightingBrother = false;
                        try {
                            NPC hintArrow = Microbot.getClient().getHintArrowNpc();
                            if(hintArrow != null) {
                                // There's a brother, check if we're fighting them
                                fightingBrother = true;
                            }
                        } catch (Exception e) {
                            // No hint arrow
                        }
                        
                        if(!fightingBrother) {
                            // We're fighting a non-brother (skeleton, etc)
                            int currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                            
                            // Check if brother is still alive
                            boolean brotherAlive = false;
                            try {
                                brotherAlive = (Microbot.getClient().getHintArrowNpc() != null);
                            } catch (Exception e) {
                                // No brother
                            }
                            
                            int targetRP = brotherAlive ? 770 : 870;
                            
                            if(currentRP >= targetRP) {
                                Microbot.log("In combat with non-brother but have enough RP (" + currentRP + "), ignoring and continuing to chest");
                                // Don't engage further, just continue to chest
                                if (!Rs2Player.isMoving()) {
                                    startWalkingToTheChest();
                                }
                                // Skip gainRP since we have enough
                            } else {
                                // We need more RP, let gainRP handle it
                                gainRP(config);
                            }
                        } else {
                            // Fighting a brother, this is important
                            checkForBrother(config);
                        }
                    } else {
                        // Not in combat, do normal checks
                        // solvePuzzle(); // Removed - now handled by dedicated high-priority monitor
                        checkForBrother(config);
                        eatFood();
                        outOfSupplies(config);
                        
                        // Only gain RP if we need it
                        int currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                        boolean brotherAlive = false;
                        try {
                            brotherAlive = (Microbot.getClient().getHintArrowNpc() != null);
                        } catch (Exception e) {
                            // No brother
                        }
                        int targetRP = brotherAlive ? 770 : 870;
                        
                        if(currentRP < targetRP) {
                            gainRP(config);
                        } else {
                            Microbot.log("Already have enough RP (" + currentRP + "/" + targetRP + "), skipping skeleton fights");
                        }
                    }

                    try {
                        if(!Rs2Player.isMoving()) {
                            startWalkingToTheChest();
                        }
                    } catch (Exception e) {
                        Microbot.log("Error checking movement status: " + e.getMessage());
                        // Try to start walking anyway if we can't check movement
                        startWalkingToTheChest();
                    }

                    // solvePuzzle(); // Removed - now handled by dedicated high-priority monitor
                    checkForBrother(config);
                    
                    // If chest was already looted (via game message), handle teleporting out
                    if(chestLooted) {
                        // Check if there's still a brother to kill
                        boolean hasBrother = false;
                        try {
                            hasBrother = (Microbot.getClient().getHintArrowNpc() != null);
                        } catch (Exception e) {
                            // No hint arrow
                        }
                        
                        if(hasBrother) {
                            Microbot.log("Chest looted but brother still alive - need to kill brother first");
                            // Don't proceed with teleporting - let the normal flow handle the brother
                        } else {
                            Microbot.log("Chest already looted, proceeding to leave tunnels");
                            
                            // For Walker method, ALWAYS bank after chest looting
                            if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                shouldBank = true;
                                walkerNeedsBankingAfterChest = true; // FORCE banking sequence
                                Microbot.log("Walker method - forcing bank after chest loot");
                            } else {
                                // For other methods, check if we need to bank
                                suppliesCheck(config);
                            }
                            
                            // Reset for next run
                            ChestsOpened++;
                            WhoisTun = "Unknown";
                            inTunnels = false;
                            chestLooted = false; // Reset for next run
                            
                            // Disable all prayers before leaving tunnels
                            Rs2Prayer.disableAllPrayers();
                            
                            Microbot.log("Leaving tunnels after chest loot");
                            
                            // Now leave - always teleport to Ferox if we need to bank
                            if(shouldBank) {
                            // Need to bank - ALWAYS teleport to Ferox using Ring of Dueling
                            if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
                                Microbot.log("Teleporting to bank.");
                                sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                // Wait to ensure we're out of tunnels
                                sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(3000, 5000));
                            }
                        } else {
                            // Don't need to bank - use configured teleport method
                            if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.BARROWS_TELEPORT){
                                if(Rs2Inventory.interact("Barrows teleport", "Break")) {
                                    Microbot.log("Using Barrows teleport");
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                }
                            } else if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE) {
                                if(Rs2Inventory.interact("Teleport to house", "Inside")) {
                                    Microbot.log("Using house teleport");
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                    handlePOH(config);
                                }
                            } else if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                // This should never execute because Walker always banks after chest
                                // But keep it as a safety fallback
                                Microbot.log("WARNING: Walker method without banking - this shouldn't happen");
                                if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
                                    Microbot.log("Teleporting to Ferox anyway");
                                    sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                    sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(3000, 5000));
                                }
                                shouldBank = true; // Force banking just in case
                            }
                        }
                            
                            // Important: return here to exit the current loop iteration and restart fresh
                            return;
                        }
                    }

                    if(Rs2GameObject.findObjectById(20973) != null && Rs2GameObject.hasLineOfSight(Rs2GameObject.findObjectById(20973)) && !chestLooted){
                        //chest ID: 20973
                        stopFutureWalker();

                        TileObject chest = Rs2GameObject.findObjectById(20973);

                        if(Rs2GameObject.interact(chest, "Open")){
                            // Only wait for hint arrow if we haven't killed all 6 brothers
                            int totalKilled = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) + 
                                            Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) + 
                                            Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) + 
                                            Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) + 
                                            Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) + 
                                            Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
                            
                            if (totalKilled < 6) {
                                // Wait for final brother to spawn - shorter wait
                                sleep(400, 600);
                                
                                // IMMEDIATELY check and attack the brother
                                NPC brotherNpc = null;
                                try {
                                    brotherNpc = Microbot.getClient().getHintArrowNpc();
                                } catch (Exception e) {
                                    // No hint arrow yet
                                }
                                
                                if(brotherNpc == null) {
                                    // Wait a bit more for spawn
                                    sleepUntil(()-> {
                                        try {
                                            NPC arrow = Microbot.getClient().getHintArrowNpc();
                                            return arrow != null && arrow.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 10;
                                        } catch (Exception e) {
                                            return false;
                                        }
                                    }, Rs2Random.between(2000, 3000));
                                    
                                    // Try again
                                    try {
                                        brotherNpc = Microbot.getClient().getHintArrowNpc();
                                    } catch (Exception e) {
                                        // No hint arrow
                                    }
                                }
                                
                                if(brotherNpc != null) {
                                    Microbot.log("Final brother spawned! Attacking IMMEDIATELY!");
                                    
                                    // Force attack the brother right away, ignore skeletons
                                    Rs2NpcModel brother = new Rs2NpcModel(brotherNpc);
                                    
                                    // Attack the brother multiple times to ensure we target them
                                    for(int i = 0; i < 3; i++) {
                                        if (!super.isRunning()) break;
                                        
                                        if(!Rs2Player.isInCombat() || i > 0) {
                                            // Attack or re-target to brother
                                            if(Rs2Npc.interact(brother, "Attack")) {
                                                Microbot.log("Attacking brother, attempt " + (i + 1));
                                                sleep(300, 500);
                                            }
                                        }
                                        
                                        // Check if we're fighting the right target
                                        if(Rs2Player.isInCombat()) {
                                            try {
                                                NPC currentHint = Microbot.getClient().getHintArrowNpc();
                                                if(currentHint != null) {
                                                    // Good, brother still alive
                                                    break;
                                                }
                                            } catch (Exception e) {
                                                // Brother dead
                                                break;
                                            }
                                        }
                                    }
                                }
                            } else {
                                // All brothers killed, just wait a bit for chest to open
                                sleep(500, 1000);
                            }
                        }

                        checkForBrother(config);

                        // After dealing with any brother, try to loot the chest
                        // Re-check for hint arrow in case another brother spawned
                        boolean hasHintArrow = false;
                        try {
                            hasHintArrow = (Microbot.getClient().getHintArrowNpc() != null);
                        } catch (Exception e) {
                            // No hint arrow
                        }
                        
                        // If there's still a brother, handle it
                        if(hasHintArrow) {
                            checkForBrother(config);
                            // Re-check again after second attempt
                            try {
                                hasHintArrow = (Microbot.getClient().getHintArrowNpc() != null);
                            } catch (Exception e) {
                                hasHintArrow = false;
                            }
                        }
                        
                        if(!hasHintArrow) {
                            // Reset the flag before we start looting
                            chestLooted = false;
                            
                            // Keep trying to loot the chest until we get the game message
                            long lootTimeout = System.currentTimeMillis() + 30000; // 30 second timeout
                            int attempts = 0;
                            while (!chestLooted && System.currentTimeMillis() < lootTimeout) {
                                if (!super.isRunning()) {
                                    break;
                                }
                                
                                // CRITICAL: Check if a brother spawned during looting attempts
                                boolean brotherSpawned = false;
                                try {
                                    brotherSpawned = (Microbot.getClient().getHintArrowNpc() != null);
                                } catch (Exception e) {
                                    // No hint arrow
                                }
                                
                                if (brotherSpawned) {
                                    Microbot.log("Brother spawned during chest interaction! Must kill before looting.");
                                    // Exit the looting loop to handle the brother
                                    break;
                                }
                                
                                attempts++;
                                if (attempts > 10) {
                                    Microbot.log("Failed to loot chest after 10 attempts, breaking out");
                                    break;
                                }
                                
                                // Try to search the chest
                                if (Rs2GameObject.interact(chest, "Search")) {
                                    // Wait a bit for the loot to appear and message to trigger
                                    sleep(1000, 2000);
                                    
                                    // Check again after interaction in case brother spawned
                                    try {
                                        brotherSpawned = (Microbot.getClient().getHintArrowNpc() != null);
                                    } catch (Exception e) {
                                        // No hint arrow
                                    }
                                    
                                    if (brotherSpawned) {
                                        Microbot.log("Brother spawned after chest search! Breaking to handle.");
                                        break;
                                    }
                                }
                                
                                // Check if we got the loot message
                                if (chestLooted) {
                                    Microbot.log("Chest successfully looted!");
                                    break;
                                }
                                
                                // Small delay before retrying
                                sleep(500, 1000);
                            }
                        }
                        
                        // After the looting loop, check if we exited due to brother spawn
                        if (!chestLooted) {
                            boolean hasBrother = false;
                            try {
                                hasBrother = (Microbot.getClient().getHintArrowNpc() != null);
                            } catch (Exception e) {
                                // No hint arrow
                            }
                            
                            if (hasBrother) {
                                Microbot.log("Handling spawned brother before attempting to loot again");
                                checkForBrother(config);
                                // The chest interaction will be retried in the next main loop iteration
                            }
                        }
                        
                        // Check if we successfully looted
                        if(chestLooted) {
                            //we looted the chest time to reset
                            chestLooted = false; // Reset for next run
                            
                            // For Walker method, ALWAYS bank after chest looting
                            if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                shouldBank = true;
                                walkerNeedsBankingAfterChest = true; // FORCE banking sequence
                                Microbot.log("Walker method - forcing bank after chest loot");
                            } else {
                                // For other methods, check if we need to bank
                                suppliesCheck(config);
                            }
                            
                            // Reset for next run
                            ChestsOpened++;
                            WhoisTun = "Unknown";
                            inTunnels = false;
                            
                            // Disable all prayers before leaving tunnels
                            Rs2Prayer.disableAllPrayers();
                            
                            Microbot.log("Chest looted, leaving tunnels");
                            
                            // Now leave - always teleport to Ferox if we need to bank
                            if(shouldBank) {
                                // Need to bank - ALWAYS teleport to Ferox using Ring of Dueling
                                if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
                                    Microbot.log("Looted chest, teleporting to bank.");
                                    sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                    sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                    // Wait to ensure we're out of tunnels
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(3000, 5000));
                                }
                            } else {
                                // Don't need to bank - use configured teleport method
                                if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.BARROWS_TELEPORT){
                                    if(Rs2Inventory.interact("Barrows teleport", "Break")) {
                                        Microbot.log("Using Barrows teleport");
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                    }
                                } else if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE) {
                                    if(Rs2Inventory.interact("Teleport to house", "Inside")) {
                                        Microbot.log("Using house teleport");
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                        handlePOH(config);
                                    }
                                } else if(config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                    // This should never execute because Walker always banks after chest
                                    // But keep it as a safety fallback
                                    Microbot.log("WARNING: Walker method without banking - this shouldn't happen");
                                    if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
                                        Microbot.log("Teleporting to Ferox anyway");
                                        sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                        sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(3000, 5000));
                                    }
                                    shouldBank = true; // Force banking just in case
                                }
                            }
                            
                            // Important: return here to exit the current loop iteration and restart fresh
                            return;
                        }
                    }
                    tunnelLoopCount++;
                }

                if(shouldBank || walkerNeedsBankingAfterChest){
                    if(!Rs2Bank.isOpen()){
                        //stop the walker
                        stopFutureWalker();
                        
                        // Use the proper BankLocation method to walk to and open Ferox bank
                        if(walkerNeedsBankingAfterChest) {
                            Microbot.log("Walker: Forced banking sequence after chest loot");
                        }
                        Microbot.log("Walking to and opening Ferox bank");
                        if(Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE)) {
                            // Wait for bank to actually open
                            sleepUntil(() -> Rs2Bank.isOpen(), Rs2Random.between(3000, 5000));
                        } else {
                            // Fallback: if we're already at Ferox, try direct open
                            WorldPoint feroxLocation = new WorldPoint(3150, 3635, 0);
                            if(Rs2Player.getWorldLocation().distanceTo(feroxLocation) < 30) {
                                Microbot.log("Already at Ferox, trying direct bank open");
                                Rs2Bank.openBank();
                                sleepUntil(() -> Rs2Bank.isOpen(), Rs2Random.between(3000, 5000));
                            } else {
                                // Not at Ferox, teleport there
                                outOfSupplies(config);
                            }
                        }
                        
                        //unlock
                        plugin.getLockCondition().unlock();
                    } else {
                        // Bank is open - handle banking
                        Rs2Food ourfood = config.food();
                        int ourFoodsID = ourfood.getId();
                        String ourfoodsname = ourfood.getName();
                        
                        // First, track any Barrows pieces we got
                        if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s"))){
                            Rs2ItemModel piece = Rs2Inventory.get(it->it!=null&&it.getName().contains("'s"));
                            if(piece!=null){
                                barrowsPieces.add(piece.getName());
                                if(barrowsPieces.contains("Nothing yet.")){
                                    barrowsPieces.remove("Nothing yet.");
                                }
                            }
                        }
                        
                        // Use inventory setup to load correct items
                        Microbot.log("Loading inventory setup for banking");
                        inventorySetup.loadInventory();
                        
                        // Wait a bit for inventory to load
                        sleep(1000, 2000);
                        
                        // Ensure we have a ring of dueling equipped
                        if(Rs2Equipment.get(EquipmentInventorySlot.RING) == null || !Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling")) {
                            if(Rs2Bank.count(ItemID.RING_OF_DUELING8) > 0) {
                                Rs2Bank.withdrawAndEquip(ItemID.RING_OF_DUELING8);
                                sleepUntil(() -> Rs2Equipment.get(EquipmentInventorySlot.RING) != null && 
                                         Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling"), 
                                         Rs2Random.between(3000, 5000));
                            } else {
                                Microbot.log("Out of rings of dueling");
                                super.shutdown();
                            }
                        }

                        int howtoBank = Rs2Random.between(0,100);
                        if(!usingPoweredStaffs) {
                            if (howtoBank <= 40) {
                                if (Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= config.minRuneAmount()) {
                                    if (Rs2Bank.getBankItem(neededRune) != null) {
                                        if (Rs2Bank.getBankItem(neededRune).getQuantity() > config.minRuneAmount()) {
                                            if (Rs2Bank.withdrawX(neededRune, Rs2Random.between(config.minRuneAmount(), Rs2Bank.getBankItem(neededRune).getQuantity()))) {
                                                String therune = neededRune;
                                                sleepUntil(() -> Rs2Inventory.get(therune).getQuantity() > config.minRuneAmount(), Rs2Random.between(2000, 4000));
                                            }
                                        }
                                    } else {
                                        if(neededRune.equals("Wrath rune")){
                                            if(Rs2Bank.hasItem("Blood rune") && Rs2Bank.count("Blood rune") > config.minRuneAmount()){
                                                neededRune = "Blood rune";
                                                return;
                                            }
                                        }
                                        Microbot.log("We're out of " + neededRune + "s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        } else {
                            if(outOfPoweredStaffCharges){
                                Microbot.log("We're out of staff charges. stopping...");
                                super.shutdown();
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 60){
                            if(Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())){
                                if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID())!=null){
                                    if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID()).getQuantity()>=config.targetPrayerPots()){
                                        int amt = ((Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())) - (Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID())));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(config.prayerRestoreType().getPrayerRestoreTypeID(), amt)){
                                            sleepUntil(()-> Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) > Rs2Random.between(4,8), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.prayerRestoreType().getPrayerRestoreTypeID()+" need at least "+config.targetPrayerPots()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(config.minForgottenBrew() > 0) {
                                if (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) {
                                    if (Rs2Bank.getBankItem("Forgotten brew(4)") != null) {
                                        if (Rs2Bank.getBankItem("Forgotten brew(4)").getQuantity() >= config.targetForgottenBrew()) {
                                            int amt = ((Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) - (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)")));
                                            if (amt <= 0) {
                                                amt = 1;
                                            }
                                            Microbot.log("Withdrawing " + amt);
                                            if (Rs2Bank.withdrawX("Forgotten brew(4)", amt)) {
                                                sleepUntil(() -> Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") > Rs2Random.between(1, 3), Rs2Random.between(2000, 4000));
                                            }
                                        } else {
                                            Microbot.log("We're out of " + " Forgotten brew " + " need at least " + config.targetForgottenBrew() + " stopping...");
                                            super.shutdown();
                                        }
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            // Skip teleport item withdrawal for Walker method
                            if(!config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                if(Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())==null || Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity() < Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports())){
                                    if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())!=null){
                                        if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity()>=config.targetBarrowsTeleports()){
                                            if(Rs2Bank.withdrawX(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID(), Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports()))){
                                                sleep(Rs2Random.between(300,750));
                                            }
                                        } else {
                                            Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                            super.shutdown();
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){

                            if(Rs2Inventory.count(ourFoodsID) < config.targetFoodAmount()){
                                if(Rs2Bank.getBankItem(ourFoodsID)!=null){
                                    if(Rs2Bank.getBankItem(ourFoodsID).getQuantity()>=config.targetFoodAmount()){
                                        int amt = (Rs2Random.between(config.minFood(),config.targetFoodAmount()) - (Rs2Inventory.count(ourFoodsID)));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(ourFoodsID, amt)){
                                            sleepUntil(()-> Rs2Inventory.count(ourFoodsID) >= 10, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+ourfoodsname+" need at least "+config.targetFoodAmount()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(!Rs2Inventory.contains("Spade")){
                                if(Rs2Bank.getBankItem("Spade")!=null){
                                    if(Rs2Bank.getBankItem("Spade").getQuantity()>=1){
                                        Rs2Bank.withdrawOne("Spade");
                                        sleepUntil(()-> Rs2Inventory.contains("Spade"), Rs2Random.between(2000,4000));
                                    } else {
                                        Microbot.log("We're out of "+"Spade"+"s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank <= 40){
                            if(Rs2Equipment.get(EquipmentInventorySlot.RING)!=null){
                                // we have our ring do nothing
                            } else {
                                Microbot.log("Getting the ring of dueling");
                                if(Rs2Bank.count(ItemID.RING_OF_DUELING8)>0){
                                    if(!Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                                        if(Rs2Bank.withdrawX(ItemID.RING_OF_DUELING8, 1)){
                                            sleepUntil(()-> Rs2Inventory.contains(ItemID.RING_OF_DUELING8), Rs2Random.between(5000,15000));
                                        }
                                    }
                                } else {
                                    Microbot.log("Out of rings of dueling");
                                    super.shutdown();
                                }
                                if(Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                                    if(Rs2Inventory.interact(ItemID.RING_OF_DUELING8, "Wear")){
                                        sleepUntil(()-> Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling"), Rs2Random.between(5000,15000));
                                    }
                                }
                            }
                        }

                        // Always check supplies after banking to update shouldBank status
                        suppliesCheck(config);

                        // Handle Walker forced banking completion
                        if(walkerNeedsBankingAfterChest && !shouldBank) {
                            // We have supplies now, complete the sequence
                            Microbot.log("Walker: Supplies restocked, completing banking sequence");
                            closeBank();
                            if(!Rs2Bank.isOpen()) {
                                reJfount();
                                walkerNeedsBankingAfterChest = false;
                                Microbot.log("Walker: Banking and pool complete, ready for next run");
                            }
                        } else if(!shouldBank){
                            // Normal banking completion
                            closeBank();
                            if(!Rs2Bank.isOpen()){
                                reJfount();
                                
                                if (!config.selectedToBarrowsTPMethod().name().equals("Walker")) {
                                    handlePOH(config);
                                }
                            }
                        } else {
                            if(Rs2Player.getRunEnergy() <= 5){
                                closeBank();
                                if(!Rs2Bank.isOpen()){
                                    reJfount();
                                }
                            }
                        }

                    }
                }

                scriptDelay = Rs2Random.between(200,750);
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Error in Barrows script: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, scriptDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    public void checkForWorldMap(){
        if(Rs2Widget.getWidget(38993938) != null){
            if(Rs2Widget.getWidget(38993938).getText().contains("Key")){
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
        }
    }

    public void closeBank(){
        if(Rs2Bank.isOpen()){
            while(Rs2Bank.isOpen()) {

                if(!super.isRunning()){break;}

                if (Rs2Bank.closeBank()) {
                    sleepUntil(() -> !Rs2Bank.isOpen(), Rs2Random.between(2000, 4000));
                }
            }
        }
        // Reset gear swap flag for new run
        gearSwapped = false;
        originalGear.clear();
    }

    public void handlePOH(BarrowsConfig config){
        if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE){
            if(Rs2GameObject.getGameObject(4525) != null){
                Microbot.log("We're in our POH");
                GameObject rejPool = Rs2GameObject.getGameObject(it->it!=null&&it.getId() == 29238 || it.getId() == 29239 || it.getId() == 29241 || it.getId() == 29240);
                if(rejPool != null){
                    if(Rs2GameObject.interact(rejPool, "Drink")){
                        sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                        sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                    }
                }
                GameObject regularPortal = Rs2GameObject.getGameObject("Barrows Portal");
                if(regularPortal != null){
                    while(Rs2GameObject.getGameObject(4525) != null){
                        if(!super.isRunning()){break;}
                        if(!Rs2Player.isMoving()){
                            if(Rs2GameObject.interact(regularPortal, "Enter")){
                                sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                                sleepUntil(()-> Rs2GameObject.getGameObject("Barrows Portal") == null, Rs2Random.between(10000,15000));
                            }
                        }
                    }

                } else {
                    // we have a nexus 33410
                    Microbot.log("No nexus support yet, shutting down");
                    super.shutdown();
                }
            }
        }
    }

    public boolean everyBrotherWasKilled(){
        if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1&&
                Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1){
            return true;
        }
        return false;
    }

    public void dialogueEnterTunnels(){
        if (Rs2Dialogue.isInDialogue()) {
            while(Rs2Dialogue.isInDialogue()) {
                if (!super.isRunning()) {
                    break;
                }
                if (Rs2Dialogue.hasContinue()) {
                    Rs2Dialogue.clickContinue();
                    sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!"), Rs2Random.between(2000, 5000));
                    sleep(300, 600);
                }
                if (Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")) {
                    if (Rs2Dialogue.clickOption("Yeah I'm fearless!")) {
                        sleepUntil(() -> Rs2Player.getWorldLocation().getY() > 9600 && Rs2Player.getWorldLocation().getY() < 9730, Rs2Random.between(2500, 6000));
                        //allow some time for the tunnel to load.
                        sleep(1000, 2000);
                        inTunnels = true;
                    }
                }
                if (!Rs2Dialogue.isInDialogue()) {
                    break;
                }
                if (inTunnels) {
                    break;
                }
                if (Rs2Player.getWorldLocation().getPlane() != 3) {
                    //we're not in the mound
                    break;
                }
            }
        }
    }

    public void digIntoTheMound(Rs2WorldArea moundArea){
        while (moundArea.contains(Rs2Player.getWorldLocation()) && Rs2Player.getWorldLocation().getPlane() != 3) {
            checkForWorldMap();

            if (!super.isRunning()) {
                break;
            }

            if (Rs2Inventory.contains("Spade")) {
                if (Rs2Inventory.interact("Spade", "Dig")) {
                    sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 3, Rs2Random.between(3000, 5000));
                }
            }

            if (Rs2Player.getWorldLocation().getPlane() == 3) {
                //we made it in
                break;
            }
        }
    }

    public void goToTheMound(Rs2WorldArea moundArea){
        while (!moundArea.contains(Rs2Player.getWorldLocation())) {
            checkForWorldMap();
            int totalTiles = moundArea.toWorldPointList().size();
            WorldPoint randomMoundTile;
            if (!super.isRunning()) {
                break;
            }

            antiPatternDropVials();
            //antipattern

            // We're not in the mound yet.
            randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
            if(Rs2Walker.walkTo(randomMoundTile)){
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
            }
            if (moundArea.contains(Rs2Player.getWorldLocation())) {
                if(!Rs2Player.isMoving()) {
                    break;
                }
            } else {
                Microbot.log("At the mound, but we can't dig yet.");
                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));

                //strange old man body blocking us
                if(Rs2Npc.getNpc("Strange Old Man")!=null){
                    if(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() != null){
                        if(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() == randomMoundTile){
                            while(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() == randomMoundTile){
                                if(!super.isRunning()){break;}
                                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                sleep(250,500);
                            }
                        }
                    }
                }

                Rs2Walker.walkCanvas(randomMoundTile);
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
            }
        }
    }

    public void leaveTheMound(){
        if(Rs2GameObject.get("Staircase", true) != null) {
            if (Rs2GameObject.hasLineOfSight(Rs2GameObject.get("Staircase", true))) {
                if (Rs2Player.getWorldLocation().getPlane() == 3) {
                    while (Rs2Player.getWorldLocation().getPlane() == 3) {
                        Microbot.log("Leaving the mound");
                        if (!super.isRunning()) {
                            break;
                        }
                        if (Rs2GameObject.interact("Staircase", "Climb-up")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != 3, Rs2Random.between(3000, 6000));
                        }
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            //anti pattern turn off prayer
                            disablePrayer();
                            //anti pattern turn off prayer
                            break;
                        }
                    }
                }
                if (inTunnels) {
                    inTunnels = false;
                }
            }
        }
    }

    public void gainRP(BarrowsConfig config){
        if(shouldAttackSkeleton){
            // ALWAYS check RP first, even if we're in combat
            int currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
            
            // Check if there's still a brother alive (will give us ~100 RP)
            boolean brotherAlive = false;
            try {
                brotherAlive = (Microbot.getClient().getHintArrowNpc() != null);
            } catch (Exception e) {
                // No hint arrow, no brother
            }
            
            // Calculate target RP considering if brother is still alive
            int targetRP = 870;
            if(brotherAlive) {
                // Brother will give us about 100 RP, so we need less from skeletons
                targetRP = 770;  // 770 + 100 from brother = 870
            }
            
            if(currentRP >= targetRP){
                Microbot.log("We have enough RP (" + currentRP + "/" + targetRP + "), stopping skeleton fights");
                // If we're in combat, just let it end naturally by returning
                return;
            }
            
            Rs2NpcModel skele = Rs2Npc.getNpc("Skeleton");
            if(skele == null || skele.isDead()){
                return;
            }
            
            // If we're already in combat with something, check if we have enough RP to ignore it
            if(Rs2Player.isInCombat()){
                // Check current RP before continuing the fight
                currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                targetRP = 870;
                
                try {
                    NPC hintNpc = Microbot.getClient().getHintArrowNpc();
                    if(hintNpc != null) {
                        // Brother is still alive, they'll give us ~100 RP
                        targetRP = 770;  // 770 + 100 from brother = 870
                    }
                } catch (Exception e) {
                    // No hint arrow, no brother alive
                }
                
                if(currentRP >= targetRP){
                    Microbot.log("Already in combat but have enough RP (" + currentRP + "/" + targetRP + "), ignoring");
                    return;
                }
            }
            
            if(Rs2Npc.hasLineOfSight(skele)){
                stopFutureWalker();
                if(!Rs2Player.isInCombat()){
                    // Check RP BEFORE attacking - don't attack if we already have enough
                    currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                    targetRP = 870;
                    
                    try {
                        NPC hintNpc = Microbot.getClient().getHintArrowNpc();
                        if(hintNpc != null) {
                            // Brother is still alive, they'll give us ~100 RP
                            targetRP = 770;  // 770 + 100 from brother = 870
                        }
                    } catch (Exception e) {
                        // No hint arrow, no brother alive
                    }
                    
                    if(currentRP >= targetRP){
                        Microbot.log("Have enough RP (" + currentRP + "/" + targetRP + "), not attacking skeleton");
                        return;
                    }
                    
                    if(Rs2Npc.attack(skele)){
                        sleepUntil(()-> Rs2Player.isInCombat()&&!Rs2Player.isMoving(), Rs2Random.between(4000,8000));
                    }
                }
                if(Rs2Player.isInCombat()){
                    long combatTimeout = System.currentTimeMillis() + 60000; // 60 second timeout
                    boolean inCombat = true;
                    
                    while(inCombat && System.currentTimeMillis() < combatTimeout){
                        Microbot.log("Fighting the Skeleton.");
                        if (!super.isRunning()) {
                            break;
                        }
                        sleep(750,1500);
                        eatFood();
                        outOfSupplies(config);
                        antiPatternDropVials();

                        if(shouldBank){
                            Microbot.log("Breaking out we're out of supplies.");
                            break;
                        }

                        // Check combat status with error handling
                        try {
                            inCombat = Rs2Player.isInCombat();
                            if(!inCombat){
                                Microbot.log("Breaking out we're no longer in combat.");
                                break;
                            }
                        } catch (Exception e) {
                            Microbot.log("Error checking combat status, breaking out: " + e.getMessage());
                            break;
                        }

                        // Null check before accessing skele
                        if(skele == null || skele.isDead()){
                            Microbot.log("Breaking out the skeleton is null or dead.");
                            break;
                        }

                        // Smart RP checking - account for brother if still alive
                        currentRP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
                        targetRP = 870;
                        
                        try {
                            NPC hintNpc = Microbot.getClient().getHintArrowNpc();
                            if(hintNpc != null) {
                                // Brother is still alive, they'll give us ~100 RP
                                targetRP = 770;  // 770 + 100 from brother = 870
                                
                                Rs2NpcModel barrowsbrother = new Rs2NpcModel(hintNpc);
                                if(Rs2Npc.hasLineOfSight(barrowsbrother)) {
                                    Microbot.log("The brother is here, stopping skeleton fight.");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // No hint arrow, no brother alive
                        }
                        
                        if(currentRP >= targetRP){
                            Microbot.log("Breaking out we have enough RP (" + currentRP + "/" + targetRP + ")");
                            if (!Rs2Player.isMoving()) {
                                startWalkingToTheChest();
                            }
                            break;
                        }

                    }
                    
                    // After combat ends, immediately resume walking to chest if we're not already moving
                    if (!Rs2Player.isMoving() && !Rs2Player.isInCombat()) {
                        startWalkingToTheChest();
                    }
                }
            }
        }
    }
    public void stopFutureWalker(){
        if(WalkToTheChestFuture!=null) {
            Rs2Walker.setTarget(null);
            WalkToTheChestFuture.cancel(true);
            //stop the walker and future
        }
    }
    public void suppliesCheck(BarrowsConfig config){
        boolean needsTeleportItem = !config.selectedToBarrowsTPMethod().name().equals("Walker");
        
        if(!usingPoweredStaffs) {
            if (Rs2Equipment.get(EquipmentInventorySlot.RING) == null || !Rs2Inventory.contains("Spade") ||
                    Rs2Inventory.count(config.food().getName()) < 2 || 
                    (needsTeleportItem && Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null) ||
                    Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews ||
                    !Rs2Inventory.contains(it -> it != null && (it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"))) ||
                    Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= minRuneAmt || Rs2Player.getRunEnergy() <= 5) {
                Microbot.log("We need to bank.");
                if (Rs2Equipment.get(EquipmentInventorySlot.RING) == null) {
                    Microbot.log("We don't have a ring of dueling equipped.");
                }
                if (!Rs2Inventory.contains("Spade")) {
                    Microbot.log("We don't have a spade.");
                }
                if (Rs2Inventory.count(config.food().getName()) < 2) {
                    Microbot.log("We have less than 2 food.");
                }
                if (needsTeleportItem && (Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null)) {
                    Microbot.log("We don't have a "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName());
                }
                if (Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews) {
                    Microbot.log("We forgot our Forgotten brew.");
                }
                if (!Rs2Inventory.contains(it -> it != null && (it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth")))) {
                    Microbot.log("We don't have any prayer restore items");
                }
                if (Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= minRuneAmt) {
                    Microbot.log("We have less than 180 " + neededRune);
                }
                if(Rs2Player.getRunEnergy() <= 5){
                    Microbot.log("We need more run energy ");
                }
                shouldBank = true;
            } else {
                shouldBank = false;
            }
        }
        if(usingPoweredStaffs){
            if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null || !Rs2Inventory.contains("Spade") ||
                    Rs2Inventory.count(config.food().getName())<2 || 
                    (needsTeleportItem && Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null) ||
                    Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews ||
                    !Rs2Inventory.contains(it -> it != null && (it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"))) || outOfPoweredStaffCharges
                    || Rs2Player.getRunEnergy() <= 5){
                Microbot.log("We need to bank.");
                if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null){
                    Microbot.log("We don't have a ring of dueling equipped.");
                }
                if(!Rs2Inventory.contains("Spade")){
                    Microbot.log("We don't have a spade.");
                }
                if(Rs2Inventory.count(config.food().getName())<2){
                    Microbot.log("We have less than 2 food.");
                }
                if(needsTeleportItem && (Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) ==null)){
                    Microbot.log("We don't have a "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName());
                }
                if(Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews){
                    Microbot.log("We forgot our Forgotten brew.");
                }
                if(!Rs2Inventory.contains(it -> it != null && (it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth")))){
                    Microbot.log("We don't have any prayer restore items");
                }
                if(outOfPoweredStaffCharges){
                    Microbot.log("We're out of staff charges.");
                }
                if(Rs2Player.getRunEnergy() <= 5){
                    Microbot.log("We need more run energy ");
                }
                shouldBank = true;
            } else {
                shouldBank = false;
            }
        }
    }
    public void stuckInTunsCheck(){
        //needed for rare occasions where the walker messes up
        if(tunnelLoopCount < 1){
            FirstLoopTile = Rs2Player.getWorldLocation();
        }
        if(tunnelLoopCount >= 15){
            WorldPoint currentTile = Rs2Player.getWorldLocation();
            if(currentTile!=null&&FirstLoopTile!=null){
                if(currentTile.equals(FirstLoopTile)){
                    Microbot.log("We seem to be stuck. Resetting the walker");
                    stopFutureWalker();
                    tunnelLoopCount = 0;
                }
            }
        }
        if(tunnelLoopCount >= 30){
            tunnelLoopCount = 0;
        }
    }

    public void gettheRune(BarrowsConfig config){
        neededRune = config.selectedSpell().getRuneType();
        
        // If it's a powered staff, we don't need catalytic runes
        if(neededRune.equals("none")) {
            neededRune = "unknown"; // Keep as unknown for powered staff
        }
    }

    public void setAutoCast(BarrowsConfig config){
        switch(config.selectedSpell()) {
            case WIND_BOLT:
                if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_BOLT) {
                    Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BOLT, false);
                }
                break;
            case WIND_BLAST:
                if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_BLAST) {
                    Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BLAST, false);
                }
                break;
            case WIND_WAVE:
                if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_WAVE) {
                    Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_WAVE, false);
                }
                break;
            case WIND_SURGE:
                if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_SURGE) {
                    Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_SURGE, false);
                }
                break;
            case POWERED_STAFF:
                // No autocast needed for powered staffs
                break;
        }
    }

    public void activatePrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            Microbot.log("Turning on Prayer.");
            long prayerTimeout = System.currentTimeMillis() + 10000; // 10 second timeout
            while(!Rs2Prayer.isPrayerActive(NeededPrayer) && System.currentTimeMillis() < prayerTimeout){
                if (!super.isRunning()) {
                    break;
                }
                drinkPrayerPot();
                Rs2Prayer.toggle(NeededPrayer);
                sleep(0,750);
                if (Rs2Prayer.isPrayerActive(NeededPrayer)) {
                    Microbot.log("Praying");
                    break;
                }
            }
        }
    }
    public void antiPatternEnableWrongPrayer(){
        // Only execute antipattern if enabled in config and we're inside a crypt (plane 3)
        if(!config.enableAntipattern() || Rs2Player.getWorldLocation().getPlane() != 3){
            return;
        }
        
        if(NeededPrayer != null && !Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,2)) { // Reduced from 1-4% to 1-2%
                Rs2PrayerEnum wrongPrayer = null;
                int random = Rs2Random.between(0,100);
                if(random <= 50){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_MELEE;
                }
                if(random > 50 && random < 75){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_RANGE;
                }
                if(random >= 75){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                }
                drinkPrayerPot();
                Rs2Prayer.toggle(wrongPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternActivatePrayer(){
        // Only execute antipattern if enabled in config and we're inside a crypt (plane 3)
        if(!config.enableAntipattern() || Rs2Player.getWorldLocation().getPlane() != 3){
            return;
        }
        
        if(NeededPrayer != null && !Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,3)) { // Reduced from 1-8% to 1-3%
                drinkPrayerPot();
                Rs2Prayer.toggle(NeededPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternDropVials(){
        if(Rs2Random.between(0,100) <= Rs2Random.between(1,25)) {
            Rs2ItemModel whatToDrop = Rs2Inventory.get(it->it!=null&&it.getName().contains("Vial")||it.getName().contains("Butterfly jar"));
            if(whatToDrop!=null) {
                if (Rs2Inventory.contains(whatToDrop.getName())) {
                    if (Rs2Inventory.drop(whatToDrop.getName())) {
                        sleep(0, 750);
                    }
                }
            }
        }
    }
    public void outOfSupplies(BarrowsConfig config){
        suppliesCheck(config);
        // Needed because the walker won't teleport to the enclave while in the tunnels or in a barrow
        if(shouldBank && (inTunnels || Rs2Player.getWorldLocation().getPlane() == 3)){
            if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
                Microbot.log("We're out of supplies. Teleporting.");
                if(inTunnels){
                    inTunnels=false;
                }
                sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
            }
        }
    }
    public void disablePrayer(){
        if(Rs2Random.between(0,100) >= Rs2Random.between(0,5)) {
            Rs2Prayer.disableAllPrayers();
            sleep(0,750);
        }
    }
    public void reJfount(){
        // Check if any stat needs restoration (health, prayer, or run energy not at 100%)
        int currentHealth = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHealth = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        int currentRunEnergy = Rs2Player.getRunEnergy();
        
        // Use pool if health, prayer, or run energy is not at 100%
        boolean needsRestoration = currentHealth < maxHealth || 
                                  currentPrayer < maxPrayer || 
                                  currentRunEnergy < 100;
        
        if (!needsRestoration) {
            Microbot.log("Stats already at 100%, skipping pool");
            return;
        }
        
        Microbot.log("Using pool to restore stats (HP: " + currentHealth + "/" + maxHealth + 
                    ", Prayer: " + currentPrayer + "/" + maxPrayer + 
                    ", Run: " + currentRunEnergy + "%)");
        
        // Close bank if open
        if(Rs2Bank.isOpen()){
            if(Rs2Bank.closeBank()){
                sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,4000));
            }
        }
        
        // Use the pool
        GameObject rej = Rs2GameObject.get("Pool of Refreshment", true);
        if(rej == null){ 
            Microbot.log("Pool of Refreshment not found!");
            return; 
        }
        
        Microbot.log("Drinking from pool");
        if(Rs2GameObject.interact(rej, "Drink")){
            sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(1000,3000));
            sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(5000,10000));
            sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
            sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
            
            // Log restored stats
            Microbot.log("Stats restored (HP: " + Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) + "/" + maxHealth + 
                        ", Prayer: " + Rs2Player.getBoostedSkillLevel(Skill.PRAYER) + "/" + maxPrayer + 
                        ", Run: " + Rs2Player.getRunEnergy() + "%)");
        }
    }
    public void drinkPrayerPot(){
        boolean skipThePot = false;
        NPC hintArrow = Microbot.getClient().getHintArrowNpc();
        Rs2NpcModel currentBrother = null;
        if(hintArrow != null)  currentBrother = new Rs2NpcModel(hintArrow);
        if(currentBrother != null && !currentBrother.getName().contains("Dharok") && currentBrother.getHealthPercentage() < Rs2Random.between(35,42)) skipThePot = true;

        if(!skipThePot) {
            if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= Rs2Random.between(3, 8)) {
                if (Rs2Inventory.contains(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"))) {
                    Rs2ItemModel prayerpotion = Rs2Inventory.get(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"));
                    String action = "Drink";
                    if (prayerpotion.getName().equals("Moonlight moth")) {
                        action = "Release";
                    }
                    if (Rs2Inventory.interact(prayerpotion, action)) {
                        sleep(0, 750);
                    }
                }
            }
        }
    }
    public void checkForBrother(BarrowsConfig config){
        NPC hintArrow = null;
        try {
            hintArrow = Microbot.getClient().getHintArrowNpc();
        } catch (NullPointerException e) {
            // Client internal NPE when no hint arrow exists
            return;
        }
        Rs2NpcModel currentBrother = null;
        if (hintArrow != null) {
            currentBrother = new Rs2NpcModel(hintArrow);
            stopFutureWalker();
            
            if (currentBrother != null && Rs2Npc.hasLineOfSight(currentBrother)) {
                // Check if we should pray against this specific brother
                boolean shouldPray = false;
                Rs2PrayerEnum neededprayer = Rs2PrayerEnum.PROTECT_MELEE; // default
                
                if (currentBrother.getName().contains("Dharok") && config.prayAgainstDharok()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
                } else if (currentBrother.getName().contains("Torag") && config.prayAgainstTorag()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
                } else if (currentBrother.getName().contains("Guthan") && config.prayAgainstGuthan()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
                } else if (currentBrother.getName().contains("Verac") && config.prayAgainstVerac()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
                } else if (currentBrother.getName().contains("Ahrim") && config.prayAgainstAhrim()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_MAGIC;
                } else if (currentBrother.getName().contains("Karil") && config.prayAgainstKaril()) {
                    shouldPray = true;
                    neededprayer = Rs2PrayerEnum.PROTECT_RANGE;
                }
                
                // Only swap gear and activate prayer if we should pray
                if (shouldPray) {
                    // Swap gear when praying in tunnels
                    swapGearForPrayer(config);
                    
                    // Disable wrong prayers first
                    if (neededprayer == Rs2PrayerEnum.PROTECT_MELEE) {
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE);
                    } else if (neededprayer == Rs2PrayerEnum.PROTECT_MAGIC) {
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE);
                    } else if (neededprayer == Rs2PrayerEnum.PROTECT_RANGE) {
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);
                        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
                    }
                    
                    //activate prayer
                    if(!Rs2Prayer.isPrayerActive(neededprayer)){
                        Microbot.log("Turning on Prayer.");
                        long prayerTimeout = System.currentTimeMillis() + 10000; // 10 second timeout
                        while(!Rs2Prayer.isPrayerActive(neededprayer) && System.currentTimeMillis() < prayerTimeout){
                            if (!super.isRunning()) {
                                break;
                            }
                            // Only drink prayer pots if in combat
                            try {
                                if (Rs2Player.isInCombat()) {
                                    drinkPrayerPot();
                                }
                            } catch (Exception e) {
                                Microbot.log("Error checking combat status: " + e.getMessage());
                            }
                            Rs2Prayer.toggle(neededprayer);
                            sleep(0,750);
                            if (Rs2Prayer.isPrayerActive(neededprayer)) {
                                //we made it in
                                Microbot.log("Praying");
                                break;
                            }
                        }
                    }
                }
                
                //fight brother - always fight regardless of prayer settings
                if(currentBrother != null && !Rs2Player.isInCombat()){
                    long attackTimeout = System.currentTimeMillis() + 15000; // 15 second timeout
                    while(!Rs2Player.isInCombat() && System.currentTimeMillis() < attackTimeout){
                        if (!super.isRunning()) {
                            break;
                        }
                        Microbot.log("Attacking the brother");
                        Rs2Npc.interact(currentBrother, "Attack");
                        sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                    }
                }
                //fighting
                    long fightTimeout = System.currentTimeMillis() + 120000; // 2 minute timeout
                    // Re-check hint arrow since it may have changed
                    try {
                        hintArrow = Microbot.getClient().getHintArrowNpc();
                    } catch (Exception e) {
                        Microbot.log("Error getting hint arrow: " + e.getMessage());
                        hintArrow = null;
                    }
                    
                    while(hintArrow != null && System.currentTimeMillis() < fightTimeout){
                        Microbot.log("Fighting the brother.");
                        if (!super.isRunning()) {
                            break;
                        }
                        if(currentBrother == null || !Rs2Npc.hasLineOfSight(currentBrother)){
                            break;
                        }
                        sleep(750,1500);
                        
                        // Only drink prayer pots if in combat and using prayer
                        try {
                            if (Rs2Player.isInCombat() && shouldPray) {
                                drinkPrayerPot();
                            }
                        } catch (Exception e) {
                            Microbot.log("Error checking combat/prayer: " + e.getMessage());
                        }
                        
                        eatFood();
                        outOfSupplies(config);
                        antiPatternDropVials();
                        drinkforgottonbrew();
                        
                        // Handle special attack weapon usage
                        if (currentBrother != null) {
                            specWeaponHandler.handleSpecWeaponUsage(currentBrother.getName(), config);
                        }

                        if(shouldPray && !Rs2Prayer.isPrayerActive(neededprayer)){
                            // Disable wrong prayers first
                            if (neededprayer == Rs2PrayerEnum.PROTECT_MELEE) {
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE);
                            } else if (neededprayer == Rs2PrayerEnum.PROTECT_MAGIC) {
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE);
                            } else if (neededprayer == Rs2PrayerEnum.PROTECT_RANGE) {
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);
                                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
                            }
                            
                            Microbot.log("Turning on Prayer.");
                            long prayerTimeout2 = System.currentTimeMillis() + 10000; // 10 second timeout
                            while(!Rs2Prayer.isPrayerActive(neededprayer) && System.currentTimeMillis() < prayerTimeout2){
                                if (!super.isRunning()) {
                                    break;
                                }
                                // Only drink prayer pots if in combat
                                try {
                                    if (Rs2Player.isInCombat()) {
                                        drinkPrayerPot();
                                    }
                                } catch (Exception e) {
                                    Microbot.log("Error checking combat: " + e.getMessage());
                                }
                                Rs2Prayer.toggle(neededprayer);
                                sleep(0,750);
                                if (Rs2Prayer.isPrayerActive(neededprayer)) {
                                    //we made it in
                                    Microbot.log("Praying");
                                    break;
                                }
                            }
                        }

                        // Re-check hint arrow at end of loop
                        try {
                            hintArrow = Microbot.getClient().getHintArrowNpc();
                        } catch (Exception e) {
                            Microbot.log("Error updating hint arrow: " + e.getMessage());
                            hintArrow = null;
                        }
                        
                        try {
                            if(!Rs2Player.isInCombat()){
                                if(hintArrow == null) {
                                    // if we're not in combat and the brother isn't there.
                                    Microbot.log("Breaking out hint arrow is null.");
                                    break;
                                } else {
                                    // if we're not in combat and the brother is there.
                                    Microbot.log("Attacking the brother");
                                    if (currentBrother != null) {
                                        Rs2Npc.interact(currentBrother, "Attack");
                                        sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Microbot.log("Error checking combat status: " + e.getMessage());
                            break;
                        }

                        if(currentBrother != null && currentBrother.isDead()){
                            Microbot.log("Breaking out the brother is dead.");
                            // Disable prayer and restore gear if we were praying in tunnels
                            if(shouldPray) {
                                disablePrayer();
                                restoreOriginalGear();
                            }
                            
                            // Restore spec weapon gear if needed
                            specWeaponHandler.restorePreSpecGear();
                            sleepUntil(()-> Microbot.getClient().getHintArrowNpc() == null, Rs2Random.between(3000,6000));
                            break;
                        }

                    }
            }
        }
    }

    private void swapGearForPrayer(BarrowsConfig config) {
        String gearSwapConfig = config.prayerGearSwap();
        if (gearSwapConfig == null || gearSwapConfig.trim().isEmpty()) {
            return; // No gear swap configured
        }
        
        if (gearSwapped) {
            return; // Already swapped
        }
        
        Microbot.log("Swapping gear for prayer fight");
        originalGear.clear();
        
        // Save currently equipped items before swapping
        // We check common slots that might be swapped for magic gear
        EquipmentInventorySlot[] slotsToCheck = {
            EquipmentInventorySlot.AMULET,
            EquipmentInventorySlot.RING,
            EquipmentInventorySlot.GLOVES,
            EquipmentInventorySlot.BOOTS,
            EquipmentInventorySlot.CAPE,
            EquipmentInventorySlot.SHIELD,
            EquipmentInventorySlot.WEAPON,
            EquipmentInventorySlot.HEAD,
            EquipmentInventorySlot.BODY,
            EquipmentInventorySlot.LEGS
        };
        
        for (EquipmentInventorySlot slot : slotsToCheck) {
            Rs2ItemModel equippedItem = Rs2Equipment.get(slot);
            if (equippedItem != null) {
                originalGear.put(slot, equippedItem.getName());
                Microbot.log("Saved " + slot + ": " + equippedItem.getName());
            }
        }
        
        String[] items = gearSwapConfig.split(",");
        for (String item : items) {
            String itemName = item.trim();
            if (itemName.isEmpty()) continue;
            
            // Check if we have the item in inventory
            if (!Rs2Inventory.hasItem(itemName)) {
                Microbot.log("Missing item for prayer gear swap: " + itemName);
                continue;
            }
            
            // Equip the item
            if (Rs2Inventory.wield(itemName)) {
                Microbot.log("Equipped: " + itemName);
                sleep(300, 600);
            }
        }
        
        gearSwapped = true;
    }
    
    private void restoreOriginalGear() {
        if (!gearSwapped) {
            return; // Nothing to restore
        }
        
        Microbot.log("Restoring original gear after prayer fight");
        
        // Re-equip the original items we saved
        for (Map.Entry<EquipmentInventorySlot, String> entry : originalGear.entrySet()) {
            String itemName = entry.getValue();
            
            // Check if we need to swap this slot (if something different is equipped)
            Rs2ItemModel currentlyEquipped = Rs2Equipment.get(entry.getKey());
            if (currentlyEquipped != null && currentlyEquipped.getName().equals(itemName)) {
                continue; // Already wearing the original item
            }
            
            // Check if we have the original item in inventory
            if (Rs2Inventory.hasItem(itemName)) {
                if (Rs2Inventory.wield(itemName)) {
                    Microbot.log("Re-equipped: " + itemName);
                    sleep(300, 600);
                }
            } else {
                Microbot.log("Cannot restore " + itemName + " - not in inventory");
            }
        }
        
        gearSwapped = false;
        originalGear.clear();
    }
    
    private void walkToChest(){
        Rs2Walker.walkTo(Chest);
    }

    private void startWalkingToTheChest() {
        if(WalkToTheChestFuture == null || WalkToTheChestFuture.isCancelled() || WalkToTheChestFuture.isDone()) {
            if(inTunnels) {
                WalkToTheChestFuture = scheduledExecutorService.scheduleWithFixedDelay(
                        this::walkToChest,
                        0,
                        500,
                        TimeUnit.MILLISECONDS
                );
            }
        }
    }

    public void drinkforgottonbrew() {
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Forgotten brew"))) {
                if(Rs2Player.getBoostedSkillLevel(Skill.MAGIC) <= (Rs2Player.getRealSkillLevel(Skill.MAGIC) + Rs2Random.between(1,4))) {
                    Microbot.log("Drinking a Forgotten brew.");
                    if(Rs2Inventory.contains("Forgotten brew(1)")) {
                        Rs2Inventory.interact("Forgotten brew(1)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(2)")) {
                        Rs2Inventory.interact("Forgotten brew(2)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(3)")) {
                        Rs2Inventory.interact("Forgotten brew(3)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(4)")) {
                        Rs2Inventory.interact("Forgotten brew(4)", "Drink");
                        sleep(300,1000);
                    }
                }
            }
    }

    public void eatFood(){
        if(Rs2Player.getHealthPercentage() <= 60){
            if(Rs2Inventory.contains(it->it!=null&&it.isFood())){
                Rs2ItemModel food = Rs2Inventory.get(it->it!=null&&it.isFood());
                if(Rs2Inventory.interact(food, "Eat")){
                    sleep(0,750);
                }
            }
        }
    }
    public void solvePuzzle(){
        //correct model ids are  6725, 6731, 6713, 6719
        //widget ids are 1638413, 1638415,1638417

        int widgets[] = {1638413, 1638415, 1638417};
        int modelIDs[] = {6725, 6731, 6713, 6719};
        
        // Check if puzzle is open
        boolean puzzleOpen = false;
        for (int widget : widgets) {
            if(Rs2Widget.getWidget(widget) != null) {
                puzzleOpen = true;
                break;
            }
        }
        
        if(!puzzleOpen) {
            return; // No puzzle to solve
        }
        
        // Puzzle is open - solve it quickly
        stopFutureWalker();
        
        // Try to solve multiple times in case we get interrupted
        int attempts = 0;
        long timeout = System.currentTimeMillis() + 5000; // 5 second timeout
        
        while(attempts < 10 && System.currentTimeMillis() < timeout) {
            if(!super.isRunning()) break;
            
            boolean solved = false;
            
            for (int widget : widgets) {
                if(!super.isRunning()) break;

                Widget w = Rs2Widget.getWidget(widget);
                if(w != null){
                    for (int modelID : modelIDs) {
                        if(!super.isRunning()) break;

                        if(w.getModelId() == modelID){
                            Microbot.log("Solution found, clicking immediately!");
                            Rs2Widget.clickWidget(widget);
                            solved = true;
                            
                            // Very short sleep to see if puzzle closes
                            sleep(100, 200);
                            
                            // Check if puzzle is still open
                            boolean stillOpen = false;
                            for (int checkWidget : widgets) {
                                if(Rs2Widget.getWidget(checkWidget) != null) {
                                    stillOpen = true;
                                    break;
                                }
                            }
                            
                            if(!stillOpen) {
                                Microbot.log("Puzzle solved successfully!");
                                return; // Puzzle solved
                            } else {
                                Microbot.log("Puzzle still open, retrying...");
                            }
                            break;
                        }
                    }
                    if(solved) break;
                }
            }
            
            attempts++;
            
            // Check if puzzle disappeared (we might have been interrupted)
            boolean puzzleStillOpen = false;
            for (int widget : widgets) {
                if(Rs2Widget.getWidget(widget) != null) {
                    puzzleStillOpen = true;
                    break;
                }
            }
            
            if(!puzzleStillOpen) {
                Microbot.log("Puzzle closed, likely interrupted by combat");
                
                // Try to re-open the door quickly if we're near one
                GameObject door = Rs2GameObject.getGameObject("Door");
                if(door != null && Rs2GameObject.hasLineOfSight(door)) {
                    Microbot.log("Re-opening puzzle door");
                    Rs2GameObject.interact(door, "Open");
                    sleep(200, 300);
                }
                return;
            }
            
            // Very short sleep between attempts
            sleep(50, 100);
        }
    }


    public enum BarrowsBrothers {
        DHAROK ("Dharok the Wretched", new Rs2WorldArea(3573,3296,3,3,0), Rs2PrayerEnum.PROTECT_MELEE),
        KARIL  ("Karil the Tainted", new Rs2WorldArea(3564,3274,3,3,0), Rs2PrayerEnum.PROTECT_RANGE),
        AHRIM  ("Ahrim the Blighted", new Rs2WorldArea(3563,3288,3,3,0), Rs2PrayerEnum.PROTECT_MAGIC),
        GUTHAN ("Guthan the Infested", new Rs2WorldArea(3575,3280,3,3,0), Rs2PrayerEnum.PROTECT_MELEE),
        TORAG  ("Torag the Corrupted", new Rs2WorldArea(3552,3282,2,2,0), Rs2PrayerEnum.PROTECT_MELEE),
        VERAC  ("Verac the Defiled", new Rs2WorldArea(3556,3297,3,3,0), Rs2PrayerEnum.PROTECT_MELEE);

        private String name;

        private Rs2WorldArea humpWP;

        private Rs2PrayerEnum whatToPray;


        BarrowsBrothers(String name, Rs2WorldArea humpWP, Rs2PrayerEnum whatToPray) {
            this.name = name;
            this.humpWP = humpWP;
            this.whatToPray = whatToPray;
        }

        public String getName() { return name; }
        public Rs2WorldArea getHumpWP() { return humpWP; }
        public Rs2PrayerEnum getWhatToPray() { return whatToPray; }

    }

    @Override
    public void shutdown() {
        specWeaponHandler.reset();
        if (puzzleMonitorFuture != null) {
            puzzleMonitorFuture.cancel(true);
        }
        super.shutdown();
    }
}
