package rs.readahead.washington.mobile.views.fragment.forms

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.hzontal.shared_ui.utils.CrashlyticsUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import rs.readahead.washington.mobile.MyApplication
import rs.readahead.washington.mobile.bus.SingleLiveEvent
import rs.readahead.washington.mobile.data.database.DataSource
import rs.readahead.washington.mobile.data.database.KeyDataSource
import rs.readahead.washington.mobile.data.repository.OpenRosaRepository
import rs.readahead.washington.mobile.data.repository.ProgressListener
import rs.readahead.washington.mobile.domain.entity.collect.CollectFormInstance
import rs.readahead.washington.mobile.domain.entity.collect.CollectFormInstanceStatus
import rs.readahead.washington.mobile.domain.entity.collect.CollectServer
import rs.readahead.washington.mobile.domain.entity.collect.FormMediaFile
import rs.readahead.washington.mobile.domain.entity.collect.FormMediaFileStatus
import rs.readahead.washington.mobile.domain.entity.collect.NegotiatedCollectServer
import rs.readahead.washington.mobile.domain.entity.collect.OpenRosaPartResponse
import rs.readahead.washington.mobile.domain.exception.NoConnectivityException
import rs.readahead.washington.mobile.domain.repository.IOpenRosaRepository
import rs.readahead.washington.mobile.odk.FormController
import rs.readahead.washington.mobile.util.C
import javax.inject.Inject

