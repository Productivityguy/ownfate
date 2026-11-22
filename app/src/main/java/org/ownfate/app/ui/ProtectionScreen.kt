package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.Lock
import org.ownfate.app.data.Store
import org.ownfate.app.isDeviceAdmin
import org.ownfate.app.isServiceOn

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.ownfate.app.ui.Brand.S1
import org.ownfate.app.ui.Brand.S2
import org.ownfate.app.ui.Brand.S3
import org.ownfate.app.ui.Brand.S4
import org.ownfate.app.ui.Brand.S6
import org.ownfate.app.ui.Brand.S8
import org.ownfate.app.ui.Brand.body
import org.ownfate.app.ui.Brand.card
import org.ownfate.app.ui.Brand.col
import org.ownfate.app.ui.Brand.dp
import org.ownfate.app.ui.Brand.gap
import org.ownfate.app.ui.Brand.grow
import org.ownfate.app.ui.Brand.row
import org.ownfate.app.ui.Brand.title

/**
 * Protection.
 *
 * Everything that makes the guard itself hard to switch off: the accessibility binding, the
 * optional device-admin step, the two tamper toggles, and the change lock.
 *
 * The lock is the part with rules. Off, Delay and Password are the three modes; a delay runs
 * from ten minutes to two days. A stricter change always applies at once. A weaker one waits, is
 * listed here while it waits, and can be cancelled; a correct password applies it immediately.
 * There is no recovery, and the screen says so.
 */
class ProtectionScreen(private val host: MainActivity) : Screen {

    private val ctx = host
    private val content: LinearLayout = page(host)
    private val scroll: ScrollView = host.appScroll(content)

    override val view: View get() = scroll

    init {
        refresh()
    }

    override fun refresh() {
        val y = scroll.scrollY
        content.removeAllViews()

        val accessibility = isServiceOn(ctx)
        val admin = isDeviceAdmin(ctx)

        content.addView(gap(ctx, S6))

        // ---- capabilities --------------------------------------------------
        content.addView(host.infoTitle("Status"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            addView(statusRow(
                on = accessibility,
                label = "Accessibility service",
                sub = "",
                turnOn = !accessibility
            ) { host.openAccessibility() })
            addView(Brand.rule(ctx))
            addView(statusRow(
                on = admin,
                label = "Device admin",
                sub = "",
                turnOn = !admin
            ) {
                host.requestDeviceAdmin()
            })
        })
        content.addView(gap(ctx, S6))

        // ---- tamper toggles ------------------------------------------------
        content.addView(host.infoTitle("Guards"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            addView(toggleRow(
                label = "Protect OwnFate",
                sub = "",
                on = Store.bool(ctx, Store.PROTECT_APP, false),
                feature = Lock.Feature.PROTECT_APP
            ))
            addView(Brand.rule(ctx))
            addView(toggleRow(
                label = "Block power menu",
                sub = "",
                on = Store.bool(ctx, Store.BLOCK_POWER, false),
                feature = Lock.Feature.BLOCK_POWER
            ))
        })
        content.addView(gap(ctx, S6))

        // ---- lock ---------------------------------------------------------
        val mode = Lock.mode(ctx)
        val modePending = Lock.pendingFor(ctx, Lock.Feature.LOCK_MODE)
        content.addView(host.infoTitle("Lock changes"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            val options = listOf(Lock.Mode.OFF, Lock.Mode.DELAY, Lock.Mode.PASSWORD)
            options.forEachIndexed { i, m ->
                addView(row(ctx).apply {
                    setPadding(0, dp(ctx, S3), 0, dp(ctx, S3))
                    minimumHeight = dp(ctx, Brand.MIN_ROW)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        when (m) {
                            Lock.Mode.OFF -> { applyMode(Lock.Mode.OFF); refresh() }
                            Lock.Mode.DELAY -> showDelaySetupPopup()
                            Lock.Mode.PASSWORD -> showPasswordSetupPopup()
                        }
                    }
                    addView(title(ctx, modeLabel(m), Brand.T_TITLE).apply { layoutParams = grow() })
                    addView(ctx.stateDot(mode == m && modePending == null, 13f))
                })
                if (i < options.lastIndex) addView(Brand.rule(ctx))
            }
        })
        content.addView(gap(ctx, S6))

