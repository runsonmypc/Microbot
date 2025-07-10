package net.runelite.client.plugins.microbot.constructionphials;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("constructionphials")
public interface ConstructionPhialsConfig extends Config {

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
            description = "Which hotspot the script should build."
    )
    default FurnitureType furniture() {
        return FurnitureType.OAK_DUNGEON_DOOR;
    }

    @ConfigItem(
            keyName = "antiban",
            name = "Anti-ban profile",
            description = "How aggressively to randomise actions."
    )
    default AntibanProfile antiban() {
        return AntibanProfile.SIMPLE;
    }

    @ConfigItem(
            keyName = "breakHandler",
            name = "Use break handler",
            description = "Take long breaks at random intervals."
    )
    default boolean breakHandler() {
        return true;
    }
}
