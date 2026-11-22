package org.ownfate.app

import org.ownfate.app.data.HostLists
import org.ownfate.app.data.Lock
import org.ownfate.app.data.Store
import org.ownfate.app.guard.ScreenSwitch
import org.ownfate.app.ui.BottomNavBar
import org.ownfate.app.ui.Brand
import org.ownfate.app.ui.dismissHomeMenu
import org.ownfate.app.ui.backgroundRes
import org.ownfate.app.ui.CustomScreen
import org.ownfate.app.ui.GuardScreen
import org.ownfate.app.ui.LogScreen
import org.ownfate.app.ui.ProtectionScreen
import org.ownfate.app.ui.Screen
import org.ownfate.app.ui.lineField
import org.ownfate.app.ui.primaryButton
import org.ownfate.app.ui.quietButton

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

/**
 * The only activity.
 *
 * The bottom tabs do not open windows: one activity, one container, each tab kept alive, and a
 * short fade-and-slide while the old screen is swapped for the new one. OwnFate does the same in
 * plain views, which keeps the no-AndroidX rule. The background art, the status bars and the
 * bottom tray never move; only the content host fades and slides.
 *
 * Each screen is built once and hidden, never rebuilt for existing data except by its own
 * refresh(). Home, Custom and Protection are rebuilt while the content is invisible, so their data
 * is current without a visible reload.
 */
class MainActivity : Activity() {

    private lateinit var contentHost: FrameLayout
    private lateinit var trayHost: FrameLayout
    internal lateinit var root: FrameLayout
    private lateinit var backgroundView: ImageView
    private var navBar: BottomNavBar? = null
    internal var homeMenu: FrameLayout? = null
    private var popup: FrameLayout? = null

    private val screens = mutableMapOf<String, Screen>()
    private var current = "home"
    private var switching = false
    private var firstResume = true

    private val ui = Handler(Looper.getMainLooper())

