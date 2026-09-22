package com.skatescape;

import java.util.Arrays;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

/**
 * Owns SkateScape's normal skating movement and idle animation state: movement
 * playback, movement tracking, idle timing, and the 1708/7..9 -> 1708/10..17
 * stop/idle handoff. Trick choreography remains outside this controller.
 */
final class SkatingMovementController {
    private final Client client;
    private final int idleSkateAnimation;
    private final int movingSkateAnimation;
    private final int idleTransitionStartFrame;
    private final int idleStartFrame;
    private final int idleEndFrame;
    private final int idleSlowdown;
    private final int movingStartFrame;
    private final int movingEndFrame;
    private final int movingRunSpeedMultiplier;
    private final int movementGraceTicksMax;

    private LocalPoint lastPlayerLocation;
    private int movementGraceTicks;
    private boolean movementRunning;

    /*
     * Animation 3004 uses frames 5..16 for skating movement. A small playback
     * clock keeps native timing while walking and consumes the same timings at
     * 2x speed while running. Frames outside the loop never become visible.
     */
    private Animation movingAnimation;
    private int[] movingFrameLengths;
    private int movingLoopFrame;
    private int movingLoopProgressUnits;
    private boolean movingLoopActive;

    private boolean originalMovementAnimationsSaved;
    private int originalWalkAnimation;
    private int originalRunAnimation;
    private int originalWalkRotateLeft;
    private int originalWalkRotateRight;
    private int originalWalkRotate180;
    private int originalIdlePoseAnimation;
    private int originalIdleRotateLeft;
    private int originalIdleRotateRight;

    /*
     * STOP-TRANSITION STATE:
     *
     * false/false = moving or otherwise not in SkateScape idle
     * true/false  = playing 1708/7..9
     * false/true  = normal 1708/10..17 idle loop
     */
    private boolean idleTransitionActive;
    private boolean idleSkatingActive;

    /*
     * Animation 1708 keeps native RuneScape playback. Only its real frame
     * lengths are multiplied to slow the settled idle loop.
     */
    private Animation idleAnimation;
    private int[] originalIdleFrameLengths;
    private int appliedIdleSlowdown = -1;
    private int appliedIdleStartFrame = -1;
    private int appliedIdleEndFrame = -1;

    SkatingMovementController(
            Client client,
            int idleSkateAnimation,
            int movingSkateAnimation,
            int idleTransitionStartFrame,
            int idleStartFrame,
            int idleEndFrame,
            int idleSlowdown,
            int movingStartFrame,
            int movingEndFrame,
            int movingRunSpeedMultiplier,
            int movementGraceTicksMax) {

        this.client = client;
        this.idleSkateAnimation = idleSkateAnimation;
        this.movingSkateAnimation = movingSkateAnimation;
        this.idleTransitionStartFrame = idleTransitionStartFrame;
        this.idleStartFrame = idleStartFrame;
        this.idleEndFrame = idleEndFrame;
        this.idleSlowdown = idleSlowdown;
        this.movingStartFrame = movingStartFrame;
        this.movingEndFrame = movingEndFrame;
        this.movingRunSpeedMultiplier = Math.max(1, movingRunSpeedMultiplier);
        this.movementGraceTicksMax = movementGraceTicksMax;
        this.movingLoopFrame = movingStartFrame;
    }

    void resetMovementTracking() {
        lastPlayerLocation = null;
        movementGraceTicks = 0;
        movementRunning = false;
        movingAnimation = null;
        movingFrameLengths = null;
        resetMovingPoseLoop();
    }

    void clearIdleState() {
        idleTransitionActive = false;
        idleSkatingActive = false;
    }

    void forgetSavedOriginalMovementAnimations() {
        originalMovementAnimationsSaved = false;
    }

