package net.runelite.client.plugins.microbot.pert.constructionphials;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("constructionphials")
public interface ConstructionPhialsConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "Requirements:\n" +
                "• House in Rimmington\n" +
                "• Inventory: Saw, Hammer, Nails, Coins, Noted planks\n" +
                "• Start location: Inside your house OR outside near portal\n\n" +
                "The bot will automatically build/remove furniture, exit when low on planks,\n" +
                "unnote planks with Phials, and re-enter the house to continue.";
    }

    enum FurnitureType {
        WOODEN_LARDER,
        OAK_LARDER,
        OAK_DUNGEON_DOOR
    }

    enum AntibanProfile {
        SIMPLE,
        AGGRESSIVE,
        DISABLED
    }

    @ConfigItem(
            keyName = "furniture",
            name = "Furniture to build",
            description = "Which hotspot the script should build.",
            position = 1
    )
    default FurnitureType furniture() {
        return FurnitureType.OAK_DUNGEON_DOOR;
    }

    @ConfigItem(
            keyName = "antiban",
            name = "Anti-ban profile",
            description = "How aggressively to randomise actions.",
            position = 2
    )
    default AntibanProfile antiban() {
        return AntibanProfile.SIMPLE;
    }

    @ConfigItem(
            keyName = "breakHandler",
            name = "Use break handler",
            description = "Take long breaks at random intervals.",
            position = 3
    )
    default boolean breakHandler() {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug logging",
            description = "Enable detailed logging for troubleshooting",
            position = 4
    )
    default boolean debug() {
        return false;
    }
}
