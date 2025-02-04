package rs.readahead.washington.mobile.domain.usecases.reports

import io.reactivex.Single
import rs.readahead.washington.mobile.domain.entity.reports.ReportInstance
import rs.readahead.washington.mobile.domain.repository.reports.ITellaReportsRepository
import rs.readahead.washington.mobile.domain.usecases.base.SingleUseCase
import javax.inject.Inject

class SaveReportFormInstanceUseCase @Inject constructor(private val reportsRepository: ITellaReportsRepository) :
    SingleUseCase<ReportInstance>() {

    private lateinit var reportInstance: ReportInstance

    fun setReportFormInstance(reportInstance: ReportInstance) {
        this.reportInstance = reportInstance
    }

    override fun buildUseCaseSingle(): Single<ReportInstance> {
        return reportsRepository.saveInstance(reportInstance)
    }
}