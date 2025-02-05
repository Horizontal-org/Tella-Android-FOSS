package rs.readahead.washington.mobile.views.fragment.reports.submitted

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.hzontal.shared_ui.bottomsheet.BottomSheetUtils
import rs.readahead.washington.mobile.R
import rs.readahead.washington.mobile.databinding.FragmentSendReportBinding
import rs.readahead.washington.mobile.domain.entity.reports.ReportInstance
import rs.readahead.washington.mobile.util.hide
import rs.readahead.washington.mobile.views.base_ui.BaseBindingFragment
import rs.readahead.washington.mobile.views.fragment.reports.entry.BUNDLE_REPORT_FORM_INSTANCE
import rs.readahead.washington.mobile.views.fragment.reports.ReportsViewModel
import rs.readahead.washington.mobile.views.fragment.uwazi.widgets.ReportsFormEndView

@AndroidEntryPoint
class ReportSubmittedFragment :
    BaseBindingFragment<FragmentSendReportBinding>(FragmentSendReportBinding::inflate) {

    private val viewModel by viewModels<ReportsViewModel>()
    private lateinit var endView: ReportsFormEndView
    private var reportInstance: ReportInstance? = null


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initData()
    }

    private fun initData() {
        with(viewModel) {
            instanceDeleted.observe(viewLifecycleOwner) { title ->
                title?.let {
                    nav().popBackStack()
                }

            }
        }
    }

    private fun initView() {
        arguments?.let {
            reportInstance = it.get(BUNDLE_REPORT_FORM_INSTANCE) as ReportInstance
            showFormEndView()
        }

        binding.toolbar.backClickListener = {
            nav().popBackStack()
        }
        binding.toolbar.onRightClickListener =
            { reportInstance?.let { showDeleteBottomSheet(it) } }

        binding.nextBtn.hide()

    }

    private fun showDeleteBottomSheet(entityInstance: ReportInstance) {
        BottomSheetUtils.showConfirmSheet(
            baseActivity.supportFragmentManager,
            entityInstance.title,
            getString(R.string.Delete_Report_Confirmation),
            getString(R.string.action_yes),
            getString(R.string.action_no), consumer = object : BottomSheetUtils.ActionConfirmed {
                override fun accept(isConfirmed: Boolean) {
                    if (isConfirmed) {
                        viewModel.deleteReport(entityInstance)
                    }
                }
            })
    }

    private fun showFormEndView() {
        if (reportInstance == null) {
            return
        }

        reportInstance?.let { reportFormInstance ->
            binding.nextBtn.hide()

            endView = ReportsFormEndView(
                activity,
                reportFormInstance.title,
                reportFormInstance.description
            )
            endView.setInstance(reportFormInstance, false, true)
            binding.endViewContainer.removeAllViews()
            binding.endViewContainer.addView(endView)
            endView.clearPartsProgress(reportFormInstance)
        }

    }

}