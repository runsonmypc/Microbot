package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.Skill;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("EventHandler")
public interface EventHandlerConfig extends Config {

    @ConfigSection(
            name = "Lamp Events",
            description = "Settings for lamp-giving random events",
            position = 0
    )
    String lampSection = "lampEvents";

    @ConfigItem(
            name = "Genie",
            keyName = "genieAction",
            position = 0,
            section = lampSection,
            description = "Accept or dismiss Genie random event"
    )
    default EventAction genieAction() {
        return EventAction.ACCEPT;
    }

    @ConfigItem(
            name = "Count Check",
            keyName = "countCheckAction",
            position = 1,
            section = lampSection,
            description = "Accept or dismiss Count Check random event. Note: Requires bank PIN and authenticator enabled to receive lamp"
    )
    default EventAction countCheckAction() {
        return EventAction.ACCEPT;
    }

    @ConfigItem(
            name = "Lamp Skill",
            keyName = "lampSkill",
            position = 2,
            section = lampSection,
            description = "Skill to use experience lamps on"
    )
    default Skill lampSkill() {
        return Skill.HERBLORE;
    }

    @ConfigItem(
            name = "Check for Lamps",
            keyName = "checkForLamps",
            position = 3,
            section = lampSection,
            description = "Periodically check inventory for lamps and use them. Not needed for random events (Genie/Count Check handle automatically), but useful for testing and other lamp sources"
    )
    default boolean checkForLamps() {
        return false;
    }

    @ConfigSection(
            name = "Other Events",
            description = "Settings for other random events",
            position = 1
    )
    String otherSection = "otherEvents";

    @ConfigItem(
            name = "Beekeeper Dismiss",
            keyName = "dismissBeekeeper",
            position = 0,
            section = otherSection,
            description = "Dismiss Beekeeper random event"
    )
    default boolean dismissBeekeeper() {
        return true;
    }

    @ConfigItem(
            name = "Capt' Arnav Dismiss",
            keyName = "dismissCaptArnav",
            position = 1,
            section = otherSection,
            description = "Dismiss Capt' Arnav random event"
    )
    default boolean dismissArnav() {
        return true;
    }

    @ConfigItem(
            name = "Certers Dismiss",
            keyName = "dismissCerters",
            position = 2,
            section = otherSection,
            description = "Dismiss Giles, Miles, and Niles Certer random events"
    )
    default boolean dismissCerters() {
        return true;
    }

    @ConfigItem(
            name = "Drill Demon Dismiss",
            keyName = "dismissDrillDemon",
            position = 3,
            section = otherSection,
            description = "Dismiss Drill Demon random event"
    )
    default boolean dismissDrillDemon() {
        return true;
    }

    @ConfigItem(
            name = "Drunken Dwarf Dismiss",
            keyName = "dismissDrunkenDwarf",
            position = 4,
            section = otherSection,
            description = "Dismiss Drunken Dwarf random event"
    )
    default boolean dismissDrunkenDwarf() {
        return true;
    }

    @ConfigItem(
            name = "Evil Bob Dismiss",
            keyName = "dismissEvilBob",
            position = 5,
            section = otherSection,
            description = "Dismiss Evil Bob random event"
    )
    default boolean dismissEvilBob() {
        return true;
    }

    @ConfigItem(
            name = "Evil Twin Dismiss",
            keyName = "dismissEvilTwin",
            position = 6,
            section = otherSection,
            description = "Dismiss Evil Twin random event"
    )
    default boolean dismissEvilTwin() {
        return true;
    }

    @ConfigItem(
            name = "Freaky Forester Dismiss",
            keyName = "dismissFreakyForester",
            position = 7,
            section = otherSection,
            description = "Dismiss Freaky Forester random event"
    )
    default boolean dismissFreakyForester() {
        return true;
    }

    @ConfigItem(
            name = "Gravedigger Dismiss",
            keyName = "dismissGravedigger",
            position = 8,
            section = otherSection,
            description = "Dismiss Gravedigger random event"
    )
    default boolean dismissGravedigger() {
        return true;
    }

    @ConfigItem(
            name = "Jekyll and Hyde Dismiss",
            keyName = "dismissJekyllAndHyde",
            position = 9,
            section = otherSection,
            description = "Dismiss Jekyll and Hyde random events"
    )
    default boolean dismissJekyllAndHyde() {
        return true;
    }

    @ConfigItem(
            name = "Kiss the Frog Dismiss",
            keyName = "dismissKissTheFrog",
            position = 10,
            section = otherSection,
            description = "Dismiss Kiss the Frog random event"
    )
    default boolean dismissKissTheFrog() {
        return true;
    }

    @ConfigItem(
            name = "Mysterious Old Man Dismiss",
            keyName = "dismissMysteriousOldMan",
            position = 11,
            section = otherSection,
            description = "Dismiss Mysterious Old Man random event"
    )
    default boolean dismissMysteriousOldMan() {
        return true;
    }

    @ConfigItem(
            name = "Pillory Dismiss",
            keyName = "dismissPillory",
            position = 12,
            section = otherSection,
            description = "Dismiss Pillory random event"
    )
    default boolean dismissPillory() {
        return true;
    }

    @ConfigItem(
            name = "Pinball Dismiss",
            keyName = "dismissPinball",
            position = 13,
            section = otherSection,
            description = "Dismiss Pinball random events"
    )
    default boolean dismissPinball() {
        return true;
    }

    @ConfigItem(
            name = "Quiz Master Dismiss",
            keyName = "dismissQuizMaster",
            position = 14,
            section = otherSection,
            description = "Dismiss Quiz Master random event"
    )
    default boolean dismissQuizMaster() {
        return true;
    }

    @ConfigItem(
            name = "Rick Turpentine Dismiss",
            keyName = "dismissRickTurpentine",
            position = 15,
            section = otherSection,
            description = "Dismiss Rick Turpentine random event"
    )
    default boolean dismissRickTurpentine() {
        return true;
    }

    @ConfigItem(
            name = "Sandwich Lady Dismiss",
            keyName = "dismissSandwichLady",
            position = 16,
            section = otherSection,
            description = "Dismiss Sandwich Lady random event"
    )
    default boolean dismissSandwichLady() {
        return true;
    }

    @ConfigItem(
            name = "Strange Plant Dismiss",
            keyName = "dismissStrangePlant",
            position = 17,
            section = otherSection,
            description = "Dismiss Strange Plant random event"
    )
    default boolean dismissStrangePlant() {
        return true;
    }

    @ConfigItem(
            name = "Surprise Exam Dismiss",
            keyName = "dismissSurpriseExam",
            position = 18,
            section = otherSection,
            description = "Dismiss Surprise Exam random event"
    )
    default boolean dismissSurpriseExam() {
        return true;
    }

}
