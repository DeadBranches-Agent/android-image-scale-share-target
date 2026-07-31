package ca.urbanlight.imagescale.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ca.urbanlight.imagescale.MainActivity
import ca.urbanlight.imagescale.data.AppConfig
import ca.urbanlight.imagescale.icons.IconIndex

/** Mirrors the visible share targets into the share sheet as dynamic sharing shortcuts. */
class ShortcutPublisher(private val context: Context) {

    fun publish(config: AppConfig): Int {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val visible = config.visibleTargets
        val published = visible.take(max)
        val shortcuts = published.map { target ->
            ShortcutInfoCompat.Builder(context, target.id)
                .setShortLabel(target.label.ifBlank { "Scaled JPG" })
                .setLongLabel(target.label.ifBlank { "Scaled JPG" })
                .setIcon(renderIcon(target.iconName))
                .setCategories(setOf(SHARE_CATEGORY))
                // Launcher long-press fallback; the share sheet supplies the real SEND intent.
                .setIntent(
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                )
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        return visible.size - published.size
    }

    private fun renderIcon(iconName: String): IconCompat {
        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND_COLOR }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val drawable = requireNotNull(context.getDrawable(IconIndex.resFor(iconName)))
        drawable.setTint(Color.WHITE)
        val inset = (size * 0.22f).toInt()
        drawable.setBounds(inset, inset, size - inset, size - inset)
        drawable.draw(canvas)
        return IconCompat.createWithBitmap(bitmap)
    }

    companion object {
        const val SHARE_CATEGORY = "ca.urbanlight.imagescale.SHARE_TARGET"
        private const val BACKGROUND_COLOR = 0xFF1E5F74.toInt()
    }
}
