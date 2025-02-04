package rs.readahead.washington.mobile.views.custom;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageButton;

import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.data.sharedpref.Preferences;
import rs.readahead.washington.mobile.util.DialogsUtil;


public class CameraPreviewAnonymousButton extends AppCompatImageButton {
    public CameraPreviewAnonymousButton(Context context) {
        this(context, null);
    }

    public CameraPreviewAnonymousButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreviewAnonymousButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        displayDrawable();
        setOnClickListener(view -> DialogsUtil.showMetadataSwitchDialog(getContext(), CameraPreviewAnonymousButton.this));
    }

    public void displayDrawable() {
        if (Preferences.isAnonymousMode()) {
            displayDisable();
        } else {
            displayEnable();
        }
    }

    private void displayEnable() {
        setImageResource(R.drawable.ic_location_searching_black);
    }

    private void displayDisable() {
        setImageResource(R.drawable.ic_location_disabled_white);
    }
}