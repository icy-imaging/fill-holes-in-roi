/*
 * Copyright (c) 2010-2026. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.adufour.morphology;

import fr.icy.extension.kernel.roi.roi2d.ROI2DArea;
import fr.icy.extension.kernel.roi.roi3d.ROI3DArea;
import fr.icy.extension.plugin.abstract_.PluginActionable;
import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.model.roi.ROI;
import fr.icy.model.roi.ROI2D;
import fr.icy.model.roi.ROI3D;
import fr.icy.model.roi.ROIUtil;
import fr.icy.model.roi.mask.BooleanMask2D;
import fr.icy.model.roi.mask.BooleanMask3D;
import fr.icy.model.sequence.Sequence;
import fr.icy.system.IcyHandledException;
import plugins.adufour.blocks.tools.roi.ROIBlock;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.VarBoolean;
import plugins.adufour.vars.lang.VarROIArray;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Algorithms for filling holes in regions of interest
 *
 * @author Alexandre Dufour
 */
@IcyPluginName("Fill Holes in ROI")
public class FillHolesInROI extends PluginActionable implements ROIBlock {
    private boolean blockMode = false;

    private final VarROIArray roiIN = new VarROIArray("List of ROI");

    private final VarROIArray roiOUT = new VarROIArray("List of hole-filled ROI");

    @Override
    public void declareInput(final VarList inputMap) {
        blockMode = true;
        inputMap.add("List of ROI", roiIN);
    }

    @Override
    public void declareOutput(final VarList outputMap) {
        outputMap.add("List of hole-filled ROI", roiOUT);
    }

