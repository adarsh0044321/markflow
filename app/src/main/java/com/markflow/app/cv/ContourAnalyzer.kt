package com.markflow.app.cv

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.markflow.app.domain.model.BoundingBox
import com.markflow.app.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes contours (connected components) in binary masks to identify
 * individual mark candidates. Groups nearby contours that likely belong
 * to the same mark (e.g., fraction marks like "7/10").
 */
@Singleton
class ContourAnalyzer @Inject constructor() {

    /**
     * A detected contour region with its bounding box and properties.
     */
    data class ContourRegion(
        val boundingBox: BoundingBox,
        val area: Int,
        val pixelCount: Int,
        /** Density of white pixels within the bounding box (0-1) */
        val density: Double,
        /** Whether this region likely contains a fraction (e.g., 7/10) */
        val isFractionCandidate: Boolean = false,
        /** Sub-regions if this is a grouped contour */
        val subRegions: List<BoundingBox> = emptyList()
    )

    /**
     * Find all contour regions in a binary mask (white = foreground).
     * Uses connected component labeling via flood fill.
     *
     * @param mask Binary mask bitmap
     * @return List of ContourRegion candidates sorted top-to-bottom, left-to-right
     */
    fun findContours(mask: Bitmap): List<ContourRegion> {
        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        // Connected component labeling
        val labels = IntArray(width * height) { -1 }
        var currentLabel = 0
        val components = mutableMapOf<Int, MutableList<Int>>() // label -> pixel indices

        for (i in pixels.indices) {
            if (pixels[i] == Color.WHITE && labels[i] == -1) {
                // Flood fill this component
                val component = mutableListOf<Int>()
                floodFill(pixels, labels, width, height, i, currentLabel, component)
                if (component.size >= Constants.MIN_CONTOUR_AREA.toInt() / 4) {
                    components[currentLabel] = component
                }
                currentLabel++
            }
        }

        // Convert components to contour regions
        val regions = components.map { (_, pixelIndices) ->
            val xs = pixelIndices.map { it % width }
            val ys = pixelIndices.map { it / width }
            val minX = xs.min()
            val maxX = xs.max()
            val minY = ys.min()
            val maxY = ys.max()
            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val area = boxWidth * boxHeight

            ContourRegion(
                boundingBox = BoundingBox(minX, minY, boxWidth, boxHeight),
                area = area,
                pixelCount = pixelIndices.size,
                density = pixelIndices.size.toDouble() / area
            )
        }

        // Group nearby contours (for fractions, multi-digit marks, circled numbers)
        val grouped = groupNearbyContours(regions, width, height)

        // Filter by area and aspect ratio after grouping
        val filtered = grouped.filter { region ->
            val area = region.area.toDouble()
            val aspectRatio = region.boundingBox.width.toDouble() / region.boundingBox.height.toDouble()
            area >= Constants.MIN_CONTOUR_AREA &&
            area <= Constants.MAX_CONTOUR_AREA &&
            aspectRatio >= Constants.MIN_MARK_ASPECT_RATIO &&
            aspectRatio <= Constants.MAX_MARK_ASPECT_RATIO
        }

        // Sort top-to-bottom, then left-to-right
        return filtered.sortedWith(compareBy({ it.boundingBox.y }, { it.boundingBox.x }))
    }

