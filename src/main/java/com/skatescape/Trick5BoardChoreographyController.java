package com.skatescape;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

final class Trick5BoardChoreographyController {
    static final int FRAME_COUNT = 9;

    private static final String GROUP = "skatescape";

    private final SkateScapeConfig config;
    private final ConfigManager configManager;
    private final SkateScapePlugin host;

    private final int[] x = new int[FRAME_COUNT];
    private final int[] y = new int[FRAME_COUNT];
    private final int[] z = new int[FRAME_COUNT];
    private final int[] pitch = new int[FRAME_COUNT];
    private final int[] yaw = new int[FRAME_COUNT];
    private final int[] roll = new int[FRAME_COUNT];
    private final boolean[] locked = new boolean[FRAME_COUNT];

    private int preTransitionCycles;
    private int[] frameCycles;
    private int totalCycles;

    private boolean syncingEditor;
    private boolean syncingInspector;

    Trick5BoardChoreographyController(
            SkateScapeConfig config,
            ConfigManager configManager,
            SkateScapePlugin host) {

        this.config = config;
        this.configManager = configManager;
        this.host = host;
    }

    void initialize() {
        ensureAllStoredValues();
        loadCache();
        syncEditorFromSelection();
    }

    void reset() {
        for (int frame = 1; frame <= FRAME_COUNT; frame++) {
            setStored(frame, "x", defaultValue(frame, "x"));
            setStored(frame, "y", defaultValue(frame, "y"));
            setStored(frame, "z", defaultValue(frame, "z"));
            setStored(frame, "pitch", defaultValue(frame, "pitch"));
            setStored(frame, "yaw", defaultValue(frame, "yaw"));
            setStored(frame, "roll", defaultValue(frame, "roll"));
            setFrameLocked(frame, defaultLocked(frame));
        }

        loadCache();
        syncEditorFromSelection();
        host.refreshOpenRuneLiteConfigPanel();
    }

    void updateTimeline(
            int preTransitionCycles,
            int[] playerFrameCycles,
            int totalCycles) {

        this.preTransitionCycles = Math.max(0, preTransitionCycles);
        this.frameCycles = playerFrameCycles;
        this.totalCycles = Math.max(0, totalCycles);
    }

