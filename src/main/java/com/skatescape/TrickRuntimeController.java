package com.skatescape;

import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;

final class TrickRuntimeController {
    private final Client client;
    private final SkateScapePlugin host;

    TrickRuntimeController(
            Client client,
            SkateScapePlugin host) {
        this.client = client;
        this.host = host;
    }

    void handleHotkeyRequest(Player player) {
        final int trickSlot = host.pendingTrickSlot;

        if (trickSlot == 0) {
            return;
        }

        final TrickDefinition definition =
                TrickRegistry.forSlot(trickSlot);

        if (definition == null) {
            /*
             * Slots without an actual trick definition are never buffered.
             * If one is pressed, treat it as the newest input and clear it.
             */
            host.pendingTrickSlot = 0;
            return;
        }

        if (host.trickActive) {
            /*
             * COMBO BUFFER.
             *
             * Keep exactly one requested trick while the current trick is
             * active. Another hotkey press replaces it with the newest request.
             * The buffer is consumed only on the configured exact exit cycle.
             */
            return;
        }

        host.pendingTrickSlot = 0;
        start(
                player,
                definition
        );
    }

    void update(
            Player player,
            boolean moving) {

        if (!host.trickActive) {
            return;
        }

        host.trickElapsedCycles =
                Math.max(
                        0,
                        client.getGameCycle()
                                - host.trickStartGameCycle
                );

        host.trickProgress =
                clamp(
                        host.trickElapsedCycles
                                / (double) Math.max(
                                        1,
                                        host.trickTotalCycles
                                ),
                        0.0,
                        1.0
                );

        /*
         * DURATION-AWARE EXACT QUEUED-TRICK SPLICE.
         *
         * The configured exit still appears in the UI as the exact complete
         * Trick Inspector cycle. Per-trick storage keeps that point tied to
         * the same visual PRE / MAIN / seam / RETURN position when MAIN
         * duration is changed for slow-motion inspection.
         *
         * The handoff itself remains intentionally exact: the next trick must
         * already be buffered on the configured exit cycle. If that cycle is
         * missed, the current trick finishes and returns normally.
         */
        final TrickDefinition currentDefinition = host.activeTrick;
        if (currentDefinition != null
                && host.pendingTrickSlot != 0
                && host.trickElapsedCycles
                        == host.getQueuedExitCycle(
                                currentDefinition.getSlot()
                        )
                && startBufferedTrickAtConfiguredCycle(
                        player,
                        moving
                )) {

            return;
        }

        if (host.trickElapsedCycles
                >= host.trickTotalCycles) {

            finish(
                    player,
                    moving
            );
            return;
        }

        /*
         * PHASE 1: PRE-JUMP 1708 TRANSITION
         *
         * Keep the 18 -> 27 centring choreography on the POSE layer while the
         * action layer stays clear for equipment-safe playback.
         */
        if (host.trickElapsedCycles
                < host.trickPreTransitionCycles) {

            host.updateNaturalPreTransition(player);
            return;
        }

        final int jumpElapsed =
                host.trickElapsedCycles
                        - host.trickPreTransitionCycles;

        /*
         * PHASE 2: MAIN
         *
         * Tricks 1-3 use the configured 1603 pose sequence, Trick 4 may use
         * natural 2890 action playback, and Trick 5 uses its mixed-animation
         * pose sequence. Player and board remain on the same MAIN clock.
         */
        if (jumpElapsed
                < host.trickMainSequenceCycles) {

            host.updateMainTrickAnimation(
                    player,
                    jumpElapsed,
                    moving
            );

            return;
        }

        /*
         * Restore any temporary natural MAIN timing patch. If Trick 4 used
         * action-layer 2890, leave that action alive until the 1708 landing
         * seam replaces it in this same ClientTick.
         */
        host.finishNaturalMainTrickPlayback();

        final int seamElapsed =
                Math.max(
                        0,
                        jumpElapsed
                                - host.trickMainSequenceCycles
                );

        /*
         * PHASE 3: 1708/27 LANDING SEAM
         *
         * Keep the player and board centred for one tiny beat before the
         * configured RETURN sequence begins at 1708/0.
         */
        if (seamElapsed
                < host.trickPostSeamCycles) {

            host.updateNaturalPostSeam(player);
            return;
        }

        /*
         * PHASE 4: 1708 RETURN / BALANCE
         *
         * 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9
         *
         * Only now do body and board begin moving back from the tile centre
         * toward the normal skating anchor.
         */
        final int postElapsed =
                Math.max(
                        0,
                        seamElapsed
                                - host.trickPostSeamCycles
                );

        host.updateNaturalPostReturn(
                player,
                postElapsed,
                moving
        );
    }

    private boolean startBufferedTrickAtConfiguredCycle(
            Player player,
            boolean moving) {

        final int trickSlot = host.pendingTrickSlot;

        if (trickSlot == 0) {
            return false;
        }

        final TrickDefinition definition =
                TrickRegistry.forSlot(trickSlot);

        /*
         * Consume exactly one buffered input. A later hotkey press while the
         * chained trick is running can populate the buffer again.
         */
        host.pendingTrickSlot = 0;

        if (definition == null) {
            // COMBO DEAD. MIKE HAWK IS ON THE FUCKING PAVEMENT.
            return false;
        }

        return startAtTimelineCycle(
                player,
                definition,
                host.getQueuedStartCycle(trickSlot),
                moving
        );
    }

