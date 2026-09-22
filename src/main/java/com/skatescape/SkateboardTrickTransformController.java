package com.skatescape;

import net.runelite.api.ModelData;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

/**
 * Applies trick-specific skateboard transforms. SkateScapePlugin owns the
 * timeline; this controller converts the current timeline state into anchor
 * movement, pop height, board rotation, pitch, catch descent, and clearance.
 *
 * Mike Hawk may bend the laws of board geometry here instead of doing it in
 * the kitchen sink. :D
 */
final class SkateboardTrickTransformController {
    static final class Context {
        boolean trickActive;
        TrickDefinition activeTrick;

        int elapsedCycles;
        int totalCycles;
        int preTransitionCycles;
        int mainSequenceCycles;
        int boardMainSequenceCycles;
        int postSeamCycles;

        int popStartCycleOffset;
        int popEndCycleOffset;
        int flipStartCycleOffset;
        int flipEndCycleOffset;
        int catchCycleOffset;

        int[] preFrameCycles;
        int[] preBoardPositions;
        int preFrameCount;

        int[] postFrameCycles;
        int[] postBoardPositions;
        int postFrameCount;

        int popHeight;
        int kickStartMainCycle;
        int kickEndMainCycle;
        int impossibleStartMainCycle;
        int catchMainCycle;

        int kickflipDegrees;
        int shoveStartMainCycle;
        int shoveEndMainCycle;
        int shoveDegrees;
        int impossibleDegrees;

        int frame9AbsoluteCycle;
        int frame10AbsoluteCycle;

        int[] trick5BoardFrameCycles;
        int[] trick5BoardFrameX;
        int[] trick5BoardFrameY;
        int[] trick5BoardFrameZ;
        int[] trick5BoardFramePitchDegrees;
        int[] trick5BoardFrameYawDegrees;
        int[] trick5BoardFrameRollDegrees;
        boolean[] trick5BoardFrameLocked;

        Context set(
                boolean trickActive,
                TrickDefinition activeTrick,
                int elapsedCycles,
                int totalCycles,
                int preTransitionCycles,
                int mainSequenceCycles,
                int boardMainSequenceCycles,
                int postSeamCycles,
                int popStartCycleOffset,
                int popEndCycleOffset,
                int flipStartCycleOffset,
                int flipEndCycleOffset,
                int catchCycleOffset,
                int[] preFrameCycles,
                int[] preBoardPositions,
                int preFrameCount,
                int[] postFrameCycles,
                int[] postBoardPositions,
                int postFrameCount,
                int popHeight,
                int kickStartMainCycle,
                int kickEndMainCycle,
                int impossibleStartMainCycle,
                int catchMainCycle,
                int kickflipDegrees,
                int shoveStartMainCycle,
                int shoveEndMainCycle,
                int shoveDegrees,
                int impossibleDegrees,
                int frame9AbsoluteCycle,
                int frame10AbsoluteCycle,
                int[] trick5BoardFrameCycles,
                int[] trick5BoardFrameX,
                int[] trick5BoardFrameY,
                int[] trick5BoardFrameZ,
                int[] trick5BoardFramePitchDegrees,
                int[] trick5BoardFrameYawDegrees,
                int[] trick5BoardFrameRollDegrees,
                boolean[] trick5BoardFrameLocked) {

            this.trickActive = trickActive;
            this.activeTrick = activeTrick;
            this.elapsedCycles = elapsedCycles;
            this.totalCycles = totalCycles;
            this.preTransitionCycles = preTransitionCycles;
            this.mainSequenceCycles = mainSequenceCycles;
            this.boardMainSequenceCycles = boardMainSequenceCycles;
            this.postSeamCycles = postSeamCycles;
            this.popStartCycleOffset = popStartCycleOffset;
            this.popEndCycleOffset = popEndCycleOffset;
            this.flipStartCycleOffset = flipStartCycleOffset;
            this.flipEndCycleOffset = flipEndCycleOffset;
            this.catchCycleOffset = catchCycleOffset;
            this.preFrameCycles = preFrameCycles;
            this.preBoardPositions = preBoardPositions;
            this.preFrameCount = preFrameCount;
            this.postFrameCycles = postFrameCycles;
            this.postBoardPositions = postBoardPositions;
            this.postFrameCount = postFrameCount;
            this.popHeight = popHeight;
            this.kickStartMainCycle = kickStartMainCycle;
            this.kickEndMainCycle = kickEndMainCycle;
            this.impossibleStartMainCycle = impossibleStartMainCycle;
            this.catchMainCycle = catchMainCycle;
            this.kickflipDegrees = kickflipDegrees;
            this.shoveStartMainCycle = shoveStartMainCycle;
            this.shoveEndMainCycle = shoveEndMainCycle;
            this.shoveDegrees = shoveDegrees;
            this.impossibleDegrees = impossibleDegrees;
            this.frame9AbsoluteCycle = frame9AbsoluteCycle;
            this.frame10AbsoluteCycle = frame10AbsoluteCycle;
            this.trick5BoardFrameCycles = trick5BoardFrameCycles;
            this.trick5BoardFrameX = trick5BoardFrameX;
            this.trick5BoardFrameY = trick5BoardFrameY;
            this.trick5BoardFrameZ = trick5BoardFrameZ;
            this.trick5BoardFramePitchDegrees = trick5BoardFramePitchDegrees;
            this.trick5BoardFrameYawDegrees = trick5BoardFrameYawDegrees;
            this.trick5BoardFrameRollDegrees = trick5BoardFrameRollDegrees;
            this.trick5BoardFrameLocked = trick5BoardFrameLocked;
            return this;
        }

