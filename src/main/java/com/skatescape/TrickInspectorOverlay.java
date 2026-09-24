package com.skatescape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TrickInspectorOverlay extends Overlay {
    private static final Color BACKGROUND = new Color(0, 0, 0, 170);

    private final SkateScapePlugin plugin;

    TrickInspectorOverlay(SkateScapePlugin plugin) {
        super(plugin);
        this.plugin = plugin;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        final SkateScapeConfig config =
                plugin.getConfigForTrickInspectorOverlay();

        final Client client =
                plugin.getClientForTrickInspectorOverlay();

        /*
         * Animation Inspector uses the RuneLite config UI for its live
         * animation ID/frame. This overlay is only for the useful Trick
         * Inspector player/trick readout; cycle position stays in the UI.
         */
        if (config == null
                || client == null
                || !config.trickInspectorEnabled()
                || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }

        final TrickDefinition activeTrick =
                plugin.getActiveTrickForTrickInspectorOverlay();

        final String firstLine =
                activeTrick == null
                        ? "Trick Inspector"
                        : activeTrick.getName();

        final int maxCycle =
                Math.max(
                        0,
                        plugin.getTrickTotalCyclesForTrickInspectorOverlay()
                                - 1
                );

        final double currentSeconds =
                plugin.getTrickElapsedCyclesForTrickInspectorOverlay()
                        * 0.020;

        final double totalSeconds =
                maxCycle * 0.020;

        final String secondLine =
                String.format(
                        "Time: %.2f s / %.2f s",
                        currentSeconds,
                        totalSeconds
                );

        final Player inspectorPlayer =
                client.getLocalPlayer();

        final String phase =
                plugin.getTrickTracePhaseForTrickInspectorOverlay();

        final String thirdLine;
        if (inspectorPlayer == null) {
            thirdLine = phase;
        } else {
            /*
             * MAIN may be carried by either animation layer. Tricks 1-3 and
             * Trick 5 use the POSE layer by default, while Trick 4 normally
             * uses ACTION-layer 2890. Report whichever layer is actually
             * carrying the visible trick animation.
             */
            final boolean actionLayerActive =
                    inspectorPlayer.getAnimation() >= 0;

            final int displayedAnimation =
                    actionLayerActive
                            ? inspectorPlayer.getAnimation()
                            : inspectorPlayer.getPoseAnimation();

            final int displayedFrame =
                    actionLayerActive
                            ? inspectorPlayer.getAnimationFrame()
                            : inspectorPlayer.getPoseAnimationFrame();

            thirdLine =
                    phase
                            + " | Player "
                            + displayedAnimation
                            + " / frame "
                            + displayedFrame;
        }

        final FontMetrics metrics =
                graphics.getFontMetrics();

        final int padding = 6;
        final int lineHeight =
                metrics.getHeight();

        final int textWidth =
                Math.max(
                        metrics.stringWidth(firstLine),
                        Math.max(
                                metrics.stringWidth(secondLine),
                                metrics.stringWidth(thirdLine)
                        )
                );

        final int width =
                textWidth
                        + padding * 2;

        final int height =
                lineHeight * 3
                        + padding * 2;

        graphics.setColor(BACKGROUND);

        graphics.fillRoundRect(
                0,
                0,
                width,
                height,
                8,
                8
        );

        graphics.setColor(Color.WHITE);

        final int firstBaseline =
                padding
                        + metrics.getAscent();

        graphics.drawString(
                firstLine,
                padding,
                firstBaseline
        );

        graphics.drawString(
                secondLine,
                padding,
                firstBaseline
                        + lineHeight
        );

        graphics.drawString(
                thirdLine,
                padding,
                firstBaseline
                        + lineHeight * 2
        );

        return new Dimension(
                width,
                height
        );
    }
}
