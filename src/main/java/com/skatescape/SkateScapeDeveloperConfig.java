package com.skatescape;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("skatescape")
public interface SkateScapeDeveloperConfig extends SkateScapeConfig {
    @ConfigSection(
            name = "Trick Controls",
            description = "Hotkeys used to perform SkateScape tricks",
            position = 0,
            closedByDefault = false
    )
    String trickControlsSection = "trickControls";

    @ConfigSection(
            name = "Trick Tuning",
            description = "Choose the trick used by all SkateScape trick-authoring tools; tune its shared MAIN timeline, board motion and rotation settings",
            position = 1,
            closedByDefault = true
    )
    String trickTuningSection = "trickTuning";

    @ConfigSection(
            name = "Trick Inspector",
            description = "Inspect the trick selected in Trick Tuning; freeze the complete timeline one client cycle at a time",
            position = 2,
            closedByDefault = true
    )
    String trickInspectorSection = "trickInspector";

    @ConfigSection(
            name = "Pose Tuning",
            description = "Edit the saved player pose sequence for the trick selected in Trick Tuning",
            position = 3,
            closedByDefault = true
    )
    String poseTuningSection = "poseTuning";

    @ConfigSection(
            name = "Advanced Frame Timing",
            description = "Set literal per-frame millisecond timings for the trick selected in Trick Tuning",
            position = 4,
            closedByDefault = true
    )
    String poseTimingSection = "poseTiming";

    @ConfigSection(
            name = "Animation Inspector",
            description = "Developer inspector for browsing player animations and frames",
            position = 5,
            closedByDefault = true
    )
    String animationTestingSection = "animationTesting";
}
