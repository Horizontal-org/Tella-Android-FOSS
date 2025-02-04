package org.hzontal.shared_ui.utils

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.hzontal.shared_ui.R
import org.hzontal.shared_ui.databinding.LayoutBottomMessageWithButtonBinding

object DialogUtils {

    @JvmStatic
    fun showBottomMessage(
        context: Activity,
        msg: String,
        isError: Boolean,
        duration: Long = 2000L
    ) {
        showBottomMessage(
            context,
            msg,
            if (isError) R.color.light_red else R.color.tigers_eye,
            duration
        )
    }

    @JvmStatic
    fun showBottomMessage(context: Activity, msg: String, isError: Boolean) {
        showBottomMessage(
            context,
            msg,
            if (isError) R.color.light_red else R.color.tigers_eye,
            2000L
        )
    }

    @JvmStatic
    private fun showBottomMessage(context: Activity, msg: String, colorRes: Int, duration: Long) {
        val container = context.findViewById<ViewGroup>(android.R.id.content)
        val view =
            LayoutInflater.from(context).inflate(R.layout.layout_bottom_message, container, false)
        val textViewMsg = view.findViewById<TextView>(R.id.txv_msg)
        textViewMsg.text = msg
        container.addView(view)

        view.requestFocus()
        view.announceForAccessibility(msg)

        view.alpha = 0f
        view.animate().alphaBy(1f).setDuration(500).withEndAction {
            if (view.isAttachedToWindow) {
                view.animate().alpha(0f).setStartDelay(2000).duration = 500
            }
        }
    }

    @JvmStatic
    fun showBottomMessageWithButton(context: Activity, msg: String, onBtnClick: () -> Unit) {
        val container = context.findViewById<ViewGroup>(android.R.id.content)

        // Create a FrameLayout to hold both the overlay and the dialog content
        val frameLayout = FrameLayout(context)
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Create a transparent background overlay
        val overlay = View(context)
        overlay.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
        overlay.alpha = 0.5f // Adjust the alpha value as needed
        overlay.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.addView(overlay)

        // Create a transparent view to disable interaction with the views behind
        val disableView = View(context)
        disableView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        disableView.isClickable = true
        disableView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.addView(disableView)
        val binding = LayoutBottomMessageWithButtonBinding.inflate(
            LayoutInflater.from(context),
            container,
            false
        )
        binding.txvMsg.text = msg
        frameLayout.addView(binding.root)
        binding.root.requestFocus()
        binding.root.announceForAccessibility(msg)

        // Set up the "OK" button
        binding.btnOk.setOnClickListener {
            // Dismiss the message when the button is clicked
            container.removeView(frameLayout)
            onBtnClick.invoke()
        }
        container.addView(frameLayout)
    }
}
