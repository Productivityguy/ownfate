package org.ownfate.app.data.apps

import org.ownfate.app.data.Store
import org.ownfate.app.guard.OffSwitch

import android.content.Context

/**
 * The one answer to "should this app be bounced, and why".
 *
 * Three lists can block a package: the user's own list, the bundled gambling apps, and the
 * bundled prediction-market apps. The last two are category switches; the user's own list is
 * always on. The exclude list wins over everything: it is the explicit "not monitored" answer.
 *
 * Settings shells, launchers and OwnFate itself are never blocked, even if a package somehow
 * reaches a list. A blocker that can be switched off from the blocked list is not a blocker.
 */
object AppBlocklist {

    enum class Reason(val title: String, val kicker: String) {
        CUSTOM("Blocked app", "blocked app"),
        PREDICTION("Prediction market app", "prediction-market app"),
        GAMBLING("Gambling app", "gambling app")
    }

    /** Why [pkg] is blocked right now, or null when it should be left alone. */
    fun reason(ctx: Context, pkg: String): Reason? {
        if (pkg == ctx.packageName || OffSwitch.looksLikeSettings(pkg)) return null
        if (pkg in Store.set(ctx, Store.ALLOW_APPS)) return null
        if (pkg in Store.set(ctx, Store.CUSTOM_APPS)) return Reason.CUSTOM

        val removed = Store.set(ctx, Store.REMOVED_BUNDLED)
        if (pkg in removed) return null
        if (Store.bool(ctx, Store.PREDICTION_MARKETS, false) && pkg in PredictionApps.bundled) {
            return Reason.PREDICTION
        }
        if (Store.bool(ctx, Store.GAMBLING_APPS, false) && pkg in GamblingApps.bundled) {
            return Reason.GAMBLING
        }
        return null
    }

    /** True for any package OwnFate itself bundles, so a picker can show it as on. */
    fun isBundled(pkg: String): Boolean =
        pkg in GamblingApps.bundled || pkg in PredictionApps.bundled

    /** The pure form of [reason], testable without a Context. */
    internal fun reasonFor(
        pkg: String,
        selfPkg: String,
        blockedApps: Set<String>,
        allowedApps: Set<String>,
        removedBundled: Set<String>,
        gamblingOn: Boolean,
        predictionOn: Boolean
    ): Reason? {
        if (pkg == selfPkg) return null
        if (pkg in allowedApps) return null
        if (pkg in blockedApps) return Reason.CUSTOM
        if (pkg in removedBundled) return null
        if (predictionOn && pkg in PredictionApps.bundled) return Reason.PREDICTION
        if (gamblingOn && pkg in GamblingApps.bundled) return Reason.GAMBLING
        return null
    }
}
