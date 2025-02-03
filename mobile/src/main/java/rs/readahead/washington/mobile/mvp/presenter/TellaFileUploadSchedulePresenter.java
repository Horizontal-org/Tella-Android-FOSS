package rs.readahead.washington.mobile.mvp.presenter;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.hzontal.shared_ui.utils.CrashlyticsUtil;
import com.hzontal.tella_vault.VaultFile;

import org.hzontal.tella.keys.key.LifecycleMainKey;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import rs.readahead.washington.mobile.MyApplication;
import rs.readahead.washington.mobile.data.database.KeyDataSource;
import rs.readahead.washington.mobile.domain.entity.collect.FormMediaFile;
import rs.readahead.washington.mobile.mvp.contract.ITellaFileUploadSchedulePresenterContract;
import rs.readahead.washington.mobile.util.jobs.WorkerUploadReport;


public class TellaFileUploadSchedulePresenter implements ITellaFileUploadSchedulePresenterContract.IPresenter {
    private ITellaFileUploadSchedulePresenterContract.IView view;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final KeyDataSource keyDataSource;


    public TellaFileUploadSchedulePresenter(ITellaFileUploadSchedulePresenterContract.IView view) {
        this.view = view;
        this.keyDataSource = MyApplication.getKeyDataSource();
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }
/*
    @Override
    public void scheduleUploadMediaFiles(final List<VaultFile> mediaFiles) {
        disposables.add(keyDataSource.getDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapCompletable(dataSource -> dataSource.scheduleUploadMediaFiles(mediaFiles))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    //TODO Implement with work lib
                    view.onMediaFilesUploadScheduled();
                }, throwable -> {
                    CrashlyticsUtil.handleThrowable(throwable);
                    view.onMediaFilesUploadScheduleError(throwable);
                })
        );
    }

    @Override
    public void scheduleUploadMediaFilesWithPriority(final List<VaultFile> mediaFiles, long uploadServerId, boolean metadata) {
        disposables.add(keyDataSource.getDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapCompletable(dataSource -> dataSource.scheduleUploadMediaFilesWithPriority(mediaFiles, uploadServerId, metadata))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    //TODO Implement with work lib
                    view.onMediaFilesUploadScheduled();
                }, throwable -> {
                    CrashlyticsUtil.handleThrowable(throwable);
                    view.onMediaFilesUploadScheduleError(throwable);
                })
        );
    }
*/
    @Override
    public void scheduleUploadReportFiles(VaultFile vaultFile, long uploadServerId) {
        disposables.add(keyDataSource.getDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapCompletable(dataSource -> dataSource.scheduleUploadReport(FormMediaFile.fromMediaFile(vaultFile), uploadServerId))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    Constraints constraints =
                            new Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build();
                    OneTimeWorkRequest onetimeJob = new OneTimeWorkRequest.Builder(WorkerUploadReport.class)
                            .setConstraints(constraints).build();
                    WorkManager.getInstance(view.getContext())
                            .enqueueUniqueWork("WorkerUploadReport2 ", ExistingWorkPolicy.KEEP, onetimeJob);
                    MyApplication.getMainKeyHolder().setTimeout(LifecycleMainKey.NO_TIMEOUT);
                    view.onMediaFilesUploadScheduled();
                }, throwable -> {
                    CrashlyticsUtil.handleThrowable(throwable);
                    view.onMediaFilesUploadScheduleError(throwable);
                })
        );

    }
}
