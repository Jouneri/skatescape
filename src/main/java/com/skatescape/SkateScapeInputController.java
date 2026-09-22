package com.skatescape;

import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

/**
 * Handles hotkey registration and keyboard routing. TrickRuntimeController
 * owns trick buffering and decides when pending input is consumed.
 *
 * <p>Developer-tool arrow-key priority:</p>
 * <ul>
 *     <li>Trick Inspector while enabled.</li>
 *     <li>Animation Inspector otherwise.</li>
 *     <li>Modified keys and non-game-canvas input are ignored.</li>
 * </ul>
 */
final class SkateScapeInputController {
    private final Client client;
    private final SkateScapeConfig config;
    private final KeyManager keyManager;
    private final TrickInspector trickInspector;
    private final AnimationInspector animationInspector;
    private final IntConsumer pendingTrickSlotConsumer;
    private final boolean developerToolsEnabled;

    private boolean listenersInstalled;

    private final KeyListener animationBrowseKeyListener =
            new KeyListener() {
                @Override
                public void keyTyped(KeyEvent event) {
                }

                @Override
                public void keyPressed(KeyEvent event) {
                    if (client.getGameState() != GameState.LOGGED_IN
                            || event.getComponent() != client.getCanvas()
                            || event.isControlDown()
                            || event.isAltDown()
                            || event.isShiftDown()
                            || event.isMetaDown()) {
                        return;
                    }

                    if (config.trickInspectorEnabled()) {
                        if (trickInspector != null) {
                            trickInspector.handleTrickBrowseKeyPressed(event);
                        }
                        return;
                    }

                    if (animationInspector != null) {
                        animationInspector.handleAnimationBrowseKeyPressed(event);
                    }
                }

                @Override
                public void keyReleased(KeyEvent event) {
                }
            };

    private final HotkeyListener trick1HotkeyListener;
    private final HotkeyListener trick2HotkeyListener;
    private final HotkeyListener trick3HotkeyListener;
    private final HotkeyListener trick4HotkeyListener;
    private final HotkeyListener trick5HotkeyListener;

    SkateScapeInputController(
            Client client,
            SkateScapeConfig config,
            KeyManager keyManager,
            TrickInspector trickInspector,
            AnimationInspector animationInspector,
            IntConsumer pendingTrickSlotConsumer,
            boolean developerToolsEnabled) {

        this.client = client;
        this.config = config;
        this.keyManager = keyManager;
        this.trickInspector = trickInspector;
        this.animationInspector = animationInspector;
        this.pendingTrickSlotConsumer = pendingTrickSlotConsumer;
        this.developerToolsEnabled = developerToolsEnabled;

        trick1HotkeyListener =
                createTrickHotkeyListener(
                        1,
                        () -> config.trick1Hotkey()
                );

        trick2HotkeyListener =
                createTrickHotkeyListener(
                        2,
                        () -> config.trick2Hotkey()
                );

        trick3HotkeyListener =
                createTrickHotkeyListener(
                        3,
                        () -> config.trick3Hotkey()
                );

        trick4HotkeyListener =
                createTrickHotkeyListener(
                        4,
                        () -> config.trick4Hotkey()
                );

        trick5HotkeyListener =
                createTrickHotkeyListener(
                        5,
                        () -> config.trick5Hotkey()
                );
    }

    void installListeners() {
        if (listenersInstalled) {
            return;
        }

        if (developerToolsEnabled) {
            keyManager.registerKeyListener(animationBrowseKeyListener);
        }
        keyManager.registerKeyListener(trick1HotkeyListener);
        keyManager.registerKeyListener(trick2HotkeyListener);
        keyManager.registerKeyListener(trick3HotkeyListener);
        keyManager.registerKeyListener(trick4HotkeyListener);
        keyManager.registerKeyListener(trick5HotkeyListener);

        listenersInstalled = true;
    }

    void uninstallListeners() {
        if (!listenersInstalled) {
            return;
        }

        if (developerToolsEnabled) {
            keyManager.unregisterKeyListener(animationBrowseKeyListener);
        }
        keyManager.unregisterKeyListener(trick1HotkeyListener);
        keyManager.unregisterKeyListener(trick2HotkeyListener);
        keyManager.unregisterKeyListener(trick3HotkeyListener);
        keyManager.unregisterKeyListener(trick4HotkeyListener);
        keyManager.unregisterKeyListener(trick5HotkeyListener);

        listenersInstalled = false;
    }

    private HotkeyListener createTrickHotkeyListener(
            int trickSlot,
            java.util.function.Supplier<net.runelite.client.config.Keybind>
                    keybindSupplier) {

        return new HotkeyListener(keybindSupplier) {
            @Override
            public void hotkeyPressed() {
                pendingTrickSlotConsumer.accept(trickSlot);
            }
        };
    }
}
