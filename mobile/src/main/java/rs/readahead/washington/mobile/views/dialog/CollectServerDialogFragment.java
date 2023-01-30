package rs.readahead.washington.mobile.views.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.security.ProviderInstaller;
import com.google.android.material.textfield.TextInputLayout;

import org.jetbrains.annotations.NotNull;

import java.net.UnknownHostException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.data.http.HttpStatus;
import rs.readahead.washington.mobile.domain.entity.IErrorBundle;
import rs.readahead.washington.mobile.domain.entity.collect.CollectServer;
import rs.readahead.washington.mobile.mvp.contract.ICheckOdkServerContract;
import rs.readahead.washington.mobile.mvp.presenter.CheckOdkServerPresenter;
import rs.readahead.washington.mobile.views.custom.PanelToggleButton;
import timber.log.Timber;


public class CollectServerDialogFragment extends AppCompatDialogFragment implements
        ICheckOdkServerContract.IView {
    public static final String TAG = CollectServerDialogFragment.class.getSimpleName();

    private static final String TITLE_KEY = "tk";
    private static final String ID_KEY = "ik";
    private static final String OBJECT_KEY = "ok";

    @BindView(R.id.name_layout)
    TextInputLayout nameLayout;
    @BindView(R.id.name)
    EditText name;
    @BindView(R.id.url_layout)
    TextInputLayout urlLayout;
    @BindView(R.id.url)
    EditText url;
    @BindView(R.id.username_layout)
    TextInputLayout usernameLayout;
    @BindView(R.id.username)
    EditText username;
    @BindView(R.id.password_layout)
    TextInputLayout passwordLayout;
    @BindView(R.id.password)
    EditText password;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.server_input)
    View serverInput;
    @BindView(R.id.cancel)
    TextView cancel;
    @BindView(R.id.next)
    TextView next;
    @BindView(R.id.back)
    ImageView back;
    @BindView(R.id.toggle_button)
    PanelToggleButton advancedToggle;
    @BindView(R.id.advanced_panel)
    ViewGroup advancedPanel;

    private Unbinder unbinder;
    private boolean validated = true;
    private CheckOdkServerPresenter presenter;

    private boolean securityProviderUpgradeAttempted = false;

    public interface CollectServerDialogHandler {
        void onCollectServerDialogCreate(CollectServer server);
        void onCollectServerDialogUpdate(CollectServer server);
        void onDialogDismiss();
    }


    public static CollectServerDialogFragment newInstance(@Nullable CollectServer server) {
        CollectServerDialogFragment frag = new CollectServerDialogFragment();

        Bundle args = new Bundle();
        if (server == null) {
            args.putInt(TITLE_KEY, R.string.settings_servers_add_server_dialog_title);
        } else {
            args.putInt(TITLE_KEY, R.string.settings_docu_dialog_title_server_settings);
            args.putSerializable(ID_KEY, server.getId());
            args.putSerializable(OBJECT_KEY, server);
        }

        frag.setArguments(args);

        return frag;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        assert getArguments() != null;
        final long serverId = getArguments().getLong(ID_KEY, 0);
        Object obj = getArguments().getSerializable(OBJECT_KEY);

        final View dialogView;
            dialogView = inflater.inflate(R.layout.dialog_collect_server, container, false);

        unbinder = ButterKnife.bind(this, dialogView);

        presenter = new CheckOdkServerPresenter(this);

        if (obj != null) {
            CollectServer server = (CollectServer) obj;
            name.setText(server.getName());
            url.setText(server.getUrl());

            if (!server.getName().isEmpty() && !server.getPassword().isEmpty()){
                advancedToggle.setOpen();
                username.setText(server.getUsername());
                password.setText(server.getPassword());
            }
        }

        advancedToggle.setOnStateChangedListener(open -> maybeShowAdvancedPanel());

        maybeShowAdvancedPanel();

        cancel.setOnClickListener(v -> dismissDialog());
        back.setOnClickListener(v -> dismissDialog());
        next.setOnClickListener(v -> {
            validate();
            if (validated) {
                checkServer(copyFields(new CollectServer(serverId)), false);
            }
        });

        return dialogView;
    }

    @Override
    public void onStart() {
        if (getDialog() == null) {
            return;
        }

        getDialog().getWindow().setWindowAnimations(
                R.style.CollectDialogAnimation);

        super.onStart();
    }

   @NotNull
   @Override
   public Dialog onCreateDialog(final Bundle savedInstanceState) {

       // the content
       final RelativeLayout root = new RelativeLayout(getActivity());
       root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

       if (getActivity() == null) {
           return super.onCreateDialog(savedInstanceState);
       }

       Context context = getContext();

       if (context == null) {
           return super.onCreateDialog(savedInstanceState);
       }

       // creating the fullscreen dialog
      final Dialog dialog = new Dialog(getActivity());
       dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
       dialog.setContentView(root);
        if (dialog.getWindow() != null) {
           dialog.getWindow().setBackgroundDrawable(context.getResources().getDrawable(R.drawable.collect_server_dialog_layout_background));
           dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
       }
       return dialog;
   }

    @Override
    public void onDismiss(@NotNull DialogInterface dialog) {
        super.onDismiss(dialog);
        unbinder.unbind();
        if (presenter != null) {
            presenter.destroy();
            presenter = null;
        }
    }

    @Override
    public void onServerCheckSuccess(CollectServer server) {
        save(server);
    }

    @Override
    public void onServerCheckFailure(IErrorBundle errorBundle) {
        if (errorBundle.getCode() == HttpStatus.UNAUTHORIZED_401) {
            if (username.getText().length() > 0 || password.getText().length() > 0) {
                advancedToggle.setOpen();
                usernameLayout.setError(getString(R.string.settings_docu_error_wrong_credentials));
            } else {
                urlLayout.setError(getString(R.string.settings_docu_error_auth_required));
            }
        } else if (errorBundle.getException() instanceof UnknownHostException) {
            urlLayout.setError(getString(R.string.settings_docu_error_domain_doesnt_exit));
        } else {
            urlLayout.setError(getString(R.string.settings_docu_error_unknown_connection_error));
        }

        validated = false;
    }

    @Override
    public void onServerCheckError(Throwable error) {
        Toast.makeText(getActivity(), getString(R.string.settings_docu_error_unknown_connection_error), Toast.LENGTH_LONG).show();
        validated = false;
    }

    @Override
    public void showServerCheckLoading() {
        progressBar.setVisibility(View.VISIBLE);
        setEnabledViews(false);
    }

    @Override
    public void hideServerCheckLoading() {
        setEnabledViews(true);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onNoConnectionAvailable() {
        Toast.makeText(getActivity(), getString(R.string.settings_docu_error_no_internet), Toast.LENGTH_LONG).show();
        next.setText(R.string.settings_docu_dialog_action_try_again_connecting);

        validated = false;
    }

    @Override
    public void setSaveAnyway(boolean enabled) {
        //dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(enabled ? View.VISIBLE : View.GONE);
      //  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
      //          getString(enabled ? R.string.settings_dialog_action_save_server_no_internet : R.string.action_ok));
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    private void checkServer(@NonNull CollectServer server,  boolean connectionRequired) {
        // lets go with sync solution as this will not influence UX too much here
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 &&
                !securityProviderUpgradeAttempted && getContext() != null) {
            try {
                ProviderInstaller.installIfNeeded(getContext());
            } catch (Exception e) {
                Timber.e(e);//TODO Crahslytics removed
            }
            /*(GooglePlayServicesRepairableException e) {
                GoogleApiAvailability.getInstance()
                        .showErrorNotification(getContext(), e.getConnectionStatusCode());
                securityProviderUpgradeAttempted = true;
                return;
            } catch (GooglePlayServicesNotAvailableException e) {
                Timber.d(e);
            }*/
        }

        if (presenter != null) {
            presenter.checkServer(server, connectionRequired);
        }
    }

    private void validate() {
        validated = true;
        validateRequired(name, nameLayout);
        validateUrl(url, urlLayout);
        //validateRequired(username, usernameLayout);
        //validateRequired(password, passwordLayout);
    }

    private void validateRequired(EditText field, TextInputLayout layout) {
        layout.setError(null);

        if (TextUtils.isEmpty(field.getText().toString())) {
            layout.setError(getString(R.string.settings_text_empty_field));
            validated = false;
        }
    }

    private void validateUrl(EditText field, TextInputLayout layout) {
        String url = field.getText().toString();

        layout.setError(null);

        if (TextUtils.isEmpty(url)) {
            layout.setError(getString(R.string.settings_text_empty_field));
            validated = false;
        } else {
            url = url.trim();
            field.setText(url);

            if (!Patterns.WEB_URL.matcher(url).matches()) {
                layout.setError(getString(R.string.settings_docu_error_not_valid_URL));
                validated = false;
            }
        }
    }

    @NonNull
    private CollectServer copyFields(@NonNull CollectServer server) {
        server.setName(name.getText().toString());
        server.setUrl(url.getText().toString().trim());
        server.setUsername(username.getText().toString().trim());
        server.setPassword(password.getText().toString());

        return server;
    }

    private void setEnabledViews(boolean enabled) {
        nameLayout.setEnabled(enabled);
        urlLayout.setEnabled(enabled);
        usernameLayout.setEnabled(enabled);
        passwordLayout.setEnabled(enabled);
    }

    private void save(CollectServer server) {
        dismiss();

        CollectServerDialogHandler activity = (CollectServerDialogHandler) getActivity();
        if (activity == null) {
            return;
        }

        if (server.getId() == 0) {
            activity.onCollectServerDialogCreate(server);
        } else {
            activity.onCollectServerDialogUpdate(server);
        }
    }

    private void onDialogDismiss() {
        CollectServerDialogHandler activity = (CollectServerDialogHandler) getActivity();
        if (activity == null) {
            return;
        }
        activity.onDialogDismiss();
    }

    private void dismissDialog() {
        dismiss();

        onDialogDismiss();
    }

    @Override
    public void onCancel(@NotNull DialogInterface dialog) {
        super.onCancel(dialog);

        onDialogDismiss();
    }

    private void maybeShowAdvancedPanel() {
        advancedPanel.setVisibility(advancedToggle.isOpen() ? View.VISIBLE : View.GONE);
    }
}
