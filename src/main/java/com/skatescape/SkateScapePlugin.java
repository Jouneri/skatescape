package com.skatescape;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "SkateScape"
)
public class SkateScapePlugin extends Plugin {
    private static final int PLANK_ITEM_ID = ItemID.WOODPLANK;

    /*
     * Normal skating idle loops 1708/10..17.
     *
     * When movement stops, 1708/7..9 plays at native speed as a short
     * root/pose handoff into frame 10. This avoids teleporting
     * directly from movement animation 3004 into the middle of 1708.
     */
    private static final int IDLE_TRANSITION_START_FRAME = 7;
    private static final int IDLE_START_FRAME = 10;
    private static final int IDLE_END_FRAME = 17;
    private static final int IDLE_SLOWDOWN = 3;

    private static final int WHEEL_ITEM_ID = ItemID.MCANNONBALL;
    private static final int WHEEL_SIZE_PERCENT = 20;
    private static final int WHEEL_FRONT_BACK_OFFSET = 40;
    private static final int WHEEL_LEFT_RIGHT_OFFSET = 15;
    private static final int WHEEL_HEIGHT = 0;

    private static final int IDLE_SKATE_ANIMATION = AnimationID.HW07_ARM_FROM_THE_GROUND_EMOTE;
    static final int MOVING_SKATE_ANIMATION = AnimationID.HUMAN_MARIONETTE_WALK;
    private static final int MOVING_SKATE_START_FRAME = 5;
    private static final int MOVING_SKATE_END_FRAME = 16;
    private static final int MOVING_SKATE_RUN_SPEED_MULTIPLIER = 2;
    private static final int MOVEMENT_GRACE_TICKS = 6;
    static final int MAX_SAFE_ANIMATION_ID = 14520;

    /*
     * Trick Inspector freezes the selected trick at one exact logical client
     * cycle. Player animation, board transform, seam and return all read the
     * same frozen trickElapsedCycles value.
     */

    /*
     * NATURAL PLAYBACK HANDOFF GUARD.
     *
     * Native RuneScape animations advance on their own frame clock.
     * The logical SkateScape phase clock can reach a boundary one render after
     * the outgoing action animation expires, briefly exposing the underlying
     * 3004 skating pose.
     *
     * Give only the final frame of each natural phase a few extra native
     * animation cycles. These cycles are deliberately not added to the logical
     * PRE / MAIN / seam / RETURN timing, so the trick does not become longer.
     * The next phase replaces the still-alive outgoing animation before the
     * guard is consumed.
     */
    private static final int NATURAL_PHASE_GUARD_CYCLES = 3;

    /*
     * SHARED TRICKS 1-3 CHOREOGRAPHY
     *
     * PRE-JUMP POSITION TRANSITION:
     *   1708 / 18 -> 19 -> 20 -> 21 -> 22 -> 23 -> 24 -> 25 -> 26 -> 27
     *
     *   Mike Hawk and the skateboard travel together from the normal
     *   skating anchor (-35/-20) to the tile-centred trick anchor (0/0).
     *
     * MAIN:
     *   1603 / 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 12
     *
     *   Frame 11 is not part of the configured pose sequence. The board stays
     *   centred at 0/0 throughout MAIN.
     *
     * LANDING SEAM:
     *   1708 / 27
     *
     *   A brief centred seam separates MAIN from the return.
     *
     * RETURN / BALANCE TRANSITION:
     *   1708 / 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9
     *
     *   Only after the seam do Mike Hawk and the skateboard travel together
     *   from 0/0 back toward the normal -35/-20 skating position.
     *
     * 1603 stays on the POSE layer. Normal SkateScape animation IDs are kept
     * raw by the interpolation filter; Animation Testing owns a separate
     * developer-only filter.
     */
    static final int TRICK_PLAYER_ANIMATION = AnimationID.HUMAN_JUMP_HURDLE;

