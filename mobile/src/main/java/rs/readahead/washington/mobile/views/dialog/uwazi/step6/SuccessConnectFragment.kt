package rs.readahead.washington.mobile.views.dialog.uwazi.step6

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import rs.readahead.washington.mobile.R
import rs.readahead.washington.mobile.databinding.FragmentSuccessConnectFlowBinding
import rs.readahead.washington.mobile.domain.entity.UWaziUploadServer
import rs.readahead.washington.mobile.views.base_ui.BaseFragment
import rs.readahead.washington.mobile.views.dialog.ID_KEY
import rs.readahead.washington.mobile.views.dialog.IS_UPDATE_SERVER
import rs.readahead.washington.mobile.views.dialog.OBJECT_KEY
import rs.readahead.washington.mobile.views.dialog.TITLE_KEY
import rs.readahead.washington.mobile.views.dialog.SharedLiveData.createServer
import rs.readahead.washington.mobile.views.dialog.SharedLiveData.updateServer

class SuccessConnectFragment : BaseFragment() {
    private lateinit var binding: FragmentSuccessConnectFlowBinding
    private lateinit var server: UWaziUploadServer
    private var isUpdate = false
    companion object{
        val TAG = SuccessConnectFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(server: UWaziUploadServer,isUpdate : Boolean): SuccessConnectFragment {
            val frag = SuccessConnectFragment()
            val args = Bundle()
            args.putInt(TITLE_KEY, R.string.settings_docu_dialog_title_server_settings)
            args.putSerializable(ID_KEY, server.id)
            args.putString(OBJECT_KEY, Gson().toJson(server))
            args.putBoolean(IS_UPDATE_SERVER,isUpdate)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSuccessConnectFlowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListeners()
    }

    override fun initView(view: View) {
        arguments?.getString(OBJECT_KEY)?.let {
            server = Gson().fromJson(it, UWaziUploadServer::class.java)
        }

        arguments?.getBoolean(IS_UPDATE_SERVER)?.let {
            isUpdate = it
        }

    }

    private fun initListeners() {
        with(binding) {
            nextBtn.setOnClickListener {
                if (isUpdate){
                    updateServer.postValue(server)

                }else {
                    createServer.postValue(server)
                }
                baseActivity.finish()
            }
        }
    }
}