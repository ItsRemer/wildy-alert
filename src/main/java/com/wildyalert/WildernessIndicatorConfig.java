package com.wildyalert;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("wildindicator")
public interface WildernessIndicatorConfig extends Config {

    enum FlashType {
        CUSTOM,
        FLASH_UNTIL_CANCELLED,
    }

    @ConfigItem(
            position = 0,
            keyName = "enableRuneliteNotifications",
            name = "Enable RuneLite Notifications",
            description = "Enable notifications from RuneLite when a player can attack you"
    )
    default boolean enableRuneliteNotifications() {
        return true;
    }

    @ConfigItem(
            position = 1,
            keyName = "flashType",
            name = "Flash Type",
            description = "Configure how the screen should flash when alerted"
    )
    default FlashType flashType() {
        return FlashType.CUSTOM;
    }

    @ConfigItem(
            position = 2,
            keyName = "alertColor",
            name = "Alert Color",
            description = "Color of the alert when a player can attack you"
    )
    @Alpha
    default Color alertColor() {
        return new Color(255, 0, 0, 125);
    }

    @ConfigItem(
            position = 3,
            keyName = "flashCount",
            name = "Alert Flash Count",
            description = "The amount of times the alert should flash for."
    )
    @Range(
            min = 1,
            max = 10
    )
    default int customFlashCount() {
        return 3;
    }
}