        Context setInactive() {
            clear();
            return this;
        }

        void clear() {
            trickActive = false;
            activeTrick = null;
            elapsedCycles = 0;
            totalCycles = 0;
            preTransitionCycles = 0;
            mainSequenceCycles = 0;
            boardMainSequenceCycles = 0;
            postSeamCycles = 0;
            popStartCycleOffset = 0;
            popEndCycleOffset = 0;
            flipStartCycleOffset = 0;
            flipEndCycleOffset = 0;
            catchCycleOffset = 0;
            preFrameCycles = null;
            preBoardPositions = null;
            preFrameCount = 0;
            postFrameCycles = null;
            postBoardPositions = null;
            postFrameCount = 0;
            popHeight = 0;
            kickStartMainCycle = 0;
            kickEndMainCycle = 0;
            impossibleStartMainCycle = 0;
            catchMainCycle = 0;
            kickflipDegrees = 0;
            shoveStartMainCycle = 0;
            shoveEndMainCycle = 0;
            shoveDegrees = 0;
            impossibleDegrees = 0;
            frame9AbsoluteCycle = 0;
            frame10AbsoluteCycle = 0;
            trick5BoardFrameCycles = null;
            trick5BoardFrameX = null;
            trick5BoardFrameY = null;
            trick5BoardFrameZ = null;
            trick5BoardFramePitchDegrees = null;
            trick5BoardFrameYawDegrees = null;
            trick5BoardFrameRollDegrees = null;
            trick5BoardFrameLocked = null;
        }
    }

    private static final int BOARD_FORWARD_OFFSET = -35;
    private static final int BOARD_SIDEWAYS_OFFSET = -20;
    private static final int TRICK_BOARD_FORWARD_OFFSET = 0;
    private static final int TRICK_BOARD_SIDEWAYS_OFFSET = 0;

    private static final double TRICK4_IMPOSSIBLE_EDGE_RAMP_END = 0.25;
    private static final double TRICK4_IMPOSSIBLE_EDGE_HOLD_END = 0.55;
    private static final double TRICK4_IMPOSSIBLE_EDGE_DEGREES = 90.0;

    private static final double TRICK_NOSE_UP_ANGLE =
            Math.toRadians(-14.0);
    private static final double TRICK_APEX_ANGLE =
            Math.toRadians(-2.0);
    private static final double TRICK_CATCH_ANGLE =
            Math.toRadians(5.0);

    private static final double SHOVE_SCOOP_ANGLE =
            Math.toRadians(-6.0);
    private static final double SHOVE_APEX_ANGLE =
            Math.toRadians(-0.5);
    private static final double SHOVE_CATCH_ANGLE =
            Math.toRadians(2.0);

    private static final int TRICK_GROUND_CLEARANCE = 1;

    private final SkateboardSceneController sceneController;

    SkateboardTrickTransformController(
            SkateboardSceneController sceneController) {

        this.sceneController = sceneController;
    }

    void update(
            Player player,
            Context context) {

        if (player == null
                || context == null
                || sceneController == null) {
            return;
        }

        final LocalPoint playerLocation =
                player.getLocalLocation();

        if (playerLocation == null) {
            return;
        }

        final LocalPoint boardLocation =
                calculateBoardLocation(
                        player,
                        playerLocation,
                        context
                );

        sceneController.update(
                player,
                boardLocation,
                context.trickActive,
                (terrainPitch, terrainRoll) ->
                        applySkateboardTransforms(
                                context,
                                terrainPitch,
                                terrainRoll
                        )
        );
    }

    double getBoardAnchorBlend(
            Context context) {

        if (context == null
                || !context.trickActive
                || context.totalCycles <= 0) {

            return 0.0;
        }

        if (context.elapsedCycles
                < context.preTransitionCycles) {

            return getTransitionBoardBlend(
                    context.elapsedCycles,
                    context.preFrameCycles,
                    context.preBoardPositions,
                    context.preFrameCount
            );
        }

        final int jumpEndCycle =
                context.preTransitionCycles
                        + context.mainSequenceCycles;

        if (context.elapsedCycles < jumpEndCycle) {
            return 1.0;
        }

        final int seamEndCycle =
                jumpEndCycle
                        + context.postSeamCycles;

        if (context.elapsedCycles < seamEndCycle) {
            return 1.0;
        }

        final int postElapsed =
                Math.max(
                        0,
                        context.elapsedCycles
                                - seamEndCycle
                );

        return getTransitionBoardBlend(
                postElapsed,
                context.postFrameCycles,
                context.postBoardPositions,
                context.postFrameCount
        );
    }

