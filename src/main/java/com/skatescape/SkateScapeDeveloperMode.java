package com.skatescape;

/**
 * Local source-build switch for SkateScape's authoring and inspection tools.
 *
 * <p>Leave this false for the normal public build. Set it to true and rebuild
 * to restore the full developer settings UI and runtime tools.</p>
 */
final class SkateScapeDeveloperMode {
    static final boolean ENABLED = false;

    private SkateScapeDeveloperMode() {
    }
}
