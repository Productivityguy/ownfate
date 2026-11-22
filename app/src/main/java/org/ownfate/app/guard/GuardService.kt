package org.ownfate.app.guard

import org.ownfate.app.data.Catch
import org.ownfate.app.data.HostLists
import org.ownfate.app.data.Lock
import org.ownfate.app.data.Store
import org.ownfate.app.data.apps.AppBlocklist
import org.ownfate.app.filter.Filter
import org.ownfate.app.ui.appLabel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * The watcher.
 *
 * Two paths. Typed text arrives at TYPE_VIEW_TEXT_CHANGED, character by character, which is the
 * cheapest and most precise signal: it is the search you are about to run. The tree walk is driven
 * by events *and* by an always-on poll of the window in front, so nothing waits for an event that
 * a static or unusual screen may never emit. Address bars are always read because that is where a
 * tube site announces itself by name.
 *
 * The signal decides the response. A host on either bundled site list, plus STRONG, HARD and
 * CUSTOM, is stepped back from at once and shown a Close-only card. The site lists are checked
 * against the address bar, never against page text, and offer no appeal. The step back is
 * checked, not fixed: one BACK, a look at the window 180 ms later, and a second BACK only if a
 * blocked screen is still in front. Two BACKs are the whole budget and HOME is never used, so an
 * app that leaves its search in one press is not popped twice. The same check is app-agnostic,
 * so a search in YouTube or Instagram behaves like a browser. SOFT is not stepped back from: the
 * card appears over the page, Close means "that is adult content" and leaves the page, and
 * "appeal" opens a retype gate. Passing the gate releases that screen — not just the words
 * currently on it — for five minutes, in memory only.
 *
 * The four app search add-ons are caught by their screen ids — `find(id) != null` means BACK at
 * once — and land on the app's own feed or chat list. The same block card then covers every catch,
 * search included: it is inert for two seconds so the BACK can land, its scrim swallows every other
 * touch, and it stays until Close (or Appeal on a soft match) is pressed. One catch means one BACK
 * pair and one card; there is no repeat loop and no timer that lets the card vanish on its own.
 *
 * There is no quiet time. The window in front is scanned on a fixed poll for every app, every
 * option is checked on every pass, and events add a same-frame scan on top. Nothing is rested
 * after an action or a card: as soon as a screen is still blockable it is seen and answered again.
 * Only the Log is throttled, so one stuck screen does not fill it with duplicate lines.
 *
 * The off-switch screens get BACK, BACK, HOME: the page is popped off Settings' back stack instead
 * of merely being covered by HOME, so a back-swipe does not return to it. This detection runs only
 * while Protect OwnFate is on; the power-menu check has its own switch. The accessibility detail
 * page is recognised structurally (title + app name + a real toggle), because its switch is often
 * labelled "Use OwnFate" rather than "Use service". Settings search and other apps' device-admin
 * pages are never treated as this app's off switch.
 *
 * The card is a TYPE_ACCESSIBILITY_OVERLAY, which needs no extra permission and, unlike
 * startActivity from a service on Android 14+, is actually allowed. It is focusable, so the gate's
 * field can raise the keyboard and the sentence can be typed in place.
 */
class GuardService : AccessibilityService() {

    private val ui = Handler(Looper.getMainLooper())

    /** Reads window text, guarded so one stale node cannot take the service down. */
    private val reader = PageReader(this)

    /**
     * Last package the poll saw in front. Accessibility events from the keyboard carry the IME's
     * package, not the app being typed into, so the event's own package cannot tell us that the
     * user is typing inside OwnFate. The poll can. Both this field and the event callback run on
     * the main looper, so no synchronization is needed.
     */
    private var foregroundPkg: String? = null

    /** Single owner for every global BACK sequence: page blocks, app cards and off-switch ladders. */
    private val actions = ActionGate()

    /** Leaves the Settings screens that would switch the guard off. */
    private val offSwitch = OffSwitchGuard(this, reader, actions)
    private var overlay: View? = null

    /** The package the visible card was raised for, and whether it is the blocked-app card. */
    private var overlayPkg: String? = null
    private var appOverlay = false

    private var lastScan = 0L
    private var lastGuard = 0L
    private var lastPending = 0L
    private var lastAppBlockAt = 0L
    private var lastAppBlockPkg = ""
    private var lastAppCheck = 0L

    /** The last log line, so a screen that survives two BACKs is not logged every 40 ms. */
    private var lastCatchKey = ""
    private var lastCatchAt = 0L

    /** True while the retype gate is on screen, which changes how a Close has to let go. */
    private var gateOpen = false

