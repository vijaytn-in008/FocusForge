package com.focusforge.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.focusforge.app.R
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShareTimeframe(val label: String, val heroTitle: String, val reportTitle: String) {
    TODAY("Today", "TODAY'S FOCUS TIME", "Daily Focus Report"),
    WEEK_7("7 Days", "7-DAY FOCUS TIME", "7-Day Focus Report"),
    MONTH_30("30 Days", "30-DAY FOCUS TIME", "Monthly Focus Report")
}

data class ShareCardOptions(
    val timeframe: ShareTimeframe = ShareTimeframe.TODAY,
    val showName: Boolean = true,
    val showSections: Boolean = true,
    val showWeeklyTotal: Boolean = true,
    val showRating: Boolean = true
)

/**
 * Generates a high-resolution (1080x1350) social-ready FocusForge report card
 * with optional user background photo, logo, user's name, timeframe stats, and selected journeys.
 */
object FocusShareCardGenerator {

    private const val APP_INSTALL_URL = "https://github.com/vijaytn-in008/FocusForge/releases"

    fun decodePhotoFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val targetW = 1080
                    val sample = (info.size.width / targetW).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
            raw
        } catch (_: Exception) {
            null
        }
    }

    fun generateBitmap(
        context: Context,
        userName: String,
        heroTotalMinutes: Int,
        heroDeepMinutes: Int,
        heroShallowMinutes: Int,
        weekTotalMinutes: Int,
        weekDeepMinutes: Int,
        selectedSessions: List<Session>,
        options: ShareCardOptions = ShareCardOptions(),
        backgroundPhoto: Bitmap? = null
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val deepWorkRatio = if (heroTotalMinutes > 0) heroDeepMinutes.toFloat() / heroTotalMinutes.toFloat() else 0f

        // 1. Background: User's Photo (center-cropped + frosted dark scrim) OR Sanctuary Gradient
        if (backgroundPhoto != null) {
            val scale = maxOf(
                width.toFloat() / backgroundPhoto.width.toFloat(),
                height.toFloat() / backgroundPhoto.height.toFloat()
            )
            val scaledW = backgroundPhoto.width * scale
            val scaledH = backgroundPhoto.height * scale
            val left = (width - scaledW) / 2f
            val top = (height - scaledH) / 2f
            val destRect = RectF(left, top, left + scaledW, top + scaledH)
            canvas.drawBitmap(backgroundPhoto, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            // Dark translucent scrim so glass cards remain crisp & readable
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.argb(175, 11, 19, 36),
                        Color.argb(150, 15, 23, 42),
                        Color.argb(200, 11, 19, 36)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#0B1324"),
                        Color.parseColor("#0F2420"),
                        Color.parseColor("#0F172A")
                    ),
                    floatArrayOf(0f, 0.42f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    width * 0.8f, 140f, 480f,
                    intArrayOf(Color.argb(65, 16, 185, 129), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(width * 0.8f, 140f, 480f, glowPaint)
        }

        val pad = 72f

        // 2. Brand Header with App Logo
        val logoSize = 84
        val logoRect = RectF(pad, 72f, pad + logoSize, 72f + logoSize)
        val clipPath = Path().apply { addOval(logoRect, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)?.let { bg ->
            bg.setBounds(logoRect.left.toInt(), logoRect.top.toInt(), logoRect.right.toInt(), logoRect.bottom.toInt())
            bg.draw(canvas)
        }
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.let { fg ->
            val inset = -14
            fg.setBounds(
                logoRect.left.toInt() + inset,
                logoRect.top.toInt() + inset,
                logoRect.right.toInt() - inset,
                logoRect.bottom.toInt() - inset
            )
            fg.draw(canvas)
        }
        canvas.restore()

        val logoBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#34D399")
        }
        canvas.drawOval(logoRect, logoBorder)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34D399")
            textSize = 30f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("FOCUSFORGE", pad + logoSize + 24f, 108f, brandPaint)

        val dateStr = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date())
        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(dateStr, pad + logoSize + 24f, 144f, subBrandPaint)

        // 3. User Name Headline
        val displayUser = if (options.showName) userName.trim() else ""
        val titleText = if (displayUser.isNotEmpty()) "$displayUser's ${options.timeframe.reportTitle}" else options.timeframe.reportTitle
        val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(titleText, pad, 248f, headlinePaint)

        // 4. Hero Glass Card (Focus Time + Deep/Shallow Split)
        val heroRect = RectF(pad, 292f, width - pad, 650f)
        drawGlassCard(canvas, heroRect, Color.parseColor("#10B981"))

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6EE7B7")
            textSize = 26f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(options.timeframe.heroTitle, heroRect.left + 44f, heroRect.top + 66f, labelPaint)

        val heroTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 112f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(TimeUtils.formatMinutesToDuration(heroTotalMinutes), heroRect.left + 44f, heroRect.top + 190f, heroTimePaint)

        // Progress Track
        val barLeft = heroRect.left + 44f
        val barRight = heroRect.right - 44f
        val barTop = heroRect.top + 236f
        val barBottom = barTop + 20f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 255, 255, 255) }
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 10f, 10f, trackPaint)

        val clampedRatio = deepWorkRatio.coerceIn(0f, 1f)
        if (heroTotalMinutes > 0) {
            val splitX = barLeft + (barRight - barLeft) * clampedRatio
            if (clampedRatio > 0f) {
                val deepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#34D399") }
                canvas.drawRoundRect(RectF(barLeft, barTop, splitX, barBottom), 10f, 10f, deepPaint)
            }
            if (clampedRatio < 1f) {
                val shallowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
                canvas.drawRoundRect(RectF(splitX, barTop, barRight, barBottom), 10f, 10f, shallowPaint)
            }
        }

        val deepLegendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A7F3D0")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val shallowLegendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FCD34D")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Deep: ${TimeUtils.formatMinutesToDuration(heroDeepMinutes)} (${(clampedRatio * 100).toInt()}%)",
            barLeft,
            barBottom + 54f,
            deepLegendPaint
        )
        canvas.drawText(
            "Shallow: ${TimeUtils.formatMinutesToDuration(heroShallowMinutes)}",
            barRight,
            barBottom + 54f,
            shallowLegendPaint
        )

        // 5. Two-Column Stats Row
        val gap = 28f
        val colWidth = (width - pad * 2 - gap) / 2f
        val leftStatRect = RectF(pad, 686f, pad + colWidth, 886f)
        val rightStatRect = RectF(pad + colWidth + gap, 686f, width - pad, 886f)

        drawGlassCard(canvas, leftStatRect, Color.parseColor("#34D399"))
        drawGlassCard(canvas, rightStatRect, Color.parseColor("#F59E0B"))

        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 24f
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 26f
        }

        val avgRating = when {
            !options.showRating -> "Completed blocks"
            selectedSessions.isNotEmpty() -> String.format(Locale.getDefault(), "%.1f ★ avg focus", selectedSessions.map { it.focusRating }.average())
            else -> "Ready to forge"
        }
        canvas.drawText("SHARED SESSIONS", leftStatRect.left + 36f, leftStatRect.top + 56f, statLabelPaint)
        canvas.drawText("${selectedSessions.size}", leftStatRect.left + 36f, leftStatRect.top + 122f, statValPaint)
        canvas.drawText(avgRating, leftStatRect.left + 36f, leftStatRect.top + 168f, statSubPaint)

        if (options.showWeeklyTotal) {
            canvas.drawText("7-DAY FOCUS", rightStatRect.left + 36f, rightStatRect.top + 56f, statLabelPaint)
            canvas.drawText(TimeUtils.formatMinutesToDuration(weekTotalMinutes), rightStatRect.left + 36f, rightStatRect.top + 122f, statValPaint)
            canvas.drawText("${TimeUtils.formatMinutesToDuration(weekDeepMinutes)} deep", rightStatRect.left + 36f, rightStatRect.top + 168f, statSubPaint)
        } else {
            canvas.drawText("DEEP WORK RATIO", rightStatRect.left + 36f, rightStatRect.top + 56f, statLabelPaint)
            canvas.drawText("${(clampedRatio * 100).toInt()}%", rightStatRect.left + 36f, rightStatRect.top + 122f, statValPaint)
            canvas.drawText("in ${options.timeframe.label.lowercase()}", rightStatRect.left + 36f, rightStatRect.top + 168f, statSubPaint)
        }

        // 6. Session Highlights Card
        val listRect = RectF(pad, 922f, width - pad, 1220f)
        drawGlassCard(canvas, listRect, Color.parseColor("#64748B"))
        canvas.drawText("FOCUS JOURNEY HIGHLIGHTS", listRect.left + 36f, listRect.top + 54f, statLabelPaint)

        val topSessions = selectedSessions.take(3)
        if (!options.showSections) {
            val privatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 30f
            }
            canvas.drawText(
                "Journey details hidden • ${selectedSessions.size} block(s) completed",
                listRect.left + 36f,
                listRect.top + 140f,
                privatePaint
            )
        } else if (topSessions.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 30f
            }
            canvas.drawText("Select journeys to highlight them here.", listRect.left + 36f, listRect.top + 140f, emptyPaint)
        } else {
            val rowNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowDurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#34D399")
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            topSessions.forEachIndexed { idx, session ->
                val y = listRect.top + 114f + idx * 66f
                val dotColor = if (session.type == SessionType.DEEP) Color.parseColor("#34D399") else Color.parseColor("#F59E0B")
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
                canvas.drawCircle(listRect.left + 46f, y - 10f, 8f, dotPaint)

                val label = if (session.topic.isNotBlank()) "${session.subject} • ${session.topic}" else session.subject
                val trimmed = if (label.length > 34) label.take(33) + "…" else label
                canvas.drawText(trimmed, listRect.left + 72f, y, rowNamePaint)
                canvas.drawText(TimeUtils.formatMinutesToDuration(session.durationMin), listRect.right - 36f, y, rowDurPaint)
            }
        }

        // 7. Footer with App Install Link
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Get FocusForge • github.com/vijaytn-in008/FocusForge", width / 2f, 1292f, footerPaint)

        return bitmap
    }

    private fun drawGlassCard(canvas: Canvas, rect: RectF, accentColor: Int) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(48, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.argb(26, 255, 255, 255),
                    Color.argb(32, 15, 23, 42)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, 32f, 32f, fillPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(120, 255, 255, 255),
                    Color.argb(150, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.argb(40, 255, 255, 255)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, 32f, 32f, borderPaint)
    }

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, "focusforge_daily_report.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Forged my focus today with FocusForge! Get the app here: $APP_INSTALL_URL"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Focus Report"))
    }
}