    boolean updateMovementState(Player player) {
        final LocalPoint currentLocation = player.getLocalLocation();

        if (currentLocation == null) {
            return false;
        }

        if (lastPlayerLocation == null) {
            lastPlayerLocation = currentLocation;
            movementRunning = false;
            return false;
        }

        if (!currentLocation.equals(lastPlayerLocation)) {
            final int deltaX =
                    Math.abs(currentLocation.getX() - lastPlayerLocation.getX());
            final int deltaY =
                    Math.abs(currentLocation.getY() - lastPlayerLocation.getY());

            /*
             * Client-side LocalPoint movement is roughly 4 units/client tick
             * while walking and roughly 8 while running. Use a midpoint
             * threshold so turns/diagonals still classify cleanly without
             * depending on the run-orb toggle (which can disagree with actual
             * movement, e.g. forced walking).
             */
            movementRunning = Math.max(deltaX, deltaY) >= 6;

            lastPlayerLocation = currentLocation;
            movementGraceTicks = movementGraceTicksMax;
            return true;
        }

        if (movementGraceTicks > 0) {
            /*
             * Do NOT use "pose animation == idle pose animation" as a stop
             * detector. SkateScape deliberately sets both the moving pose and
             * RuneScape's idle fallback definition to 3004, so that equality
             * is expected while moving too.
             *
             * The root-safe fallback lets the small location-based grace
             * period expire without exposing RuneScape's centred default idle.
             */
            movementGraceTicks--;
            return true;
        }

        movementRunning = false;
        return false;
    }

    boolean isPlayerStillMoving(Player player) {
        final LocalPoint currentLocation = player.getLocalLocation();

        if (currentLocation == null) {
            return movementGraceTicks > 0;
        }

        return (lastPlayerLocation != null
                && !currentLocation.equals(lastPlayerLocation))
                || movementGraceTicks > 0;
    }

    private void resetMovingPoseLoop() {
        movingLoopActive = false;
        movingLoopFrame = movingStartFrame;
        movingLoopProgressUnits = 0;
    }

    private void ensureMovingAnimationTiming() {
        if (movingAnimation != null && movingFrameLengths != null) {
            return;
        }

        final Animation animation = client.loadAnimation(movingSkateAnimation);

        if (animation == null || animation.isMayaAnim()) {
            movingAnimation = animation;
            movingFrameLengths = null;
            return;
        }

        final int[] frameLengths = animation.getFrameLengths();

        if (frameLengths == null || frameLengths.length == 0) {
            movingAnimation = animation;
            movingFrameLengths = null;
            return;
        }

        if (movingAnimation != animation
                || movingFrameLengths != frameLengths) {
            movingAnimation = animation;
            movingFrameLengths = frameLengths;
            resetMovingPoseLoop();
        }
    }

    private int getMovingFrameLength(int frame) {
        if (movingFrameLengths == null
                || frame < 0
                || frame >= movingFrameLengths.length) {
            return 1;
        }

        return Math.max(1, movingFrameLengths[frame]);
    }

    private void advanceMovingPoseLoop(Player player) {
        ensureMovingAnimationTiming();

        final int maxFrame = movingFrameLengths == null
                ? movingEndFrame
                : movingFrameLengths.length - 1;
        final int safeStart = Math.max(
                0,
                Math.min(movingStartFrame, maxFrame)
        );
        final int safeEnd = Math.max(
                safeStart,
                Math.min(movingEndFrame, maxFrame)
        );

        if (!movingLoopActive
                || movingLoopFrame < safeStart
                || movingLoopFrame > safeEnd) {
            movingLoopActive = true;
            movingLoopFrame = safeStart;
            movingLoopProgressUnits = 0;

            player.setPoseAnimation(movingSkateAnimation);
            player.setPoseAnimationFrame(movingLoopFrame);
            return;
        }

        final int speed = movementRunning
                ? movingRunSpeedMultiplier
                : 1;

        movingLoopProgressUnits += speed;

        while (true) {
            final int frameLength =
                    getMovingFrameLength(movingLoopFrame);

            if (movingLoopProgressUnits < frameLength) {
                break;
            }

            movingLoopProgressUnits -= frameLength;
            movingLoopFrame++;

            if (movingLoopFrame > safeEnd) {
                movingLoopFrame = safeStart;
            }
        }

        player.setPoseAnimation(movingSkateAnimation);
        player.setPoseAnimationFrame(movingLoopFrame);
    }