    /**
     * The always-on watch.
     *
     * Accessibility events are not guaranteed: a search screen or a static page can open and sit
     * still without emitting anything this service sees. The window in front is therefore read on
     * a fixed interval for *every* app, with every option checked each time — blocked apps, the
     * off-switch screens, the add-on search ids, the address bar and the word lists. Events add a
     * same-frame scan on top, but they are never the only reason a screen gets looked at.
     */
    private val scanPoll = object : Runnable {
        override fun run() {
            runCatching { pollFront() }
            ui.postDelayed(this, SCAN_POLL_MS)
        }
    }

    private fun switches(): Switches = Switches(
        adultSites = Store.bool(this, Store.ADULT_SITES, false),
        adultWords = Store.bool(this, Store.ADULT_WORDS, false),
        imageVideoSearch = Store.bool(this, Store.ADULT_IMAGE_VIDEO_SEARCH, false),
        gamblingSites = Store.bool(this, Store.GAMBLING_SITES, false),
        predictionMarkets = Store.bool(this, Store.PREDICTION_MARKETS, false),
        protect = Store.bool(this, Store.PROTECT_APP, false),
        power = Store.bool(this, Store.BLOCK_POWER, false),
        instagramSearch = Store.bool(this, Store.BLOCK_INSTAGRAM_SEARCH, false),
        xSearch = Store.bool(this, Store.BLOCK_X_SEARCH, false),
        redditSearch = Store.bool(this, Store.BLOCK_REDDIT_SEARCH, false),
        telegramSearch = Store.bool(this, Store.BLOCK_TELEGRAM_SEARCH, false)
    )

    /** Read at runtime so a renamed app is still recognised on the Settings screen. */
    private val appLabel: String by lazy {
        runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
            .getOrDefault("OwnFate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Both assets are parsed once, off the main thread. Until they are ready the keyword
        // engine still runs; only site matching waits.
        HostLists.load(this)
        // Restart in case the service is reconnected: one watch, not a growing pile of them.
        ui.removeCallbacks(scanPoll)
        ui.post(scanPoll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return

        // Never react to ourselves.
        if (pkg == packageName) return

        // While OwnFate is the window in front, the keyboard floods us with text and content
        // events under the IME's package. They are not ours to police, and handling them reads a
        // dozen preferences plus the whole active window tree on the same main thread the user is
        // typing on — that is the brief hitch. A real window transition is still handled below,
        // so an app coming forward is never missed.
        val windowState = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!windowState && foregroundPkg == packageName) return

        val now = System.currentTimeMillis()

        // A pending change is a setting waiting out its delay. Applying it here means it lands
        // even when the OwnFate window is not open; this is the only place with a steady event
        // stream. The preference write is cheap, and the check is throttled.
        if (now - lastPending >= PENDING_CHECK_MS) {
            lastPending = now
            Lock.applyDue(this, now)
        }

        // An excluded app is not monitored at all: no page text, no address bar, no app block.
        // The picker never offers Settings, launchers or OwnFate, so this cannot shield an off
        // switch or the guard itself. It runs before the blocked-app check because an exclusion
        // is the user's explicit "leave this one alone" and outranks every bundled list.
        if (pkg in Store.set(this, Store.ALLOW_APPS)) return

        // A blocked app coming to the front is answered before any scanning: there is nothing to
        // read on a betting app that should not be open at all. A window-state event is answered
        // at once; other events are checked on a throttle, because some launchers hand an app to
        // the foreground without a state change this service ever sees.
        if (windowState || now - lastAppCheck >= APP_CHECK_MS) {
            lastAppCheck = now
            // A background app also emits content events. Only a window-state event is taken as
            // proof that the package just came forward; every other event has to name the window
            // that is actually in front, or a background betting app could raise its card over
            // whatever the user is really doing.
            if ((windowState || reader.activePackage() == pkg) && blockedApp(pkg)) {
                blockApp(pkg, now)
                return
            }
        }

        // The off switch lives in Settings, the launcher and the package installer. This runs
        // before the card check: a stale card must not shield the screen that would remove the app.
        // A card from another app is dismissed first, because a focusable overlay would otherwise
        // be the window we read.
        val sw = switches()
        if (OffSwitch.looksLikeSettings(pkg)) {
            if (overlay != null) hideOverlay()
            if (now - lastGuard >= GUARD_THROTTLE_MS) {
                lastGuard = now
                if (offSwitch.guard(pkg, e.className?.toString(), sw, appLabel)) return
            }
        }

        // Never scan underneath our own card. A BACK already in flight is not a quiet time: the
        // poll keeps looking; it just will not start a second action on top of the first.
        if (overlay != null) return

        val custom = Store.custom(this)
        val allowWords = Store.allowWords(this)

        // App search add-ons answer before the page is read in full. The event's own source can
        // name the screen (`expanded_search_field`, `search_section_global`), and a direct id
        // lookup catches it even when the event says nothing. Either way the BACK is immediate.
        val source = reader.safe { e.source }
        if (catchSearch(pkg, source, now)) return

        // Cheap path: what you actually typed, before the search is submitted. In a known browser
        // every non-password field counts, including page search boxes, forms, comments, notes and
        // private messages: a blocked word typed there is still a blocked word. A pass never
        // covers this — typing a new term is a new intent, not the page you unlocked.
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && UrlBar.isBrowser(pkg) &&
            !e.isPassword && reader.safe { e.source?.isPassword } != true
        ) {
            for (t in e.text) {
                val s = t?.toString() ?: continue
                val site = siteHit(s, sw)
                if (site != null) {
                    block(pkg, Filter.Match(site.first, Filter.Tier.WEBSITE), site.second,
                        s, null, now)
                    return
                }
                val m = Filter.match(s, custom, sw.adultWords, allowWords)
                if (m != null) {
                    block(pkg, m, kindOf(m), s, null, now)
                    return
                }
            }
        }

        // Events can burst on one screen. This floor only collapses those duplicates; the always-on
        // poll is the reason a quiet screen still gets read.
        if (now - lastScan < SCAN_MS) return
        lastScan = now

        val root = reader.safe { rootInActiveWindow } ?: return
        scanWindow(pkg, root, source, custom, allowWords, now)
    }

