package org.ownfate.app.ui

import org.ownfate.app.MainActivity
import org.ownfate.app.R
import org.ownfate.app.data.CustomEdit
import org.ownfate.app.data.Lock
import org.ownfate.app.data.Store
import org.ownfate.app.data.apps.AppBlocklist
import org.ownfate.app.data.apps.GamblingApps
import org.ownfate.app.data.apps.PredictionApps
import org.ownfate.app.guard.OffSwitch

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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

/** Custom: Blocklist, Allowlist, and bundled gambling apps, styled like Home. */
class CustomScreen(private val host: MainActivity) : Screen {

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

        content.addView(gap(ctx, S6))

        content.addView(host.infoTitle("Blocklist"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            addView(editRow("Words / sites", Lock.Feature.CUSTOM_WORDS) { showEditWords(block = true) })
            addView(Brand.rule(ctx))
            addView(editRow("Apps", Lock.Feature.CUSTOM_APPS) { showEditApps(block = true) })
        })
        content.addView(gap(ctx, S6))

        content.addView(host.infoTitle("Exclude"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            addView(editRow("Words / sites", Lock.Feature.ALLOW_WORDS) { showEditWords(block = false) })
            addView(Brand.rule(ctx))
            addView(editRow("Exclude apps", Lock.Feature.ALLOW_APPS) { showEditApps(block = false) })
        })

        content.addView(gap(ctx, S6))

        content.addView(host.infoTitle("Search add-ons"))
        content.addView(gap(ctx, S3))
        content.addView(card(ctx).apply {
            addView(searchToggle(
                "Instagram search",
                Lock.Feature.INSTAGRAM_SEARCH,
                Store.BLOCK_INSTAGRAM_SEARCH
            ))
            addView(Brand.rule(ctx))
            addView(searchToggle(
                "X (Twitter) search",
                Lock.Feature.X_SEARCH,
                Store.BLOCK_X_SEARCH
            ))
            addView(Brand.rule(ctx))
            addView(searchToggle(
                "Reddit search",
                Lock.Feature.REDDIT_SEARCH,
                Store.BLOCK_REDDIT_SEARCH
            ))
            addView(Brand.rule(ctx))
            addView(searchToggle(
                "Telegram search",
                Lock.Feature.TELEGRAM_SEARCH,
                Store.BLOCK_TELEGRAM_SEARCH
            ))
        })