        // ---- pending -------------------------------------------------------
        val pending = Lock.pending(ctx)
        if (pending.isNotEmpty()) {
            content.addView(host.infoTitle("Waiting"))
            content.addView(gap(ctx, S3))
            content.addView(card(ctx).apply {
                pending.forEachIndexed { i, p ->
                    addView(row(ctx).apply {
                        setPadding(0, dp(ctx, S3), 0, dp(ctx, S3))
                        minimumHeight = dp(ctx, Brand.MIN_ROW)
                        addView(col(ctx).apply {
                            layoutParams = grow()
                            addView(title(ctx, p.feature.label, Brand.T_BODY))
                            addView(gap(ctx, 2f))
                            addView(body(ctx, p.remainingLabel(), Brand.T_CAPTION, Brand.INK_45))
                        })
                        addView(body(ctx, "Cancel", Brand.T_BODY, Brand.PRIMARY).apply {
                            isClickable = true
                            setPadding(dp(ctx, S3), dp(ctx, S2), dp(ctx, S1), dp(ctx, S2))
                            setOnClickListener {
                                Lock.cancelPending(ctx, p.feature)
                                refresh()
                            }
                        })
                    })
                    if (i < pending.lastIndex) addView(Brand.rule(ctx))
                }
            })
            content.addView(gap(ctx, S6))
        }

        content.addView(gap(ctx, S8))

