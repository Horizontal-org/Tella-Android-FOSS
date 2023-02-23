package rs.readahead.washington.mobile.views.dialog.reports.step1

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import org.hzontal.shared_ui.bottomsheet.KeyboardUtil
import org.hzontal.shared_ui.utils.DialogUtils
import rs.readahead.washington.mobile.MyApplication
import rs.readahead.washington.mobile.R
import rs.readahead.washington.mobile.databinding.FragmentEnterServerBinding
import rs.readahead.washington.mobile.domain.entity.reports.TellaReportServer
import rs.readahead.washington.mobile.views.base_ui.BaseBindingFragment
import rs.readahead.washington.mobile.views.dialog.ConnectFlowUtils.validateUrl
import rs.readahead.washington.mobile.views.dialog.OBJECT_KEY
import rs.readahead.washington.mobile.views.dialog.reports.ReportsConnectFlowViewModel
import rs.readahead.washington.mobile.views.dialog.reports.step3.LoginReportsFragment


@AndroidEntryPoint
class EnterUploadServerFragment :
    BaseBindingFragment<FragmentEnterServerBinding>(FragmentEnterServerBinding::inflate) {
    private val serverReports: TellaReportServer by lazy {
        TellaReportServer()
    }
    private val viewModel by viewModels<ReportsConnectFlowViewModel>()
    private var projectSlug = ""

    companion object {
        val TAG: String = EnterUploadServerFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(server: TellaReportServer): EnterUploadServerFragment {
            val frag = EnterUploadServerFragment()
            val args = Bundle()
            args.putString(OBJECT_KEY, Gson().toJson(server))
            frag.arguments = args
            return frag
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
        initObservers()
    }

    private fun initObservers() {
        viewModel.serverAlreadyExist.observe(viewLifecycleOwner) { serverAlreadyExist ->
            if (serverAlreadyExist) {
                DialogUtils.showBottomMessage(
                    baseActivity,
                    getString(R.string.Report_Server_Already_Exist_Error),
                    false
                )
            } else {
                KeyboardUtil.hideKeyboard(activity)
                baseActivity.addFragment(
                    LoginReportsFragment.newInstance(
                        serverReports,
                        projectSlug
                    ), R.id.container
                )
            }
        }
    }

    private fun initListeners() {
        with(binding!!) {
            backBtn.setOnClickListener {
                baseActivity.finish()
            }
            nextBtn.setOnClickListener {
                if (!MyApplication.isConnectedToInternet(baseActivity)) {
                    DialogUtils.showBottomMessage(
                        baseActivity,
                        getString(R.string.settings_docu_error_no_internet),
                        true
                    )
                } else {
                    if (validateUrl(url, urlLayout, baseActivity, serverReports)) {
                        val url = serverReports.url
                        projectSlug = url.substring(url.lastIndexOf('/') - 1)
                        serverReports.url = url.substring(0, url.lastIndexOf('/') - 1)
                        viewModel.listServers(url)
                    }
                }
            }
        }
    }

}