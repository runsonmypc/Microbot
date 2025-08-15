package net.runelite.client.plugins.microbot.farmingcontract.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.farmingcontract.FarmingContractConfig;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Manages all banking operations for farming contracts.
 * Consolidates tool, seed, and coin management.
 */
@Slf4j
public class BankingManager {
    
    private final FarmingContractConfig config;
    
    public BankingManager(FarmingContractConfig config) {
        this.config = config;
    }
    
    /**
     * Banking result containing what was prepared.
     */
    public static class BankingResult {
        public final boolean success;
        public final boolean hasTools;
        public final boolean hasSeeds;
        public final boolean needsDowngrade;
        public final String message;
        
        private BankingResult(boolean success, boolean hasTools, boolean hasSeeds, 
                             boolean needsDowngrade, String message) {
            this.success = success;
            this.hasTools = hasTools;
            this.hasSeeds = hasSeeds;
            this.needsDowngrade = needsDowngrade;
            this.message = message;
        }
        
        public static BankingResult success(boolean hasTools, boolean hasSeeds) {
            return new BankingResult(true, hasTools, hasSeeds, false, "Banking successful");
        }
        
        public static BankingResult needsDowngrade(String message) {
            return new BankingResult(false, false, false, true, message);
        }
        
        public static BankingResult failure(String message) {
            return new BankingResult(false, false, false, false, message);
        }
    }
    
    /**
     * Prepare for planting a contract.
     * Gets tools and seeds needed.
     */
    public BankingResult prepareForPlanting(Produce contract) {
        log.info("Preparing to plant {}", contract.getName());
        
        if (!openBankSafely()) {
            return BankingResult.failure("Could not open bank");
        }
        
        // Deposit everything except tools and coins (for trees)
        depositUnnecessaryItems(contract);
        
        // Withdraw tools
        boolean hasTools = withdrawPlantingTools();
        
        // Withdraw seeds
        int seedId = FarmingContractData.getSeedId(contract);
        if (seedId == -1) {
            log.error("No seed mapping for {}", contract.getName());
            Rs2Bank.closeBank();
            return BankingResult.failure("Unknown seed type");
        }
        
        int seedsNeeded = getSeedsRequired(contract);
        boolean hasSeeds = withdrawSeeds(seedId, seedsNeeded);
        
        if (!hasSeeds) {
            // Try to open seed packs
            if (openSeedPacks(seedId)) {
                hasSeeds = withdrawSeeds(seedId, seedsNeeded);
            }
        }
        
        if (!hasSeeds && config.autoDowngrade()) {
            log.info("No seeds available, need to downgrade contract");
            Rs2Bank.closeBank();
            return BankingResult.needsDowngrade("No seeds for " + contract.getName());
        }
        
        // Withdraw compost if configured
        if (config.useCompost() && hasSeeds) {
            withdrawCompost();
        }
        
        // Withdraw coins for trees
        if (needsCoinsForContract(contract)) {
            withdrawCoins();
        }
        
        Rs2Bank.closeBank();
        return BankingResult.success(hasTools, hasSeeds);
    }
    
    /**
     * Prepare for harvesting or checking a contract.
     */
    public BankingResult prepareForHarvesting(Produce contract, boolean needsClearing) {
        log.info("Preparing to harvest {}", contract.getName());
        
        if (!openBankSafely()) {
            return BankingResult.failure("Could not open bank");
        }
        
        // Deposit everything except tools
        depositUnnecessaryItems(contract);
        
        // Always need spade for harvesting
        boolean hasSpade = withdrawSpade();
        
        // Get magic secateurs for herbs
        if (contract.getPatchImplementation() == PatchImplementation.HERB) {
            withdrawMagicSecateurs();
        }
        
        // If we need to clear after (bushes, cacti), ensure we have spade
        if (needsClearing && !hasSpade) {
            log.warn("Need spade for clearing after harvest");
        }
        
        // Withdraw coins for trees if checking
        if (needsCoinsForContract(contract)) {
            withdrawCoins();
        }
        
        Rs2Bank.closeBank();
        return BankingResult.success(hasSpade, false);
    }
    