        scroll.post { scroll.scrollTo(0, y) }
    }

    // ---- rows -------------------------------------------------------------

    private fun statusRow(
        on: Boolean,
        label: String,
        sub: String,
        turnOn: Boolean,
        onTap: () -> Unit
    ): View = row(ctx).apply {
        setPadding(0, dp(ctx, S3), 0, dp(ctx, S3))
        minimumHeight = dp(ctx, Brand.MIN_ROW)
        isClickable = turnOn
        if (turnOn) setOnClickListener { onTap() }
        addView(ctx.stateDot(on))
        addView(col(ctx).apply {
            layoutParams = grow()
            addView(title(ctx, label, Brand.T_BODY))
            if (sub.isNotEmpty()) {
                addView(gap(ctx, 2f))
                addView(body(ctx, sub, Brand.T_CAPTION, Brand.INK_45))
            }
        })
        if (turnOn) {
            // One action, always with the same glyph. Once the capability is on, the action is
            // gone: there is nothing left to turn on, and a dead button reads as unfinished UI.
            addView(row(ctx).apply {
                setPadding(dp(ctx, S3), dp(ctx, S2), dp(ctx, S1), dp(ctx, S2))
                addView(android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_power)
                    contentDescription = null
                    imageTintList = android.content.res.ColorStateList.valueOf(Brand.PRIMARY)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(
                        dp(ctx, 18f), dp(ctx, 18f)
                    ).apply { marginEnd = dp(ctx, 5f) }
                })
                addView(body(ctx, "Turn on", Brand.T_BODY, Brand.PRIMARY))
            })
        }
    }

    private fun toggleRow(
        label: String,
        sub: String,
        on: Boolean,
        feature: Lock.Feature
    ): View {
        val waiting = Lock.pendingFor(ctx, feature)
        val caption = when {
            waiting == null -> sub
            sub.isEmpty() -> "Turns ${if (on) "off" else "on"} in ${waiting.remainingLabel()}"
            else -> "$sub · turns ${if (on) "off" else "on"} in ${waiting.remainingLabel()}"
        }
        return ctx.toggleRow(label, caption, on) {
            host.change(feature, (!on).toString())
        }
    }

    private fun modeLabel(mode: Lock.Mode): String = when (mode) {
        Lock.Mode.OFF -> "Off"
        Lock.Mode.DELAY -> "Delay"
        Lock.Mode.PASSWORD -> "Password"
    }

    private fun showDelaySetupPopup() {
        host.showPopup { dismiss ->
            val box = popupCard(ctx, "Delay")
            val current = Lock.delayMs(ctx)
            val day = Lock.DAY
            val hour = Lock.HOUR
            val (initUnit, initNum) =
                if (current >= day && current % day == 0L) {
                    day to (current / day).toString()
                } else {
                    hour to (current / hour).coerceAtLeast(1L).toString()
                }
            var unit = initUnit
            // The popup only offers hours and days. A legacy minute-long delay cannot be shown
            // exactly, so leave it untouched unless the user actually edits the field.
            var touched = false
            val number = ctx.lineField("How many", initNum).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) { touched = true }
                })
            }
            box.addView(ctx.segmented(
                listOf("Hours" to "h", "Days" to "d"),
                if (initUnit == day) "d" else "h"
            ) { v ->
                unit = if (v == "d") day else hour
                touched = true
            })
            box.addView(gap(ctx, S3))
            box.addView(number)
            if (initNum.toLong() * initUnit != current) {
                box.addView(gap(ctx, S2))
                box.addView(body(
                    ctx,
                    "Current: ${Lock.delayLabel(current)}",
                    Brand.T_CAPTION,
                    Brand.INK_45
                ))
            }
            box.addView(gap(ctx, S4))
            box.addView(ctx.primaryButton("Save") {
                val target = if (!touched) {
                    current
                } else {
                    val n = number.text?.toString()?.trim()?.toLongOrNull()
                    if (n == null || n < 1L) {
                        host.toast("Enter a number")
                        return@primaryButton
                    }
                    n.coerceAtMost(Lock.MAX_DELAY / unit).coerceAtLeast(1L) * unit
                }
                dismiss()
                // The delay and the mode travel together so a password is asked for once, not
                // twice. Going through the lock also keeps the pending store and any leftover
                // configuration consistent, instead of writing two SharedPreferences keys
                // directly behind its back.
                host.changeAll(listOf(
                    Lock.Feature.LOCK_DELAY to target.toString(),
                    Lock.Feature.LOCK_MODE to Lock.Mode.DELAY.name
                ))
                refresh()
            })
            box.addView(gap(ctx, S2))
            box.addView(ctx.quietButton("Cancel") { dismiss() })
            box
        }
    }

    private fun showPasswordSetupPopup() {
        if (Lock.hasPassword(ctx)) {
            // A password already exists: change it, then make sure Password mode is the one in
            // use. This also covers a password left behind by an earlier Password mode.
            host.showPopup { dismiss ->
                val box = popupCard(ctx, "Password")
                val current = passwordField("Current password")
                val first = passwordField("New password")
                val second = passwordField("Repeat password")
                box.addView(current)
                box.addView(gap(ctx, S2))
                box.addView(first)
                box.addView(gap(ctx, S2))
                box.addView(second)
                box.addView(gap(ctx, S4))
                box.addView(ctx.primaryButton("Save") {
                    val cur = current.text?.toString().orEmpty()
                    if (!Lock.verify(ctx, cur)) {
                        host.toast("Wrong password — nothing changed")
                        return@primaryButton
                    }
                    val a = first.text?.toString().orEmpty()
                    val b = second.text?.toString().orEmpty()
                    when {
                        a.length < Lock.MIN_PASSWORD_LENGTH ->
                            host.toast("At least ${Lock.MIN_PASSWORD_LENGTH} characters")
                        a != b -> host.toast("Passwords do not match")
                        else -> when (Lock.setPassword(ctx, a, cur)) {
                            Lock.Outcome.APPLIED -> {
                                dismiss()
                                host.toast("Password set")
                                if (Lock.mode(ctx).hasPassword) {
                                    refresh()
                                } else {
                                    host.change(
                                        Lock.Feature.LOCK_MODE,
                                        Lock.Mode.PASSWORD.name
                                    )
                                    refresh()
                                }
                            }
                            else -> host.toast("Could not save the password")
                        }
                    }
                })
                box.addView(gap(ctx, S2))
                box.addView(ctx.quietButton("Cancel") { dismiss() })
                box
            }
        } else {
            // Set a new password, then enable Password mode.
            askNewPassword(current = null) {
                host.change(Lock.Feature.LOCK_MODE, Lock.Mode.PASSWORD.name)
                refresh()
            }
        }
    }

    private fun applyMode(target: Lock.Mode) {
        if (target.hasPassword && !Lock.hasPassword(ctx)) {
            askNewPassword(current = null) {
                host.change(Lock.Feature.LOCK_MODE, target.name)
            }
            return
        }
        host.change(Lock.Feature.LOCK_MODE, target.name)
    }

    private fun askNewPassword(current: String?, onSaved: (() -> Unit)? = null) {
        host.showPopup { dismiss ->
            val box = popupCard(ctx, if (current == null) "Set a password" else "Change password")
            val first = passwordField("New password")
            val second = passwordField("Repeat password")
            box.addView(first)
            box.addView(gap(ctx, S2))
            box.addView(second)
            box.addView(gap(ctx, S4))
            box.addView(ctx.primaryButton("Save") {
                val a = first.text?.toString().orEmpty()
                val b = second.text?.toString().orEmpty()
                when {
                    a.length < Lock.MIN_PASSWORD_LENGTH ->
                        host.toast("At least ${Lock.MIN_PASSWORD_LENGTH} characters")
                    a != b -> host.toast("Passwords do not match")
                    else -> when (Lock.setPassword(ctx, a, current)) {
                        Lock.Outcome.APPLIED -> {
                            dismiss()
                            host.toast("Password set")
                            if (onSaved != null) onSaved() else refresh()
                        }
                        else -> host.toast("Could not save the password")
                    }
                }
            })
            box.addView(gap(ctx, S2))
            box.addView(ctx.quietButton("Cancel") { dismiss() })
            box
        }
    }

    private fun passwordField(hint: String): EditText = ctx.lineField(hint).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
}
