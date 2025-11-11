package com.alphawallet.app.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import com.alphawallet.app.R

/**
 * Compound view that renders a spinning progress indicator with knob animation and completion state.
 */
class ProgressKnobkerry @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val spinner: ProgressBar
    private val spinnerKnob: ProgressBar
    private val indeterminate: ProgressBar
    private val progressComplete: ImageView

    init {
        inflate(context, R.layout.item_progress_knobkerry, this)
        spinner = findViewById(R.id._progress_bar_main)
        spinnerKnob = findViewById(R.id._progress_bar_knob)
        indeterminate = findViewById(R.id._progress_bar_waiting)
        progressComplete = findViewById(R.id._progress_complete)
    }

    /**
     * Starts the animation sequence with a fixed duration until completion.
     *
     * @param secondsToComplete Total time the progress should take to finish.
     */
    fun startAnimation(secondsToComplete: Long) {
        spinner.visibility = View.VISIBLE
        spinnerKnob.visibility = View.VISIBLE
        indeterminate.visibility = View.GONE

        ObjectAnimator.ofInt(spinner, "progress", 0, SPINNER_MAX_PROGRESS).apply {
            duration = secondsToComplete * DateUtils.SECOND_IN_MILLIS
            interpolator = LinearInterpolator()
            start()
        }

        AnimationUtils.loadAnimation(context, R.anim.rotate_knob_anim).apply {
            duration = secondsToComplete * DateUtils.SECOND_IN_MILLIS
            repeatCount = Animation.INFINITE
            spinnerKnob.startAnimation(this)
        }
    }

    /**
     * Starts the animation sequence using a window of time, ensuring the indicator is aligned with progress.
     *
     * @param startSeconds Epoch seconds when the progress started.
     * @param endSeconds Epoch seconds when the progress should complete.
     */
    fun startAnimation(startSeconds: Long, endSeconds: Long) {
        spinner.visibility = View.VISIBLE
        spinnerKnob.visibility = View.VISIBLE
        indeterminate.visibility = View.GONE

        var adjustedEndSeconds = endSeconds
        if (adjustedEndSeconds < startSeconds) {
            adjustedEndSeconds = startSeconds + DEFAULT_DURATION_FALLBACK_SECONDS
        }

        val currentSeconds = System.currentTimeMillis() / DateUtils.SECOND_IN_MILLIS
        val spinnerCycleTime = adjustedEndSeconds - startSeconds

        val rawFraction = (currentSeconds - startSeconds).toFloat() / spinnerCycleTime.toFloat()
        val fractionComplete = rawFraction.coerceIn(0f, 1f)
        val completionDuration =
            if (currentSeconds < adjustedEndSeconds) (adjustedEndSeconds - currentSeconds).toInt() else 1

        ObjectAnimator.ofInt(
            spinner,
            "progress",
            (fractionComplete * SPINNER_MAX_PROGRESS).toInt(),
            SPINNER_MAX_PROGRESS
        ).apply {
            duration = completionDuration * DateUtils.SECOND_IN_MILLIS
            interpolator = LinearInterpolator()
            start()
        }

        val startDegrees = 360f * fractionComplete
        rotateKnob(startDegrees, completionDuration.toLong(), infinite = false).setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) = Unit

                override fun onAnimationEnd(animation: Animation) {
                    rotateKnob(0f, spinnerCycleTime, infinite = true)
                }

                override fun onAnimationRepeat(animation: Animation) = Unit
            }
        )
    }

    /**
     * Spins the knob from the supplied start position; optionally repeats indefinitely.
     */
    private fun rotateKnob(startDegrees: Float, completionDurationSeconds: Long, infinite: Boolean): RotateAnimation {
        val rotate = RotateAnimation(
            startDegrees,
            360f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        rotate.duration = completionDurationSeconds * DateUtils.SECOND_IN_MILLIS
        rotate.interpolator = LinearInterpolator()
        rotate.repeatCount = if (infinite) Animation.INFINITE else 0
        spinnerKnob.startAnimation(rotate)
        return rotate
    }

    /**
     * Shows an indeterminate waiting animation when progress data is unavailable.
     */
    fun waitCycle() {
        spinner.visibility = View.GONE
        spinnerKnob.visibility = View.GONE
        indeterminate.visibility = View.VISIBLE

        AnimationUtils.loadAnimation(context, R.anim.rotate_knob_anim).apply {
            duration = 2 * DateUtils.SECOND_IN_MILLIS
            repeatCount = Animation.INFINITE
            indeterminate.startAnimation(this)
        }
    }

    /**
     * Ends the animation cycle and displays success or failure iconography.
     *
     * @param succeeded True to show a success indicator, otherwise displays failure.
     */
    fun setComplete(succeeded: Boolean) {
        indeterminate.clearAnimation()
        spinnerKnob.clearAnimation()
        spinner.clearAnimation()

        progressComplete.setImageResource(
            if (succeeded) {
                R.drawable.ic_correct
            } else {
                R.drawable.ic_tx_fail
            }
        )

        progressComplete.alpha = 0f
        progressComplete.visibility = View.VISIBLE
        progressComplete.animate()
            .alpha(1f)
            .setDuration(COMPLETE_FADE_DURATION_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    spinner.visibility = View.GONE
                    spinnerKnob.visibility = View.GONE
                    indeterminate.visibility = View.GONE
                }
            })
    }

    companion object {
        private const val SPINNER_MAX_PROGRESS = 500
        private const val DEFAULT_DURATION_FALLBACK_SECONDS = 60
        private const val COMPLETE_FADE_DURATION_MS = 500L
    }
}
