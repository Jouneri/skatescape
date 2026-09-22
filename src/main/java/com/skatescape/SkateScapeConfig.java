package com.skatescape;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("skatescape")
public interface SkateScapeConfig extends Config {
    enum TrickSelection {
        KICKFLIP(1, "Kickflip"),
        SHOVE_IT_360(2, "360 Shove-it"),
        VARIAL_KICKFLIP(3, "Varial Kickflip"),
        KICKFLIP_IMPOSSIBLE_360(4, "Kickflip 360 Shove-it Body Varial"),
        TRICK_5(5, "Christ Air");

        private final int slot;
        private final String label;

        TrickSelection(int slot, String label) {
            this.slot = slot;
            this.label = label;
        }

        int getSlot() {
            return slot;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum BoardFrameSlot {
        FRAME_1(1, "Frame 1 - " + AnimationID.HUMAN_APPLECRUSH + "/0"),
        FRAME_2(2, "Frame 2 - " + AnimationID.HUMAN_APPLECRUSH + "/1"),
        FRAME_3(3, "Frame 3 - " + AnimationID.HUMAN_APPLECRUSH + "/2"),
        FRAME_4(4, "Frame 4 - " + AnimationID.HUMAN_APPLECRUSH + "/3 (up)"),
        FRAME_5(5, "Frame 5 - " + AnimationID.VAMP_LAND + "/8 Christ Air"),
        FRAME_6(6, "Frame 6 - " + AnimationID.HUMAN_APPLECRUSH + "/3 (down)"),
        FRAME_7(7, "Frame 7 - " + AnimationID.HUMAN_APPLECRUSH + "/2"),
        FRAME_8(8, "Frame 8 - " + AnimationID.HUMAN_APPLECRUSH + "/1"),
        FRAME_9(9, "Frame 9 - " + AnimationID.HUMAN_APPLECRUSH + "/0");

        private final int slot;
        private final String label;

        BoardFrameSlot(int slot, String label) {
            this.slot = slot;
            this.label = label;
        }

        int getSlot() {
            return slot;
        }

        static BoardFrameSlot fromSlot(int slot) {
            final int safeSlot = Math.max(1, Math.min(9, slot));
            return values()[safeSlot - 1];
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigSection(
            name = "Trick Controls",
            description = "Hotkeys used to perform SkateScape tricks",
            position = 0,
            closedByDefault = false
    )
    String trickControlsSection = "trickControls";

    /*
     * Developer-only editor section keys. The public release does not register
     * these as ConfigSections, and their ConfigItems are hidden below. A local
     * development build can re-register/unhide them when new tricks need tuning.
     */
    String trickTuningSection = "trickTuning";
    String trickInspectorSection = "trickInspector";
    String poseTuningSection = "poseTuning";
    String poseTimingSection = "poseTiming";
    String animationTestingSection = "animationTesting";

    @ConfigItem(
            keyName = "trick1Hotkey",
            name = "Kickflip",
            description = "Hotkey for the Kickflip",
            position = 0,
            section = trickControlsSection
    )
    default Keybind trick1Hotkey() {
        return new Keybind(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK);
    }

    @ConfigItem(
            keyName = "trick2Hotkey",
            name = "360 Shove-it",
            description = "Hotkey for the 360 Shove-it",
            position = 1,
            section = trickControlsSection
    )
    default Keybind trick2Hotkey() {
        return new Keybind(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK);
    }

    @ConfigItem(
            keyName = "trick3Hotkey",
            name = "Varial Kickflip",
            description = "Hotkey for the Varial Kickflip",
            position = 2,
            section = trickControlsSection
    )
    default Keybind trick3Hotkey() {
        return new Keybind(KeyEvent.VK_3, InputEvent.ALT_DOWN_MASK);
    }

    @ConfigItem(
            keyName = "trick4Hotkey",
            name = "Kickflip 360 Shove-it Body Varial",
            description = "Hotkey for the Kickflip 360 Shove-it Body Varial",
            position = 3,
            section = trickControlsSection
    )
    default Keybind trick4Hotkey() {
        return new Keybind(KeyEvent.VK_4, InputEvent.ALT_DOWN_MASK);
    }

    @ConfigItem(
            keyName = "trick5Hotkey",
            name = "Christ Air",
            description = "Hotkey for Christ Air",
            position = 4,
            section = trickControlsSection
    )
    default Keybind trick5Hotkey() {
        return new Keybind(KeyEvent.VK_5, InputEvent.ALT_DOWN_MASK);
    }

    // ---------------------------------------------------------------------
    // Developer-only Trick Tuning editor. Per-trick values remain available
    // in source/config storage, but the public settings panel hides this UI.
    // Some persisted key names retain their original suffixes so existing
    // RuneLite config remains compatible; changing them requires migration.
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "tuningSelectedTrickV132",
            name = "Trick",
            description = "Choose which trick these tuning controls edit",
            hidden = true,
            position = 0,
            section = trickTuningSection
    )
    default TrickSelection tuningSelectedTrick() {
        return TrickSelection.KICKFLIP;
    }

    @Range(min = 400, max = 20000)
    @ConfigItem(
            keyName = "tuningDurationMsV132",
            name = "Trick duration (ms)",
            description = "Duration of the selected trick's main action. Player pose timing and all board timing percentages follow this duration",
            hidden = true,
            position = 1,
            section = trickTuningSection
    )
    default int tuningDurationMs() {
        return 900;
    }

    @Range(min = 0, max = 128)
    @ConfigItem(
            keyName = "tuningPopHeightV132",
            name = "Board pop height",
            description = "How high the skateboard rises during the selected trick",
            hidden = true,
            position = 2,
            section = trickTuningSection
    )
    default int tuningPopHeight() {
        return 60;
    }

    @ConfigItem(
            keyName = "tuningPopStartCycleV132",
            name = "Pop starts (%)",
            description = "Point in the MAIN timeline where the board begins rising",
            hidden = true,
            position = 3,
            section = trickTuningSection
    )
    default double tuningPopStartPercent() {
        return 33.3;
    }

    @ConfigItem(
            keyName = "tuningKickflipStartCycleV132",
            name = "Kickflip starts (%)",
            description = "Point in the MAIN timeline where the selected trick's primary board rotation starts",
            hidden = true,
            position = 4,
            section = trickTuningSection
    )
    default double tuningKickflipStartPercent() {
        return 33.3;
    }

    @ConfigItem(
            keyName = "tuningKickflipEndCycleV132",
            name = "Kickflip ends (%)",
            description = "Point in the MAIN timeline where the primary board rotation ends",
            hidden = true,
            position = 5,
            section = trickTuningSection
    )
    default double tuningKickflipEndPercent() {
        return 80.0;
    }

    @Range(min = 180, max = 720)
    @ConfigItem(
            keyName = "tuningTrick5KickflipDegreesV146",
            name = "Kickflip rotation (degrees)",
            description = "Kickflip rotation amount. Christ Air additionally supports 540 and 720 degrees.",
            hidden = true,
            position = 6,
            section = trickTuningSection
    )
    default int tuningTrick5KickflipDegrees() {
        return 180;
    }

    @Range(min = 180, max = 360)
    @ConfigItem(
            keyName = "tuningTrick4ImpossibleDegreesV147",
            name = "360 Shove-it rotation (degrees)",
            description = "Kickflip 360 Shove-it Body Varial: choose 180 or 360 degrees of Shove-it rotation",
            hidden = true,
            position = 12,
            section = trickTuningSection
    )
    default int tuningTrick4ImpossibleDegrees() {
        return 360;
    }

    @ConfigItem(
            keyName = "tuningImpossibleStartCycleV132",
            name = "360 Shove-it starts (%)",
            description = "Shared secondary control: Shove-it timing for Kickflip 360 Shove-it Body Varial; Shove-it rotation amount for 360 Shove-it, Varial Kickflip and Christ Air",
            hidden = true,
            position = 9,
            section = trickTuningSection
    )
    default double tuningSecondaryValue() {
        return 80.0;
    }

    @ConfigItem(
            keyName = "tuningVarialShoveStartCycleV142",
            name = "Varial Shove-it starts (%)",
            description = "Varial Kickflip: point in the MAIN timeline where the Shove-it rotation starts",
            hidden = true,
            position = 7,
            section = trickTuningSection
    )
    default double tuningVarialShoveStartPercent() {
        return 33.3;
    }

    @ConfigItem(
            keyName = "tuningVarialShoveEndCycleV142",
            name = "Varial Shove-it ends (%)",
            description = "Varial Kickflip: point in the MAIN timeline where the Shove-it rotation ends",
            hidden = true,
            position = 8,
            section = trickTuningSection
    )
    default double tuningVarialShoveEndPercent() {
        return 80.0;
    }

    @ConfigItem(
            keyName = "tuningCatchCycleV132",
            name = "Board catch (%)",
            description = "Point in the MAIN timeline where the board is caught. On Kickflip 360 Shove-it Body Varial, this also ends the Shove-it rotation.",
            hidden = true,
            position = 10,
            section = trickTuningSection
    )
    default double tuningCatchPercent() {
        return 80.0;
    }

    @ConfigItem(
            keyName = "tuningTouchdownCycleV132",
            name = "Board touchdown (%)",
            description = "Point in the MAIN timeline where the board reaches the ground",
            hidden = true,
            position = 11,
            section = trickTuningSection
    )
    default double tuningTouchdownPercent() {
        return 93.3;
    }

    /*
     * QUEUED TRICK SPLICE TUNING.
     *
     * Both values use the same complete PRE -> MAIN -> seam -> RETURN client
     * cycle timeline shown by Trick Inspector. This lets a visually chosen
     * Inspector cycle be copied directly into the combo splice controls.
     */
    @Range(min = 0, max = 500)
    @ConfigItem(
            keyName = "tuningQueuedExitCycleV182",
            name = "Queued exit cycle",
            description = "If another trick is already queued, stop the selected current trick at this Trick Inspector cycle. The visual splice point stays aligned when Trick duration changes",
            hidden = true,
            position = 23,
            section = trickTuningSection
    )
    default int tuningQueuedExitCycle() {
        return 56;
    }

    @Range(min = 0, max = 500)
    @ConfigItem(
            keyName = "tuningQueuedStartCycleV182",
            name = "Queued start cycle",
            description = "When the selected trick starts from a queue, begin it at this Trick Inspector cycle. The visual splice point stays aligned when Trick duration changes",
            hidden = true,
            position = 24,
            section = trickTuningSection
    )
    default int tuningQueuedStartCycle() {
        return 23;
    }

    // ---------------------------------------------------------------------
    // Christ Air board frame editor. Each visible XYZ/rotation control edits one
    // actual player frame. The runtime interpolates the board smoothly between
    // adjacent frame targets using the same timing as the player poses.
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "trick5BoardFrameSlotV197",
            name = "Board frame",
            description = "Choose the exact Christ Air player frame to edit. Trick Inspector follows this frame, and Inspector browsing follows this selector.",
            hidden = true,
            position = 15,
            section = trickTuningSection
    )
    default BoardFrameSlot trick5BoardFrameSlot() {
        return BoardFrameSlot.FRAME_5;
    }

    @ConfigItem(
            keyName = "trick5BoardFrameLockV198",
            name = "Keyframe lock",
            description = "Lock this Christ Air board frame's XYZ and rotation values and hold that exact board transform for the entire matching player frame. Board frame still follows Trick Inspector.",
            hidden = true,
            position = 16,
            section = trickTuningSection
    )
    default boolean trick5BoardFrameLock() {
        return false;
    }

    @Range(min = -256, max = 256)
    @ConfigItem(
            keyName = "trick5BoardFrameXV197",
            name = "X - right / left",
            description = "Board target on the sideways axis for the selected frame. Positive moves right; negative moves left.",
            hidden = true,
            position = 17,
            section = trickTuningSection
    )
    default int trick5BoardFrameX() {
        return 71;
    }

    @Range(min = -256, max = 256)
    @ConfigItem(
            keyName = "trick5BoardFrameYV197",
            name = "Y - up / down",
            description = "Board height target for the selected frame. Zero is the normal under-feet height; positive moves up; negative moves down.",
            hidden = true,
            position = 18,
            section = trickTuningSection
    )
    default int trick5BoardFrameY() {
        return 227;
    }

    @Range(min = -256, max = 256)
    @ConfigItem(
            keyName = "trick5BoardFrameZV197",
            name = "Z - forward / back",
            description = "Board target on the forward axis for the selected frame. Positive moves forward; negative moves backward.",
            hidden = true,
            position = 19,
            section = trickTuningSection
    )
    default int trick5BoardFrameZ() {
        return 22;
    }

    @Range(min = -180, max = 180)
    @ConfigItem(
            keyName = "trick5BoardFramePitchV197",
            name = "Pitch",
            description = "Board pitch in degrees for the selected frame.",
            hidden = true,
            position = 20,
            section = trickTuningSection
    )
    default int trick5BoardFramePitch() {
        return 0;
    }

    @Range(min = -180, max = 180)
    @ConfigItem(
            keyName = "trick5BoardFrameYawV197",
            name = "Yaw",
            description = "Board yaw in degrees for the selected frame.",
            hidden = true,
            position = 21,
            section = trickTuningSection
    )
    default int trick5BoardFrameYaw() {
        return 0;
    }

    @Range(min = -180, max = 180)
    @ConfigItem(
            keyName = "trick5BoardFrameRollV197",
            name = "Roll",
            description = "Board roll in degrees for the selected frame.",
            hidden = true,
            position = 22,
            section = trickTuningSection
    )
    default int trick5BoardFrameRoll() {
        return 0;
    }

    // ---------------------------------------------------------------------
    // Trick Inspector editor.
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "inspectorSelectedTrickV132",
            name = "Trick",
            description = "Choose which trick the inspector freezes",
            hidden = true,
            position = 0,
            section = trickInspectorSection
    )
    default TrickSelection inspectorSelectedTrick() {
        return TrickSelection.KICKFLIP;
    }