    private LocalPoint calculateBoardLocation(
            Player player,
            LocalPoint playerLocation,
            Context context) {

        if (!context.trickActive) {
            return SkateboardSceneController.offsetRelativeToPlayer(
                    player,
                    playerLocation,
                    BOARD_FORWARD_OFFSET,
                    BOARD_SIDEWAYS_OFFSET
            );
        }

        /*
         * Trick MAIN playback uses the tile-centred anchor rather than the
         * normal skating offset. The 1708 PRE transition carries Mike Hawk and
         * the board from -35/-20 to 0/0, they stay centred through MAIN and the
         * 1708/27 landing seam, and the 1708/0..9 RETURN sequence carries them
         * back toward -35/-20 together.
         */
        final double anchorBlend =
                getBoardAnchorBlend(context);

        final int forward =
                (int) Math.round(
                        lerp(
                                BOARD_FORWARD_OFFSET,
                                TRICK_BOARD_FORWARD_OFFSET,
                                anchorBlend
                        )
                );

        final int sideways =
                (int) Math.round(
                        lerp(
                                BOARD_SIDEWAYS_OFFSET,
                                TRICK_BOARD_SIDEWAYS_OFFSET,
                                anchorBlend
                        )
                );

        return SkateboardSceneController.offsetRelativeToPlayer(
                player,
                playerLocation,
                forward,
                sideways
        );
    }

    private double getTransitionBoardBlend(
            int elapsedCycles,
            int[] frameCycles,
            int[] boardPositionsPercent,
            int frameCount) {

        if (frameCycles == null
                || frameCycles.length == 0
                || boardPositionsPercent == null
                || boardPositionsPercent.length == 0
                || frameCount <= 0) {

            return 0.0;
        }

        final int usableFrameCount =
                Math.min(
                        frameCount,
                        frameCycles.length
                );

        int cycleCursor = 0;

        for (int i = 0;
                i < usableFrameCount;
                i++) {

            final int duration =
                    Math.max(
                            1,
                            frameCycles[i]
                    );

            final int nextCycle =
                    cycleCursor
                            + duration;

            if (elapsedCycles < nextCycle) {
                final int currentPercent =
                        getRepeatedListValue(
                                boardPositionsPercent,
                                i
                        );

                final int nextPercent =
                        getRepeatedListValue(
                                boardPositionsPercent,
                                Math.min(
                                        i + 1,
                                        usableFrameCount - 1
                                )
                        );

                final double withinFrame =
                        clamp(
                                (elapsedCycles - cycleCursor)
                                        / (double) duration,
                                0.0,
                                1.0
                        );

                return clamp(
                        lerp(
                                currentPercent / 100.0,
                                nextPercent / 100.0,
                                smootherStep(withinFrame)
                        ),
                        0.0,
                        1.0
                );
            }

            cycleCursor = nextCycle;
        }

        return clamp(
                getRepeatedListValue(
                        boardPositionsPercent,
                        usableFrameCount - 1
                ) / 100.0,
                0.0,
                1.0
        );
    }