    void applyIdleAnimationTiming() {
        final Animation animation = idleAnimation != null
                ? idleAnimation
                : client.loadAnimation(idleSkateAnimation);

        if (animation == null || animation.isMayaAnim()) {
            return;
        }

        final int[] frameLengths = animation.getFrameLengths();

        if (frameLengths == null || frameLengths.length == 0) {
            return;
        }

        if (idleAnimation != animation
                || originalIdleFrameLengths == null
                || originalIdleFrameLengths.length != frameLengths.length) {

            restoreIdleAnimationTiming();

            idleAnimation = animation;
            originalIdleFrameLengths =
                    Arrays.copyOf(frameLengths, frameLengths.length);

            appliedIdleSlowdown = -1;
            appliedIdleStartFrame = -1;
            appliedIdleEndFrame = -1;
        }

        final int maxFrame = frameLengths.length - 1;

        final int startFrame = Math.max(
                0,
                Math.min(idleStartFrame, maxFrame)
        );

        final int endFrame = Math.max(
                startFrame,
                Math.min(idleEndFrame, maxFrame)
        );

        if (idleSlowdown == appliedIdleSlowdown
                && startFrame == appliedIdleStartFrame
                && endFrame == appliedIdleEndFrame) {
            return;
        }

        System.arraycopy(
                originalIdleFrameLengths,
                0,
                frameLengths,
                0,
                frameLengths.length
        );

        for (int frame = startFrame;
             frame <= endFrame;
             frame++) {

            frameLengths[frame] = Math.max(
                    1,
                    originalIdleFrameLengths[frame] * idleSlowdown
            );
        }

        appliedIdleSlowdown = idleSlowdown;
        appliedIdleStartFrame = startFrame;
        appliedIdleEndFrame = endFrame;
    }

    void restoreIdleAnimationTiming() {
        if (idleAnimation != null
                && originalIdleFrameLengths != null) {

            final int[] frameLengths = idleAnimation.getFrameLengths();

            if (frameLengths != null
                    && frameLengths.length
                    == originalIdleFrameLengths.length) {

                System.arraycopy(
                        originalIdleFrameLengths,
                        0,
                        frameLengths,
                        0,
                        frameLengths.length
                );
            }
        }

        idleAnimation = null;
        originalIdleFrameLengths = null;
        appliedIdleSlowdown = -1;
        appliedIdleStartFrame = -1;
        appliedIdleEndFrame = -1;
    }

    void saveOriginalMovementAnimations(Player player) {
        if (originalMovementAnimationsSaved) {
            return;
        }

        originalWalkAnimation = player.getWalkAnimation();
        originalRunAnimation = player.getRunAnimation();
        originalWalkRotateLeft = player.getWalkRotateLeft();
        originalWalkRotateRight = player.getWalkRotateRight();
        originalWalkRotate180 = player.getWalkRotate180();

        originalIdlePoseAnimation =
                player.getIdlePoseAnimation();

        originalIdleRotateLeft =
                player.getIdleRotateLeft();

        originalIdleRotateRight =
                player.getIdleRotateRight();

        originalMovementAnimationsSaved = true;
    }

    void applySkatingMovementAnimations(Player player) {
        player.setWalkAnimation(movingSkateAnimation);
        player.setRunAnimation(movingSkateAnimation);
        player.setWalkRotateLeft(movingSkateAnimation);
        player.setWalkRotateRight(movingSkateAnimation);
        player.setWalkRotate180(movingSkateAnimation);

        /*
         * PROACTIVE IDLE FALLBACK SHIELD.
         *
         * Do not wait for RuneScape to select its normal idle and then try
         * to overwrite the rendered pose afterward. Change the definition
         * RuneScape itself will select when movement ends.
         *
         * 3004 is intentional here:
         * - it is already the active skating movement root
         * - therefore the instant "moving -> idle fallback" transition has
         *   no animation/root discontinuity
         * - 1708 still takes over immediately afterward for SkateScape's
         *   actual stop-transition and idle loop
         */
        player.setIdlePoseAnimation(movingSkateAnimation);
        player.setIdleRotateLeft(movingSkateAnimation);
        player.setIdleRotateRight(movingSkateAnimation);
    }

