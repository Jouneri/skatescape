package com.skatescape;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Owns the live skateboard scene object, base model, placement, orientation,
 * and terrain sampling. Trick choreography and model transforms are handled by
 * {@link SkateboardTrickTransformController}.
 *
 * The weird skateboard tricks remain Mike Hawk's problem. :D
 */
final class SkateboardSceneController {
    @FunctionalInterface
    interface TransformApplier {
        void apply(double terrainPitch, double terrainRoll);
    }

    private static final double ORIENTATION_TO_RADIANS =
            (Math.PI * 2.0) / 2048.0;

    private final Client client;

    private final int plankItemId;
    private final boolean showBoard;
    private final boolean showWheels;
    private final int wheelItemId;
    private final int wheelSizePercent;
    private final int wheelFrontBackOffset;
    private final int wheelLeftRightOffset;
    private final int wheelHeight;

    private final int terrainSampleForward;
    private final int terrainSampleSideways;
    private final double maxTerrainTiltRadians;
    private final double terrainAngleQuantization;

    private RuneLiteObject board;
    private ModelData baseSkateboardData;

    private int appliedTerrainPitchKey = Integer.MIN_VALUE;
    private int appliedTerrainRollKey = Integer.MIN_VALUE;
    private boolean terrainTiltApplied;
    private boolean trickTransformApplied;

    SkateboardSceneController(
            Client client,
            int plankItemId,
            boolean showBoard,
            boolean showWheels,
            int wheelItemId,
            int wheelSizePercent,
            int wheelFrontBackOffset,
            int wheelLeftRightOffset,
            int wheelHeight,
            int terrainSampleForward,
            int terrainSampleSideways,
            double maxTerrainTiltRadians,
            double terrainAngleQuantization) {

        this.client = client;
        this.plankItemId = plankItemId;
        this.showBoard = showBoard;
        this.showWheels = showWheels;
        this.wheelItemId = wheelItemId;
        this.wheelSizePercent = wheelSizePercent;
        this.wheelFrontBackOffset = wheelFrontBackOffset;
        this.wheelLeftRightOffset = wheelLeftRightOffset;
        this.wheelHeight = wheelHeight;
        this.terrainSampleForward = terrainSampleForward;
        this.terrainSampleSideways = terrainSampleSideways;
        this.maxTerrainTiltRadians = maxTerrainTiltRadians;
        this.terrainAngleQuantization = terrainAngleQuantization;
    }

    void update(
            Player player,
            LocalPoint boardLocation,
            boolean trickActive,
            TransformApplier transformApplier) {

        if (player == null || boardLocation == null) {
            return;
        }

        if (board == null) {
            createSkateboard(player, boardLocation);
        }

        if (board == null) {
            return;
        }

        final WorldView worldView = player.getWorldView();

        board.setWorldView(worldView.getId());
        board.setLocation(
                boardLocation,
                worldView.getPlane()
        );
        board.setOrientation(player.getCurrentOrientation());

        updateTerrainTilt(
                player,
                boardLocation,
                worldView.getPlane(),
                trickActive,
                transformApplier
        );
    }

    ModelData createWorkingModelData() {
        if (baseSkateboardData == null) {
            return null;
        }

        return baseSkateboardData
                .shallowCopy()
                .cloneVertices();
    }

    void setModel(Model model) {
        if (board != null && model != null) {
            board.setModel(model);
        }
    }

    void markTransformApplied(boolean trickActive) {
        trickTransformApplied = trickActive;
    }

    void clearTrickTransformApplied() {
        trickTransformApplied = false;
    }

    void removeBoard() {
        if (board != null) {
            board.setActive(false);
            board = null;
        }

        baseSkateboardData = null;
        appliedTerrainPitchKey = Integer.MIN_VALUE;
        appliedTerrainRollKey = Integer.MIN_VALUE;
        terrainTiltApplied = false;
        trickTransformApplied = false;
    }

    private void createSkateboard(
            Player player,
            LocalPoint boardLocation) {

        removeBoard();

        /*
         * Model construction stays in the dedicated factory. This controller merely
         * becomes the owner of the resulting live scene object.
         */
        final ModelData combinedData =
                SkateboardModelFactory.createCombinedSkateboardData(
                        client,
                        plankItemId,
                        showBoard,
                        showWheels,
                        wheelItemId,
                        wheelSizePercent,
                        wheelFrontBackOffset,
                        wheelLeftRightOffset,
                        wheelHeight
                );

        if (combinedData == null) {
            return;
        }

        baseSkateboardData = combinedData;

        appliedTerrainPitchKey = Integer.MIN_VALUE;
        appliedTerrainRollKey = Integer.MIN_VALUE;
        terrainTiltApplied = false;
        trickTransformApplied = false;

        final WorldView worldView = player.getWorldView();

        board = client.createRuneLiteObject();
        board.setModel(combinedData.light());
        board.setWorldView(worldView.getId());
        board.setLocation(
                boardLocation,
                worldView.getPlane()
        );
        board.setOrientation(player.getCurrentOrientation());
        board.setActive(true);
    }

