package com.hold1.swipeactions

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.support.annotation.IntDef
import android.support.annotation.NonNull
import android.support.annotation.Nullable
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign


/**
 * Created by Cristian Holdunu on 14/11/2018.
 */
class SwipeActionsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    GestureDetector.OnGestureListener,
    Animator.AnimatorListener,
    ValueAnimator.AnimatorUpdateListener {

    @IntDef(STATE_CLOSED, STATE_OPEN_START, STATE_OPEN_END)
    @Retention(AnnotationRetention.SOURCE)
    annotation class OpenState {}

    private var gestureDetector: GestureDetector = GestureDetector(context, this)
    private var animator = ValueAnimator.ofFloat(0F, 0F)
    private var startReveal: View? = null
    private var endReveal: View? = null
    private var mainView: View? = null

    private var touchX = 0F
    private var moveX = 0F

    //Used to detect event handling. WIP
    private var dragDist = 0F
    private var dragTouchX = 0F

    private var startMoveDistance = 0
    private var endMoveDistance = 0

    private var startAlpha = 1.0f
    private var startScale = 1.0f

    private var viewInSlideMode = false
    private var disableParentClipping = false

    var cornerRadius = 0
    var elevation = 0

    private var outlineProvider = CustomOutline()

    @OpenState
    var openState: Int = STATE_CLOSED
        private set

    private var structure: Int = STRUCTURE_STACK

    init {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        animator.addUpdateListener(this)
        animator.addListener(this)
        animator.setDuration(ANIM_DURATION.toLong())

        val a = context.obtainStyledAttributes(attrs, R.styleable.SwipeActionsView)
        startAlpha = a.getFloat(R.styleable.SwipeActionsView_swStartAlpha, startAlpha)
        startScale = a.getFloat(R.styleable.SwipeActionsView_swStartScale, startScale)
        disableParentClipping = a.getBoolean(R.styleable.SwipeActionsView_swDisableParentClipping, false)
        cornerRadius = a.getDimensionPixelSize(R.styleable.SwipeActionsView_swCornerRadius, 0)
        elevation = a.getDimensionPixelSize(R.styleable.SwipeActionsView_swElevation, 0)
        structure = a.getInt(R.styleable.SwipeActionsView_swStructure, STRUCTURE_STACK)
        a.recycle()

        clipToPadding = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if ((child.layoutParams as SwipeRevealLayoutParams).revealMode == REVEAL_START) {
                startReveal = child
            } else if ((child.layoutParams as SwipeRevealLayoutParams).revealMode == REVEAL_END) {
                endReveal = child
            } else if (i == childCount - 1) {
                mainView = child
            } else {
                child.visibility = View.GONE
            }

            startReveal?.let { view ->
                view.setPadding(view.left, view.top, view.right + 2 * cornerRadius, view.bottom)
            }
            endReveal?.let { view ->
                view.setPadding(view.left + 2 * cornerRadius, view.top, view.right, view.bottom)
            }
        }

        mainView?.let {
            ViewCompat.setElevation(it, elevation.toFloat())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (cornerRadius > 0) {
                mainView?.outlineProvider = outlineProvider
                startReveal?.outlineProvider = outlineProvider
                endReveal?.outlineProvider = outlineProvider
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (disableParentClipping)
            (parent as? ViewGroup)?.clipChildren = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        startReveal?.let {
            startMoveDistance = it.measuredWidth - 2 * cornerRadius
        }
        endReveal?.let {
            endMoveDistance = it.measuredWidth - 2 * cornerRadius
        }

        Timber.d("startDistance=$startMoveDistance endDistance=$endMoveDistance")
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        startReveal?.pivotX = startReveal?.measuredWidth?.toFloat() ?: 0F
        startReveal?.pivotY = (startReveal?.measuredHeight?.toFloat() ?: 0F) / 2
        endReveal?.pivotX = 0f
        endReveal?.pivotY = (endReveal?.measuredHeight?.toFloat() ?: 0F) / 2

        startReveal?.scaleX = startScale
        startReveal?.scaleY = startScale
        endReveal?.scaleY = startScale
        endReveal?.scaleX = startScale

        startReveal?.alpha = startAlpha
        endReveal?.alpha = startAlpha

        if (structure == STRUCTURE_LINEAR) {
            //We need to translate the views outside original viewport
            startReveal?.translationX = (-startMoveDistance).toFloat()
            endReveal?.translationX = endMoveDistance.toFloat()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Timber.d("onTouchEvent action ${event?.action}")
        if (gestureDetector.onTouchEvent(event)) return true
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moveX = event.x - touchX
                if (abs(moveX) > ViewConfiguration.get(context).scaledTouchSlop) {
                    if (!viewInSlideMode) {
                        touchX = event.x
                    }
                    startTracking()

                }
                if (viewInSlideMode) {
                    moveX = event.x - touchX
                    applyMovement(moveX)
                    touchX = event.x
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                stopTracking()
                return true
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (!isEnabled) {
            return false
        }
        when (ev?.action) {
            MotionEvent.ACTION_DOWN -> {
                dragDist = 0F
                dragTouchX = ev.x
            }
            MotionEvent.ACTION_MOVE -> {
                dragDist = abs(ev.x - dragTouchX)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                dragDist = 0F
            }
        }

        if (dragDist > ViewConfiguration.get(context).scaledTouchSlop) {
            return true
        } else {
            return super.onInterceptTouchEvent(ev)
        }
    }

    private fun startTracking() {
        viewInSlideMode = true
        attemptClaimDrag()
    }

    private fun attemptClaimDrag() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun stopTracking() {
        viewInSlideMode = false

        //Set the final state
        val currentTranslation = mainView?.translationX ?: 0F
        if (currentTranslation > 0) {
            if (currentTranslation > startMoveDistance / 2 - cornerRadius) {
                setState(STATE_OPEN_START)
            } else {
                setState(STATE_CLOSED)
            }
        } else {
            if (abs(currentTranslation) > endMoveDistance / 2 - cornerRadius) {
                setState(STATE_OPEN_END)
            } else {
                setState(STATE_CLOSED)
            }
        }
    }

    private fun applyMovement(move: Float) {
        Timber.d("applyMovement move=$move")
        var newPosition = (mainView?.translationX ?: 0.0F) + move
        if ((sign(newPosition) > 0 && openState == STATE_OPEN_END) || sign(newPosition) < 0 && openState == STATE_OPEN_START) {
            newPosition = 0F
        }
        val translation: Float?
        if (newPosition < 0) {
            //Handle handle end reveal
            translation = max(newPosition, -endMoveDistance.toFloat())
        } else {
            translation = min(newPosition, startMoveDistance.toFloat())
        }
        applyTranslationInner(translation)
    }

    private fun applyTranslationInner(translation: Float) {
        mainView?.translationX = translation

        if (structure == STRUCTURE_LINEAR) {
            endReveal?.translationX = endMoveDistance + translation
            startReveal?.translationX = -startMoveDistance + translation
        }
        if (translation < 0) {
            val percentage = abs(translation) / endMoveDistance.toFloat()
            endReveal?.alpha = startAlpha + (1f - startAlpha) * percentage
            endReveal?.scaleX = startScale + (1f - startScale) * percentage
            endReveal?.scaleY = startScale + (1f - startScale) * percentage

        } else {
            val percentage = translation / startMoveDistance.toFloat()
            startReveal?.alpha = startAlpha + (1f - startAlpha) * percentage
            startReveal?.scaleX = startScale + (1f - startScale) * percentage
            startReveal?.scaleY = startScale + (1f - startScale) * percentage
        }
    }

    fun setState(@OpenState state: Int, animDuration: Int = ANIM_DURATION) {
        var duration = animDuration
        var startValue: Float = 0F
        var endValue: Float = 0F
        when (state) {
            STATE_CLOSED -> {
                startValue = mainView?.translationX ?: 0F
                endValue = 0F
                if (startValue > 0) {
                    duration = (startValue / startMoveDistance.toFloat() * duration).toInt()
                } else {
                    duration = ((abs(startValue) / endMoveDistance.toFloat()) * duration).toInt()
                }
            }
            STATE_OPEN_END -> {
                startValue = mainView?.translationX ?: 0F
                endValue = -endMoveDistance.toFloat()
                duration = (abs(startValue) / endMoveDistance * duration).toInt()
            }
            STATE_OPEN_START -> {
                startValue = mainView?.translationX ?: 0F
                endValue = startMoveDistance.toFloat()
                duration = (abs(startValue) / startMoveDistance * duration).toInt()
            }
        }
        animator?.cancel()
        animator.setFloatValues(startValue, endValue)
        animator.duration = duration.toLong()
        animator.start()
        this.openState = state
    }

    override fun onShowPress(e: MotionEvent?) = Unit

    override fun onSingleTapUp(e: MotionEvent?) = false

    override fun onDown(e: MotionEvent?): Boolean = false

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        Timber.d("fling it $velocityX $velocityY")
        if (abs(velocityX) > abs(velocityY)) {
            if (abs(velocityX) > 600) {
                if (velocityX < 0) {
                    if (openState == STATE_CLOSED)
                        setState(STATE_OPEN_END)
                    else if (openState == STATE_OPEN_START)
                        setState(STATE_CLOSED)

                } else {
                    if (openState == STATE_CLOSED)
                        setState(STATE_OPEN_START)
                    else if (openState == STATE_OPEN_END)
                        setState(STATE_CLOSED)
                }
                return true
            }
        }
        return false
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean = false

    override fun onLongPress(e: MotionEvent?) = Unit

    override fun onAnimationUpdate(animation: ValueAnimator?) {
        val translation = animation?.getAnimatedValue() as Float
        applyTranslationInner(translation)
    }

    override fun onAnimationEnd(animation: Animator?) = Unit

    override fun onAnimationStart(animation: Animator?) = Unit

    override fun onAnimationRepeat(animation: Animator?) = Unit

    override fun onAnimationCancel(animation: Animator?) {
        when (openState) {
            STATE_CLOSED -> applyTranslationInner(0F)
            STATE_OPEN_START -> applyTranslationInner(startMoveDistance.toFloat())
            STATE_OPEN_END -> applyTranslationInner(-endMoveDistance.toFloat())
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is SwipeRevealLayoutParams
    }

    override fun generateLayoutParams(attrs: AttributeSet): FrameLayout.LayoutParams {
        return SwipeRevealLayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): FrameLayout.LayoutParams {
        return SwipeRevealLayoutParams(p as SwipeRevealLayoutParams)
    }

    inner class SwipeRevealLayoutParams : LayoutParams {

        var revealMode = REVEAL_NONE

        constructor(@NonNull c: Context, @Nullable attrs: AttributeSet) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.SwipeActionsView_Layout)
            revealMode = a.getInt(R.styleable.SwipeActionsView_Layout_swReveal, Companion.REVEAL_NONE)
            when (revealMode) {
                REVEAL_START -> gravity = Gravity.START
                REVEAL_END -> gravity = Gravity.END
            }
            a.recycle()
        }

        constructor(params: SwipeRevealLayoutParams) : super(params)
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class CustomOutline internal constructor() : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
            view.clipToOutline = true
        }
    }

    companion object {
        const val STATE_CLOSED = 0
        const val STATE_OPEN_END = 2
        const val STATE_OPEN_START = 1
        const val REVEAL_END = 1
        const val REVEAL_NONE = -1
        const val REVEAL_START = 0
        const val STRUCTURE_STACK = 0
        const val STRUCTURE_LINEAR = 1
        private const val ANIM_DURATION = 240
    }
}