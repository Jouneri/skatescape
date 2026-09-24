package com.skatescape;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;

final class PlayerTrickAnimationController {
    private final Client client;
    private final IntFunction<Animation> safeAnimationLoader;

    private final int TRICK_PLAYER_ANIMATION;
    private final int TRICK_TRANSITION_ANIMATION;
    private final int MOVING_SKATE_ANIMATION;
    private final int NATURAL_PHASE_GUARD_CYCLES;

    private int[] trickActivePlayerAnimationIds;
    private int[] trickActivePlayerFrames;
    private int[] trickActivePlayerPoseCycles;
    private int[] trickActiveJumpFrames;
    private int[] trickActiveJumpFrameCycles;
    private int trickMainSequenceCycles;
    private int[] trickActivePreFrames;
    private int[] trickPreFrameCycles;
    private int trickPreTransitionCycles;
    private int trickElapsedCycles;
    private int trickActivePostSeamFrame;
    private int trickPostSeamCycles;
    private int[] trickActivePostFrames;
    private int[] trickPostFrameCycles;

    private Animation trickNaturalMainAnimation;
    private int[] trickNaturalMainOriginalFrameLengths;
    private int[] trickNaturalMainFrames;
    private int[] trickNaturalMainFrameCycles;
    private int trickNaturalMainAnimationId = -1;
    private boolean trickNaturalMainPlaybackPrepared;
    private boolean trickNaturalMainPlaybackStarted;
    private boolean trickMainLayerDecided;
    private boolean trickMainUsePoseLayer;

    private Animation trickNaturalTransitionAnimation;
    private int[] trickNaturalTransitionOriginalFrameLengths;
    private boolean trickNaturalPrePlaybackStarted;
    private boolean trickNaturalPreReachedFinalFrame;
    private boolean trickNaturalPostSeamStarted;
    private boolean trickNaturalPostReturnStarted;

    private IntPredicate trickPreviousInterpolationFilter;
    private IntPredicate trickInterpolationFilter;
    private boolean trickInterpolationFilterInstalled;

    static final class Context {
        int[] trickActivePlayerAnimationIds;
        int[] trickActivePlayerFrames;
        int[] trickActivePlayerPoseCycles;
        int[] trickActiveJumpFrames;
        int[] trickActiveJumpFrameCycles;
        int trickMainSequenceCycles;
        int[] trickActivePreFrames;
        int[] trickPreFrameCycles;
        int trickPreTransitionCycles;
        int trickElapsedCycles;
        int trickActivePostSeamFrame;
        int trickPostSeamCycles;
        int[] trickActivePostFrames;
        int[] trickPostFrameCycles;

        void clear() {
            trickActivePlayerAnimationIds = null;
            trickActivePlayerFrames = null;
            trickActivePlayerPoseCycles = null;
            trickActiveJumpFrames = null;
            trickActiveJumpFrameCycles = null;
            trickMainSequenceCycles = 0;
            trickActivePreFrames = null;
            trickPreFrameCycles = null;
            trickPreTransitionCycles = 0;
            trickElapsedCycles = 0;
            trickActivePostSeamFrame = 0;
            trickPostSeamCycles = 0;
            trickActivePostFrames = null;
            trickPostFrameCycles = null;
        }
    }

    PlayerTrickAnimationController(
            Client client,
            IntFunction<Animation> safeAnimationLoader,
            int trickPlayerAnimation,
            int trickTransitionAnimation,
            int movingSkateAnimation,
            int naturalPhaseGuardCycles) {
        this.client = client;
        this.safeAnimationLoader = safeAnimationLoader;
        this.TRICK_PLAYER_ANIMATION = trickPlayerAnimation;
        this.TRICK_TRANSITION_ANIMATION = trickTransitionAnimation;
        this.MOVING_SKATE_ANIMATION = movingSkateAnimation;
        this.NATURAL_PHASE_GUARD_CYCLES = naturalPhaseGuardCycles;
    }

    void syncContext(Context context) {
        if (context == null) {
            return;
        }

        trickActivePlayerAnimationIds = context.trickActivePlayerAnimationIds;
        trickActivePlayerFrames = context.trickActivePlayerFrames;
        trickActivePlayerPoseCycles = context.trickActivePlayerPoseCycles;
        trickActiveJumpFrames = context.trickActiveJumpFrames;
        trickActiveJumpFrameCycles = context.trickActiveJumpFrameCycles;
        trickMainSequenceCycles = context.trickMainSequenceCycles;
        trickActivePreFrames = context.trickActivePreFrames;
        trickPreFrameCycles = context.trickPreFrameCycles;
        trickPreTransitionCycles = context.trickPreTransitionCycles;
        trickElapsedCycles = context.trickElapsedCycles;
        trickActivePostSeamFrame = context.trickActivePostSeamFrame;
        trickPostSeamCycles = context.trickPostSeamCycles;
        trickActivePostFrames = context.trickActivePostFrames;
        trickPostFrameCycles = context.trickPostFrameCycles;
    }

