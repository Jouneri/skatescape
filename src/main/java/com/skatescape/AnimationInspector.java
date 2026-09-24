package com.skatescape;

import java.awt.event.KeyEvent;
import java.util.function.IntPredicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;

@Slf4j
final class AnimationInspector {
    private final Client client;
    private final SkateScapeConfig config;
    private final ConfigManager configManager;
    private final Runnable refreshOpenConfigPanel;
    private final Runnable applyOpenConfigPanelFixes;
    private final int maxSafeAnimationId;

    /*
     * Animation Inspector has its own temporary smoothing wrapper.
     * It stays separate from SkateScape's normal raw-playback filter.
     *
     * freezeFrame ON  -> exclude only the tested animation
     * freezeFrame OFF -> restore RuneLite's original smoothing filter
     */
    private IntPredicate previousInterpolationFilter;
    private IntPredicate interpolationFilter;
    private boolean interpolationFilterInstalled;
    private int interpolationExcludedId = -1;

    private volatile int pendingAnimationIdDelta;
    private volatile int pendingAnimationFrameDelta;

    private int animationBrowseMaxFrame = -1;
    private int animationTestId;
    private int animationTestFrame;

    AnimationInspector(
            Client client,
            SkateScapeConfig config,
            ConfigManager configManager,
            Runnable refreshOpenConfigPanel,
            Runnable applyOpenConfigPanelFixes,
            int maxSafeAnimationId) {

        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.refreshOpenConfigPanel = refreshOpenConfigPanel;
        this.applyOpenConfigPanelFixes = applyOpenConfigPanelFixes;
        this.maxSafeAnimationId = maxSafeAnimationId;
    }

    void syncFromConfig() {
        animationTestId = config.animationId();
        animationTestFrame = config.animationFrame();
    }

    int getBrowseMaxFrame() {
        return animationBrowseMaxFrame;
    }

