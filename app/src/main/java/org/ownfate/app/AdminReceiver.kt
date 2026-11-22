package org.ownfate.app


import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional device admin, used as a speed bump only.
 *
 * Activation is always a deliberate choice on OwnFate's own screen. While the admin is active,
 * uninstalling the app or switching the admin off starts on a Settings screen, and that is one of
 * the screens [OffSwitch] already watches.
 *
 * No policies are declared in `res/xml/device_admin.xml`. OwnFate does not want wipe-data,
 * password or lock-screen power; it only wants the deactivation step that Android puts in front
 * of uninstall for any active admin. This is still not a lock: a force-stop, safe mode or ADB wins.
 */
class AdminReceiver : DeviceAdminReceiver() {

    /** Shown by Settings when the user reaches the deactivation confirmation. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "OwnFate will lose its deactivation step. The accessibility guard still protects the " +
            "app, but a force-stop, safe mode or ADB can still remove it."
}