        content.addView(gap(ctx, S8))
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun editRow(label: String, feature: Lock.Feature, onEdit: () -> Unit): View =
        row(ctx).apply {
            setPadding(0, dp(ctx, S3), 0, dp(ctx, S3))
            minimumHeight = dp(ctx, Brand.MIN_ROW)
            addView(title(ctx, label, Brand.T_TITLE).apply { layoutParams = grow() })
            val waiting = Lock.pendingFor(ctx, feature)
            if (waiting != null) {
                addView(body(ctx, "Waiting", Brand.T_CAPTION, Brand.INK_45).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, dp(ctx, S2), 0)
                })
            }
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_edit)
                contentDescription = "Edit $label"
                imageTintList = ColorStateList.valueOf(Brand.PRIMARY)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                background = Brand.tappable(ctx, Brand.R_PILL, Brand.FIELD)
                setPadding(dp(ctx, 11f), dp(ctx, 11f), dp(ctx, 11f), dp(ctx, 11f))
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f))
                isClickable = true
                isFocusable = true
                setOnClickListener { onEdit() }
            })
        }


    /**
     * A search add-on switch. A waiting change is shown in the caption, so a delayed turn-off does
     * not look like a switch that ignored the tap.
     */
    private fun searchToggle(label: String, feature: Lock.Feature, key: String): View {
        val on = Store.bool(ctx, key, false)
        val waiting = Lock.pendingFor(ctx, feature)
        val sub = waiting?.let { "Turns ${if (on) "off" else "on"} in ${it.remainingLabel()}" }
            .orEmpty()
        return ctx.toggleRow(label, sub, on) { host.change(feature, (!on).toString()) }
    }


    private fun showEditWords(block: Boolean) {
        host.showPopup { dismiss ->
            val titleText = if (block) "Block words / sites" else "Excluded words / sites"
            val feature = if (block) Lock.Feature.CUSTOM_WORDS else Lock.Feature.ALLOW_WORDS
            val box = popupCard(ctx, titleText)

            // Show what the next save will build on. A waiting removal is already the intended
            // state, and editing the active text would let the old value come back.
            val waiting = Lock.pendingFor(ctx, feature)?.value
            val initial = if (block) {
                waiting ?: Store.customRaw(ctx)
            } else {
                CustomEdit.effective(Store.set(ctx, Store.ALLOW_WORDS), waiting)
                    .toList().sorted().joinToString("\n")
            }
            val field = android.widget.EditText(ctx).apply {
                setText(initial)
                hint = "One per line"
                setTextColor(Brand.INK)
                setHintTextColor(Brand.INK_45)
                typeface = Brand.regular(ctx)
                background = Brand.surface(ctx, Brand.R_TILE, Brand.FIELD)
                setPadding(dp(ctx, S4), dp(ctx, S4), dp(ctx, S4), dp(ctx, S4))
                isSingleLine = false
                minLines = 6
                maxLines = 6
                // A word list is data, not prose: spell check and suggestions only add lag.
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                gravity = Gravity.TOP or Gravity.START
                isVerticalScrollBarEnabled = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 160f)
                )
            }
            box.addView(field)
            box.addView(gap(ctx, S4))
            box.addView(ctx.primaryButton("Save") {
                val draft = CustomEdit.words(field.text?.toString().orEmpty())
                // Compare against the value a waiting change will write, not against the active
                // value. Otherwise the save silently cancels the pending entry, or schedules a
                // value that erases what was just edited.
                val pending = Lock.pendingFor(ctx, feature)
                val baseRaw = if (block) {
                    pending?.value ?: Store.customRaw(ctx)
                } else {
                    CustomEdit.effective(Store.set(ctx, Store.ALLOW_WORDS), pending?.value)
                        .sorted().joinToString("\n")
                }
                val base = CustomEdit.words(baseRaw)
                val split = CustomEdit.split(base, draft)
                val added = split.added
                val removed = split.removed
                if (added.isEmpty() && removed.isEmpty()) {
                    host.toast("Nothing changed"); return@primaryButton
                }
                // One write for the whole edit. A mixed add-and-remove goes through the lock as
                // one change, so a wrong password cannot leave the additions applied while the
                // removals are refused.
                val target = when {
                    removed.isEmpty() && block -> CustomEdit.withAdded(baseRaw, added)
                    removed.isEmpty() -> (base + added).sorted().joinToString("\n")
                    block -> field.text?.toString()?.trim().orEmpty()
                    else -> draft.sorted().joinToString("\n")
                }
                host.change(feature, target)
                dismiss(); refresh()
            })
            box.addView(gap(ctx, S2))
            box.addView(ctx.quietButton("Cancel") { dismiss(); refresh() })
            box
        }
    }

    private fun showEditApps(block: Boolean) {
        val installed = installedApps(ctx).filter { app ->
            app.pkg != ctx.packageName && !OffSwitch.looksLikeSettings(app.pkg)
        }
        host.showPopup { dismiss ->
            val box = popupCard(ctx, if (block) "Blocked apps" else "Exclude apps")
            if (!block) {
                box.addView(body(ctx, "Excluded apps are not monitored.", Brand.T_CAPTION, Brand.INK_45))
                box.addView(gap(ctx, S2))
            }
            val selected = linkedSetOf<String>()
            var query = ""

            // Snapshot the sets once for the whole dialog instead of re-reading preferences for
            // every row on every keystroke. A toggle refreshes the snapshot before updating only
            // the row it changed.
            fun customNow(): Set<String> =
                pendingSet(Lock.Feature.CUSTOM_APPS, Store.set(ctx, Store.CUSTOM_APPS))
            fun removedNow(): Set<String> =
                pendingSet(Lock.Feature.REMOVED_BUNDLED, Store.set(ctx, Store.REMOVED_BUNDLED))
            fun allowNow(): Set<String> =
                pendingSet(Lock.Feature.ALLOW_APPS, Store.set(ctx, Store.ALLOW_APPS))
            fun gamblingOn(): Boolean =
                pendingBool(Lock.Feature.GAMBLING_APPS, Store.bool(ctx, Store.GAMBLING_APPS, false))
            fun predictionOn(): Boolean =
                pendingBool(Lock.Feature.PREDICTION_MARKETS,
                    Store.bool(ctx, Store.PREDICTION_MARKETS, false))

            var customSet = customNow()
            var removedSet = removedNow()
            var allowSet = allowNow()
            var gambling = gamblingOn()
            var prediction = predictionOn()

            fun refreshState() {
                customSet = customNow()
                removedSet = removedNow()
                allowSet = allowNow()
                gambling = gamblingOn()
                prediction = predictionOn()
            }

            // The bundled lists are a real block while their switches are on. Showing only custom
            // entries made every bundled app look off, and offered no way to except one.
            fun bundledBlocked(pkg: String): Boolean =
                ((gambling && pkg in GamblingApps.bundled) ||
                    (prediction && pkg in PredictionApps.bundled)) && pkg !in removedSet

            fun isBlocked(pkg: String): Boolean =
                pkg !in allowSet && (pkg in customSet || bundledBlocked(pkg))

            fun shown(pkg: String): Boolean = if (block) isBlocked(pkg) else pkg in allowSet

            // One stable order for as long as the popup is open: entries already in the list
            // first, then everything else. Rows are built once in this order and never moved.
            val baseOrder = installed.filter { shown(it.pkg) } + installed.filter { !shown(it.pkg) }

            val listHost = col(ctx).apply {
                setPadding(0, dp(ctx, S2), 0, dp(ctx, S2))
            }
            val listScroll = ScrollView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 216f)
                )
                isVerticalScrollBarEnabled = false
                addView(listHost)
            }

            val appCard = card(ctx)
            val noneCard = card(ctx).apply {
                addView(body(ctx, "None", Brand.T_BODY, Brand.INK_45))
            }
            // One container per app holds its row and its trailing rule. Filtering only flips
            // container visibility, so typing never creates or destroys a single view.
            val rows = LinkedHashMap<String, LinearLayout>()
            lateinit var replaceRow: (AppEntry, Int) -> Unit

            fun makeRow(app: AppEntry, index: Int): View {
                val inList = shown(app.pkg)
                val on = inList || selected.contains(app.pkg)
                return ctx.toggleRow(app.label, "", on) {
                    if (inList) {
                        if (block) unblockApp(app.pkg) else removeAllowApp(app.pkg)
                    } else if (selected.contains(app.pkg)) {
                        selected.remove(app.pkg)
                    } else {
                        selected.add(app.pkg)
                    }
                    refreshState()
                    replaceRow(app, index)
                    refresh()
                }
            }

            replaceRow = { app, index ->
                rows[app.pkg]?.let { c ->
                    c.removeAllViews()
                    c.addView(makeRow(app, index))
                    if (index < baseOrder.lastIndex) c.addView(Brand.rule(ctx))
                }
            }

            baseOrder.forEachIndexed { i, app ->
                val container = col(ctx)
                rows[app.pkg] = container
                container.addView(makeRow(app, i))
                if (i < baseOrder.lastIndex) container.addView(Brand.rule(ctx))
                appCard.addView(container)
            }
            listHost.addView(appCard)
            listHost.addView(noneCard)

            fun filter() {
                val q = query
                var visible = 0
                baseOrder.forEach { app ->
                    val show = q.isBlank() || app.label.contains(q, true) || app.pkg.contains(q, true)
                    rows[app.pkg]?.visibility = if (show) View.VISIBLE else View.GONE
                    if (show) visible++
                }
                appCard.visibility = if (visible == 0) View.GONE else View.VISIBLE
                noneCard.visibility = if (visible == 0) View.VISIBLE else View.GONE
                listScroll.scrollTo(0, 0)
            }

            val search = ctx.lineField("Search apps", "")
            val applyQuery = Runnable {
                val next = search.text?.toString().orEmpty()
                if (next != query) {
                    query = next
                    filter()
                }
            }
            search.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    v.removeCallbacks(applyQuery)
                }
            })
            box.addView(search)
            filter()
            box.addView(listScroll)
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    search.removeCallbacks(applyQuery)
                    search.postDelayed(applyQuery, SEARCH_DEBOUNCE_MS)
                }
            })
            box.addView(gap(ctx, S4))
            box.addView(ctx.primaryButton("Save") {
                search.removeCallbacks(applyQuery)
                val added = selected.filter { !shown(it) }.toSet()
                if (added.isEmpty()) {
                    host.toast("Nothing to add")
                    return@primaryButton
                }
                if (block) addBlockedApps(added) else addAllowedApps(added)
                selected.clear()
                dismiss()
                refresh()
            })
            box.addView(gap(ctx, S2))
            box.addView(ctx.quietButton("Cancel") {
                search.removeCallbacks(applyQuery)
                dismiss()
                refresh()
            })
            box
        }
    }

    // ---- mutations ---------------------------------------------------------

    /** The active set, or the value a waiting change will write. */
    private fun pendingSet(feature: Lock.Feature, active: Set<String>): Set<String> =
        CustomEdit.effective(active, Lock.pendingFor(ctx, feature)?.value)

    private fun pendingBool(feature: Lock.Feature, active: Boolean): Boolean =
        Lock.pendingFor(ctx, feature)?.value?.toBoolean() ?: active

    private fun text(set: Set<String>): String = set.sorted().joinToString("\n")

    /**
     * Take [pkg] off the blocked list. A bundled app is excepted rather than silently re-added to
     * the custom list, so turning the bundled switch off later does not leave an invisible block
     * behind. Both edits go through one password prompt.
     */
    private fun unblockApp(pkg: String) {
        unblockApps(setOf(pkg))
    }

    private fun unblockApps(pkgs: Set<String>) {
        if (pkgs.isEmpty()) return
        val changes = ArrayList<Pair<Lock.Feature, String>>()
        val custom = pendingSet(Lock.Feature.CUSTOM_APPS, Store.set(ctx, Store.CUSTOM_APPS))
        val nextCustom = custom - pkgs
        if (nextCustom != custom) changes += Lock.Feature.CUSTOM_APPS to text(nextCustom)

        val bundled = pendingBool(
            Lock.Feature.GAMBLING_APPS, Store.bool(ctx, Store.GAMBLING_APPS, false)
        ) || pendingBool(
            Lock.Feature.PREDICTION_MARKETS, Store.bool(ctx, Store.PREDICTION_MARKETS, false)
        )
        if (bundled) {
            val removed = pendingSet(Lock.Feature.REMOVED_BUNDLED, Store.set(ctx, Store.REMOVED_BUNDLED))
            val nextRemoved = removed + pkgs.filter { AppBlocklist.isBundled(it) }
            if (nextRemoved != removed) {
                changes += Lock.Feature.REMOVED_BUNDLED to text(nextRemoved)
            }
        }
        host.changeAll(changes)
    }

    /**
     * Add apps to the blocklist. Adding is stricter, so it applies at once; an allow exception for
     * the same package and a bundled-app exception are removed in the same batch, or the app would
     * stay reachable while showing as blocked.
     */
    private fun addBlockedApps(added: Set<String>) {
        if (added.isEmpty()) return
        val changes = ArrayList<Pair<Lock.Feature, String>>()
        val custom = pendingSet(Lock.Feature.CUSTOM_APPS, Store.set(ctx, Store.CUSTOM_APPS))
        val nextCustom = custom + added
        if (nextCustom != custom) changes += Lock.Feature.CUSTOM_APPS to text(nextCustom)

        val allow = pendingSet(Lock.Feature.ALLOW_APPS, Store.set(ctx, Store.ALLOW_APPS))
        val nextAllow = allow - added
        if (nextAllow != allow) changes += Lock.Feature.ALLOW_APPS to text(nextAllow)

        val removed = pendingSet(Lock.Feature.REMOVED_BUNDLED, Store.set(ctx, Store.REMOVED_BUNDLED))
        val nextRemoved = removed - added
        if (nextRemoved != removed) changes += Lock.Feature.REMOVED_BUNDLED to text(nextRemoved)

        host.changeAll(changes)
    }

    /** Removing an allow exception is stricter, so the lock applies it at once. */
    private fun removeAllowApp(pkg: String) {
        val allow = pendingSet(Lock.Feature.ALLOW_APPS, Store.set(ctx, Store.ALLOW_APPS))
        host.change(Lock.Feature.ALLOW_APPS, text(allow - pkg))
    }

    /** Adding an allow exception is weaker, so the lock may schedule it or ask for a password. */
    private fun addAllowedApps(added: Set<String>) {
        if (added.isEmpty()) return
        val allow = pendingSet(Lock.Feature.ALLOW_APPS, Store.set(ctx, Store.ALLOW_APPS))
        host.change(Lock.Feature.ALLOW_APPS, text(allow + added))
    }

    // ---- data --------------------------------------------------------------

    private data class AppEntry(val pkg: String, val label: String)

    private companion object {
        /**
         * Filtering now only toggles row visibility, so it is cheap enough to run almost
         * immediately. A short debounce just coalesces a burst of key events into one pass.
         */
        const val SEARCH_DEBOUNCE_MS = 80L
    }

    private fun installedApps(ctx: Context): List<AppEntry> = runCatching {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
            .map {
                AppEntry(
                    it.activityInfo.packageName,
                    runCatching { it.loadLabel(pm).toString() }
                        .getOrDefault(it.activityInfo.packageName)
                )
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}
