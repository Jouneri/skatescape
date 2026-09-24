package com.skatescape;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ModelData;

@Slf4j
final class SkateboardModelFactory {
    private static final int MODEL_SCALE_BASE = 128;

    private SkateboardModelFactory() {
    }

    static ModelData createCombinedSkateboardData(
            Client client,
            int plankItemId,
            boolean showBoard,
            boolean showWheels,
            int wheelItemId,
            int wheelSizePercent,
            int wheelFrontBackOffset,
            int wheelLeftRightOffset,
            int wheelHeight) {

        final ModelData[] parts = new ModelData[5];
        int partCount = 0;

        if (showBoard) {
            final int plankModelId =
                    client.getItemDefinition(plankItemId)
                            .getInventoryModel();

            ModelData plankData = client.loadModelData(plankModelId);

            if (plankData == null) {
                log.warn("Could not load SkateScape plank model");
            } else {
                plankData = plankData.cloneVertices();

                // Sacred existing plank centering. Do not disturb. :D
                SkateboardModelGeometry.centerModel(plankData, false);

                /*
                 * keep the stock plank completely untouched and add only
                 * the missing underside.
                 *
                 * The extra copy is flattened onto the plank's real bottom
                 * plane before it is merged. Its top-face triangles therefore
                 * become a bottom cap, while the copied side faces collapse down
                 * to the perimeter instead of creating a second full plank.
                 * The cap's winding is then reversed so it renders downward.
                 *
                 * Result: original top/sides/lighting stay exactly as they were,
                 * but the deck is no longer hollow when a trick exposes it.
                 */
                final ModelData cappedPlank =
                        createBottomCappedPlank(client, plankData);

                parts[partCount++] =
                        cappedPlank != null
                                ? cappedPlank
                                : plankData;
            }
        }

        if (showWheels) {
            final ModelData[] wheelParts = createWheelParts(
                    client,
                    wheelItemId,
                    wheelSizePercent,
                    wheelFrontBackOffset,
                    wheelLeftRightOffset,
                    wheelHeight
            );

            if (wheelParts != null) {
                for (ModelData wheelPart : wheelParts) {
                    parts[partCount++] = wheelPart;
                }
            }
        }

        if (partCount == 0) {
            return null;
        }

        final ModelData combinedData =
                client.mergeModels(parts, partCount);

        if (combinedData == null) {
            log.warn("Could not merge SkateScape model");
        }

        return combinedData;
    }

    private static ModelData createBottomCappedPlank(
            Client client,
            ModelData plankData) {

        if (plankData == null) {
            return null;
        }

        final int originalFaceCount =
                plankData.getFaceCount();

        if (originalFaceCount <= 0) {
            return plankData;
        }

        /*
         * Build a second copy only as raw material for the missing bottom cap.
         * Clone vertices before changing Y so the stock plank stays untouched.
         */
        final ModelData bottomCap =
                plankData
                        .shallowCopy()
                        .cloneVertices();

        final float[] capY =
                bottomCap.getVerticesY();

        if (capY == null || capY.length == 0) {
            return plankData;
        }

        /*
         * RuneScape model Y uses negative values upward, so the largest Y is
         * the lowest point of the deck. Flatten every cap vertex onto that
         * plane. The original top-surface triangles become the missing bottom
         * surface; the copied vertical side triangles collapse to the perimeter.
         */
        float bottomY = capY[0];

        for (int i = 1; i < capY.length; i++) {
            bottomY = Math.max(bottomY, capY[i]);
        }

        for (int i = 0; i < capY.length; i++) {
            capY[i] = bottomY;
        }

        final ModelData cappedPlank =
                client.mergeModels(
                        new ModelData[] {
                                plankData,
                                bottomCap
                        },
                        2
                );

        if (cappedPlank == null) {
            return null;
        }

        final int[] faceA = cappedPlank.getFaceIndices1();
        final int[] faceB = cappedPlank.getFaceIndices2();
        final int[] faceC = cappedPlank.getFaceIndices3();

        if (faceA == null || faceB == null || faceC == null) {
            return null;
        }

        final int availableFaces = Math.min(
                cappedPlank.getFaceCount(),
                Math.min(
                        faceA.length,
                        Math.min(faceB.length, faceC.length)
                )
        );

        /*
         * The first face set is the untouched stock plank. Reverse only the
         * flattened cap set: A-B-C -> A-C-B, so the new bottom faces are visible
         * when Mike Hawk flips the deck over.
         */
        final int capFaceStart =
                originalFaceCount;

        final int capFaceEnd = Math.min(
                availableFaces,
                originalFaceCount * 2
        );

        if (capFaceEnd <= capFaceStart) {
            return null;
        }

        for (int i = capFaceStart; i < capFaceEnd; i++) {
            final int temp = faceB[i];
            faceB[i] = faceC[i];
            faceC[i] = temp;
        }

        return cappedPlank;
    }

    private static ModelData[] createWheelParts(
            Client client,
            int wheelItemId,
            int wheelSizePercent,
            int frontBack,
            int leftRight,
            int wheelHeight) {

        if (wheelItemId < 0) {
            return null;
        }

        final int wheelModelId;

        try {
            wheelModelId = client.getItemDefinition(wheelItemId)
                    .getInventoryModel();
        } catch (Exception ex) {
            log.debug(
                    "Could not resolve wheel item ID {}",
                    wheelItemId,
                    ex
            );
            return null;
        }

        final ModelData sourceWheelData =
                client.loadModelData(wheelModelId);

        if (sourceWheelData == null) {
            log.debug(
                    "Could not load wheel model for item ID {}",
                    wheelItemId
            );
            return null;
        }

        final int scale = Math.max(
                1,
                Math.round(
                        MODEL_SCALE_BASE
                                * Math.max(1, wheelSizePercent)
                                / 100.0f
                )
        );

        /*
         * Relative wheel positions:
         *
         * 0 = front-left
         * 1 = front-right
         * 2 = rear-left
         * 3 = rear-right
         *
         * Model-space conversion:
         *   X = -sideways
         *   Z = -forward
         *
         * Applying the player's yaw to the combined object turns these into
         * the intended world-relative wheel offsets.
         */
        final int[] forwardOffsets = {
                frontBack,
                frontBack,
                -frontBack,
                -frontBack
        };

        final int[] sidewaysOffsets = {
                -leftRight,
                leftRight,
                -leftRight,
                leftRight
        };

        final ModelData[] wheelParts = new ModelData[4];

        for (int i = 0; i < wheelParts.length; i++) {
            ModelData wheelData =
                    sourceWheelData.shallowCopy().cloneVertices();

            SkateboardModelGeometry.centerModel(wheelData, true);
            wheelData.scale(scale, scale, scale);

            wheelData.translate(
                    -sidewaysOffsets[i],
                    wheelHeight,
                    -forwardOffsets[i]
            );

            wheelParts[i] = wheelData;
        }

        return wheelParts;
    }
}
