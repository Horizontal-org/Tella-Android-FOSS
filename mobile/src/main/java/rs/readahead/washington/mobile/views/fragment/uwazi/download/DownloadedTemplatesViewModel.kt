package rs.readahead.washington.mobile.views.fragment.uwazi.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.hzontal.shared_ui.utils.CrashlyticsUtil
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import rs.readahead.washington.mobile.MyApplication
import rs.readahead.washington.mobile.data.database.KeyDataSource
import rs.readahead.washington.mobile.data.database.UwaziDataSource
import rs.readahead.washington.mobile.data.repository.UwaziRepository
import rs.readahead.washington.mobile.domain.entity.UWaziUploadServer
import rs.readahead.washington.mobile.domain.entity.uwazi.CollectTemplate
import rs.readahead.washington.mobile.domain.entity.uwazi.ListTemplateResult
import rs.readahead.washington.mobile.domain.exception.NoConnectivityException
import rs.readahead.washington.mobile.views.fragment.uwazi.download.adapter.ViewTemplateItem
import rs.readahead.washington.mobile.views.fragment.uwazi.mappers.toViewTemplateItem
import timber.log.Timber
import java.util.ArrayList

class DownloadedTemplatesViewModel : ViewModel(){
    private val uwaziRepository by lazy { UwaziRepository() }
    var error = MutableLiveData<Throwable>()
    private val _progress = MutableLiveData<Boolean>()
    val progress : LiveData<Boolean> get() = _progress
    private val _templates = MutableLiveData<List<ViewTemplateItem>>()
    val templates: LiveData<List<ViewTemplateItem>> get() = _templates
    private val keyDataSource: KeyDataSource = MyApplication.getKeyDataSource()
    private var templateList = mutableListOf<ViewTemplateItem>()
    private val disposables = CompositeDisposable()
    private var  _connectionAvailable = MutableLiveData<Boolean>()
    val connectionAvailable : LiveData<Boolean> get() = _connectionAvailable
    private var  _showDeleteSheet = MutableLiveData<Pair<Boolean,CollectTemplate>>()
    val showDeleteSheet : LiveData<Pair<Boolean,CollectTemplate>> get() = _showDeleteSheet


    init {
        refreshTemplateList()
    }

    private fun onDownloadClicked(template : CollectTemplate){
        disposables.add(keyDataSource.uwaziDataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { _progress.postValue(true) }
            .flatMap { dataSource: UwaziDataSource -> dataSource.saveBlankTemplate(template).toObservable() }
            .doFinally { _progress.postValue(false)  }
            .subscribe ({ templateResult ->
                val viewTemplateItem = collectTemplateToViewTemplate(templateResult)
                templateList.map {
                   if (it.id == viewTemplateItem.id){
                       val index = templateList.indexOf(it)
                       templateList.removeAt(index)
                       templateList.add(index,viewTemplateItem)
                       _templates.postValue(templateList)
                       return@map
                   }
                }
            }

            ) { throwable: Throwable? ->
                CrashlyticsUtil.handleThrowable(
                    throwable
                        ?: throw NullPointerException("Expression 'throwable' must not be null")
                )
                error.postValue(throwable)
            })
    }

    private fun onMoreClicked(template : CollectTemplate){
        _showDeleteSheet.postValue(Pair(true,template))
    }

    fun confirmDelete(template : CollectTemplate){
        disposables.add(keyDataSource.uwaziDataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { _progress.postValue(true) }
            .flatMapCompletable { dataSource: UwaziDataSource -> dataSource.deleteTemplate(template.id) }
            .doFinally { _progress.postValue(false)  }
            .subscribe(
                {
                    template.apply { isDownloaded = false }
                    val viewTemplateItem = collectTemplateToViewTemplate(template)
                    templateList.map {
                        if (it.id == viewTemplateItem.id){
                            val index = templateList.indexOf(it)
                            templateList.removeAt(index)
                            templateList.add(index,viewTemplateItem)
                            _templates.postValue(templateList)
                            return@map
                        }
                    }}
            ) { throwable: Throwable? ->
                CrashlyticsUtil.handleThrowable(throwable)
                error.postValue(throwable)
            }
        )
    }

    fun refreshTemplateList() {
        disposables.add(keyDataSource.uwaziDataSource
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { _progress.postValue(true) }
            .flatMap { dataSource: UwaziDataSource -> dataSource.listUwaziServers().toObservable() }
            .flatMap { servers ->
                val singles: MutableList<Single<ListTemplateResult>> = ArrayList()
                for (server in servers) {
                    singles.add(uwaziRepository.getTemplatesResult(server))
                }
                Single.zip(
                    singles
                ) { objects: Array<Any?> ->
                    val allResults = ListTemplateResult()
                    for (obj in objects) {
                        if (obj is ListTemplateResult) {
                            val templates =
                                obj.templates
                            val errors =
                                obj.errors
                            allResults.templates.addAll(templates)
                            allResults.errors.addAll(errors)
                        }
                    }
                    allResults
                }.toObservable()
            }.flatMap { result ->
                keyDataSource.uwaziDataSource.flatMap {  dataSource ->
                    dataSource.updateBlankTemplatesIfNeeded(result).toObservable()
                }

            }
            .doFinally { _progress.postValue(false)  }
            .subscribe ({ result ->
                val listResult = mutableListOf<ViewTemplateItem>()
                result.templates.forEach { template ->
                    val mappedTemplate = template.toViewTemplateItem(onDownloadClicked = { onDownloadClicked(template) },
                        onMoreClicked = {onMoreClicked(template)}
                    )
                    listResult.add(mappedTemplate)
                }
                templateList = listResult
                _templates.postValue(templateList)
            }) { throwable: Throwable? ->
            if (throwable is NoConnectivityException) {
                _connectionAvailable.postValue(true)
            } else {
                CrashlyticsUtil.handleThrowable(
                    throwable
                        ?: throw NullPointerException("Expression 'throwable' must not be null")
                )
                error.postValue(throwable)
            }
        }
        )
    }

    private fun collectTemplateToViewTemplate(template : CollectTemplate) : ViewTemplateItem {
     return   template.toViewTemplateItem(onDownloadClicked = { onDownloadClicked(template) },
            onMoreClicked = {onMoreClicked(template)})
    }
}