    @ConfigItem(
            keyName = "trickInspectorEnabledV132",
            name = "Enable trick inspector",
            description = "Freeze the selected trick's real player + board timeline",
            hidden = true,
            position = 1,
            section = trickInspectorSection
    )
    default boolean trickInspectorEnabled() {
        return false;
    }

    @Range(min = 0, max = 500)
    @ConfigItem(
            keyName = "trickInspectorCycleV132",
            name = "Trick position (cycle)",
            description = "Complete selected-trick timeline position. Left = rewind, Right = forward. One cycle is about 20 ms",
            hidden = true,
            position = 2,
            section = trickInspectorSection
    )
    default int trickInspectorCycle() {
        return 0;
    }

    // ---------------------------------------------------------------------
    // Pose Tuning editor.
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "poseSelectedTrickV132",
            name = "Trick",
            description = "Choose which trick's player pose sequence to edit",
            hidden = true,
            position = 0,
            section = poseTuningSection
    )
    default TrickSelection poseSelectedTrick() {
        return TrickSelection.KICKFLIP;
    }

    @Range(min = 1, max = 19)
    @ConfigItem(
            keyName = "poseSequenceLengthV132",
            name = "Pose sequence length",
            description = "Number of active entries from the selected trick's animation/frame lists. Kickflip, 360 Shove-it and Varial Kickflip max at 12 poses; Kickflip 360 Shove-it Body Varial maxes at 19; Christ Air maxes at 9",
            hidden = true,
            position = 1,
            section = poseTuningSection
    )
    default int poseSequenceLength() {
        return 12;
    }

