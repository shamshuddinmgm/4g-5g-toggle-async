package com.shams.srk.nrtoggle.network

import android.content.Context
import android.provider.Settings
import com.shams.srk.nrtoggle.shizuku.ShizukuShell
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class NetworkMode {
    LTE,
    NR
}

/**
 * HyperOS-calibrated (Redmi Note 13 Pro+):
 * - Prefer LTE → mode 9, mask MASK_4G
 * - Prefer 5G  → mode 27, mask MASK_5G
 *
 * Reads prefer [Settings.Global] (cheap, no shell). Writes via Shizuku `cmd phone`.
 */
object NetworkModeController {

    const val MASK_4G = "01001101001110000111"
    const val MASK_5G = "11001111101111111111"

    /** HyperOS Prefer LTE */
    private const val MODE_PREFER_LTE = 9

    /** HyperOS Prefer 5G */
    private const val MODE_PREFER_5G = 27

    private val busy = AtomicBoolean(false)
    private val lastKnown = AtomicReference<NetworkMode?>(null)

    fun isBusy(): Boolean = busy.get()

    fun lastKnownMode(): NetworkMode? = lastKnown.get()

    /**
     * Fast path: Global preferred_network_mode* (no Shizuku).
     * Falls back to shell only when Settings look empty/ambiguous and Shizuku is ready.
     */
    fun readMode(context: Context, allowShellFallback: Boolean = true): NetworkMode? {
        readFromSettings(context)?.let {
            lastKnown.set(it)
            return it
        }
        if (!allowShellFallback || !ShizukuShell.isReady()) {
            return lastKnown.get()
        }
        val fromShell = readFromShell()
        if (fromShell != null) lastKnown.set(fromShell)
        return fromShell ?: lastKnown.get()
    }

    fun toggle(context: Context): ToggleResult {
        if (!busy.compareAndSet(false, true)) return ToggleResult.Busy
        return try {
            if (!ShizukuShell.isReady()) return ToggleResult.NeedShizuku
            val current = readMode(context, allowShellFallback = true) ?: NetworkMode.NR
            val target = if (current == NetworkMode.NR) NetworkMode.LTE else NetworkMode.NR
            apply(context, target)
        } finally {
            busy.set(false)
        }
    }

    fun setMode(context: Context, mode: NetworkMode): ToggleResult {
        if (!busy.compareAndSet(false, true)) return ToggleResult.Busy
        return try {
            if (!ShizukuShell.isReady()) return ToggleResult.NeedShizuku
            apply(context, mode)
        } finally {
            busy.set(false)
        }
    }

    private fun apply(context: Context, target: NetworkMode): ToggleResult {
        val mask = if (target == NetworkMode.LTE) MASK_4G else MASK_5G
        var anyOk = false
        var lastErr = "set failed"

        // Dual-SIM: push both slots; skip empty subs without probing first (saves 2 shell reads).
        for (slot in 0..1) {
            val set = ShizukuShell.exec(
                "cmd phone set-allowed-network-types-for-users -s $slot $mask"
            )
            val out = (set.stdout + "\n" + set.stderr).trim()
            when {
                set.isSuccess || out.contains("completed", ignoreCase = true) -> anyOk = true
                out.contains("No valid subscription", ignoreCase = true) -> Unit
                else -> lastErr = out.ifBlank { "set failed slot $slot" }
            }
        }
        if (!anyOk) return ToggleResult.Failed(lastErr)

        val expectedPref = if (target == NetworkMode.LTE) MODE_PREFER_LTE else MODE_PREFER_5G
        val verified = awaitSettingsMode(context, target, expectedPref)
            ?: readMode(context, allowShellFallback = true)

        if (verified != null && verified != target) {
            return ToggleResult.Failed("Radio did not switch to ${target.name}")
        }
        val finalMode = verified ?: target
        lastKnown.set(finalMode)
        return ToggleResult.Ok(target, finalMode)
    }

    private fun awaitSettingsMode(
        context: Context,
        target: NetworkMode,
        expectedPref: Int
    ): NetworkMode? {
        // HyperOS usually syncs preferred_network_mode within ~100–300ms; poll Settings only.
        val deadline = System.nanoTime() + 450_000_000L
        while (System.nanoTime() < deadline) {
            val prefs = readPrefInts(context)
            if (prefs.any { it == expectedPref } || modeFromPrefs(prefs) == target) {
                return target
            }
            try {
                Thread.sleep(40)
            } catch (_: InterruptedException) {
                break
            }
        }
        return modeFromPrefs(readPrefInts(context))
    }

    private fun readFromSettings(context: Context): NetworkMode? =
        modeFromPrefs(readPrefInts(context))

    private fun readPrefInts(context: Context): List<Int> {
        val keys = listOf(
            "preferred_network_mode",
            "preferred_network_mode1",
            "preferred_network_mode2"
        )
        val out = ArrayList<Int>(6)
        val cr = context.contentResolver
        for (key in keys) {
            val raw = try {
                Settings.Global.getString(cr, key)
            } catch (_: Throwable) {
                null
            } ?: continue
            for (part in raw.split(',', ';', ' ')) {
                val n = part.trim().toIntOrNull() ?: continue
                out.add(n)
            }
        }
        return out
    }

    private fun modeFromPrefs(prefs: List<Int>): NetworkMode? {
        if (prefs.isEmpty()) return null
        // Explicit HyperOS Prefer values win.
        if (prefs.any { it == MODE_PREFER_5G }) return NetworkMode.NR
        if (prefs.any { it == MODE_PREFER_LTE }) return NetworkMode.LTE
        // AOSP NR family typically starts at 23 (NR_ONLY).
        if (prefs.any { it in 23..33 }) return NetworkMode.NR
        // Common LTE / global-without-NR modes.
        if (prefs.any { it in setOf(8, 9, 10, 11, 12, 15, 17, 19, 20, 22) }) return NetworkMode.LTE
        return null
    }

    private fun readFromShell(): NetworkMode? {
        val outputs = (0..1).mapNotNull { slot ->
            val r = ShizukuShell.exec("cmd phone get-allowed-network-types-for-users -s $slot")
            val text = (r.stdout + "\n" + r.stderr).trim()
            if (text.isBlank() || text.contains("No valid subscription", ignoreCase = true)) null
            else text
        }
        if (outputs.isEmpty()) return null
        return if (outputs.any { rawIndicatesNr(it) }) NetworkMode.NR else NetworkMode.LTE
    }

    private fun rawIndicatesNr(raw: String): Boolean {
        val tokens = raw.split('|', ',', ' ', '\n', '\t')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        if (tokens.any { it == "NR" || it == "5G" || it.startsWith("NR_") }) return true
        return Regex("""(^|[|\s,])NR($|[|\s,])""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
    }

    sealed class ToggleResult {
        data class Ok(val target: NetworkMode, val verified: NetworkMode?) : ToggleResult()
        data object Busy : ToggleResult()
        data object NeedShizuku : ToggleResult()
        data class Failed(val reason: String) : ToggleResult()
    }
}