    /**
     * Flood fill algorithm for connected component labeling.
     */
    private fun floodFill(
        pixels: IntArray,
        labels: IntArray,
        width: Int,
        height: Int,
        startIdx: Int,
        label: Int,
        component: MutableList<Int>
    ) {
        val stack = ArrayDeque<Int>()
        stack.addLast(startIdx)

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            if (idx < 0 || idx >= pixels.size) continue
            if (labels[idx] != -1) continue
            if (pixels[idx] != Color.WHITE) continue

            labels[idx] = label
            component.add(idx)

            val x = idx % width
            val y = idx / width

            // 4-connectivity neighbors
            if (x > 0) stack.addLast(idx - 1)
            if (x < width - 1) stack.addLast(idx + 1)
            if (y > 0) stack.addLast(idx - width)
            if (y < height - 1) stack.addLast(idx + width)
        }
    }

    /**
     * Group nearby contour regions that likely belong to the same mark.
     * This handles:
     * - Multi-digit numbers (e.g., "10" where "1" and "0" are separate contours)
     * - Fraction marks (e.g., "7/10" where numerator, slash, denominator are separate)
     * - Circled/boxed marks where the circle/box is a separate contour
     */
    private fun groupNearbyContours(
        regions: List<ContourRegion>,
        imageWidth: Int,
        imageHeight: Int
    ): List<ContourRegion> {
        if (regions.isEmpty()) return emptyList()

        val merged = mutableListOf<ContourRegion>()
        val used = BooleanArray(regions.size)
        
        // Custom directional thresholds based on image dimensions
        val thresholdX = (imageWidth * 0.035).toInt() // 3.5% of width for horizontal grouping
        val thresholdY = (imageHeight * 0.025).toInt() // 2.5% of height for vertical grouping

        for (i in regions.indices) {
            if (used[i]) continue

            val group = mutableListOf(regions[i])
            used[i] = true

            for (j in i + 1 until regions.size) {
                if (used[j]) continue

                // Check if regions are close enough to be part of the same mark (transitive grouping)
                val closeToAny = group.any { member ->
                    areClose(member.boundingBox, regions[j].boundingBox, thresholdX, thresholdY)
                }
                if (closeToAny) {
                    group.add(regions[j])
                    used[j] = true
                }
            }

            if (group.size == 1) {
                merged.add(group[0])
            } else {
                // Merge into a single bounding box
                val allBoxes = group.map { it.boundingBox }
                val minX = allBoxes.minOf { it.x }
                val minY = allBoxes.minOf { it.y }
                val maxX = allBoxes.maxOf { it.x + it.width }
                val maxY = allBoxes.maxOf { it.y + it.height }
                val totalPixels = group.sumOf { it.pixelCount }

                // Check if this looks like a fraction (vertically stacked sub-regions)
                val isFraction = isVerticallyStacked(allBoxes)

                merged.add(
                    ContourRegion(
                        boundingBox = BoundingBox(minX, minY, maxX - minX, maxY - minY),
                        area = (maxX - minX) * (maxY - minY),
                        pixelCount = totalPixels,
                        density = totalPixels.toDouble() / ((maxX - minX) * (maxY - minY)),
                        isFractionCandidate = isFraction,
                        subRegions = allBoxes
                    )
                )
            }
        }

        return merged
    }

    /**
     * Compute horizontal and vertical edge-to-edge distance between two bounding boxes.
     */
    private fun edgeDistance(a: BoundingBox, b: BoundingBox): Pair<Int, Int> {
        val dx = when {
            a.x + a.width < b.x -> b.x - (a.x + a.width)
            b.x + b.width < a.x -> a.x - (b.x + b.width)
            else -> 0
        }
        val dy = when {
            a.y + a.height < b.y -> b.y - (a.y + a.height)
            b.y + b.height < a.y -> a.y - (b.y + b.height)
            else -> 0
        }
        return dx to dy
    }

    /**
     * Check if two bounding boxes are close enough horizontally or vertically to be part of the same mark.
     */
    private fun areClose(a: BoundingBox, b: BoundingBox, thresholdX: Int, thresholdY: Int): Boolean {
        val (dx, dy) = edgeDistance(a, b)
        if (dx == 0 && dy == 0) return true

        // Horizontal proximity: horizontally close and vertically aligned/overlapping
        val isHorizontalClose = dx < thresholdX && dy < (minOf(a.height, b.height) * 0.8).toInt()

        // Vertical proximity: vertically close and horizontally aligned/overlapping
        val isVerticalClose = dy < thresholdY && dx < (minOf(a.width, b.width) * 0.8).toInt()

        return isHorizontalClose || isVerticalClose
    }

    /**
     * Determine if sub-regions are vertically stacked (fraction pattern).
     */
    private fun isVerticallyStacked(boxes: List<BoundingBox>): Boolean {
        if (boxes.size < 2) return false
        val sorted = boxes.sortedBy { it.y }
        val first = sorted.first()
        val last = sorted.last()

        // Check if they overlap horizontally but are stacked vertically
        val horizontalOverlap = first.x < last.x + last.width && last.x < first.x + first.width
        val verticalSeparation = last.y - (first.y + first.height)

        return horizontalOverlap && verticalSeparation > 0
    }

    /**
     * Checks if a contour contains an outer enclosing shape (circle/box) and returns
     * the bounding box of the inner regions (the digit/text inside the circle).
     */
    fun getInnerBoundingBox(contour: ContourRegion): BoundingBox? {
        if (contour.subRegions.size >= 2) {
            val sorted = contour.subRegions.sortedByDescending { it.width * it.height }
            val outer = sorted.first()
            val otherRegions = sorted.drop(1)

            val outerArea = outer.width * outer.height
            val nextLargestArea = otherRegions.first().width * otherRegions.first().height
            if (outerArea < nextLargestArea * 1.3) return null

            val margin = 15
            val allEnclosed = otherRegions.all { inner ->
                inner.x >= outer.x - margin &&
                inner.y >= outer.y - margin &&
                (inner.x + inner.width) <= (outer.x + outer.width) + margin &&
                (inner.y + inner.height) <= (outer.y + outer.height) + margin
            }
            if (allEnclosed) {
                val minX = otherRegions.minOf { it.x }
                val minY = otherRegions.minOf { it.y }
                val maxX = otherRegions.maxOf { it.x + it.width }
                val maxY = otherRegions.maxOf { it.y + it.height }
                return BoundingBox(minX, minY, maxX - minX, maxY - minY)
            }
        }
        return null
    }
}
