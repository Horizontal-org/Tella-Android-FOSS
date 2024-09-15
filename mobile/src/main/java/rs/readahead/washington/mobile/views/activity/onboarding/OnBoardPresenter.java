package rs.readahead.washington.mobile.views.activity.onboarding;

import org.hzontal.shared_ui.utils.CrashlyticsUtil;

import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import rs.readahead.washington.mobile.MyApplication;
import rs.readahead.washington.mobile.data.database.DataSource;
import rs.readahead.washington.mobile.data.database.KeyDataSource;
import rs.readahead.washington.mobile.data.database.UwaziDataSource;
import rs.readahead.washington.mobile.domain.entity.reports.TellaReportServer;
import rs.readahead.washington.mobile.domain.entity.UWaziUploadServer;
import rs.readahead.washington.mobile.domain.entity.collect.CollectServer;

public class OnBoardPresenter implements IOnBoardPresenterContract.IPresenter {
    private final KeyDataSource keyDataSource;
    private IOnBoardPresenterContract.IView view;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public OnBoardPresenter(IOnBoardPresenterContract.IView view) {
        keyDataSource = MyApplication.getKeyDataSource();
        this.view = view;
    }

    @Override
    public void create(final TellaReportServer server) {
        disposables.add(keyDataSource.getDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> view.showLoading())
                .flatMapSingle((Function<DataSource, SingleSource<TellaReportServer>>)
                        dataSource -> dataSource.createTellaUploadServer(server))
                .doFinally(() -> view.hideLoading())
                .subscribe(server1 -> view.onCreatedTUServer(server1),
                        throwable -> {
                            CrashlyticsUtil.Companion.handleThrowable(throwable);
                            view.onCreateTUServerError(throwable);
                        })
        );
    }

    @Override
    public void create(final CollectServer server) {
        disposables.add(keyDataSource.getDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> view.showLoading())
                .flatMapSingle((Function<DataSource, SingleSource<CollectServer>>)
                        dataSource -> dataSource.createCollectServer(server))
                .doFinally(() -> view.hideLoading())
                .subscribe(server1 -> view.onCreatedServer(server1),
                        throwable -> {
                            CrashlyticsUtil.Companion.handleThrowable(throwable);
                            view.onCreateCollectServerError(throwable);
                        })
        );
    }

    @Override
    public void create(UWaziUploadServer server) {
        disposables.add(keyDataSource.getUwaziDataSource()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle((Function<UwaziDataSource, SingleSource<UWaziUploadServer>>)
                        dataSource -> dataSource.createUWAZIServer(server))
                .subscribe(server1 -> view.onCreatedUwaziServer(server1),
                        throwable -> {
                            CrashlyticsUtil.Companion.handleThrowable(throwable);
                            view.onCreateCollectServerError(throwable);
                        })
        );
    }

    @Override
    public void destroy() {
        disposables.dispose();
        view = null;
    }
}
