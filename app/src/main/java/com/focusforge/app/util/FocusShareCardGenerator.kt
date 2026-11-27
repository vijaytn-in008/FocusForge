package com.focusforge.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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

/**
 * Generates a high-resolution (1080x1350) social-ready FocusForge daily report card
 * with the app logo, user's name, daily focus time, Deep/Shallow split, and session highlights.
 */
object FocusShareCardGenerator {

    fun generateBitmap(
        context: Context,
        userName: String,
        todayTotalMinutes: Int,
        todayDeepMinutes: Int,
        todayShallowMinutes: Int,
        deepWorkRatio: Float,
        weekTotalMinutes: Int,
        weekDeepMinutes: Int,
        todaySessions: List<Session>
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep Sanctuary Slate + Emerald Gradient Background
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

        // Subtle emerald top glow orb
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                width * 0.8f, 140f, 480f,
                intArrayOf(Color.argb(65, 16, 185, 129), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.8f, 140f, 480f, glowPaint)

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
        val displayUser = userName.trim()
        val titleText = if (displayUser.isNotEmpty()) "$displayUser's Focus Report" else "Daily Focus Report"
        val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(titleText, pad, 248f, headlinePaint)

        // 4. Hero Glass Card (Daily Focus Time + Deep/Shallow Split)
        val heroRect = RectF(pad, 292f, width - pad, 650f)
        drawGlassCard(canvas, heroRect, Color.parseColor("#10B981"))

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6EE7B7")
            textSize = 26f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TODAY'S FOCUS TIME", heroRect.left + 44f, heroRect.top + 66f, labelPaint)

        val heroTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 112f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(TimeUtils.formatMinutesToDuration(todayTotalMinutes), heroRect.left + 44f, heroRect.top + 190f, heroTimePaint)

        // Progress Track
        val barLeft = heroRect.left + 44f
        val barRight = heroRect.right - 44f
        val barTop = heroRect.top + 236f
        val barBottom = barTop + 20f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 255, 255, 255) }
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 10f, 10f, trackPaint)

        val clampedRatio = deepWorkRatio.coerceIn(0f, 1f)
        if (todayTotalMinutes > 0) {
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
            "Deep: ${TimeUtils.formatMinutesToDuration(todayDeepMinutes)} (${(clampedRatio * 100).toInt()}%)",
            barLeft,
            barBottom + 54f,
            deepLegendPaint
        )
        canvas.drawText(
            "Shallow: ${TimeUtils.formatMinutesToDuration(todayShallowMinutes)}",
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
            color = Color.parseColor("#94A3B8")
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
            color = Color.parseColor("#CBD5E1")
            textSize = 26f
        }

        val avgRating = if (todaySessions.isNotEmpty()) {
            String.format(Locale.getDefault(), "%.1f ★ avg focus", todaySessions.map { it.focusRating }.average())
        } else {
            "Ready to forge"
        }
        canvas.drawText("SESSIONS TODAY", leftStatRect.left + 36f, leftStatRect.top + 56f, statLabelPaint)
        canvas.drawText("${todaySessions.size}", leftStatRect.left + 36f, leftStatRect.top + 122f, statValPaint)
        canvas.drawText(avgRating, leftStatRect.left + 36f, leftStatRect.top + 168f, statSubPaint)

        canvas.drawText("7-DAY FOCUS", rightStatRect.left + 36f, rightStatRect.top + 56f, statLabelPaint)
        canvas.drawText(TimeUtils.formatMinutesToDuration(weekTotalMinutes), rightStatRect.left + 36f, rightStatRect.top + 122f, statValPaint)
        canvas.drawText("${TimeUtils.formatMinutesToDuration(weekDeepMinutes)} deep", rightStatRect.left + 36f, rightStatRect.top + 168f, statSubPaint)

        // 6. Session Highlights Card
        val listRect = RectF(pad, 922f, width - pad, 1220f)
        drawGlassCard(canvas, listRect, Color.parseColor("#64748B"))
        canvas.drawText("TODAY'S FOCUS SECTIONS", listRect.left + 36f, listRect.top + 54f, statLabelPaint)

        val topSessions = todaySessions.take(3)
        if (topSessions.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 30f
            }
            canvas.drawText("No sessions logged yet today — start your first block!", listRect.left + 36f, listRect.top + 140f, emptyPaint)
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

        // 7. Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Forged on-device with FocusForge • Digital Sanctuary", width / 2f, 1292f, footerPaint)

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
            putExtra(Intent.EXTRA_TEXT, "My daily focus report from FocusForge!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Focus Report"))
    }
}