    void onConfigChanged(ConfigChanged event) {
        if (event == null
                || !GROUP.equals(event.getGroup())) {
            return;
        }

        final String key = event.getKey();

        if ("trick5BoardFrameSlotV197".equals(key)) {
            if (!syncingEditor) {
                syncEditorFromSelection();
                syncInspectorToSelectedFrame();
                host.refreshOpenRuneLiteConfigPanel();
            }
            return;
        }

        /*
         * Inspector browsing always follows the matching board frame. The lock
         * protects the XYZ/Pitch/Yaw/Roll values stored for the selected frame;
         * it does not lock the selector itself.
         */
        if ("trickInspectorCycleV132".equals(key)
                && !syncingInspector
                && config.tuningSelectedTrick() != null
                && config.tuningSelectedTrick().getSlot() == 5) {

            syncBoardFrameFromInspectorCycle();
            return;
        }

        if ("trick5BoardFrameLockV198".equals(key)) {
            if (syncingEditor) {
                return;
            }

            setFrameLocked(
                    selectedFrame(),
                    config.trick5BoardFrameLock()
            );

            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if (!isEditorValueKey(key)
                || syncingEditor) {
            return;
        }

        final int frame = selectedFrame();

        if (isFrameLocked(frame)) {
            syncEditorFromSelection();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        if (event.getNewValue() == null) {
            final String field = fieldForEditorKey(key);
            final int value = defaultValue(frame, field);

            saveEditorValue(frame, field, value);
            syncEditorFromSelection();
            host.refreshOpenRuneLiteConfigPanel();
            return;
        }

        switch (key) {
            case "trick5BoardFrameXV197":
                saveEditorValue(frame, "x", config.trick5BoardFrameX());
                break;
            case "trick5BoardFrameYV197":
                saveEditorValue(frame, "y", config.trick5BoardFrameY());
                break;
            case "trick5BoardFrameZV197":
                saveEditorValue(frame, "z", config.trick5BoardFrameZ());
                break;
            case "trick5BoardFramePitchV197":
                saveEditorValue(frame, "pitch", config.trick5BoardFramePitch());
                break;
            case "trick5BoardFrameYawV197":
                saveEditorValue(frame, "yaw", config.trick5BoardFrameYaw());
                break;
            case "trick5BoardFrameRollV197":
                saveEditorValue(frame, "roll", config.trick5BoardFrameRoll());
                break;
            default:
                break;
        }
    }

    int[] getX() {
        return x;
    }

    int[] getY() {
        return y;
    }

    int[] getZ() {
        return z;
    }

    int[] getPitch() {
        return pitch;
    }

    int[] getYaw() {
        return yaw;
    }

    int[] getRoll() {
        return roll;
    }

    boolean[] getLocked() {
        return locked;
    }

    private void syncBoardFrameFromInspectorCycle() {
        final int frame = frameForInspectorCycle(
                config.trickInspectorCycle()
        );

        if (frame != selectedFrame()) {
            syncingEditor = true;
            try {
                configManager.setConfiguration(
                        GROUP,
                        "trick5BoardFrameSlotV197",
                        SkateScapeConfig.BoardFrameSlot.fromSlot(frame).name()
                );
            } finally {
                syncingEditor = false;
            }
        }

        syncEditorFromFrame(frame);
        host.refreshOpenRuneLiteConfigPanel();
    }

    private void syncInspectorToSelectedFrame() {
        if (syncingInspector
                || frameCycles == null
                || frameCycles.length == 0) {
            return;
        }

        final int cycle = frameStartCycle(selectedFrame());

        syncingInspector = true;
        try {
            if (config.tuningSelectedTrick() == null
                    || config.tuningSelectedTrick().getSlot() != 5) {
                configManager.setConfiguration(
                        GROUP,
                        "inspectorSelectedTrickV132",
                        SkateScapeConfig.TrickSelection.TRICK_5.name()
                );
            }

            configManager.setConfiguration(
                    GROUP,
                    "trickInspectorCycleV132",
                    cycle
            );
        } finally {
            syncingInspector = false;
        }
    }

    private int frameForInspectorCycle(int inspectorCycle) {
        if (frameCycles == null
                || frameCycles.length == 0) {
            return selectedFrame();
        }

        int safeCycle = Math.max(0, inspectorCycle);
        if (totalCycles > 0) {
            safeCycle = Math.min(totalCycles, safeCycle);
        }

        final int mainElapsed = safeCycle - preTransitionCycles;

        if (mainElapsed <= 0) {
            return 1;
        }

        int cycle = 0;
        final int count = Math.min(FRAME_COUNT, frameCycles.length);

        for (int i = 0; i < count; i++) {
            cycle += Math.max(1, frameCycles[i]);

            if (mainElapsed < cycle) {
                return i + 1;
            }
        }

        return count > 0
                ? count
                : 1;
    }

    private int frameStartCycle(int frame) {
        int cycle = preTransitionCycles;

        if (frameCycles == null) {
            return Math.max(0, cycle);
        }

        final int stop = Math.min(
                Math.max(0, frame - 1),
                frameCycles.length
        );

        for (int i = 0; i < stop; i++) {
            cycle += Math.max(1, frameCycles[i]);
        }

        cycle = Math.max(0, cycle);
        if (totalCycles > 0) {
            cycle = Math.min(totalCycles, cycle);
        }

        return cycle;
    }

    private void saveEditorValue(
            int frame,
            String field,
            int value) {

        final int safeValue =
                field.equals("pitch")
                        || field.equals("yaw")
                        || field.equals("roll")
                        ? clamp(value, -180, 180)
                        : clamp(value, -256, 256);

        setStored(frame, field, safeValue);
        setCached(frame, field, safeValue);
    }

    private void syncEditorFromSelection() {
        syncEditorFromFrame(selectedFrame());
    }

    private void syncEditorFromFrame(int frame) {
        final int safeFrame = clamp(frame, 1, FRAME_COUNT);

        syncingEditor = true;
        try {
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameXV197",
                    getStored(safeFrame, "x")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameYV197",
                    getStored(safeFrame, "y")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameZV197",
                    getStored(safeFrame, "z")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFramePitchV197",
                    getStored(safeFrame, "pitch")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameYawV197",
                    getStored(safeFrame, "yaw")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameRollV197",
                    getStored(safeFrame, "roll")
            );
            configManager.setConfiguration(
                    GROUP,
                    "trick5BoardFrameLockV198",
                    isFrameLocked(safeFrame)
            );
        } finally {
            syncingEditor = false;
        }
    }

    private void ensureAllStoredValues() {
        for (int frame = 1; frame <= FRAME_COUNT; frame++) {
            for (String field : new String[]{"x", "y", "z", "pitch", "yaw", "roll"}) {
                final String key = storageKey(frame, field);
                final String existing = configManager.getConfiguration(GROUP, key);

                if (existing == null
                        || existing.trim().isEmpty()) {
                    setStored(
                            frame,
                            field,
                            defaultValue(frame, field)
                    );
                }
            }
        }
    }

    private void loadCache() {
        for (int frame = 1; frame <= FRAME_COUNT; frame++) {
            x[frame - 1] = getStored(frame, "x");
            y[frame - 1] = getStored(frame, "y");
            z[frame - 1] = getStored(frame, "z");
            pitch[frame - 1] = getStored(frame, "pitch");
            yaw[frame - 1] = getStored(frame, "yaw");
            roll[frame - 1] = getStored(frame, "roll");
            locked[frame - 1] = isFrameLocked(frame);
        }
    }

    private int getStored(
            int frame,
            String field) {

        final String raw =
                configManager.getConfiguration(
                        GROUP,
                        storageKey(frame, field)
                );

        return parseOrDefault(
                raw,
                defaultValue(frame, field)
        );
    }

    private int parseOrDefault(
            String raw,
            int fallback) {

        if (raw == null
                || raw.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void setStored(
            int frame,
            String field,
            int value) {

        configManager.setConfiguration(
                GROUP,
                storageKey(frame, field),
                value
        );
    }

    private void setCached(
            int frame,
            String field,
            int value) {

        final int index = clamp(frame, 1, FRAME_COUNT) - 1;

        switch (field) {
            case "x":
                x[index] = value;
                break;
            case "y":
                y[index] = value;
                break;
            case "z":
                z[index] = value;
                break;
            case "pitch":
                pitch[index] = value;
                break;
            case "yaw":
                yaw[index] = value;
                break;
            case "roll":
                roll[index] = value;
                break;
            default:
                break;
        }
    }

    private int selectedFrame() {
        final SkateScapeConfig.BoardFrameSlot selected =
                config.trick5BoardFrameSlot();

        return selected == null
                ? 5
                : clamp(selected.getSlot(), 1, FRAME_COUNT);
    }

    private int defaultValue(
            int frame,
            String field) {

        if (frame == 4 || frame == 6) {
            switch (field) {
                case "x":
                    return 50;
                case "y":
                    return 100;
                case "z":
                    return 10;
                case "pitch":
                    return 30;
                case "roll":
                    return 145;
                default:
                    return 0;
            }
        }

        if (frame == 5) {
            switch (field) {
                case "x":
                    return 88;
                case "y":
                    return 220;
                case "z":
                    return 17;
                case "pitch":
                    return 60;
                case "roll":
                    return 170;
                default:
                    return 0;
            }
        }

        return 0;
    }

    private boolean defaultLocked(int frame) {
        return clamp(frame, 1, FRAME_COUNT) == 5;
    }

    private boolean isFrameLocked(int frame) {
        final String raw =
                configManager.getConfiguration(
                        GROUP,
                        frameLockStorageKey(frame)
                );

        if (raw == null || raw.trim().isEmpty()) {
            return defaultLocked(frame);
        }

        return Boolean.parseBoolean(raw);
    }

    private void setFrameLocked(
            int frame,
            boolean locked) {

        final int safeFrame = clamp(frame, 1, FRAME_COUNT);

        configManager.setConfiguration(
                GROUP,
                frameLockStorageKey(safeFrame),
                locked
        );

        this.locked[safeFrame - 1] = locked;
    }

    private String frameLockStorageKey(int frame) {
        return "v199_t5_board_frame_"
                + clamp(frame, 1, FRAME_COUNT)
                + "_locked";
    }

    private String storageKey(
            int frame,
            String field) {

        return "v197_t5_board_frame_"
                + clamp(frame, 1, FRAME_COUNT)
                + "_"
                + field;
    }

    private String fieldForEditorKey(String key) {
        switch (key) {
            case "trick5BoardFrameXV197":
                return "x";
            case "trick5BoardFrameYV197":
                return "y";
            case "trick5BoardFrameZV197":
                return "z";
            case "trick5BoardFramePitchV197":
                return "pitch";
            case "trick5BoardFrameYawV197":
                return "yaw";
            case "trick5BoardFrameRollV197":
                return "roll";
            default:
                return "x";
        }
    }

    private boolean isEditorValueKey(String key) {
        return "trick5BoardFrameXV197".equals(key)
                || "trick5BoardFrameYV197".equals(key)
                || "trick5BoardFrameZV197".equals(key)
                || "trick5BoardFramePitchV197".equals(key)
                || "trick5BoardFrameYawV197".equals(key)
                || "trick5BoardFrameRollV197".equals(key);
    }

    private static int clamp(
            int value,
            int min,
            int max) {

        return Math.max(min, Math.min(max, value));
    }
}
