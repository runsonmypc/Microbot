package net.runelite.client.plugins.microbot.farmingcontract.data;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.util.Map;

/**
 * Central data repository for farming contract mappings.
 * Replaces hardcoded switch statements with efficient map lookups.
 */
public class FarmingContractData {
    
    /**
     * Mapping of produce to their corresponding seed/sapling item IDs.
     * Used for withdrawing correct items from bank.
     */
    public static final Map<Produce, Integer> SEED_MAPPINGS = ImmutableMap.<Produce, Integer>builder()
        // Allotments
        .put(Produce.POTATO, ItemID.POTATO_SEED)
        .put(Produce.ONION, ItemID.ONION_SEED)
        .put(Produce.CABBAGE, ItemID.CABBAGE_SEED)
        .put(Produce.TOMATO, ItemID.TOMATO_SEED)
        .put(Produce.SWEETCORN, ItemID.SWEETCORN_SEED)
        .put(Produce.STRAWBERRY, ItemID.STRAWBERRY_SEED)
        .put(Produce.WATERMELON, ItemID.WATERMELON_SEED)
        .put(Produce.SNAPE_GRASS, ItemID.SNAPE_GRASS_SEED)
        
        // Flowers
        .put(Produce.MARIGOLD, ItemID.MARIGOLD_SEED)
        .put(Produce.ROSEMARY, ItemID.ROSEMARY_SEED)
        .put(Produce.NASTURTIUM, ItemID.NASTURTIUM_SEED)
        .put(Produce.WOAD, ItemID.WOAD_SEED)
        .put(Produce.LIMPWURT, ItemID.LIMPWURT_SEED)
        .put(Produce.WHITE_LILY, ItemID.WHITE_LILY_SEED)
        
        // Herbs
        .put(Produce.GUAM, ItemID.GUAM_SEED)
        .put(Produce.MARRENTILL, ItemID.MARRENTILL_SEED)
        .put(Produce.TARROMIN, ItemID.TARROMIN_SEED)
        .put(Produce.HARRALANDER, ItemID.HARRALANDER_SEED)
        .put(Produce.RANARR, ItemID.RANARR_SEED)
        .put(Produce.TOADFLAX, ItemID.TOADFLAX_SEED)
        .put(Produce.IRIT, ItemID.IRIT_SEED)
        .put(Produce.AVANTOE, ItemID.AVANTOE_SEED)
        .put(Produce.KWUARM, ItemID.KWUARM_SEED)
        .put(Produce.SNAPDRAGON, ItemID.SNAPDRAGON_SEED)
        .put(Produce.CADANTINE, ItemID.CADANTINE_SEED)
        .put(Produce.LANTADYME, ItemID.LANTADYME_SEED)
        .put(Produce.DWARF_WEED, ItemID.DWARF_WEED_SEED)
        .put(Produce.TORSTOL, ItemID.TORSTOL_SEED)
        
        // Trees - use saplings (in plantpots)
        .put(Produce.OAK, ItemID.PLANTPOT_OAK_SAPLING)
        .put(Produce.WILLOW, ItemID.PLANTPOT_WILLOW_SAPLING)
        .put(Produce.MAPLE, ItemID.PLANTPOT_MAPLE_SAPLING)
        .put(Produce.YEW, ItemID.PLANTPOT_YEW_SAPLING)
        .put(Produce.MAGIC, ItemID.PLANTPOT_MAGIC_TREE_SAPLING)
        
        // Fruit trees - use saplings (in plantpots)
        .put(Produce.APPLE, ItemID.PLANTPOT_APPLE_SAPLING)
        .put(Produce.BANANA, ItemID.PLANTPOT_BANANA_SAPLING)
        .put(Produce.ORANGE, ItemID.PLANTPOT_ORANGE_SAPLING)
        .put(Produce.CURRY, ItemID.PLANTPOT_CURRY_SAPLING)
        .put(Produce.PINEAPPLE, ItemID.PLANTPOT_PINEAPPLE_SAPLING)
        .put(Produce.PAPAYA, ItemID.PLANTPOT_PAPAYA_SAPLING)
        .put(Produce.PALM, ItemID.PLANTPOT_PALM_SAPLING)
        .put(Produce.DRAGONFRUIT, ItemID.PLANTPOT_DRAGONFRUIT_SAPLING)
        
        // Bushes
        .put(Produce.REDBERRIES, ItemID.REDBERRY_BUSH_SEED)
        .put(Produce.CADAVABERRIES, ItemID.CADAVABERRY_BUSH_SEED)
        .put(Produce.DWELLBERRIES, ItemID.DWELLBERRY_BUSH_SEED)
        .put(Produce.JANGERBERRIES, ItemID.JANGERBERRY_BUSH_SEED)
        .put(Produce.WHITEBERRIES, ItemID.WHITEBERRY_BUSH_SEED)
        .put(Produce.POISON_IVY, ItemID.POISONIVY_BUSH_SEED)
        