    /**
     * One pass of the always-on watch. Every option is checked on every pass: an app that should
     * never open, an off switch, an add-on search screen by its id, and the page's own words.
     */
    private fun pollFront() {
        val now = System.currentTimeMillis()
        if (overlay != null) return
        val pkg = reader.activePackage() ?: return
        foregroundPkg = pkg
        if (pkg == packageName) return
        if (pkg in Store.set(this, Store.ALLOW_APPS)) return

        val sw = switches()
        if (blockedApp(pkg)) {
            blockApp(pkg, now)
            return
        }
        if (OffSwitch.looksLikeSettings(pkg) && now - lastGuard >= GUARD_THROTTLE_MS) {
            lastGuard = now
            if (offSwitch.guard(pkg, null, sw, appLabel)) return
        }

        val root = reader.safe { rootInActiveWindow } ?: return
        if (reader.ownWindow(root)) return
        if (reader.safe { root.packageName?.toString() } != pkg) return
        scanWindow(pkg, root, null, Store.custom(this), Store.allowWords(this), now)
    }

    /**
     * Catch an add-on search screen, by its id first.
     *
     * [PageReader.readSearch] walks the window once for the exact id entry — `find(id) != null` is
     * the whole test — so Reddit's bare Compose tag `expanded_search_field` and Telegram's
     * `search_section_global` answer on the first poll that sees them, before a field focuses or a
     * query is typed. The same pass reports a focused field, which is the fallback for Instagram
     * and X. The event's source is checked too, for the frame where it names the screen before the
     * root does.
     *
     * Returns true when the screen was a search screen, whether or not the BACK was in flight; the
     * caller then skips the page scan for that window.
     */
    private fun catchSearch(pkg: String, source: AccessibilityNodeInfo?, now: Long): Boolean {
        val surface = SearchSurface.of(pkg) ?: return false
        if (!switches().search(surface)) return false
        // An action chain is still checking this screen. Walking the tree on every spammed event
        // would starve the main thread and delay the BACKs already queued. The gate covers the
        // off-switch ladder too, so a Settings action cannot collide with an app search here.
        if (actions.current() != null) return true

        val byEvent = source != null &&
            SearchSurface.screenOf(reader.safe { source.viewIdResourceName }) == surface &&
            reader.safe { source.isVisibleToUser } == true
        val shown = reader.safe { source?.text?.toString() }?.takeIf { it.isNotBlank() }

        // One walk answers both questions: the same pass that sees the screen id also reports a
        // focused search field. Walking once per poll matters — two walks every 32 ms kept the
        // main thread busy long enough to delay the BACKs this path sends. The page text is not
        // used for the log line: a catch names the search, not whatever was behind it.
        reader.readSearch(reader.safe { rootInActiveWindow })
        val byId = reader.searchScreen == surface
        if (!byId && !byEvent && !reader.searchActive) return false

        searchCatch(pkg, surface, shown, now)
        return true
    }