    private void applySkateboardTransforms(
            Context context,
            double terrainPitch,
            double terrainRoll) {

        final ModelData transformedData =
                sceneController.createWorkingModelData();

        if (transformedData == null) {
            return;
        }

        if (context.trickActive
                && context.activeTrick != null) {

            if (context.activeTrick.getSlot() == 4) {
                /*
                 * Trick 4 is intentionally NOT described by one generic
                 * flipTurns/shoveTurns rotation. Its board sequence is:
                 *
                 *   pop -> kickflip
                 *       -> late edge-on 360 Shove-it/body-follow -> catch
                 */
                applyTrick4BoardRotation(
                        transformedData,
                        context
                );
            } else if (context.activeTrick.getSlot() != 5) {
                final double spinProgress =
                        getTrickRotationProgress(
                                getTrickPhaseProgress(
                                        context,
                                        context.flipStartCycleOffset,
                                        context.flipEndCycleOffset
                                ),
                                context.activeTrick
                        );

                final double flipTurns =
                        context.activeTrick.getSlot() == 1
                                || context.activeTrick.getSlot() == 3
                                ? context.kickflipDegrees / 360.0
                                : context.activeTrick.getFlipTurns();

                final double flipAngle =
                        Math.PI * 2.0
                                * flipTurns
                                * spinProgress;

                if (Math.abs(flipAngle) > 0.000001) {
                    SkateboardModelGeometry.rotateAroundZ(
                            transformedData,
                            flipAngle
                    );
                }

                final double shoveTurns;
                final double shoveSpinProgress;

                if (context.activeTrick.getSlot() == 3) {
                    shoveTurns =
                            context.shoveDegrees / 360.0;
                    shoveSpinProgress =
                            getTrickRotationProgress(
                                    getVarialShoveProgress(
                                            context
                                    ),
                                    context.activeTrick
                            );
                } else if (context.activeTrick.getSlot() == 2) {
                    shoveTurns =
                            context.shoveDegrees / 360.0;
                    shoveSpinProgress = spinProgress;
                } else {
                    shoveTurns =
                            context.activeTrick.getShoveTurns();
                    shoveSpinProgress = spinProgress;
                }

                final double shoveAngle =
                        Math.PI * 2.0
                                * shoveTurns
                                * shoveSpinProgress;

                if (Math.abs(shoveAngle) > 0.000001) {
                    SkateboardModelGeometry.rotateAroundY(
                            transformedData,
                            shoveAngle
                    );
                }

                /*
                 * Pitch belongs to the FREE board phase only. Once Mike Hawk
                 * catches the deck it is level under his feet.
                 */
                final double boardPitch =
                        context.elapsedCycles
                                >= context.catchCycleOffset
                        ? 0.0
                        : getTrickBoardPitch(
                                getTrickPhaseProgress(
                                        context,
                                        context.popStartCycleOffset,
                                        context.flipEndCycleOffset
                                ),
                                context.activeTrick
                        );

                if (Math.abs(boardPitch) > 0.000001) {
                    SkateboardModelGeometry.rotateAroundX(
                            transformedData,
                            boardPitch
                    );
                }
            }
        }

        /*
         * Trick 5 frame-by-frame board choreography.
         *
         * Each player frame owns an XYZ + Pitch/Yaw/Roll target. Values
         * interpolate across that frame's real live duration, so board motion
         * automatically follows Trick duration and Advanced Pose Timing.
         */
        if (context.trickActive
                && context.activeTrick != null
                && context.activeTrick.getSlot() == 5) {

            final double posePitch =
                    Math.toRadians(
                            getTrick5BoardFrameValue(
                                    context,
                                    context.trick5BoardFramePitchDegrees,
                                    0.0
                            )
                    );
            final double poseYaw =
                    Math.toRadians(
                            getTrick5BoardFrameValue(
                                    context,
                                    context.trick5BoardFrameYawDegrees,
                                    0.0
                            )
                    );
            final double poseRoll =
                    Math.toRadians(
                            getTrick5BoardFrameValue(
                                    context,
                                    context.trick5BoardFrameRollDegrees,
                                    0.0
                            )
                    );

            if (Math.abs(posePitch) > 0.000001) {
                SkateboardModelGeometry.rotateAroundX(
                        transformedData,
                        posePitch
                );
            }

            if (Math.abs(poseYaw) > 0.000001) {
                SkateboardModelGeometry.rotateAroundY(
                        transformedData,
                        poseYaw
                );
            }

            if (Math.abs(poseRoll) > 0.000001) {
                SkateboardModelGeometry.rotateAroundZ(
                        transformedData,
                        poseRoll
                );
            }
        }

        SkateboardModelGeometry.rotateForTerrain(
                transformedData,
                terrainPitch,
                terrainRoll
        );

        if (context.trickActive) {
            /*
             * Trick 5's board is now fully authored by its nine
             * player frames. XYZ is an absolute extra transform relative to the
             * normal centred trick anchor: 0/0/0 means the deck is under the
             * feet, and there is no automatic pop/catch/touchdown curve underneath
             * it. Tricks 1-4 keep their normal automatic pop system.
             */
            if (context.activeTrick != null
                    && context.activeTrick.getSlot() == 5) {

                final double targetX =
                        getTrick5BoardFrameValue(
                                context,
                                context.trick5BoardFrameX,
                                0.0
                        );
                final double targetHeight =
                        getTrick5BoardFrameValue(
                                context,
                                context.trick5BoardFrameY,
                                0.0
                        );
                final double targetZ =
                        getTrick5BoardFrameValue(
                                context,
                                context.trick5BoardFrameZ,
                                0.0
                        );

                transformedData.translate(
                        (int) Math.round(-targetX),
                        (int) Math.round(-targetHeight),
                        (int) Math.round(-targetZ)
                );
            } else {
                final int popHeight =
                        (int) Math.round(
                                Math.max(
                                        0,
                                        context.popHeight
                                )
                                        * getTrickBoardHeightMultiplier(
                                                context
                                        )
                        );

                final int groundClearance =
                        getTrickGroundClearance(
                                transformedData,
                                terrainPitch,
                                terrainRoll
                        );

                transformedData.translate(
                        0,
                        -(popHeight + groundClearance),
                        0
                );
            }
        }

        sceneController.setModel(
                transformedData.light()
        );
        sceneController.markTransformApplied(
                context.trickActive
        );
    }