    /*
     * Lifecycle paths live with the phase machine so trick start, combo
     * handoff, finish and interruption cleanup share one runtime owner. :D
     */
    void start(
            Player player,
            TrickDefinition definition) {

        /*
         * Never stack the Animation Testing smoothing wrapper underneath
         * SkateScape's normal raw-playback wrapper. The tester re-applies its
         * own state after the trick if Animation Testing is still enabled.
         */
        host.restoreAnimationTestInterpolationFilter();

        /*
         * A previous trick/combo may have temporarily patched an Animation's
         * live frame-length array for natural action-layer playback.
         * Always restore it before reading native timing for a new trick.
         */
        host.restoreNaturalMainTrickTiming();
        host.restoreNaturalTransitionTiming();

        final Animation animation =
                client.loadAnimation(
                        SkateScapePlugin.TRICK_PLAYER_ANIMATION
                );

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            host.logTrickLifecycleAnimationUnavailable(
                    "start",
                    definition
            );
            return;
        }

        host.prepareTrickTimeline(
                animation,
                definition
        );

        if (host.trickFrameLengths == null
                || host.trickFrameLengths.length == 0
                || host.trickTotalCycles <= 0) {

            return;
        }

        host.activeTrick = definition;
        host.trickActive = true;
        host.trickProgress = 0.0;
        host.trickElapsedCycles = 0;

        host.restoreIdleAnimationTiming();
        host.prepareNaturalTransitionTiming();
        host.clearSkatingIdleState();

        /*
         * Keep the skating walk/run/idle FALLBACK DEFINITIONS alive
         * underneath the trick. onClientTick continues reinforcing these
         * definitions for the full trick duration.
         */
        host.saveOriginalMovementAnimations(player);
        host.applySkatingMovementAnimations(player);

        /*
         * Save whatever pose RuneScape was using so shutdown/interruption
         * can restore it cleanly.
         */
        host.trickPreviousPoseAnimation =
                player.getPoseAnimation();

        host.trickPreviousPoseFrame =
                player.getPoseAnimationFrame();

        host.trickStartGameCycle =
                client.getGameCycle();

        host.installTrickInterpolationFilter();