    void clearTimelineContext() {
        trickActivePlayerAnimationIds = null;
        trickActivePlayerFrames = null;
        trickActivePlayerPoseCycles = null;
        trickActiveJumpFrames = null;
        trickActiveJumpFrameCycles = null;
        trickMainSequenceCycles = 0;
        trickActivePreFrames = null;
        trickPreFrameCycles = null;
        trickPreTransitionCycles = 0;
        trickElapsedCycles = 0;
        trickActivePostSeamFrame = 0;
        trickPostSeamCycles = 0;
        trickActivePostFrames = null;
        trickPostFrameCycles = null;
    }

    void applyInspectorMainCycle(
            Player player,
            int playerElapsed) {
        if (player == null) {
            return;
        }

        if (hasCustomActivePlayerPoseTimeline()) {
            final int poseIndex =
                    getTrick4PlayerPoseIndexForCycle(
                            playerElapsed
                    );

            final int animationId =
                    trickActivePlayerAnimationIds[poseIndex];

            final int frame =
                    trickActivePlayerFrames[poseIndex];

            if (trickNaturalMainAnimationId == animationId
                    && animationId == AnimationID.DARK_SPEC_PLAYER) {
                player.setAnimation(animationId);
                player.setAnimationFrame(frame);
            } else {
                player.setAnimation(-1);
                player.setAnimationFrame(0);
                applyTrickPoseFrame(player, animationId, frame);
            }

            return;
        }

        player.setAnimation(-1);
        player.setAnimationFrame(0);
        applyActiveMainTrickPoseForCycle(
                player,
                playerElapsed
        );
    }

    private Animation loadSafeAnimation(int animationId) {
        return safeAnimationLoader == null
                ? null
                : safeAnimationLoader.apply(animationId);
    }