    /** Search is silent: a checked BACK pair and no card. */
    private fun searchCatch(
        pkg: String,
        surface: SearchSurface,
        text: String?,
        now: Long
    ) {
        block(
            pkg,
            Filter.Match(surface.label, Filter.Tier.WEBSITE),
            Kind.APP_SEARCH,
            text?.takeIf { it.isNotBlank() } ?: surface.label,
            null, now
        )
    }

    /** The first bundled host list that contains [s], with the switch it came from. */
    private fun siteHit(s: String, sw: Switches): Pair<String, Kind>? {
        if (sw.adultSites) {
            val h = HostLists.adult.match(s) ?: HostLists.adult.matchDescription(s)
            if (h != null) return h to Kind.ADULT_SITE
        }
        if (sw.gamblingSites) {
            val h = HostLists.gambling.match(s) ?: HostLists.gambling.matchDescription(s)
            if (h != null) return h to Kind.GAMBLING_SITE
        }
        if (sw.predictionMarkets) {
            val h = HostLists.predictionMarkets.match(s) ?: HostLists.predictionMarkets.matchDescription(s)
            if (h != null) return h to Kind.PREDICTION_MARKET
        }
        return null
    }

    private fun kindOf(m: Filter.Match): Kind = when (m.tier) {
        Filter.Tier.CUSTOM -> Kind.CUSTOM_WORD
        else -> Kind.ADULT_WORD
    }

    /**
     * True when [pkg] is a blocked app that the guard may answer.
     *
     * Settings, launchers, the system UI and this app are never answered, even if one somehow
     * ends up in the user's list: a blocker that bounces Settings is a blocker that cannot be
     * turned off.
     */
    private fun blockedApp(pkg: String): Boolean = AppBlocklist.reason(this, pkg) != null

