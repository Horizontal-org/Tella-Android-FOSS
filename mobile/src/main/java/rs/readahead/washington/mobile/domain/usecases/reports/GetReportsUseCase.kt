package rs.readahead.washington.mobile.domain.usecases.reports

import io.reactivex.Single
import rs.readahead.washington.mobile.domain.entity.EntityStatus
import rs.readahead.washington.mobile.domain.entity.reports.ReportInstance
import rs.readahead.washington.mobile.domain.repository.reports.ITellaReportsRepository
import rs.readahead.washington.mobile.domain.usecases.base.SingleUseCase
import javax.inject.Inject

class GetReportsUseCase @Inject constructor(private val reportsRepository: ITellaReportsRepository) :
    SingleUseCase<List<ReportInstance>>() {

    private lateinit var entityStatus: EntityStatus

    fun setEntityStatus(entityStatus: EntityStatus) {
        this.entityStatus = entityStatus
    }

    override fun buildUseCaseSingle(): Single<List<ReportInstance>> {
        return when (entityStatus) {
            EntityStatus.SUBMITTED -> {
                reportsRepository.listSubmittedReportInstances()
            }
            EntityStatus.FINALIZED -> {
                reportsRepository.listOutboxReportInstances()
            }
            else -> {
                reportsRepository.listDraftReportInstances()
            }
        }
    }
}