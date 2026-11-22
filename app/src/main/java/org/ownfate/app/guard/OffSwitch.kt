package org.ownfate.app.guard


/**
 * The only thing standing between the user and the off switch.
 *
 * A device admin can add a deactivation step before uninstall, but it is not a lock either.
 * What this does is notice the screen that offers the off switch and leave it. Detection is
 * deliberately narrow, so ordinary Settings stays usable:
 *
 *   - uninstall, force-stop and clear-data screens must name this app near the top and use
 *     Settings' own destructive wording;
 *   - the device-admin deactivation screen is recognised by its activity class
 *     (`DeviceAdminAdd`) plus this app's name as the page heading and a deactivation action. Every
 *     app's admin uses the same activity, so the class alone is never enough;
 *   - the accessibility detail page is recognised by this app's own service description, its
 *     "<app> shortcut" row, or a toggle on a page titled with the app label;
 *   - the power menu is recognised by its actual actions, not by a "Power menu" title.
 *
 * Settings search results are never an off switch: they name many apps at once and must stay
 * usable. The same is true of the main Accessibility list, the list of device admins, other apps'
 * admin pages and the general accessibility-shortcut pages. It is scoped to the shells where those
 * screens actually live (Settings, the launcher's uninstall dialog, the package installer, the
 * system power menu). This is friction, not a lock: the firmware's own safe-mode boot and ADB still
 * win.
 */
object OffSwitch {

    /**
     * Words that only appear where this app can actually be removed or switched off. Deliberately
     * not "Accessibility" or "Device admin": those LIVE on index pages that have nothing to do
     * with disabling anything. "Use service" is how Samsung labels the accessibility toggle.
     */
    private val DANGER = listOf(
        "Uninstall", "Force stop", "Deactivate", "Clear data", "Clear storage", "turn off",
        "Turn off this service", "Stop service", "Use service"
    )

    /**
     * Pages that must always stay reachable. If any of these appear, the screen is a normal
     * Settings page and is left alone no matter what else it mentions.
     */
    private val EXEMPT = listOf(
        "Screen lock", "Biometrics", "Face recognition", "Fingerprint",
        "Find My Mobile", "Security update", "Google Play Protect", "Secure Wi-Fi",
        "Permission manager", "Privacy", "Auto Blocker", "Install unknown apps",
        "Encrypt", "Credential", "Backup", "Restore", "Accounts and backup",
        "Smart Switch", "Samsung Cloud", "Developer options", "USB debugging"
    )

    /**
     * Packages where the guard is allowed to look. Mostly Settings, the launcher (long-press
     * uninstall dialog) and the package installer (the uninstall confirmation). SystemUI covers
     * the power menu and the few OEM skins that route the confirmation through it; the power-menu
     * packages are listed separately because their names vary. `android` is the package some
     * builds use for the global-actions dialog.
     */
    private val SHELLS = listOf(
        "settings", "launcher", "packageinstaller", "permissioncontroller", "systemui",
        "com.miui.home", "com.huawei.android.launcher", "com.oplus.launcher","appmanager",
        "powerui", "powermenu", "power.menu", "power_menu", "globalactions"
    )

    /**
     * Titles of Settings' device-admin pages: the index that lists every admin and the single
     * app's detail page. The index stays reachable even when it names OwnFate: switching another
     * app's admin off has nothing to do with this one. OwnFate's own deactivation page is caught
     * by [isDeviceAdminScreen], which checks the activity and the heading, not this generic word
     * match.
     */
    private val DEVICE_ADMIN_TITLES = listOf(
        "Device admin apps", "Device administrators", "Device administrator",
        "Device admins", "Device admin app", "Device admin"
    )

    /** Titles of a single app's admin detail page, used when the event has no activity class. */
    private val DEVICE_ADMIN_DETAIL_TITLES = listOf(
        "Device admin app", "Device admin", "Device administrator"
    )

    /** The full deactivation wording, not the bare toggle label used in the admin index. */
    private val DEVICE_ADMIN_ACTIONS = listOf(
        "Deactivate this device admin app",
        "Deactivate this device administrator",
        "Remove this device admin"
    )

    /**
     * Markers of Settings' own search page. A result list can show this app's name next to an
     * "Uninstall" or "Deactivate" row, but it is not the screen that performs the action. Blocking
     * search made the whole of Settings feel broken, so it is always left alone.
     */
    private val SEARCH_PAGES = listOf(
        "Search results", "Search settings", "Recent searches", "No results found"
    )

