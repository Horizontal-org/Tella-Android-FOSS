package rs.readahead.washington.mobile.data.feedback.remote

import io.reactivex.Single
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import rs.readahead.washington.mobile.data.ParamsNetwork
import rs.readahead.washington.mobile.data.entity.feedback.FeedbackBodyEntity
import rs.readahead.washington.mobile.data.entity.feedback.FeedbackPostResponse

@JvmSuppressWildcards
interface FeedbackApiService {

    @POST
    fun submitFeedback(
            @Url url: String = ParamsNetwork.URL_FEEDBACK,
            @Header(ParamsNetwork.FEEDBACK_HEADER) tellaPlatform: String,
            @Body data: FeedbackBodyEntity,
    ): Single<FeedbackPostResponse>
}
