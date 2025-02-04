package org.hzontal.shared_ui.switches

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Checkable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.hzontal.shared_ui.R


class TellaSwitchWithMessage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), Checkable {

    @StringRes
    private var titleText: Int = -1

    @StringRes
    private var explainText: Int = -1

    @StringRes
    private var labelText: Int = -1

    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var learnMoreTextView: TextView
    private lateinit var labelTextView: TextView
    lateinit var mSwitch: SwitchCompat

    private var mChecked = false
    private var isDisabledTheme = false
    private var isSwitchVisible = false

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_with_text, this, true)
        initView()
        extractAttributes(attrs, defStyleAttr)
    }

    private fun initView() {
        titleTextView = findViewById(R.id.titleTV)
        messageTextView = findViewById(R.id.explainTV)
        learnMoreTextView = findViewById(R.id.learnMoreTV)
        labelTextView = findViewById(R.id.labelTv)
        mSwitch = findViewById(R.id.mSwitch)
        titleTextView.labelFor = mSwitch.id
        //    background = ContextCompat.getDrawable(context, R.drawable.rounded_light_purple_background)
    }


    private fun extractAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        attrs?.let {
            val typedArray = context
                .obtainStyledAttributes(
                    attrs,
                    R.styleable.TellaSwitchWithMessage,
                    defStyleAttr,
                    defStyleAttr
                )

            try {
                titleText =
                    typedArray.getResourceId(R.styleable.TellaSwitchWithMessage_titleText, -1)
                explainText =
                    typedArray.getResourceId(R.styleable.TellaSwitchWithMessage_explainText, -1)
                mChecked =
                    typedArray.getBoolean(R.styleable.TellaSwitchWithMessage_is_checked, false)
                mSwitch.isChecked = mChecked

                isDisabledTheme =
                    typedArray.getBoolean(R.styleable.TellaSwitchWithMessage_is_disabled, false)

                labelText =
                    typedArray.getResourceId(R.styleable.TellaSwitchWithMessage_labelText, -1)

                isSwitchVisible =
                    typedArray.getBoolean(R.styleable.TellaSwitchWithMessage_isSwitchVisible, true)
            } finally {
                typedArray.recycle()
            }
        }
        bindView()
    }

    private fun bindView() {
        setTextAndVisibility(titleText, titleTextView)
        setTextAndVisibility(explainText, messageTextView)
        setTextAndVisibility(labelText, labelTextView)
        setSwitchVisibility()
        isDisabledTheme(isDisabledTheme)
    }

    private fun setTextAndVisibility(text: Int, textView: TextView) {
        if (text != -1) {
            textView.visibility = View.VISIBLE
            textView.text = context.getString(text)
        }
    }

    private fun setSwitchVisibility() {
        mSwitch.isVisible = isSwitchVisible
    }

    fun setTextAndAction(textResource: Int, action: () -> Unit) {
        with(learnMoreTextView) {
            visibility = View.VISIBLE
            text = context.getString(textResource)
            setOnClickListener { action() }
        }
    }

    fun isDisabledTheme(isDisabled: Boolean) {
        if (isDisabled) {
            mSwitch.trackTintList =
                ContextCompat.getColorStateList(context, R.color.switch_track_color_disabled)
        } else {
            mSwitch.trackTintList =
                ContextCompat.getColorStateList(context, R.color.switch_track_color)
        }
    }

    fun setExplainText(textResource: Int) {
        with(messageTextView) {
            visibility = View.VISIBLE
            text = context.getString(textResource)
        }
    }

    override fun isChecked(): Boolean {
        return mSwitch.isChecked
    }

    override fun toggle() {
        mSwitch.isChecked = !mChecked
    }

    override fun setChecked(checked: Boolean) {
        if (mChecked != checked) {
            mChecked = checked
        }
    }
}