    /**
     * Entries that identify the system power menu. A "Safe mode" row in that menu is the only
     * safe-mode route an accessibility service can ever see: the service does not run once the
     * phone has actually booted into safe mode. It is a speed bump, not a safe-mode lock.
     */
    private val POWER_MENU = listOf(
        "Power off", "Restart", "Reboot", "Emergency", "Lockdown"
    )

    fun looksLikeSettings(pkg: String?): Boolean {
        val p = pkg?.lowercase() ?: return false
        if (p.isEmpty()) return false
        if (p == "android") return true
        return SHELLS.any { p.contains(it) } || p.endsWith(".home")
    }

    /** True when [text] is a real off-switch for [label], not just a Settings page mentioning it. */
    fun isOffSwitch(text: String?, label: String): Boolean {
        if (text.isNullOrBlank() || label.length < 3) return false
        if (looksLikeSearchResults(text)) return false
        // Adding the admin is not removing it. The consent screen repeats this app's own
        // explanation, which says "deactivation step before uninstall", so the generic danger
        // match would otherwise bounce the very screen that turns the optional guard on.
        if (isDeviceAdminActivation(text, label)) return false
        if (EXEMPT.any { text.contains(it, ignoreCase = true) }) return false
        // The list of admins names OwnFate too. It is an index, not the off switch: other apps'
        // admin entries live there and have to stay reachable. Device-admin detail pages are
        // handled by [isDeviceAdminScreen], not by this generic wording match.
        if (looksLikeDeviceAdminPage(text)) return false
        // The app's name has to be near the top: that is where the page says which app this
        // screen is about. A generic page that mentions OwnFate somewhere below is not its
        // off switch.
        if (headLines(text, 6).none { it.contains(label, ignoreCase = true) }) return false
        return DANGER.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * The accessibility detail page, which often does not use the word "service" at all: a Pixel
     * shows a switch labelled "Use OwnFate" and nothing this app's [DANGER] list would match.
     *
     * Page-specific signals identify it:
     *
     *   - the opening words of this app's own service description, which Settings displays there;
     *   - the "&lt;app&gt; shortcut" row that Settings shows on the same page;
     *   - a real toggle on a page whose title is this app's label.
     *
     * The signals are gated on the app label, so another app's accessibility page is left alone.
     * Crucially, there is no "toggle plus the word Accessibility" rule: the main Accessibility
     * list has switches for other services and shows this app's row, so that rule bounced the
     * whole list and made other apps' services unreachable.
     */
    fun isAccessibilityTogglePage(
        text: String?,
        label: String,
        hasToggle: Boolean,
        description: String? = null
    ): Boolean {
        if (text.isNullOrBlank() || label.length < 3) return false
        if (looksLikeSearchResults(text)) return false
        if (!text.contains(label, ignoreCase = true)) return false
        if (description != null && description.length >= 24 &&
            text.contains(description.take(24), ignoreCase = true)
        ) {
            return true
        }
        if (text.contains("$label shortcut", ignoreCase = true)) return true
        return hasToggle && titleLine(text).equals(label, ignoreCase = true)
    }

    /**
     * True for the device-admin consent screen Android shows before the admin becomes active.
     *
     * It is the opposite of an off switch: it only adds the admin, and it cannot remove or stop
     * anything yet. The page repeats this app's explanation, whose words "deactivation" and
     * "uninstall" would otherwise read as a destructive action. The app label still has to name
     * the page, so another app's consent screen is unaffected.
     */
    fun isDeviceAdminActivation(text: String?, label: String): Boolean {
        if (text.isNullOrBlank() || label.length < 3) return false
        if (looksLikeSearchResults(text)) return false
        if (headLines(text, 6).none { it.contains(label, ignoreCase = true) }) return false
        return text.contains("Activate device admin", ignoreCase = true)
    }

    /** True for the activity Android uses for every app's admin consent and detail pages. */
    fun isDeviceAdminClass(className: String?): Boolean =
        className != null && className.contains("DeviceAdminAdd", ignoreCase = true)

    /**
     * The device-admin deactivation screen. Android opens the single `DeviceAdminAdd` activity for
     * every app's admin, so the class name alone is not enough: the page heading must name this
     * app, and it must offer a deactivation action. Another app's page that mentions OwnFate
     * further down is not this app's off switch.
     *
     * Activation is only offered while the admin is inactive, which the caller checks before
     * calling this, so a deliberate activation is not bounced.
     */
    fun isDeviceAdminScreen(className: String?, text: String?, label: String): Boolean {
        if (label.length < 3 || text.isNullOrBlank()) return false
        if (looksLikeSearchResults(text)) return false
        // The app label is rendered as its own heading line near the top, above the action. A
        // matching substring in a description ("OwnFate is also an administrator") or an exact
        // line in a list of active admins does not count.
        if (!adminHeadingNames(text, label)) return false

        // An admin that is already active can only be on its deactivation page. Some builds
        // render this sentence before the action button, and the button used to be the only
        // signal: a page read in that window looked harmless and was left open. The heading
        // above has already tied the page to this app.
        val active = text.contains("admin app is active", ignoreCase = true)

        val fullAction = DEVICE_ADMIN_ACTIONS.any { text.contains(it, ignoreCase = true) }
        if (isDeviceAdminClass(className)) {
            // Window-state events carry the activity class. A bare "Deactivate" is enough here
            // because the activity and the heading have already identified the page.
            return active ||
                text.contains("Deactivate", ignoreCase = true) ||
                text.contains("Remove this device admin", ignoreCase = true)
        }

        // Content-change events can carry a child view's class instead of the activity. Fall
        // back to the detail page's own title and its state or deactivation wording; the admin
        // index uses a different title and never that wording, so it stays reachable.
        val title = titleLine(text) ?: return false
        val detailTitle = title.equals(label, ignoreCase = true) ||
            DEVICE_ADMIN_DETAIL_TITLES.any { it.equals(title, ignoreCase = true) }
        if (active) return detailTitle
        if (!fullAction) return false
        return detailTitle
    }

    /** True when [label] is the app heading on a `DeviceAdminAdd` page, not a row further down. */
    private fun adminHeadingNames(text: String, label: String): Boolean {
        val head = headLines(text, 4)
        val action = head.indexOfFirst {
            it.contains("Deactivate", ignoreCase = true) ||
                it.contains("Remove this device admin", ignoreCase = true)
        }
        val heading = if (action >= 0) head.take(action) else head
        return heading.any { it.equals(label, ignoreCase = true) }
    }

    /** The first meaningful line of the window: the screen title, with navigation skipped. */
    private fun titleLine(text: String): String? {
        val nav = listOf("Navigate up", "Back", "More options", "Close")
        return headLines(text, 5).firstOrNull { line ->
            nav.none { it.equals(line, ignoreCase = true) }
        }
    }

    private fun headLines(text: String, n: Int): List<String> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(n).toList()

    /**
     * True for any Settings device-admin page, index or detail. None of them is a generic
     * off switch: the index must stay reachable, and this app's own detail page is recognised
     * separately by its activity and heading.
     */
    private fun looksLikeDeviceAdminPage(text: String): Boolean {
        // "Device admin apps" (plural) is unique to the index, so it is safe anywhere near the
        // top. The older spellings are matched only as the title, so an ordinary page that merely
        // mentions a "device administrator" in its description is not exempted.
        if (headLines(text, 5).any { it.equals("Device admin apps", ignoreCase = true) }) return true
        val title = titleLine(text) ?: return false
        return DEVICE_ADMIN_TITLES.any { it.equals(title, ignoreCase = true) }
    }

    /**
     * True for Settings' search page, including its result list and empty state. Search names many
     * apps at once and shows destructive-looking labels in its results; it never performs the
     * action itself, so it must stay reachable.
     */
    fun looksLikeSearchResults(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (SEARCH_PAGES.any { text.contains(it, ignoreCase = true) }) return true
        val title = titleLine(text) ?: return false
        return title.equals("Search", ignoreCase = true) ||
            title.equals("Search results", ignoreCase = true)
    }

    /**
     * The power menu itself, before the Safe mode row appears. A menu offers several of these at
     * once ("Power off", "Restart", "Emergency"); ordinary text almost never does. The notification
     * shade's own "Power menu" tile is deliberately not enough on its own: matching that title
     * bounced the whole shade and hid notifications.
     */
    fun isPowerMenu(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        // One action is not a power menu. "Power off" appears on ordinary help pages, battery
        // settings and app screens, and bouncing every one of them made the phone feel broken.
        return POWER_MENU.count { text.contains(it, ignoreCase = true) } >= 2
    }

    /**
     * The power menu's on-screen safe-mode entry. Requires both "Safe mode" and a power-menu
     * action, so an ordinary Settings page that merely mentions safe mode is left alone. Even a
     * match is only friction: the firmware's key-combo boot cannot be intercepted, and the
     * accessibility service is not running in safe mode.
     */
    fun isSafeModeMenu(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (!text.contains("Safe mode", ignoreCase = true)) return false
        return POWER_MENU.any { text.contains(it, ignoreCase = true) }
    }
}