    private double getTrick5BoardFrameValue(
            Context context,
            int[] values,
            double neutralValue) {

        if (context == null
                || !context.trickActive
                || context.activeTrick == null
                || context.activeTrick.getSlot() != 5
                || context.trick5BoardFrameCycles == null
                || values == null) {

            return neutralValue;
        }

        final int poseCount =
                Math.min(
                        context.trick5BoardFrameCycles.length,
                        values.length
                );

        if (poseCount <= 0) {
            return neutralValue;
        }

        final int mainElapsed =
                context.elapsedCycles
                        - context.preTransitionCycles;

        if (mainElapsed < 0) {
            return neutralValue;
        }

        int holdIndex = -1;
        if (context.trick5BoardFrameLocked != null) {
            final int lockCount =
                    Math.min(
                            poseCount,
                            context.trick5BoardFrameLocked.length
                    );

            for (int i = 0; i < lockCount; i++) {
                if (context.trick5BoardFrameLocked[i]) {
                    holdIndex = i;
                    break;
                }
            }
        }

        /*
         * Derive one outer bridge endpoint around the locked Christ Air hold.
         * Use the same endpoint on both sides so the Frame 4 entry and Frame 6
         * exit stay symmetric and the bridge boundaries remain continuous.
         */
        double legacyOuterValue = neutralValue;
        boolean hasLegacyOuter = false;

        if (holdIndex >= 0 && holdIndex + 1 < poseCount) {
            final int releaseIndex = holdIndex + 1;
            final int releaseDuration =
                    Math.max(
                            1,
                            context.trick5BoardFrameCycles[releaseIndex]
                    );
            final double releaseValue =
                    values[releaseIndex] == 0
                            ? neutralValue
                            : values[releaseIndex];
            final double followingValue =
                    releaseIndex + 1 < poseCount
                            ? (values[releaseIndex + 1] == 0
                                    ? neutralValue
                                    : values[releaseIndex + 1])
                            : neutralValue;

            final double oldRawProgress =
                    releaseDuration <= 1
                            ? 0.0
                            : (releaseDuration - 1)
                                    / (double) releaseDuration;

            if (oldRawProgress < 0.5) {
                final double heldValue =
                        values[holdIndex] == 0
                                ? neutralValue
                                : values[holdIndex];
                final double oldRecoveryBlend =
                        smootherStep(
                                clamp(
                                        oldRawProgress * 2.0,
                                        0.0,
                                        1.0
                                )
                        );

                legacyOuterValue =
                        lerp(
                                heldValue,
                                releaseValue,
                                oldRecoveryBlend
                        );
            } else {
                final double oldReleaseBlend =
                        smootherStep(
                                clamp(
                                        (oldRawProgress - 0.5) * 2.0,
                                        0.0,
                                        1.0
                                )
                        );

                legacyOuterValue =
                        lerp(
                                releaseValue,
                                followingValue,
                                oldReleaseBlend
                        );
            }

            hasLegacyOuter = true;
        }

        int cycle = 0;

        for (int i = 0; i < poseCount; i++) {
            final int duration =
                    Math.max(
                            1,
                            context.trick5BoardFrameCycles[i]
                    );

            final int nextCycle =
                    cycle + duration;

            if (mainElapsed < nextCycle) {
                final double from =
                        values[i] == 0
                                ? neutralValue
                                : values[i];

                /* A locked board frame is a true full-frame hold. */
                if (context.trick5BoardFrameLocked != null
                        && i < context.trick5BoardFrameLocked.length
                        && context.trick5BoardFrameLocked[i]) {
                    return from;
                }

                /*
                 * Hit both visible endpoints exactly. Using duration - 1 for
                 * the divisor prevents tiny jumps at frame boundaries.
                 */
                final int localCycle =
                        mainElapsed - cycle;
                final double linearBlend =
                        duration <= 1
                                ? 1.0
                                : clamp(
                                        localCycle
                                                / (double) (duration - 1),
                                        0.0,
                                        1.0
                                );
                final double blend =
                        smootherStep(linearBlend);

                if (hasLegacyOuter && holdIndex >= 2) {
                    final int preBridgeIndex = holdIndex - 1;
                    final int preOuterIndex = holdIndex - 2;
                    final int postBridgeIndex = holdIndex + 1;
                    final int postOuterIndex = holdIndex + 2;

                    /*
                     * The frame before the pre-Christ-Air bridge finishes at
                     * the same outer bridge point, so the next frame begins
                     * without a teleport.
                     */
                    if (i == preOuterIndex) {
                        return lerp(
                                from,
                                legacyOuterValue,
                                blend
                        );
                    }

                    /*
                     * Frame 4: outer bridge -> authored Frame 4 -> Christ Air.
                     * The authored Frame 4 transform remains a real waypoint.
                     */
                    if (i == preBridgeIndex) {
                        final double heldValue =
                                values[holdIndex] == 0
                                        ? neutralValue
                                        : values[holdIndex];

                        if (linearBlend < 0.5) {
                            return lerp(
                                    legacyOuterValue,
                                    from,
                                    smootherStep(
                                            clamp(
                                                    linearBlend * 2.0,
                                                    0.0,
                                                    1.0
                                            )
                                    )
                            );
                        }

                        return lerp(
                                from,
                                heldValue,
                                smootherStep(
                                        clamp(
                                                (linearBlend - 0.5) * 2.0,
                                                0.0,
                                                1.0
                                        )
                                )
                        );
                    }

                    /*
                     * Frame 6: Christ Air -> authored Frame 6 -> outer bridge.
                     * The final endpoint matches the Frame 4 side exactly.
                     */
                    if (i == postBridgeIndex) {
                        final double heldValue =
                                values[holdIndex] == 0
                                        ? neutralValue
                                        : values[holdIndex];

                        if (linearBlend < 0.5) {
                            return lerp(
                                    heldValue,
                                    from,
                                    smootherStep(
                                            clamp(
                                                    linearBlend * 2.0,
                                                    0.0,
                                                    1.0
                                            )
                                    )
                            );
                        }

                        return lerp(
                                from,
                                legacyOuterValue,
                                smootherStep(
                                        clamp(
                                                (linearBlend - 0.5) * 2.0,
                                                0.0,
                                                1.0
                                        )
                                )
                        );
                    }

                    /*
                     * Frame 7 starts from the same outer bridge point, reaches
                     * its authored transform halfway through, then continues to
                     * Frame 8 without a boundary teleport.
                     */
                    if (i == postOuterIndex) {
                        final double to =
                                i + 1 < poseCount
                                        ? (values[i + 1] == 0
                                                ? neutralValue
                                                : values[i + 1])
                                        : neutralValue;

                        if (linearBlend < 0.5) {
                            return lerp(
                                    legacyOuterValue,
                                    from,
                                    smootherStep(
                                            clamp(
                                                    linearBlend * 2.0,
                                                    0.0,
                                                    1.0
                                            )
                                    )
                            );
                        }

                        return lerp(
                                from,
                                to,
                                smootherStep(
                                        clamp(
                                                (linearBlend - 0.5) * 2.0,
                                                0.0,
                                                1.0
                                        )
                                )
                        );
                    }
                }

                final double to;
                if (i + 1 < poseCount) {
                    to =
                            values[i + 1] == 0
                                    ? neutralValue
                                    : values[i + 1];
                } else {
                    to = neutralValue;
                }

                return lerp(
                        from,
                        to,
                        blend
                );
            }

            cycle = nextCycle;
        }

        return neutralValue;
    }

