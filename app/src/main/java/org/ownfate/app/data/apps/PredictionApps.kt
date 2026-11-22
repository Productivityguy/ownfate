package org.ownfate.app.data.apps


/**
 * Prediction-market apps bundled with OwnFate.
 *
 * The Prediction markets switch covers both the site list and these apps: a market is a market
 * whether it opens in Chrome or in its own launcher icon. The list is a starter set of the larger
 * platforms; anything else can be added from Custom → Apps, and a false positive can be excepted
 * from the same picker.
 *
 * Exact package names only. A changed package is simply added by the user.
 */
object PredictionApps {

    val bundled: List<String> = listOf(
        // Kalshi / Polymarket / Manifold
        "com.kalshi.mobile",
        "com.polymarket.android",
        "com.markets.manifold",
        // US regulated event-contract apps
        "com.prophetxsweepstake",
        "us.novig.app",
        "com.fanatics.markets",
        "com.draftkings.predict",
        "com.fanduel.flywheelnativecontainer.futures",
        // Social and newer event-market apps
        "com.outpollapp",
        "com.flipx.get",
        "com.tradebanta.app",
        "com.luminamarkets.app"
    ).distinct().sorted()
}
