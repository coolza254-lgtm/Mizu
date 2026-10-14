package com.example.mizu.quick

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.mizu.MainActivity
import com.example.mizu.R
import com.example.mizu.container
import com.example.mizu.core.Bottle
import com.example.mizu.util.localized
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Intent extras understood by [MainActivity] (tiles, launcher shortcuts). */
object QuickActions {
    const val EXTRA_OPEN = "mizu_open"
    const val OPEN_WEIGH = "weigh"
    const val EXTRA_ACTION = "mizu_action"
    const val ACTION_FILL = "fill"

    fun openWeigh(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_WEIGH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun grouped(v: Int): String = String.format(Locale.US, "%,d", v)
}

private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private suspend fun Context.localizedCtx(): Context = localized(container.settings.settings.first().language)

/** Quick panel: "Update water" opens the weigh screen. */
class UpdateWaterTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val ctx = localizedCtx()
            val bottle = container.repository.currentBottle()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = ctx.getString(R.string.tile_update_water)
                    state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = bottle.statusText(ctx)
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = QuickActions.openWeigh(this)
        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}

/** Quick panel: "Refill bottle" sets the bottle in use back to its full amount after a confirm dialog, without weighing or opening the app. */
class FillBottleTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    /** Asks first (a stray tap in the quick panel is easy), then refills. */
    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val ctx = localizedCtx()
            val bottle = container.repository.currentBottle()
            withContext(Dispatchers.Main) {
                val capacity = bottle?.capacityMl
                if (bottle == null || capacity == null) {
                    Toast.makeText(applicationContext, ctx.getString(R.string.tile_needs_weigh), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val dialog = AlertDialog.Builder(this@FillBottleTile, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle(ctx.getString(R.string.fill_confirm_title))
                    .setMessage(ctx.getString(R.string.fill_confirm_body, bottle.name, QuickActions.grouped(capacity)))
                    .setPositiveButton(ctx.getString(R.string.fill_confirm_ok)) { _, _ -> fill(ctx) }
                    .setNegativeButton(ctx.getString(R.string.cancel), null)
                    .create()
                showDialog(dialog)
            }
        }
    }

    private fun fill(ctx: Context) {
        tileScope.launch {
            val filled = container.repository.fillCurrentBottle()
            withContext(Dispatchers.Main) {
                val msg = if (filled != null) {
                    ctx.getString(R.string.toast_filled, QuickActions.grouped(filled.capacityMl ?: 0))
                } else {
                    ctx.getString(R.string.tile_needs_weigh)
                }
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun refresh() {
        tileScope.launch {
            val ctx = localizedCtx()
            val bottle = container.repository.currentBottle()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    label = ctx.getString(R.string.tile_fill_bottle)
                    state = if (bottle?.capacityMl != null) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
                    icon = Icon.createWithResource(this@FillBottleTile, R.drawable.ic_tile_fill)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = bottle.statusText(ctx)
                    updateTile()
                }
            }
        }
    }
}

private fun Bottle?.statusText(ctx: Context): String {
    val water = this?.currentWaterG ?: return ctx.getString(R.string.tile_needs_weigh)
    val capacity = this.capacityMl
    return if (capacity != null) {
        ctx.getString(R.string.tile_water_left, QuickActions.grouped(water), QuickActions.grouped(capacity))
    } else {
        "${QuickActions.grouped(water)} ml"
    }
}
