package org.ownfate.app.guard

import android.accessibilityservice.AccessibilityService
import org.ownfate.app.R
import org.ownfate.app.data.Catch
import org.ownfate.app.data.Store
import org.ownfate.app.isDeviceAdmin

/**
 * Stops the user from switching the guard off from inside the guard.
 *
 * This is the one part of the service that answers a Settings screen rather than page content.
 * It recognises this app's own app-info page, accessibility entry, device-admin deactivation
 * screen, the safe-mode menu and the power menu, then leaves with the shortest sequence that
 * actually works: one BACK for a dialog, BACK, BACK, HOME for a Settings page.
 *
 * Detection is keyed on the app label plus a destructive action, a real toggle, this app's own
 * service description, or the `DeviceAdminAdd` activity — never on the word "Settings" or a
 * generic page title. Settings search and other apps' admin pages are left alone, and so is the
 * consent screen that adds the optional admin: activation must never be mistaken for removal.
 *
 * The whole ladder is skipped while both Protect OwnFate and the power-menu switch are off, so
 * the window is not even read.
 */
internal class OffSwitchGuard(
    private val service: AccessibilityService,
    private val reader: PageReader,
    private val gate: ActionGate
) {

    /** While a BACK/BACK/HOME sequence is running, do not start a second one. */
    private var busyUntil = 0L

    /** Give up ownership of the shared input gate, e.g. when a blocked app takes over. */
    fun cancel() {
        busyUntil = 0L
        if (gate.current() == OWNER) gate.cancel()
    }

    /**
     * Returns true when it intervened, so the caller skips the text scan. [appLabel] is read at
     * runtime by the service, so a rename is still recognised.
     */
    fun guard(pkg: String, eventClass: String?, sw: Switches, appLabel: String): Boolean {
        // Neither guard is on: do not read the window at all. Accessibility and device-admin
        // detection belong to Protect OwnFate; only the power menu has its own switch.
        if (!sw.protect && !sw.power) return false

        val now = System.currentTimeMillis()

        // Another BACK/BACK/HOME is already in flight. Do not stack a second one, and do not fall
        // through to the keyword scan on a Settings page.
        if (now < busyUntil) return true

        val root = reader.safe { service.rootInActiveWindow } ?: return false
        // The event's package is not enough: make sure the window we read belongs to it, and is
        // not one of our own windows.
        if (reader.safe { root.packageName?.toString() } != pkg || reader.ownWindow(root)) return false

        reader.reset()
        val sb = StringBuilder()
        reader.collect(root, sb, 0)
        val text = sb.toString()

        // Settings search lists many apps next to destructive-looking labels. It is not an off
        // switch, and blocking it made ordinary Settings feel broken.
        if (OffSwitch.looksLikeSearchResults(text)) return false

        // The consent screen only adds the admin. This app's own explanation ("deactivation step
        // before uninstall") would otherwise match the destructive wording, so the user could
        // never turn the optional device admin on while Protect OwnFate was already enabled.
        if (OffSwitch.isDeviceAdminActivation(text, appLabel)) return false

        // Every app's admin uses the same `DeviceAdminAdd` activity. Leave any page that is not
        // this app's own deactivation screen alone, even if this app appears somewhere on it.
        // Activation is also skipped: the admin is only active once the user has added it.
        val adminClass = OffSwitch.isDeviceAdminClass(eventClass)
        val deviceAdminScreen = sw.protect && isDeviceAdmin(service) &&
            OffSwitch.isDeviceAdminScreen(eventClass, text, appLabel)
        if (adminClass && !deviceAdminScreen) return false

        val safeModeMenu = sw.power && OffSwitch.isSafeModeMenu(text)
        val powerMenu = sw.power && OffSwitch.isPowerMenu(text)
        val protectHit = sw.protect && (deviceAdminScreen ||
            OffSwitch.isOffSwitch(text, appLabel) ||
            OffSwitch.isAccessibilityTogglePage(
                text,
                appLabel,
                reader.sawToggle,
                service.getString(R.string.acc_desc)
            ))
        val hit = protectHit || safeModeMenu || powerMenu
        if (!hit) return false

        val kind = when {
            safeModeMenu -> "Safe mode"
            powerMenu -> "Power menu"
            deviceAdminScreen -> "Device admin"
            else -> "Off switch"
        }
        Store.addCatch(service, Catch(kind, pkg, sentence(text), now))

        // The power menu is a dialog: one BACK dismisses it and leaves the user where they were.
        // The Settings off-switch screens need the longer BACK, BACK, HOME sequence. Taking the
        // shared gate here cancels any page ladder already in flight, so the two never stack.
        val t = gate.begin(OWNER)
        if (safeModeMenu || powerMenu) {
            busyUntil = now + POWER_GUARD_BUSY_MS
            dismissPowerMenu(t, pkg)
        } else {
            busyUntil = now + GUARD_SEQUENCE_MS
            leaveOffSwitch(t)
        }
        return true
    }

    /**
     * BACK, BACK, HOME.
     *
     * One HOME only hides the page: Settings keeps it in the task, and a back-swipe or a recents
     * tap returns straight to it. Two BACKs pop the OwnFate page (and the page above it) off that
     * back stack first, so returning lands on ordinary Settings instead. HOME then backgrounds
     * Settings so the user is not left staring at it.
     *
     * This is still friction: a force-stop, safe mode or ADB wins.
     */
    private fun leaveOffSwitch(t: Long) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        gate.post(t, GUARD_STEP_MS) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            gate.post(t, GUARD_STEP_MS) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                gate.cancel()
            }
        }
    }

    /**
     * BACK out of the power menu. No HOME: the menu is a dialog over whatever the user was doing,
     * so dismissing it should return them to that screen. A second BACK is sent only when the
     * menu is still in front, so a normal dismissal cannot also step the app back.
     */
    private fun dismissPowerMenu(t: Long, pkg: String) {
        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            gate.post(t, GUARD_STEP_MS) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                gate.cancel()
            }
            return
        }
        gate.post(t, GUARD_STEP_MS) {
            val root = reader.safe { service.rootInActiveWindow }
            if (root != null && reader.safe { root.packageName?.toString() } == pkg) {
                val text = reader.readText(root)
                if (OffSwitch.isPowerMenu(text) || OffSwitch.isSafeModeMenu(text)) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
            gate.cancel()
        }
    }

    /** A short, readable slice of the settings screen for the log. */
    private fun sentence(text: String): String =
        text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(200)

    private companion object {
        /** How this ladder identifies itself in the shared [ActionGate]. */
        const val OWNER = "off-switch"

        /** Spacing between the two BACKs and the HOME that follows them. */
        const val GUARD_STEP_MS = 350L

        /** How long a BACK/BACK/HOME sequence owns the guard, so a second one cannot stack. */
        const val GUARD_SEQUENCE_MS = 1_400L

        /** Power menus get a shorter busy window, so a menu that ignored BACK is tried again. */
        const val POWER_GUARD_BUSY_MS = 700L
    }
}
