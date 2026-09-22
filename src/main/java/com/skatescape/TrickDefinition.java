package com.skatescape;

/**
 * Immutable description of the generic board-motion part of a SkateScape
 * trick. Player choreography is prepared separately and may differ per trick.
 *
 * This class supplies the board rotation/pitch profile used by the shared
 * transform engine.
 */
final class TrickDefinition {
    enum RotationProfile {
        /* Kickflip rotation curve used by Trick 1. */
        CLASSIC_FLIP,

        /*
         * Scoop -> rapid rotation -> controlled catch.
         * Intended for shove-it-family tricks.
         */
        SCOOPED_SHOVE
    }

    enum PitchProfile {
        /* Kickflip pitch: nose-up snap -> level apex -> nose-down catch. */
        CLASSIC_FLIP,

        /*
         * Much flatter deck behavior for shove-it-family tricks:
         * small scoop -> nearly flat spin -> tiny catch -> level.
         */
        FLAT_SHOVE,

        /*
         * No secondary deck pitch.
         */
        NONE
    }

    private final int slot;
    private final String name;

    /*
     * Full turns around model Z.
     *
     * +1.0 = Kickflip direction
     * -1.0 = opposite direction (useful for a future Heelflip)
     */
    private final double flipTurns;

    /*
     * Full turns around model Y.
     *
     * 0.5 = 180-degree shove-it
     * 1.0 = 360-degree shove-it
     * -1.0 = 360-degree shove-it in the opposite direction
     */
    private final double shoveTurns;

    private final RotationProfile rotationProfile;
    private final PitchProfile pitchProfile;

    TrickDefinition(
            int slot,
            String name,
            double flipTurns,
            double shoveTurns,
            RotationProfile rotationProfile,
            PitchProfile pitchProfile) {

        this.slot = slot;
        this.name = name;
        this.flipTurns = flipTurns;
        this.shoveTurns = shoveTurns;
        this.rotationProfile = rotationProfile;
        this.pitchProfile = pitchProfile;
    }

    int getSlot() {
        return slot;
    }

    String getName() {
        return name;
    }

    double getFlipTurns() {
        return flipTurns;
    }

    double getShoveTurns() {
        return shoveTurns;
    }

    RotationProfile getRotationProfile() {
        return rotationProfile;
    }

    PitchProfile getPitchProfile() {
        return pitchProfile;
    }
}