    /**
     * Read one window and act on it.
     *
     * Shared by the event path and by [scanPoll]. A page that ignores BACK may not emit an event
     * at all, so the always-on poll is what re-checks it; otherwise "did I actually leave?" would
     * only ever run on a page that happened to be changing.
     */
    private fun scanWindow(
        pkg: String,
        root: AccessibilityNodeInfo,
        source: AccessibilityNodeInfo?,
        custom: List<String>,
        allowWords: List<String>,
        now: Long
    ) {
        // The launcher's event can arrive after its window is already gone and this app's window
        // is in front of us. Guarding on the event package alone is not enough; the window we are
        // about to read has to be someone else's.
        if (reader.ownWindow(root)) return

        // And it has to be the window the event is actually about. A launcher event can point at
        // a stale root from the app you just left; acting on it would step back out of whatever
        // just opened.
        if (reader.safe { root.packageName?.toString() } != pkg) return

        // The add-on search id is the strongest signal: a direct lookup, not a word match. This is
        // also what the always-on poll uses, so a search screen is answered on the first pass that
        // sees the id, whether or not any event arrived.
        if (catchSearch(pkg, source, now)) return

        // Address bar first: a named site is the strongest signal on the screen. The bundled
        // site lists are checked before the keyword engine, so a listed host is a hard block
        // with no appeal. Its text also feeds the pass's place, because a URL is the one thing
        // that stays put while a page scrolls.
        val sw = switches()
        var address: String? = null
        val windowId = reader.safe { root.windowId } ?: 0
        val bar = UrlBar.find(root, source, pkg)
        if (bar != null) {
            val s = UrlBar.read(bar)
            if (!s.isNullOrBlank()) {
                address = s

                // A plain bar is a host; a Compose bar describes the page, so its address is
                // pulled out of the description before the list is consulted.
                val site = siteHit(s, sw)
                if (site != null) {
                    block(pkg, Filter.Match(site.first, Filter.Tier.WEBSITE), site.second,
                        s, null, now)
                    return
                }

                if (sw.imageVideoSearch) {
                    val pattern = SearchPatterns.hit(s)
                    if (pattern != null) {
                        block(pkg, Filter.Match(pattern, Filter.Tier.WEBSITE),
                            Kind.IMAGE_VIDEO_SEARCH, s, null, now)
                        return
                    }
                }

                val m = Filter.match(s, custom, sw.adultWords, allowWords)
                if (m != null) {
                    val kind = kindOf(m)
                    if (m.tier == Filter.Tier.SOFT) {
                        // The pass needs the place; the body is not read here because the card
                        // no longer carries a page key.
                        val place = PageKey.place(address, windowId)
                        if (!PagePass.active(pkg, place, now)) {
                            block(pkg, m, kind, s, place, now)
                            return
                        }
                    } else {
                        block(pkg, m, kind, s, null, now)
                        return
                    }
                }
            }
        }

        val text = reader.readText(root)
        if (text.isEmpty()) return

        val m = Filter.match(text, custom, sw.adultWords, allowWords) ?: return
        val kind = kindOf(m)
        if (m.tier == Filter.Tier.SOFT) {
            // A feed scrolls new words into view constantly. The pass follows the window (or the
            // address), not the words, so scrolling the same screen does not re-ask for an appeal.
            val place = PageKey.place(address, windowId)
            if (PagePass.active(pkg, place, now)) return
            block(pkg, m, kind, reader.around(text, m.term), place, now)
        } else {
            block(pkg, m, kind, reader.around(text, m.term), null, now)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /** Drop anything that outlives the service: a card, the poll and an unfinished step back. */
    private fun teardown() {
        ui.removeCallbacks(scanPoll)
        hideOverlay()
        offSwitch.cancel()
        actions.cancel()
        lastAppBlockPkg = ""
        lastAppCheck = 0L
    }

    // ---- the intervention -------------------------------------------------

    /**
     * One catch, one answer.
     *
     * The step back is checked, not guessed: one BACK is sent at once, the window is looked at
     * again after a short recheck, and a second BACK follows only if a blocked screen is still in
     * front. Two is the whole budget — HOME is never used as a page action, so a clean page is
     * never traded for the launcher. Search uses the shorter recheck because its id is a direct
     * lookup; pages use [ACTION_RECHECK_MS] for the transition.
     *
     * Nothing is suppressed by a cooldown. A screen that survives and is seen again is answered
     * again; the only record throttled is the Log.
     */
    private fun block(
        pkg: String,
        match: Filter.Match,
        kind: Kind,
        text: String,
        passPlace: String?,
        now: Long
    ) {
        // Our own card is in front. A BACK sent now goes to the overlay, not to the page.
        if (overlay != null) return
        // One global action at a time. The gate covers the off-switch ladder as well, so a
        // Settings action cannot stack its BACK/BACK/HOME on top of a page BACK pair.
        if (actions.current() != null) return

        logCatch(pkg, match, kind, text, now)

        if (match.tier == Filter.Tier.SOFT) {
            // SOFT never steps back on its own: the page stays and the card asks first. Hold the
            // gate for the one frame it takes to attach the card so two events cannot queue two.
            val t = actions.begin("card:$pkg")
            actions.post(t) {
                showOverlay(pkg, match, kind, text, passPlace)
                actions.cancel()
            }
            return
        }

        // A search is answered only while the *search screen* is still there. Checking page words
        // here would pop the feed or chat list we just landed on.
        val recheck = if (kind == Kind.APP_SEARCH) SEARCH_RECHECK_MS else ACTION_RECHECK_MS
        val still: () -> Boolean = if (kind == Kind.APP_SEARCH) {
            { stillSearch(pkg) }
        } else {
            { stillBlocked(pkg) }
        }
        val t = actions.begin(pkg)
        stepBack(t, recheck, still, keyboardFirst = kind == Kind.APP_SEARCH) {
            // Every block gets the same card. Search lands on the app's own feed first, then the
            // card covers it: inert for two seconds, then only Close is live, and it stays until
            // that Close is pressed. The card itself, not a repeat loop, is the spam barrier.
            showOverlay(pkg, match, kind, text)
        }
    }

    /**
     * Write one line per catch, throttled.
     *
     * The scan rate is high enough that a screen which survives its BACKs would otherwise fill
     * the Log with the same line several times a second. The first sighting is always written; a
     * repeat of the same term in the same app waits [CATCH_LOG_MS].
     */
    private fun logCatch(pkg: String, match: Filter.Match, kind: Kind, text: String, now: Long) {
        val key = "$pkg|${match.term}"
        if (key == lastCatchKey && now - lastCatchAt < CATCH_LOG_MS) return
        lastCatchKey = key
        lastCatchAt = now
        Store.addCatch(this, Catch(logTerm(match, kind), pkg, text.take(160), now))
    }

    /** The log line's title: the exact term, with the category when it is not an adult keyword. */
    private fun logTerm(match: Filter.Match, kind: Kind): String = when (kind) {
        Kind.ADULT_SITE -> "Adult site · ${match.term}"
        Kind.GAMBLING_SITE -> "Gambling site · ${match.term}"
        Kind.PREDICTION_MARKET -> "Prediction market · ${match.term}"
        Kind.IMAGE_VIDEO_SEARCH -> "Image & video search · ${match.term}"
        Kind.APP_SEARCH -> "App search · ${match.term}"
        Kind.CUSTOM_WORD -> "Custom word · ${match.term}"
        else -> match.term
    }

    /**
     * Send a BACK, look again, and send a second BACK only if a block is still in front.
     *
     * The look is the point. Telegram leaves its search in one press; Reddit needs one
     * with the keyboard down and two with it up. Sending two unconditionally popped an extra
     * screen in the first case, which is how a guard ends up in the launcher. The re-check makes
     * the count follow the app instead of a fixed guess.
     *
     * [keyboardFirst] spends the first press on the IME when it is up, then uses the one press that
     * is left on the app. The in-flight guard is held until the pair has landed and [then] has
     * attached the card. Once the card is up the poll stops scanning underneath it, so nothing can
     * start a second pair in the animation gap. One catch therefore means one pair and one card.
     */
    private fun stepBack(
        t: Long,
        recheck: Long,
        still: () -> Boolean,
        keyboardFirst: Boolean = false,
        then: () -> Unit
    ) {
        if (keyboardFirst && keyboardUp()) {
            // The keyboard owns the first press. Sending the app BACK before the IME has gone
            // feeds a second press to the keyboard instead, and the search screen stays open.
            // Spend one press on the IME, wait for its window to disappear, then spend the one
            // press that is left on the app. If the search went with the keyboard, stop here.
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                actions.post(t, CLOSE_RETRY_MS) { performGlobalAction(GLOBAL_ACTION_BACK) }
            }
            awaitKeyboardGone(t, 0) {
                if (runCatching { still() }.getOrDefault(false)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    actions.post(t, ACTION_CARD_MS) { finishStepBack(t, then) }
                } else {
                    finishStepBack(t, then)
                }
            }
            return
        }
        val sent = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!sent) {
            // The system declined the first press. Retrying it is the same press, not a second.
            actions.post(t, CLOSE_RETRY_MS) { performGlobalAction(GLOBAL_ACTION_BACK) }
        }
        // Look only after the press has actually been dispatched, including the retry.
        val wait = if (sent) recheck else CLOSE_RETRY_MS + recheck
        actions.post(t, wait) { stepBackAgain(t, still, sent = 1, then) }
    }

