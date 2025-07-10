package net.runelite.client.plugins.microbot.constructionphials;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.constructionphials.ConstructionPhialsConfig;
import net.runelite.client.plugins.microbot.constructionphials.enums.ConstructionPhialsState;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class ConstructionPhialsScript extends Script {
    public static final double VERSION = 2.8;

    // Portal IDs
    private static final int HOUSE_PORTAL       = 4525;
    private static final int RIMM_PORTAL        = 15478;
    
    // Hotspot IDs (empty build spots)
    private static final int HOTSPOT_LARDER     = 15403;
    private static final int HOTSPOT_DUNGEON    = 15392;
    
    // Built furniture IDs
    private static final int BUILT_WOODEN_LARDER = 13565;
    private static final int BUILT_OAK_LARDER    = 13566;
    private static final int BUILT_OAK_DOOR      = 13344;
    
    // Other constants
    private static final int FURNITURE_WIDGET   = (458 << 16);
    private static final int PLANK_NOTED        = 961;
    private static final int OAK_PLANK_NOTED    = 8779;
    private static final int BUILD_TIMEOUT      = 2000;
    private static final int UNNOTE_THRESHOLD   = 8;

    private static final WorldPoint PHIALS_TILE = new WorldPoint(2956, 3224, 0);

    private final ScheduledExecutorService exec = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryBuilder().setNameFormat("construction-phials-%d").build());
    private ScheduledFuture<?> mainFuture;

    private ConstructionPhialsConfig cfg;
    @Getter private volatile ConstructionPhialsState state = ConstructionPhialsState.STOP;

    public boolean run(ConstructionPhialsConfig config) {
        this.cfg = config;
        log.info("Construction-Phials script starting with furniture type: {}", config.furniture());
        mainFuture = exec.scheduleWithFixedDelay(this::loop, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        log.info("Construction-Phials script shutting down");
        if (mainFuture != null) {
            mainFuture.cancel(true);
            mainFuture = null;
        }
    }

    private void loop() {
        if (!Microbot.isLoggedIn()) {
            if (state != ConstructionPhialsState.STOP) {
                log.debug("Not logged in, transitioning to STOP state");
                state = ConstructionPhialsState.STOP;
            }
            return;
        }
        
        ConstructionPhialsState oldState = state;
        
        switch (state) {
            case STOP:        
                // Determine initial state based on location and inventory
                if (isInHouse()) {
                    state = ConstructionPhialsState.BUILD;
                    log.info("Starting in house, transitioning to BUILD state");
                } else if (Rs2Inventory.count(unnotedPlankId()) >= UNNOTE_THRESHOLD) {
                    state = ConstructionPhialsState.ENTER_HOUSE;
                    log.info("Have planks outside house, transitioning to ENTER_HOUSE state");
                } else {
                    state = ConstructionPhialsState.UNNOTE;
                    log.info("Need planks outside house, transitioning to UNNOTE state");
                }
                break;
            case BUILD:       
                buildCycle();                           
                break;
            case EXIT_HOUSE:  
                exitHouse();                            
                break;
            case UNNOTE:      
                unnoteCycle();                          
                break;
            case ENTER_HOUSE: 
                enterHouse();                           
                break;
            case ANTIBAN:     
                antibanTick();                          
                break;
            default:          
                break;
        }
        
        if (oldState != state) {
            log.debug("State transition: {} -> {}", oldState, state);
        }
    }

    private boolean isInHouse() {
        // Check if house portal exists (we're inside)
        return Rs2GameObject.findObjectById(HOUSE_PORTAL) != null;
    }

    private void buildCycle() {
        // Safety check: ensure we're actually inside the house
        if (!isInHouse()) {
            log.warn("Not in house during BUILD state, transitioning to ENTER_HOUSE");
            state = ConstructionPhialsState.ENTER_HOUSE;
            return;
        }
        
        if (Rs2Inventory.count(unnotedPlankId()) < UNNOTE_THRESHOLD) {
            log.info("Low on planks ({} < {}), exiting house", Rs2Inventory.count(unnotedPlankId()), UNNOTE_THRESHOLD);
            // Small delay to ensure we're not mid-animation or mid-action
            sleep(600, 1000);
            state = ConstructionPhialsState.EXIT_HOUSE;
            return;
        }
        
        switch (cfg.furniture()) {
            case WOODEN_LARDER:
                buildAt(HOTSPOT_LARDER, BUILT_WOODEN_LARDER);
                break;
            case OAK_LARDER:
                buildAt(HOTSPOT_LARDER, BUILT_OAK_LARDER);
                break;
            case OAK_DUNGEON_DOOR:
                buildAt(HOTSPOT_DUNGEON, BUILT_OAK_DOOR);
                break;
        }
        
        if (Rs2Random.between(1, 1200) == 1) {
            log.debug("Triggering anti-ban");
            state = ConstructionPhialsState.ANTIBAN;
        }
    }

    /**
     * If a built object exists, remove it; otherwise build at the hotspot.
     * Both hotspot and built furniture are TileObjects, accessed by ID.
     */
    private void buildAt(int hotspotId, int builtId) {
        // Check for built furniture first
        TileObject built = Rs2GameObject.findObjectById(builtId);
        if (built != null) {
            log.debug("Found built furniture ID {}, attempting removal", builtId);
            if (Rs2GameObject.interact(built, "Remove")) {
                Rs2Dialogue.sleepUntilHasQuestion("Really remove it?");
                Rs2Dialogue.keyPressForDialogueOption(1);
                sleepUntil(() -> Rs2GameObject.findObjectById(builtId) == null, 5000);
                log.debug("Removed furniture ID {}", builtId);
            }
            return;
        }
        
        // No built furniture, check for hotspot
        TileObject hotspot = Rs2GameObject.findObjectById(hotspotId);
        if (hotspot == null) {
            log.debug("No hotspot found with ID {}", hotspotId);
            return;
        }
        
        if (Rs2Player.isAnimating()) {
            log.debug("Player is animating, skipping build");
            return;
        }
        
        log.debug("Building at hotspot {}", hotspotId);
        if (Rs2GameObject.interact(hotspot, "Build")) {
            if (sleepUntil(this::hasFurnitureInterfaceOpen, 3000)) {
                // Only select option if the interface actually opened
                selectOption();
                sleepUntil(() -> Rs2GameObject.findObjectById(builtId) != null, 5000);
                log.debug("Build action completed");
            } else {
                log.debug("Furniture interface did not open - likely out of planks");
            }
        }
    }

    private boolean hasFurnitureInterfaceOpen() {
        return Rs2Widget.getWidget(FURNITURE_WIDGET) != null;
    }

    private void selectOption() {
        // Double-check the interface is still open before pressing keys
        if (!hasFurnitureInterfaceOpen()) {
            log.warn("Furniture interface closed before selecting option");
            return;
        }
        
        switch (cfg.furniture()) {
            case WOODEN_LARDER:      
                Rs2Keyboard.keyPress('1'); 
                log.debug("Selected option 1 for Wooden larder");
                break;
            case OAK_LARDER:         
                Rs2Keyboard.keyPress('2'); 
                log.debug("Selected option 2 for Oak larder");
                break;
            case OAK_DUNGEON_DOOR:   
                Rs2Keyboard.keyPress('1'); 
                log.debug("Selected option 1 for Oak dungeon door");
                break;
        }
    }

    private void exitHouse() {
        // Safety check: ensure we're actually inside the house
        if (!isInHouse()) {
            log.warn("Already outside house, transitioning to UNNOTE");
            state = ConstructionPhialsState.UNNOTE;
            return;
        }
        
        TileObject portal = Rs2GameObject.findObjectById(HOUSE_PORTAL);
        if (portal == null) {
            log.warn("Cannot find house portal to exit");
            return;
        }
        
        log.debug("Exiting house through portal");
        if (Rs2GameObject.interact(portal, "Exit")) {
            Rs2Player.waitForAnimation(1200);
            sleepUntil(() -> !isInHouse(), 3000);
            state = ConstructionPhialsState.UNNOTE;
            log.info("Exited house, transitioning to UNNOTE");
            return;
        }
        
        // Walk closer if interaction failed
        Rs2Walker.walkFastLocal(portal.getLocalLocation());
    }

    private void unnoteCycle() {
        // Safety check: ensure we're actually outside the house
        if (isInHouse()) {
            log.warn("Still inside house during UNNOTE state, transitioning to EXIT_HOUSE");
            state = ConstructionPhialsState.EXIT_HOUSE;
            return;
        }
        
        // First check if we already have enough unnoted planks
        int currentPlanks = Rs2Inventory.count(unnotedPlankId());
        if (currentPlanks >= UNNOTE_THRESHOLD) {
            log.info("Already have {} unnoted planks, entering house", currentPlanks);
            state = ConstructionPhialsState.ENTER_HOUSE;
            return;
        }
        
        // Check for and close any blocking interfaces (like level-up)
        if (Rs2Widget.hasWidget("Congratulations") || Rs2Widget.hasWidget("Level")) {
            log.debug("Closing level-up interface");
            Rs2Widget.clickWidget("Close");
            sleep(600, 1000);
        }
        
        if (Rs2Npc.getNpc(NpcID.PHIALS) == null) {
            log.debug("Walking to Phials at {}", PHIALS_TILE);
            
            // Check if we're in Rimmington area (should be close to the portal)
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null) {
                int distance = playerLoc.distanceTo(PHIALS_TILE);
                if (distance > 50) {
                    log.warn("Player is very far from Phials (distance: {}), may be in wrong area", distance);
                    // Try to walk to the portal area first as a waypoint
                    WorldPoint portalArea = new WorldPoint(2953, 3224, 0);
                    Rs2Walker.walkTo(portalArea, 5);
                    sleep(1000, 2000);
                }
            }
            
            // Now try to walk to Phials
            if (!Rs2Walker.walkTo(PHIALS_TILE, 3)) {
                log.error("Failed to walk to Phials - path may be blocked");
                // As a fallback, try walking with minimap
                Rs2Walker.walkMiniMap(PHIALS_TILE);
            }
            return;
        }
        
        int noted = notedPlankId();
        if (Rs2Inventory.count(noted) == 0) {
            Microbot.showMessage("Out of planks – stopping");
            log.warn("No noted planks remaining, stopping script");
            state = ConstructionPhialsState.STOP;
            return;
        }
        
        log.debug("Unnoting planks with Phials (have {} noted)", Rs2Inventory.count(noted));
        
        // Try use-on-NPC first
        if (Rs2Inventory.use(noted)) {
            sleep(100, 200); // Small delay after using item
            if (Rs2Npc.interact(NpcID.PHIALS, "Use")) {
                log.debug("Used noted planks on Phials");
                // Wait for dialogue to appear
                if (Rs2Dialogue.sleepUntilInDialogue()) {
                    handleDialogue();
                }
            } else {
                log.debug("Failed to use on Phials, trying Exchange-All");
                if (Rs2Npc.interact(NpcID.PHIALS, "Exchange-All")) {
                    if (Rs2Dialogue.sleepUntilInDialogue()) {
                        handleDialogue();
                    }
                }
            }
        } else {
            log.debug("Failed to use noted planks, trying Exchange-All directly");
            if (Rs2Npc.interact(NpcID.PHIALS, "Exchange-All")) {
                if (Rs2Dialogue.sleepUntilInDialogue()) {
                    handleDialogue();
                }
            }
        }
        
        sleepUntil(() -> Rs2Inventory.count(unnotedPlankId()) >= 24 || Rs2Inventory.count(noted) == 0, 4000);
    }

    private void handleDialogue() {
        log.debug("Handling Phials dialogue");
        
        // Look for "Exchange All: X coins" option immediately
        if (Rs2Dialogue.hasSelectAnOption()) {
            // Click any option that starts with "Exchange All:" (using partial match)
            if (Rs2Dialogue.clickOption("Exchange All:", false)) {
                log.debug("Selected 'Exchange All:' option");
                // Exchange happens immediately after clicking, no continue needed
            } else {
                log.warn("Could not find 'Exchange All:' option in dialogue");
            }
        }
    }

    private void enterHouse() {
        // Safety check: ensure we're actually outside the house
        if (isInHouse()) {
            log.warn("Already inside house, transitioning to BUILD");
            state = ConstructionPhialsState.BUILD;
            return;
        }
        
        TileObject portal = Rs2GameObject.findObjectById(RIMM_PORTAL);
        if (portal == null) {
            log.warn("Cannot find Rimmington portal");
            return;
        }
        
        log.debug("Entering house in build mode");
        if (Rs2GameObject.interact(portal, "Build mode")) {
            Rs2Player.waitForAnimation(1200);
            sleepUntil(this::isInHouse, 3000);
            state = ConstructionPhialsState.BUILD;
            log.info("Entered house, transitioning to BUILD");
            return;
        }
        
        // Walk closer if interaction failed
        Rs2Walker.walkFastLocal(portal.getLocalLocation());
    }

    private void antibanTick() {
        if (cfg.antiban() == ConstructionPhialsConfig.AntibanProfile.DISABLED) {
            state = ConstructionPhialsState.BUILD;
            return;
        }
        
        log.debug("Performing anti-ban actions");
        
        if (cfg.antiban() == ConstructionPhialsConfig.AntibanProfile.SIMPLE) {
            sleep(Rs2Random.between(600, 1400));
        } else {
            sleep(Rs2Random.between(1500, 3000));
            Rs2Tab.switchToInventoryTab();
        }
        
        int angle = (Rs2Camera.getAngle() + Rs2Random.between(30, 90)) % 360;
        Rs2Camera.setAngle(angle, 50);
        
        state = ConstructionPhialsState.BUILD;
        log.debug("Anti-ban complete, returning to BUILD");
    }

    private int unnotedPlankId() {
        return cfg.furniture() == ConstructionPhialsConfig.FurnitureType.WOODEN_LARDER
                ? ItemID.PLANK : ItemID.OAK_PLANK;
    }

    private int notedPlankId() {
        return cfg.furniture() == ConstructionPhialsConfig.FurnitureType.WOODEN_LARDER
                ? PLANK_NOTED : OAK_PLANK_NOTED;
    }
}