    void restoreOriginalMovementAnimations(Player player) {
        if (!originalMovementAnimationsSaved) {
            return;
        }

        player.setWalkAnimation(originalWalkAnimation);
        player.setRunAnimation(originalRunAnimation);
        player.setWalkRotateLeft(originalWalkRotateLeft);
        player.setWalkRotateRight(originalWalkRotateRight);
        player.setWalkRotate180(originalWalkRotate180);

        player.setIdlePoseAnimation(originalIdlePoseAnimation);
        player.setIdleRotateLeft(originalIdleRotateLeft);
        player.setIdleRotateRight(originalIdleRotateRight);

        originalMovementAnimationsSaved = false;
    }

    void applyMovingState(
            Player player,
            Runnable installInterpolationFilter) {

        /*
         * All normal SkateScape playback is raw. Keep the shared interpolation
         * wrapper installed for 3004 movement and carry that same raw state
         * through 1708/7..9 and settled 1708/10..17 idle.
         */
        installInterpolationFilter.run();

        clearIdleState();

        if (player.getAnimation() == idleSkateAnimation) {
            player.setAnimation(-1);
        }

        /*
         * Release either the transition or idle 1708 pose layer straight back
         * to the configured skating movement animation. The PostClientTick
         * movement clock then constrains 3004 to frames 5..16 and applies
         * walk/run playback speed.
         */
        if (player.getPoseAnimation() == idleSkateAnimation) {
            player.setPoseAnimation(movingSkateAnimation);
        }
    }

    void applyIdleState(
            Player player,
            Runnable installInterpolationFilter,
            Runnable restoreInterpolationFilter) {

        resetMovingPoseLoop();

        final int currentAnimation = player.getAnimation();

        if (currentAnimation != -1
                && currentAnimation != idleSkateAnimation) {

            restoreInterpolationFilter.run();
            clearIdleState();
            return;
        }

        /*
         * Keep settled 1708 idle on the POSE layer. Action animations can
         * override rendered hand equipment.
         *
         * The 3004 root-safe fallback is installed immediately before this
         * method. Re-select 1708 as the stationary pose definition only after
         * settled SkateScape idle has begun.
         */
        if (idleSkatingActive) {
            /* settled idle stays raw too. */
            installInterpolationFilter.run();
            applySettledIdlePose(player, -1);
            return;
        }

        /*
         * Keep the 1708/7..9 bridge on the POSE layer as well. The same native
         * frames and timing are used without action-layer equipment overrides.
         */
        if (idleTransitionActive) {
            applyIdleTransitionPose(player, false);
            return;
        }

        /*
         * REAL STOP TRANSITION.
         *
         * Do NOT jump directly into idle frame 10.
         *
         * 1708/7..9 already match the normal skating/root position, so let
         * those native frames bridge 3004 into the 1708/10..17 idle loop.
         *
         * Keep this bridge on the pose layer so 1708 cannot apply its
         * action-sequence hand-item override. Both the bridge and settled
         * frames 10..17 remain raw.
         */
        installInterpolationFilter.run();

        applyIdleTransitionPose(player, true);

        idleTransitionActive = true;
        idleSkatingActive = false;
    }

    /*
     * WALK -> IDLE HANDOFF.
     *
     *   movement 3004
     *       ->
     *   1708/7 -> 1708/8 -> 1708/9
     *       ->
     *   normal idle loop 1708/10..17
     *
     * Frames 7..9 keep native timing. Frames 10..17 use the configured
     * slowdown. Both bridge and settled idle run on the pose layer so
     * equipped hand items remain visible throughout the complete handoff.
     */
    void onPostClientTick(
            Player player,
            boolean trickActive,
            boolean animationTest,
            Runnable restoreInterpolationFilter) {

        if (trickActive || animationTest) {
            return;
        }

        if (isPlayerStillMoving(player)) {
            advanceMovingPoseLoop(player);
            return;
        }

        if (!idleTransitionActive && !idleSkatingActive) {
            return;
        }

        final int currentAnimation = player.getAnimation();

        /* Never fight a genuine gameplay action animation. */
        if (currentAnimation != -1
                && currentAnimation != idleSkateAnimation) {

            restoreInterpolationFilter.run();
            clearIdleState();
            return;
        }

        if (idleTransitionActive) {
            /*
             * Keep the native 1708 bridge selected as the stationary pose.
             * applySkatingMovementAnimations() deliberately restores the
             * root-safe 3004 fallback definitions each ClientTick, so reapply
             * the 1708 pose definition here without rewinding a healthy frame.
             */
            applyIdleTransitionPose(player, false);

            int frame = player.getPoseAnimationFrame();

            if (frame < idleTransitionStartFrame) {
                frame = idleTransitionStartFrame;
                player.setPoseAnimationFrame(frame);
            }

            /*
             * Do NOT loop or clamp frames 7..9. Let 1708 advance naturally on
             * the pose layer. At frame 10 the same pose simply becomes the
             * settled 1708/10..17 idle loop; no action-layer handoff remains.
             */
            if (frame >= idleStartFrame) {
                if (frame > idleEndFrame) {
                    frame = idleStartFrame;
                }

                applySettledIdlePose(player, frame);

                idleTransitionActive = false;
                idleSkatingActive = true;

                /* do not restore smoothing here; settled idle stays raw. */
            }

            return;
        }

        if (idleSkatingActive) {
            applySettledIdlePose(player, -1);
        }
    }

