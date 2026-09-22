package com.skatescape;

import java.util.Locale;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

/**
 * Owns SkateScape's hidden per-trick storage, editor synchronization,
 * canonical defaults and config-change routing.
 */
final class PerTrickConfigController {
    private final SkateScapeConfig config;
    private final ConfigManager configManager;
    private final SkateScapePlugin host;

    /* Developer inspector modes are mutually exclusive. */
    private boolean syncingInspectorModes;

    PerTrickConfigController(
            SkateScapeConfig config,
            ConfigManager configManager,
            SkateScapePlugin host) {

        this.config = config;
        this.configManager = configManager;
        this.host = host;
    }

    void initializeStorage() {
        initializeCurrentPerTrickStorage();
        syncAllEditorsFromSelection();
    }

    /*
     * PER-TRICK EDITORS.
     *
     * RuneLite config items are static, so each editor section uses one
     * visible set of controls plus hidden per-trick backing keys. Changing a
     * section's Trick dropdown swaps the visible values immediately. Runtime
     * playback always reads the backing values for the trick actually being
     * performed, regardless of which editor is currently open.
     */
    private boolean syncingPerTrickEditor;

    private String perTrickKey(
            int slot,
            String family,
            String field) {

        return "v132_t"
                + clampInt(slot, 1, 5)
                + "_"
                + family
                + "_"
                + field;
    }

    private String getConfigString(
            String key,
            String fallback) {

        final String value =
                configManager.getConfiguration(
                        "skatescape",
                        key
                );

        return value == null
                || value.trim().isEmpty()
                        ? fallback
                        : value;
    }

    private int getConfigInt(
            String key,
            int fallback) {

        final String value =
                configManager.getConfiguration(
                        "skatescape",
                        key
                );

        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double getConfigDouble(
            String key,
            double fallback) {

        final String value =
                configManager.getConfiguration(
                        "skatescape",
                        key
                );

        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean getConfigBoolean(
            String key,
            boolean fallback) {

        final String value =
                configManager.getConfiguration(
                        "skatescape",
                        key
                );

        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return Boolean.parseBoolean(value.trim());
    }

    private void ensureConfigValue(
            String key,
            Object value) {

        final String existing =
                configManager.getConfiguration(
                        "skatescape",
                        key
                );

        if (existing == null
                || existing.trim().isEmpty()) {

            configManager.setConfiguration(
                    "skatescape",
                    key,
                    value
            );
        }
    }
    private int defaultTuningInt(
            int slot,
            String field) {

        switch (field) {
            case "durationMs":
                return defaultMainDurationMs(slot);
            case "popHeight":
                if (slot == 5) {
                    return 128;
                }
                return slot == 4 ? 50 : 60;
            default:
                return 0;
        }
    }

    /*
     * One trick duration owns the MAIN window. Board landmarks and advanced
     * pose timing are stored as percentages of that duration.
     */
    private int defaultMainDurationMs(int slot) {
        /* Tricks 4 and 5 use a 1420 ms canonical MAIN duration. */
        if (slot == 4) {
            return 1420;
        }

        if (slot == 5) {
            return 1420;
        }

        return 900;
    }

    private int defaultMainCycles(int slot) {
        return Math.max(
                1,
                (int) Math.round(defaultMainDurationMs(slot) / 20.0)
        );
    }

    /*
     * Queue controls use exact full-timeline cycles. Exit is the splice point
     * on the current trick; start is the entry point on the queued trick.
     */

    private int currentMainCyclesForQueueDefault(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);

        int targetCycles =
                Math.max(
                        1,
                        (int) Math.round(
                                Math.max(
                                        300,
                                        getTuningInt(safeSlot, "durationMs")
                                ) / 20.0
                        )
                );

        targetCycles =
                Math.max(
                        targetCycles,
                        SkateScapePlugin.DEFAULT_TRICK_JUMP_FRAMES.length
                );

        if (safeSlot == 4
                || !usesLegacyPlayerPose(safeSlot)) {

            targetCycles =
                    Math.max(
                            targetCycles,
                            clampInt(
                                    getPoseLength(safeSlot),
                                    1,
                                    maxPoseSequenceLength(safeSlot)
                            )
                    );
        }

        return targetCycles;
    }
    /*
     * DURATION-AWARE QUEUE POSITIONS.
     *
     * The visible editor still uses the exact complete-timeline cycle shown by
     * Trick Inspector. Hidden storage uses one compact normalized coordinate:
     *
     *   0.x = PRE, 1.x = MAIN, 2.x = landing seam, 3.x = RETURN
     *
     * The fractional part is the position inside that phase. Only MAIN changes
     * length with Trick duration, so a splice chosen while slowed down stays on
     * the same visual pose when normal speed is restored.
     */
    private int transitionCycles(
            int[] frames,
            int[] durationsMs) {

        int total = 0;
        for (int i = 0; i < frames.length; i++) {
            total += Math.max(
                    1,
                    (int) Math.round(
                            durationsMs[Math.min(i, durationsMs.length - 1)]
                                    / 20.0
                    )
            );
        }
        return Math.max(1, total);
    }

    private int[] queuePhaseCycles(int slot) {
        return new int[]{
                transitionCycles(
                        SkateScapePlugin.DEFAULT_TRICK_PRE_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_PRE_DURATIONS_MS
                ),
                currentMainCyclesForQueueDefault(slot),
                Math.max(
                        1,
                        (int) Math.round(
                                SkateScapePlugin.DEFAULT_TRICK_POST_SEAM_DURATION_MS
                                        / 20.0
                        )
                ),
                transitionCycles(
                        SkateScapePlugin.DEFAULT_TRICK_POST_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_POST_DURATIONS_MS
                )
        };
    }

    private String queuePositionKey(
            int slot,
            String side) {

        return perTrickKey(
                slot,
                "queue",
                side + "PositionV184"
        );
    }

    private boolean hasQueuePosition(
            int slot,
            String side) {

        final String value =
                configManager.getConfiguration(
                        "skatescape",
                        queuePositionKey(slot, side)
                );

        return value != null && !value.trim().isEmpty();
    }

    private void storeQueuePosition(
            int slot,
            String side,
            int requestedCycle) {

        final int safeSlot = clampInt(slot, 1, 5);
        final int[] lengths = queuePhaseCycles(safeSlot);
        int total = 0;
        for (int length : lengths) {
            total += length;
        }

        final int cycle =
                clampInt(
                        requestedCycle,
                        0,
                        Math.max(0, total - 1)
                );

        int phase = 0;
        int phaseStart = 0;
        while (phase < lengths.length - 1
                && cycle >= phaseStart + lengths[phase]) {
            phaseStart += lengths[phase++];
        }

        final double position =
                phase
                        + (cycle - phaseStart)
                                / (double) Math.max(1, lengths[phase]);

        configManager.setConfiguration(
                "skatescape",
                queuePositionKey(safeSlot, side),
                position
        );

        /* Keep the raw-cycle key as a readable/cache value. */
        setPerTrickConfig(
                safeSlot,
                "queue",
                side + "Cycle",
                cycle
        );
    }

    private int getQueuePositionCycle(
            int slot,
            String side,
            int rawFallback) {

        final int safeSlot = clampInt(slot, 1, 5);
        final int[] lengths = queuePhaseCycles(safeSlot);
        int total = 0;
        for (int length : lengths) {
            total += length;
        }

        if (!hasQueuePosition(safeSlot, side)) {
            return clampInt(rawFallback, 0, Math.max(0, total - 1));
        }

        final double position =
                clampDouble(
                        getConfigDouble(
                                queuePositionKey(safeSlot, side),
                                1.0
                        ),
                        0.0,
                        3.999999
                );

        final int phase =
                clampInt(
                        (int) Math.floor(position),
                        0,
                        lengths.length - 1
                );

        int phaseStart = 0;
        for (int i = 0; i < phase; i++) {
            phaseStart += lengths[i];
        }

        final int offset =
                clampInt(
                        (int) Math.round(
                                (position - phase)
                                        * lengths[phase]
                        ),
                        0,
                        Math.max(0, lengths[phase] - 1)
                );

        return clampInt(
                phaseStart + offset,
                0,
                Math.max(0, total - 1)
        );
    }
    /*
     * QUEUE DEFAULTS.
     *
     * These defaults were chosen visually at the canonical trick durations:
     * Tricks 1-3 use 900 ms, while Tricks 4-5 use 1420 ms. Store them as
     * the same phase-relative coordinates so changing Trick duration later
     * keeps the splice attached to the same visual point.
     */
    private int defaultQueuedExitCycle(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);
        return safeSlot >= 4 ? 70 : 56;
    }