    /*
     * TERRAIN FOLLOWING
     *
     * The skateboard remains one rigid model. Sample terrain in the board's
     * local front/rear/left/right directions, calculate pitch and roll, then
     * hand those exact angles to SkateboardTrickTransformController's
     * transform pipeline.
     */
    private void updateTerrainTilt(
            Player player,
            LocalPoint boardLocation,
            int plane,
            boolean trickActive,
            TransformApplier transformApplier) {

        if (board == null
                || baseSkateboardData == null
                || transformApplier == null) {
            return;
        }

        /*
         * All four samples share the same player orientation. Calculate the
         * trigonometry once instead of repeating sin/cos for every point.
         */
        final double angle =
                player.getCurrentOrientation()
                        * ORIENTATION_TO_RADIANS;
        final double sin = Math.sin(angle);
        final double cos = Math.cos(angle);

        final LocalPoint frontPoint = offsetRelativeToOrientation(
                boardLocation,
                terrainSampleForward,
                0,
                sin,
                cos
        );

        final LocalPoint rearPoint = offsetRelativeToOrientation(
                boardLocation,
                -terrainSampleForward,
                0,
                sin,
                cos
        );

        final LocalPoint leftPoint = offsetRelativeToOrientation(
                boardLocation,
                0,
                -terrainSampleSideways,
                sin,
                cos
        );

        final LocalPoint rightPoint = offsetRelativeToOrientation(
                boardLocation,
                0,
                terrainSampleSideways,
                sin,
                cos
        );

        final int frontHeight =
                Perspective.getTileHeight(client, frontPoint, plane);
        final int rearHeight =
                Perspective.getTileHeight(client, rearPoint, plane);
        final int leftHeight =
                Perspective.getTileHeight(client, leftPoint, plane);
        final int rightHeight =
                Perspective.getTileHeight(client, rightPoint, plane);

        /*
         * Model-space mapping:
         *
         * front  = negative Z
         * rear   = positive Z
         * left   = positive X
         * right  = negative X
         */
        double pitch = Math.atan2(
                frontHeight - rearHeight,
                terrainSampleForward * 2.0
        );

        double roll = Math.atan2(
                leftHeight - rightHeight,
                terrainSampleSideways * 2.0
        );

        pitch = clamp(
                pitch,
                -maxTerrainTiltRadians,
                maxTerrainTiltRadians
        );

        roll = clamp(
                roll,
                -maxTerrainTiltRadians,
                maxTerrainTiltRadians
        );

        /*
         * Quantize very slightly so microscopic floating-point noise does not
         * rebuild the board model every client tick.
         */
        final int pitchKey = (int) Math.round(
                pitch * terrainAngleQuantization
        );

        final int rollKey = (int) Math.round(
                roll * terrainAngleQuantization
        );

        if (!trickActive
                && !trickTransformApplied
                && terrainTiltApplied
                && pitchKey == appliedTerrainPitchKey
                && rollKey == appliedTerrainRollKey) {
            return;
        }

        transformApplier.apply(
                pitch,
                roll
        );

        appliedTerrainPitchKey = pitchKey;
        appliedTerrainRollKey = rollKey;
        terrainTiltApplied = true;
    }

    static LocalPoint offsetRelativeToPlayer(
            Player player,
            LocalPoint origin,
            int forward,
            int sideways) {

        final double angle =
                player.getCurrentOrientation()
                        * ORIENTATION_TO_RADIANS;

        return offsetRelativeToOrientation(
                origin,
                forward,
                sideways,
                Math.sin(angle),
                Math.cos(angle)
        );
    }

    private static LocalPoint offsetRelativeToOrientation(
            LocalPoint origin,
            int forward,
            int sideways,
            double sin,
            double cos) {

        final int offsetX = (int) Math.round(
                -sin * forward
                        - cos * sideways
        );

        final int offsetY = (int) Math.round(
                -cos * forward
                        + sin * sideways
        );

        return origin.plus(offsetX, offsetY);
    }

    private static double clamp(
            double value,
            double min,
            double max) {

        return Math.max(min, Math.min(max, value));
    }
}
