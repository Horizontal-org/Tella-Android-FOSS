package rs.readahead.washington.mobile.views.adapters.reports

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.hzontal.tella_vault.VaultFile
import com.hzontal.utils.MediaFile.isAudioFileType
import com.hzontal.utils.MediaFile.isImageFileType
import com.hzontal.utils.MediaFile.isVideoFileType
import rs.readahead.washington.mobile.R
import rs.readahead.washington.mobile.media.MediaFileHandler
import rs.readahead.washington.mobile.media.VaultFileUrlLoader
import rs.readahead.washington.mobile.presentation.entity.VaultFileLoaderModel
import rs.readahead.washington.mobile.views.interfaces.IReportAttachmentsHandler


open class ReportsFilesRecyclerViewAdapter(
    private val iAttachmentsMediaHandler: IReportAttachmentsHandler,
    context: Context,
    mediaFileHandler: MediaFileHandler
) :
    RecyclerView.Adapter<ReportsFilesRecyclerViewAdapter.GridAttachmentsViewHolder>() {
    private var listAttachment: ArrayList<VaultFile> = arrayListOf()
    private val glideLoader = VaultFileUrlLoader(context, mediaFileHandler)

    init {
        val file = VaultFile()
        file.type = VaultFile.Type.UNKNOWN
        insertAttachment(file)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridAttachmentsViewHolder {
        return GridAttachmentsViewHolder(parent)
    }

    fun insertAttachment(newAttachment: VaultFile) {
        if (newAttachment.type != VaultFile.Type.UNKNOWN) {
            if (!listAttachment.contains(newAttachment)) {
                listAttachment.add(0, newAttachment)
                notifyItemInserted(0)
            }
        } else {
            listAttachment.add(0, newAttachment)
            notifyItemInserted(0)
        }
    }

    fun getFiles(): ArrayList<VaultFile> {
        val listFiles: ArrayList<VaultFile> = arrayListOf()
        for (file in listAttachment) {
            if (file.type != VaultFile.Type.UNKNOWN) listFiles.add(file)
        }
        return listFiles
    }

    private fun removeFile(position: Int) {
        listAttachment.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun getItemCount(): Int {
        return listAttachment.size
    }

    override fun onBindViewHolder(holder: GridAttachmentsViewHolder, position: Int) {
        holder.bind(
            vaultFile = listAttachment[position]
        )
    }

    open inner class GridAttachmentsViewHolder(val view: View) : RecyclerView.ViewHolder(view)  {

        constructor(parent: ViewGroup)
                : this(
            LayoutInflater.from(parent.context).inflate(R.layout.item_report_files, parent, false)
        )

        protected lateinit var icAttachmentImg: ImageView
        private lateinit var filePreviewImg: ImageView
        private lateinit var fileNameTextView: TextView
        private lateinit var removeBtn: View
        protected val context: Context by lazy { view.context }

        fun bind(vaultFile: VaultFile?) {
            view.apply {
                fileNameTextView = findViewById(R.id.fileNameTextView)
                filePreviewImg = findViewById(R.id.attachmentImg)
                removeBtn = findViewById(R.id.remove)
                icAttachmentImg = findViewById(R.id.icAttachmentImg)
            }

            if (vaultFile!!.type != VaultFile.Type.UNKNOWN) {
                removeBtn.setOnClickListener {
                    removeFile(position = layoutPosition)
                }

                if (isImageFileType(vaultFile.mimeType)) {
                    this.showImageInfo(vaultFile)
                    Glide.with(context)
                        .using(glideLoader)
                        .load(
                            VaultFileLoaderModel(
                                vaultFile,
                                VaultFileLoaderModel.LoadType.THUMBNAIL
                            )
                        )
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(filePreviewImg)
                } else if (isAudioFileType(vaultFile.mimeType)) {
                    this.showAudioInfo()
                    fileNameTextView.text = vaultFile.name
                } else if (isVideoFileType(vaultFile.mimeType)) {
                    this.showVideoInfo(vaultFile)
                    Glide.with(context)
                        .using(glideLoader)
                        .load(
                            VaultFileLoaderModel(
                                vaultFile,
                                VaultFileLoaderModel.LoadType.THUMBNAIL
                            )
                        )
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(filePreviewImg)
                } else {
                    fileNameTextView.text = vaultFile.name
                    this.showDocInfo()
                }
            } else {
                removeBtn.visibility = View.GONE
                showAddLink()
            }
        }

        private fun showVideoInfo(vaultFile: VaultFile) {
            Glide.with(context)
                .using(glideLoader)
                .load(VaultFileLoaderModel(vaultFile, VaultFileLoaderModel.LoadType.THUMBNAIL))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(filePreviewImg)
            icAttachmentImg.setBackgroundResource(R.drawable.ic_play)
        }

        private fun showAudioInfo() {
            icAttachmentImg.setBackgroundResource(R.drawable.ic_audio_w_small)
        }

        private fun showDocInfo() {
            icAttachmentImg.setBackgroundResource(R.drawable.ic_reports)
        }

        private fun showImageInfo(vaultFile: VaultFile) {
            Glide.with(filePreviewImg.context)
                .using(glideLoader)
                .load(VaultFileLoaderModel(vaultFile, VaultFileLoaderModel.LoadType.THUMBNAIL))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(filePreviewImg)
        }

        private fun showAddLink() {
            filePreviewImg.background = context.getDrawable(R.drawable.transparent_solid)
            filePreviewImg.setImageResource(R.drawable.upload_box_btn)
            filePreviewImg.setOnClickListener {
                iAttachmentsMediaHandler.addFiles()
            }
        }
    }
}