@HiltViewModel
class SubmitFormsViewModel @Inject constructor(private val mApplication: Application) :
    AndroidViewModel(mApplication) {
    private var keyDataSource: KeyDataSource = MyApplication.getKeyDataSource()
    private val disposables = CompositeDisposable()
    private var openRosaRepository: IOpenRosaRepository = OpenRosaRepository()

    private var _showFormSubmitLoading = SingleLiveEvent<CollectFormInstance>()
    val showFormSubmitLoading: LiveData<CollectFormInstance> get() = _showFormSubmitLoading

    private var _showReFormSubmitLoading = SingleLiveEvent<CollectFormInstance>()
    val showReFormSubmitLoading: LiveData<CollectFormInstance> get() = _showReFormSubmitLoading

    private var _formPartSubmitStart = SingleLiveEvent<Pair<CollectFormInstance, String>>()
    val formPartSubmitStart: LiveData<Pair<CollectFormInstance, String>> get() = _formPartSubmitStart

    private var _formPartResubmitStart = SingleLiveEvent<Pair<CollectFormInstance, String>>()
    val formPartResubmitStart: LiveData<Pair<CollectFormInstance, String>> get() = _formPartResubmitStart

    private val _progressCallBack = SingleLiveEvent<Pair<String, Float>>()
    val progressCallBack: LiveData<Pair<String, Float>> get() = _progressCallBack

    private val _formPartSubmitSuccess =
        SingleLiveEvent<Pair<CollectFormInstance, OpenRosaPartResponse?>>()
    val formPartSubmitSuccess: LiveData<Pair<CollectFormInstance, OpenRosaPartResponse?>> get() = _formPartSubmitSuccess

    private val _formPartResubmitSuccess =
        SingleLiveEvent<Pair<CollectFormInstance, OpenRosaPartResponse?>>()
    val formPartResubmitSuccess: LiveData<Pair<CollectFormInstance, OpenRosaPartResponse?>> get() = _formPartResubmitSuccess

    private val _formSubmitNoConnectivity = SingleLiveEvent<Boolean>()
    val formSubmitNoConnectivity: LiveData<Boolean> get() = _formSubmitNoConnectivity

    private val _formPartSubmitError = SingleLiveEvent<Throwable?>()
    val formPartSubmitError: LiveData<Throwable?> get() = _formPartSubmitError

    private val _hideFormSubmitLoading = SingleLiveEvent<Boolean>()
    val hideFormSubmitLoading: LiveData<Boolean> get() = _hideFormSubmitLoading

    private val _hideReFormSubmitLoading = SingleLiveEvent<Boolean>()
    val hideReFormSubmitLoading: LiveData<Boolean> get() = _hideReFormSubmitLoading

    private var _formPartsSubmitEnded = SingleLiveEvent<CollectFormInstance>()
    val formPartsSubmitEnded: LiveData<CollectFormInstance> get() = _formPartsSubmitEnded

    private val _saveForLaterFormInstanceSuccess = SingleLiveEvent<Boolean>()
    val saveForLaterFormInstanceSuccess: LiveData<Boolean> get() = _saveForLaterFormInstanceSuccess

    private val _saveForLaterFormInstanceError = SingleLiveEvent<Throwable?>()
    val saveForLaterFormInstanceError: LiveData<Throwable?> get() = _saveForLaterFormInstanceError

    private val _submissionStoppedByUser = SingleLiveEvent<Boolean>()
    val submissionStoppedByUser: LiveData<Boolean> get() = _submissionStoppedByUser

    private val _formReSubmitNoConnectivity = SingleLiveEvent<Boolean>()
    val formReSubmitNoConnectivity: LiveData<Boolean> get() = _formReSubmitNoConnectivity

    private val _formPartReSubmitError = SingleLiveEvent<Throwable?>()
    val formPartReSubmitError: LiveData<Throwable?> get() = _formPartReSubmitError

    private var _formPartsResubmitEnded = SingleLiveEvent<CollectFormInstance>()
    val formPartsResubmitEnded: LiveData<CollectFormInstance> get() = _formPartsResubmitEnded

    fun submitActiveFormInstance(name: String?) {
        val instance = FormController.getActive().collectFormInstance
        if (!TextUtils.isEmpty(name)) {
            instance.instanceName = name
        }
        // submitFormInstance(instance);
        submitFormInstanceGranular(instance)
    }

    fun submitFormInstanceGranular(instance: CollectFormInstance) {
        val offlineMode = false
        val startStatus = instance.status
        disposables.add(keyDataSource.dataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { disposable: Disposable? ->
                _showFormSubmitLoading.postValue(instance)
            }
            .flatMapSingle<CollectServer?> { dataSource: DataSource ->
                finalizeFormInstance(
                    dataSource,
                    instance
                )
            }
            .flatMapSingle<NegotiatedCollectServer?> { server: CollectServer? ->
                negotiateServer(
                    server,
                    offlineMode
                )
            }
            .flatMap { negotiatedCollectServer: NegotiatedCollectServer? ->
                createPartBundles(
                    instance,
                    negotiatedCollectServer
                )
            }
            .flatMap { source: List<GranularSubmissionBundle?>? ->
                Observable.fromIterable(
                    source
                )
            }
            .concatMap { bundle: GranularSubmissionBundle ->
                _formPartSubmitStart.postValue(Pair(instance, bundle.partName))

                bundle.server?.let {
                    openRosaRepository.submitFormGranular(
                        it, instance, bundle.attachment,
                        ProgressListener(bundle.partName, _progressCallBack)
                    ).toObservable()
                }
            }
            .flatMap<OpenRosaPartResponse?> { response: OpenRosaPartResponse ->
                // set form and attachments statuses
                setPartSuccessSubmissionStatuses(instance, response.partName)
                rxSaveFormInstance<OpenRosaPartResponse?>(instance, response, null)
            }
            .onErrorResumeNext { throwable: Throwable ->
                setErrorSubmissionStatuses(instance, startStatus, throwable)
                rxSaveFormInstance<OpenRosaPartResponse?>(instance, null, throwable)
            }
            .doFinally({ _hideFormSubmitLoading.postValue(true) })
            .subscribe(
                { response: OpenRosaPartResponse? ->
                    _formPartSubmitSuccess.postValue(Pair(instance, response))
                },
                { throwable: Throwable? ->
                    if (throwable is NoConnectivityException) {
                        // PendingFormSendJob.scheduleJob();
                        _formSubmitNoConnectivity.postValue(true)
                    } else {
                        CrashlyticsUtil.handleThrowable(throwable)
                        _formPartSubmitError.postValue(throwable)
                    }
                },
                { _formPartsSubmitEnded.postValue(instance) }
            )
        )
    }

    // todo: move to FormSaver
    fun saveForLaterFormInstance(name: String?) {
        val instance = FormController.getActive().collectFormInstance

        if (!TextUtils.isEmpty(name)) {
            instance.instanceName = name
        }
        disposables.add(keyDataSource.dataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapSingle<CollectFormInstance?> { dataSource: DataSource ->
                finalizeAndSaveFormInstance(
                    dataSource,
                    instance
                )
            }
            .subscribe(
                { collectServer: CollectFormInstance? ->
                    _saveForLaterFormInstanceSuccess.postValue(
                        true
                    )
                }
            ) { throwable: Throwable? ->
                _saveForLaterFormInstanceError.postValue(throwable)
            }
        )
    }

    fun isSubmitting(): Boolean {
        return disposables.size() > 0
    }

    fun userStopSubmission() {
        stopSubmission()
        _submissionStoppedByUser.postValue(true)
    }

    fun stopSubmission() {
        disposables.clear()
    }

    fun destroy() {
        disposables.dispose()
    }

    private fun finalizeFormInstance(
        dataSource: DataSource,
        instance: CollectFormInstance
    ): Single<CollectServer?> {
        // finalize form (FormDef & CollectFormInstance)
        instance.formDef.postProcessInstance()
        if (instance.status == CollectFormInstanceStatus.UNKNOWN || instance.status == CollectFormInstanceStatus.DRAFT) {
            instance.status = CollectFormInstanceStatus.FINALIZED
        }
        return dataSource.saveInstance(instance).flatMap { instance1: CollectFormInstance ->
            dataSource.getCollectServer(
                instance1.serverId
            )
        }
    }

    private fun finalizeAndSaveFormInstance(
        dataSource: DataSource,
        instance: CollectFormInstance
    ): Single<CollectFormInstance?>? {
        // finalize form (FormDef & CollectFormInstance)
        instance.formDef.postProcessInstance()
        instance.status = CollectFormInstanceStatus.SUBMISSION_PENDING
        return dataSource.saveInstance(instance)
    }

    @Throws(OfflineModeException::class, NoConnectivityException::class)
    private fun negotiateServer(
        server: CollectServer?,
        offlineMode: Boolean
    ): Single<NegotiatedCollectServer?>? {
        if (offlineMode) {
            throw OfflineModeException()
        }
        if (!MyApplication.isConnectedToInternet(mApplication.baseContext)) {
            throw NoConnectivityException()
        }
        return openRosaRepository.submitFormNegotiate(server)
    }

    private fun createPartBundles(
        instance: CollectFormInstance,
        server: NegotiatedCollectServer?
    ): Observable<List<GranularSubmissionBundle?>?> {
        val bundles: MutableList<GranularSubmissionBundle> = ArrayList()

        // we're adding this separately for simpler UI (having parts in order), if form is not already submitted partially or fully
        if (instance.status != CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS &&
            instance.status != CollectFormInstanceStatus.SUBMITTED
        ) {
            bundles.add(GranularSubmissionBundle(server))
        }
        for (attachment in instance.widgetMediaFiles) {
            if (attachment.uploading && attachment.status != FormMediaFileStatus.SUBMITTED) {
                bundles.add(GranularSubmissionBundle(server, attachment))
            }
        }
        return Observable.just(bundles)
    }

    private fun <T> rxSaveFormInstance(
        instance: CollectFormInstance,
        value: T,
        throwable: Throwable?
    ): Observable<T>? {
        return keyDataSource.dataSource.flatMap { dataSource: DataSource ->
            dataSource.saveInstance(instance)
                .toObservable()
                .flatMap { instance1: CollectFormInstance? ->
                    if (throwable == null) {
                        Observable.just(
                            value
                        )
                    } else {
                        Observable.error(throwable)
                    }
                }
        }
    }

    private fun setSuccessSubmissionStatuses(instance: CollectFormInstance) {
        var status = CollectFormInstanceStatus.SUBMITTED
        for (mediaFile in instance.widgetMediaFiles) {
            if (mediaFile.uploading) {
                mediaFile.status = FormMediaFileStatus.SUBMITTED
            } else {
                mediaFile.status = FormMediaFileStatus.NOT_SUBMITTED
                status = CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS
            }
        }
        instance.status = status
    }

    private fun setPartSuccessSubmissionStatuses(instance: CollectFormInstance, partName: String) {
        var status = CollectFormInstanceStatus.SUBMITTED
        if (C.OPEN_ROSA_XML_PART_NAME == partName) { // from xml data part is submitted
            instance.formPartStatus = FormMediaFileStatus.SUBMITTED
            if (!instance.widgetMediaFiles.isEmpty()) {
                status = CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS
            }
        } else {
            // update part status
            for (mediaFile in instance.widgetMediaFiles) {
                if (mediaFile.partName == partName) {
                    mediaFile.status = FormMediaFileStatus.SUBMITTED
                    break
                }
            }
            // check instance status
            for (mediaFile in instance.widgetMediaFiles) {
                if (mediaFile.status != FormMediaFileStatus.SUBMITTED) {
                    status = CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS
                    break
                }
            }
        }
        instance.status = status
    }

    private fun setErrorSubmissionStatuses(
        instance: CollectFormInstance,
        startStatus: CollectFormInstanceStatus,
        throwable: Throwable
    ) {
        val status: CollectFormInstanceStatus
        status = if (startStatus == CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS) {
            startStatus
        } else if (throwable is OfflineModeException || throwable is NoConnectivityException) {
            CollectFormInstanceStatus.SUBMISSION_PENDING
        } else {
            CollectFormInstanceStatus.SUBMISSION_ERROR
        }
        instance.status = status
    }

    /* @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void updateMediaFilesQueue(@NonNull Collection<MediaFile> attachments) {
        if (attachments.size() == 0) {
            return;
        }

        RawMediaFileQueue queue = MyApplication.mediaFileQueue();

        if (queue == null) {
            return;
        }

        for (MediaFile mediaFile: attachments) {
            queue.addAndStartUpload(mediaFile);
        }
    } */

    /* @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void updateMediaFilesQueue(@NonNull Collection<MediaFile> attachments) {
        if (attachments.size() == 0) {
            return;
        }

        RawMediaFileQueue queue = MyApplication.mediaFileQueue();

        if (queue == null) {
            return;
        }

        for (MediaFile mediaFile: attachments) {
            queue.addAndStartUpload(mediaFile);
        }
    } */
    private class OfflineModeException : Exception()

    private class GranularSubmissionBundle {
        var server: NegotiatedCollectServer?
        var attachment: FormMediaFile? = null

        constructor(server: NegotiatedCollectServer?) {
            this.server = server
        }

        constructor(server: NegotiatedCollectServer?, attachment: FormMediaFile?) {
            this.server = server
            this.attachment = attachment
        }

        val partName: String
            get() = if (attachment != null) attachment!!.partName else C.OPEN_ROSA_XML_PART_NAME
    }


    fun reSubmitFormInstanceGranular(instance: CollectFormInstance) {
        val startStatus = instance.status
        disposables.add(keyDataSource.dataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { disposable: Disposable? ->
                _showReFormSubmitLoading.postValue(instance)
            }
            .flatMapSingle<CollectServer?> { dataSource: DataSource ->
                setFormDef(dataSource, instance)
            }
            .flatMapSingle<NegotiatedCollectServer?> { server: CollectServer? ->
                this.negotiateServer(server)
            }
            .flatMap { negotiatedCollectServer: NegotiatedCollectServer? ->
                createResubmissionPartBundles(instance, negotiatedCollectServer)
            }
            .flatMap { source: List<GranularResubmissionBundle?>? ->
                Observable.fromIterable(source)
            }
            .concatMap { bundle: GranularResubmissionBundle ->
                _formPartResubmitStart.postValue(
                    Pair(instance, bundle.partName)
                )//view.formPartResubmitStart(instance, bundle.partName)
                openRosaRepository.submitFormGranular(
                    bundle.server!!, instance, bundle.attachment,
                    ProgressListener(bundle.partName, _progressCallBack)
                ).toObservable()
            }
            .flatMap { response: OpenRosaPartResponse ->
                // set form and attachments statuses
                setPartSuccessSubmissionStatuses(instance, response.partName)
                rxSaveSuccessInstance(instance, response)
            }
            .onErrorResumeNext { throwable: Throwable ->
                setErrorSubmissionStatuses(instance, startStatus, throwable)
                rxSaveErrorInstance<OpenRosaPartResponse>(instance, throwable)
            }
            .doFinally({ _hideReFormSubmitLoading.postValue(true) })
            .subscribe(
                { response: OpenRosaPartResponse? ->
                    _formPartResubmitSuccess.postValue(Pair(instance, response))
                },
                { throwable: Throwable? ->
                    if (throwable is NoConnectivityException) {
                        // PendingFormSendJob.scheduleJob();
                        _formReSubmitNoConnectivity.postValue(true)
                    } else {
                        CrashlyticsUtil.handleThrowable(throwable)
                        _formPartReSubmitError.postValue(throwable)
                    }
                },
                { _formPartsResubmitEnded.postValue(instance) }
            )
        )
    }

    fun isReSubmitting(): Boolean {
        return disposables.size() > 0
    }

    fun userStopReSubmission() {
        stopReSubmission()
        _submissionStoppedByUser.postValue(true)
    }

    fun stopReSubmission() {
        disposables.clear()
    }

    private fun setFormDef(
        dataSource: DataSource,
        instance: CollectFormInstance
    ): Single<CollectServer?> {
        return dataSource.getInstance(instance.id)
            .flatMap { fullInstance: CollectFormInstance ->
                instance.formDef = fullInstance.formDef // todo: think about this..
                dataSource.getCollectServer(instance.serverId)
            }
    }

    @Throws(NoConnectivityException::class)
    private fun negotiateServer(server: CollectServer?): Single<NegotiatedCollectServer?>? {
        if (!MyApplication.isConnectedToInternet(mApplication.baseContext)) {
            throw NoConnectivityException()
        }
        return openRosaRepository.submitFormNegotiate(server)
    }

    private fun <T> rxSaveSuccessInstance(
        instance: CollectFormInstance,
        value: T
    ): ObservableSource<T>? {
        return keyDataSource.dataSource.flatMap { dataSource: DataSource ->
            dataSource.saveInstance(instance)
                .toObservable()
                .flatMap { instance1: CollectFormInstance? ->
                    Observable.just(
                        value
                    )
                }
        }
    }

    private fun <T> rxSaveErrorInstance(
        instance: CollectFormInstance,
        throwable: Throwable
    ): ObservableSource<T?>? {
        return keyDataSource.dataSource.flatMap { dataSource: DataSource ->
            dataSource.saveInstance(instance)
                .toObservable()
                .flatMap { instance1: CollectFormInstance? ->
                    Observable.error(
                        throwable
                    )
                }
        }
    }

    private fun createResubmissionPartBundles(
        instance: CollectFormInstance,
        server: NegotiatedCollectServer?
    ): Observable<List<GranularResubmissionBundle?>?> {
        val bundles: MutableList<GranularResubmissionBundle> = java.util.ArrayList()
        if (instance.status != CollectFormInstanceStatus.SUBMISSION_PARTIAL_PARTS &&
            instance.status != CollectFormInstanceStatus.SUBMITTED
        ) {
            bundles.add(GranularResubmissionBundle(server))
        }
        for (attachment in instance.widgetMediaFiles) {
            if (attachment.uploading && attachment.status != FormMediaFileStatus.SUBMITTED) {
                bundles.add(GranularResubmissionBundle(server, attachment))
            }
        }
        return Observable.just(bundles)
    }

    private class GranularResubmissionBundle {
        var server: NegotiatedCollectServer?
        var attachment: FormMediaFile? = null

        internal constructor(server: NegotiatedCollectServer?) {
            this.server = server
        }

        internal constructor(server: NegotiatedCollectServer?, attachment: FormMediaFile?) {
            this.server = server
            this.attachment = attachment
        }

        val partName: String
            get() = if (attachment != null) attachment!!.partName else C.OPEN_ROSA_XML_PART_NAME
    }
    /*
      // todo: merge IForm{Re}Submitter presenter interfaces
      internal class ProgressListener(
          private val partName: String,
          private val view: IFormReSubmitterContract.IView?
      ) :
          IProgressListener {
          private var time: Long = 0
          override fun onProgressUpdate(current: Long, total: Long) {
              val now = Util.currentTimestamp()
              if (view != null && now - time > REFRESH_TIME_MS) {
                  time = now
                  view.formPartUploadProgress(partName, current.toFloat() / total.toFloat())
              }
          }

          companion object {
              private const val REFRESH_TIME_MS: Long = 500
          }
      }

      // Ugly, think about elegant Rx/Flowable solution
   internal class ProgressListener(
          private val partName: String,
          private val view: IFormSubmitterContract.IView?
      ) :
          IProgressListener {
          private var time: Long = 0
          override fun onProgressUpdate(current: Long, total: Long) {
              val now = Util.currentTimestamp()
              if (view != null && now - time > REFRESH_TIME_MS) {
                  time = now
                  ThreadUtil.runOnMain {
                      view.formPartUploadProgress(
                          partName, current.toFloat() / total.toFloat()
                      )
                  }
              }
          }

          companion object {
              private const val REFRESH_TIME_MS: Long = 500
          }
      }*/
}