    /** Applies a change whose delay ran out while this activity is the only thing in front. */
    private val pendingTick = object : Runnable {
        override fun run() {
            if (Lock.applyDue(this@MainActivity).isNotEmpty()) refreshCurrentScreen()
            ui.postDelayed(this, PENDING_TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Brand.applyTheme(this)
        systemBars()

        val root = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Brand.PAGE)
        }
        this.root = root

        // One background for the whole app, not one per screen. The art is chosen in Settings;
        // an ImageView keeps the cost off the text layout. Width-fit scaling keeps every art
        // aligned to the screen width without the side cropping that CENTER_CROP can cause.
        backgroundView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.MATRIX
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBackgroundMatrix() }
        }
        root.addView(backgroundView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        contentHost = FrameLayout(this)
        root.addView(contentHost, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Warm both bundled lists as soon as the app is opened, so the guard's first browser
        // window does not race the parse. The service calls the same loader; it runs once per
        // process.
        HostLists.load(this)
        // Parse the bundled fonts off the main thread so opening any screen (or the word
        // editor) never stutters while a 325 KB TTF is parsed on the UI thread.
        Brand.preloadFonts(this)

        trayHost = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        }
        root.addView(trayHost)

        setContentView(root)
        applyBackground()
        install("home")
    }

    override fun onResume() {
        super.onResume()
        // A pending change is written by the service too, but the service may not be bound right
        // now. Applying due changes here keeps the screen honest even while the guard is off.
        val applied = Lock.applyDue(this)
        // The first resume follows onCreate, which already built Home. If a due change landed
        // between that build and now, the screen is stale and has to be rebuilt before it is seen.
        if (firstResume) {
            firstResume = false
            if (applied.isNotEmpty()) refreshCurrentScreen()
        } else {
            refreshCurrentScreen()
        }
        // Stay honest while OwnFate itself is in front. The service's event stream does not run
        // for a window it owns, so a delay that runs out here would otherwise show stale switches
        // until the next tab switch.
        ui.removeCallbacks(pendingTick)
        ui.postDelayed(pendingTick, PENDING_TICK_MS)
    }

    override fun onPause() {
        ui.removeCallbacks(pendingTick)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (popup != null) {
            dismissPopup()
            return
        }
        if (homeMenu != null) {
            dismissHomeMenu()
            return
        }
        if (current != "home") show("home") else super.onBackPressed()
    }

    /** Switch tabs with the shared transition: fade out, swap, fade and rise in. */
    fun show(tab: String, animate: Boolean = true) {
        if (tab == current && screens.containsKey(tab)) return
        // A tap during the short transition is ignored rather than swapping a half-faded frame.
        if (switching) return
        // A two-tap confirmation belongs to the tab that asked for it. Leaving disarms it before
        // the new screen is built.
        screens[current]?.onHide()
        if (!animate || screens[current]?.view?.parent == null) {
            ScreenSwitch.reset(contentHost)
            install(tab)
            return
        }

        // The tray answers the tap immediately; the transition follows.
        rebuildTray(tab)
        switching = true
        ScreenSwitch.out(contentHost) {
            install(tab)
            ScreenSwitch.inView(contentHost) { switching = false }
        }
    }

    /**
     * Put [tab] on screen. The target is refreshed here, while the content host is still at alpha
     * zero, so a rebuild cannot show as a flash. Calling this during the first show builds the
     * screen; its constructor has already filled it.
     */
    private fun install(tab: String) {
        val target = screen(tab)
        val existed = target.view.parent != null

        if (!existed) {
            contentHost.addView(
                target.view,
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
        }
        for ((id, other) in screens) {
            if (id != tab) other.view.visibility = View.GONE
        }

        current = tab
        if (existed) target.refresh()
        target.view.visibility = View.VISIBLE
        rebuildTray()
    }

    private fun screen(tab: String): Screen = screens.getOrPut(tab) {
        when (tab) {
            "custom" -> CustomScreen(this)
            "protection" -> ProtectionScreen(this)
            "log" -> LogScreen(this)
            else -> GuardScreen(this)
        }
    }

    /** Rebuild the visible screen in place, if it has data to refresh. */
    fun refreshCurrentScreen() {
        screens[current]?.refresh()
    }

    private fun rebuildTray(selected: String = current) {
        val bar = navBar
        if (bar == null) {
            navBar = BottomNavBar(this, selected) { tab -> show(tab) }
            trayHost.addView(navBar)
        } else {
            bar.select(selected)
        }
    }

    // ---- home settings menu ----------------------------------------------

    internal fun applyBackground() {
        val id = Store.str(this, Store.BACKGROUND, Store.BG_GRID)
        val res = backgroundRes(id)
        if (res == 0) {
            backgroundView.visibility = View.GONE
            backgroundView.setImageDrawable(null)
            backgroundView.imageMatrix = android.graphics.Matrix()
        } else {
            backgroundView.visibility = View.VISIBLE
            backgroundView.setImageResource(res)
            // MULTIPLY keeps the art's black ground black and tints only the bright glyphs.
            backgroundView.setColorFilter(
                Store.int(this, Store.ART_COLOR, Store.ART_COLOR_DEFAULT),
                android.graphics.PorterDuff.Mode.MULTIPLY
            )
            updateBackgroundMatrix()
        }
    }

    /**
     * Scales the background so its width matches the screen and centers it vertically. This keeps
     * the full art width visible on every device, unlike CENTER_CROP which can crop the sides when
     * the image aspect ratio differs from the screen.
     */
    private fun updateBackgroundMatrix() {
        val d = backgroundView.drawable ?: return
        val vw = backgroundView.width
        val vh = backgroundView.height
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) return
        val scale = vw.toFloat() / dw.toFloat()
        val dy = (vh - dh * scale) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(0f, dy)
        }
        backgroundView.imageMatrix = matrix
    }

    /** Rebuild the palette and every live view after a theme or text-colour change. */
    internal fun applyThemeChange() {
        Brand.applyTheme(this)
        systemBars()
        root.setBackgroundColor(Brand.PAGE)
        applyBackground()

        val tab = current.ifEmpty { "home" }
        contentHost.removeAllViews()
        screens.clear()
        navBar = null
        trayHost.removeAllViews()
        current = ""
        install(tab)
    }

    /**
     * The braille art bundled in the APK. The source artwork is kept outside the source release;
     * these PNGs are pre-rendered so the app does not lay out a 74-line text block behind
     * every screen.
     */
    private companion object {
        /** How often a due pending change is checked while this screen is in front. */
        const val PENDING_TICK_MS = 15_000L

    }
    /**
     * A dimmed overlay card, like the Home settings menu. [build] returns the card; call dismiss
     * to close it. Only one popup at a time.
     */
    fun showPopup(build: (dismiss: () -> Unit) -> View) {
        if (popup != null || homeMenu != null) return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Brand.SCRIM)
            isClickable = true
            setOnClickListener { dismissPopup() }
        }
        popup = overlay
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(150L).start()

        // Tie the closure to this overlay. A change made from inside a popup can raise the
        // password prompt, which replaces [popup]; the old popup's trailing dismiss() must not
        // then close the prompt that was just opened.
        val dismiss = { if (popup === overlay) dismissPopup() }
        val card = build(dismiss)
        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = Brand.dp(this@MainActivity, 20f)
            marginEnd = Brand.dp(this@MainActivity, 20f)
            topMargin = Brand.dp(this@MainActivity, 24f)
            bottomMargin = Brand.dp(this@MainActivity, 24f)
        })
        card.alpha = 0f
        card.translationY = Brand.dp(this, 10f).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(180L).start()
    }

    private fun dismissPopup() {
        val overlay = popup ?: return
        popup = null
        overlay.animate().alpha(0f).setDuration(140L)
            .withEndAction { root.removeView(overlay) }
            .start()
    }


    /**
     * Change one protected setting through [Lock].
     *
     * A weaker change with a password configured asks for it first, so a stricter change is never
     * interrupted by a dialog. Everything else is decided by the lock: applied at once, scheduled
     * to wait, or refused.
     */
    fun change(feature: Lock.Feature, value: String) = changeAll(listOf(feature to value))

    /**
     * Apply several protected settings with at most one password prompt. Custom's Save can remove
     * a word and an app together; asking twice would stack two dialogs.
     */
    fun changeAll(changes: List<Pair<Lock.Feature, String>>) {
        if (changes.isEmpty()) return
        val protected = changes.filter { Lock.needsAuth(this, it.first, it.second) }
        if (protected.isNotEmpty() && Lock.hasPassword(this)) {
            val message = if (protected.size == 1) {
                "This change is weaker than the current setting. Enter the password to apply it now."
            } else {
                "${protected.size} changes are weaker than the current settings. Enter the password " +
                    "to apply them now."
            }
            if (Lock.mode(this).hasDelay) {
                promptPassword(
                    "Password required",
                    message,
                    neutralLabel = "Let them wait",
                    onNeutral = { submitChanges(changes, null) }
                ) { password -> submitChanges(changes, password) }
            } else {
                promptPassword("Password required", message) { password ->
                    submitChanges(changes, password)
                }
            }
        } else {
            submitChanges(changes, null)
        }
    }

    private fun submitChanges(changes: List<Pair<Lock.Feature, String>>, password: String?) {
        // Check the password once, before any feature is written. Verifying inside the loop would
        // let a stricter member of a mixed batch land before the wrong password was discovered,
        // and the toast would still say "nothing changed".
        if (password != null && changes.any { Lock.needsAuth(this, it.first, it.second) } &&
            !Lock.verify(this, password)
        ) {
            toast("Wrong password — nothing changed")
            return
        }
        var needsPassword = false
        var wrong = false
        var pending = false
        changes.forEach { (feature, value) ->
            when (Lock.request(this, feature, value, password)) {
                Lock.Outcome.APPLIED -> Unit
                Lock.Outcome.PENDING -> pending = true
                Lock.Outcome.NEEDS_PASSWORD -> needsPassword = true
                Lock.Outcome.WRONG_PASSWORD -> wrong = true
                Lock.Outcome.UNCHANGED -> Unit
            }
        }
        refreshCurrentScreen()
        when {
            wrong -> toast("Wrong password — nothing changed")
            needsPassword -> {
                if (Lock.hasPassword(this)) {
                    promptPassword(
                        "Password required",
                        "Enter the password to apply this change."
                    ) { submitChanges(changes, it) }
                } else {
                    toast("Set a password in Protection first")
                }
            }
            pending -> {
                val left = changes.asSequence()
                    .mapNotNull { Lock.pendingFor(this, it.first)?.remainingLabel() }
                    .firstOrNull()
                    .orEmpty()
                toast(if (left.isEmpty()) "Change scheduled" else "Change scheduled · $left")
            }
            // An applied change is its own feedback: the switch or field has already moved.
            // No "Saved" toast — the user dislikes the message popping up under every toggle.
            else -> Unit
        }
    }

    /** A one-field platform dialog. The keyboard must not autofill or suggest on a password. */
    fun promptPassword(
        title: String,
        message: String,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        onOk: (String) -> Unit
    ) {
        // A password prompt can be raised from inside an editor popup. Only one overlay is
        // allowed at a time, so close the editor first instead of silently skipping the prompt.
        if (popup != null) dismissPopup()
        if (homeMenu != null) dismissHomeMenu()
        showPopup { dismiss ->
            Brand.col(this).apply {
                background = Brand.popupSurface(this@MainActivity)
                setPadding(Brand.dp(this@MainActivity, 20f), Brand.dp(this@MainActivity, 20f),
                    Brand.dp(this@MainActivity, 20f), Brand.dp(this@MainActivity, 16f))
                isClickable = true
                addView(Brand.title(this@MainActivity, title, 20f))
                addView(Brand.gap(this@MainActivity, Brand.S2))
                addView(Brand.body(this@MainActivity, message, Brand.T_BODY, Brand.INK_45))
                addView(Brand.gap(this@MainActivity, Brand.S3))
                val input = this@MainActivity.lineField("Password").apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                addView(input)
                addView(Brand.gap(this@MainActivity, Brand.S4))
                addView(this@MainActivity.primaryButton("OK") {
                    val v = input.text?.toString().orEmpty()
                    dismiss(); onOk(v)
                })
                if (neutralLabel != null && onNeutral != null) {
                    addView(Brand.gap(this@MainActivity, Brand.S2))
                    addView(this@MainActivity.quietButton(neutralLabel) { dismiss(); onNeutral() })
                }
                addView(Brand.gap(this@MainActivity, Brand.S2))
                addView(this@MainActivity.quietButton("Cancel") { dismiss() })
            }
        }
    }

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun systemBars() {
        window.statusBarColor = Brand.PAGE
        window.navigationBarColor = Brand.PAGE
        // Dark theme wants light bar icons; Light theme wants dark ones. Set the flags from the
        // palette each time, so a live theme change cannot leave a stale value behind.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (Brand.isDark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    fun openAccessibility() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
    }

    /**
     * The system's own activation dialog. The explanation is deliberately plain: this is a speed
     * bump, not a lock, and the user should know that before granting anything.
     */
    fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this, AdminReceiver::class.java)
            )
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "OwnFate uses device admin as a deactivation step before uninstall. It cannot " +
                    "stop a force-stop, safe mode or ADB."
            )
        runCatching { startActivity(intent) }
            .onFailure { openSecuritySettings() }
    }

    /** There is no public intent for one admin's detail page, so open Security. */
    fun openSecuritySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}

/** True when this app's accessibility service is actually enabled in system settings. */
fun isServiceOn(ctx: Context): Boolean {
    val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
}

/** True when OwnFate's optional device admin is active. */
fun isDeviceAdmin(ctx: Context): Boolean {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        ?: return false
    return dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
}
