package com.skatescape;

import java.util.Arrays;
import net.runelite.api.Animation;

final class TrickTimelineBuilder {
    private final SkateScapePlugin host;

    TrickTimelineBuilder(SkateScapePlugin host) {
        this.host = host;
    }

    void prepare(
            Animation animation,
            TrickDefinition definition) {
        host.trickActivePlayerAnimationIds = null;
        host.trickActivePlayerFrames = null;
        host.trickActivePlayerPoseCycles = null;

        final int trickSlot =
                definition == null
                        ? 1
                        : clampInt(definition.getSlot(), 1, 5);

        host.trickActivePreFrames =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_PRE_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_PRE_FRAMES.length
                );

        host.trickActivePreBoardPositions =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_PRE_BOARD_POSITIONS,
                        SkateScapePlugin.DEFAULT_TRICK_PRE_BOARD_POSITIONS.length
                );

        final int[] activePreDurationsMs =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_PRE_DURATIONS_MS,
                        SkateScapePlugin.DEFAULT_TRICK_PRE_DURATIONS_MS.length
                );

        /*
         * Tricks 1-3 use shared 1603 choreography. Tricks 4 and 5 own separate
         * per-trick MAIN pose sequences.
         */
        host.trickActivePopStartFrame =
                SkateScapePlugin.DEFAULT_TRICK_POP_START_FRAME;
        host.trickActiveFlipStartFrame =
                SkateScapePlugin.DEFAULT_TRICK_FLIP_START_FRAME;
        host.trickActiveFlipEndFrame =
                SkateScapePlugin.DEFAULT_TRICK_FLIP_END_FRAME;
        host.trickActivePopEndFrame =
                SkateScapePlugin.DEFAULT_TRICK_POP_END_FRAME;

        host.trickActiveJumpFrames =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_JUMP_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_JUMP_FRAMES.length
                );

        /*
         * Trick 4 player-pose tuning is independent from Tricks 1-3. The
         * shared 1603 timeline below remains only as the board timing clock;
         * it is not Trick 4's visible MAIN player animation.
         */
        host.trickActivePostSeamFrame =
                SkateScapePlugin.DEFAULT_TRICK_POST_SEAM_FRAME;

        final int activePostSeamDurationMs =
                SkateScapePlugin.DEFAULT_TRICK_POST_SEAM_DURATION_MS;

        host.trickActivePostFrames =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_POST_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_POST_FRAMES.length
                );

        host.trickActivePostBoardPositions =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_POST_BOARD_POSITIONS,
                        SkateScapePlugin.DEFAULT_TRICK_POST_BOARD_POSITIONS.length
                );

        final int[] activePostDurationsMs =
                Arrays.copyOf(
                        SkateScapePlugin.DEFAULT_TRICK_POST_DURATIONS_MS,
                        SkateScapePlugin.DEFAULT_TRICK_POST_DURATIONS_MS.length
                );

        final int[] nativeFrameLengths =
                animation.getFrameLengths();

        if (nativeFrameLengths == null
                || nativeFrameLengths.length == 0) {

            host.trickFrameLengths = null;
            host.trickTotalCycles = 0;
            return;
        }

        /*
         * Copy 1603's native timings. The shared Animation frame-length array
         * must remain untouched.
         */
        host.trickFrameLengths =
                Arrays.copyOf(
                        nativeFrameLengths,
                        nativeFrameLengths.length
                );

        int nativeTotalCycles = 0;

        for (int frameLength
                : host.trickFrameLengths) {

            nativeTotalCycles +=
                    Math.max(
                            1,
                            frameLength
                    );
        }

        nativeTotalCycles =
                Math.max(
                        1,
                        nativeTotalCycles
                );

        /*
         * Trick duration reads from the selected trick's saved tuning. PRE,
         * the landing seam and RETURN remain additional choreography outside
         * the MAIN window.
         */
        final int configuredTargetCycles =
                Math.max(
                        1,
                        (int) Math.round(
                                Math.max(
                                        300,
                                        host.getTuningInt(
                                                trickSlot,
                                                "durationMs"
                                        )
                                ) / 20.0
                        )
                );

        /*
         * Every visible pose/frame consumes at least one client cycle. Keep
         * the single master clock large enough to contain the selected
         * choreography even if stored config sits below the normal 400 ms UI
         * minimum. This avoids a hidden player-only clock.
         */
        int minimumMainCycles =
                Math.max(
                        1,
                        host.trickActiveJumpFrames.length
                );

        if (definition != null
                && (trickSlot == 4
                        || !host.usesLegacyPlayerPose(trickSlot))) {

            minimumMainCycles =
                    Math.max(
                            minimumMainCycles,
                            clampInt(
                                    host.getPoseLength(trickSlot),
                                    1,
                                    host.maxPoseSequenceLength(trickSlot)
                            )
                    );
        }

        final int targetCycles =
                Math.max(
                        configuredTargetCycles,
                        minimumMainCycles
                );

        final double scale =
                targetCycles
                        / (double) nativeTotalCycles;

        for (int i = 0;
                i < host.trickFrameLengths.length;
                i++) {

            host.trickFrameLengths[i] =
                    Math.max(
                            1,
                            (int) Math.round(
                                    host.trickFrameLengths[i]
                                            * scale
                            )
                    );
        }

                final int fullAnimationCycles =
                getTotalAnimationCycles(
                        host.trickFrameLengths
                );
        host.trickPreFrameCycles =
                buildTransitionFrameCycles(
                        host.trickActivePreFrames,
                        activePreDurationsMs
                );

        host.trickPreTransitionCycles =
                getTotalAnimationCycles(
                        host.trickPreFrameCycles
                );

        /*
         * SHARED MAIN TIMING BASELINE.
         *
         * The 1603 frame clock provides the default Tricks 1-3 pose sequence
         * and the shared board-timing baseline:
         *
         *   0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 12
         *
         * Frame 11 is not part of that configured sequence.
         */
        buildActiveTrickJumpTimeline();

        /*
         * ONE MASTER MAIN CLOCK.
         *
         * Main trick duration owns the whole MAIN window. The board timeline,
         * normal player pose timing follow this target. Advanced Frame Timing
         * may instead supply literal per-pose milliseconds.
         */
        normalizeCycleArrayToTotal(
                host.trickActiveJumpFrameCycles,
                targetCycles
        );

        host.trickBoardMainSequenceCycles =
                getTotalAnimationCycles(
                        host.trickActiveJumpFrameCycles
                );

        host.trickPlayerMainSequenceCycles =
                host.trickBoardMainSequenceCycles;

        if (definition != null
                && (trickSlot == 4
                        || !host.usesLegacyPlayerPose(trickSlot))) {

            preparePerTrickPlayerPoseTimeline(
                    targetCycles,
                    trickSlot
            );

            host.trickPlayerMainSequenceCycles =
                    Math.max(
                            1,
                            getTotalAnimationCycles(
                                    host.trickActivePlayerPoseCycles
                            )
                    );
        }

        /*
         * Keep both prepared clocks on the same master duration. The max is a
         * defensive guard against malformed/custom data; normally both values
         * are exactly targetCycles.
         */
        host.trickMainSequenceCycles =
                Math.max(
                        host.trickBoardMainSequenceCycles,
                        host.trickPlayerMainSequenceCycles
                );

        /*
         * 1708/27 landing seam.
         *
         * The board remains centred for this entire seam.
         */
        host.trickPostSeamCycles =
                Math.max(
                        1,
                        (int) Math.round(
                                activePostSeamDurationMs
                                        / 20.0
                        )
                );

        /*
         * POST-JUMP 1708 return / balance transition.
         *
         * This begins after MAIN and the brief 1708/27 landing seam.
         */
        host.trickPostFrameCycles =
                buildTransitionFrameCycles(
                        host.trickActivePostFrames,
                        activePostDurationsMs
                );

        host.trickPostTransitionCycles =
                getTotalAnimationCycles(
                        host.trickPostFrameCycles
                );

        host.trickTotalCycles =
                host.trickPreTransitionCycles
                        + host.trickMainSequenceCycles
                        + host.trickPostSeamCycles
                        + host.trickPostTransitionCycles;

        /*
         * BOARD TIMELINE.
         *
         * Current release defaults use percentage-based landmarks. Existing
         * configs that still carry the legacy board-timing flag keep their
         * frame-derived landmarks for compatibility.
         */
        if (host.usesApprovedLegacyBoardTiming(trickSlot)) {
            host.trickActivePopStartFrame =
                    SkateScapePlugin.DEFAULT_TRICK_POP_START_FRAME;

            host.trickActiveFlipStartFrame =
                    SkateScapePlugin.DEFAULT_TRICK_FLIP_START_FRAME;

            host.trickActiveFlipEndFrame = 8;

            host.trickActivePopEndFrame =
                    SkateScapePlugin.DEFAULT_TRICK_POP_END_FRAME;

            host.trickPopStartCycleOffset =
                    host.trickPreTransitionCycles
                            + getCycleOffsetAtActiveTrickFrame(
                                    host.trickActivePopStartFrame
                            );

            host.trickFlipStartCycleOffset =
                    host.trickPreTransitionCycles
                            + getCycleOffsetAtActiveTrickFrame(
                                    host.trickActiveFlipStartFrame
                            );

            host.trickFlipEndCycleOffset =
                    host.trickPreTransitionCycles
                            + getCycleOffsetAfterActiveTrickFrame(
                                    host.trickActiveFlipEndFrame
                            );

            host.trickCatchCycleOffset =
                    host.trickFlipEndCycleOffset;

            host.trickPopEndCycleOffset =
                    host.trickPreTransitionCycles
                            + getCycleOffsetAtActiveTrickFrame(
                                    host.trickActivePopEndFrame
                            );

            /*
             * Store the resolved frame-derived cycles so the editor shows the
             * values used by the runtime.
             */
            host.storeExactLegacyBoardCycles(trickSlot);
        } else {
            /*
             * Custom editor mode: every landmark is independent and may
             * overlap any other landmark exactly as requested.
             */
            final int boardMainCycles =
                    Math.max(
                            1,
                            host.trickBoardMainSequenceCycles
                    );

            final int popStartMainCycle =
                    percentageToMainCycle(
                            host.getTuningPercent(trickSlot, "popStart"),
                            boardMainCycles
                    );

            final int primaryStartMainCycle =
                    percentageToMainCycle(
                            host.getTuningPercent(trickSlot, "kickStart"),
                            boardMainCycles
                    );

            final int primaryEndMainCycle =
                    percentageToMainCycle(
                            host.getTuningPercent(trickSlot, "kickEnd"),
                            boardMainCycles
                    );

            final int catchMainCycle =
                    percentageToMainCycle(
                            host.getTuningPercent(trickSlot, "catch"),
                            boardMainCycles
                    );

            final int touchdownMainCycle =
                    percentageToMainCycle(
                            host.getTuningPercent(trickSlot, "touchdown"),
                            boardMainCycles
                    );

            host.trickPopStartCycleOffset =
                    host.trickPreTransitionCycles
                            + popStartMainCycle;

            host.trickFlipStartCycleOffset =
                    host.trickPreTransitionCycles
                            + primaryStartMainCycle;

            host.trickFlipEndCycleOffset =
                    host.trickPreTransitionCycles
                            + primaryEndMainCycle;

            host.trickCatchCycleOffset =
                    host.trickPreTransitionCycles
                            + catchMainCycle;

            host.trickPopEndCycleOffset =
                    host.trickPreTransitionCycles
                            + touchdownMainCycle;
        }

        /*
         * Tricks 1-3 use the 1603 POSE-layer path. Trick 4 may prepare natural
         * 2890 MAIN playback on the ACTION layer, while Trick 5's mixed
         * animation sequence remains on the POSE layer.
         */
        if (definition != null && definition.getSlot() == 4) {
            host.prepareNaturalMainTrickPlayback(definition);
        } else {
            host.restoreNaturalMainTrickTiming();
        }
    }

    private int[] parseConfiguredIntList(
            String configuredValue,
            int[] fallback,
            int minimum,
            int maximum) {

        if (configuredValue == null
                || configuredValue.trim().isEmpty()) {

            return Arrays.copyOf(
                    fallback,
                    fallback.length
            );
        }

        final String[] pieces =
                configuredValue.split(",");

        if (pieces.length == 0) {
            return Arrays.copyOf(
                    fallback,
                    fallback.length
            );
        }

        final int[] parsed =
                new int[pieces.length];

        try {
            for (int i = 0;
                    i < pieces.length;
                    i++) {

                final int value =
                        Integer.parseInt(
                                pieces[i].trim()
                        );

                if (value < minimum
                        || value > maximum) {

                    return Arrays.copyOf(
                            fallback,
                            fallback.length
                    );
                }

                parsed[i] = value;
            }
        } catch (NumberFormatException ex) {
            return Arrays.copyOf(
                    fallback,
                    fallback.length
            );
        }

        return parsed;
    }

    private double[] parseConfiguredDoubleList(
            String configured,
            double[] fallback,
            double min,
            double max) {

        if (configured == null || configured.trim().isEmpty()) {
            return Arrays.copyOf(fallback, fallback.length);
        }

        final String[] pieces = configured.split(",");
        final double[] parsed = new double[pieces.length];

        try {
            for (int i = 0; i < pieces.length; i++) {
                final double value = Double.parseDouble(pieces[i].trim());
                if (!Double.isFinite(value) || value < min || value > max) {
                    return Arrays.copyOf(fallback, fallback.length);
                }
                parsed[i] = value;
            }
        } catch (NumberFormatException ignored) {
            return Arrays.copyOf(fallback, fallback.length);
        }

        return parsed.length == 0
                ? Arrays.copyOf(fallback, fallback.length)
                : parsed;
    }

    private int[] parsePoseTimingMs(
            String configured,
            int expectedCount) {

        if (configured == null
                || configured.trim().isEmpty()
                || expectedCount <= 0) {
            return null;
        }

        final String[] pieces = configured.split(",");
        if (pieces.length != expectedCount) {
            return null;
        }

        final int[] parsed = new int[pieces.length];
        try {
            for (int i = 0; i < pieces.length; i++) {
                final int value = Integer.parseInt(pieces[i].trim());

                if (value <= 0 || value > 20000) {
                    return null;
                }

                parsed[i] = value;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }

        return parsed;
    }

    private int percentageToMainCycle(
            double percent,
            int totalCycles) {

        final int safeTotal = Math.max(1, totalCycles);
        final double safePercent =
                Math.max(0.0, Math.min(100.0, percent));

        return clampInt(
                (int) Math.round(
                        safePercent * safeTotal / 100.0
                ),
                0,
                safeTotal
        );
    }

    private int[] buildTransitionFrameCycles(
            int[] frames,
            int[] durationsMs) {

        final int[] cycles =
                new int[
                        frames.length
                ];

        for (int i = 0;
                i < frames.length;
                i++) {

            final int durationMs =
                    durationsMs[
                            Math.min(
                                    i,
                                    durationsMs.length - 1
                            )
                    ];

            cycles[i] =
                    Math.max(
                            1,
                            (int) Math.round(
                                    durationMs / 20.0
                            )
                    );
        }

        return cycles;
    }

    private void preparePerTrickPlayerPoseTimeline(
            int targetCycles,
            int trickSlot) {

        final int safeSlot =
                clampInt(trickSlot, 1, 5);

        final int sequenceLength =
                clampInt(
                        host.getPoseLength(safeSlot),
                        1,
                        host.maxPoseSequenceLength(safeSlot)
                );

        final boolean trick4 =
                safeSlot == 4;

        final int fallbackAnimationId =
                trick4
                        ? SkateScapePlugin.TRICK4_DEFAULT_PLAYER_ANIMATION
                        : SkateScapePlugin.TRICK_PLAYER_ANIMATION;

        final int[] fallbackFrames =
                trick4
                        ? SkateScapePlugin.TRICK4_DEFAULT_PLAYER_FRAMES
                        : SkateScapePlugin.DEFAULT_TRICK_JUMP_FRAMES;

        final int[] configuredAnimationIds =
                parseConfiguredIntList(
                        host.getPoseAnimations(safeSlot),
                        new int[]{
                                fallbackAnimationId
                        },
                        0,
                        SkateScapePlugin.MAX_SAFE_ANIMATION_ID
                );

        final int[] configuredFrames =
                parseConfiguredIntList(
                        host.getPoseFrames(safeSlot),
                        fallbackFrames,
                        0,
                        200
                );

        final int[] configuredPoseTimingMs =
                parsePoseTimingMs(
                        host.getPoseTimingMs(safeSlot),
                        sequenceLength
                );

        final boolean advancedTiming =
                host.getAdvancedPoseTiming(safeSlot);

        final boolean literalPoseTiming =
                advancedTiming
                        && configuredPoseTimingMs != null;

        host.trickActivePlayerAnimationIds =
                new int[sequenceLength];

        host.trickActivePlayerFrames =
                new int[sequenceLength];

        host.trickActivePlayerPoseCycles =
                new int[sequenceLength];

        for (int i = 0;
                i < sequenceLength;
                i++) {

            final int fallbackFrame =
                    fallbackFrames[
                            Math.min(
                                    i,
                                    fallbackFrames.length - 1
                            )
                    ];

            int animationId =
                    getRepeatedListValue(
                            configuredAnimationIds,
                            i
                    );

            Animation poseAnimation =
                    host.loadSafeAnimation(
                            animationId
                    );

            if (poseAnimation == null) {
                animationId =
                        fallbackAnimationId;

                poseAnimation =
                        host.loadSafeAnimation(
                                animationId
                        );
            }

            int frame =
                    getRepeatedListValue(
                            configuredFrames,
                            i
                    );

            if (poseAnimation == null) {
                animationId = SkateScapePlugin.MOVING_SKATE_ANIMATION;
                poseAnimation = host.loadSafeAnimation(animationId);
                frame = 0;
            } else {
                frame =
                        clampInt(
                                frame,
                                0,
                                host.getSafeMaxFrame(
                                        poseAnimation
                                )
                        );
            }

            host.trickActivePlayerAnimationIds[i] =
                    animationId;

            host.trickActivePlayerFrames[i] =
                    frame;

            if (literalPoseTiming) {
                host.trickActivePlayerPoseCycles[i] =
                        Math.max(
                                1,
                                (int) Math.round(
                                        configuredPoseTimingMs[i]
                                                / 20.0
                                )
                        );
            } else {
                /*
                 * Normal mode inherits the shared 1603 timing shape by
                 * pose position, then scales the selected sequence to the
                 * selected trick's configured MAIN duration.
                 */
                host.trickActivePlayerPoseCycles[i] =
                        Math.max(
                                1,
                                host.trickActiveJumpFrameCycles[
                                        Math.min(
                                                i,
                                                host.trickActiveJumpFrameCycles.length - 1
                                        )
                                ]
                        );
            }
        }

        /*
         * Valid Advanced Frame Timing is literal milliseconds. Do not silently
         * normalize those values back to Trick duration. Blank, invalid or
         * incomplete lists fall back to normal fitted timing.
         */
        if (!literalPoseTiming) {
            normalizeCycleArrayToTotal(
                    host.trickActivePlayerPoseCycles,
                    targetCycles
            );
        }
    }

    private void normalizeCycleArrayToTotal(
            int[] cycles,
            int targetTotal) {

        if (cycles == null
                || cycles.length == 0) {

            return;
        }

        final int safeTarget =
                Math.max(
                        cycles.length,
                        targetTotal
                );

        int sourceTotal = 0;

        for (int cycle : cycles) {
            sourceTotal +=
                    Math.max(
                            1,
                            cycle
                    );
        }

        sourceTotal =
                Math.max(
                        1,
                        sourceTotal
                );

        int scaledTotal = 0;

        for (int i = 0;
                i < cycles.length;
                i++) {

            cycles[i] =
                    Math.max(
                            1,
                            (int) Math.round(
                                    Math.max(
                                            1,
                                            cycles[i]
                                    )
                                            * safeTarget
                                            / (double) sourceTotal
                            )
                    );

            scaledTotal += cycles[i];
        }

        int difference =
                safeTarget - scaledTotal;

        int cursor = 0;
        int guard = 0;

        while (difference != 0
                && guard < 10000) {

            final int index =
                    cursor % cycles.length;

            if (difference > 0) {
                cycles[index]++;
                difference--;
            } else if (cycles[index] > 1) {
                cycles[index]--;
                difference++;
            }

            cursor++;
            guard++;
        }
    }

    private void buildActiveTrickJumpTimeline() {
        if (host.trickActiveJumpFrames == null
                || host.trickActiveJumpFrames.length == 0) {

            host.trickActiveJumpFrames =
                    SkateScapePlugin.DEFAULT_TRICK_JUMP_FRAMES.clone();
        }

        host.trickActiveJumpFrameCycles =
                new int[
                        host.trickActiveJumpFrames.length
                ];

        for (int i = 0;
                i < host.trickActiveJumpFrames.length;
                i++) {

            final int nativeFrame =
                    host.trickActiveJumpFrames[i];

            if (nativeFrame < 0
                    || nativeFrame >= host.trickFrameLengths.length) {

                /*
                 * Should not happen because config parsing clamps 0..12,
                 * but keep the timeline safe if the cache ever changes.
                 */
                host.trickActiveJumpFrameCycles[i] = 1;
                continue;
            }

            host.trickActiveJumpFrameCycles[i] =
                    Math.max(
                            1,
                            host.trickFrameLengths[nativeFrame]
                    );
        }
    }

    int getCycleOffsetAfterActiveTrickFrame(
            int nativeFrame) {

        if (host.trickActiveJumpFrames == null
                || host.trickActiveJumpFrameCycles == null
                || host.trickActiveJumpFrames.length == 0) {

            return 0;
        }

        int cycleOffset = 0;

        for (int i = 0;
                i < host.trickActiveJumpFrames.length;
                i++) {

            cycleOffset +=
                    Math.max(
                            1,
                            host.trickActiveJumpFrameCycles[i]
                    );

            if (host.trickActiveJumpFrames[i]
                    >= nativeFrame) {

                return cycleOffset;
            }
        }

        return cycleOffset;
    }

    int getCycleOffsetAtActiveTrickFrame(
            int nativeFrame) {

        if (host.trickActiveJumpFrames == null
                || host.trickActiveJumpFrameCycles == null
                || host.trickActiveJumpFrames.length == 0) {

            return 0;
        }

        int cycleOffset = 0;

        for (int i = 0;
                i < host.trickActiveJumpFrames.length;
                i++) {

            if (host.trickActiveJumpFrames[i]
                    >= nativeFrame) {

                return cycleOffset;
            }

            cycleOffset +=
                    Math.max(
                            1,
                            host.trickActiveJumpFrameCycles[i]
                    );
        }

        return cycleOffset;
    }

    private int getTotalAnimationCycles(
            int[] frameLengths) {

        int totalCycles = 0;

        for (int frameLength : frameLengths) {
            totalCycles +=
                    Math.max(
                            1,
                            frameLength
                    );
        }

        return Math.max(
                1,
                totalCycles
        );
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

    private double getRepeatedListValue(
            double[] values,
            int index) {

        if (values == null || values.length == 0) {
            return 1.0;
        }

        return values[
                Math.min(
                        Math.max(0, index),
                        values.length - 1
                )
        ];
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
}