    @ConfigItem(
            keyName = "poseAnimationIdsV132",
            name = "Player animation IDs",
            description = "Comma-separated animation IDs for the selected trick. A single ID repeats for the whole pose sequence",
            hidden = true,
            position = 2,
            section = poseTuningSection
    )
    default String poseAnimationIds() {
        return Integer.toString(AnimationID.HUMAN_JUMP_HURDLE);
    }

    @ConfigItem(
            keyName = "poseFramesV132",
            name = "Player frames",
            description = "Comma-separated animation frames for the selected trick",
            hidden = true,
            position = 3,
            section = poseTuningSection
    )
    default String poseFrames() {
        return "0,1,2,3,4,5,6,7,8,9,10,12";
    }

    // ---------------------------------------------------------------------
    // Advanced Pose Timing editor.
    // ---------------------------------------------------------------------

    @ConfigItem(
            keyName = "poseTimingSelectedTrickV132",
            name = "Trick",
            description = "Choose which trick's pose timing to edit",
            hidden = true,
            position = 0,
            section = poseTimingSection
    )
    default TrickSelection poseTimingSelectedTrick() {
        return TrickSelection.KICKFLIP;
    }

    @ConfigItem(
            keyName = "advancedPoseTimingV132",
            name = "Advanced pose timing",
            description = "Off = automatically fit the player pose sequence to Trick duration. On = use the per-pose percentage distribution below",
            hidden = true,
            position = 1,
            section = poseTimingSection
    )
    default boolean advancedPoseTiming() {
        return false;
    }

