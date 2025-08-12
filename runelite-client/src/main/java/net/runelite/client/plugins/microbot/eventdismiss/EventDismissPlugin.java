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
public class EventDismissPlugin extends Plugin {
    @Inject
    private ConfigManager configManager;
    @Inject
    private EventDismissConfig config;

    private DismissNpcEvent dismissNpcEvent;
    private UseLampEvent useLampEvent;

    @Provides
    EventDismissConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EventDismissConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        dismissNpcEvent = new DismissNpcEvent(config);
        useLampEvent = new UseLampEvent(config);
        Microbot.getBlockingEventManager().add(dismissNpcEvent);
        Microbot.getBlockingEventManager().add(useLampEvent);
    }

    protected void shutDown() {
        Microbot.getBlockingEventManager().remove(dismissNpcEvent);
        Microbot.getBlockingEventManager().remove(useLampEvent);
        dismissNpcEvent = null;
        useLampEvent = null;
    }
}
