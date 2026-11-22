package org.ownfate.app

import org.ownfate.app.guard.OffSwitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The off-switch guard is deliberately easy to test and hard to make too broad: it needs the app
 * label AND a destructive action, and it backs off entirely on pages that are ordinary Settings.
 */
class OffSwitchTest {

    // ---- where it is allowed to look --------------------------------------

    @Test fun settingsIsScoped() = assertTrue(OffSwitch.looksLikeSettings("com.android.settings"))

    @Test fun samsungSettingsIsScoped() =
        assertTrue(OffSwitch.looksLikeSettings("com.samsung.android.settings"))

    @Test fun packageInstallerIsScoped() =
        assertTrue(OffSwitch.looksLikeSettings("com.google.android.packageinstaller"))

    @Test fun launcherIsScoped() = assertTrue(OffSwitch.looksLikeSettings("com.android.launcher3"))

    @Test fun xiaomiHomeIsScoped() = assertTrue(OffSwitch.looksLikeSettings("com.miui.home"))

    @Test fun systemUiPowerMenuIsScoped() =
        assertTrue(OffSwitch.looksLikeSettings("com.android.systemui"))

    @Test fun samsungPowerMenuIsScoped() =
        assertTrue(OffSwitch.looksLikeSettings("com.samsung.android.app.powerui"))

    @Test fun androidGlobalActionsIsScoped() =
        assertTrue(OffSwitch.looksLikeSettings("android"))

    @Test fun browserIsNotScoped() = assertFalse(OffSwitch.looksLikeSettings("com.android.chrome"))

    @Test fun blankPackageIsNotScoped() {
        assertFalse(OffSwitch.looksLikeSettings(null))
        assertFalse(OffSwitch.looksLikeSettings(""))
    }

    // ---- when it bounces --------------------------------------------------

    @Test fun appInfoPageIsAnOffSwitch() =
        assertTrue(OffSwitch.isOffSwitch("OwnFate\nOpen\nUninstall\nForce stop", "OwnFate"))

    @Test fun launcherUninstallDialogIsAnOffSwitch() =
        assertTrue(OffSwitch.isOffSwitch("Uninstall OwnFate?\nCancel\nUninstall", "OwnFate"))

    @Test fun samsungAccessibilityToggleIsAnOffSwitch() =
        assertTrue(OffSwitch.isOffSwitch("Accessibility\nOwnFate\nUse service", "OwnFate"))

    @Test fun clearDataIsAnOffSwitch() =
        assertTrue(OffSwitch.isOffSwitch("Storage & cache\nClear storage", "OwnFate")
            || OffSwitch.isOffSwitch("OwnFate\nClear data", "OwnFate"))

    // A device-admin detail page is detected by its activity and heading, never by the generic
    // word match: that is what kept other apps' admin pages and the admin index reachable.
    @Test fun deviceAdminDetailPageIsDetectedByItsActivity() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    @Test fun aDeviceAdminLookingPageIsNotAGenericOffSwitch() =
        assertFalse(OffSwitch.isOffSwitch(
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    // The index that merely lists admins must stay reachable, or the whole Security page breaks.
    @Test fun deviceAdminListIsNotAnOffSwitch() =
        assertFalse(OffSwitch.isOffSwitch(
            "Device admin apps\nOwnFate\nDevice admin app", "OwnFate"))

    @Test fun deviceAdminListWithADeactivateLabelIsStillNotAnOffSwitch() =
        assertFalse(OffSwitch.isOffSwitch(
            "Device admin apps\nOwnFate\nDevice admin app\nDeactivate", "OwnFate"))

    // A single admin's deactivation screen is the `DeviceAdminAdd` activity and names the app.
    @Test fun deviceAdminScreenIsDetected() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    @Test fun deviceAdminListIsNotTheAdminScreen() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.Settings\$DeviceAdminSettingsActivity",
            "Device admin apps\nOwnFate\nDeactivate", "OwnFate"))

    @Test fun anotherAppsAdminScreenIsNotOwnFates() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nSome other app\nDeactivate this device admin app", "OwnFate"))

