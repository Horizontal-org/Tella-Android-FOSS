package org.hzontal.shared_ui.pinview

import android.view.View
import android.widget.TextView
import org.hzontal.shared_ui.R


class OnKeyBoardClickListener (private val minLength : Int, private val mPinLockListener: PinLockListener?, private val mPinViewListener: PinViewListener) : View.OnClickListener{

    private var mPin = ""

    /**
      * Called when a view has been clicked.
      *
      * @param v The view that was clicked.
      */
     override fun onClick(v: View?) {

        when(v?.id){
            R.id.plusMinusBtn->{onNegationClicked()}
            R.id.deleteBtn->{onDeleteClicked()}
            R.id.okBtn->{onConfirmClickListener()}
            else -> {onNumberClicked((v as TextView).text.toString())}
        }

     }

    private fun  onNumberClicked(keyValue : String) {
        mPin += keyValue

        if (mPin.length == minLength) {
            mPinViewListener.onHiLightView(mPin)
        }
        mPinLockListener?.onPinChange(mPin.length, mPin)
    }

    private fun onDeleteClicked() {
        if (mPin.isNotEmpty()) {
            mPin = mPin.substring(0, mPin.length - 1)

            if (mPin.length < minLength) {
                mPinViewListener.onHiLightView(mPin)
            }
            if (mPinLockListener != null) {
                if (mPin.isEmpty()) {
                    mPinLockListener.onEmpty()
                    clearInternalPin()
                } else {
                    mPinLockListener.onPinChange(mPin.length, mPin)
                }
            }
        } else {
            mPinLockListener?.onEmpty()
        }
    }

    fun onClearClicked() {
        clearInternalPin()
        if (mPinLockListener != null) {
            mPinLockListener.onEmpty()
        }
    }

    private fun onNegationClicked() {
        if (mPin.isNotEmpty()) {
            mPin = "-($mPin)"
            mPinLockListener?.onPinChange(mPin.length, mPin)
        } else {
            mPinLockListener?.onEmpty()
        }
    }

    private fun onConfirmClickListener () {
        if (mPin.length >= minLength) {
            mPinLockListener?.onPinConfirmation(mPin)
        }
    }

    private fun clearInternalPin() {
        mPin = ""
    }
}


