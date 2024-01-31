package com.wildyalert;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
        name = "Wilderness Alerts",
        description = "Indicates when a player can attack you in the wilderness",
        tags = {"wilderness", "pvp", "combat", "indicator"},
        enabledByDefault = false
)
public class WildernessPlugin extends Plugin {
    @Inject private Client client;
    @Inject private WildernessOverlay warningOverlay;
    @Inject private OverlayManager overlayManager;
    @Inject private WildernessIndicatorConfig config;

    @Inject
    private Notifier notifier;

    private ScheduledExecutorService alertExecutor = Executors.newSingleThreadScheduledExecutor();
    private boolean isFlashing = false;
    private AtomicInteger counter = new AtomicInteger();
    private ScheduledFuture<?> flashFuture;

    private int numberOfAlertsTriggered = 0;
    private Set<String> encounteredPlayers = new HashSet<>();
    private Map<String, Long> lastEncounteredTimes = new ConcurrentHashMap<>();
    private final long encounterTimeout = TimeUnit.MINUTES.toMillis(10);
    private static final int MINIMUM_FLASH_DURATION_TICKS = 20;
    private static final int MIN_FLASH_COUNT_BEFORE_CANCEL = 2;

    @Provides
    WildernessIndicatorConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(WildernessIndicatorConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(warningOverlay);
        if (alertExecutor.isShutdown() || alertExecutor.isTerminated()) {
            alertExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        setupEncounterCleanupTask();
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(warningOverlay);
        alertExecutor.shutdownNow();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!isInWilderness()) {
            resetAlerts();
            return;
        }
        checkNearbyPlayers();
    }

    private boolean isInWilderness() {
        return client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
    }

    private void checkNearbyPlayers() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return;

        int playerCombatLevel = localPlayer.getCombatLevel();
        int wildernessLevel = getWildernessLevel();

        client.getPlayers().stream()
                .filter(player -> player != null && !player.equals(localPlayer))
                .filter(player -> canAttack(playerCombatLevel, player.getCombatLevel(), wildernessLevel))
                .forEach(this::handleAttackablePlayer);
    }

    private void setupEncounterCleanupTask() {
        long cleanUpInterval = TimeUnit.MINUTES.toMillis(5);
        if (!alertExecutor.isShutdown() && !alertExecutor.isTerminated()) {
            alertExecutor.scheduleAtFixedRate(() -> {
                long currentTime = System.currentTimeMillis();
                lastEncounteredTimes.entrySet().removeIf(entry -> currentTime - entry.getValue() > encounterTimeout);
            }, cleanUpInterval, cleanUpInterval, TimeUnit.MILLISECONDS);
        }
    }

    private int getWildernessLevel() {
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        return calculateWildernessLevel(location.getX(), location.getY());
    }

    private int calculateWildernessLevel(int x, int y) {
        if (x >= 2944 && x <= 3392) {
            if (y >= 3520 && y <= 3967) return ((y - 3520) / 8) + 1;
            if (y >= 9918 && y <= 10366) return ((y - 9918) / 8) + 1;
        }
        return 0;
    }

    private boolean canAttack(int playerLevel, int otherPlayerLevel, int wildernessLevel) {
        return Math.abs(playerLevel - otherPlayerLevel) <= wildernessLevel;
    }

    private void handleAttackablePlayer(Player player) {
        String playerName = player.getName();
        long currentTime = System.currentTimeMillis();

        if (!encounteredPlayers.contains(playerName) ||
                currentTime - lastEncounteredTimes.getOrDefault(playerName, 0L) > encounterTimeout) {
            String message = playerName + " can attack you! They are level " + player.getCombatLevel() +
                    " and you are level " + client.getLocalPlayer().getCombatLevel() + ".";
            sendChatMessage(message);
            if (config.enableRuneliteNotifications()) {
                notifier.notify(message);
            }
            if (numberOfAlertsTriggered < config.customFlashCount()) {
                triggerAlert(config.flashType(), config.customFlashCount());
                numberOfAlertsTriggered++;
            }
            encounteredPlayers.add(playerName);
            lastEncounteredTimes.put(playerName, currentTime);
        }
    }

    private void sendChatMessage(String message) {
        String chatMessage = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(message)
                .build();

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chatMessage, null);
    }

    private void triggerAlert(WildernessIndicatorConfig.FlashType flashType, int customFlashCount) {
        if (isFlashing) {
            return;
        }

        isFlashing = true;
        counter.set(0);
        warningOverlay.setShowAlert(true);

        if (alertExecutor.isShutdown()) {
            return;
        }

        flashFuture = alertExecutor.scheduleAtFixedRate(() -> {
            performFlashing(flashType, customFlashCount);
        }, 0, 300, TimeUnit.MILLISECONDS);
    }

    private void performFlashing(WildernessIndicatorConfig.FlashType flashType, int customFlashCount) {
        if (flashType == WildernessIndicatorConfig.FlashType.CUSTOM) {
            handleCustomFlashing(customFlashCount);
        } else if (flashType == WildernessIndicatorConfig.FlashType.FLASH_UNTIL_CANCELLED) {
            handleFlashUntilCancelled();
        }
    }

    private void handleCustomFlashing(int customFlashCount) {
        if (counter.get() >= customFlashCount * 2) {
            stopFlashing();
        } else {
            toggleFlash();
        }
    }

    private void handleFlashUntilCancelled() {
        warningOverlay.setShowAlert(!warningOverlay.isShowAlert());
        counter.incrementAndGet();
        if (counter.get() >= MIN_FLASH_COUNT_BEFORE_CANCEL * 2 &&
                (client.getMouseIdleTicks() < MINIMUM_FLASH_DURATION_TICKS ||
                        client.getKeyboardIdleTicks() < MINIMUM_FLASH_DURATION_TICKS)) {
            stopFlashing();
        }
    }

    private void toggleFlash() {
        warningOverlay.setShowAlert(!warningOverlay.isShowAlert());
        counter.incrementAndGet();
    }

    private void stopFlashing() {
        if (flashFuture != null) {
            flashFuture.cancel(false);
            flashFuture = null; // Reset the future to allow new flashing sequences
        }
        warningOverlay.setShowAlert(false);
        isFlashing = false;
        System.out.println("Flashing stopped."); // Debugging log
    }

    private void resetAlerts() {
        numberOfAlertsTriggered = 0;
        encounteredPlayers.clear();
    }

    public void shutdown() {
        alertExecutor.shutdown();
    }
}