    private int getTrickGroundClearance(
            ModelData transformedData,
            double terrainPitch,
            double terrainRoll) {

        if (transformedData == null) {
            return 0;
        }

        final ModelData restingReference =
                sceneController.createWorkingModelData();

        if (restingReference == null) {
            return 0;
        }

        SkateboardModelGeometry.rotateForTerrain(
                restingReference,
                terrainPitch,
                terrainRoll
        );

        final float restingLowestY =
                SkateboardModelGeometry.getMaxVertexY(
                        restingReference
                );

        final float trickLowestY =
                SkateboardModelGeometry.getMaxVertexY(
                        transformedData
                );

        final double extraDownwardReach =
                trickLowestY - restingLowestY;

        if (extraDownwardReach <= 0.001) {
            return 0;
        }

        return Math.max(
                0,
                (int) Math.ceil(extraDownwardReach)
                        + TRICK_GROUND_CLEARANCE
        );
    }

    private void applyTrick4EdgeOnShoveImpossible(
            ModelData modelData,
            double impossibleProgress,
            double shoveProgress,
            double impossibleTurns,
            double impossibleDirection,
            double pathXOffset,
            double pathYOffset,
            double pathZOffset) {

        final double p = clamp(
                impossibleProgress,
                0.0,
                1.0
        );

        final double edgeAmount;

        if (p < TRICK4_IMPOSSIBLE_EDGE_RAMP_END) {
            edgeAmount = smootherStep(
                    p / TRICK4_IMPOSSIBLE_EDGE_RAMP_END
            );
        } else if (p <= TRICK4_IMPOSSIBLE_EDGE_HOLD_END) {
            edgeAmount = 1.0;
        } else {
            edgeAmount = 1.0 - smootherStep(
                    (p - TRICK4_IMPOSSIBLE_EDGE_HOLD_END)
                            / (1.0 - TRICK4_IMPOSSIBLE_EDGE_HOLD_END)
            );
        }

        final double edgeAngle =
                Math.toRadians(
                        TRICK4_IMPOSSIBLE_EDGE_DEGREES
                                * edgeAmount
                                * impossibleDirection
                );

        if (Math.abs(edgeAngle) > 0.000001) {
            SkateboardModelGeometry.rotateAroundZ(
                    modelData,
                    edgeAngle
            );
        }

        final double shoveAngle =
                Math.PI * 2.0
                        * clamp(shoveProgress, 0.0, 1.0)
                        * impossibleTurns
                        * impossibleDirection;

        if (Math.abs(shoveAngle) > 0.000001) {
            SkateboardModelGeometry.rotateAroundY(
                    modelData,
                    shoveAngle
            );
        }

        final double tuneEnvelope =
                Math.sin(Math.PI * p);

        final double offsetX =
                pathXOffset * tuneEnvelope;
        final double offsetY =
                pathYOffset * tuneEnvelope;
        final double offsetZ =
                pathZOffset * tuneEnvelope;

        if (Math.abs(offsetX) <= 0.000001
                && Math.abs(offsetY) <= 0.000001
                && Math.abs(offsetZ) <= 0.000001) {
            return;
        }

        final float[] verticesX = modelData.getVerticesX();
        final float[] verticesY = modelData.getVerticesY();
        final float[] verticesZ = modelData.getVerticesZ();

        for (int i = 0; i < verticesX.length; i++) {
            verticesX[i] += (float) offsetX;
            verticesY[i] += (float) offsetY;
            verticesZ[i] += (float) offsetZ;
        }
    }

    private double getIndependentBoardPopHeightMultiplier(
            Context context,
            int absoluteCycle) {

        if (context.popEndCycleOffset
                <= context.popStartCycleOffset) {

            return 0.0;
        }

        final double motionProgress =
                clamp(
                        (absoluteCycle
                                - context.popStartCycleOffset)
                                / (double) (
                                        context.popEndCycleOffset
                                                - context.popStartCycleOffset
                                ),
                        0.0,
                        1.0
                );

        final double easedProgress =
                smootherStep(motionProgress);

        return Math.max(
                0.0,
                Math.sin(
                        Math.PI
                                * easedProgress
                )
        );
    }

