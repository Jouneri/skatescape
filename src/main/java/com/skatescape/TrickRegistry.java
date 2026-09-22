package com.skatescape;

/**
 * Central SkateScape trick list.
 *
 * Player choreography and timing are editable per trick. The five release
 * slots are Kickflip, 360 Shove-it, Varial Kickflip, Kickflip 360 Shove-it
 * Body Varial and Christ Air. Each {@link TrickDefinition} supplies the generic
 * board-motion profile used where a trick does not provide dedicated board
 * choreography.
 */
final class TrickRegistry {
    static final TrickDefinition KICKFLIP =
            new TrickDefinition(
                    1,
                    "Kickflip",
                    1.0,
                    0.0,
                    TrickDefinition.RotationProfile.CLASSIC_FLIP,
                    TrickDefinition.PitchProfile.CLASSIC_FLIP
            );

    static final TrickDefinition SHOVE_IT_360 =
            new TrickDefinition(
                    2,
                    "360 Shove-it",
                    0.0,
                    1.0,
                    TrickDefinition.RotationProfile.SCOOPED_SHOVE,
                    TrickDefinition.PitchProfile.FLAT_SHOVE
            );

    static final TrickDefinition VARIAL_KICKFLIP =
            new TrickDefinition(
                    3,
                    "Varial Kickflip",
                    1.0,
                    0.5,
                    TrickDefinition.RotationProfile.CLASSIC_FLIP,
                    TrickDefinition.PitchProfile.CLASSIC_FLIP
            );

    static final TrickDefinition TRICK_4 =
            new TrickDefinition(
                    4,
                    "Kickflip 360 Shove-it Body Varial",
                    1.0,
                    0.0,
                    TrickDefinition.RotationProfile.CLASSIC_FLIP,
                    TrickDefinition.PitchProfile.CLASSIC_FLIP
            );

    /*
     * Christ Air uses its dedicated frame-by-frame board choreography, so the
     * registry's generic Kickflip/Shove-it turn counts stay at zero.
     */
    static final TrickDefinition TRICK_5 =
            new TrickDefinition(
                    5,
                    "Christ Air",
                    0.0,
                    0.0,
                    TrickDefinition.RotationProfile.CLASSIC_FLIP,
                    TrickDefinition.PitchProfile.CLASSIC_FLIP
            );

    private TrickRegistry() {
    }

    static TrickDefinition forSlot(int slot) {
        switch (slot) {
            case 1:
                return KICKFLIP;

            case 2:
                return SHOVE_IT_360;

            case 3:
                return VARIAL_KICKFLIP;

            case 4:
                return TRICK_4;

            case 5:
                return TRICK_5;

            default:
                return null;
        }
    }

    static TrickDefinition forEditorSlot(int slot) {
        return forSlot(slot);
    }
}
