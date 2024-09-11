package rs.readahead.washington.mobile.views.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.hzontal.shared_ui.utils.CrashlyticsUtil;
import com.hzontal.tella_vault.VaultFile;
import com.simplify.ink.InkView;

import java.io.ByteArrayOutputStream;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import org.hzontal.shared_ui.utils.DialogUtils;

import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.databinding.ActivitySignatureBinding;
import rs.readahead.washington.mobile.mvp.contract.ISignaturePresenterContract;
import rs.readahead.washington.mobile.mvp.presenter.SignaturePresenter;
import rs.readahead.washington.mobile.util.DialogsUtil;
import rs.readahead.washington.mobile.views.base_ui.BaseLockActivity;


public class SignatureActivity extends BaseLockActivity implements
        ISignaturePresenterContract.IView {

    public static final String MEDIA_FILE_KEY = "mfk";

    Toolbar toolbar;
    InkView ink;

    private ProgressDialog progressDialog;
    private SignaturePresenter presenter;

    private ActivitySignatureBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setManualOrientation(true);

        super.onCreate(savedInstanceState);

        binding = ActivitySignatureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();

        presenter = new SignaturePresenter(this);

        setupToolbar();
        setupSignaturePad();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.signature_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_item_clear) {
            ink.clear();
            return true;
        }

        if (id == R.id.menu_item_save) {
            saveSignature();
            return true;
        }

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        destroyPresenter();
        super.onDestroy();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.collect_form_signature_app_bar);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white);
        }
    }

    public void saveSignature() {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            if (ink.getBitmap((getResources().getColor(R.color.wa_white))).compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                presenter.addPngImage(stream.toByteArray());
            }
        } catch (Exception exception) {
            CrashlyticsUtil.Companion.handleThrowable(exception);
        }
    }

    @Override
    public void onAddingStart() {
        progressDialog = DialogsUtil.showProgressDialog(this, getString(R.string.gallery_dialog_expl_encrypting));
    }

    @Override
    public void onAddingEnd() {
        hideProgressDialog();
        DialogUtils.showBottomMessage(
                this,
                getString(R.string.gallery_toast_file_encrypted),
                false
        );
    }

    @Override
    public void onAddSuccess(VaultFile vaultFile) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(MEDIA_FILE_KEY, vaultFile));
        finish();
    }

    @Override
    public void onAddError(Throwable error) {
        DialogUtils.showBottomMessage(
                this,
                getString(R.string.collect_form_signature_toast_fail_saving),
                true
        );
    }

    @Override
    public Context getContext() {
        return this;
    }

    private void setupSignaturePad() {
        ink.setColor(getResources().getColor(android.R.color.black));
        ink.setMinStrokeWidth(1.5f);
        ink.setMaxStrokeWidth(6f);
    }

    private void destroyPresenter() {
        if (presenter != null) {
            presenter.destroy();
        }
        presenter = null;
    }

    private void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void initView() {
        toolbar = binding.toolbar;
        ink = binding.content.ink;
    }
}