    /*
     * Trick 4 has its own independent MAIN player choreography.
     * Do not fall back to the shared Tricks 1-3 1603 jump.
     */
    static final int TRICK4_DEFAULT_PLAYER_ANIMATION = AnimationID.DARK_SPEC_PLAYER;
    static final int[] TRICK4_DEFAULT_PLAYER_FRAMES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
            19, 20, 21, 22, 23
    };
    static final int DEFAULT_TRICK_POP_START_FRAME = 3;
    static final int DEFAULT_TRICK_FLIP_START_FRAME = 3;
    static final int DEFAULT_TRICK_FLIP_END_FRAME = 7;
    static final int DEFAULT_TRICK_POP_END_FRAME = 11;

    /*
     * Default 1603 pose choreography.
     *
     * Frame 11 is intentionally absent because its crossed-leg stance looks
     * wrong on the skateboard. The default pose path never selects that frame.
     */
    static final int[] DEFAULT_TRICK_JUMP_FRAMES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12
    };

    static final int TRICK_TRANSITION_ANIMATION = AnimationID.HW07_ARM_FROM_THE_GROUND_EMOTE;

    /*
     * PRE-JUMP:
     * normal skating position -> tile centre.
     */
    static final int[] DEFAULT_TRICK_PRE_FRAMES = {
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27
    };
    static final int[] DEFAULT_TRICK_PRE_DURATIONS_MS = {
            30, 20, 20, 20, 20, 20, 20, 30
    };

    static final int[] DEFAULT_TRICK_PRE_BOARD_POSITIONS = {
            0, 10, 20, 35, 50, 65, 80, 90, 97, 100
    };

    /*
     * LANDING SEAM:
     *
     * 1708/27 is the tile-centred seam between MAIN and the 1708 return
     * sequence. For Tricks 1-3, 1603/11 is absent from the configured pose
     * choreography because its crossed-leg stance looks wrong on the board.
     *
     * The board remains at 0/0 for this seam.
     */
    static final int DEFAULT_TRICK_POST_SEAM_FRAME = 27;
    static final int DEFAULT_TRICK_POST_SEAM_DURATION_MS = 60;

    /*
     * RETURN / BALANCE:
     * tile centre -> normal skating position.
     */
    static final int[] DEFAULT_TRICK_POST_FRAMES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9
    };
    static final int[] DEFAULT_TRICK_POST_DURATIONS_MS = {
            30, 30, 30, 75, 40, 50, 40
    };

    static final int[] DEFAULT_TRICK_POST_BOARD_POSITIONS = {
            100, 75, 50, 30, 15, 5, 0, 0, 0, 0
    };

    /*
     * TRICK 4 BOARD CHOREOGRAPHY:
     *
     * The developer Trick Tuning controls define Trick 4's board timeline:
     *
     *   Kickflip starts      -> beginning of the kickflip
     *   Kickflip ends        -> completion of the kickflip
     *   360 Shove-it starts  -> beginning of the late shove/body-varial phase
     *   Board catch          -> end of the late rotation / catch
     */


    /*
     * Terrain-follow sampling distances in local units.
     * 128 local units = one tile.
     */
    private static final int TERRAIN_SAMPLE_FORWARD = 48;
    private static final int TERRAIN_SAMPLE_SIDEWAYS = 24;
    private static final double MAX_TERRAIN_TILT_RADIANS =
            Math.toRadians(35.0);
    private static final double TERRAIN_ANGLE_QUANTIZATION = 1000.0;

    /*
     * TRICK 4 ACTION-LAYER PLAYBACK.
     *
     * Normal movement, idle, transitions and tricks stay raw. Trick 4's
     * prepared natural MAIN animation (2890) runs on the ACTION layer while
     * the root-safe 3004 pose remains underneath. Tricks 1-3 use 1603 on the
     * POSE layer, and Trick 5's mixed-animation MAIN also uses the POSE layer.
     */

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SkateScapeConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    /*
     * Animation Inspector runtime is isolated in its own subsystem.
     * The plugin owns the mode handoff because entering/leaving the inspector
     * changes normal skating/player state.
     */
    private AnimationInspector animationInspector;

    /*
     * Trick Inspector lifecycle, browsing and inspector-only state live in
     * their own controller. The shared trick timeline remains the single
     * source of truth for what the inspector freezes.
     */
    private TrickInspector trickInspector;

    /*
     * RuneLite config-panel live refresh, row choreography and spinner
     * guards live together in one controller.
     */
    private SkateScapeConfigPanelController configPanelController;

    /*
     * Normal skating movement and idle animation state live in one controller.
     * Shared prepared trick state remains here, while TrickRuntimeController
     * owns the live phase lifecycle and PlayerTrickAnimationController owns
     * player-side playback.
     */
    private SkatingMovementController skatingMovementController;

    /*
     * Live skateboard scene-object ownership, placement and terrain sampling
     * stay together in the scene controller instead of leaking scene state
     * throughout the trick engine.
     */
    private SkateboardSceneController skateboardSceneController;

    /*
     * Trick-specific skateboard anchor movement, pop/landing height,
     * rotation/pitch choreography and ground-clearance math live in one
     * dedicated transform controller. Timeline construction lives in
     * TrickTimelineBuilder while prepared runtime values stay shared here.
     */
    private SkateboardTrickTransformController skateboardTrickTransformController;
    private final SkateboardTrickTransformController.Context
            skateboardTrickTransformContext =
                    new SkateboardTrickTransformController.Context();

    boolean trickActive;
    TrickDefinition activeTrick;
    int trickStartGameCycle;
    int trickElapsedCycles;
    int trickTotalCycles;

    int trickPopStartCycleOffset;
    int trickPopEndCycleOffset;
    int trickFlipStartCycleOffset;
    int trickFlipEndCycleOffset;
    int trickCatchCycleOffset;

    int trickActivePopStartFrame =
            DEFAULT_TRICK_POP_START_FRAME;
    int trickActivePopEndFrame =
            DEFAULT_TRICK_POP_END_FRAME;
    int trickActiveFlipStartFrame =
            DEFAULT_TRICK_FLIP_START_FRAME;
    int trickActiveFlipEndFrame =
            DEFAULT_TRICK_FLIP_END_FRAME;

    int trickPreTransitionCycles;
    int[] trickPreFrameCycles;
    int[] trickActivePreFrames;
    int[] trickActivePreBoardPositions;

    /*
     * Keep board and player MAIN clocks explicit for per-trick choreography.
     *
     * trickBoardMainSequenceCycles = board/airtime window from Trick duration.
     * trickPlayerMainSequenceCycles = player sequence duration.
     * trickMainSequenceCycles = visible MAIN phase long enough for both.
     */
    int trickMainSequenceCycles;
    int trickBoardMainSequenceCycles;
    int trickPlayerMainSequenceCycles;
    int[] trickActiveJumpFrames;
    int[] trickActiveJumpFrameCycles;

    /*
     * Per-trick player choreography.
     *
     * Each trick owns its own editable pose sequence. The developer editor
     * switches between those saved per-trick values while runtime playback
     * reads the values belonging to the active trick.
     */
    int[] trickActivePlayerAnimationIds;
    int[] trickActivePlayerFrames;
    int[] trickActivePlayerPoseCycles;

    /*
     * PRE / MAIN / landing-seam / RETURN player playback state lives in
     * PlayerTrickAnimationController. The arrays above remain the prepared
     * timeline shared with board timing and Inspector.
     */
    private PlayerTrickAnimationController playerTrickAnimationController;
    private final PlayerTrickAnimationController.Context
            playerTrickAnimationContext =
                    new PlayerTrickAnimationController.Context();

    /*
     * TrickRuntimeController owns the live phase/combo lifecycle. Prepared
     * timeline data remains shared here for rendering and developer tools.
     */
    private TrickRuntimeController trickRuntimeController;

    /*
     * TrickTimelineBuilder constructs the shared prepared timeline used by the
     * runtime, player animation, skateboard, and inspector code.
     */
    private TrickTimelineBuilder trickTimelineBuilder;

    int trickPostSeamCycles;
    int trickActivePostSeamFrame =
            DEFAULT_TRICK_POST_SEAM_FRAME;

    int trickPostTransitionCycles;
    int[] trickPostFrameCycles;
    int[] trickActivePostFrames;
    int[] trickActivePostBoardPositions;

    double trickProgress;

    int[] trickFrameLengths;
    int trickPreviousPoseAnimation = -1;
    int trickPreviousPoseFrame;

    volatile int pendingTrickSlot;

    private boolean previousAnimationTestState;

    /* Trick Inspector rendering exists only in local developer builds. */
    private TrickInspectorOverlay trickInspectorOverlay;

    /* Package-private read-only bridge for TrickInspectorOverlay. */
    Client getClientForTrickInspectorOverlay() {
        return client;
    }

    SkateScapeConfig getConfigForTrickInspectorOverlay() {
        return config;
    }

    TrickDefinition getActiveTrickForTrickInspectorOverlay() {
        return activeTrick;
    }

    int getTrickElapsedCyclesForTrickInspectorOverlay() {
        return trickElapsedCycles;
    }

    int getTrickTotalCyclesForTrickInspectorOverlay() {
        return trickTotalCycles;
    }

    String getTrickTracePhaseForTrickInspectorOverlay() {
        return getTrickTracePhase();
    }

    /*
     * INPUT EXTRACTION.
     *
     * RuneLite key registration, developer inspector arrow browsing and the
     * five trick hotkeys now live in SkateScapeInputController. The one-slot
     * combo buffer itself deliberately remains authoritative in
     * TrickRuntimeController via pendingTrickSlot.
     */
    private SkateScapeInputController inputController;

    /*
     * Hidden per-trick backing values, editor synchronization, defaults and
     * ConfigChanged routing live in PerTrickConfigController.
     */
    private PerTrickConfigController perTrickConfigController;

    /*
     * Trick 5's skateboard is authored one player frame at a time.
     * Runtime playback interpolates smoothly between the nine authored frame
     * transforms using the live player-frame timing.
     */
    private Trick5BoardChoreographyController trick5BoardChoreographyController;

    private Trick5BoardChoreographyController trick5BoardChoreography() {
        if (trick5BoardChoreographyController == null) {
            trick5BoardChoreographyController =
                    new Trick5BoardChoreographyController(
                            config,
                            configManager,
                            this
                    );
        }

        return trick5BoardChoreographyController;
    }

    private PerTrickConfigController perTrickConfig() {
        if (perTrickConfigController == null) {
            perTrickConfigController =
                    new PerTrickConfigController(
                            config,
                            configManager,
                            this
                    );
        }

        return perTrickConfigController;
    }

    int normalizeShoveItDegrees(int degrees) {
        return perTrickConfig().normalizeShoveItDegrees(degrees);
    }

    int getKickflipDegrees() {
        return perTrickConfig().getKickflipDegrees();
    }

    int getTrick4KickflipDegrees() {
        return perTrickConfig().getTrick4KickflipDegrees();
    }

    int getTrick4ImpossibleDegrees() {
        return perTrickConfig().getTrick4ImpossibleDegrees();
    }

    int normalizeTrick5RotationDegrees(int degrees) {
        return perTrickConfig().normalizeTrick5RotationDegrees(degrees);
    }

    int getTrick5KickflipDegrees() {
        return perTrickConfig().getTrick5KickflipDegrees();
    }

    private double getTrick5ShoveStartPercent() {
        return perTrickConfig().getTrick5ShoveStartPercent();
    }

    private double getTrick5ShoveEndPercent() {
        return perTrickConfig().getTrick5ShoveEndPercent();
    }

    int getTrick5ShoveDegrees() {
        return perTrickConfig().getTrick5ShoveDegrees();
    }

    int getShoveItDegrees() {
        return perTrickConfig().getShoveItDegrees();
    }

    int getVarialKickflipDegrees() {
        return perTrickConfig().getVarialKickflipDegrees();
    }

    private double getVarialShoveStartPercent() {
        return perTrickConfig().getVarialShoveStartPercent();
    }

    private double getVarialShoveEndPercent() {
        return perTrickConfig().getVarialShoveEndPercent();
    }

    int getVarialShoveDegrees() {
        return perTrickConfig().getVarialShoveDegrees();
    }

    int maxPoseSequenceLength(int slot) {
        return perTrickConfig().maxPoseSequenceLength(slot);
    }

    boolean usesLegacyPlayerPose(int slot) {
        return perTrickConfig().usesLegacyPlayerPose(slot);
    }

    boolean usesApprovedLegacyBoardTiming(int slot) {
        return perTrickConfig().usesApprovedLegacyBoardTiming(slot);
    }

    void storeExactLegacyBoardCycles(int slot) {
        perTrickConfig().storeExactLegacyBoardCycles(slot);
    }

    int getTuningInt(
            int slot,
            String field) {

        return perTrickConfig().getTuningInt(slot, field);
    }

    double getTuningPercent(
            int slot,
            String field) {

        return perTrickConfig().getTuningPercent(slot, field);
    }

    int getQueuedExitCycle(int slot) {
        return perTrickConfig().getQueuedExitCycle(slot);
    }

    int getQueuedStartCycle(int slot) {
        return perTrickConfig().getQueuedStartCycle(slot);
    }

    int getPoseLength(int slot) {
        return perTrickConfig().getPoseLength(slot);
    }

    String getPoseAnimations(int slot) {
        return perTrickConfig().getPoseAnimations(slot);
    }

    String getPoseFrames(int slot) {
        return perTrickConfig().getPoseFrames(slot);
    }

    boolean getAdvancedPoseTiming(int slot) {
        return perTrickConfig().getAdvancedPoseTiming(slot);
    }

    String getPoseTimingMs(int slot) {
        return perTrickConfig().getPoseTimingMs(slot);
    }

    String getPoseTimingWarning(int slot) {
        return perTrickConfig().getPoseTimingWarning(slot);
    }

    private void syncInspectorEditorFromSelection() {
        perTrickConfig().syncInspectorEditorFromSelection();
    }

    /* RuneLite config-panel operations are delegated to their controller. */
    void refreshOpenRuneLiteConfigPanel() {
        if (configPanelController != null) {
            configPanelController.refreshOpenRuneLiteConfigPanel();
        }
    }

    void applyOpenRuneLiteConfigPanelFixes() {
        if (configPanelController != null) {
            configPanelController.applyOpenRuneLiteConfigPanelFixes();
        }
    }

    void suppressPerTrickEditorWrites() {
        if (configPanelController != null) {
            configPanelController.suppressPerTrickEditorWrites();
        }
    }

    void clearPerTrickEditorWriteSuppression() {
        if (configPanelController != null) {
            configPanelController.clearPerTrickEditorWriteSuppression();
        }
    }

    boolean isSuppressingPerTrickEditorWrites() {
        return configPanelController != null
                && configPanelController.isSuppressingPerTrickEditorWrites();
    }

    void invalidateTrickInspectorTuningSignature() {
        if (trickInspector != null) {
            trickInspector.invalidateTuningSignature();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!SkateScapeDeveloperMode.ENABLED) {
            return;
        }

        trick5BoardChoreography().onConfigChanged(event);
        perTrickConfig().onConfigChanged(event);
    }

    @Override
    public void resetConfiguration() {
        perTrickConfig().resetConfiguration();
        trick5BoardChoreography().reset();
    }

    @Override
    protected void startUp() {
        trick5BoardChoreography()
                .initialize();

        perTrickConfig()
                .initializeStorage();

        if (SkateScapeDeveloperMode.ENABLED) {
            configPanelController =
                    new SkateScapeConfigPanelController(
                            config,
                            this,
                            MAX_SAFE_ANIMATION_ID
                    );

            if (config.trickInspectorEnabled()
                    && config.animationTest()) {
                configManager.setConfiguration(
                        "skatescape",
                        "animationTest",
                        false
                );
            }

            animationInspector =
                    new AnimationInspector(
                            client,
                            config,
                            configManager,
                            this::refreshOpenRuneLiteConfigPanel,
                            this::applyOpenRuneLiteConfigPanelFixes,
                            MAX_SAFE_ANIMATION_ID
                    );

            trickInspector =
                    new TrickInspector(
                            config,
                            configManager,
                            this
                    );

            trickInspectorOverlay =
                    new TrickInspectorOverlay(this);

            overlayManager.add(trickInspectorOverlay);
            configPanelController.installListeners();

            /*
             * If the developer config page was already open when the plugin
             * started, refresh the existing Swing controls once.
             */
            refreshOpenRuneLiteConfigPanel();
        }

        skatingMovementController =
                new SkatingMovementController(
                        client,
                        IDLE_SKATE_ANIMATION,
                        MOVING_SKATE_ANIMATION,
                        IDLE_TRANSITION_START_FRAME,
                        IDLE_START_FRAME,
                        IDLE_END_FRAME,
                        IDLE_SLOWDOWN,
                        MOVING_SKATE_START_FRAME,
                        MOVING_SKATE_END_FRAME,
                        MOVING_SKATE_RUN_SPEED_MULTIPLIER,
                        MOVEMENT_GRACE_TICKS
                );

        playerTrickAnimationController =
                new PlayerTrickAnimationController(
                        client,
                        this::loadSafeAnimation,
                        TRICK_PLAYER_ANIMATION,
                        TRICK_TRANSITION_ANIMATION,
                        MOVING_SKATE_ANIMATION,
                        NATURAL_PHASE_GUARD_CYCLES
                );

        trickRuntimeController =
                new TrickRuntimeController(
                        client,
                        this
                );

        trickTimelineBuilder =
                new TrickTimelineBuilder(this);

        inputController =
                new SkateScapeInputController(
                        client,
                        config,
                        keyManager,
                        trickInspector,
                        animationInspector,
                        slot -> pendingTrickSlot = slot,
                        SkateScapeDeveloperMode.ENABLED
                );

        /*
         * Own a fresh scene controller for each enable cycle. Its RuneLiteObject
         * is explicitly deactivated and released during shutdown.
         */
        skateboardSceneController =
                new SkateboardSceneController(
                        client,
                        PLANK_ITEM_ID,
                        true,
                        true,
                        WHEEL_ITEM_ID,
                        WHEEL_SIZE_PERCENT,
                        WHEEL_FRONT_BACK_OFFSET,
                        WHEEL_LEFT_RIGHT_OFFSET,
                        WHEEL_HEIGHT,
                        TERRAIN_SAMPLE_FORWARD,
                        TERRAIN_SAMPLE_SIDEWAYS,
                        MAX_TERRAIN_TILT_RADIANS,
                        TERRAIN_ANGLE_QUANTIZATION
                );

        skateboardTrickTransformController =
                new SkateboardTrickTransformController(
                        skateboardSceneController
                );

        inputController.installListeners();

        pendingTrickSlot = 0;
        if (trickInspector != null) {
            trickInspector.resetState();
        }
        resetMovementTracking();
        resetTrickState();
        skatingMovementController.clearIdleState();
        log.debug("SkateScape started!");
    }

    @Override
    protected void shutDown() {
        if (trickInspectorOverlay != null) {
            overlayManager.remove(trickInspectorOverlay);
        }

        if (configPanelController != null) {
            configPanelController.uninstallListeners();
        }

        if (inputController != null) {
            inputController.uninstallListeners();
        }

        pendingTrickSlot = 0;
        if (trickInspector != null) {
            trickInspector.resetState();
        }

        /*
         * Plugin start/stop runs on Swing's EDT, while Player, Animation and
         * RuneLiteObject state belongs to the client thread. Capture this
         * enable-cycle's controllers before dropping the plugin references so
         * a rapid OFF -> ON cannot make the queued cleanup touch the new board
         * or newly enabled animation state.
         */
        scheduleClientStateCleanupForShutdown(
                skatingMovementController,
                playerTrickAnimationController,
                animationInspector,
                skateboardSceneController,
                trickActive,
                trickPreviousPoseAnimation,
                trickPreviousPoseFrame,
                previousAnimationTestState
        );

        if (skatingMovementController != null) {
            skatingMovementController.resetMovementTracking();
            skatingMovementController.clearIdleState();
        }

        if (trickRuntimeController != null) {
            trickRuntimeController.discardState();
        } else {
            trickActive = false;
            activeTrick = null;
            trickProgress = 0.0;
        }

        playerTrickAnimationContext.clear();
        skateboardTrickTransformContext.clear();
        previousAnimationTestState = false;

        /*
         * Drop runtime/controller references so a disabled plugin retains no
         * scene objects, Swing/listener owners, inspector state, or prepared
         * trick arrays. The queued client-thread cleanup above owns references
         * to the captured enable-cycle objects until it has restored them.
         */
        configPanelController = null;
        animationInspector = null;
        trickInspector = null;
        trickInspectorOverlay = null;
        inputController = null;
        skatingMovementController = null;
        playerTrickAnimationController = null;
        trickRuntimeController = null;
        trickTimelineBuilder = null;
        skateboardTrickTransformController = null;
        skateboardSceneController = null;
        perTrickConfigController = null;
        trick5BoardChoreographyController = null;

        log.debug("SkateScape stopped!");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) {
            final Player player = client.getLocalPlayer();

            if (player != null) {
                if (trickActive) {
                    restorePlayerAfterTrick(player);
                }

                restoreOriginalMovementAnimations(player);

                if (player.getAnimation() == IDLE_SKATE_ANIMATION) {
                    player.setAnimation(-1);
                }
            }

            restoreAnimationTestInterpolationFilter();
            restoreIdleAnimationTiming();
            removeBoard();
            resetMovementTracking();
            resetTrickState();
            pendingTrickSlot = 0;
            if (trickInspector != null) {
                trickInspector.resetState();
            }

            skatingMovementController.clearIdleState();
            skatingMovementController.forgetSavedOriginalMovementAnimations();
            previousAnimationTestState = false;
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        final Player player = client.getLocalPlayer();

        if (player == null) {
            removeBoard();
            return;
        }

        /*
         * Trick Inspector lifecycle/browsing lives in its own controller. It
         * still drives the exact shared trick timeline, so the frozen preview
         * is a real state the live trick can pass through.
         */
        if (trickInspector != null
                && trickInspector.handleClientTick(player)) {
            updateSkateboard(player);
            return;
        }

        /*
         * Movement tracking must continue even while a trick is active.
         * Calculate it once per ClientTick and share the result with both the
         * trick lifecycle and the normal movement/idle path.
         */
        final boolean moving =
                updateMovementState(player);

        trickRuntimeController.handleHotkeyRequest(player);
        trickRuntimeController.update(
                player,
                moving
        );

        updateSkateboard(player);

        if (trickActive) {
            /*
             * Keep SkateScape's movement fallback definitions reinforced while
             * a trick drives the visible pose. RuneScape can refresh an Actor's
             * animation-set definitions during a long trick; without this, its
             * centred default idle can briefly reappear when movement ends.
             *
             * These setters only change fallback definitions. They do not alter
             * the current trick pose animation or frame.
             */
            saveOriginalMovementAnimations(player);
            applySkatingMovementAnimations(player);
            return;
        }

        if (animationInspector != null
                && config.animationTest()) {
            restoreIdleAnimationTiming();
            restoreTrickInterpolationFilter();
            skatingMovementController.clearIdleState();

            if (!previousAnimationTestState) {
                restoreOriginalMovementAnimations(player);

                if (player.getAnimation() == IDLE_SKATE_ANIMATION) {
                    player.setAnimation(-1);
                }
            }

            previousAnimationTestState = true;

            animationInspector.syncFromConfig();
            animationInspector.handleBrowseRequest();
            animationInspector.update(player);
            return;
        }

        if (previousAnimationTestState) {
            restoreAnimationTestInterpolationFilter();

            player.setAnimation(-1);
            player.setPoseAnimation(player.getIdlePoseAnimation());
            player.setPoseAnimationFrame(0);

            if (animationInspector != null) {
                animationInspector.clearBrowseState();
            }

            resetMovementTracking();
            skatingMovementController.clearIdleState();
            previousAnimationTestState = false;
        }

        applyIdleAnimationTiming();

        saveOriginalMovementAnimations(player);
        applySkatingMovementAnimations(player);

        if (moving) {
            applyMovingState(player);
        } else {
            applyIdleState(player);
        }
    }

    /*
     * WALK -> IDLE HANDOFF.
     *
     * The stop handoff uses 1708/7..9 to avoid the root/pose discontinuity
     * caused by jumping directly from movement animation 3004 to 1708/10.
     *
     * Path:
     *
     *   movement 3004
     *       ->
     *   1708/7 -> 1708/8 -> 1708/9
     *       ->
     *   normal idle loop 1708/10..17
     *
     * Frames 7..9 keep native timing. Frames 10..17 use IDLE_SLOWDOWN.
     *
     * RuneScape's underlying idle fallback definition is proactively kept
     * on 3004 by applySkatingMovementAnimations(). The pose/action handling
     * below then performs SkateScape's visible 1708 handoff on top of that
     * root-safe fallback.
     *
     * This path does not alter player location, trick choreography or
     * skateboard position.
     */
    @Subscribe
    public void onPostClientTick(PostClientTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        final Player player = client.getLocalPlayer();

        if (player == null) {
            return;
        }

        final boolean animationTestActive =
                animationInspector != null
                        && config.animationTest();

        skatingMovementController.onPostClientTick(
                player,
                trickActive,
                animationTestActive,
                this::restoreTrickInterpolationFilter
        );

        /*
         * trick RETURN now uses the equipment-safe pose layer.
         * Reinforce only that RETURN after RuneScape has had its normal
         * chance to refresh the actor pose during this client tick. This
         * matches the PostClientTick ownership used by the normal walk ->
         * idle pose transition.
         */
        if (trickActive
                && !animationTestActive
                && playerTrickAnimationController != null) {

            syncPlayerTrickAnimationContext();
            playerTrickAnimationController
                    .reinforcePostReturnPose(player);
        }
    }

    private void applyIdleAnimationTiming() {
        skatingMovementController.applyIdleAnimationTiming();
    }

    void restoreIdleAnimationTiming() {
        skatingMovementController.restoreIdleAnimationTiming();
    }

    void saveOriginalMovementAnimations(Player player) {
        skatingMovementController.saveOriginalMovementAnimations(player);
    }

    void applySkatingMovementAnimations(Player player) {
        skatingMovementController.applySkatingMovementAnimations(player);
    }

    private void restoreOriginalMovementAnimations(Player player) {
        skatingMovementController.restoreOriginalMovementAnimations(player);
    }

    /*
     * TRICK INSPECTOR BRIDGE.
     *
     * TrickInspector owns inspector lifecycle, browsing and inspector-only
     * state. These bridges intentionally reuse the live trick timeline rather
     * than duplicating it. That keeps the inspector honest.
     */
    void enterTrickInspectorMode(Player player) {
        if (trickActive) {
            restorePlayerAfterTrick(player);
            resetTrickState();
        }

        restoreAnimationTestInterpolationFilter();
        restoreIdleAnimationTiming();
        restoreTrickInterpolationFilter();
        skatingMovementController.clearIdleState();
        pendingTrickSlot = 0;
    }

    Animation loadTrickPlayerAnimationForInspector() {
        return client.loadAnimation(TRICK_PLAYER_ANIMATION);
    }

    void prepareTrickTimelineForInspector(
            Animation animation,
            TrickDefinition definition) {
        prepareTrickTimeline(animation, definition);
    }

    boolean isTrickInspectorTimelineReady() {
        return trickFrameLengths != null
                && trickTotalCycles > 0;
    }

    boolean trickInspectorNeedsInitialization(
            TrickDefinition selectedDefinition) {
        return !trickActive
                || activeTrick == null
                || selectedDefinition == null
                || activeTrick.getSlot() != selectedDefinition.getSlot()
                || trickTotalCycles <= 0;
    }

    void activateTrickInspectorTimeline(
            Player player,
            TrickDefinition definition) {
        activeTrick = definition;
        trickActive = true;
        trickStartGameCycle = client.getGameCycle();
        trickElapsedCycles = 0;
        trickProgress = 0.0;

        saveOriginalMovementAnimations(player);
        applySkatingMovementAnimations(player);

        trickPreviousPoseAnimation = player.getPoseAnimation();
        trickPreviousPoseFrame = player.getPoseAnimationFrame();

        installTrickInterpolationFilter();
        skatingMovementController.clearIdleState();
    }

    void setActiveTrickForInspector(TrickDefinition definition) {
        activeTrick = definition;
    }

    int getActiveTrickSlotForInspector() {
        return activeTrick == null ? 0 : activeTrick.getSlot();
    }

    int getTrickTotalCyclesForInspector() {
        return trickTotalCycles;
    }

    boolean isTrickActiveForInspector() {
        return trickActive;
    }

    void applyTrickInspectorCycle(
            Player player,
            int inspectorCycle) {
        trickElapsedCycles =
                clampInt(
                        inspectorCycle,
                        0,
                        Math.max(0, trickTotalCycles - 1)
                );

        trickProgress =
                clamp(
                        trickElapsedCycles
                                / (double) Math.max(1, trickTotalCycles),
                        0.0,
                        1.0
                );

        saveOriginalMovementAnimations(player);
        applySkatingMovementAnimations(player);
        installTrickInterpolationFilter();

        player.setPoseAnimation(MOVING_SKATE_ANIMATION);
        player.setPoseAnimationFrame(0);

        if (trickElapsedCycles < trickPreTransitionCycles) {
            final int frameIndex =
                    getTransitionFrameIndex(
                            trickElapsedCycles,
                            trickPreFrameCycles,
                            trickActivePreFrames.length
                    );

            player.setAnimation(TRICK_TRANSITION_ANIMATION);
            player.setAnimationFrame(trickActivePreFrames[frameIndex]);
            return;
        }

        final int jumpElapsed =
                trickElapsedCycles - trickPreTransitionCycles;

        if (jumpElapsed < trickMainSequenceCycles) {
            final int playerElapsed =
                    Math.min(
                            jumpElapsed,
                            Math.max(0, trickPlayerMainSequenceCycles - 1)
                    );

            syncPlayerTrickAnimationContext();
            playerTrickAnimationController.applyInspectorMainCycle(
                    player,
                    playerElapsed
            );

            return;
        }

        final int seamElapsed =
                jumpElapsed - trickMainSequenceCycles;

        if (seamElapsed < trickPostSeamCycles) {
            player.setPoseAnimation(MOVING_SKATE_ANIMATION);
            player.setPoseAnimationFrame(0);
            player.setAnimation(TRICK_TRANSITION_ANIMATION);
            player.setAnimationFrame(trickActivePostSeamFrame);
            return;
        }

        final int postElapsed =
                Math.max(0, seamElapsed - trickPostSeamCycles);

        final int frameIndex =
                getTransitionFrameIndex(
                        postElapsed,
                        trickPostFrameCycles,
                        trickActivePostFrames.length
                );

        player.setPoseAnimation(MOVING_SKATE_ANIMATION);
        player.setPoseAnimationFrame(0);
        player.setAnimation(TRICK_TRANSITION_ANIMATION);
        player.setAnimationFrame(trickActivePostFrames[frameIndex]);
    }

    void stopTrickInspectorTimeline(Player player) {
        if (player != null) {
            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }

        restorePlayerAfterTrick(player);
        restoreOriginalMovementAnimations(player);
        resetTrickState();
    }

    void exitTrickInspectorMode(Player player) {
        stopTrickInspectorTimeline(player);
        resetMovementTracking();
        skatingMovementController.clearIdleState();
    }

    void syncInspectorEditorForTrickInspector() {
        syncInspectorEditorFromSelection();
    }

    void refreshConfigPanelForTrickInspector() {
        refreshOpenRuneLiteConfigPanel();
    }

    int getInspectorTuningSignature(int slot) {
        int signature = 17;

        signature = 31 * signature + slot;
        signature = 31 * signature + getTuningInt(slot, "durationMs");
        signature = 31 * signature + getTuningInt(slot, "popHeight");
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "popStart"));
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "kickStart"));
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "kickEnd"));
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "impossibleStart"));
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "catch"));
        signature = 31 * signature + Double.hashCode(getTuningPercent(slot, "touchdown"));

        if (slot == 3) {
            signature = 31 * signature + getVarialKickflipDegrees();
            signature = 31 * signature + Double.hashCode(getVarialShoveStartPercent());
            signature = 31 * signature + Double.hashCode(getVarialShoveEndPercent());
            signature = 31 * signature + getVarialShoveDegrees();
        }

        if (slot == 1) {
            signature = 31 * signature + getKickflipDegrees();
        }

        if (slot == 2) {
            signature = 31 * signature + getShoveItDegrees();
        }

        if (slot == 4) {
            signature = 31 * signature + getTrick4KickflipDegrees();
            signature = 31 * signature + getTrick4ImpossibleDegrees();
        }

        if (slot == 5) {
            signature = 31 * signature + getTrick5KickflipDegrees();
            signature = 31 * signature + Double.hashCode(getTrick5ShoveStartPercent());
            signature = 31 * signature + Double.hashCode(getTrick5ShoveEndPercent());
            signature = 31 * signature + getTrick5ShoveDegrees();
        }

        return signature;
    }

    private void applyMovingState(Player player) {
        skatingMovementController.applyMovingState(
                player,
                this::installTrickInterpolationFilter
        );
    }

    private void applyIdleState(Player player) {
        skatingMovementController.applyIdleState(
                player,
                this::installTrickInterpolationFilter,
                this::restoreTrickInterpolationFilter
        );
    }

    private boolean updateMovementState(Player player) {
        return skatingMovementController.updateMovementState(player);
    }

    /*
     * SHARED TRICK ENGINE / PLAYER CHOREOGRAPHY
     *
     * The engine owns shared timing, anchors, pop, landing and cleanup.
     * TrickDefinition and per-trick pose data supply the trick-specific board
     * and player choreography.
     */
    /*
     * PLAYER TRICK ANIMATION CONTEXT.
     *
     * The prepared timeline is passed to PlayerTrickAnimationController
     * through one reusable context object so player playback reads the same
     * timing state as the runtime and board controllers.
     */
    private void syncPlayerTrickAnimationContext() {
        if (playerTrickAnimationController == null) {
            return;
        }

        playerTrickAnimationContext.trickActivePlayerAnimationIds =
                trickActivePlayerAnimationIds;
        playerTrickAnimationContext.trickActivePlayerFrames =
                trickActivePlayerFrames;
        playerTrickAnimationContext.trickActivePlayerPoseCycles =
                trickActivePlayerPoseCycles;
        playerTrickAnimationContext.trickActiveJumpFrames =
                trickActiveJumpFrames;
        playerTrickAnimationContext.trickActiveJumpFrameCycles =
                trickActiveJumpFrameCycles;
        playerTrickAnimationContext.trickMainSequenceCycles =
                trickMainSequenceCycles;
        playerTrickAnimationContext.trickActivePreFrames =
                trickActivePreFrames;
        playerTrickAnimationContext.trickPreFrameCycles =
                trickPreFrameCycles;
        playerTrickAnimationContext.trickPreTransitionCycles =
                trickPreTransitionCycles;
        playerTrickAnimationContext.trickElapsedCycles =
                trickElapsedCycles;
        playerTrickAnimationContext.trickActivePostSeamFrame =
                trickActivePostSeamFrame;
        playerTrickAnimationContext.trickPostSeamCycles =
                trickPostSeamCycles;
        playerTrickAnimationContext.trickActivePostFrames =
                trickActivePostFrames;
        playerTrickAnimationContext.trickPostFrameCycles =
                trickPostFrameCycles;

        playerTrickAnimationController.syncContext(
                playerTrickAnimationContext
        );
    }

    void clearPlayerTrickAnimationContext() {
        playerTrickAnimationContext.clear();
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.clearTimelineContext();
        }
    }

    void clearSkateboardTrickTransformContext() {
        skateboardTrickTransformContext.clear();
    }

    void installTrickInterpolationFilter() {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.installTrickInterpolationFilter();
    }

    void restoreTrickInterpolationFilter() {
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.restoreTrickInterpolationFilter();
        }
    }

    void prepareNaturalMainTrickPlayback(
            TrickDefinition definition) {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.prepareNaturalMainTrickPlayback(
                definition
        );
    }

    void restoreNaturalMainTrickTiming() {
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.restoreNaturalMainTrickTiming();
        }
    }

    void prepareNaturalTransitionTiming() {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.prepareNaturalTransitionTiming();
    }

    void restoreNaturalTransitionTiming() {
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.restoreNaturalTransitionTiming();
        }
    }

    void startNaturalPreTransition(Player player) {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.startNaturalPreTransition(player);
    }

    void updateNaturalPreTransition(Player player) {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.updateNaturalPreTransition(player);
    }

    void updateNaturalPostSeam(Player player) {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.updateNaturalPostSeam(player);
    }

    void updateNaturalPostReturn(
            Player player,
            int postElapsed,
            boolean moving) {

        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.updateNaturalPostReturn(
                player,
                postElapsed,
                moving
        );
    }

    void updateMainTrickAnimation(
            Player player,
            int jumpElapsed,
            boolean moving) {
        if (playerTrickAnimationController == null) {
            return;
        }

        syncPlayerTrickAnimationContext();
        playerTrickAnimationController.updateMainTrickAnimation(
                player,
                jumpElapsed,
                moving
        );
    }

    void finishNaturalMainTrickPlayback() {
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.finishNaturalMainTrickPlayback();
        }
    }

    void stopNaturalMainTrickAnimation(Player player) {
        if (playerTrickAnimationController != null) {
            playerTrickAnimationController.stopNaturalMainTrickAnimation(
                    player
            );
        }
    }

    /*
     * TIMELINE BUILDER EXTRACTION.
     *
     * All config-heavy PRE / MAIN / seam / RETURN timeline construction now
     * lives in TrickTimelineBuilder. These bridges keep runtime and board
     * rendering on the same prepared state.
     */
    void prepareTrickTimeline(
            Animation animation,
            TrickDefinition definition) {

        trickTimelineBuilder.prepare(
                animation,
                definition
        );
    }

    int getCycleOffsetAfterActiveTrickFrame(
            int nativeFrame) {

        return trickTimelineBuilder == null
                ? 0
                : trickTimelineBuilder
                        .getCycleOffsetAfterActiveTrickFrame(nativeFrame);
    }

    int getCycleOffsetAtActiveTrickFrame(
            int nativeFrame) {

        return trickTimelineBuilder == null
                ? 0
                : trickTimelineBuilder
                        .getCycleOffsetAtActiveTrickFrame(nativeFrame);
    }


    /*
     * Read-only phase label used by the developer Trick Inspector.
     * Inspector rendering reads the live trick clock without owning it.
     */
    private String getTrickTracePhase() {
        if (!trickActive) {
            return "INACTIVE";
        }

        if (trickElapsedCycles < trickPreTransitionCycles) {
            return "PRE";
        }

        final int mainElapsed =
                trickElapsedCycles - trickPreTransitionCycles;

        if (mainElapsed < trickMainSequenceCycles) {
            return "MAIN";
        }

        final int seamElapsed =
                mainElapsed - trickMainSequenceCycles;

        if (seamElapsed < trickPostSeamCycles) {
            return "SEAM";
        }

        return "RETURN";
    }

    private int getTransitionFrameIndex(
            int elapsedCycles,
            int[] frameCycles,
            int frameCount) {

        if (frameCycles == null
                || frameCycles.length == 0
                || frameCount <= 0) {

            return 0;
        }

        int cycleCursor = 0;

        for (int i = 0;
                i < frameCycles.length;
                i++) {

            cycleCursor +=
                    Math.max(
                            1,
                            frameCycles[i]
                    );

            if (elapsedCycles < cycleCursor) {
                return Math.min(
                        i,
                        frameCount - 1
                );
            }
        }

        return frameCount - 1;
    }

    boolean isPlayerStillMoving(
            Player player) {

        return skatingMovementController.isPlayerStillMoving(player);
    }

    /*
     * RUNTIME/LIFECYCLE BRIDGES.
     *
     * Plugin lifecycle and inspector paths still need to restore/reset trick
     * state, while the implementation itself lives with the phase machine in
     * TrickRuntimeController.
     */
    private void restorePlayerAfterTrick(
            Player player) {

        if (trickRuntimeController != null) {
            trickRuntimeController.restorePlayerAfterTrick(player);
        }
    }

    private void resetTrickState() {
        if (trickRuntimeController != null) {
            trickRuntimeController.reset();
            return;
        }

        /*
         * Startup safety only: startUp() creates the runtime controller before
         * the first reset, but keeping these three values harmlessly reset makes
         * shutdown/partial-start failure paths robust.
         */
        trickActive = false;
        activeTrick = null;
        trickProgress = 0.0;
    }

    void logTrickLifecycleAnimationUnavailable(
            String action,
            TrickDefinition definition) {

        log.debug(
                "Could not {} {}: animation {} unavailable",
                action,
                definition == null ? "trick" : definition.getName(),
                TRICK_PLAYER_ANIMATION
        );
    }

    void clearSkatingIdleState() {
        skatingMovementController.clearIdleState();
    }

    void enterSettledIdleAfterTrick(Player player) {
        /* direct post-trick idle must remain raw from its first frame. */
        installTrickInterpolationFilter();
        skatingMovementController.enterSettledIdleAfterTrick(player);
    }

    void clearSkateboardTrickTransformApplied() {
        if (skateboardSceneController != null) {
            skateboardSceneController.clearTrickTransformApplied();
        }
    }

    /*
     * BOARD + WHEELS
     *
     * The deck and all four wheels are rendered as ONE RuneLiteObject.
     * This avoids the disappearing-piece problem caused by several
     * temporary scene objects occupying the same area.
     *
     * Grandpa protocol:
     * - board world location is still calculated exactly the same way
     * - board orientation still follows the player exactly the same way
     * - known-good board offsets remain untouched
     */
    private void updateSkateboard(Player player) {
        if (skateboardTrickTransformController == null) {
            return;
        }

        skateboardTrickTransformController.update(
                player,
                createSkateboardTrickTransformContext()
        );
    }

    private int percentageToMainCycle(double percent) {
        final int totalCycles = Math.max(1, trickBoardMainSequenceCycles);
        final double safePercent = Math.max(0.0, Math.min(100.0, percent));

        return Math.max(
                0,
                Math.min(
                        totalCycles,
                        (int) Math.round(
                                safePercent * totalCycles / 100.0
                        )
                )
        );
    }

    private SkateboardTrickTransformController.Context
            createSkateboardTrickTransformContext() {

        if (!trickActive || activeTrick == null) {
            return skateboardTrickTransformContext.setInactive();
        }

        final int trickSlot = activeTrick.getSlot();

        final int popHeight =
                getTuningInt(
                        trickSlot,
                        "popHeight"
                );

        final int kickStartMainCycle =
                percentageToMainCycle(
                        getTuningPercent(
                                trickSlot,
                                "kickStart"
                        )
                );

        final int kickEndMainCycle =
                percentageToMainCycle(
                        getTuningPercent(
                                trickSlot,
                                "kickEnd"
                        )
                );

        final int impossibleStartMainCycle =
                percentageToMainCycle(
                        getTuningPercent(
                                trickSlot,
                                "impossibleStart"
                        )
                );

        final int catchMainCycle =
                percentageToMainCycle(
                        getTuningPercent(
                                trickSlot,
                                "catch"
                        )
                );

        int kickflipDegrees = 0;
        int shoveStartMainCycle = 0;
        int shoveEndMainCycle = 0;
        int shoveDegrees = 0;
        int impossibleDegrees = 0;

        int[] trick5BoardX = null;
        int[] trick5BoardY = null;
        int[] trick5BoardZ = null;
        int[] trick5BoardPitch = null;
        int[] trick5BoardYaw = null;
        int[] trick5BoardRoll = null;
        boolean[] trick5BoardLocked = null;

        switch (trickSlot) {
            case 1:
                kickflipDegrees =
                        getKickflipDegrees();
                break;

            case 2:
                shoveDegrees =
                        getShoveItDegrees();
                break;

            case 3:
                kickflipDegrees =
                        getVarialKickflipDegrees();
                shoveStartMainCycle =
                        percentageToMainCycle(
                                getVarialShoveStartPercent()
                        );
                shoveEndMainCycle =
                        percentageToMainCycle(
                                getVarialShoveEndPercent()
                        );
                shoveDegrees =
                        getVarialShoveDegrees();
                break;

            case 4:
                kickflipDegrees =
                        getTrick4KickflipDegrees();
                impossibleDegrees =
                        getTrick4ImpossibleDegrees();
                break;

            case 5: {
                kickflipDegrees =
                        getTrick5KickflipDegrees();
                shoveStartMainCycle =
                        percentageToMainCycle(
                                getTrick5ShoveStartPercent()
                        );
                shoveEndMainCycle =
                        percentageToMainCycle(
                                getTrick5ShoveEndPercent()
                        );
                shoveDegrees =
                        getTrick5ShoveDegrees();

                final Trick5BoardChoreographyController choreography =
                        trick5BoardChoreography();

                choreography.updateTimeline(
                        trickPreTransitionCycles,
                        trickActivePlayerPoseCycles,
                        trickTotalCycles
                );

                trick5BoardX = choreography.getX();
                trick5BoardY = choreography.getY();
                trick5BoardZ = choreography.getZ();
                trick5BoardPitch = choreography.getPitch();
                trick5BoardYaw = choreography.getYaw();
                trick5BoardRoll = choreography.getRoll();
                trick5BoardLocked = choreography.getLocked();
                break;
            }

            default:
                break;
        }

        final int frame9AbsoluteCycle =
                trickPreTransitionCycles
                        + getCycleOffsetAtActiveTrickFrame(9);

        final int frame10AbsoluteCycle =
                trickPreTransitionCycles
                        + getCycleOffsetAtActiveTrickFrame(10);

        return skateboardTrickTransformContext.set(
                true,
                activeTrick,
                trickElapsedCycles,
                trickTotalCycles,
                trickPreTransitionCycles,
                trickMainSequenceCycles,
                trickBoardMainSequenceCycles,
                trickPostSeamCycles,
                trickPopStartCycleOffset,
                trickPopEndCycleOffset,
                trickFlipStartCycleOffset,
                trickFlipEndCycleOffset,
                trickCatchCycleOffset,
                trickPreFrameCycles,
                trickActivePreBoardPositions,
                trickActivePreFrames == null
                        ? 0
                        : trickActivePreFrames.length,
                trickPostFrameCycles,
                trickActivePostBoardPositions,
                trickActivePostFrames == null
                        ? 0
                        : trickActivePostFrames.length,
                popHeight,
                kickStartMainCycle,
                kickEndMainCycle,
                impossibleStartMainCycle,
                catchMainCycle,
                kickflipDegrees,
                shoveStartMainCycle,
                shoveEndMainCycle,
                shoveDegrees,
                impossibleDegrees,
                frame9AbsoluteCycle,
                frame10AbsoluteCycle,
                trickActivePlayerPoseCycles,
                trick5BoardX,
                trick5BoardY,
                trick5BoardZ,
                trick5BoardPitch,
                trick5BoardYaw,
                trick5BoardRoll,
                trick5BoardLocked
        );
    }

    private int clampInt(
            int value,
            int min,
            int max) {

        if (max < min) {
            return min;
        }

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private double clamp(
            double value,
            double min,
            double max) {

        return Math.max(min, Math.min(max, value));
    }

    private void removeBoard() {
        if (skateboardSceneController != null) {
            skateboardSceneController.removeBoard();
        }
    }

    private void scheduleClientStateCleanupForShutdown(
            SkatingMovementController movementController,
            PlayerTrickAnimationController animationController,
            AnimationInspector animationInspectorForShutdown,
            SkateboardSceneController sceneController,
            boolean wasTrickActive,
            int previousPoseAnimation,
            int previousPoseFrame,
            boolean wasAnimationTestActive) {

        clientThread.invoke(() -> {
            final Player player = client.getLocalPlayer();

            if (animationController != null) {
                /*
                 * These timing/filter patches mutate client-owned Animation
                 * state, so restore them on the client thread even if the
                 * player vanished during logout/shutdown.
                 */
                animationController.stopNaturalMainTrickAnimation(player);
                animationController.restoreNaturalTransitionTiming();
                animationController.restoreTrickInterpolationFilter();
            }

            if (animationInspectorForShutdown != null) {
                animationInspectorForShutdown.restoreInterpolationFilter();
            }

            if (player != null) {
                if (wasTrickActive) {
                    if (player.getAnimation()
                            == TRICK_TRANSITION_ANIMATION) {

                        player.setAnimation(-1);
                        player.setAnimationFrame(0);
                    }

                    if (previousPoseAnimation >= 0) {
                        player.setPoseAnimation(previousPoseAnimation);
                        player.setPoseAnimationFrame(
                                Math.max(0, previousPoseFrame)
                        );
                    } else {
                        player.setPoseAnimation(
                                player.getIdlePoseAnimation()
                        );
                        player.setPoseAnimationFrame(0);
                    }
                } else if (wasAnimationTestActive) {
                    player.setAnimation(-1);
                    player.setPoseAnimation(
                            player.getIdlePoseAnimation()
                    );
                    player.setPoseAnimationFrame(0);
                }

                if (movementController != null) {
                    movementController
                            .restoreOriginalMovementAnimations(player);
                }

                if (player.getAnimation() == IDLE_SKATE_ANIMATION) {
                    player.setAnimation(-1);
                }
            }

            if (movementController != null) {
                movementController.restoreIdleAnimationTiming();
                movementController.clearIdleState();
                movementController.resetMovementTracking();
            }

            if (sceneController != null) {
                sceneController.removeBoard();
            }
        });
    }

    private void resetMovementTracking() {
        skatingMovementController.resetMovementTracking();
    }

    /*
     * Generic safe animation helpers remain here because the live trick engine
     * uses them directly. AnimationInspector keeps isolated browser helpers.
     */
    Animation loadSafeAnimation(int animationId) {
        if (animationId < 0
                || animationId > MAX_SAFE_ANIMATION_ID) {
            return null;
        }

        try {
            final Animation animation =
                    client.loadAnimation(animationId);

            if (animation == null
                    || animation.getNumFrames() <= 0) {
                return null;
            }

            return animation;
        } catch (RuntimeException ex) {
            log.debug(
                    "Skipping unsafe/nonexistent animation {}",
                    animationId,
                    ex
            );
            return null;
        }
    }

    int getSafeMaxFrame(Animation animation) {
        if (animation == null) {
            return 0;
        }

        return Math.max(
                0,
                animation.getNumFrames() - 1
        );
    }

    /* Animation Inspector state is owned by AnimationInspector. */
    int getAnimationBrowseMaxFrame() {
        return animationInspector == null
                ? -1
                : animationInspector.getBrowseMaxFrame();
    }

    int getLiveInspectorMaxCycleForConfigPanel() {
        return trickInspector == null
                ? 500
                : trickInspector.getLiveInspectorMaxCycle();
    }

    void restoreAnimationTestInterpolationFilter() {
        if (animationInspector != null) {
            animationInspector.restoreInterpolationFilter();
        }
    }

    @Provides
    SkateScapeConfig provideConfig(ConfigManager configManager) {
        if (SkateScapeDeveloperMode.ENABLED) {
            return configManager.getConfig(SkateScapeDeveloperConfig.class);
        }

        return configManager.getConfig(SkateScapeConfig.class);
    }
}