    /**
     * Called only after SkateScapePlugin has applied the shared inspector key
     * guards (logged in, canvas focused, no modifiers, Trick Inspector first).
     */
    boolean handleAnimationBrowseKeyPressed(KeyEvent event) {
        if (!config.animationTest()) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.VK_UP:
                pendingAnimationIdDelta++;
                event.consume();
                return true;

            case KeyEvent.VK_DOWN:
                pendingAnimationIdDelta--;
                event.consume();
                return true;

            case KeyEvent.VK_RIGHT:
                pendingAnimationFrameDelta++;
                event.consume();
                return true;

            case KeyEvent.VK_LEFT:
                pendingAnimationFrameDelta--;
                event.consume();
                return true;

            default:
                return false;
        }
    }

    void clearBrowseState() {
        pendingAnimationIdDelta = 0;
        pendingAnimationFrameDelta = 0;
        animationBrowseMaxFrame = -1;
    }

    private void setAnimationTestId(int animationId) {
        animationTestId = animationId;
        configManager.setConfiguration(
                "skatescape",
                "animationId",
                animationId
        );
    }

    private void setAnimationTestFrame(int animationFrame) {
        animationTestFrame = animationFrame;
        configManager.setConfiguration(
                "skatescape",
                "animationFrame",
                animationFrame
        );
    }

    private Animation loadSafeAnimation(int animationId) {
        if (animationId < 0
                || animationId > maxSafeAnimationId) {
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

    private int findNextValidAnimationId(
            int currentId,
            int direction) {

        if (direction == 0) {
            return currentId;
        }

        int candidate =
                currentId + Integer.signum(direction);

        while (candidate >= 0
                && candidate <= maxSafeAnimationId) {

            if (loadSafeAnimation(candidate) != null) {
                return candidate;
            }

            candidate += Integer.signum(direction);
        }

        return currentId;
    }

    private int getSafeMaxFrame(Animation animation) {
        if (animation == null) {
            return 0;
        }

        return Math.max(
                0,
                animation.getNumFrames() - 1
        );
    }

    private void clampAnimationTestFrame(Animation animation) {
        final int maxFrame =
                getSafeMaxFrame(animation);

        final int previousMaxFrame =
                animationBrowseMaxFrame;

        animationBrowseMaxFrame = maxFrame;

        if (config.animationTest()
                && previousMaxFrame != maxFrame) {
            applyOpenConfigPanelFixes.run();
        }

        final int safeFrame =
                Math.max(
                        0,
                        Math.min(
                                animationTestFrame,
                                maxFrame
                        )
                );

        if (safeFrame != animationTestFrame) {
            setAnimationTestFrame(safeFrame);
        }
    }

    void handleBrowseRequest() {
        int animationDelta =
                pendingAnimationIdDelta;

        int frameDelta =
                pendingAnimationFrameDelta;

        pendingAnimationIdDelta = 0;
        pendingAnimationFrameDelta = 0;

        boolean visibleConfigChanged = false;

        if (animationDelta != 0) {
            int animationId =
                    animationTestId;

            final int direction =
                    Integer.signum(animationDelta);

            for (int i = 0;
                    i < Math.abs(animationDelta);
                    i++) {

                final int nextAnimationId =
                        findNextValidAnimationId(
                                animationId,
                                direction
                        );

                if (nextAnimationId == animationId) {
                    break;
                }

                animationId = nextAnimationId;
            }

            if (animationId != animationTestId) {
                setAnimationTestId(animationId);
                visibleConfigChanged = true;

                final Animation animation =
                        loadSafeAnimation(animationId);

                final int oldFrame = animationTestFrame;
                clampAnimationTestFrame(animation);
                visibleConfigChanged |=
                        animationTestFrame != oldFrame;
            }
        }

        if (frameDelta != 0) {
            final Animation animation =
                    loadSafeAnimation(
                            animationTestId
                    );

            if (animation != null) {
                final int maxFrame =
                        getSafeMaxFrame(animation);

                animationBrowseMaxFrame = maxFrame;

                final int safeFrame =
                        Math.max(
                                0,
                                Math.min(
                                        animationTestFrame
                                                + frameDelta,
                                        maxFrame
                                )
                        );

                if (safeFrame != animationTestFrame) {
                    setAnimationTestFrame(safeFrame);
                    visibleConfigChanged = true;
                }
            }
        }

        if (visibleConfigChanged) {
            refreshOpenConfigPanel.run();
        }
    }

    private void updateInterpolationFilter(int animationId) {
        /*
         * Full action-animation playback keeps RuneLite Animation Smoothing
         * completely untouched.
         */
        if (!config.freezeFrame()) {
            restoreInterpolationFilter();
            return;
        }

        final IntPredicate currentFilter =
                client.getAnimationInterpolationFilter();

        /*
         * Animation Smoothing is off (or there is no active interpolation
         * filter). There is nothing for SkateScape to suppress.
         */
        if (!interpolationFilterInstalled
                && currentFilter == null) {

            previousInterpolationFilter = null;
            interpolationExcludedId = -1;
            return;
        }

        /*
         * If another plugin replaces the filter while the tester wrapper is
         * active, drop ownership instead of restoring stale state.
         */
        if (interpolationFilterInstalled
                && currentFilter != interpolationFilter) {

            previousInterpolationFilter = currentFilter;
            interpolationFilter = null;
            interpolationFilterInstalled = false;
            interpolationExcludedId = -1;
        }

        /*
         * Same frozen animation with the wrapper still installed; no rebuild
         * is needed.
         */
        if (interpolationFilterInstalled
                && currentFilter == interpolationFilter
                && interpolationExcludedId == animationId) {

            return;
        }

        /*
         * When the tested animation changes while frozen, rebuild from the
         * original RuneLite filter instead of the previous wrapper.
         */
        if (interpolationFilterInstalled
                && currentFilter == interpolationFilter) {

            client.setAnimationInterpolationFilter(
                    previousInterpolationFilter
            );

            interpolationFilter = null;
            interpolationFilterInstalled = false;
            interpolationExcludedId = -1;
        }

        final IntPredicate baseFilter =
                client.getAnimationInterpolationFilter();

        if (baseFilter == null) {
            previousInterpolationFilter = null;
            return;
        }

        previousInterpolationFilter =
                baseFilter;

        interpolationExcludedId =
                animationId;

        interpolationFilter =
                candidateAnimationId ->
                        candidateAnimationId != animationId
                                && baseFilter.test(candidateAnimationId);

        client.setAnimationInterpolationFilter(
                interpolationFilter
        );

        interpolationFilterInstalled = true;
    }

    void restoreInterpolationFilter() {
        if (!interpolationFilterInstalled) {
            previousInterpolationFilter = null;
            interpolationFilter = null;
            interpolationExcludedId = -1;
            return;
        }

        /*
         * Restore only while this wrapper is still installed. Do not overwrite
         * a filter installed later by another plugin.
         */
        if (client.getAnimationInterpolationFilter()
                == interpolationFilter) {

            client.setAnimationInterpolationFilter(
                    previousInterpolationFilter
            );
        }

        previousInterpolationFilter = null;
        interpolationFilter = null;
        interpolationFilterInstalled = false;
        interpolationExcludedId = -1;
    }

    void update(Player player) {
        int animationId =
                animationTestId;

        /*
         * Protect against old saved values or manual edits outside the
         * current cache range. Never apply an unchecked animation ID to
         * the player.
         */
        if (animationId < 0
                || animationId > maxSafeAnimationId) {

            animationId =
                    Math.max(
                            0,
                            Math.min(
                                    animationId,
                                    maxSafeAnimationId
                            )
                    );

            Animation animation =
                    loadSafeAnimation(animationId);

            if (animation == null) {
                int fallback =
                        findNextValidAnimationId(
                                animationId,
                                -1
                        );

                if (fallback == animationId) {
                    fallback =
                            findNextValidAnimationId(
                                    animationId,
                                    1
                            );
                }

                animationId = fallback;
                animation = loadSafeAnimation(animationId);
            }

            if (animation == null) {
                return;
            }

            setAnimationTestId(animationId);
            clampAnimationTestFrame(animation);
            return;
        }

        final Animation animation =
                loadSafeAnimation(animationId);

        if (animation == null) {
            /*
             * A hole/nonexistent sequence inside the numeric range is
             * simply ignored rather than being applied to the player.
             * Arrow browsing will skip over it automatically.
             */
            animationBrowseMaxFrame = -1;
            restoreInterpolationFilter();
            return;
        }

        clampAnimationTestFrame(animation);
        updateInterpolationFilter(animationId);

        final int safeFrame =
                Math.max(
                        0,
                        Math.min(
                                animationTestFrame,
                                getSafeMaxFrame(animation)
                        )
                );

        /*
         * Simplified Animation Testing modes:
         *
         * Freeze frame ON  -> exact pose-frame, smoothing suppressed only for
         *                     this tested animation
         * Freeze frame OFF -> normal action animation with RuneLite smoothing
         *                     untouched
         */
        if (config.freezeFrame()) {
            if (player.getPoseAnimation() != animationId) {
                player.setPoseAnimation(animationId);
            }

            player.setPoseAnimationFrame(safeFrame);
        } else {
            if (player.getAnimation() != animationId) {
                player.setAnimation(animationId);
            }
        }
    }
}