    @Override
    public void run() {
        roiOUT.setValue(new ROI[0]);

        Sequence sequence = null;
        final Iterable<ROI> rois;

        if (blockMode) {
            rois = Arrays.asList(roiIN.getValue());
        }
        else {
            sequence = getActiveSequence();
            if (sequence == null)
                throw new IcyHandledException("Fill holes in ROI: please open an image first.");
            rois = sequence.getROIs();
        }

        try {
            for (final ROI roi : rois) {
                if (roi instanceof ROI2D) {
                    final ROI2D roi2d = (ROI2D) (blockMode ? roi.getCopy() : roi);

                    final BooleanMask2D mask = roi2d.getBooleanMask(true);
                    final boolean change = fillHoles(mask);

                    if (blockMode) {
                        if (!change) {
                            roiOUT.add(roi2d);
                        }
                        else {
                            final ROI2D newROI = new ROI2DArea(mask);
                            ROIUtil.copyROIProperties(roi2d, newROI, true);
                            roiOUT.add(newROI);
                        }
                    }
                    else if (change) {
                        final ROI2DArea newROI = new ROI2DArea(mask);
                        ROIUtil.copyROIProperties(roi2d, newROI, true);
                        if (sequence != null) {
                            sequence.addROI(newROI);
                            sequence.removeROI(roi);
                        }
                    }
                }
                else if (roi instanceof ROI3D) {
                    final ROI3D roi3d = (ROI3D) (blockMode ? roi.getCopy() : roi);

                    final BooleanMask3D mask3d = roi3d.getBooleanMask(true);
                    final boolean change = fillHoles(mask3d);

                    if (blockMode) {
                        if (!change) {
                            roiOUT.add(roi3d);
                        }
                        else {
                            final ROI3D newROI = new ROI3DArea(mask3d);
                            ROIUtil.copyROIProperties(roi3d, newROI, true);
                            roiOUT.add(newROI);
                        }
                    }
                    else if (change) {
                        final ROI3DArea newROI = new ROI3DArea(mask3d);
                        ROIUtil.copyROIProperties(roi3d, newROI, true);
                        if (sequence != null) {
                            sequence.addROI(newROI);
                            sequence.removeROI(roi);
                        }
                    }
                }
                else if (blockMode)
                    roiOUT.add(roi);
            }
        }
        catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fill holes in the specified path iterator, and returns the result as a new Path2D. The
     * algorithm extracts all sub-paths of the iterator (i.e. sub-shapes), and computes their
     * algebraic area. Sub-paths with a negative algebraic area represent "holes" in the original
     * shape and are removed from the final path.<br>
     * <b>WARNING: Path filling does not work on unions of more than 2 ROI (some holes are not
     * detected due to the way Java AWT merges paths together)</b>
     *
     * @param path        a {@link PathIterator} object to browse
     * @param pathChanged an optional boolean variable that indicates whether the input and output paths are
     *                    different when the method returns (set to <code>null</code> if not necessary)
     * @return a new {@link Path2D} object with no holes (or null if the path is empty). Note that
     * the returned path may still comprise multiple sub-paths, but each of the sub-paths is
     * guaranteed to have no holes.
     */
    public static Path2D fillPath(final PathIterator path, final VarBoolean pathChanged) {
        if (pathChanged != null)
            pathChanged.setValue(false);

        final Path2D.Double filledPath = new Path2D.Double();
        boolean isPathEmpty = true;

        final double[] coords = new double[6];

        //ArrayList<ArrayList<Point2D.Double>> segments = new ArrayList<>();

        ArrayList<Point2D.Double> currentSegment = null;
        Path2D currentPath = null;

        while (!path.isDone()) {
            final int segment = path.currentSegment(coords);

            switch (segment) {
                case PathIterator.SEG_MOVETO:

                    //segments.add(currentSegment = new ArrayList<>());
                    currentSegment = new ArrayList<>();

                    currentSegment.add(new Point2D.Double(coords[0], coords[1]));
                    currentPath = new Path2D.Double();
                    currentPath.moveTo(coords[0], coords[1]);

                    break;
                case PathIterator.SEG_CLOSE:
                    if (currentSegment != null) {
                        currentPath.closePath();

                        System.out.print(currentSegment.size() + " points: algebraic area is ");

                        // calculate algebraic area
                        final int nm1 = currentSegment.size() - 1;

                        double area = 0;

                        // all points but the last
                        for (int i = 0; i < nm1; i++) {
                            final Point2D.Double p1 = currentSegment.get(i);
                            final Point2D.Double p2 = currentSegment.get(i + 1);
                            area += (p2.x * p1.y - p1.x * p2.y) * 0.5;
                        }

                        // last point
                        final Point2D.Double p1 = currentSegment.get(nm1);
                        final Point2D.Double p2 = currentSegment.get(0);
                        area += (p2.x * p1.y - p1.x * p2.y) * 0.5;

                        System.out.println(area > 0 ? "positive, keeping" : "negative, discarding");
                        if (area > 0) {
                            // flush the current path
                            filledPath.append(currentPath, false);
                            isPathEmpty = false;
                        }
                        else if (pathChanged != null)
                            pathChanged.setValue(true);
                    }
                    break;
                case PathIterator.SEG_CUBICTO:
                    if (currentSegment != null) {
                        currentSegment.add(new Point2D.Double(coords[4], coords[5]));
                        currentPath.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    }
                    break;
                case PathIterator.SEG_LINETO:
                    if (currentSegment != null) {
                        currentSegment.add(new Point2D.Double(coords[0], coords[1]));
                        currentPath.lineTo(coords[0], coords[1]);
                    }
                    break;
                case PathIterator.SEG_QUADTO:
                    if (currentSegment != null) {
                        currentSegment.add(new Point2D.Double(coords[2], coords[3]));
                        currentPath.quadTo(coords[0], coords[1], coords[2], coords[3]);
                    }
                    break;
            }
            path.next();
        }

        return isPathEmpty ? null : filledPath;
    }

    /**
     * Fill holes in the specified boolean mask. The algorithm extracts the background by flooding
     * from the mask edges, then fills all remaining background pixels (i.e., the holes in the mask)
     *
     * @param mask boolean mask
     * @return <code>true</code> if the mask has changed, <code>false</code> otherwise (i.e., no hole
     * was detected)
     */
    public static boolean fillHoles(final BooleanMask2D mask) {
        final int width = mask.bounds.width;
        final int height = mask.bounds.height;

        // avoid single line / column masks
        if (width == 1 || height == 1)
            return false;

        final int slice = width * height;

        final boolean[] isObject = mask.mask;

        final byte[] labels = new byte[slice];
        final byte UNKNOWN = 0;
        final byte TO_VISIT = 1;
        final byte BACKGROUND = 2;

        // hold a list of pixels to visit
        final int[] pixelOffsetsToVisit = new int[slice];

        int n = 0;

        // 1) extract background pixels on the image edges

        // 1.a) top + bottom edges
        for (int top = 0, bottom = slice - width; top < width; top++, bottom++) {
            if (!isObject[top]) {
                labels[top] = BACKGROUND;

                final int neighbor = top + width;
                if (labels[neighbor] == UNKNOWN) {
                    pixelOffsetsToVisit[n++] = neighbor;
                    labels[neighbor] = TO_VISIT;
                }
            }

            if (!isObject[bottom]) {
                labels[bottom] = BACKGROUND;

                final int neighbor = bottom - width;
                if (labels[neighbor] == UNKNOWN) {
                    pixelOffsetsToVisit[n++] = neighbor;
                    labels[neighbor] = TO_VISIT;
                }
            }
        }

        // 1.b) left + right edges (minus the corners, done above)
        for (int left = width, right = 2 * width - 1; left < slice - width; left += width, right += width) {
            if (!isObject[left]) {
                labels[left] = BACKGROUND;

                final int neighbor = left + 1;
                if (labels[neighbor] == UNKNOWN) {
                    pixelOffsetsToVisit[n++] = neighbor;
                    labels[neighbor] = TO_VISIT;
                }
            }

            if (!isObject[right]) {
                labels[right] = BACKGROUND;

                final int neighbor = right - 1;
                if (labels[neighbor] == UNKNOWN) {
                    pixelOffsetsToVisit[n++] = neighbor;
                    labels[neighbor] = TO_VISIT;
                }
            }
        }

        // process only if there is something to process...
        if (n > 0) {
            // 2) flood the image from the list of (background) border pixels

            final int[] neighbors = new int[4];

            for (int index = 0; index < n; index++) {
                final int offset = pixelOffsetsToVisit[index];

                // if the current pixel is already tagged 'background'
                // it's neighbors are already in the list of pixels to visit
                // => don't go further
                if (labels[offset] == BACKGROUND)
                    continue;

                // if background => flood
                if (!isObject[offset]) {
                    // tag the current pixel
                    labels[offset] = BACKGROUND;

                    // calculate neighbor offsets
                    neighbors[0] = offset - 1;
                    neighbors[1] = offset + 1;
                    neighbors[2] = offset - width;
                    neighbors[3] = offset + width;

                    // and add its immediate neighbor to the list of pixels to visit
                    for (final int neighbor : neighbors)
                        if (labels[neighbor] == UNKNOWN && !isObject[neighbor]) {
                            pixelOffsetsToVisit[n++] = neighbor;
                            labels[neighbor] = TO_VISIT;
                        }
                }
            }
        }

        // 3) fill holes by filling all "UNKNOWN" pixels out of the mask

        boolean change = false;

        for (int i = 0; i < slice; i++)
            if (labels[i] == UNKNOWN && !isObject[i]) {
                isObject[i] = true;
                change = true;
            }

        return change;
    }

    private static boolean fillHoles(final BooleanMask3D mask3d) {
        boolean change = false;
        int slice = mask3d.bounds.z;
        for (int k = 0; k < mask3d.bounds.sizeZ; k++) {
            final BooleanMask2D mask2d = mask3d.getMask2D(slice);
            change = fillHoles(mask2d) || change;
            slice++;
        }
        return change;
    }
}