    private int defaultQueuedStartCycle(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);

        if (safeSlot == 5) {
            return 30;
        }

        return safeSlot == 4 ? 25 : 23;
    }

    private int[] referenceQueuePhaseCycles(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);

        /*
         * Queue defaults are authored at each finished trick's canonical
         * duration: 900 ms for Tricks 1-3, 1420 ms for Tricks 4-5. Keep that
         * reference clock stable so the stored phase-relative position maps
         * back to the same Inspector cycle after a reset or fresh install.
         */
        final int referenceMainCycles =
                safeSlot >= 4
                        ? Math.max(1, (int) Math.round(1420 / 20.0))
                        : Math.max(1, (int) Math.round(900 / 20.0));

        return new int[]{
                transitionCycles(
                        SkateScapePlugin.DEFAULT_TRICK_PRE_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_PRE_DURATIONS_MS
                ),
                referenceMainCycles,
                Math.max(
                        1,
                        (int) Math.round(
                                SkateScapePlugin.DEFAULT_TRICK_POST_SEAM_DURATION_MS
                                        / 20.0
                        )
                ),
                transitionCycles(
                        SkateScapePlugin.DEFAULT_TRICK_POST_FRAMES,
                        SkateScapePlugin.DEFAULT_TRICK_POST_DURATIONS_MS
                )
        };
    }

    private double normalizedQueuePositionFromReference(
            int slot,
            int requestedReferenceCycle) {

        final int[] lengths = referenceQueuePhaseCycles(slot);
        int total = 0;
        for (int length : lengths) {
            total += length;
        }

        final int cycle =
                clampInt(
                        requestedReferenceCycle,
                        0,
                        Math.max(0, total - 1)
                );

        int phase = 0;
        int phaseStart = 0;
        while (phase < lengths.length - 1
                && cycle >= phaseStart + lengths[phase]) {
            phaseStart += lengths[phase++];
        }

        return phase
                + (cycle - phaseStart)
                        / (double) Math.max(1, lengths[phase]);
    }

    private void storeDefaultQueuePosition(
            int slot,
            String side) {

        final int safeSlot = clampInt(slot, 1, 5);
        final int referenceCycle =
                "exit".equals(side)
                        ? defaultQueuedExitCycle(safeSlot)
                        : defaultQueuedStartCycle(safeSlot);

        configManager.setConfiguration(
                "skatescape",
                queuePositionKey(safeSlot, side),
                normalizedQueuePositionFromReference(
                        safeSlot,
                        referenceCycle
                )
        );

        setPerTrickConfig(
                safeSlot,
                "queue",
                side + "Cycle",
                getQueuePositionCycle(
                        safeSlot,
                        side,
                        referenceCycle
                )
        );
    }
    private double defaultTuningPercent(
            int slot,
            String field) {

        final int mainCycles = defaultMainCycles(slot);
        final int oldCycle;

        if (slot == 5) {
            switch (field) {
                case "popStart":
                    return 5.0;
                case "kickStart":
                    return 10.0;
                case "kickEnd":
                    return 58.0;
                case "impossibleStart":
                    return 0.0;
                case "catch":
                    return 86.0;
                case "touchdown":
                    return 96.0;
                default:
                    return 0.0;
            }
        } else if (slot == 4) {
            switch (field) {
                case "popStart":
                    oldCycle = 6;
                    break;
                case "kickStart":
                    oldCycle = 8;
                    break;
                case "kickEnd":
                    oldCycle = 30;
                    break;
                case "impossibleStart":
                    oldCycle = 14;
                    break;
                case "catch":
                    oldCycle = 45;
                    break;
                case "touchdown":
                    oldCycle = 54;
                    break;
                default:
                    oldCycle = 0;
                    break;
            }
        } else {
            switch (field) {
                case "popStart":
                case "kickStart":
                    oldCycle = 15;
                    break;
                case "kickEnd":
                case "impossibleStart":
                case "catch":
                    oldCycle = 36;
                    break;
                case "touchdown":
                    oldCycle = 42;
                    break;
                default:
                    oldCycle = 0;
                    break;
            }
        }

        return roundPercent(
                cycleToPercent(oldCycle, mainCycles)
        );
    }

    private double defaultVarialShoveStartPercent() {
        return cycleToPercent(15, defaultMainCycles(3));
    }

    private double defaultVarialShoveEndPercent() {
        return cycleToPercent(36, defaultMainCycles(3));
    }

    private double defaultTrick5ShoveStartPercent() {
        return 34.0;
    }

    private double defaultTrick5ShoveEndPercent() {
        return 82.0;
    }

    private String defaultPoseTimingPercentages(int slot) {
        if (slot == 5) {
            /*
             * four fast 2306 launch poses, a somewhat readable Christ
             * Air hold :D, then the same 2306 poses mirrored back down. These
             * are relative weights; the runtime normalizes them to MAIN.
             */
            return "5,5,5,5,10,5,5,5,5";
        }

        if (slot != 4) {
            return "";
        }

        final int[] oldActualCycles = {
                3, 3, 3, 3,
                5, 5, 5, 5, 5, 5, 5,
                3, 3, 3, 3, 3, 3, 3, 3
        };

        return cycleWeightsToPercentList(oldActualCycles);
    }

    private static double cycleToPercent(
            int cycle,
            int totalCycles) {

        if (totalCycles <= 0) {
            return 0.0;
        }

        return clampDouble(
                cycle * 100.0 / totalCycles,
                0.0,
                100.0
        );
    }

    private static String formatPercent(double value) {
        String formatted = String.format(
                Locale.ROOT,
                "%.1f",
                roundPercent(value)
        );

        while (formatted.indexOf('.') >= 0
                && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        return formatted;
    }

    /*
     * the Trick Lab exposes percentage timing to one decimal place.
     * A 0.1% step is still far finer than one client cycle at the standard
     * trick durations, while keeping the editor cleaner and easier to tune.
     */
    private static double roundPercent(double value) {
        final double rounded = Math.round(value * 10.0) / 10.0;
        return Math.abs(rounded) < 0.05 ? 0.0 : rounded;
    }

    private static String formatPosePercentageList(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return "";
        }

        final String[] pieces = configured.split(",");
        final StringBuilder builder = new StringBuilder();

        try {
            for (int i = 0; i < pieces.length; i++) {
                final double value = Double.parseDouble(pieces[i].trim());

                if (!Double.isFinite(value)
                        || value < 0.0
                        || value > 100.0) {
                    return configured.trim();
                }

                if (i > 0) {
                    builder.append(',');
                }

                builder.append(formatPercent(value));
            }
        } catch (NumberFormatException ignored) {
            /*
             * Do not fight the user while they are editing an incomplete
             * text value. The timeline parser will keep its existing fallback
             * behavior until the text becomes valid again.
             */
            return configured.trim();
        }

        return builder.toString();
    }

    private static String cycleWeightsToPercentList(int[] cycles) {
        if (cycles == null || cycles.length == 0) {
            return "";
        }

        int total = 0;
        for (int cycle : cycles) {
            total += Math.max(1, cycle);
        }

        total = Math.max(1, total);

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cycles.length; i++) {
            if (i > 0) {
                builder.append(',');
            }

            builder.append(
                    formatPercent(
                            Math.max(1, cycles[i]) * 100.0 / total
                    )
            );
        }

        return builder.toString();
    }

    private static double clampDouble(
            double value,
            double min,
            double max) {

        if (max < min) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }

    int normalizeShoveItDegrees(int degrees) {
        return degrees < 270 ? 180 : 360;
    }

    int getKickflipDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(1, "tune", "kickDegreesV147"),
                        360
                )
        );
    }

    private void setKickflipDegrees(int degrees) {
        setPerTrickConfig(
                1,
                "tune",
                "kickDegreesV147",
                normalizeShoveItDegrees(degrees)
        );
    }

    int getTrick4KickflipDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(4, "tune", "kickDegreesV147"),
                        360
                )
        );
    }

    private void setTrick4KickflipDegrees(int degrees) {
        setPerTrickConfig(
                4,
                "tune",
                "kickDegreesV147",
                normalizeShoveItDegrees(degrees)
        );
    }

    int getTrick4ImpossibleDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(4, "tune", "impossibleDegreesV147"),
                        360
                )
        );
    }

    private void setTrick4ImpossibleDegrees(int degrees) {
        setPerTrickConfig(
                4,
                "tune",
                "impossibleDegreesV147",
                normalizeShoveItDegrees(degrees)
        );
    }

    int normalizeTrick5RotationDegrees(int degrees) {
        final int steps =
                clampInt(
                        (int) Math.round(degrees / 180.0),
                        1,
                        4
                );

        return steps * 180;
    }

    int getTrick5KickflipDegrees() {
        return normalizeTrick5RotationDegrees(
                getConfigInt(
                        perTrickKey(5, "tune", "kickDegreesV146"),
                        720
                )
        );
    }

    private void setTrick5KickflipDegrees(int degrees) {
        setPerTrickConfig(
                5,
                "tune",
                "kickDegreesV146",
                normalizeTrick5RotationDegrees(degrees)
        );
    }

    int getTrick5ShoveDegrees() {
        return normalizeTrick5RotationDegrees(
                getConfigInt(
                        perTrickKey(5, "tune", "shoveDegreesV146"),
                        720
                )
        );
    }

    private void setTrick5ShoveDegrees(int degrees) {
        setPerTrickConfig(
                5,
                "tune",
                "shoveDegreesV146",
                normalizeTrick5RotationDegrees(degrees)
        );
    }

    int getShoveItDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(2, "tune", "shoveDegreesV139"),
                        360
                )
        );
    }

    private void setShoveItDegrees(int degrees) {
        setPerTrickConfig(
                2,
                "tune",
                "shoveDegreesV139",
                normalizeShoveItDegrees(degrees)
        );
    }

    /*
     * Varial Kickflip now exposes an independent 180/360 Kickflip
     * rotation selector, matching its already-independent Shove-it selector.
     */
    int getVarialKickflipDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(3, "tune", "varialKickDegreesV149"),
                        360
                )
        );
    }

    private void setVarialKickflipDegrees(int degrees) {
        setPerTrickConfig(
                3,
                "tune",
                "varialKickDegreesV149",
                normalizeShoveItDegrees(degrees)
        );
    }

    /*
     * Varial Kickflip keeps its Kickflip and Shove-it clocks separate. The
     * default 180-degree shove shares the Kickflip's 15 -> 36 window, while
     * the editor can move either component independently.
     */

    int getVarialShoveDegrees() {
        return normalizeShoveItDegrees(
                getConfigInt(
                        perTrickKey(3, "tune", "varialShoveDegreesV142"),
                        180
                )
        );
    }

    private void setVarialShoveDegrees(int degrees) {
        setPerTrickConfig(
                3,
                "tune",
                "varialShoveDegreesV142",
                normalizeShoveItDegrees(degrees)
        );
    }

    private String defaultPoseAnimationIds(int slot) {
        /*
         * The pose parser repeats the final supplied animation ID for the
         * remaining active poses, so a single ID is the clean canonical form
         * when the whole sequence comes from one animation. Trick 5 is a
         * deliberately authored mixed-animation sequence.
         */
        if (slot == 4) {
            return Integer.toString(AnimationID.DARK_SPEC_PLAYER);
        }

        if (slot == 5) {
            return AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.VAMP_LAND + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH + ","
                    + AnimationID.HUMAN_APPLECRUSH;
        }

        return Integer.toString(AnimationID.HUMAN_JUMP_HURDLE);
    }

    private String defaultPoseFrames(int slot) {
        if (slot == 4) {
            return "0,1,2,3,4,5,6,7,8,9,10,11,12,13,19,20,21,22,23";
        }

        if (slot == 5) {
            return "0,1,2,3,8,3,2,1,0";
        }

        return "0,1,2,3,4,5,6,7,8,9,10,12";
    }

    private String defaultPoseDurations(int slot) {
        if (slot == 4) {
            return "56,56,56,56,90,90,90,90,90,90,90,56,56,56,56,56,56,56,56";
        }

        /*
         * Advanced Pose Timing is stored as percentages. This compatibility
         * fallback remains blank for non-Trick-4 profiles; the active
         * default lives in defaultPoseTimingPercentages().
         */
        return "";
    }

    private int defaultPoseLength(int slot) {
        if (slot == 4) {
            return 19;
        }

        return slot == 5 ? 9 : 12;
    }

    int maxPoseSequenceLength(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);

        if (safeSlot == 4) {
            return 19;
        }

        if (safeSlot == 5) {
            return 9;
        }

        return 12;
    }

    private boolean defaultAdvancedPoseTiming(int slot) {
        return slot == 4 || slot == 5;
    }

    /*
     * Tricks 1-3 use the built-in 1603 pose path until their pose controls are
     * edited. Leave custom arrays null while the default setup is unchanged.
     */
    boolean usesLegacyPlayerPose(int slot) {
        if (slot < 1 || slot > 3) {
            return false;
        }

        return getPoseLength(slot) == defaultPoseLength(slot)
                && defaultPoseAnimationIds(slot).equals(getPoseAnimations(slot))
                && defaultPoseFrames(slot).equals(getPoseFrames(slot))
                && !getAdvancedPoseTiming(slot);
    }

    /*
     * Existing Tricks 1-3 configs can retain frame-derived board timing when
     * the compatibility flag is present. Fresh release defaults use the
     * percentage-based board clock.
     */
    boolean usesApprovedLegacyBoardTiming(int slot) {
        if (slot < 1 || slot > 3) {
            return false;
        }

        return getConfigBoolean(
                perTrickKey(slot, "tune", "legacyBoardV135"),
                false
        );
    }

    private void setApprovedLegacyBoardTiming(
            int slot,
            boolean enabled) {

        if (slot < 1 || slot > 3) {
            return;
        }

        setPerTrickConfig(
                slot,
                "tune",
                "legacyBoardV135",
                enabled
        );
    }

    void storeExactLegacyBoardCycles(int slot) {
        if (slot < 1 || slot > 3) {
            return;
        }

        final int popStart =
                host.getCycleOffsetAtActiveTrickFrame(
                        SkateScapePlugin.DEFAULT_TRICK_POP_START_FRAME
                );

        final int kickStart =
                host.getCycleOffsetAtActiveTrickFrame(
                        SkateScapePlugin.DEFAULT_TRICK_FLIP_START_FRAME
                );

        /* Catch happens after native 1603 frame 8. */
        final int catchCycle =
                host.getCycleOffsetAfterActiveTrickFrame(8);

        /* Native frame 11 is skipped, so this resolves to frame 12's start. */
        final int touchdown =
                host.getCycleOffsetAtActiveTrickFrame(
                        SkateScapePlugin.DEFAULT_TRICK_POP_END_FRAME
                );

        setPerTrickConfig(slot, "tune", "popStart", popStart);
        setPerTrickConfig(slot, "tune", "kickStart", kickStart);
        setPerTrickConfig(slot, "tune", "kickEnd", catchCycle);
        setPerTrickConfig(slot, "tune", "impossibleStart", catchCycle);
        setPerTrickConfig(slot, "tune", "catch", catchCycle);
        setPerTrickConfig(slot, "tune", "touchdown", touchdown);
    }

    private void initializeCurrentPerTrickStorage() {
        for (int slot = 1; slot <= 5; slot++) {
            if (slot <= 3) {
                /*
                 * The finished release uses the percentage-based board clock.
                 * Keep the old storage key for compatibility with the runtime
                 * and developer tooling, but fresh installs start in current mode.
                 */
                ensureConfigValue(
                        perTrickKey(slot, "tune", "legacyBoardV135"),
                        false
                );
            }

            if (slot == 1) {
                ensureConfigValue(
                        perTrickKey(1, "tune", "kickDegreesV147"),
                        360
                );
            } else if (slot == 2) {
                ensureConfigValue(
                        perTrickKey(2, "tune", "shoveDegreesV139"),
                        360
                );
            } else if (slot == 3) {
                ensureConfigValue(
                        perTrickKey(3, "tune", "varialKickDegreesV149"),
                        360
                );
                ensureConfigValue(
                        perTrickKey(3, "tune", "varialShoveStartV142"),
                        roundPercent(defaultVarialShoveStartPercent())
                );
                ensureConfigValue(
                        perTrickKey(3, "tune", "varialShoveEndV142"),
                        roundPercent(defaultVarialShoveEndPercent())
                );
                ensureConfigValue(
                        perTrickKey(3, "tune", "varialShoveDegreesV142"),
                        180
                );
            } else if (slot == 4) {
                ensureConfigValue(
                        perTrickKey(4, "tune", "kickDegreesV147"),
                        360
                );
                ensureConfigValue(
                        perTrickKey(4, "tune", "impossibleDegreesV147"),
                        360
                );
            } else {
                ensureConfigValue(
                        perTrickKey(5, "tune", "kickDegreesV146"),
                        720
                );
                ensureConfigValue(
                        perTrickKey(5, "tune", "shoveStartV146"),
                        defaultTrick5ShoveStartPercent()
                );
                ensureConfigValue(
                        perTrickKey(5, "tune", "shoveEndV146"),
                        defaultTrick5ShoveEndPercent()
                );
                ensureConfigValue(
                        perTrickKey(5, "tune", "shoveDegreesV146"),
                        720
                );
            }

            ensureConfigValue(
                    perTrickKey(slot, "tune", "durationMs"),
                    defaultMainDurationMs(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "tune", "popHeight"),
                    defaultTuningInt(slot, "popHeight")
            );

            for (String field : new String[]{
                    "popStart",
                    "kickStart",
                    "kickEnd",
                    "impossibleStart",
                    "catch",
                    "touchdown"
            }) {
                ensureConfigValue(
                        perTrickKey(slot, "tune", field),
                        defaultTuningPercent(slot, field)
                );
            }

            ensureConfigValue(
                    perTrickKey(slot, "pose", "length"),
                    defaultPoseLength(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "pose", "animations"),
                    defaultPoseAnimationIds(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "pose", "frames"),
                    defaultPoseFrames(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "timing", "advanced"),
                    defaultAdvancedPoseTiming(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "timing", "durations"),
                    defaultPoseTimingPercentages(slot)
            );
            ensureConfigValue(
                    perTrickKey(slot, "inspect", "cycle"),
                    0
            );

            if (!hasQueuePosition(slot, "exit")) {
                storeDefaultQueuePosition(slot, "exit");
            }
            if (!hasQueuePosition(slot, "start")) {
                storeDefaultQueuePosition(slot, "start");
            }
        }

        final String kickflip = SkateScapeConfig.TrickSelection.KICKFLIP.name();
        ensureConfigValue("tuningSelectedTrickV132", kickflip);
        ensureConfigValue("inspectorSelectedTrickV132", kickflip);
        ensureConfigValue("poseSelectedTrickV132", kickflip);
        ensureConfigValue("poseTimingSelectedTrickV132", kickflip);
    }
    private boolean allCommaSeparatedValuesEqual(
            String value,
            String expected) {

        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        final String[] pieces = value.split(",");
        if (pieces.length == 0) {
            return false;
        }

        for (String piece : pieces) {
            if (!expected.equals(piece.trim())) {
                return false;
            }
        }

        return true;
    }
    /*
     * A focus-loss write can arrive after the editor switches tricks. Clear
     * stale Trick 4 pose durations before they can leak into another profile.
     */
    private void sanitizeUnusedPoseDurations(int slot) {
        if (slot < 1
                || slot > 5
                || slot == 4
                || getAdvancedPoseTiming(slot)) {
            return;
        }

        final String durationsKey =
                perTrickKey(slot, "timing", "durations");

        final String durations =
                configManager.getConfiguration(
                        "skatescape",
                        durationsKey
                );

        final String trimmed =
                durations == null ? "" : durations.trim();

        if (allCommaSeparatedValuesEqual(trimmed, "75")
                || defaultPoseDurations(4).equals(trimmed)) {
            configManager.setConfiguration(
                    "skatescape",
                    durationsKey,
                    ""
            );
        }
    }

    /*
     * A right-click Reset on one shared editor control first UNSETS that
     * visible key. Catch that unset before RuneLite applies the interface's
     * static Trick-4 default, and restore the selected trick's own default to
     * both its backing storage and the visible editor key.
     */
    private boolean handleSelectedEditorItemReset(String key) {
        if (key == null) {
            return false;
        }

        final int tuningSlot =
                config.tuningSelectedTrick().getSlot();

        switch (key) {
            case "tuningDurationMsV132":
                setPerTrickConfig(tuningSlot, "tune", "durationMs", defaultMainDurationMs(tuningSlot));
                syncTuningEditorFromSelection();
                return true;
            case "tuningPopHeightV132":
                setPerTrickConfig(tuningSlot, "tune", "popHeight", defaultTuningInt(tuningSlot, "popHeight"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningPopStartCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "popStart", defaultTuningPercent(tuningSlot, "popStart"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningKickflipStartCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "kickStart", defaultTuningPercent(tuningSlot, "kickStart"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningKickflipEndCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "kickEnd", defaultTuningPercent(tuningSlot, "kickEnd"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningTrick5KickflipDegreesV146":
                if (tuningSlot == 1) {
                    setKickflipDegrees(360);
                } else if (tuningSlot == 3) {
                    setVarialKickflipDegrees(360);
                } else if (tuningSlot == 4) {
                    setTrick4KickflipDegrees(360);
                } else if (tuningSlot == 5) {
                    setTrick5KickflipDegrees(720);
                }
                syncTuningEditorFromSelection();
                return true;
            case "tuningTrick4ImpossibleDegreesV147":
                if (tuningSlot == 4) {
                    setTrick4ImpossibleDegrees(360);
                }
                syncTuningEditorFromSelection();
                return true;
            case "tuningImpossibleStartCycleV132":
                if (tuningSlot == 2) {
                    setShoveItDegrees(360);
                } else if (tuningSlot == 3) {
                    setVarialShoveDegrees(180);
                } else if (tuningSlot == 4) {
                    setPerTrickConfig(
                            4,
                            "tune",
                            "impossibleStart",
                            defaultTuningPercent(4, "impossibleStart")
                    );
                } else if (tuningSlot == 5) {
                    setTrick5ShoveDegrees(720);
                }
                syncTuningEditorFromSelection();
                return true;
            case "tuningVarialShoveStartCycleV142":
                if (tuningSlot == 3) {
                    setVarialShoveStartPercent(defaultVarialShoveStartPercent());
                } else if (tuningSlot == 5) {
                    setTrick5ShoveStartPercent(defaultTrick5ShoveStartPercent());
                }
                syncTuningEditorFromSelection();
                return true;
            case "tuningVarialShoveEndCycleV142":
                if (tuningSlot == 3) {
                    setVarialShoveEndPercent(defaultVarialShoveEndPercent());
                } else if (tuningSlot == 5) {
                    setTrick5ShoveEndPercent(defaultTrick5ShoveEndPercent());
                }
                syncTuningEditorFromSelection();
                return true;
            case "tuningCatchCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "catch", defaultTuningPercent(tuningSlot, "catch"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningTouchdownCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "touchdown", defaultTuningPercent(tuningSlot, "touchdown"));
                syncTuningEditorFromSelection();
                return true;
            case "tuningQueuedExitCycleV182":
                storeDefaultQueuePosition(tuningSlot, "exit");
                syncTuningEditorFromSelection();
                return true;
            case "tuningQueuedStartCycleV182":
                storeDefaultQueuePosition(tuningSlot, "start");
                syncTuningEditorFromSelection();
                return true;
            default:
                break;
        }

        final int poseSlot =
                config.poseSelectedTrick().getSlot();

        switch (key) {
            case "poseSequenceLengthV132":
                setPerTrickConfig(poseSlot, "pose", "length", defaultPoseLength(poseSlot));
                syncPoseEditorFromSelection();
                return true;
            case "poseAnimationIdsV132":
                setPerTrickConfig(poseSlot, "pose", "animations", defaultPoseAnimationIds(poseSlot));
                syncPoseEditorFromSelection();
                return true;
            case "poseFramesV132":
                setPerTrickConfig(poseSlot, "pose", "frames", defaultPoseFrames(poseSlot));
                syncPoseEditorFromSelection();
                return true;
            default:
                break;
        }

        final int timingSlot =
                config.poseTimingSelectedTrick().getSlot();

        switch (key) {
            case "advancedPoseTimingV132":
                setPerTrickConfig(timingSlot, "timing", "advanced", defaultAdvancedPoseTiming(timingSlot));
                syncPoseTimingEditorFromSelection();
                return true;
            case "poseDurationsMsV132":
                setPerTrickConfig(timingSlot, "timing", "durations", defaultPoseTimingPercentages(timingSlot));
                syncPoseTimingEditorFromSelection();
                return true;
            default:
                break;
        }

        if ("trickInspectorCycleV132".equals(key)) {
            final int inspectorSlot =
                    config.inspectorSelectedTrick().getSlot();

            setPerTrickConfig(inspectorSlot, "inspect", "cycle", 0);
            syncInspectorEditorFromSelection();
            return true;
        }

        return false;
    }

    int getTuningInt(
            int slot,
            String field) {

        return getConfigInt(
                perTrickKey(slot, "tune", field),
                defaultTuningInt(slot, field)
        );
    }

    int getQueuedExitCycle(int slot) {
        return getQueuePositionCycle(
                slot,
                "exit",
                getConfigInt(
                        perTrickKey(slot, "queue", "exitCycle"),
                        defaultQueuedExitCycle(slot)
                )
        );
    }

    int getQueuedStartCycle(int slot) {
        return getQueuePositionCycle(
                slot,
                "start",
                getConfigInt(
                        perTrickKey(slot, "queue", "startCycle"),
                        defaultQueuedStartCycle(slot)
                )
        );
    }

    double getTuningPercent(
            int slot,
            String field) {

        return roundPercent(
                clampDouble(
                        getConfigDouble(
                                perTrickKey(slot, "tune", field),
                                defaultTuningPercent(slot, field)
                        ),
                        0.0,
                        100.0
                )
        );
    }

    double getVarialShoveStartPercent() {
        return roundPercent(
                clampDouble(
                        getConfigDouble(
                                perTrickKey(3, "tune", "varialShoveStartV142"),
                                defaultVarialShoveStartPercent()
                        ),
                        0.0,
                        100.0
                )
        );
    }

    double getVarialShoveEndPercent() {
        return roundPercent(
                clampDouble(
                        getConfigDouble(
                                perTrickKey(3, "tune", "varialShoveEndV142"),
                                defaultVarialShoveEndPercent()
                        ),
                        0.0,
                        100.0
                )
        );
    }

    private void setVarialShoveStartPercent(double percent) {
        setPerTrickConfig(
                3,
                "tune",
                "varialShoveStartV142",
                roundPercent(clampDouble(percent, 0.0, 100.0))
        );
    }

    private void setVarialShoveEndPercent(double percent) {
        setPerTrickConfig(
                3,
                "tune",
                "varialShoveEndV142",
                roundPercent(clampDouble(percent, 0.0, 100.0))
        );
    }

    double getTrick5ShoveStartPercent() {
        return roundPercent(
                clampDouble(
                        getConfigDouble(
                                perTrickKey(5, "tune", "shoveStartV146"),
                                defaultTrick5ShoveStartPercent()
                        ),
                        0.0,
                        100.0
                )
        );
    }

    double getTrick5ShoveEndPercent() {
        return roundPercent(
                clampDouble(
                        getConfigDouble(
                                perTrickKey(5, "tune", "shoveEndV146"),
                                defaultTrick5ShoveEndPercent()
                        ),
                        0.0,
                        100.0
                )
        );
    }

    private void setTrick5ShoveStartPercent(double percent) {
        setPerTrickConfig(
                5,
                "tune",
                "shoveStartV146",
                roundPercent(clampDouble(percent, 0.0, 100.0))
        );
    }

    private void setTrick5ShoveEndPercent(double percent) {
        setPerTrickConfig(
                5,
                "tune",
                "shoveEndV146",
                roundPercent(clampDouble(percent, 0.0, 100.0))
        );
    }

    int getPoseLength(int slot) {
        return getConfigInt(
                perTrickKey(slot, "pose", "length"),
                defaultPoseLength(slot)
        );
    }

    String getPoseAnimations(int slot) {
        return getConfigString(
                perTrickKey(slot, "pose", "animations"),
                defaultPoseAnimationIds(slot)
        );
    }

    String getPoseFrames(int slot) {
        return getConfigString(
                perTrickKey(slot, "pose", "frames"),
                defaultPoseFrames(slot)
        );
    }

    boolean getAdvancedPoseTiming(int slot) {
        return getConfigBoolean(
                perTrickKey(slot, "timing", "advanced"),
                defaultAdvancedPoseTiming(slot)
        );
    }

    String getPoseTimingPercentages(int slot) {
        return formatPosePercentageList(
                getConfigString(
                        perTrickKey(slot, "timing", "durations"),
                        defaultPoseTimingPercentages(slot)
                )
        );
    }

    private int getInspectorCycle(int slot) {
        return getConfigInt(
                perTrickKey(slot, "inspect", "cycle"),
                0
        );
    }

    void setPerTrickConfig(
            int slot,
            String family,
            String field,
            Object value) {

        configManager.setConfiguration(
                "skatescape",
                perTrickKey(slot, family, field),
                value
        );
    }

    private void syncTuningEditorFromSelection() {
        final int slot =
                config.tuningSelectedTrick().getSlot();

        syncingPerTrickEditor = true;
        try {
            configManager.setConfiguration("skatescape", "tuningDurationMsV132", getTuningInt(slot, "durationMs"));
            configManager.setConfiguration("skatescape", "tuningPopHeightV132", getTuningInt(slot, "popHeight"));
            configManager.setConfiguration("skatescape", "tuningPopStartCycleV132", getTuningPercent(slot, "popStart"));
            configManager.setConfiguration("skatescape", "tuningKickflipStartCycleV132", getTuningPercent(slot, "kickStart"));
            configManager.setConfiguration("skatescape", "tuningKickflipEndCycleV132", getTuningPercent(slot, "kickEnd"));
            configManager.setConfiguration(
                    "skatescape",
                    "tuningTrick5KickflipDegreesV146",
                    slot == 1
                            ? getKickflipDegrees()
                            : slot == 3
                                    ? getVarialKickflipDegrees()
                                    : slot == 4
                                            ? getTrick4KickflipDegrees()
                                            : slot == 5
                                                    ? getTrick5KickflipDegrees()
                                                    : 180
            );
            configManager.setConfiguration(
                    "skatescape",
                    "tuningTrick4ImpossibleDegreesV147",
                    slot == 4
                            ? getTrick4ImpossibleDegrees()
                            : 360
            );
            configManager.setConfiguration(
                    "skatescape",
                    "tuningImpossibleStartCycleV132",
                    slot == 2
                            ? (double) getShoveItDegrees()
                            : slot == 3
                                    ? (double) getVarialShoveDegrees()
                                    : slot == 5
                                            ? (double) getTrick5ShoveDegrees()
                                            : getTuningPercent(slot, "impossibleStart")
            );
            configManager.setConfiguration(
                    "skatescape",
                    "tuningVarialShoveStartCycleV142",
                    slot == 5
                            ? getTrick5ShoveStartPercent()
                            : getVarialShoveStartPercent()
            );
            configManager.setConfiguration(
                    "skatescape",
                    "tuningVarialShoveEndCycleV142",
                    slot == 5
                            ? getTrick5ShoveEndPercent()
                            : getVarialShoveEndPercent()
            );
            configManager.setConfiguration("skatescape", "tuningCatchCycleV132", getTuningPercent(slot, "catch"));
            configManager.setConfiguration("skatescape", "tuningTouchdownCycleV132", getTuningPercent(slot, "touchdown"));
            configManager.setConfiguration("skatescape", "tuningQueuedExitCycleV182", getQueuedExitCycle(slot));
            configManager.setConfiguration("skatescape", "tuningQueuedStartCycleV182", getQueuedStartCycle(slot));
        } finally {
            syncingPerTrickEditor = false;
        }
    }

    private void syncPoseEditorFromSelection() {
        final int slot =
                config.poseSelectedTrick().getSlot();

        final int safeLength =
                clampInt(
                        getPoseLength(slot),
                        1,
                        maxPoseSequenceLength(slot)
                );

        if (safeLength != getPoseLength(slot)) {
            setPerTrickConfig(slot, "pose", "length", safeLength);
        }

        syncingPerTrickEditor = true;
        try {
            configManager.setConfiguration("skatescape", "poseSequenceLengthV132", safeLength);
            configManager.setConfiguration("skatescape", "poseAnimationIdsV132", getPoseAnimations(slot));
            configManager.setConfiguration("skatescape", "poseFramesV132", getPoseFrames(slot));
        } finally {
            syncingPerTrickEditor = false;
        }
    }

    private void syncPoseTimingEditorFromSelection() {
        final int slot =
                config.poseTimingSelectedTrick().getSlot();

        syncingPerTrickEditor = true;
        try {
            configManager.setConfiguration("skatescape", "advancedPoseTimingV132", getAdvancedPoseTiming(slot));
            configManager.setConfiguration("skatescape", "poseDurationsMsV132", getPoseTimingPercentages(slot));
        } finally {
            syncingPerTrickEditor = false;
        }
    }

    void syncInspectorEditorFromSelection() {
        final int slot =
                config.inspectorSelectedTrick().getSlot();

        syncingPerTrickEditor = true;
        try {
            configManager.setConfiguration("skatescape", "trickInspectorCycleV132", getInspectorCycle(slot));
        } finally {
            syncingPerTrickEditor = false;
        }
    }

    private void syncAllEditorsFromSelection() {
        syncTuningEditorFromSelection();
        syncPoseEditorFromSelection();
        syncPoseTimingEditorFromSelection();
        syncInspectorEditorFromSelection();
    }

    void onConfigChanged(ConfigChanged event) {
        if (!"skatescape".equals(event.getGroup())) {
            return;
        }

        final String key = event.getKey();

        /*
         * Per-item Reset unsets the shared editor key before RuneLite reapplies
         * interface defaults. Restore the SELECTED trick's own default first.
         */
        if (event.getNewValue() == null
                && handleSelectedEditorItemReset(key)) {

            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if (!syncingInspectorModes
                && "trickInspectorEnabledV132".equals(key)
                && config.trickInspectorEnabled()
                && config.animationTest()) {

            syncingInspectorModes = true;
            try {
                configManager.setConfiguration(
                        "skatescape",
                        "animationTest",
                        false
                );
            } finally {
                syncingInspectorModes = false;
            }

            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if (!syncingInspectorModes
                && "animationTest".equals(key)
                && config.animationTest()
                && config.trickInspectorEnabled()) {

            syncingInspectorModes = true;
            try {
                configManager.setConfiguration(
                        "skatescape",
                        "trickInspectorEnabledV132",
                        false
                );
            } finally {
                syncingInspectorModes = false;
            }

            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if ("tuningSelectedTrickV132".equals(key)) {
            host.suppressPerTrickEditorWrites();
            syncTuningEditorFromSelection();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if ("poseSelectedTrickV132".equals(key)) {
            host.suppressPerTrickEditorWrites();
            syncPoseEditorFromSelection();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if ("poseTimingSelectedTrickV132".equals(key)) {
            host.suppressPerTrickEditorWrites();
            sanitizeUnusedPoseDurations(
                    config.poseTimingSelectedTrick().getSlot()
            );
            syncPoseTimingEditorFromSelection();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if ("inspectorSelectedTrickV132".equals(key)) {
            host.suppressPerTrickEditorWrites();
            syncInspectorEditorFromSelection();
            host.invalidateTrickInspectorTuningSignature();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if (syncingPerTrickEditor
                || host.isSuppressingPerTrickEditorWrites()) {
            return;
        }

        final int tuningSlot =
                config.tuningSelectedTrick().getSlot();

        switch (key) {
            case "tuningDurationMsV132":
                setPerTrickConfig(tuningSlot, "tune", "durationMs", config.tuningDurationMs());

                /*
                 * queue positions are phase-relative, so changing MAIN
                 * duration changes the complete-timeline cycle numbers shown
                 * in the editor. Keep the old raw-cycle backing keys in sync
                 * as readable caches, then refresh the two visible spinners.
                 */
                setPerTrickConfig(
                        tuningSlot,
                        "queue",
                        "exitCycle",
                        getQueuedExitCycle(tuningSlot)
                );
                setPerTrickConfig(
                        tuningSlot,
                        "queue",
                        "startCycle",
                        getQueuedStartCycle(tuningSlot)
                );
                syncTuningEditorFromSelection();
                host.refreshOpenRuneLiteConfigPanel();
                return;
            case "tuningPopHeightV132":
                setPerTrickConfig(tuningSlot, "tune", "popHeight", config.tuningPopHeight());
                return;
            case "tuningPopStartCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "popStart", roundPercent(clampDouble(config.tuningPopStartPercent(), 0.0, 100.0)));
                return;
            case "tuningKickflipStartCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "kickStart", roundPercent(clampDouble(config.tuningKickflipStartPercent(), 0.0, 100.0)));
                return;
            case "tuningKickflipEndCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "kickEnd", roundPercent(clampDouble(config.tuningKickflipEndPercent(), 0.0, 100.0)));
                return;
            case "tuningTrick5KickflipDegreesV146":
                if (tuningSlot == 1 || tuningSlot == 3 || tuningSlot == 4) {
                    final int kickDegrees =
                            normalizeShoveItDegrees(
                                    config.tuningTrick5KickflipDegrees()
                            );

                    if (tuningSlot == 1) {
                        setKickflipDegrees(kickDegrees);
                    } else if (tuningSlot == 3) {
                        setVarialKickflipDegrees(kickDegrees);
                    } else {
                        setTrick4KickflipDegrees(kickDegrees);
                    }

                    if (kickDegrees != config.tuningTrick5KickflipDegrees()) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                } else if (tuningSlot == 5) {
                    final int kickDegrees =
                            normalizeTrick5RotationDegrees(
                                    config.tuningTrick5KickflipDegrees()
                            );

                    setTrick5KickflipDegrees(kickDegrees);

                    if (kickDegrees != config.tuningTrick5KickflipDegrees()) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                }
                return;
            case "tuningTrick4ImpossibleDegreesV147":
                if (tuningSlot == 4) {
                    final int impossibleDegrees =
                            normalizeShoveItDegrees(
                                    config.tuningTrick4ImpossibleDegrees()
                            );

                    setTrick4ImpossibleDegrees(impossibleDegrees);

                    if (impossibleDegrees
                            != config.tuningTrick4ImpossibleDegrees()) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                }
                return;
            case "tuningImpossibleStartCycleV132":
                if (tuningSlot == 2) {
                    final int shoveDegrees =
                            normalizeShoveItDegrees(
                                    (int) Math.round(config.tuningSecondaryValue())
                            );
                    setShoveItDegrees(shoveDegrees);
                    if (Math.abs(shoveDegrees - config.tuningSecondaryValue()) > 0.000001) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                } else if (tuningSlot == 3) {
                    final int varialDegrees =
                            normalizeShoveItDegrees(
                                    (int) Math.round(config.tuningSecondaryValue())
                            );
                    setVarialShoveDegrees(varialDegrees);
                    if (Math.abs(varialDegrees - config.tuningSecondaryValue()) > 0.000001) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                } else if (tuningSlot == 4) {
                    setPerTrickConfig(
                            4,
                            "tune",
                            "impossibleStart",
                            roundPercent(clampDouble(config.tuningSecondaryValue(), 0.0, 100.0))
                    );
                } else if (tuningSlot == 5) {
                    final int trick5ShoveDegrees =
                            normalizeTrick5RotationDegrees(
                                    (int) Math.round(config.tuningSecondaryValue())
                            );
                    setTrick5ShoveDegrees(trick5ShoveDegrees);
                    if (Math.abs(trick5ShoveDegrees - config.tuningSecondaryValue()) > 0.000001) {
                        syncTuningEditorFromSelection();
                        host.refreshOpenRuneLiteConfigPanel();
                    }
                }
                return;
            case "tuningVarialShoveStartCycleV142":
                if (tuningSlot == 3) {
                    setVarialShoveStartPercent(config.tuningVarialShoveStartPercent());
                } else if (tuningSlot == 5) {
                    setTrick5ShoveStartPercent(config.tuningVarialShoveStartPercent());
                }
                return;
            case "tuningVarialShoveEndCycleV142":
                if (tuningSlot == 3) {
                    setVarialShoveEndPercent(config.tuningVarialShoveEndPercent());
                } else if (tuningSlot == 5) {
                    setTrick5ShoveEndPercent(config.tuningVarialShoveEndPercent());
                }
                return;
            case "tuningCatchCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "catch", roundPercent(clampDouble(config.tuningCatchPercent(), 0.0, 100.0)));
                return;
            case "tuningTouchdownCycleV132":
                setPerTrickConfig(tuningSlot, "tune", "touchdown", roundPercent(clampDouble(config.tuningTouchdownPercent(), 0.0, 100.0)));
                return;
            case "tuningQueuedExitCycleV182": {
                final int requested =
                        clampInt(config.tuningQueuedExitCycle(), 0, 500);

                storeQueuePosition(
                        tuningSlot,
                        "exit",
                        requested
                );

                final int stored = getQueuedExitCycle(tuningSlot);
                if (stored != requested) {
                    syncTuningEditorFromSelection();
                    host.refreshOpenRuneLiteConfigPanel();
                }
                return;
            }
            case "tuningQueuedStartCycleV182": {
                final int requested =
                        clampInt(config.tuningQueuedStartCycle(), 0, 500);

                storeQueuePosition(
                        tuningSlot,
                        "start",
                        requested
                );

                final int stored = getQueuedStartCycle(tuningSlot);
                if (stored != requested) {
                    syncTuningEditorFromSelection();
                    host.refreshOpenRuneLiteConfigPanel();
                }
                return;
            }
            default:
                break;
        }

        final int poseSlot =
                config.poseSelectedTrick().getSlot();

        switch (key) {
            case "poseSequenceLengthV132":
                final int safePoseLength =
                        clampInt(
                                config.poseSequenceLength(),
                                1,
                                maxPoseSequenceLength(poseSlot)
                        );

                setPerTrickConfig(poseSlot, "pose", "length", safePoseLength);

                if (safePoseLength != config.poseSequenceLength()) {
                    syncPoseEditorFromSelection();
                    host.refreshOpenRuneLiteConfigPanel();
                }
                return;
            case "poseAnimationIdsV132":
                setPerTrickConfig(poseSlot, "pose", "animations", config.poseAnimationIds());
                return;
            case "poseFramesV132":
                setPerTrickConfig(poseSlot, "pose", "frames", config.poseFrames());
                return;
            default:
                break;
        }

        final int timingSlot =
                config.poseTimingSelectedTrick().getSlot();

        switch (key) {
            case "advancedPoseTimingV132":
                setPerTrickConfig(
                        timingSlot,
                        "timing",
                        "advanced",
                        config.advancedPoseTiming()
                );
                return;

            case "poseDurationsMsV132":
                setPerTrickConfig(
                        timingSlot,
                        "timing",
                        "durations",
                        formatPosePercentageList(
                                config.poseTimingPercentages()
                        )
                );
                return;

            default:
                break;
        }

        if ("trickInspectorCycleV132".equals(key)) {
            final int inspectorSlot =
                    config.inspectorSelectedTrick().getSlot();

            setPerTrickConfig(
                    inspectorSlot,
                    "inspect",
                    "cycle",
                    config.trickInspectorCycle()
            );
        }
    }

    private void resetPerTrickSlotToDefaults(int slot) {
        final int safeSlot = clampInt(slot, 1, 5);

        if (safeSlot <= 3) {
            setApprovedLegacyBoardTiming(safeSlot, false);
        }

        if (safeSlot == 1) {
            setKickflipDegrees(360);
        } else if (safeSlot == 2) {
            setShoveItDegrees(360);
        } else if (safeSlot == 3) {
            setVarialKickflipDegrees(360);
            setVarialShoveStartPercent(defaultVarialShoveStartPercent());
            setVarialShoveEndPercent(defaultVarialShoveEndPercent());
            setVarialShoveDegrees(180);
        } else if (safeSlot == 4) {
            setTrick4KickflipDegrees(360);
            setTrick4ImpossibleDegrees(360);
        } else if (safeSlot == 5) {
            setTrick5KickflipDegrees(720);
            setTrick5ShoveStartPercent(defaultTrick5ShoveStartPercent());
            setTrick5ShoveEndPercent(defaultTrick5ShoveEndPercent());
            setTrick5ShoveDegrees(720);
        }

        setPerTrickConfig(safeSlot, "tune", "durationMs", defaultMainDurationMs(safeSlot));
        setPerTrickConfig(safeSlot, "tune", "popHeight", defaultTuningInt(safeSlot, "popHeight"));
        setPerTrickConfig(safeSlot, "tune", "popStart", defaultTuningPercent(safeSlot, "popStart"));
        setPerTrickConfig(safeSlot, "tune", "kickStart", defaultTuningPercent(safeSlot, "kickStart"));
        setPerTrickConfig(safeSlot, "tune", "kickEnd", defaultTuningPercent(safeSlot, "kickEnd"));
        setPerTrickConfig(safeSlot, "tune", "impossibleStart", defaultTuningPercent(safeSlot, "impossibleStart"));
        setPerTrickConfig(safeSlot, "tune", "catch", defaultTuningPercent(safeSlot, "catch"));
        setPerTrickConfig(safeSlot, "tune", "touchdown", defaultTuningPercent(safeSlot, "touchdown"));

        setPerTrickConfig(safeSlot, "pose", "length", defaultPoseLength(safeSlot));
        setPerTrickConfig(safeSlot, "pose", "animations", defaultPoseAnimationIds(safeSlot));
        setPerTrickConfig(safeSlot, "pose", "frames", defaultPoseFrames(safeSlot));

        setPerTrickConfig(safeSlot, "timing", "advanced", defaultAdvancedPoseTiming(safeSlot));
        setPerTrickConfig(safeSlot, "timing", "durations", defaultPoseTimingPercentages(safeSlot));

        storeDefaultQueuePosition(safeSlot, "exit");
        storeDefaultQueuePosition(safeSlot, "start");

        setPerTrickConfig(safeSlot, "inspect", "cycle", 0);
    }

    void resetConfiguration() {
        /*
         * RuneLite's global Reset only knows declared ConfigItems. Per-trick
         * backing keys are programmatic, so reset all five editor profiles
         * here as well.
         */
        for (int slot = 1; slot <= 5; slot++) {
            resetPerTrickSlotToDefaults(slot);
        }

        host.clearPerTrickEditorWriteSuppression();
        syncAllEditorsFromSelection();
        host.refreshOpenRuneLiteConfigPanel();
    }

    private static int clampInt(
            int value,
            int min,
            int max) {

        if (max < min) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