    private double getTrickBoardHeightMultiplier(
            Context context) {

        if (!context.trickActive) {
            return 0.0;
        }

        if (context.elapsedCycles
                <= context.popStartCycleOffset) {

            return 0.0;
        }

        if (context.elapsedCycles
                >= context.popEndCycleOffset) {

            return 0.0;
        }

        if (context.elapsedCycles
                < context.catchCycleOffset) {

            return getIndependentBoardPopHeightMultiplier(
                    context,
                    context.elapsedCycles
            );
        }

        final double catchHeight =
                getIndependentBoardPopHeightMultiplier(
                        context,
                        context.catchCycleOffset
                );

        return catchHeight
                * getCaughtBoardDescentMultiplier(
                        context
                );
    }

    private double getCaughtBoardDescentMultiplier(
            Context context) {

        final int catchCycle =
                context.catchCycleOffset;

        final int touchdownCycle =
                context.popEndCycleOffset;

        if (touchdownCycle <= catchCycle) {
            return 0.0;
        }

        final int frame9Cycle =
                context.frame9AbsoluteCycle;

        final int frame10Cycle =
                context.frame10AbsoluteCycle;

        if (frame9Cycle <= catchCycle
                || frame10Cycle <= frame9Cycle
                || touchdownCycle <= frame10Cycle) {

            return smoothInterpolate(
                    1.0,
                    0.0,
                    getTrickPhaseProgress(
                            context,
                            catchCycle,
                            touchdownCycle
                    )
            );
        }

        if (context.elapsedCycles < frame9Cycle) {
            return smoothInterpolate(
                    1.0,
                    0.78,
                    getTrickPhaseProgress(
                            context,
                            catchCycle,
                            frame9Cycle
                    )
            );
        }

        if (context.elapsedCycles < frame10Cycle) {
            return smoothInterpolate(
                    0.78,
                    0.42,
                    getTrickPhaseProgress(
                            context,
                            frame9Cycle,
                            frame10Cycle
                    )
            );
        }

        return smoothInterpolate(
                0.42,
                0.0,
                getTrickPhaseProgress(
                        context,
                        frame10Cycle,
                        touchdownCycle
                )
        );
    }

