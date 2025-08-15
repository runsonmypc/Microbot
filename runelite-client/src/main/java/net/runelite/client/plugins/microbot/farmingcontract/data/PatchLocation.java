package net.runelite.client.plugins.microbot.farmingcontract.data;

import com.google.common.collect.ImmutableMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;

import java.awt.Polygon;
import java.util.Map;

/**
 * Model class representing a farming patch location in the Farming Guild.
 * Encapsulates patch coordinates, type, and detection logic.
 */
public class PatchLocation {
    
    private final String name;
    private final PatchImplementation type;
    private final WorldPoint location;
    private final Polygon area;
    
    /**
     * Private constructor - use factory methods or constants.
     */
    private PatchLocation(String name, PatchImplementation type, WorldPoint location, Polygon area) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.area = area;
    }
    
    /**
     * Create patch location with single point.
     */
    public static PatchLocation of(String name, PatchImplementation type, WorldPoint location) {
        return new PatchLocation(name, type, location, null);
    }
    
    /**
     * Create patch location with area polygon.
     */
    public static PatchLocation of(String name, PatchImplementation type, WorldPoint location, Polygon area) {
        return new PatchLocation(name, type, location, area);
    }
    
    /**
     * All patch locations in the Farming Guild.
     */
    public static final class FarmingGuildPatches {
        // Herb patch - Southeast area
        public static final PatchLocation HERB = PatchLocation.of(
            "Herb", 
            PatchImplementation.HERB,
            new WorldPoint(1269, 3726, 0)
        );
        
        // Allotment patches
        public static final PatchLocation ALLOTMENT_NORTH = PatchLocation.of(
            "North Allotment",
            PatchImplementation.ALLOTMENT,
            new WorldPoint(1265, 3737, 0)
        );
        
        public static final PatchLocation ALLOTMENT_SOUTH = PatchLocation.of(
            "South Allotment",
            PatchImplementation.ALLOTMENT,
            new WorldPoint(1265, 3724, 0)
        );
        
        // Flower patch
        public static final PatchLocation FLOWER = PatchLocation.of(
            "Flower",
            PatchImplementation.FLOWER,
            new WorldPoint(1260, 3731, 0)
        );
        
        // Bush patch - West side
        public static final PatchLocation BUSH = PatchLocation.of(
            "Bush",
            PatchImplementation.BUSH,
            new WorldPoint(1249, 3718, 0)
        );
        
        // Tree patch - Northwest corner
        public static final PatchLocation TREE = PatchLocation.of(
            "Tree",
            PatchImplementation.TREE,
            new WorldPoint(1231, 3736, 0)
        );
        
        // Fruit tree patch - West side
        public static final PatchLocation FRUIT_TREE = PatchLocation.of(
            "Fruit Tree",
            PatchImplementation.FRUIT_TREE,
            new WorldPoint(1240, 3717, 0)
        );
        
        // Cactus patch - North area with polygon area
        public static final PatchLocation CACTUS = PatchLocation.of(
            "Cactus",
            PatchImplementation.CACTUS,
            new WorldPoint(1264, 3747, 0),
            new Polygon(
                new int[]{1264, 1265, 1265, 1264},
                new int[]{3747, 3747, 3748, 3748},
                4
            )
        );
        
        // Map for quick lookup by patch type
        private static final Map<PatchImplementation, PatchLocation> TYPE_MAP = 
            ImmutableMap.<PatchImplementation, PatchLocation>builder()
                .put(PatchImplementation.HERB, HERB)
                .put(PatchImplementation.FLOWER, FLOWER)
                .put(PatchImplementation.BUSH, BUSH)
                .put(PatchImplementation.TREE, TREE)
                .put(PatchImplementation.FRUIT_TREE, FRUIT_TREE)
                .put(PatchImplementation.CACTUS, CACTUS)
                .build();
        
        /**
         * Get patch location by type.
         * Note: Returns only first match for types with multiple patches (e.g., allotments).
         */
        public static PatchLocation getByType(PatchImplementation type) {
            if (type == PatchImplementation.ALLOTMENT) {
                // Default to north for allotments when not specified
                return ALLOTMENT_NORTH;
            }
            return TYPE_MAP.get(type);
        }
        
        /**
         * Get allotment patch by name.
         */
        public static PatchLocation getAllotment(String name) {
            if (name != null && name.toLowerCase().contains("south")) {
                return ALLOTMENT_SOUTH;
            }
            return ALLOTMENT_NORTH;
        }
    }
    
    /**
     * Check if a world point is within this patch's area.
     */
    public boolean contains(WorldPoint point) {
        if (area != null) {
            return area.contains(point.getX(), point.getY());
        }
        return location.equals(point);
    }
    
    /**
     * Check if a world point is near this patch (within distance).
     */
    public boolean isNear(WorldPoint point, int distance) {
        return location.distanceTo(point) <= distance;
    }
    
    // Getters
    public String getName() {
        return name;
    }
    
    public PatchImplementation getType() {
        return type;
    }
    
    public WorldPoint getLocation() {
        return location;
    }
    
    public Polygon getArea() {
        return area;
    }
    
    @Override
    public String toString() {
        return String.format("PatchLocation[%s, %s, %s]", name, type, location);
    }
}