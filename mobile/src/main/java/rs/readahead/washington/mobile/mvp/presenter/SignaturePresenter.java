package rs.readahead.washington.mobile.mvp.presenter;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import rs.readahead.washington.mobile.media.MediaFileHandler;
import rs.readahead.washington.mobile.mvp.contract.ISignaturePresenterContract;
import timber.log.Timber;


public class SignaturePresenter implements ISignaturePresenterContract.IPresenter {
    private ISignaturePresenterContract.IView view;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public SignaturePresenter(ISignaturePresenterContract.IView view) {
        this.view = view;
    }

    @Override
    public void addPngImage(final byte[] png) {
        disposables.add(
                Observable.fromCallable(() -> MediaFileHandler.savePngImage(png))
                        .subscribeOn(Schedulers.io())
                        .doOnSubscribe(disposable -> view.onAddingStart())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> view.onAddingEnd())
                        .subscribe(mediaFile -> view.onAddSuccess(mediaFile), throwable -> {
                            Timber.e(throwable);//TODO Crahslytics removed
                            view.onAddError(throwable);
                        })
        );
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }
}