    private fun stepBackAgain(
        t: Long,
        still: () -> Boolean,
        sent: Int,
        then: () -> Unit
    ) {
        if (sent >= ACTION_BACK_LIMIT || !runCatching { still() }.getOrDefault(false)) {
            finishStepBack(t, then)
            return
        }
        // Still there: one more BACK, then stop. The pair is the entire budget.
        performGlobalAction(GLOBAL_ACTION_BACK)
        actions.post(t, ACTION_CARD_MS) { finishStepBack(t, then) }
    }

    private fun finishStepBack(t: Long, then: () -> Unit) {
        if (!actions.owns(t)) return
        actions.cancel()
        then()
    }

    /**
     * Ask whether the add-on search screen is still in front, and nothing else.
     *
     * The id is checked first, for the same reason detection uses it: it is the earliest and
     * cheapest signal. The field check covers Instagram and X, which have no screen id. Page words
     * are deliberately not consulted — landing on the app's own feed must not cause another BACK.
     */
    private fun stillSearch(pkg: String): Boolean {
        val surface = SearchSurface.of(pkg) ?: return false
        if (!switches().search(surface)) return false
        val root = reader.safe { rootInActiveWindow } ?: return false
        if (reader.ownWindow(root)) return false
        if (reader.safe { root.packageName?.toString() } != pkg) return false
        reader.readSearch(root)
        if (reader.searchActive || reader.searchScreen == surface) return true
        // Instagram and X have no screen id. Losing keyboard focus can make the field look idle
        // while its search screen is still open, so presence is the recheck for those apps.
        return surface.screenIds.isEmpty() && reader.searchFieldPresent
    }

    /**
     * Ask the window whether something blockable is still in front.
     *
     * Deliberately broad: after a BACK, any blockable screen counts, because the second BACK
     * exists to leave *a* blocked screen, not necessarily the same one. Search surfaces are
     * checked by their ids and field; pages by the address bar and the word lists.
     */
    private fun stillBlocked(pkg: String): Boolean {
        val root = reader.safe { rootInActiveWindow } ?: return false
        if (reader.ownWindow(root)) return false
        if (reader.safe { root.packageName?.toString() } != pkg) return false

        val sw = switches()
        val surface = SearchSurface.of(pkg)
        if (surface != null && sw.search(surface)) {
            reader.readSearch(root)
            if (reader.searchActive || reader.searchScreen == surface) return true
        }

        val custom = Store.custom(this)
        val allow = Store.allowWords(this)
        val bar = UrlBar.find(root, null, pkg)
        if (bar != null) {
            val s = UrlBar.read(bar)
            if (!s.isNullOrBlank()) {
                if (siteHit(s, sw) != null) return true
                if (Filter.match(s, custom, sw.adultWords, allow) != null) return true
            }
        }

        val text = reader.readText(root)
        return text.isNotEmpty() && Filter.match(text, custom, sw.adultWords, allow) != null
    }

