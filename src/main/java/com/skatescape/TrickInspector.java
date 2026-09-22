package com.skatescape;

import java.awt.event.KeyEvent;
import net.runelite.api.Animation;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;

/**
 * Trick Inspector runtime/controller.
 *
 * The inspector intentionally does NOT own a second copy of SkateScape's trick
 * engine. It owns the developer-tool lifecycle, arrow-key browsing, selected
 * trick navigation and live tuning signature, then asks SkateScapePlugin to
 * freeze the real shared PRE / MAIN / SEAM / RETURN timeline at one cycle.
 *
 * The inspector must only show states produced by the real trick engine.
 */
final class TrickInspector {
    private static final int FIRST_TRICK_SLOT = 1;
    private static final int LAST_TRICK_SLOT = 5;
    private static final int FALLBACK_MAX_CYCLE = 500;

    private final SkateScapeConfig config;
    private final ConfigManager configManager;
    private final SkateScapePlugin plugin;

    private volatile int pendingCycleDelta;
    private volatile int pendingTrickDelta;

    private boolean previousEnabled;
    private int tuningSignature = Integer.MIN_VALUE;

    TrickInspector(
            SkateScapeConfig config,
            ConfigManager configManager,
            SkateScapePlugin plugin) {
        this.config = config;
        this.configManager = configManager;
        this.plugin = plugin;
    }

