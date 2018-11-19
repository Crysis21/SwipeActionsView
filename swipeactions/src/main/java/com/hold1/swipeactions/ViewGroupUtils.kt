package com.hold1.swipeactions

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent

/**
 * A simple utility class copied from the design support library
 * source code (see j.mp/ViewGroupUtils).
 */
internal object ViewGroupUtils {
    private val sMatrix = Matrix()
    private val sRectF = RectF()
    private val sIdentity = Matrix()
    private val sRect = Rect()

    /**
     * Check if a given point in the parent's coordinates are within
     * the view bounds of the given direct child view.
     *
     * @param child child view to test
     * @param x     X coordinate to test, in the parent's coordinate system
     * @param y     Y coordinate to test, in the parent's coordinate system
     * @return true if the point is within the child's bounds, false otherwise
     */
    fun isPointInChildBounds(parent: ViewGroup?, child: View?, x: Int, y: Int): Boolean {
        getDescendantRect(parent, child, sRect)
        return sRect.contains(x, y)
    }

    /**
     * Retrieve the transformed bounding rect of an arbitrary descendant view.
     * This does not need to be a direct child.
     *
     * @param descendant descendant view to reference
     * @param out        rect to set to the bounds of the descendant view
     */
    private fun getDescendantRect(parent: ViewGroup?, descendant: View?, out: Rect) {
        out.set(0, 0, descendant?.width ?: 0, descendant?.height ?: 0)
        offsetDescendantRect(parent, descendant, out)
    }

    /**
     * This is a port of the common
     * [ViewGroup.offsetDescendantRectToMyCoords] from the
     * framework, but adapted to take transformations into account. The result
     * will be the bounding rect of the real transformed rect.
     *
     * @param descendant view defining the original coordinate system of rect
     * @param rect       the rect to offset from descendant to this view's coordinate system
     */
    private fun offsetDescendantRect(parent: ViewGroup?, descendant: View?, rect: Rect) {
        sMatrix.set(sIdentity)
        offsetDescendantMatrix(parent, descendant, sMatrix)
        sRectF.set(rect)
        sMatrix.mapRect(sRectF)
        val left = (sRectF.left + 0.5f).toInt()
        val top = (sRectF.top + 0.5f).toInt()
        val right = (sRectF.right + 0.5f).toInt()
        val bottom = (sRectF.bottom + 0.5f).toInt()
        rect.set(left, top, right, bottom)
    }

    private fun offsetDescendantMatrix(target: ViewParent?, view: View?, m: Matrix) {
        val parent = view?.parent
        if (parent is View && parent !== target) {
            val vp = parent as View
            offsetDescendantMatrix(target, vp, m)
            m.preTranslate((-vp.scrollX).toFloat(), (-vp.scrollY).toFloat())
        }
        m.preTranslate(view?.left?.toFloat() ?: 0F, view?.top?.toFloat() ?: 0F)
        if (view?.matrix?.isIdentity == false) {
            m.preConcat(view.matrix)
        }
    }
}