    /**
     * Prepare for clearing dead crops.
     */
    public BankingResult prepareForClearing(Produce contract) {
        log.info("Preparing to clear dead {}", contract.getName());
        
        if (!openBankSafely()) {
            return BankingResult.failure("Could not open bank");
        }
        
        depositUnnecessaryItems(contract);
        
        // Need all tools for clearing and replanting
        boolean hasTools = withdrawPlantingTools();
        
        // Also get seeds for replanting
        int seedId = FarmingContractData.getSeedId(contract);
        int seedsNeeded = getSeedsRequired(contract);
        boolean hasSeeds = withdrawSeeds(seedId, seedsNeeded);
        
        if (!hasSeeds) {
            openSeedPacks(seedId);
            hasSeeds = withdrawSeeds(seedId, seedsNeeded);
        }
        
        if (config.useCompost() && hasSeeds) {
            withdrawCompost();
        }
        
        Rs2Bank.closeBank();
        return BankingResult.success(hasTools, hasSeeds);
    }
    
    /**
     * Prepare for curing diseased crops.
     */
    public BankingResult prepareForCuring() {
        log.info("Preparing to cure diseased crop");
        
        if (!openBankSafely()) {
            return BankingResult.failure("Could not open bank");
        }
        
        depositUnnecessaryItems(null);
        
        boolean hasPlantCure = false;
        if (Rs2Bank.hasBankItem(FarmingContractData.Tools.PLANT_CURE, 1)) {
            Rs2Bank.withdrawX(FarmingContractData.Tools.PLANT_CURE, 1);
            hasPlantCure = true;
            log.info("Withdrew plant cure");
        } else {
            log.warn("No plant cure available in bank");
        }
        
        Rs2Bank.closeBank();
        return BankingResult.success(false, hasPlantCure);
    }
    
    // Helper methods
    