    /*
     * WEAPON-SAFE STOP TRANSITION.
     *
     * Run 1708/7..9 as the pose animation so the centre/root
     * bridge remains visible without invoking 1708's action-sequence hand-item
     * overrides. A healthy in-progress pose is never rewound; frame 7 is set
     * only when the bridge is first started or if RuneScape replaced the pose.
     */
    private void applyIdleTransitionPose(
            Player player,
            boolean forceStartFrame) {

        player.setIdlePoseAnimation(idleSkateAnimation);
        player.setIdleRotateLeft(idleSkateAnimation);
        player.setIdleRotateRight(idleSkateAnimation);

        final boolean poseChanged =
                player.getPoseAnimation() != idleSkateAnimation;

        if (poseChanged) {
            player.setPoseAnimation(idleSkateAnimation);
        }

        if (forceStartFrame || poseChanged) {
            player.setPoseAnimationFrame(idleTransitionStartFrame);
        }

        /*
         * Clear a stale 1708 action-layer copy if one is present. Normal
         * stop/idle choreography stays on the pose layer, and unrelated
         * gameplay action animations must remain untouched.
         */
        if (player.getAnimation() == idleSkateAnimation) {
            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }
    }

    /*
     * WEAPON-SAFE SETTLED IDLE.
     *
     * Keep 1708/10..17 as the visual idle, but run it as the
     * movement/pose sequence instead of an action sequence. RuneScape action
     * sequences may override the rendered hand equipment; pose sequences do
     * not need that action-layer equipment substitution for this idle.
     *
     * requestedFrame >= 0 is used only for a deliberate handoff (frame 10 or
     * the direct post-trick entry). Otherwise the existing pose frame is left
     * alone so 1708 continues to advance at its patched native timing.
     */
    private void applySettledIdlePose(
            Player player,
            int requestedFrame) {

        player.setIdlePoseAnimation(idleSkateAnimation);
        player.setIdleRotateLeft(idleSkateAnimation);
        player.setIdleRotateRight(idleSkateAnimation);

        final boolean poseChanged =
                player.getPoseAnimation() != idleSkateAnimation;

        if (poseChanged) {
            player.setPoseAnimation(idleSkateAnimation);
        }

        int frame = requestedFrame >= 0
                ? requestedFrame
                : player.getPoseAnimationFrame();

        if (poseChanged && requestedFrame < 0) {
            frame = idleStartFrame;
        }

        if (frame < idleStartFrame || frame > idleEndFrame) {
            frame = idleStartFrame;
        }

        if (requestedFrame >= 0
                || poseChanged
                || player.getPoseAnimationFrame() != frame) {
            player.setPoseAnimationFrame(frame);
        }

        /*
         * 1708 must not remain on the action layer once settled idle starts.
         * Clearing it is the whole point of the equipment-preservation
         * path. Never clear unrelated genuine gameplay actions here.
         */
        if (player.getAnimation() == idleSkateAnimation) {
            player.setAnimation(-1);
            player.setAnimationFrame(0);
        }
    }

    void enterSettledIdleAfterTrick(Player player) {
        applySettledIdlePose(player, idleStartFrame);

        idleTransitionActive = false;
        idleSkatingActive = true;
    }
}
