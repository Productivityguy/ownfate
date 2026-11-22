package org.ownfate.app.guard


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View

/**
 * The tab transition, without the blur: the old screen fades and lifts slightly, the new one
 * fades in while settling up from below. Short enough that a tap and the arrival feel like one
 * event, and it never runs when the system has animations switched off.
 */
object ScreenSwitch {

    private const val OUT_MS = 120L
    private const val IN_MS = 150L
    private const val LIFT_DP = 8f
    private const val RISE_DP = 12f

    /** The animator that is running now, so a reset can stop it instead of racing it. */
    private var active: ValueAnimator? = null

    /** Fade the current screen out and lift it a little. */
    fun out(view: View, onComplete: () -> Unit): ValueAnimator {
        stop()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 0f
            view.translationY = 0f
            onComplete()
            return ValueAnimator()
        }
        return run(
            view,
            view.alpha, 0f,
            view.translationY, -dp(view, LIFT_DP),
            OUT_MS, onComplete
        ).also { active = it }
    }

    /** Fade the newly installed screen in, rising from just below its resting place. */
    fun inView(view: View, onComplete: () -> Unit = {}): ValueAnimator {
        stop()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            reset(view)
            onComplete()
            return ValueAnimator()
        }
        view.alpha = 0f
        view.translationY = dp(view, RISE_DP)
        return run(
            view,
            0f, 1f,
            view.translationY, 0f,
            IN_MS, onComplete
        ).also { active = it }
    }

    fun reset(view: View) {
        stop()
        view.animate().cancel()
        view.alpha = 1f
        view.translationY = 0f
    }

    private fun stop() {
        val animator = active
        active = null
        animator?.cancel()
    }

    private fun run(
        view: View,
        startAlpha: Float,
        endAlpha: Float,
        startY: Float,
        endY: Float,
        duration: Long,
        onComplete: () -> Unit
    ): ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        addUpdateListener { animator ->
            val p = animator.animatedFraction
            view.alpha = startAlpha + (endAlpha - startAlpha) * p
            view.translationY = startY + (endY - startY) * p
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
                reset(view)
            }

            override fun onAnimationEnd(animation: Animator) {
                if (active === animation) active = null
                if (!cancelled) onComplete()
            }
        })
        start()
    }

    private fun dp(view: View, value: Float): Float =
        value * view.resources.displayMetrics.density
}