    private void applyTrick4BoardRotation(
            ModelData transformedData,
            Context context) {

        final double mainProgress =
                getTrick4MainProgress(context);

        final double kickflipStart =
                getMainCycleTimelineProgress(
                        context,
                        context.kickStartMainCycle
                );

        final double catchPoint =
                getMainCycleTimelineProgress(
                        context,
                        context.catchMainCycle
                );

        final double impossibleStart =
                clamp(
                        getMainCycleTimelineProgress(
                                context,
                                context.impossibleStartMainCycle
                        ),
                        0.0,
                        1.0
                );

        final double kickflipEnd =
                clamp(
                        getMainCycleTimelineProgress(
                                context,
                                context.kickEndMainCycle
                        ),
                        0.0,
                        1.0
                );

        final double kickflipProgress =
                getSubPhaseProgress(
                        mainProgress,
                        kickflipStart,
                        kickflipEnd
                );

        final double kickflipSpin =
                smootherStep(
                        kickflipProgress
                );

        if (kickflipSpin > 0.0) {
            SkateboardModelGeometry.rotateAroundZ(
                    transformedData,
                    Math.PI * 2.0
                            * (context.kickflipDegrees / 360.0)
                            * kickflipSpin
            );
        }

        if (mainProgress < kickflipEnd) {
            final double kickflipPitch =
                    getClassicTrickBoardPitch(
                            kickflipProgress
                    );

            if (Math.abs(kickflipPitch) > 0.000001) {
                SkateboardModelGeometry.rotateAroundX(
                        transformedData,
                        kickflipPitch
                );
            }
        }

        final double impossibleProgress =
                getSubPhaseProgress(
                        mainProgress,
                        impossibleStart,
                        catchPoint
                );

        final double impossibleSpin =
                smootherStep(
                        impossibleProgress
                );

        if (impossibleSpin > 0.0) {
            final double impossibleDirection = -1.0;

            applyTrick4EdgeOnShoveImpossible(
                    transformedData,
                    impossibleProgress,
                    impossibleSpin,
                    context.impossibleDegrees / 360.0,
                    impossibleDirection,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    private double getTrick4MainProgress(
            Context context) {

        if (!context.trickActive
                || context.activeTrick == null
                || context.activeTrick.getSlot() != 4) {

            return 0.0;
        }

        final int mainStart =
                context.preTransitionCycles;

        final int mainEnd =
                mainStart
                        + getBoardMainSequenceCycles(
                                context
                        );

        return getTrickPhaseProgress(
                context,
                mainStart,
                mainEnd
        );
    }

    private double getMainCycleTimelineProgress(
            Context context,
            int configuredMainCycle) {

        final int mainCycles =
                Math.max(
                        1,
                        getBoardMainSequenceCycles(
                                context
                        )
                );

        return clampInt(
                configuredMainCycle,
                0,
                mainCycles
        ) / (double) mainCycles;
    }

    private double getSubPhaseProgress(
            double mainProgress,
            double start,
            double end) {

        if (end <= start) {
            return 0.0;
        }

        return clamp(
                (mainProgress - start)
                        / (end - start),
                0.0,
                1.0
        );
    }

    private double getVarialShoveProgress(
            Context context) {

        final int boardMainCycles =
                Math.max(
                        1,
                        getBoardMainSequenceCycles(
                                context
                        )
                );

        final int shoveStart =
                context.preTransitionCycles
                        + clampInt(
                                context.shoveStartMainCycle,
                                0,
                                boardMainCycles
                        );

        final int shoveEnd =
                context.preTransitionCycles
                        + clampInt(
                                context.shoveEndMainCycle,
                                0,
                                boardMainCycles
                        );

        return getTrickPhaseProgress(
                context,
                shoveStart,
                shoveEnd
        );
    }

    private double getTrickPhaseProgress(
            Context context,
            int startCycle,
            int endCycle) {

        return clamp(
                (context.elapsedCycles - startCycle)
                        / (double) Math.max(
                                1,
                                endCycle - startCycle
                        ),
                0.0,
                1.0
        );
    }

    private int getBoardMainSequenceCycles(
            Context context) {

        if (context.boardMainSequenceCycles > 0) {
            return context.boardMainSequenceCycles;
        }

        return Math.max(
                1,
                context.mainSequenceCycles
        );
    }

    private double getTrickRotationProgress(
            double value,
            TrickDefinition activeTrick) {

        if (activeTrick == null) {
            return 0.0;
        }

        switch (activeTrick.getRotationProfile()) {
            case SCOOPED_SHOVE:
                return shoveSpinCurve(value);

            case CLASSIC_FLIP:
            default:
                return trickSpinCurve(value);
        }
    }

    private double trickSpinCurve(
            double value) {

        return momentumLossSpinCurve(
                value,
                0.70
        );
    }

    private double shoveSpinCurve(
            double value) {

        return momentumLossSpinCurve(
                value,
                0.80
        );
    }

    private double momentumLossSpinCurve(
            double value,
            double braking) {

        final double progress =
                clamp(value, 0.0, 1.0);

        final double brakeStrength =
                clamp(braking, 0.0, 0.95);

        return clamp(
                progress
                        + brakeStrength
                                * progress
                                * (1.0 - progress),
                0.0,
                1.0
        );
    }

    private double getTrickBoardPitch(
            double value,
            TrickDefinition activeTrick) {

        if (activeTrick == null) {
            return 0.0;
        }

        switch (activeTrick.getPitchProfile()) {
            case FLAT_SHOVE:
                return getShoveBoardPitch(value);

            case NONE:
                return 0.0;

            case CLASSIC_FLIP:
            default:
                return getClassicTrickBoardPitch(value);
        }
    }

    private double getClassicTrickBoardPitch(
            double value) {

        final double progress =
                clamp(value, 0.0, 1.0);

        if (progress <= 0.15) {
            return smoothInterpolate(
                    0.0,
                    TRICK_NOSE_UP_ANGLE,
                    progress / 0.15
            );
        }

        if (progress <= 0.50) {
            return smoothInterpolate(
                    TRICK_NOSE_UP_ANGLE,
                    TRICK_APEX_ANGLE,
                    (progress - 0.15) / 0.35
            );
        }

        if (progress <= 0.78) {
            return smoothInterpolate(
                    TRICK_APEX_ANGLE,
                    TRICK_CATCH_ANGLE,
                    (progress - 0.50) / 0.28
            );
        }

        return smoothInterpolate(
                TRICK_CATCH_ANGLE,
                0.0,
                (progress - 0.78) / 0.22
        );
    }

    private double getShoveBoardPitch(
            double value) {

        final double progress =
                clamp(value, 0.0, 1.0);

        if (progress <= 0.12) {
            return smoothInterpolate(
                    0.0,
                    SHOVE_SCOOP_ANGLE,
                    progress / 0.12
            );
        }

        if (progress <= 0.36) {
            return smoothInterpolate(
                    SHOVE_SCOOP_ANGLE,
                    SHOVE_APEX_ANGLE,
                    (progress - 0.12) / 0.24
            );
        }

        if (progress <= 0.68) {
            return smoothInterpolate(
                    SHOVE_APEX_ANGLE,
                    0.0,
                    (progress - 0.36) / 0.32
            );
        }

        if (progress <= 0.82) {
            return smoothInterpolate(
                    0.0,
                    SHOVE_CATCH_ANGLE,
                    (progress - 0.68) / 0.14
            );
        }

        return smoothInterpolate(
                SHOVE_CATCH_ANGLE,
                0.0,
                (progress - 0.82) / 0.18
        );
    }

    private double smoothInterpolate(
            double start,
            double end,
            double progress) {

        final double eased =
                smootherStep(progress);

        return start
                + (end - start) * eased;
    }

    private double smootherStep(
            double value) {

        final double t =
                clamp(value, 0.0, 1.0);

        return t * t * t
                * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private int getRepeatedListValue(
            int[] values,
            int index) {

        if (values == null
                || values.length == 0) {

            return 0;
        }

        return values[
                Math.min(
                        Math.max(
                                0,
                                index
                        ),
                        values.length - 1
                )
        ];
    }

    private double lerp(
            double start,
            double end,
            double progress) {

        return start
                + (end - start)
                        * clamp(
                                progress,
                                0.0,
                                1.0
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

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}
