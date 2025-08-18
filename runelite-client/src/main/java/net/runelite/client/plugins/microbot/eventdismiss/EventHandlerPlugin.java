package net.runelite.client.plugins.microbot.eventdismiss;

import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Event Handler</html>",
        description = "Handles random events - dismiss or accept rewards",
        tags = {"random", "events", "microbot", "lamp"},
        enabledByDefault = false
)
@Slf4j
public class EventHandlerPlugin extends Plugin {
    @Inject
    private ConfigManager configManager;
    @Inject
    private EventHandlerConfig config;

    private HandleNpcEvent handleNpcEvent;
    private UseLampEvent useLampEvent;

    @Provides
    EventHandlerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventHandlerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        handleNpcEvent = new HandleNpcEvent(config);
        useLampEvent = new UseLampEvent(config);
        Microbot.getBlockingEventManager().add(handleNpcEvent);
        Microbot.getBlockingEventManager().add(useLampEvent);
    }

    @Override
    protected void shutDown() {
        Microbot.getBlockingEventManager().remove(handleNpcEvent);
        Microbot.getBlockingEventManager().remove(useLampEvent);
        handleNpcEvent = null;
        useLampEvent = null;
    }
}