    /**
     * Send a BACK, and when the keyboard is up send another once the IME window has gone.
     *
     * Used by a SOFT Close, where the page stayed under the card. The IME is a window of type
     * [AccessibilityWindowInfo.TYPE_INPUT_METHOD] in every app, not just a browser, so checking
     * at the BACK keeps the sequence app-agnostic. The first BACK only dismisses the keyboard;
     * the second is the page BACK, and the pair is all a Close sends.
     *
     * [onPageBack] runs after the page BACK has been dispatched.
     */
    private fun backOut(t: Long, onPageBack: (() -> Unit)? = null) {
        if (!keyboardUp()) {
            pageBack(t) { onPageBack?.invoke() }
            return
        }
        // The first BACK only dismisses the keyboard.
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
            actions.post(t, CLOSE_RETRY_MS) { performGlobalAction(GLOBAL_ACTION_BACK) }
        }
        awaitKeyboardGone(t, 0) {
            pageBack(t) { onPageBack?.invoke() }
        }
    }

    /**
     * One page BACK, with a single retry for a BACK that a closing window swallowed.
     *
     * [then] releases the input gate, so it runs only after the press has had time to land.
     * Releasing it at dispatch time would let the poll start a second ladder against a window
     * that is still closing.
     */
    private fun pageBack(t: Long, then: () -> Unit) {
        if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            actions.post(t, ACTION_CARD_MS, then)
            return
        }
        actions.post(t, CLOSE_RETRY_MS) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            actions.post(t, ACTION_CARD_MS, then)
        }
    }

    private fun awaitKeyboardGone(t: Long, step: Int, then: () -> Unit) {
        if (!keyboardUp() || step >= IME_WAIT_STEPS) {
            then()
            return
        }
        actions.post(t, IME_POLL_MS) { awaitKeyboardGone(t, step + 1, then) }
    }

    /** True while an on-screen keyboard is a visible window. No extra permission is involved. */
    private fun keyboardUp(): Boolean = runCatching {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }.getOrDefault(false)

    /**
     * A blocked app has come to the front.
     *
     * The card is a full-screen accessibility overlay rather than a launched activity: starting an
     * activity from the background is restricted on current Android, while an overlay is the
     * documented capability of an accessibility service. The blocked app is still running behind
     * it, so Close goes HOME rather than simply lifting the card.
     *
     * A package that is still trying to come back within the cooldown is sent HOME directly, with
     * no second card, so a relaunch loop cannot flash the card forever.
     */
    private fun blockApp(pkg: String, now: Long) {
        if (overlay != null) {
            // The same app is already covered. A card for anything else is replaced: the blocked
            // app must not stay usable behind an overlay that belongs to a page the user left.
            if (appOverlay && overlayPkg == pkg) return
            hideOverlay()
        }
        // A blocked app outranks any ladder already in flight. Cancel it before the card goes up,
        // or a queued BACK lands on the overlay instead of the page/app it was meant for, and the
        // off-switch guard's independent busy window must not keep a second ladder alive.
        offSwitch.cancel()
        actions.cancel()
        if (pkg == lastAppBlockPkg && now - lastAppBlockAt < APP_BLOCK_COOLDOWN_MS) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        lastAppBlockPkg = pkg
        lastAppBlockAt = now

        val reason = AppBlocklist.reason(this, pkg) ?: return
        val label = appLabel(packageManager, pkg)
        Store.addCatch(this, Catch(reason.title, pkg, label, now))
        showAppOverlay(pkg, label, reason)
    }

    private fun showAppOverlay(pkg: String, label: String, reason: AppBlocklist.Reason) {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )

        val view = newCard().app(reason, label) { closeAppBlock() }
        runCatching { wm.addView(view, lp) }.onSuccess {
            overlay = view
            overlayPkg = pkg
            appOverlay = true
            gateOpen = false
        }
    }

    /** HOME leaves the blocked app, not just the card. */
    private fun closeAppBlock() {
        hideOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * The real launcher icon.
     *
     * The old card used a bundled 512px mark, which is not the icon on the home screen; the card
     * now looks like the app the user just tapped. Loaded from the package manager rather than a
     * resource so an adaptive icon is resolved the same way the launcher resolves it.
     */
    private fun appIcon(): android.graphics.drawable.Drawable? = runCatching {
        packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /** Wires the card's buttons back to this service. The card itself keeps no state. */
    private fun newCard(): BlockCard = BlockCard(
        this,
        appIcon(),
        onCloseSoft = { closeSoft() },
        onCloseHard = { closeHard() },
        onGate = { gateOpen = it },
        onAppeal = { pkg, passPlace, typed ->
            PagePass.grant(pkg, passPlace)
            Store.addCatch(this, Catch("appeal", pkg, typed, System.currentTimeMillis()))
            hideOverlay()
            // The always-on poll picks the page straight back up. A placeless pass is still waiting
            // for its place (the search has not been submitted), so nothing is matched yet.
        }
    )

    private fun showOverlay(
        pkg: String,
        match: Filter.Match,
        kind: Kind,
        text: String,
        passPlace: String? = null
    ) {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java) ?: return

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val view = newCard().page(pkg, match, kind, text, passPlace)
        runCatching { wm.addView(view, lp) }.onSuccess {
            overlay = view
            overlayPkg = pkg
            appOverlay = false
            gateOpen = false
            // No auto-hide: the card is inert for two seconds, then lives until Close or Appeal.
        }
    }

    // ---- closing ----------------------------------------------------------

    /**
     * SOFT's "yes, it is": take the card down, let focus return, then leave the page.
     *
     * The order matters. The overlay is focusable, so a BACK sent in the same frame as its
     * removal goes to a window that no longer exists — the bug that made Close look inert. The
     * keyboard, when the gate was open, needs a little longer still.
     */
    private fun closeSoft() {
        val gate = gateOpen
        val pkg = overlayPkg.orEmpty()
        hideOverlay()
        // The close is an action in flight, not a quiet time: the poll keeps scanning, but the
        // page cannot start a new BACK pair while this one is being sent.
        val t = actions.begin("close:$pkg")

        val wait = if (gate) CLOSE_SETTLE_GATE_MS else CLOSE_SETTLE_MS
        actions.post(t, wait) {
            backOut(t) {
                actions.cancel()
            }
        }
    }

    /**
     * HARD/STRONG/WEBSITE Close. The step back was already sent before the card appeared, so
     * pressing Close only dismisses the card. The always-on poll sees the page again on its next
     * pass; if it is still blocked, the ordinary two-BACK step back runs again.
     */
    private fun closeHard() {
        hideOverlay()
    }

    private fun hideOverlay() {
        gateOpen = false
        overlayPkg = null
        appOverlay = false
        val v = overlay ?: return
        overlay = null
        runCatching {
            (getSystemService(InputMethodManager::class.java))
                ?.hideSoftInputFromWindow(v.windowToken, 0)
        }
        runCatching {
            (getSystemService(WindowManager::class.java))?.removeView(v)
        }
    }

    companion object {
        /**
         * The always-on poll. Every app, every option, all the time; no quiet window after an
         * action or a card. Roughly two polls per frame is fast enough to catch a search screen as
         * it opens without making the tree walk the only signal.
         */
        private const val SCAN_POLL_MS = 32L
        /** Event burst floor: collapses duplicate scans of one frame, never blocks the poll. */
        private const val SCAN_MS = 16L

        /**
         * Time allowed for a BACK to land before the window is read again. The re-check is what
         * decides whether a second BACK is needed; too short and an app that is merely animating
         * is mistaken for one that stayed.
         */
        private const val ACTION_RECHECK_MS = 180L
        /** An add-on search id is a direct lookup, so its transition needs less time to land. */
        private const val SEARCH_RECHECK_MS = 100L
        /** The most BACKs one catch may send. There is no escalation past this. */
        private const val ACTION_BACK_LIMIT = 2
        /** Let a just-dispatched second BACK land before a focusable card is attached. */
        private const val ACTION_CARD_MS = 90L
        /** The same term in the same app is written to the Log at most this often. */
        private const val CATCH_LOG_MS = 1_500L

        /** React to a Settings off-switch screen quickly: it is one tap from turning the guard off. */
        private const val GUARD_THROTTLE_MS = 120L
        /** Time for the removed overlay to lose focus before BACK is sent. */
        private const val CLOSE_SETTLE_MS = 220L
        /** Longer when a keyboard has to come down first. */
        private const val CLOSE_SETTLE_GATE_MS = 350L
        private const val CLOSE_RETRY_MS = 300L

        /** Poll the IME window at this rate, for at most [IME_WAIT_STEPS] checks. */
        private const val IME_POLL_MS = 60L
        private const val IME_WAIT_STEPS = 8

        /** How often the service writes any pending change whose delay has elapsed. */
        private const val PENDING_CHECK_MS = 15_000L

        /** How often a non-window event checks whether a blocked app is in front. */
        private const val APP_CHECK_MS = 500L
        /** A blocked app relaunching inside this window is sent Home without a second card. */
        private const val APP_BLOCK_COOLDOWN_MS = 8_000L
    }
}
