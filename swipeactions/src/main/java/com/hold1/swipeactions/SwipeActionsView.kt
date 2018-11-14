package com.hold1.swipeactions

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.support.annotation.IntDef
import android.support.annotation.NonNull
import android.support.annotation.Nullable
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
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr),
        GestureDetector.OnGestureListener,
        Animator.AnimatorListener,
        ValueAnimator.AnimatorUpdateListener {

    @IntDef(REVEAL_NONE, REVEAL_START, REVEAL_END)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class RevealMode {}

    @IntDef(STATE_CLOSED, STATE_OPEN_START, STATE_OPEN_END)
    @Retention(AnnotationRetention.SOURCE)
    annotation class OpenState {}

    private var gestureDetector: GestureDetector = GestureDetector(context, this)
    private var animator = ValueAnimator.ofFloat(0F, 0F)
    private var startReveal: View? = null
    private var endReveal: View? = null
    private var mainView: View? = null

    private var touchX = 0F
    private var touchY = 0F
    private var moveX = 0F

    private var startMoveDistance = 0
    private var endMoveDistance = 0

    private var startAlpha = 1.0f
    private var startScale = 1.0f

    private var viewInSlideMode = true

    @OpenState
    var openState: Int = STATE_CLOSED
        private set

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
        a.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if ((child.layoutParams as SwipeRevealLayoutParams).revealMode == Companion.REVEAL_START) {
                startReveal = child
            } else if ((child.layoutParams as SwipeRevealLayoutParams).revealMode == Companion.REVEAL_END) {
                endReveal = child
            } else if (i == childCount - 1) {
                mainView = child
            } else {
                child.visibility = View.GONE
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        startMoveDistance = startReveal?.measuredWidth ?: 0
        endMoveDistance = endReveal?.measuredWidth ?: 0

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
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Timber.d("action ${event?.action}")
        if (gestureDetector.onTouchEvent(event)) return true
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moveX = event.x - touchX
                if (abs(moveX) > ViewConfiguration.get(context).scaledTouchSlop) {
                    viewInSlideMode = true
                }
                if (viewInSlideMode) {
                    moveX = event.x - touchX
                    applyMovement(moveX)
                    touchY = event.y
                    touchX = event.x
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                //Trigger animation
                viewInSlideMode = false

                //Set the final state
                val currentTranslation = mainView?.translationX ?: 0F
                if (currentTranslation > 0) {
                    if (currentTranslation > startMoveDistance / 2) {
                        setState(STATE_OPEN_START)
                    } else {
                        setState(STATE_CLOSED)
                    }
                } else {
                    if (abs(currentTranslation) > endMoveDistance / 2) {
                        setState(STATE_OPEN_END)
                    } else {
                        setState(STATE_CLOSED)
                    }
                }

                return true
            }
        }
        return false
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

    private fun setState(@OpenState state: Int) {
        var animDuration = ANIM_DURATION
        var startValue: Float = 0F
        var endValue: Float = 0F
        when (state) {
            STATE_CLOSED -> {
                startValue = mainView?.translationX ?: 0F
                endValue = 0F
                if (startValue > 0) {
                    animDuration = (startValue / startMoveDistance.toFloat() * animDuration).toInt()
                } else {
                    animDuration = ((abs(startValue) / endMoveDistance.toFloat()) * animDuration).toInt()
                }
            }
            STATE_OPEN_END -> {
                startValue = mainView?.translationX ?: 0F
                endValue = -endMoveDistance.toFloat()
                animDuration = (abs(startValue) / abs(endValue) * animDuration).toInt()
            }
            STATE_OPEN_START -> {
                startValue = mainView?.translationX ?: 0F
                endValue = startMoveDistance.toFloat()
                animDuration = (abs(startValue) / endValue * animDuration).toInt()
            }
        }
        animator.setFloatValues(startValue, endValue)
        animator.duration = animDuration.toLong()
        animator.start()
        this.openState = state
    }

    override fun onShowPress(e: MotionEvent?) = Unit

    override fun onSingleTapUp(e: MotionEvent?) = false

    override fun onDown(e: MotionEvent?): Boolean = false

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        Timber.d("fling it")
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

    override fun onAnimationEnd(animation: Animator?) {
    }

    override fun onAnimationStart(animation: Animator?) {
    }

    override fun onAnimationRepeat(animation: Animator?) {
    }

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

    companion object {
        const val STATE_CLOSED = 0
        const val STATE_OPEN_END = 2
        const val STATE_OPEN_START = 1
        const val REVEAL_END = 1
        const val REVEAL_NONE = -1
        const val REVEAL_START = 0
        private val ANIM_DURATION = 240
    }
}