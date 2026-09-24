package com.skatescape;

import net.runelite.api.ModelData;

final class SkateboardModelGeometry {
    private SkateboardModelGeometry() {
    }

    static float getMaxVertexY(ModelData modelData) {
        if (modelData == null) {
            return 0.0f;
        }

        final float[] verticesY =
                modelData.getVerticesY();

        if (verticesY == null || verticesY.length == 0) {
            return 0.0f;
        }

        float maxY = verticesY[0];

        for (int i = 1; i < verticesY.length; i++) {
            maxY = Math.max(maxY, verticesY[i]);
        }

        return maxY;
    }

    static void rotateAroundZ(
            ModelData modelData,
            double angle) {

        final float[] verticesX = modelData.getVerticesX();
        final float[] verticesY = modelData.getVerticesY();

        final double sin = Math.sin(angle);
        final double cos = Math.cos(angle);

        for (int i = 0; i < verticesX.length; i++) {
            final double x = verticesX[i];
            final double y = verticesY[i];

            verticesX[i] = (float) (
                    x * cos - y * sin
            );

            verticesY[i] = (float) (
                    x * sin + y * cos
            );
        }
    }

    static void rotateAroundY(
            ModelData modelData,
            double angle) {

        final float[] verticesX = modelData.getVerticesX();
        final float[] verticesZ = modelData.getVerticesZ();

        final double sin = Math.sin(angle);
        final double cos = Math.cos(angle);

        for (int i = 0; i < verticesX.length; i++) {
            final double x = verticesX[i];
            final double z = verticesZ[i];

            verticesX[i] = (float) (
                    x * cos + z * sin
            );

            verticesZ[i] = (float) (
                    -x * sin + z * cos
            );
        }
    }

    static void rotateAroundX(
            ModelData modelData,
            double angle) {

        final float[] verticesY = modelData.getVerticesY();
        final float[] verticesZ = modelData.getVerticesZ();

        final double sin = Math.sin(angle);
        final double cos = Math.cos(angle);

        for (int i = 0; i < verticesY.length; i++) {
            final double y = verticesY[i];
            final double z = verticesZ[i];

            verticesY[i] = (float) (
                    y * cos - z * sin
            );

            verticesZ[i] = (float) (
                    y * sin + z * cos
            );
        }
    }

    static void rotateForTerrain(
            ModelData modelData,
            double pitch,
            double roll) {

        final float[] verticesX = modelData.getVerticesX();
        final float[] verticesY = modelData.getVerticesY();
        final float[] verticesZ = modelData.getVerticesZ();

        final double sinPitch = Math.sin(pitch);
        final double cosPitch = Math.cos(pitch);
        final double sinRoll = Math.sin(roll);
        final double cosRoll = Math.cos(roll);

        for (int i = 0; i < verticesX.length; i++) {
            final double x = verticesX[i];
            final double y = verticesY[i];
            final double z = verticesZ[i];

            /*
             * Pitch around model X.
             */
            final double pitchedY =
                    y * cosPitch - z * sinPitch;
            final double pitchedZ =
                    y * sinPitch + z * cosPitch;

            /*
             * Roll around model Z.
             */
            final double rolledX =
                    x * cosRoll - pitchedY * sinRoll;
            final double rolledY =
                    x * sinRoll + pitchedY * cosRoll;

            verticesX[i] = (float) rolledX;
            verticesY[i] = (float) rolledY;
            verticesZ[i] = (float) pitchedZ;
        }
    }

    /*
     * Horizontal centering is used for the plank.
     * Wheels are also centered vertically so wheelHeight starts from
     * the model's actual centre rather than an arbitrary item origin.
     */
    static void centerModel(
            ModelData modelData,
            boolean centerVertically) {

        final float[] verticesX = modelData.getVerticesX();
        final float[] verticesY = modelData.getVerticesY();
        final float[] verticesZ = modelData.getVerticesZ();

        if (verticesX.length == 0
                || verticesY.length == 0
                || verticesZ.length == 0) {
            return;
        }

        float minX = verticesX[0];
        float maxX = verticesX[0];
        float minY = verticesY[0];
        float maxY = verticesY[0];
        float minZ = verticesZ[0];
        float maxZ = verticesZ[0];

        for (int i = 1; i < verticesX.length; i++) {
            minX = Math.min(minX, verticesX[i]);
            maxX = Math.max(maxX, verticesX[i]);

            minY = Math.min(minY, verticesY[i]);
            maxY = Math.max(maxY, verticesY[i]);

            minZ = Math.min(minZ, verticesZ[i]);
            maxZ = Math.max(maxZ, verticesZ[i]);
        }

        final int centerX =
                Math.round((minX + maxX) / 2.0f);
        final int centerY = centerVertically
                ? Math.round((minY + maxY) / 2.0f)
                : 0;
        final int centerZ =
                Math.round((minZ + maxZ) / 2.0f);

        modelData.translate(
                -centerX,
                -centerY,
                -centerZ
        );
    }
}
