package android.MetaCore

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.reflect.Field
import org.lsposed.lsparanoid.Obfuscate

/**
 * Self-contained cinematic activation/expiry dialog for ParallaxElite.
 *
 * The old implementation depended on a WebView, remote JavaScript and external
 * Lottie assets. That made an important security-state UI dependent on network
 * availability. This implementation is fully native and ships inside the AAR.
 */
@Obfuscate
object AdvancedPopupHelper {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeDialog: Dialog? = null

    private const val GOLD = "#FFD166"
    private const val GOLD_SOFT = "#B58A35"
    private const val RED = "#FF3B5C"
    private const val WHITE = "#F8FAFC"
    private const val MUTED = "#A8B2C7"
    private const val DEEP = "#07090F"
    private const val PANEL = "#111521"

    private fun getTopActivity(): Activity? {
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentAT = atClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField: Field = atClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(currentAT) as Map<*, *>

            for (record in activities.values) {
                if (record == null) continue
                val recordClass = record::class.java
                val pausedField = recordClass.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!pausedField.getBoolean(record)) {
                    val activityField = recordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(record) as? Activity
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun showAuto() {
        showAuto(nk.getServerMessage())
    }

    @JvmStatic
    fun showAuto(reason: String?) {
        val act = getTopActivity() ?: return
        if (act.isFinishing || act.isDestroyed) return
        handler.post { showPopup(act, reason.orEmpty()) }
    }

    private fun showPopup(act: Activity, rawReason: String) {
        if (act.isFinishing || act.isDestroyed) return

        try {
            activeDialog?.let {
                if (it.isShowing) it.dismiss()
            }

            val dialog = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)

            val reason = sanitizeReason(rawReason)
            val expired = reason.contains("expired", ignoreCase = true)
                || reason.contains("expiry", ignoreCase = true)

            val outer = FrameLayout(act).apply {
                setPadding(dp(act, 1), dp(act, 1), dp(act, 1), dp(act, 1))
                background = roundedGradient(
                    30f,
                    intArrayOf(Color.parseColor(GOLD), Color.parseColor(RED), Color.parseColor("#7C3AED")),
                    GradientDrawable.Orientation.TL_BR,
                )
                elevation = dp(act, 24).toFloat()
            }

            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 20))
                background = roundedGradient(
                    29f,
                    intArrayOf(Color.parseColor(DEEP), Color.parseColor(PANEL), Color.parseColor("#090B12")),
                    GradientDrawable.Orientation.TOP_BOTTOM,
                )
            }
            outer.addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))

            val eyebrow = text(act, "PARALLAX ELITE  •  SECURE PREMIERE", 10f, GOLD, true).apply {
                letterSpacing = 0.16f
                gravity = Gravity.CENTER
            }
            card.addView(eyebrow, matchWrap())

            val lightBar = View(act).apply {
                background = roundedSolid(999f, Color.parseColor(GOLD))
                alpha = 0.9f
            }
            card.addView(lightBar, LinearLayout.LayoutParams(dp(act, 58), dp(act, 3)).apply {
                topMargin = dp(act, 12)
            })

            val iconFrame = FrameLayout(act).apply {
                background = roundedSolid(999f, Color.parseColor(if (expired) "#22FF3B5C" else "#227C3AED"))
            }
            card.addView(iconFrame, LinearLayout.LayoutParams(dp(act, 76), dp(act, 76)).apply {
                topMargin = dp(act, 18)
            })

            val icon = text(act, if (expired) "✦" else "◆", 34f, if (expired) RED else GOLD, true).apply {
                gravity = Gravity.CENTER
            }
            iconFrame.addView(icon, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            val title = text(
                act,
                if (expired) "ACCESS EXPIRED" else "ACCESS LOCKED",
                26f,
                WHITE,
                true,
            ).apply {
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
            }
            card.addView(title, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 18) })

            val subtitle = text(
                act,
                if (expired) "THE SESSION HAS ENDED" else "SECURE ACTIVATION REQUIRED",
                11f,
                if (expired) RED else GOLD,
                true,
            ).apply {
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
            }
            card.addView(subtitle, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 5) })

            val messageBox = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 15), dp(act, 13), dp(act, 15), dp(act, 13))
                background = roundedStroke(
                    18f,
                    Color.parseColor("#B3121723"),
                    Color.parseColor("#33FFD166"),
                    dp(act, 1),
                )
            }
            card.addView(messageBox, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 18) })

            messageBox.addView(text(act, "SECURITY STATUS", 9f, GOLD, true).apply {
                letterSpacing = 0.13f
            }, matchWrap())
            messageBox.addView(text(
                act,
                reason.ifBlank {
                    if (expired) "Your Elite license is no longer active. Renew access to continue."
                    else "ParallaxElite could not validate this session."
                },
                12f,
                WHITE,
                false,
            ).apply {
                setLineSpacing(dp(act, 2).toFloat(), 1f)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 7) })

            val deviceBox = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(act, 15), dp(act, 12), dp(act, 15), dp(act, 12))
                background = roundedSolid(16f, Color.parseColor("#9910141D"))
            }
            card.addView(deviceBox, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 12) })

            deviceBox.addView(infoRow(act, "DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}"))
            deviceBox.addView(infoRow(act, "ANDROID", "${Build.VERSION.RELEASE}  •  API ${Build.VERSION.SDK_INT}"))
            deviceBox.addView(infoRow(act, "CORE", "ParallaxElite / Protected"))

            val close = text(act, if (expired) "CLOSE PREMIERE" else "CLOSE", 13f, DEEP, true).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                letterSpacing = 0.08f
                background = roundedGradient(
                    16f,
                    intArrayOf(Color.parseColor("#FFE39A"), Color.parseColor(GOLD), Color.parseColor("#F3B934")),
                    GradientDrawable.Orientation.LEFT_RIGHT,
                )
                setOnClickListener {
                    if (dialog.isShowing) dialog.dismiss()
                }
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        }
                    }
                    false
                }
            }
            card.addView(close, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(act, 54),
            ).apply { topMargin = dp(act, 18) })

            card.addView(text(act, "PARALLAX ELITE  •  CINEMATIC SECURITY", 8f, MUTED, true).apply {
                gravity = Gravity.CENTER
                letterSpacing = 0.11f
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(act, 14) })

            dialog.setContentView(outer)
            dialog.setOnDismissListener {
                if (activeDialog === dialog) activeDialog = null
            }
            dialog.show()
            activeDialog = dialog

            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.84f)
                setGravity(Gravity.CENTER)
                val maxWidth = (act.resources.displayMetrics.widthPixels * 0.90f).toInt()
                setLayout(minOf(dp(act, 370), maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
            }

            outer.alpha = 0f
            outer.scaleX = 0.92f
            outer.scaleY = 0.92f
            outer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .start()

            ObjectAnimator.ofFloat(iconFrame, View.ALPHA, 0.58f, 1f).apply {
                duration = 1150L
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } catch (_: Throwable) {
        }
    }

    private fun sanitizeReason(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank()) return ""
        // Keep the user-facing dialog useful without exposing cryptographic or
        // server internals that belong in logs/panel audit trails.
        return when {
            trimmed.contains("PACKAGE_MISMATCH", true) -> "This app edition is not yet linked to the Elite license."
            trimmed.contains("DEVICE_KEY_MISMATCH", true) -> "This device needs a secure license rebind."
            trimmed.contains("SIGNATURE_MISMATCH", true) -> "The installed release is not authorized for this Elite license."
            trimmed.contains("LICENSE_EXPIRED", true) -> "Your Elite license has expired. Renew access to continue."
            trimmed.contains("INVALID_LICENSE", true) -> "The Elite activation key is not valid."
            trimmed.contains("RATE_LIMITED", true) -> "Too many activation attempts. Please try again shortly."
            trimmed.contains("lease expired", true) -> "Your verified Elite session has expired. Reconnect to renew access."
            trimmed.length > 180 -> trimmed.take(180) + "…"
            else -> trimmed
        }
    }

    private fun infoRow(act: Activity, label: String, value: String): LinearLayout {
        return LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(act, 3), 0, dp(act, 3))
            addView(text(act, label, 9f, MUTED, true).apply {
                letterSpacing = 0.08f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(act, value, 10f, WHITE, true).apply {
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.7f))
        }
    }

    private fun text(
        act: Activity,
        value: String,
        sizeSp: Float,
        colorHex: String,
        bold: Boolean,
    ): TextView {
        return TextView(act).apply {
            text = value
            textSize = sizeSp
            setTextColor(Color.parseColor(colorHex))
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }
    }

    private fun roundedSolid(radiusDp: Float, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * 3f
            setColor(color)
        }
    }

    private fun roundedGradient(
        radiusDp: Float,
        colors: IntArray,
        orientation: GradientDrawable.Orientation,
    ): GradientDrawable {
        return GradientDrawable(orientation, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * 3f
        }
    }

    private fun roundedStroke(
        radiusDp: Float,
        fill: Int,
        stroke: Int,
        strokeWidth: Int,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * 3f
            setColor(fill)
            setStroke(strokeWidth, stroke)
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(act: Activity, value: Int): Int {
        return (value * act.resources.displayMetrics.density + 0.5f).toInt()
    }
}