    void handleTrickBrowseKeyPressed(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.VK_RIGHT:
                pendingCycleDelta++;
                event.consume();
                break;

            case KeyEvent.VK_LEFT:
                pendingCycleDelta--;
                event.consume();
                break;

            case KeyEvent.VK_DOWN:
                pendingTrickDelta++;
                event.consume();
                break;

            case KeyEvent.VK_UP:
                pendingTrickDelta--;
                event.consume();
                break;

            default:
                break;
        }
    }

    /**
     * @return true while Trick Inspector owns this ClientTick and normal
     *         skating/trick handling must stop for the tick.
     */
    boolean handleClientTick(Player player) {
        if (config.trickInspectorEnabled()) {
            if (!previousEnabled) {
                plugin.enterTrickInspectorMode(player);
                initialize(player);
            }

            previousEnabled = true;
            applyState(player);
            return true;
        }

        if (previousEnabled) {
            plugin.exitTrickInspectorMode(player);
            previousEnabled = false;
            pendingCycleDelta = 0;
            pendingTrickDelta = 0;
        }

        return false;
    }

    int getLiveInspectorMaxCycle() {
        if (!config.trickInspectorEnabled()
                || !plugin.isTrickActiveForInspector()
                || plugin.getTrickTotalCyclesForInspector() <= 0
                || plugin.getActiveTrickSlotForInspector()
                        != config.inspectorSelectedTrick().getSlot()) {

            return FALLBACK_MAX_CYCLE;
        }

        return Math.max(
                0,
                plugin.getTrickTotalCyclesForInspector() - 1
        );
    }

    void invalidateTuningSignature() {
        tuningSignature = Integer.MIN_VALUE;
    }

    void resetState() {
        pendingCycleDelta = 0;
        pendingTrickDelta = 0;
        previousEnabled = false;
        tuningSignature = Integer.MIN_VALUE;
    }

    private TrickDefinition getSelectedDefinition() {
        return TrickRegistry.forEditorSlot(
                config.inspectorSelectedTrick().getSlot()
        );
    }

    private SkateScapeConfig.TrickSelection getSelectionForSlot(
            int slot) {
        final int safeSlot =
                clampInt(
                        slot,
                        FIRST_TRICK_SLOT,
                        LAST_TRICK_SLOT
                );

        for (SkateScapeConfig.TrickSelection selection
                : SkateScapeConfig.TrickSelection.values()) {

            if (selection.getSlot() == safeSlot) {
                return selection;
            }
        }

        return SkateScapeConfig.TrickSelection.KICKFLIP;
    }

    private void handleTrickBrowseRequest() {
        final int trickDelta = pendingTrickDelta;
        pendingTrickDelta = 0;

        if (trickDelta == 0) {
            return;
        }

        final int currentSlot =
                config.inspectorSelectedTrick().getSlot();

        final int trickCount =
                LAST_TRICK_SLOT - FIRST_TRICK_SLOT + 1;

        final int normalizedDelta =
                trickDelta % trickCount;

        final int nextSlot =
                ((currentSlot - FIRST_TRICK_SLOT + normalizedDelta)
                        % trickCount + trickCount)
                        % trickCount + FIRST_TRICK_SLOT;

        if (nextSlot == currentSlot) {
            return;
        }

        final SkateScapeConfig.TrickSelection nextSelection =
                getSelectionForSlot(nextSlot);

        configManager.setConfiguration(
                "skatescape",
                "inspectorSelectedTrickV132",
                nextSelection.name()
        );

        /*
         * Pull the newly selected trick's remembered inspector position into
         * the shared visible field immediately. The active timeline is then
         * reinitialized below in this same ClientTick.
         */
        plugin.syncInspectorEditorForTrickInspector();
        invalidateTuningSignature();
        plugin.refreshConfigPanelForTrickInspector();
    }

    private void initialize(Player player) {
        final TrickDefinition definition =
                getSelectedDefinition();

        if (definition == null) {
            return;
        }

        final Animation animation =
                plugin.loadTrickPlayerAnimationForInspector();

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            return;
        }

        plugin.prepareTrickTimelineForInspector(
                animation,
                definition
        );

        tuningSignature =
                plugin.getInspectorTuningSignature(
                        definition.getSlot()
                );

        if (!plugin.isTrickInspectorTimelineReady()) {
            return;
        }

        plugin.activateTrickInspectorTimeline(
                player,
                definition
        );
    }

    private void applyState(Player player) {
        handleTrickBrowseRequest();

        final TrickDefinition selectedDefinition =
                getSelectedDefinition();

        if (plugin.trickInspectorNeedsInitialization(
                selectedDefinition)) {

            initialize(player);

            if (!plugin.isTrickActiveForInspector()
                    || plugin.getTrickTotalCyclesForInspector() <= 0) {
                return;
            }
        }

        refreshTuningIfNeeded();

        final int maxCycle =
                Math.max(
                        0,
                        plugin.getTrickTotalCyclesForInspector() - 1
                );

        handleCycleBrowseRequest(maxCycle);

        final int inspectorCycle =
                clampInt(
                        config.trickInspectorCycle(),
                        0,
                        maxCycle
                );

        plugin.applyTrickInspectorCycle(
                player,
                inspectorCycle
        );
    }

    private void refreshTuningIfNeeded() {
        final TrickDefinition definition =
                getSelectedDefinition();

        if (definition == null) {
            return;
        }

        final int currentSignature =
                plugin.getInspectorTuningSignature(
                        definition.getSlot()
                );

        if (currentSignature == tuningSignature) {
            return;
        }

        final Animation animation =
                plugin.loadTrickPlayerAnimationForInspector();

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            return;
        }

        /*
         * Trick Tuning remains live while Inspector is open. Rebuild the same
         * shared board/player clock while preserving the selected full-timeline
         * cycle while rebuilding from the shared live tuning state.
         */
        plugin.prepareTrickTimelineForInspector(
                animation,
                definition
        );

        plugin.setActiveTrickForInspector(definition);
        tuningSignature = currentSignature;
    }

    private void handleCycleBrowseRequest(int maxCycle) {
        final int cycleDelta = pendingCycleDelta;
        pendingCycleDelta = 0;

        final int configuredCycle =
                config.trickInspectorCycle();

        final int currentCycle =
                clampInt(
                        configuredCycle,
                        0,
                        maxCycle
                );

        final int nextCycle =
                clampInt(
                        currentCycle + cycleDelta,
                        0,
                        maxCycle
                );

        if (nextCycle != configuredCycle) {
            configManager.setConfiguration(
                    "skatescape",
                    "trickInspectorCycleV132",
                    nextCycle
            );

            plugin.refreshConfigPanelForTrickInspector();
        }
    }

    private static int clampInt(
            int value,
            int minimum,
            int maximum) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}
