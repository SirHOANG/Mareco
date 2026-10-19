package com.mareco.app.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

interface DialogHandle {
    fun dismiss()
}

/**
 * Something that can show a dialog: the app (a real Dialog) or the floating overlay (a window).
 * Dialog contents are built once ([Dialogs]) and work in both.
 */
interface DialogHost {
    val context: Context

    /** [onCancel] runs when the user closes it by tapping outside or pressing back. */
    fun show(content: View, cancelable: Boolean = true, scroll: Boolean = false, onCancel: (() -> Unit)? = null): DialogHandle

    fun toast(message: String)
}

@Suppress("DEPRECATION")
class ActivityDialogHost(private val activity: Activity) : DialogHost {
    override val context: Context get() = activity

    override fun show(content: View, cancelable: Boolean, scroll: Boolean, onCancel: (() -> Unit)?): DialogHandle {
        val dialog = Dialog(activity, com.mareco.app.R.style.Theme_Mareco_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var cancelledByUser = false
        val frame = dialogFrame(activity, content, 440, scroll) {
            if (cancelable) {
                cancelledByUser = true
                dialog.dismiss()
            }
        }
        dialog.setContentView(frame, ViewGroup.LayoutParams(MATCH, MATCH))
        dialog.setCancelable(cancelable)
        dialog.setOnCancelListener { cancelledByUser = true }
        dialog.setOnDismissListener {
            if (cancelledByUser) onCancel?.invoke()
        }
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        return object : DialogHandle {
            override fun dismiss() {
                if (dialog.isShowing) dialog.dismiss()
            }
        }
    }

    override fun toast(message: String) {
        activity.toast(message)
    }
}
