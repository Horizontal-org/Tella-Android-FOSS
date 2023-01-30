package rs.readahead.washington.mobile.mvp.presenter;

import android.net.Uri;

import com.hzontal.tella_vault.VaultFile;

import java.util.concurrent.Callable;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import rs.readahead.washington.mobile.MyApplication;
import rs.readahead.washington.mobile.media.MediaFileHandler;
import rs.readahead.washington.mobile.mvp.contract.IMediaFileViewerPresenterContract;
import timber.log.Timber;


public class MediaFileViewerPresenter implements IMediaFileViewerPresenterContract.IPresenter {
    private final CompositeDisposable disposables = new CompositeDisposable();
    private IMediaFileViewerPresenterContract.IView view;

    public MediaFileViewerPresenter(IMediaFileViewerPresenterContract.IView view) {
        this.view = view;
    }

    @Override
    public void exportNewMediaFile(Boolean withMetadata, final VaultFile vaultFile, Uri path) {
        disposables.add(Completable.fromCallable((Callable<Void>) () -> {
                            MediaFileHandler.exportMediaFile(view.getContext().getApplicationContext(), vaultFile, path);
                            if (withMetadata && vaultFile.metadata != null) {
                                MediaFileHandler.exportMediaFile(view.getContext().getApplicationContext(), MediaFileHandler.maybeCreateMetadataMediaFile(vaultFile), path);
                            }
                            return null;
                        })
                        .subscribeOn(Schedulers.computation())
                        .doOnSubscribe(disposable -> view.onExportStarted())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> view.onExportEnded())
                        .subscribe(() -> view.onMediaExported(), throwable -> {
                            Timber.e(throwable);//TODO Crahslytics removed
                            view.onExportError(throwable);
                        })
        );
    }

    @Override
    public void deleteMediaFiles(final VaultFile vaultFile) {
        disposables.add(MyApplication.rxVault.delete(vaultFile)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isDeleted -> view.onMediaFileDeleted(), throwable -> {
                    Timber.e(throwable);//TODO Crahslytics removed
                    view.onMediaFileDeletionError(throwable);
                }));
    }

    @Override
    public void renameVaultFile(String id, String name) {
        disposables.add(MyApplication.rxVault.rename(id, name)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(vaultFile -> view.onMediaFileRename(vaultFile), throwable -> {
                    Timber.e(throwable);//TODO Crahslytics removed
                    view.onMediaFileRenameError(throwable);
                }));
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }
}