    private boolean openBankSafely() {
        if (!Rs2Bank.isOpen()) {
            // Walk to bank if we're not near one
            if (!Rs2Bank.isNearBank(10)) {
                log.info("Walking to nearest bank");
                Rs2Bank.walkToBank();
                sleepUntil(() -> Rs2Bank.isNearBank(10), 15000);
            }
            
            // Try to open the bank
            Rs2Bank.openBank();
            
            // Wait for bank to open
            if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
                log.error("Failed to open bank - timed out");
                return false;
            }
        }
        return Rs2Bank.isOpen();
    }
    
    private void depositUnnecessaryItems(Produce contract) {
        boolean needsCoins = contract != null && needsCoinsForContract(contract);
        
        if (needsCoins) {
            Rs2Bank.depositAllExcept(
                FarmingContractData.Tools.RAKE,
                FarmingContractData.Tools.SPADE,
                FarmingContractData.Tools.SEED_DIBBER,
                FarmingContractData.Tools.MAGIC_SECATEURS,
                FarmingContractData.Tools.COINS,
                FarmingContractData.Tools.PLANT_CURE
            );
        } else {
            Rs2Bank.depositAllExcept(
                FarmingContractData.Tools.RAKE,
                FarmingContractData.Tools.SPADE,
                FarmingContractData.Tools.SEED_DIBBER,
                FarmingContractData.Tools.MAGIC_SECATEURS,
                FarmingContractData.Tools.PLANT_CURE
            );
        }
    }
    
    private boolean withdrawPlantingTools() {
        boolean hasAll = true;
        
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            if (Rs2Bank.hasBankItem(FarmingContractData.Tools.SPADE, 1)) {
                Rs2Bank.withdrawX(FarmingContractData.Tools.SPADE, 1);
                log.info("Withdrew spade");
            } else {
                log.warn("No spade in bank");
                hasAll = false;
            }
        }
        
        if (!Rs2Inventory.contains(FarmingContractData.Tools.RAKE)) {
            if (Rs2Bank.hasBankItem(FarmingContractData.Tools.RAKE, 1)) {
                Rs2Bank.withdrawX(FarmingContractData.Tools.RAKE, 1);
                log.info("Withdrew rake");
            } else {
                log.warn("No rake in bank");
                hasAll = false;
            }
        }
        
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            if (Rs2Bank.hasBankItem(FarmingContractData.Tools.SEED_DIBBER, 1)) {
                Rs2Bank.withdrawX(FarmingContractData.Tools.SEED_DIBBER, 1);
                log.info("Withdrew seed dibber");
            } else {
                log.warn("No seed dibber in bank");
                hasAll = false;
            }
        }
        
        return hasAll;
    }
    
    private boolean withdrawSpade() {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            if (Rs2Bank.hasBankItem(FarmingContractData.Tools.SPADE, 1)) {
                Rs2Bank.withdrawX(FarmingContractData.Tools.SPADE, 1);
                log.info("Withdrew spade");
                return true;
            } else {
                log.warn("No spade in bank");
                return false;
            }
        }
        return true;
    }
    
    private void withdrawMagicSecateurs() {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.MAGIC_SECATEURS)) {
            if (Rs2Bank.hasBankItem(FarmingContractData.Tools.MAGIC_SECATEURS, 1)) {
                Rs2Bank.withdrawX(FarmingContractData.Tools.MAGIC_SECATEURS, 1);
                log.info("Withdrew magic secateurs for better yield");
            } else {
                log.warn("No magic secateurs - will harvest with reduced yield");
            }
        }
    }
    
    private boolean withdrawSeeds(int seedId, int amount) {
        if (Rs2Inventory.contains(seedId)) {
            int currentCount = Rs2Inventory.count(seedId);
            log.info("Already have {} seeds in inventory (need {})", currentCount, amount);
            if (currentCount >= amount) {
                return true;
            }
            // Need more
            amount = amount - currentCount;
            log.info("Need {} more seeds", amount);
        }
        
        log.info("Checking bank for seed ID {} (need {} seeds)", seedId, amount);
        if (Rs2Bank.hasBankItem(seedId, amount)) {
            Rs2Bank.withdrawX(seedId, amount);
            log.info("Withdrew {} seeds (ID: {})", amount, seedId);
            return true;
        }
        
        log.warn("Insufficient seeds in bank (need {}, ID: {})", amount, seedId);
        return false;
    }
    
    private boolean openSeedPacks(int seedId) {
        int packId = FarmingContractData.getSeedPackId(seedId);
        if (packId == -1) {
            return false;
        }
        
        // Check if we have seed packs
        if (Rs2Bank.hasBankItem(String.valueOf(packId))) {
            log.info("Opening seed packs (ID: {})", packId);
            
            // Withdraw all packs
            Rs2Bank.withdrawAll(String.valueOf(packId));
            Rs2Bank.closeBank();
            
            // Open them
            sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
            while (Rs2Inventory.contains(packId)) {
                Rs2Inventory.interact(packId, "Open");
                sleepUntil(() -> !Rs2Inventory.contains(packId), 2000);
            }
            
            // Reopen bank
            return openBankSafely();
        }
        
        return false;
    }
    
    private void withdrawCompost() {
        String compostName = config.compostType().toString();
        if (!Rs2Inventory.contains(compostName)) {
            if (Rs2Bank.hasBankItem(compostName, 1)) {
                Rs2Bank.withdrawX(compostName, 1);
                log.info("Withdrew {}", compostName);
            } else {
                log.warn("No {} available", compostName);
            }
        }
    }
    
    private void withdrawCoins() {
        if (Rs2Inventory.count(FarmingContractData.Tools.COINS) < FarmingContractData.Constants.TREE_CLEARING_COST) {
            Rs2Bank.withdrawX(FarmingContractData.Tools.COINS, FarmingContractData.Constants.TREE_CLEARING_COST);
            log.info("Withdrew {} coins for tree clearing", FarmingContractData.Constants.TREE_CLEARING_COST);
        }
    }
    
    private boolean needsCoinsForContract(Produce contract) {
        return contract.getPatchImplementation() == PatchImplementation.TREE ||
               contract.getPatchImplementation() == PatchImplementation.FRUIT_TREE;
    }
    
    private int getSeedsRequired(Produce contract) {
        if (contract.getPatchImplementation() == PatchImplementation.ALLOTMENT) {
            return FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED;
        }
        return FarmingContractData.Constants.DEFAULT_SEEDS_REQUIRED;
    }
}