        /*
         * Start the 1708 corner -> centre choreography on the POSE layer.
         * PlayerTrickAnimationController keeps the action layer clear here.
         */
        host.startNaturalPreTransition(player);

    }

    boolean startAtTimelineCycle(
            Player player,
            TrickDefinition definition,
            int requestedStartCycle,
            boolean moving) {

        /*
         * A user may splice out of PRE, MAIN, seam or RETURN. Retire any
         * outgoing natural MAIN action immediately before rebuilding the next
         * trick, then restore the temporary transition timing patch.
         */
        host.stopNaturalMainTrickAnimation(player);
        host.restoreNaturalTransitionTiming();

        final Animation animation =
                client.loadAnimation(
                        SkateScapePlugin.TRICK_PLAYER_ANIMATION
                );

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            host.logTrickLifecycleAnimationUnavailable(
                    "chain",
                    definition
            );
            return false;
        }

        host.prepareTrickTimeline(
                animation,
                definition
        );

        if (host.trickFrameLengths == null
                || host.trickFrameLengths.length == 0
                || host.trickTotalCycles <= 0
                || host.trickActiveJumpFrames == null
                || host.trickActiveJumpFrames.length == 0) {

            return false;
        }

        host.activeTrick = definition;
        host.trickActive = true;

        /*
         * Queue start is the SAME full timeline coordinate shown by Trick
         * Inspector. Clamp only to the last renderable cycle of this prepared
         * trick so an oversized value cannot instantly finish the new trick.
         */
        final int startCycle =
                Math.max(
                        0,
                        Math.min(
                                requestedStartCycle,
                                Math.max(0, host.trickTotalCycles - 1)
                        )
                );

        host.trickElapsedCycles = startCycle;

        host.trickStartGameCycle =
                client.getGameCycle()
                        - startCycle;

        host.trickProgress =
                clamp(
                        host.trickElapsedCycles
                                / (double) Math.max(
                                        1,
                                        host.trickTotalCycles
                                ),
                        0.0,
                        1.0
                );

        host.restoreIdleAnimationTiming();
        host.prepareNaturalTransitionTiming();
        host.clearSkatingIdleState();

        host.saveOriginalMovementAnimations(player);
        host.applySkatingMovementAnimations(player);

        /*
         * The interpolation wrapper is already installed for a normal combo;
         * the call is idempotent. Keep the original pre-combo pose snapshot so
         * interruption/shutdown still restores the state from the very start
         * of the combo rather than the middle of a splice.
         */
        host.installTrickInterpolationFilter();

        /*
         * Render the requested phase immediately in this same ClientTick.
         * update() is the single authoritative PRE/MAIN/seam/RETURN phase
         * renderer, so arbitrary queue start cycles cannot create a second
         * competing interpretation of the trick timeline. pendingTrickSlot was
         * consumed above, therefore this cannot recursively chain again here.
         */
        update(
                player,
                moving
        );

        return true;
    }

    void finish(
            Player player,
            boolean moving) {

        host.stopNaturalMainTrickAnimation(player);
        host.restoreNaturalTransitionTiming();

        host.trickActive = false;
        host.activeTrick = null;
        host.trickProgress = 0.0;

        host.restoreTrickInterpolationFilter();
        clearTimeline();

        if (moving) {
            /*
             * Still genuinely moving at trick end.
             *
             * Leave the idle state inactive. The normal onClientTick path
             * immediately hands the pose back to movement 3004.
             */
            host.clearSkatingIdleState();
            return;
        }

        /*
         * DIRECT POST-TRICK IDLE HANDOFF.
         *
         * The trick return ends in the 1708 family, normally around frame 9,
         * so continue cleanly into settled idle instead of exposing a centred
         * default idle frame.
         */
        host.enterSettledIdleAfterTrick(player);
    }

    void restorePlayerAfterTrick(
            Player player) {

        host.stopNaturalMainTrickAnimation(player);
        host.restoreNaturalTransitionTiming();

        if (player != null) {
            if (player.getAnimation()
                    == SkateScapePlugin.TRICK_TRANSITION_ANIMATION) {

                player.setAnimation(-1);
                player.setAnimationFrame(0);
            }

            if (host.trickPreviousPoseAnimation >= 0) {
                player.setPoseAnimation(
                        host.trickPreviousPoseAnimation
                );

                player.setPoseAnimationFrame(
                        Math.max(
                                0,
                                host.trickPreviousPoseFrame
                        )
                );
            } else {
                player.setPoseAnimation(
                        player.getIdlePoseAnimation()
                );
                player.setPoseAnimationFrame(0);
            }
        }

        host.restoreTrickInterpolationFilter();
        clearTimeline();
    }

    void reset() {
        host.restoreTrickInterpolationFilter();
        clearTimeline();
        clearStateFields();
    }

    /*
     * Shutdown is initiated on Swing's EDT. By the time this method is used,
     * client-owned animation/filter state has been captured for asynchronous
     * restoration on ClientThread, so only discard Java-side runtime state.
     */
    void discardState() {
        clearPreparedTimeline();
        clearStateFields();
    }

    private void clearStateFields() {
        host.trickActive = false;
        host.activeTrick = null;
        host.trickStartGameCycle = 0;
        host.trickElapsedCycles = 0;
        host.trickTotalCycles = 0;
        host.trickPopStartCycleOffset = 0;
        host.trickPopEndCycleOffset = 0;
        host.trickFlipStartCycleOffset = 0;
        host.trickFlipEndCycleOffset = 0;
        host.trickCatchCycleOffset = 0;

        host.trickActivePopStartFrame =
                SkateScapePlugin.DEFAULT_TRICK_POP_START_FRAME;
        host.trickActivePopEndFrame =
                SkateScapePlugin.DEFAULT_TRICK_POP_END_FRAME;
        host.trickActiveFlipStartFrame =
                SkateScapePlugin.DEFAULT_TRICK_FLIP_START_FRAME;
        host.trickActiveFlipEndFrame =
                SkateScapePlugin.DEFAULT_TRICK_FLIP_END_FRAME;

        host.trickPreTransitionCycles = 0;
        host.trickPreFrameCycles = null;

        host.trickMainSequenceCycles = 0;
        host.trickBoardMainSequenceCycles = 0;
        host.trickPlayerMainSequenceCycles = 0;

        host.trickPostSeamCycles = 0;
        host.trickPostTransitionCycles = 0;
        host.trickPostFrameCycles = null;

        host.trickProgress = 0.0;

        host.clearSkateboardTrickTransformApplied();
        host.invalidateTrickInspectorTuningSignature();
    }

    private void clearTimeline() {
        host.restoreNaturalMainTrickTiming();
        host.restoreNaturalTransitionTiming();
        clearPreparedTimeline();
    }

    private void clearPreparedTimeline() {
        host.trickFrameLengths = null;

        host.trickPreFrameCycles = null;
        host.trickActivePreFrames = null;
        host.trickActivePreBoardPositions = null;

        host.trickActiveJumpFrames = null;
        host.trickActiveJumpFrameCycles = null;

        host.trickActivePlayerAnimationIds = null;
        host.trickActivePlayerFrames = null;
        host.trickActivePlayerPoseCycles = null;

        host.trickActivePostSeamFrame =
                SkateScapePlugin.DEFAULT_TRICK_POST_SEAM_FRAME;

        host.trickPostFrameCycles = null;
        host.trickActivePostFrames = null;
        host.trickActivePostBoardPositions = null;

        host.trickPreviousPoseAnimation = -1;
        host.trickPreviousPoseFrame = 0;

        host.clearPlayerTrickAnimationContext();
        host.clearSkateboardTrickTransformContext();
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }
}