    private void normalizeCycleArrayToTotal(
            int[] cycles,
            int targetTotal) {
        if (cycles == null || cycles.length == 0) {
            return;
        }

        final int safeTarget = Math.max(cycles.length, targetTotal);
        int sourceTotal = 0;

        for (int cycle : cycles) {
            sourceTotal += Math.max(1, cycle);
        }

        sourceTotal = Math.max(1, sourceTotal);
        int scaledTotal = 0;

        for (int i = 0; i < cycles.length; i++) {
            cycles[i] = Math.max(
                    1,
                    (int) Math.round(
                            Math.max(1, cycles[i])
                                    * safeTarget
                                    / (double) sourceTotal
                    )
            );
            scaledTotal += cycles[i];
        }

        int difference = safeTarget - scaledTotal;
        int cursor = 0;
        int guard = 0;

        while (difference != 0 && guard < 10000) {
            final int index = cursor % cycles.length;

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

    private int getTotalAnimationCycles(int[] frameLengths) {
        int totalCycles = 0;

        if (frameLengths == null) {
            return 1;
        }

        for (int frameLength : frameLengths) {
            totalCycles += Math.max(1, frameLength);
        }

        return Math.max(1, totalCycles);
    }

    void installTrickInterpolationFilter() {
        if (trickInterpolationFilterInstalled) {
            return;
        }

        final IntPredicate existingFilter =
                client.getAnimationInterpolationFilter();

        /*
         * A null filter means Animation Smoothing is inactive, so there is
         * nothing to override. When smoothing is active, preserve the existing
         * filter and exclude SkateScape's normal raw-playback animations.
         */
        if (existingFilter == null) {
            trickPreviousInterpolationFilter = null;
            trickInterpolationFilter = null;
            return;
        }

        trickPreviousInterpolationFilter =
                existingFilter;

        trickInterpolationFilter =
                animationId ->
                        !isTrickInterpolationExcluded(
                                animationId
                        )
                                && existingFilter.test(
                                        animationId
                                );

        client.setAnimationInterpolationFilter(
                trickInterpolationFilter
        );

        trickInterpolationFilterInstalled = true;
    }

    private boolean isTrickInterpolationExcluded(
            int animationId) {

        /*
         * ALL NORMAL SKATESCAPE PLAYBACK IS RAW.
         *
         * Movement 3004, idle/transition 1708 and active trick animations
         * are excluded from RuneLite Animation Smoothing. SkateScape drives
         * these frames directly, so interpolation can alter the intended
         * timing and visual transitions. I couldn't get this shit to work,
         * so it's raw.
         *
         * If Animation Smoothing is disabled, the existing filter is null and
         * installTrickInterpolationFilter() still does nothing, so SkateScape
         * never enables smoothing behind the user's back.
         *
         * Animation Testing uses a separate wrapper when developer tools are
         * enabled. Trick startup removes that wrapper before installing the
         * SkateScape filter, and the tester reapplies its state afterward.
         */
        if (animationId == MOVING_SKATE_ANIMATION
                || animationId == TRICK_PLAYER_ANIMATION
                || animationId == TRICK_TRANSITION_ANIMATION) {

            return true;
        }

        if (trickActivePlayerAnimationIds != null) {
            for (int activeAnimationId
                    : trickActivePlayerAnimationIds) {

                if (animationId == activeAnimationId) {
                    return true;
                }
            }
        }

        return false;
    }

    void restoreTrickInterpolationFilter() {
        if (!trickInterpolationFilterInstalled) {
            trickPreviousInterpolationFilter = null;
            trickInterpolationFilter = null;
            return;
        }

        /*
         * Restore only while this wrapper is still installed. Do not overwrite
         * a filter installed by another plugin during the trick.
         */
        if (client.getAnimationInterpolationFilter()
                == trickInterpolationFilter) {

            client.setAnimationInterpolationFilter(
                    trickPreviousInterpolationFilter
            );
        }

        trickPreviousInterpolationFilter = null;
        trickInterpolationFilter = null;
        trickInterpolationFilterInstalled = false;
    }

    private int getTrick4PlayerPoseIndexForCycle(
            int elapsedCycles) {

        if (trickActivePlayerPoseCycles == null
                || trickActivePlayerPoseCycles.length == 0) {

            return 0;
        }

        int cycleCursor = 0;

        for (int i = 0;
                i < trickActivePlayerPoseCycles.length;
                i++) {

            cycleCursor +=
                    Math.max(
                            1,
                            trickActivePlayerPoseCycles[i]
                    );

            if (elapsedCycles < cycleCursor) {
                return i;
            }
        }

        return trickActivePlayerPoseCycles.length - 1;
    }

    private boolean hasCustomActivePlayerPoseTimeline() {
        return trickActivePlayerAnimationIds != null
                && trickActivePlayerFrames != null
                && trickActivePlayerPoseCycles != null
                && trickActivePlayerAnimationIds.length > 0
                && trickActivePlayerAnimationIds.length
                == trickActivePlayerFrames.length
                && trickActivePlayerAnimationIds.length
                == trickActivePlayerPoseCycles.length;
    }

    private void applyActiveMainTrickPoseForCycle(
            Player player,
            int elapsedCycles) {

        if (hasCustomActivePlayerPoseTimeline()) {
            final int poseIndex =
                    getTrick4PlayerPoseIndexForCycle(
                            elapsedCycles
                    );

            applyTrickPoseFrame(
                    player,
                    trickActivePlayerAnimationIds[
                            poseIndex
                    ],
                    trickActivePlayerFrames[
                            poseIndex
                    ]
            );

            return;
        }

        final int frame =
                getActiveTrickFrameForCycle(
                        elapsedCycles
                );

        applyMainTrickPoseFrame(
                player,
                frame
        );
    }

    void prepareNaturalMainTrickPlayback(
            TrickDefinition definition) {

        restoreNaturalMainTrickTiming();

        if (definition == null) {
            return;
        }

        final int[] sourceAnimationIds;
        final int[] sourceFrames;
        final int[] sourceCycles;

        if (definition.getSlot() == 4
                && hasCustomActivePlayerPoseTimeline()) {

            sourceAnimationIds =
                    trickActivePlayerAnimationIds;

            sourceFrames =
                    trickActivePlayerFrames;

            sourceCycles =
                    trickActivePlayerPoseCycles;
        } else {
            /*
             * Build a contiguous 1603 timing candidate by inserting frame 11
             * between 10 and 12. The configured pose choreography still omits
             * frame 11.
             */
            sourceFrames =
                    new int[]{
                            0, 1, 2, 3, 4, 5, 6,
                            7, 8, 9, 10, 11, 12
                    };

            sourceAnimationIds =
                    new int[
                            sourceFrames.length
                    ];

            Arrays.fill(
                    sourceAnimationIds,
                    TRICK_PLAYER_ANIMATION
            );

            sourceCycles =
                    new int[
                            sourceFrames.length
                    ];

            for (int i = 0;
                    i < sourceFrames.length;
                    i++) {

                final int frame =
                        sourceFrames[i];

                if (frame == 11) {
                    /*
                     * Give frame 11 one client cycle so a contiguous natural
                     * playback candidate does not linger on the crossed-leg
                     * pose.
                     */
                    sourceCycles[i] = 1;
                } else {
                    int matchedCycles = 1;

                    for (int j = 0;
                            j < trickActiveJumpFrames.length;
                            j++) {

                        if (trickActiveJumpFrames[j]
                                == frame) {

                            matchedCycles =
                                    Math.max(
                                            1,
                                            trickActiveJumpFrameCycles[j]
                                    );
                            break;
                        }
                    }

                    sourceCycles[i] = matchedCycles;
                }
            }

            /*
             * Adding frame 11 must not make Tricks 1-3 longer. Rebalance the
             * natural player-animation frame lengths back to the exact board
             * timeline duration.
             */
            normalizeCycleArrayToTotal(
                    sourceCycles,
                    trickMainSequenceCycles
            );
        }

        if (sourceAnimationIds == null
                || sourceFrames == null
                || sourceCycles == null
                || sourceFrames.length == 0
                || sourceAnimationIds.length
                != sourceFrames.length
                || sourceCycles.length
                != sourceFrames.length) {

            return;
        }

        /*
         * Natural playback can use one action animation at a time. All active
         * poses must therefore use the same animation ID. Multi-animation
         * Frankenstein sequences still use the manual pose fallback.
         */
        final int animationId =
                sourceAnimationIds[0];

        for (int configuredAnimationId
                : sourceAnimationIds) {

            if (configuredAnimationId
                    != animationId) {

                return;
            }
        }

        final Animation animation =
                loadSafeAnimation(
                        animationId
                );

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            return;
        }

        final int[] liveFrameLengths =
                animation.getFrameLengths();

        /*
         * Compress repeated frames into one longer natural frame. A compact
         * list such as "...23,23,23" therefore remains representable by normal
         * forward action playback. Backwards frame jumps fall back to the
         * manual pose engine.
         */
        final int[] compressedFrames =
                new int[
                        sourceFrames.length
                ];

        final int[] compressedCycles =
                new int[
                        sourceFrames.length
                ];

        int compressedCount = 0;
        int previousFrame = -1;

        for (int i = 0;
                i < sourceFrames.length;
                i++) {

            final int frame =
                    sourceFrames[i];

            if (frame < 0
                    || frame >= liveFrameLengths.length) {

                return;
            }

            final int cycles =
                    Math.max(
                            1,
                            sourceCycles[i]
                    );

            if (compressedCount == 0) {
                compressedFrames[0] = frame;
                compressedCycles[0] = cycles;
                compressedCount = 1;
                previousFrame = frame;
                continue;
            }

            if (frame < previousFrame) {
                return;
            }

            if (frame == previousFrame) {
                compressedCycles[
                        compressedCount - 1
                ] += cycles;

                continue;
            }

            compressedFrames[
                    compressedCount
            ] = frame;

            compressedCycles[
                    compressedCount
            ] = cycles;

            compressedCount++;
            previousFrame = frame;
        }

        trickNaturalMainAnimation =
                animation;

        trickNaturalMainOriginalFrameLengths =
                Arrays.copyOf(
                        liveFrameLengths,
                        liveFrameLengths.length
                );

        trickNaturalMainFrames =
                Arrays.copyOf(
                        compressedFrames,
                        compressedCount
                );

        trickNaturalMainFrameCycles =
                Arrays.copyOf(
                        compressedCycles,
                        compressedCount
                );

        trickNaturalMainAnimationId =
                animationId;

        /*
         * Give each selected native frame exactly the duration already chosen
         * by SkateScape's trick timeline. Gaps between selected frames are
         * made one cycle long and are jumped over in updateNatural... before
         * they can become a visible held pose.
         *
         * This mutation is temporary and restored as soon as the main trick
         * ends, on interruption, or before another trick is prepared.
         */
        for (int i = 0;
                i < trickNaturalMainFrames.length;
                i++) {

            final int frame =
                    trickNaturalMainFrames[i];

            liveFrameLengths[frame] =
                    Math.max(
                            1,
                            trickNaturalMainFrameCycles[i]
                    );

            if (i + 1
                    < trickNaturalMainFrames.length) {

                final int nextFrame =
                        trickNaturalMainFrames[
                                i + 1
                        ];

                for (int skippedFrame =
                        frame + 1;
                        skippedFrame < nextFrame;
                        skippedFrame++) {

                    liveFrameLengths[
                            skippedFrame
                    ] = 1;
                }
            }
        }

        /*
         * The outgoing natural MAIN action must still be alive when the
         * logical MAIN window ends. Otherwise RuneScape can clear the action
         * layer first and expose the underlying 3004 skating pose for one
         * render before 1708 takes over. The guard does not extend the logical
         * MAIN phase.
         */
        final int guardedFinalMainFrame =
                trickNaturalMainFrames[
                        trickNaturalMainFrames.length - 1
                ];

        final int naturalPlayerCycles =
                getTotalAnimationCycles(
                        trickNaturalMainFrameCycles
                );

        final int finalPoseHoldCycles =
                Math.max(
                        0,
                        trickMainSequenceCycles
                                - naturalPlayerCycles
                );

        liveFrameLengths[guardedFinalMainFrame] =
                Math.max(
                        1,
                        liveFrameLengths[guardedFinalMainFrame]
                )
                        + finalPoseHoldCycles
                        + NATURAL_PHASE_GUARD_CYCLES;

        trickNaturalMainPlaybackPrepared = true;
        trickNaturalMainPlaybackStarted = false;
    }

    void restoreNaturalMainTrickTiming() {
        if (trickNaturalMainAnimation != null
                && trickNaturalMainOriginalFrameLengths != null) {

            final int[] liveFrameLengths =
                    trickNaturalMainAnimation
                            .getFrameLengths();

            if (liveFrameLengths != null) {
                System.arraycopy(
                        trickNaturalMainOriginalFrameLengths,
                        0,
                        liveFrameLengths,
                        0,
                        Math.min(
                                liveFrameLengths.length,
                                trickNaturalMainOriginalFrameLengths.length
                        )
                );
            }
        }

        trickNaturalMainAnimation = null;
        trickNaturalMainOriginalFrameLengths = null;
        trickNaturalMainFrames = null;
        trickNaturalMainFrameCycles = null;
        trickNaturalMainAnimationId = -1;
        trickNaturalMainPlaybackPrepared = false;
        trickNaturalMainPlaybackStarted = false;
        trickMainLayerDecided = false;
        trickMainUsePoseLayer = false;
    }

    void prepareNaturalTransitionTiming() {
        restoreNaturalTransitionTiming();

        final Animation animation =
                loadSafeAnimation(
                        TRICK_TRANSITION_ANIMATION
                );

        if (animation == null
                || animation.isMayaAnim()
                || animation.getFrameLengths() == null
                || animation.getFrameLengths().length == 0) {

            return;
        }

        final int[] liveFrameLengths =
                animation.getFrameLengths();

        trickNaturalTransitionAnimation =
                animation;

        trickNaturalTransitionOriginalFrameLengths =
                Arrays.copyOf(
                        liveFrameLengths,
                        liveFrameLengths.length
                );

        /*
         * Match the existing SkateScape transition timeline exactly, but let
         * RuneScape advance the frames itself. This preserves board/body
         * timing while using native animation playback.
         */
        patchNaturalTransitionFrames(
                trickActivePreFrames,
                trickPreFrameCycles
        );

        patchNaturalTransitionFrames(
                trickActivePostFrames,
                trickPostFrameCycles
        );

        /*
         * Keep the final centring and return frames alive slightly past their
         * logical phase boundaries. The extra cycles are not part of the
         * SkateScape timeline; they only prevent the underlying 3004 skating
         * pose from flashing if 1708 expires one render early.
         */
        addNaturalTransitionGuardToFinalFrame(
                trickActivePreFrames,
                trickPreFrameCycles
        );

        addNaturalTransitionGuardToFinalFrame(
                trickActivePostFrames,
                trickPostFrameCycles
        );

        trickNaturalPrePlaybackStarted = false;
        trickNaturalPreReachedFinalFrame = false;
        trickNaturalPostSeamStarted = false;
        trickNaturalPostReturnStarted = false;
    }

    private void patchNaturalTransitionFrames(
            int[] frames,
            int[] frameCycles) {

        if (trickNaturalTransitionAnimation == null
                || frames == null
                || frameCycles == null
                || frames.length == 0
                || frameCycles.length == 0) {

            return;
        }

        final int[] liveFrameLengths =
                trickNaturalTransitionAnimation
                        .getFrameLengths();

        if (liveFrameLengths == null
                || liveFrameLengths.length == 0) {

            return;
        }

        final int count =
                Math.min(
                        frames.length,
                        frameCycles.length
                );

        for (int i = 0;
                i < count;
                i++) {

            final int frame = frames[i];

            if (frame < 0
                    || frame >= liveFrameLengths.length) {

                continue;
            }

            liveFrameLengths[frame] =
                    Math.max(
                            1,
                            frameCycles[i]
                    );
        }
    }

    private void addNaturalTransitionGuardToFinalFrame(
            int[] frames,
            int[] frameCycles) {

        if (frames == null
                || frameCycles == null
                || frames.length == 0
                || frameCycles.length == 0) {

            return;
        }

        final int finalIndex =
                Math.min(
                        frames.length,
                        frameCycles.length
                ) - 1;

        setNaturalTransitionFrameLength(
                frames[finalIndex],
                Math.max(
                        1,
                        frameCycles[finalIndex]
                ) + NATURAL_PHASE_GUARD_CYCLES
        );
    }

    private void setNaturalTransitionFrameLength(
            int frame,
            int cycles) {

        if (trickNaturalTransitionAnimation == null) {
            return;
        }

        final int[] liveFrameLengths =
                trickNaturalTransitionAnimation
                        .getFrameLengths();

        if (liveFrameLengths == null
                || frame < 0
                || frame >= liveFrameLengths.length) {

            return;
        }

        liveFrameLengths[frame] =
                Math.max(
                        1,
                        cycles
                );
    }

    void restoreNaturalTransitionTiming() {
        if (trickNaturalTransitionAnimation != null
                && trickNaturalTransitionOriginalFrameLengths != null) {

            final int[] liveFrameLengths =
                    trickNaturalTransitionAnimation
                            .getFrameLengths();

            if (liveFrameLengths != null) {
                System.arraycopy(
                        trickNaturalTransitionOriginalFrameLengths,
                        0,
                        liveFrameLengths,
                        0,
                        Math.min(
                                liveFrameLengths.length,
                                trickNaturalTransitionOriginalFrameLengths.length
                        )
                );
            }
        }

        trickNaturalTransitionAnimation = null;
        trickNaturalTransitionOriginalFrameLengths = null;
        trickNaturalPrePlaybackStarted = false;
        trickNaturalPreReachedFinalFrame = false;
        trickNaturalPostSeamStarted = false;
        trickNaturalPostReturnStarted = false;
    }

    /*
     * Run 1708 PRE, landing seam, and RETURN on the POSE layer so equipped
     * hand items remain visible. MAIN behavior is unchanged.
     */
    private void clearActionLayerForTrickTransition(
            Player player) {

        if (player.getAnimation() != -1) {
            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }
    }

    private void startTrickTransitionPose(
            Player player,
            int frame) {

        clearActionLayerForTrickTransition(player);

        player.setPoseAnimation(
                TRICK_TRANSITION_ANIMATION
        );

        player.setPoseAnimationFrame(frame);
    }

    void startNaturalPreTransition(
            Player player) {

        if (trickActivePreFrames == null
                || trickActivePreFrames.length == 0) {

            return;
        }

        startTrickTransitionPose(
                player,
                trickActivePreFrames[0]
        );

        trickNaturalPrePlaybackStarted = true;
    }

    void updateNaturalPreTransition(
            Player player) {

        clearActionLayerForTrickTransition(player);

        if (!trickNaturalPrePlaybackStarted) {
            startNaturalPreTransition(player);
        }

        if (trickActivePreFrames == null
                || trickActivePreFrames.length == 0) {

            return;
        }

        /*
         * Movement/true-tile refreshes can also reassert the pose definition.
         * If that happens, restore 1708 at the PRE frame dictated by the
         * authoritative trick clock instead of rewinding to the first frame.
         */
        if (player.getPoseAnimation()
                != TRICK_TRANSITION_ANIMATION) {

            final int frameIndex =
                    getTransitionFrameIndex(
                            trickElapsedCycles,
                            trickPreFrameCycles,
                            trickActivePreFrames.length
                    );

            player.setPoseAnimation(
                    TRICK_TRANSITION_ANIMATION
            );

            player.setPoseAnimationFrame(
                    trickActivePreFrames[frameIndex]
            );
        }

        final int finalPreFrame =
                trickActivePreFrames[
                        trickActivePreFrames.length - 1
                ];

        /*
         * Latch live 1708 frame 27 until PRE ends. Without the latch, natural
         * playback can wrap 27 -> 18 before the logical PRE window reaches
         * MAIN. Extend only the final frame so the normal advance into 27
         * remains intact.
         */

        final int expectedPreFrame =
                trickActivePreFrames[
                        getTransitionFrameIndex(
                                trickElapsedCycles,
                                trickPreFrameCycles,
                                trickActivePreFrames.length
                        )
                ];

        /*
         * Same-ID pose restarts are possible too. If movement rewinds 1708
         * without changing the pose animation ID, never let PRE fall behind
         * the authoritative timeline. Natural forward playback is untouched.
         */
        if (!trickNaturalPreReachedFinalFrame
                && player.getPoseAnimationFrame() < expectedPreFrame) {

            player.setPoseAnimationFrame(expectedPreFrame);
        }

        if (!trickNaturalPreReachedFinalFrame
                && player.getPoseAnimationFrame()
                == finalPreFrame) {

            trickNaturalPreReachedFinalFrame = true;

            final int remainingPreCycles =
                    Math.max(
                            1,
                            trickPreTransitionCycles
                                    - trickElapsedCycles
                    );

            setNaturalTransitionFrameLength(
                    finalPreFrame,
                    remainingPreCycles
                            + NATURAL_PHASE_GUARD_CYCLES
            );
        }

        /*
         * Safety net only. The frame-length extension above should prevent a
         * wrap. If the client still advances past 27, snap the pose frame back
         * once. Do not continuously force frame 27; normal progression into
         * the final pose should remain intact.
         */
        if (trickNaturalPreReachedFinalFrame
                && player.getPoseAnimationFrame()
                != finalPreFrame) {

            player.setPoseAnimationFrame(
                    finalPreFrame
            );
        }
    }

    void updateNaturalPostSeam(
            Player player) {

        clearActionLayerForTrickTransition(player);

        if (trickNaturalPostSeamStarted
                && player.getPoseAnimation()
                == TRICK_TRANSITION_ANIMATION) {

            return;
        }

        /*
         * Frame 27 was also the final pre-transition frame. Re-time it here
         * for the dedicated centred landing seam before starting 1708 again.
         *
         * Trick 4 may have just finished natural 2890 on the action layer;
         * clearing that layer above is essential so this equipment-safe pose
         * is actually the visible landing seam.
         */
        setNaturalTransitionFrameLength(
                trickActivePostSeamFrame,
                trickPostSeamCycles
                        + NATURAL_PHASE_GUARD_CYCLES
        );

        player.setPoseAnimation(
                TRICK_TRANSITION_ANIMATION
        );

        player.setPoseAnimationFrame(
                trickActivePostSeamFrame
        );

        trickNaturalPostSeamStarted = true;
        trickNaturalPostReturnStarted = false;
    }

    void updateNaturalPostReturn(
            Player player,
            int postElapsed,
            boolean moving) {

        clearActionLayerForTrickTransition(player);

        if (trickActivePostFrames == null
                || trickActivePostFrames.length == 0) {

            return;
        }

        /*
         * While moving, RuneScape can restart 1708 without changing the logical
         * RETURN phase. Drive the visible RETURN frame from postElapsed every
         * ClientTick so path updates cannot rewind the transition. Keep 1708 on
         * the POSE layer so equipped hand items remain visible.
         */
        if (moving) {
            final int frameIndex =
                    getTransitionFrameIndex(
                            postElapsed,
                            trickPostFrameCycles,
                            trickActivePostFrames.length
                    );

            final int expectedFrame =
                    trickActivePostFrames[frameIndex];

            if (player.getPoseAnimation()
                    != TRICK_TRANSITION_ANIMATION) {

                player.setPoseAnimation(
                        TRICK_TRANSITION_ANIMATION
                );
            }

            if (player.getPoseAnimationFrame()
                    != expectedFrame) {

                player.setPoseAnimationFrame(
                        expectedFrame
                );
            }

            trickNaturalPostReturnStarted = true;
            return;
        }

        if (!trickNaturalPostReturnStarted) {
            /*
             * Same pose-animation ID, direct 27 -> 0 handoff. The action layer
             * stays clear so equipped items remain visible.
             */
            player.setPoseAnimation(
                    TRICK_TRANSITION_ANIMATION
            );

            player.setPoseAnimationFrame(
                    trickActivePostFrames[0]
            );

            trickNaturalPostReturnStarted = true;
            return;
        }

        if (player.getPoseAnimation()
                != TRICK_TRANSITION_ANIMATION) {

            player.setPoseAnimation(
                    TRICK_TRANSITION_ANIMATION
            );

            player.setPoseAnimationFrame(
                    trickActivePostFrames[0]
            );
        }
    }

    /*
     * Re-apply the active RETURN pose after movement/idle updates in the same
     * client tick. The trick timeline still owns the frame; PRE, MAIN, and the
     * landing seam are untouched.
     */
    void reinforcePostReturnPose(Player player) {
        if (player == null
                || !trickNaturalPostReturnStarted
                || trickActivePostFrames == null
                || trickActivePostFrames.length == 0) {

            return;
        }

        final int jumpElapsed =
                Math.max(
                        0,
                        trickElapsedCycles
                                - trickPreTransitionCycles
                );

        if (jumpElapsed < trickMainSequenceCycles) {
            return;
        }

        final int seamElapsed =
                Math.max(
                        0,
                        jumpElapsed
                                - trickMainSequenceCycles
                );

        if (seamElapsed < trickPostSeamCycles) {
            return;
        }

        final int postElapsed =
                Math.max(
                        0,
                        seamElapsed
                                - trickPostSeamCycles
                );

        final int frameIndex =
                getTransitionFrameIndex(
                        postElapsed,
                        trickPostFrameCycles,
                        trickActivePostFrames.length
                );

        final int expectedFrame =
                trickActivePostFrames[frameIndex];

        clearActionLayerForTrickTransition(player);

        if (player.getPoseAnimation()
                != TRICK_TRANSITION_ANIMATION) {

            player.setPoseAnimation(
                    TRICK_TRANSITION_ANIMATION
            );
        }

        if (player.getPoseAnimationFrame()
                != expectedFrame) {

            player.setPoseAnimationFrame(
                    expectedFrame
            );
        }
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

    private boolean isNaturalMainAllowedFrame(
            int frame) {

        if (trickNaturalMainFrames == null) {
            return false;
        }

        for (int allowedFrame
                : trickNaturalMainFrames) {

            if (frame == allowedFrame) {
                return true;
            }
        }

        return false;
    }

    private int getNextNaturalMainAllowedFrame(
            int frame) {

        if (trickNaturalMainFrames == null
                || trickNaturalMainFrames.length == 0) {

            return frame;
        }

        for (int allowedFrame
                : trickNaturalMainFrames) {

            if (allowedFrame > frame) {
                return allowedFrame;
            }
        }

        return trickNaturalMainFrames[
                trickNaturalMainFrames.length - 1
        ];
    }

    private int getExpectedNaturalMainFrameForCycle(
            int elapsedCycles) {

        if (trickNaturalMainFrames == null
                || trickNaturalMainFrames.length == 0) {

            return 0;
        }

        if (trickNaturalMainFrameCycles == null
                || trickNaturalMainFrameCycles.length == 0) {

            return trickNaturalMainFrames[0];
        }

        int remaining =
                Math.max(
                        0,
                        elapsedCycles
                );

        final int count =
                Math.min(
                        trickNaturalMainFrames.length,
                        trickNaturalMainFrameCycles.length
                );

        for (int i = 0;
                i < count;
                i++) {

            final int frameCycles =
                    Math.max(
                            1,
                            trickNaturalMainFrameCycles[i]
                    );

            if (remaining < frameCycles) {
                return trickNaturalMainFrames[i];
            }

            remaining -= frameCycles;
        }

        return trickNaturalMainFrames[
                trickNaturalMainFrames.length - 1
        ];
    }

    void updateMainTrickAnimation(
            Player player,
            int jumpElapsed,
            boolean moving) {

        /*
         * Trick 4 may run natural 2890 on the ACTION layer. Active trick
         * animations remain excluded from RuneLite interpolation.
         */
        if (trickNaturalMainAnimationId == AnimationID.DARK_SPEC_PLAYER) {
            trickMainUsePoseLayer = false;
            trickMainLayerDecided = true;

            if (updateNaturalMainTrickAnimation(
                    player,
                    jumpElapsed
            )) {
                return;
            }
        }

        /*
         * Root-safe fallback for 1603, mixed-animation Trick 4 sequences, or
         * any natural 2890 setup that could not be prepared.
         */
        trickMainUsePoseLayer = true;
        trickMainLayerDecided = true;

        if (player.getAnimation() != -1) {
            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }

        applyActiveMainTrickPoseForCycle(
                player,
                jumpElapsed
        );
    }

    private boolean updateNaturalMainTrickAnimation(
            Player player,
            int jumpElapsed) {

        if (!trickNaturalMainPlaybackPrepared
                || trickNaturalMainFrames == null
                || trickNaturalMainFrames.length == 0
                || trickNaturalMainAnimationId < 0) {

            return false;
        }

        final int firstFrame =
                trickNaturalMainFrames[0];

        final int lastFrame =
                trickNaturalMainFrames[
                        trickNaturalMainFrames.length - 1
                ];

        /*
         * MOVING TRUE-TILE RESTART GUARD.
         *
         * While the player is pathing, RuneScape can reassert the Actor's
         * action state when the logical/true tile advances. The trick timeline
         * remains authoritative, so restore the 2890 frame for the current
         * MAIN cycle instead of allowing the action layer to restart at frame 0.
         */
        final int expectedFrame =
                getExpectedNaturalMainFrameForCycle(
                        jumpElapsed
                );

        /*
         * Keep the root-safe skating pose underneath the visible action
         * animation. The action layer now owns the entire smooth main jump.
         */
        if (player.getPoseAnimation()
                != MOVING_SKATE_ANIMATION) {

            player.setPoseAnimation(
                    MOVING_SKATE_ANIMATION
            );
        }

        if (!trickNaturalMainPlaybackStarted) {
            player.setAnimation(
                    trickNaturalMainAnimationId
            );

            /*
             * queued starts may enter MAIN after cycle 0. Start natural
             * 2890 directly on the frame dictated by the authoritative MAIN
             * clock. Normal tricks still begin on firstFrame because their
             * jumpElapsed is 0, so this does not change ordinary playback.
             */
            player.setAnimationFrame(
                    expectedFrame
            );

            trickNaturalMainPlaybackStarted = true;
            return true;
        }

        if (player.getAnimation()
                != trickNaturalMainAnimationId) {

            player.setAnimation(
                    trickNaturalMainAnimationId
            );

            player.setAnimationFrame(
                    expectedFrame
            );

            return true;
        }

        final int currentFrame =
                player.getAnimationFrame();

        if (currentFrame < firstFrame) {
            player.setAnimationFrame(
                    expectedFrame
            );

            return true;
        }

        /*
         * A movement refresh can also restart the same action ID at an early
         * frame rather than clearing it outright. Never allow the visible
         * 2890 sequence to rewind behind the frame dictated by the trick
         * clock. Otherwise leave natural playback alone.
         */
        if (currentFrame < expectedFrame) {
            player.setAnimationFrame(
                    expectedFrame
            );

            return true;
        }

        if (currentFrame > lastFrame) {
            player.setAnimationFrame(
                    lastFrame
            );

            return true;
        }

        /*
         * Skip only frames that are absent from the selected natural MAIN
         * choreography, such as the configured 2890 13 -> 19 gap. Contiguous
         * stretches are otherwise left to RuneScape's action-animation engine
         * to advance normally.
         */
        if (!isNaturalMainAllowedFrame(
                currentFrame
        )) {

            player.setAnimationFrame(
                    getNextNaturalMainAllowedFrame(
                            currentFrame
                    )
            );
        }

        return true;
    }

    void finishNaturalMainTrickPlayback() {
        /*
         * Normal phase handoff: restore the temporary frame-length patch but
         * deliberately leave the current action animation in place until the
         * 1708 landing seam replaces it in the same ClientTick. No -1 gap.
         */
        restoreNaturalMainTrickTiming();
    }

    void stopNaturalMainTrickAnimation(
            Player player) {

        if (trickNaturalMainPlaybackStarted
                && player != null
                && player.getAnimation()
                == trickNaturalMainAnimationId) {

            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }

        restoreNaturalMainTrickTiming();
    }

    private int getActiveTrickFrameForCycle(
            int elapsedCycles) {

        if (trickActiveJumpFrames == null
                || trickActiveJumpFrames.length == 0
                || trickActiveJumpFrameCycles == null
                || trickActiveJumpFrameCycles.length == 0) {

            return 0;
        }

        int cycleCursor = 0;

        for (int i = 0;
                i < trickActiveJumpFrames.length;
                i++) {

            cycleCursor +=
                    Math.max(
                            1,
                            trickActiveJumpFrameCycles[i]
                    );

            if (elapsedCycles < cycleCursor) {
                return trickActiveJumpFrames[i];
            }
        }

        return trickActiveJumpFrames[
                trickActiveJumpFrames.length - 1
        ];
    }

    private void applyTrickPoseFrame(
            Player player,
            int animationId,
            int frame) {

        if (player.getPoseAnimation()
                != animationId) {

            player.setPoseAnimation(
                    animationId
            );
        }

        if (player.getPoseAnimationFrame()
                != frame) {

            player.setPoseAnimationFrame(frame);
        }
    }

    private void applyMainTrickPoseFrame(
            Player player,
            int frame) {

        applyTrickPoseFrame(
                player,
                TRICK_PLAYER_ANIMATION,
                frame
        );
    }
}
