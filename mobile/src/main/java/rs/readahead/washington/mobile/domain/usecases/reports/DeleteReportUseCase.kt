package rs.readahead.washington.mobile.domain.usecases.reports

import io.reactivex.Completable
import rs.readahead.washington.mobile.domain.repository.reports.ITellaReportsRepository
import rs.readahead.washington.mobile.domain.usecases.base.CompletableUseCase
import javax.inject.Inject

class DeleteReportUseCase @Inject constructor(private val reportsRepository: ITellaReportsRepository) :
    CompletableUseCase() {

    private var id: Long = 0

    fun setId(id: Long) {
        this.id = id
    }

    override fun buildUseCaseSingle(): Completable {
        return reportsRepository.deleteReportInstance(id)
    }
}