    // Another app's admin page may mention OwnFate lower down (for example in a list of active
    // administrators). That must not turn the other app's page into OwnFate's off switch.
    @Test fun anotherAppsAdminScreenThatMentionsOwnFateIsStillNotOwnFates() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nSome other app\nDeactivate this device admin app\n" +
                "Active device administrators\nOwnFate", "OwnFate"))

    @Test fun ownAdminScreenDeepInThePageIsNotAHeadingMatch() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nSome other app\nAbout\nDetails\nMore\nOwnFate\nDeactivate",
            "OwnFate"))

    // Settings search names many apps and shows labels such as Uninstall and Deactivate. It is a
    // result list, not the action, so the whole page must stay reachable.
    @Test fun settingsSearchResultsAreNotAnOffSwitch() {
        assertTrue(OffSwitch.looksLikeSearchResults(
            "Search results\nOwnFate\nDevice admin app\nDeactivate"))
        assertFalse(OffSwitch.isOffSwitch(
            "Search results\nOwnFate\nDevice admin app\nDeactivate", "OwnFate"))
        assertFalse(OffSwitch.isOffSwitch("Search settings\nOwnFate\nUninstall", "OwnFate"))
        assertFalse(OffSwitch.isOffSwitch("Search\nOwnFate\nClear data", "OwnFate"))
    }

    @Test fun ownAdminScreenIsStillDetectedAfterNarrowing() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.DeviceAdminAdd",
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    @Test fun deviceAdminClassIsRecognised() {
        assertTrue(OffSwitch.isDeviceAdminClass("com.android.settings.DeviceAdminAdd"))
        assertFalse(OffSwitch.isDeviceAdminClass(
            "com.android.settings.Settings\$DeviceAdminSettingsActivity"))
        assertFalse(OffSwitch.isDeviceAdminClass(null))
    }

    // The consent screen only adds the admin. It repeats this app's own explanation, which says
    // "deactivation step before uninstall": those words must not bounce the screen that lets the
    // user turn the optional guard on.
    @Test fun deviceAdminActivationIsNotAnOffSwitch() {
        val text = "Activate device admin app?\nOwnFate\nOwnFate uses device admin as a " +
            "deactivation step before uninstall. It cannot stop a force-stop, safe mode or ADB.\n" +
            "Activate this device admin app\nCancel"
        assertTrue(OffSwitch.isDeviceAdminActivation(text, "OwnFate"))
        assertFalse(OffSwitch.isOffSwitch(text, "OwnFate"))
    }

    @Test fun anotherAppsAdminActivationIsNotOwnFatesConsentScreen() =
        assertFalse(OffSwitch.isDeviceAdminActivation(
            "Activate device admin app?\nSome other app\nActivate this device admin app", "OwnFate"))

    // Deactivation is not activation: the absent "Activate" wording keeps the real detail page
    // on the guard's side of the line.
    @Test fun adminDeactivationPageIsNotAnActivationPage() =
        assertFalse(OffSwitch.isDeviceAdminActivation(
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    // A content-change event can report a child view's class instead of the activity. The page
    // heading plus the full deactivation wording still identify this app's own detail page.
    @Test fun ownAdminScreenIsDetectedWithoutTheActivityClass() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "android.widget.Switch",
            "Device admin app\nOwnFate\nDeactivate this device admin app", "OwnFate"))

    @Test fun anotherAppsAdminIsNotDetectedWithoutTheActivityClass() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "android.widget.Switch",
            "Device admin app\nSome other app\nDeactivate this device admin app", "OwnFate"))

    // The deactivation page is partly rendered when the guard first reads it: the sentence about
    // the active admin arrives before the Deactivate button, and no later event may announce that
    // button. The active sentence is therefore enough on its own.
    @Test fun activeAdminPageIsDetectedBeforeTheButtonRenders() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "android.view.ViewGroup",
            "Device admin app\nNavigate up\nDevice admin app\nOwnFate\n" +
                "This admin app is active and allows the app OwnFate to perform the following " +
                "operations:", "OwnFate"))

    @Test fun activeAdminPageWithoutItsButtonIsStillDetected() =
        assertTrue(OffSwitch.isDeviceAdminScreen(
            "android.widget.FrameLayout",
            "Device admin app\nOwnFate\nThis admin app is active and allows the app OwnFate to " +
                "perform the following operations:", "OwnFate"))

    @Test fun anotherAppsActiveAdminPageIsNotOwnFates() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "android.view.ViewGroup",
            "Device admin app\nSome other app\nThis admin app is active and allows the app " +
                "Some other app to perform the following operations:", "OwnFate"))

    // The consent page says "Activating", not "is active"; a partly rendered consent page must
    // stay open so the user can add the optional admin.
    @Test fun partlyRenderedActivationPageIsNotTheAdminScreen() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "android.view.ViewGroup",
            "Activate device admin app?\nOwnFate\nActivating this admin app will allow the app " +
                "OwnFate to perform the following operations:", "OwnFate"))

    @Test fun aNormalSettingsScreenIsNotTheAdminScreen() =
        assertFalse(OffSwitch.isDeviceAdminScreen(
            "com.android.settings.Settings\$AppInfoActivity",
            "OwnFate\nUninstall\nForce stop", "OwnFate"))

    // ---- the accessibility detail page ------------------------------------

    // Many builds label the switch "Use OwnFate" and never use the word "service", so the old
    // wording rule alone misses the exact screen that turns the guard off. A detail page titled
    // with the app label still falls back to the toggle.
    @Test fun accessibilityPageWithToggleIsAnOffSwitch() =
        assertTrue(OffSwitch.isAccessibilityTogglePage(
            "OwnFate\nUse OwnFate", "OwnFate", hasToggle = true))

    @Test fun useOwnFateWithoutDangerWordingIsNotAWordingMatch() =
        assertFalse(OffSwitch.isOffSwitch("Accessibility\nOwnFate\nUse OwnFate", "OwnFate"))

    @Test fun accessibilityPageWithoutAToggleIsNot() =
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Accessibility\nOwnFate\nUse OwnFate", "OwnFate", hasToggle = false))

    @Test fun ordinaryPageWithAToggleIsNot() =
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Default apps\nOwnFate", "OwnFate", hasToggle = true))

    @Test fun anotherServicesAccessibilityPageIsNot() =
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Accessibility\nTalkBack\nUse service", "OwnFate", hasToggle = true))

    // Some OEMs title the page just "OwnFate". The page still shows the service description, so
    // its opening words are the second signal. A description match does not need a toggle: some
    // builds expose the switch as a plain button.
    @Test fun accessibilityPageDetectedByServiceDescription() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertTrue(OffSwitch.isAccessibilityTogglePage(
            "OwnFate\nUse OwnFate\n$desc", "OwnFate", hasToggle = true, description = desc))
    }

    @Test fun accessibilityDescriptionIsEnoughWithoutAToggle() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertTrue(OffSwitch.isAccessibilityTogglePage(
            "OwnFate\nUse OwnFate\n$desc", "OwnFate", hasToggle = false, description = desc))
    }

    // Settings also shows a "<app> shortcut" row on that page, which is a third signal.
    @Test fun accessibilityPageDetectedByShortcutTitle() =
        assertTrue(OffSwitch.isAccessibilityTogglePage(
            "Accessibility\nOwnFate\nOwnFate shortcut", "OwnFate", hasToggle = false))

    // A Notifications page also names OwnFate and carries a toggle, but it has no accessibility
    // signal. It must stay reachable.
    @Test fun notificationsToggleIsNotAnAccessibilityPage() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Notifications\nOwnFate\nAll OwnFate notifications",
            "OwnFate", hasToggle = true, description = desc))
    }

    // ---- the general accessibility pages stay usable ----------------------

    // The main Accessibility list and the shortcut configuration pages are how other apps'
    // services and shortcuts are managed. None of them is OwnFate's own page, so none is blocked.
    // The list really does carry switches for the other services, so hasToggle is true here: a
    // rule of "a toggle somewhere on a page that says Accessibility" would bounce the whole list.
    @Test fun mainAccessibilityListIsNotOwnFatesPage() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Accessibility\nTalkBack\nSelect to Speak\nAccessibility shortcuts\n" +
                "Accessibility button\nOwnFate\nInstalled apps",
            "OwnFate", hasToggle = true, description = desc))
    }

    @Test fun accessibilityListWithOwnFateRowAndTogglesIsNotOwnFatesPage() =
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Accessibility\nTalkBack\nOwnFate\nSelect to Speak",
            "OwnFate", hasToggle = true))

    @Test fun volumeKeyShortcutPageIsNotOwnFatesPage() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Volume key shortcut\nUse service\nShortcut service\nAllow from lock screen",
            "OwnFate", hasToggle = true, description = desc))
        assertFalse(OffSwitch.isOffSwitch(
            "Volume key shortcut\nShortcut service\nAllow from lock screen", "OwnFate"))
    }

    @Test fun accessibilityMenuSettingsIsNotOwnFatesPage() {
        val desc = "OwnFate reads address bars, search boxes and on-screen text, and steps back."
        assertFalse(OffSwitch.isAccessibilityTogglePage(
            "Accessibility Menu\nUse service\nAccessibility Menu shortcut\nCustomize",
            "OwnFate", hasToggle = true, description = desc))
    }

    @Test fun safeModePowerMenuIsDetected() =
        assertTrue(OffSwitch.isSafeModeMenu("Power off\nRestart\nEmergency\nSafe mode"))

    @Test fun powerMenuIsDetected() =
        assertTrue(OffSwitch.isPowerMenu("Power off\nRestart\nEmergency"))

    // A notification shade can contain a "Power menu" tile. That title alone must not bounce the
    // shade; only the actual power options do.
    @Test fun powerMenuTitleAloneIsNotTheMenu() =
        assertFalse(OffSwitch.isPowerMenu("Power menu\nCancel"))

    @Test fun powerMenuTileInTheShadeIsNotTheMenu() =
        assertFalse(OffSwitch.isPowerMenu("Quick settings\nPower menu\nNotifications\nWi-Fi"))

    @Test fun safeModeRowIsAlsoThePowerMenu() =
        assertTrue(OffSwitch.isPowerMenu("Power off\nRestart\nSafe mode"))

    @Test fun aSinglePowerActionIsNotTheMenu() =
        assertFalse(OffSwitch.isPowerMenu("Restart the sync"))

    // "Power off" alone is ordinary UI copy on help and battery pages; a real menu offers more.
    @Test fun powerOffAloneIsNotTheMenu() =
        assertFalse(OffSwitch.isPowerMenu("Power off"))

    @Test fun ordinaryPowerSettingsIsNotTheMenu() =
        assertFalse(OffSwitch.isPowerMenu("Power saving mode\nRestart required"))

    @Test fun safeModeMentionWithoutPowerMenuIsNot() =
        assertFalse(OffSwitch.isSafeModeMenu("Safe mode\nAbout phone\nBuild number"))

    @Test fun powerMenuWithoutSafeModeIsNot() =
        assertFalse(OffSwitch.isSafeModeMenu("Power off\nRestart\nEmergency"))

    // ---- when it must leave the screen alone ------------------------------

    @Test fun settingsWithoutTheAppIsNotAnOffSwitch() =
        assertFalse(OffSwitch.isOffSwitch("Uninstall\nForce stop", "OwnFate"))

    @Test fun appWithoutADestructiveActionIsNotAnOffSwitch() =
        assertFalse(OffSwitch.isOffSwitch("OwnFate\nNotifications\nPermissions", "OwnFate"))

    @Test fun exemptPageWins() =
        assertFalse(OffSwitch.isOffSwitch("Privacy\nOwnFate\nUninstall", "OwnFate"))

    @Test fun exemptSecurityPageWins() =
        assertFalse(OffSwitch.isOffSwitch("Fingerprint\nOwnFate\nDeactivate", "OwnFate"))

    @Test fun shortLabelIsIgnored() =
        assertFalse(OffSwitch.isOffSwitch("Uninstall", "Fr"))

    @Test fun blankTextIsIgnored() = assertFalse(OffSwitch.isOffSwitch("", "OwnFate"))
}