        // Cactus
        .put(Produce.CACTUS, ItemID.CACTUS_SEED)
        .put(Produce.POTATO_CACTUS, ItemID.POTATO_CACTUS_SEED)
        .build();
    
    /**
     * Mapping of seeds to their corresponding seed packs.
     * Used for opening seed packs when seeds are not available.
     * Note: Currently empty as seed pack ItemIDs are not available in the API.
     * This can be populated when the IDs become available.
     */
    public static final Map<Integer, Integer> SEED_PACK_MAPPINGS = ImmutableMap.<Integer, Integer>builder()
        // TODO: Add seed pack mappings when ItemIDs become available
        // For now, seed pack functionality is disabled
        .build();
    
    /**
     * Tool item names used in farming.
     */
    public static final class Tools {
        public static final String RAKE = "Rake";
        public static final String SPADE = "Spade";
        public static final String SEED_DIBBER = "Seed dibber";
        public static final String MAGIC_SECATEURS = "Magic secateurs";
        public static final String PLANT_CURE = "Plant cure";
        public static final String COINS = "Coins";
        
        // Tool item IDs
        public static final int RAKE_ID = ItemID.RAKE;
        public static final int SPADE_ID = ItemID.SPADE;
        public static final int SEED_DIBBER_ID = 5343; // ItemID.SEED_DIBBER not available
        public static final int MAGIC_SECATEURS_ID = 7409; // ItemID.MAGIC_SECATEURS not available
        public static final int PLANT_CURE_ID = ItemID.PLANT_CURE;
    }
    
    /**
     * Constants for farming operations.
     */
    public static final class Constants {
        public static final int TREE_CLEARING_COST = 200;
        public static final int ALLOTMENT_SEEDS_REQUIRED = 3;
        public static final int DEFAULT_SEEDS_REQUIRED = 1;
        
        // Farming levels for contract tiers
        public static final int EASY_TIER_MIN_LEVEL = 45;
        public static final int MEDIUM_TIER_MIN_LEVEL = 65;
        public static final int HARD_TIER_MIN_LEVEL = 85;
    }
    
    /**
     * Get seed/sapling ID for the given produce.
     * @param produce The produce to get seed for
     * @return Seed/sapling item ID, or -1 if not found
     */
    public static int getSeedId(Produce produce) {
        return SEED_MAPPINGS.getOrDefault(produce, -1);
    }
    
    /**
     * Get seed pack ID for the given seed.
     * @param seedId The seed item ID
     * @return Seed pack item ID, or -1 if no pack exists
     */
    public static int getSeedPackId(int seedId) {
        return SEED_PACK_MAPPINGS.getOrDefault(seedId, -1);
    }
    
    /**
     * Check if a seed has a corresponding seed pack.
     * @param seedId The seed item ID
     * @return true if seed pack exists
     */
    public static boolean hasSeedPack(int seedId) {
        return SEED_PACK_MAPPINGS.containsKey(seedId);
    }
    
    /**
     * Get patch location for a produce.
     */
    public static PatchLocation getPatchLocation(Produce produce) {
        if (produce == null) {
            return null;
        }
        
        // For now, return a simple mapping based on patch type
        // In a real implementation, this would map to specific patch locations
        // Create a simple 3x3 polygon around the patch location for now
        java.awt.Polygon defaultPoly = new java.awt.Polygon(
            new int[]{-1, 1, 1, -1},
            new int[]{-1, -1, 1, 1},
            4
        );
        
        switch (produce.getPatchImplementation()) {
            case HERB:
                return PatchLocation.of("Farming Guild", PatchImplementation.HERB, 
                    new WorldPoint(1239, 3728, 0), defaultPoly);
            case ALLOTMENT:
                return PatchLocation.of("Farming Guild", PatchImplementation.ALLOTMENT,
                    new WorldPoint(1265, 3729, 0), defaultPoly);
            case FLOWER:
                return PatchLocation.of("Farming Guild", PatchImplementation.FLOWER,
                    new WorldPoint(1260, 3725, 0), defaultPoly);
            case BUSH:
                return PatchLocation.of("Farming Guild", PatchImplementation.BUSH,
                    new WorldPoint(1260, 3733, 0), defaultPoly);
            case CACTUS:
                return PatchLocation.of("Farming Guild", PatchImplementation.CACTUS,
                    new WorldPoint(1264, 3747, 0), defaultPoly);
            case TREE:
                return PatchLocation.of("Farming Guild", PatchImplementation.TREE,
                    new WorldPoint(1232, 3736, 0), defaultPoly);
            case FRUIT_TREE:
                return PatchLocation.of("Farming Guild", PatchImplementation.FRUIT_TREE,
                    new WorldPoint(1242, 3757, 0), defaultPoly);
            default:
                return null;
        }
    }
}