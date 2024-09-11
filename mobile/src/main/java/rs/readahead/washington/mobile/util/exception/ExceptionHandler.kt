package rs.readahead.washington.mobile.util.exception

import org.hzontal.shared_ui.utils.CrashlyticsUtil
import rs.readahead.washington.mobile.domain.exception.NoConnectivityException
import java.net.UnknownHostException

object ExceptionHandler {

    fun handleException(throwable: Throwable?): ExceptionType {
        return when (throwable) {
            is NoConnectivityException,is UnknownHostException -> ExceptionType.NO_CONNECTIVITY
            else -> {
                // Log the exception to Crashlytics or any other analytics tool
                CrashlyticsUtil.handleThrowable(throwable)

                //TODO can implement logic to identify other exception types if needed
                // For example, check the class or message of the exception
                ExceptionType.OTHER
            }
        }
    }

}