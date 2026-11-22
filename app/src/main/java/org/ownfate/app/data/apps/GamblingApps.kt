package org.ownfate.app.data.apps

import org.ownfate.app.data.Store

import android.content.Context

/**
 * The bundled starter list of gambling apps.
 *
 * This is deliberately a starter set, not a claim to know every betting app in every store: the
 * Custom tab lists every launchable app on the phone, so anything missing can be added, and any
 * entry here that turns out to be a false positive can be switched off without touching code.
 *
 * A package is only ever matched exactly, on the foreground window. OwnFate, launchers, Settings
 * and the system UI are never in the set.
 */
object GamblingApps {

    /**
     * Real-money sportsbooks, casinos, poker rooms, betting exchanges and the larger
     * social-casino apps, matched to their Google Play package names. Event-contract apps such as
     * DraftKings Predictions live in [PredictionApps] instead. Package names are exact: a branded
     * app that changes its package is simply added from the picker.
     */
    val bundled: List<String> = listOf(
        // DraftKings / FanDuel / Fanatics sportsbooks and casinos
        "com.draftkings.sportsbook",
        "com.draftkings.casino",
        "com.draftkings.picksix",
        "com.fanduel.sportsbook",
        "com.fanduel.casino",
        "com.fanduel.racing",
        "com.fanduel.poker",
        "com.betfanatics.sportsbook.android",
        "com.betfanatics.casino",
        // MGM / Borgata / Caesars
        "com.betmgm.eqc.sports",
        "com.betmgm.gilariver.sports",
        "com.playmgm.nj.sports",
        "com.playmgmcasino.nj",
        "com.mgmpoker.poker",
        "com.borgatapoker.casino.wrapper",
        "com.borgata.nj.sports",
        "com.caesarsdigital.igaming.caesarspalaceonline",
        "com.williamhill.us.nj.sports",
        "com.gan.wynn",
        "com.wsoppa.gp",
        // Other US sportsbooks, casinos and betting exchanges
        "com.rush.ri",
        "com.goldennugget.casino",
        "com.pennentertainment.hc",
        "com.parxcasino.pa.app.play.us",
        "com.playstar.casino.prod",
        "com.espn.bet",
        "com.awi.whrp",
        "com.hardrockdigital.client",
        "com.hardrock.casinoandroid",
        "com.ballyscorp.ballyplay",
        "com.smarkets",
        // Picks and daily fantasy contests
        "com.myprizepicks.myprizepicks",
        "app.instabet.betr",
        // bet365
        "com.bet365.bet365CasinoApp.play.NewJersey",
        "com.bet365SportsNJ.Bet365_ApplicationNJ",
        // Poker
        "com.pyrsoftware.pokerstars.net",
        "com.nsus.clubgg",
        // Larger social casinos
        "air.com.playtika.slotomania",
        "com.williamsinteractive.jackpotparty",
        "com.ddi",
        "com.playstudios.myvegas",
        "com.playstudios.popslots"
    ).distinct().sorted()

    /**
     * Packages the guard should bounce right now.
     *
     * The bundled list applies only while the Gambling apps switch is on. A removed bundled entry
     * stays removed. Custom blocked apps are always included: they are the user's own hard list,
     * not a category that can be switched off by accident. An allowed app is then removed from the
     * set: the Allowlist is the user's explicit exception for that package.
     */
    fun blocked(ctx: Context): Set<String> = blockedSet(
        bundledOn = Store.bool(ctx, Store.GAMBLING_APPS, false),
        removedBundled = Store.set(ctx, Store.REMOVED_BUNDLED),
        blockedApps = Store.set(ctx, Store.CUSTOM_APPS),
        allowedApps = Store.set(ctx, Store.ALLOW_APPS),
        selfPkg = ctx.packageName
    )

    /**
     * The pure part of [blocked], so the override rules can be tested without a Context. An
     * allowed app wins over both the bundled list and the user's blocked list.
     */
    internal fun blockedSet(
        bundledOn: Boolean,
        removedBundled: Set<String>,
        blockedApps: Set<String>,
        allowedApps: Set<String>,
        selfPkg: String
    ): Set<String> {
        val out = LinkedHashSet<String>()
        if (bundledOn) {
            out.addAll(bundled)
            out.removeAll(removedBundled)
        }
        out.addAll(blockedApps)
        out.removeAll(allowedApps)
        out.remove(selfPkg)
        return out
    }
}
