package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpotList;

@ConfigGroup("MotherloadMine")
@ConfigInformation("<b>🛠️ Motherlode Mine Bot</b><br/><br/>" +
        "<b>Setup:</b><br/>" +
        "• Have a pickaxe equipped or in inventory<br/>" +
        "• Start near the bank chest or deposit box<br/>" +
        "• (Optional) Have a hammer for waterwheel repairs<br/>" +
        "• (Optional) Have a gem bag to collect gems<br/><br/>" +
        "<b>Features:</b><br/>" +
        "• Mines pay-dirt from ore veins<br/>" +
        "• Deposits pay-dirt in hopper<br/>" +
        "• Collects ores from sack<br/>" +
        "• Banks ores using chest or deposit box<br/>" +
        "• Repairs waterwheel<br/>" +
        "• Supports upper and lower floor mining<br/>" +
        "• Anti-ban with activity intensity adjustments<br/>")
public interface MotherloadMineConfig extends Config {

    @ConfigItem(
            keyName = "PickAxeInInventory",
            name = "Pick Axe In Inventory?",
            description = "Keep pickaxe in inventory instead of equipped",
            position = 0
    )
    default boolean pickAxeInInventory() {
        return false;
    }

    @ConfigItem(
            keyName = "MineUpstairs",
            name = "Mine Upstairs?",
            description = "Mine on the upper floor (must be unlocked first)",
            position = 1
    )
    default boolean mineUpstairs() {
        return false;
    }

    @ConfigItem(
            keyName = "UpstairsHopperUnlocked",
            name = "Upstairs Hopper Unlocked?",
            description = "Have you unlocked the upstairs hopper?",
            position = 2
    )
    default boolean upstairsHopperUnlocked() {
        return false;
    }

    @ConfigItem(
            keyName = "miningArea",
            name = "Mining Area",
            description = "Choose the specific area to mine in",
            position = 3
    )
    default MLMMiningSpotList miningArea() {
        return MLMMiningSpotList.ANY;
    }

    @ConfigItem(
            keyName = "repairWaterwheel",
            name = "Repair Waterwheel?",
            description = "Repair broken struts (requires hammer)",
            position = 4
    )
    default boolean repairWaterwheel() {
        return true;
    }

    @ConfigItem(
            keyName = "useDepositBox",
            name = "Use Deposit Box?",
            description = "Use deposit box instead of bank chest for faster ore depositing",
            position = 5
    )
    default boolean useDepositBox() {
        return false;
    }

    @ConfigItem(
            keyName = "avoidPlayers",
            name = "Avoid Players (Lower Floor)",
            description = "Avoid mining veins near other players on lower floor (not needed but helps avoid attention)",
            position = 6
    )
    default boolean avoidPlayers() {
        return false;
    }
}