    @ConfigItem(
            keyName = "poseDurationsMsV132",
            name = "Pose timing (%)",
            description = "Comma-separated percentage distribution across the active poses. Values are normalized to Trick duration; a single value repeats across the sequence",
            hidden = true,
            position = 2,
            section = poseTimingSection
    )
    default String poseTimingPercentages() {
        return "";
    }

    @ConfigItem(
            keyName = "animationTest",
            name = "Enable animation inspector",
            description = "Temporarily replace normal SkateScape animation handling with the animation inspector",
            hidden = true,
            position = 0,
            section = animationTestingSection
    )
    default boolean animationTest() {
        return false;
    }

    @ConfigItem(
            keyName = "freezeFrame",
            name = "Freeze frame",
            description = "When enabled, inspect a specific pose-frame. When disabled, play the selected animation normally as an action animation",
            hidden = true,
            position = 1,
            section = animationTestingSection
    )
    default boolean freezeFrame() {
        return false;
    }

    @Range(min = 0, max = 14520)
    @ConfigItem(
            keyName = "animationId",
            name = "Animation ID",
            description = "Animation to inspect. Up/Down arrows browse valid animation IDs while the game canvas has focus",
            hidden = true,
            position = 2,
            section = animationTestingSection
    )
    default int animationId() {
        return 0;
    }

    @Range(min = 0, max = 200)
    @ConfigItem(
            keyName = "animationFrame",
            name = "Animation frame",
            description = "Frame to inspect. Left/Right arrows browse frames while the game canvas has focus",
            hidden = true,
            position = 3,
            section = animationTestingSection
    )
    default int animationFrame() {
        return 0;
    }
}
