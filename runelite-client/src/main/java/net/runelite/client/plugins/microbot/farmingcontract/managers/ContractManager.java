package net.runelite.client.plugins.microbot.farmingcontract.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages farming contract acquisition, parsing, and completion.
 * Replaces scattered contract logic from main script.
 */
@Slf4j
public class ContractManager {
    
    // Pattern from timetracking plugin - handles both dialogue variants
    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
        "(?:We need you to grow|Please could you grow) (?:some|a|an) ([a-zA-Z ]+)(?: for us\\?|\\.)"
    );
    
    private Produce currentContract;
    private boolean contractJustCompleted = false;
    
    /**
     * Parse contract from Jane's dialogue.
     * @return Produce if contract found, null otherwise
     */
    public Produce parseContract() {
        String dialogue = Rs2Dialogue.getDialogueText();
        if (dialogue == null || dialogue.isEmpty()) {
            return null;
        }
        
        Matcher matcher = CONTRACT_PATTERN.matcher(dialogue);
        if (matcher.find()) {
            String cropName = matcher.group(1).trim();
            log.info("Found contract in dialogue: {}", cropName);
            
            Produce produce = getProduceByName(cropName);
            if (produce != null) {
                currentContract = produce;
                log.info("Successfully parsed contract: {}", produce.getName());
                return produce;
            } else {
                log.error("Could not find Produce for crop name: {}", cropName);
            }
        }
        
        return null;
    }
    
    /**
     * Get Produce by contract name using reflection (temporary until API provides access).
     * This should be replaced when Produce.getByContractName becomes public.
     */
    private Produce getProduceByName(String name) {
        try {
            // Use reflection to access package-private method
            // This is temporary until the API is updated
            Method method = Produce.class.getDeclaredMethod("getByContractName", String.class);
            method.setAccessible(true);
            return (Produce) method.invoke(null, name);
        } catch (Exception e) {
            log.error("Failed to get produce by name using reflection: {}", name, e);
            
            // Fallback: Try to match by enum name
            String enumName = name.toUpperCase().replace(" ", "_");
            try {
                return Produce.valueOf(enumName);
            } catch (IllegalArgumentException ex) {
                log.error("Failed to match produce by enum name: {}", enumName);
            }
        }
        return null;
    }
    
    /**
     * Get contract tier based on farming level.
     * @return "Easy", "Medium", or "Hard"
     */
    public String getContractTier() {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        
        if (farmingLevel >= FarmingContractData.Constants.HARD_TIER_MIN_LEVEL) {
            return "Hard";
        } else if (farmingLevel >= FarmingContractData.Constants.MEDIUM_TIER_MIN_LEVEL) {
            return "Medium";
        } else if (farmingLevel >= FarmingContractData.Constants.EASY_TIER_MIN_LEVEL) {
            return "Easy";
        }
        
        log.warn("Farming level {} is too low for contracts (minimum 45)", farmingLevel);
        return "Easy"; // Default to easy
    }
    
    /**
     * Request a specific contract tier from Jane.
     * @param tier "Easy", "Medium", or "Hard"
     */
    public void requestContract(String tier) {
        log.info("Requesting {} contract from Jane", tier);
        
        // Navigate dialogue based on tier
        switch (tier.toLowerCase()) {
            case "easy":
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.clickOption("Easy");
                break;
            case "medium":
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.clickOption("Medium");
                break;
            case "hard":
                Rs2Dialogue.clickContinue();
                Rs2Dialogue.clickOption("Hard");
                break;
            default:
                log.warn("Unknown contract tier: {}", tier);
                Rs2Dialogue.clickOption("Easy"); // Default to easy
        }
    }
    
    /**
     * Request an easier contract tier.
     * @param currentTier Current tier to downgrade from
     * @return New tier name, or null if can't downgrade
     */
    public String requestEasierContract(String currentTier) {
        switch (currentTier.toLowerCase()) {
            case "hard":
                log.info("Downgrading from Hard to Medium contract");
                requestContract("Medium");
                return "Medium";
            case "medium":
                log.info("Downgrading from Medium to Easy contract");
                requestContract("Easy");
                return "Easy";
            case "easy":
                log.warn("Already at Easy tier, cannot downgrade further");
                return null;
            default:
                log.error("Unknown tier for downgrade: {}", currentTier);
                return null;
        }
    }
    
    /**
     * Mark contract as completed.
     * Called when chat message indicates completion.
     */
    public void markCompleted() {
        log.info("Contract marked as completed: {}", 
                currentContract != null ? currentContract.getName() : "None");
        contractJustCompleted = true;
    }
    
    /**
     * Clear contract after turning in.
     */
    public void clearContract() {
        log.info("Clearing current contract");
        currentContract = null;
        contractJustCompleted = false;
    }
    
    // Getters and state checks
    
    public Produce getCurrentContract() {
        return currentContract;
    }
    
    public void setCurrentContract(Produce contract) {
        this.currentContract = contract;
    }
    
    public boolean hasContract() {
        return currentContract != null;
    }
    
    public boolean isContractJustCompleted() {
        return contractJustCompleted;
    }
    
    public void resetCompletionFlag() {
        contractJustCompleted = false;
    }
    
    /**
     * Get seed ID for current contract.
     * @return Seed/sapling ID, or -1 if no contract or mapping
     */
    public int getCurrentContractSeedId() {
        if (currentContract == null) {
            return -1;
        }
        return FarmingContractData.getSeedId(currentContract);
    }
    
    /**
     * Get number of seeds required for current contract.
     * @return Number of seeds needed
     */
    public int getSeedsRequired() {
        if (currentContract == null) {
            return 0;
        }
        
        // Allotments need 3 seeds
        if (currentContract.getPatchImplementation() == 
            net.runelite.client.plugins.timetracking.farming.PatchImplementation.ALLOTMENT) {
            return FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED;
        }
        
        return FarmingContractData.Constants.DEFAULT_SEEDS_REQUIRED;
    }
}