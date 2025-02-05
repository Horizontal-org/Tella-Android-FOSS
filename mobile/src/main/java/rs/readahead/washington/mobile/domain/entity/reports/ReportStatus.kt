package rs.readahead.washington.mobile.domain.entity.reports

enum class ReportStatus {
    UNKNOWN,
    DRAFT,
    FINALIZED,
    SUBMITTED,
    SUBMISSION_ERROR,
    DELETED,
    SUBMISSION_PENDING, // no connection on sending, or offline mode - form saved
    SUBMISSION_PARTIAL_PARTS;  // some req body parts (files) are note sent

    open fun isFinal(): Boolean {
        return !(this == UNKNOWN || this == DRAFT)
    }
}
