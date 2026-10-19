package com.mareco.app.ui

import android.os.Bundle
import android.view.WindowManager
import com.mareco.app.Mareco
import com.mareco.app.R

/**
 * A small dialog-style screen for typing a name ("Record completed, saved as: ..."). It is a real
 * window so the keyboard always works, even though it is opened from the floating controls.
 */
@Suppress("DEPRECATION")
class PromptActivity : BaseActivity() {
    private var id = -1L
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Pal.apply(this)
        super.onCreate(savedInstanceState)
        id = intent.getLongExtra(EXTRA_ID, -1L)
        val ctl = Mareco.controller
        if (ctl == null || !ctl.promptShown(id)) {
            handled = true
            finish()
            return
        }
        val body = Dialogs.nameContent(
            this,
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.prompt_title),
            intent.getStringExtra(EXTRA_INITIAL) ?: "",
            intent.getStringExtra(EXTRA_POSITIVE) ?: getString(R.string.save),
            intent.getStringExtra(EXTRA_NEGATIVE),
            { text ->
                handled = true
                ctl.promptResult(id, text)
                finish()
            },
            {
                handled = true
                ctl.promptResult(id, null)
                finish()
            }
        )
        setContentView(dialogFrame(this, body, 440, false) { cancelPrompt() })
        styleSystemBars()
        backHandler = { cancelPrompt() }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    private fun cancelPrompt() {
        if (!handled) {
            handled = true
            Mareco.controller?.promptDismissed(id)
        }
        finish()
    }

    override fun onDestroy() {
        if (!handled) {
            handled = true
            Mareco.controller?.promptDismissed(id)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_INITIAL = "initial"
        const val EXTRA_POSITIVE = "positive"
        const val EXTRA_NEGATIVE = "negative"
    }
}
