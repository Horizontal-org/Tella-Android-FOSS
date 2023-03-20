package rs.readahead.washington.mobile.views.activity

/*import com.otaliastudios.cameraview.*
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Flash
import com.otaliastudios.cameraview.controls.Grid
import com.otaliastudios.cameraview.controls.Mode
import com.otaliastudios.cameraview.gesture.Gesture
import com.otaliastudios.cameraview.gesture.GestureAction
import com.otaliastudios.cameraview.size.SizeSelector*/
import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.OrientationEventListener
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager.ImageModelRequest
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.hzontal.tella_vault.VaultFile
import rs.readahead.washington.mobile.MyApplication
import rs.readahead.washington.mobile.R
import rs.readahead.washington.mobile.bus.event.CaptureEvent
import rs.readahead.washington.mobile.data.sharedpref.Preferences
import rs.readahead.washington.mobile.databinding.ActivityCameraBinding
import rs.readahead.washington.mobile.media.MediaFileHandler
import rs.readahead.washington.mobile.media.VaultFileUrlLoader
import rs.readahead.washington.mobile.media.camera.LuminosityAnalyzer
import rs.readahead.washington.mobile.media.camera.ThreadExecutor
import rs.readahead.washington.mobile.mvp.contract.ICameraPresenterContract
import rs.readahead.washington.mobile.mvp.contract.IMetadataAttachPresenterContract
import rs.readahead.washington.mobile.mvp.contract.ITellaFileUploadSchedulePresenterContract
import rs.readahead.washington.mobile.mvp.presenter.CameraPresenter
import rs.readahead.washington.mobile.mvp.presenter.MetadataAttacher
import rs.readahead.washington.mobile.presentation.entity.VaultFileLoaderModel
import rs.readahead.washington.mobile.util.*
import rs.readahead.washington.mobile.views.custom.*
import rs.readahead.washington.mobile.views.fragment.uwazi.attachments.VAULT_FILE_KEY
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val MEDIA_FILE_KEY = "mfk"
const val VAULT_CURRENT_ROOT_PARENT = "vcrf"
private const val CLICK_DELAY = 1200
private const val CLICK_MODE_DELAY = 2000

class CameraActivity : MetadataActivity(), ICameraPresenterContract.IView,
    ITellaFileUploadSchedulePresenterContract.IView, IMetadataAttachPresenterContract.IView {

    private val displayManager by lazy { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var viewFinder: PreviewView
    private lateinit var gridButton: CameraGridButton
    private lateinit var switchButton: CameraSwitchButton
    private lateinit var flashButton: CameraFlashButton
    private lateinit var captureButton: CameraCaptureButton
    private lateinit var durationView: CameraDurationTextView
    private lateinit var mSeekBar: SeekBar
    private lateinit var videoLine: View
    private lateinit var photoLine: View
    private lateinit var previewView: ImageView
    private lateinit var photoModeText: TextView
    private lateinit var videoModeText: TextView
    private lateinit var resolutionButton: CameraResolutionButton
    private lateinit var binding: ActivityCameraBinding

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var presenter: CameraPresenter? = null
    private var metadataAttacher: MetadataAttacher? = null
    private var mode: CameraMode? = null
    private var modeLocked = false
    private var intentMode: IntentMode? = null
    private var videoRecording = false
    private var progressDialog: ProgressDialog? = null
    private var mOrientationEventListener: OrientationEventListener? = null
    private var zoomLevel = 0
    private var capturedMediaFile: VaultFile? = null
    private var videoQualityDialog: AlertDialog? = null
    private var videoResolutionManager: VideoResolutionManager? = null
    private var lastClickTime = System.currentTimeMillis()
    private var glide: ImageModelRequest<VaultFileLoaderModel>? = null
    private var currentRootParent: String? = null

    private var hdrCameraSelector: CameraSelector? = null
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.window?.fitSystemWindows()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        initView()

        overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out)

        presenter = CameraPresenter(this)
        metadataAttacher = MetadataAttacher(this)
        mode = CameraMode.PHOTO
       /* if (intent.hasExtra(CAMERA_MODE)) {
            mode = CameraMode.valueOf(
                intent.getStringExtra(CAMERA_MODE)!!
            )
            modeLocked = true
        }
        intentMode = IntentMode.RETURN
        if (intent.hasExtra(INTENT_MODE)) {
            intentMode = IntentMode.valueOf(
                intent.getStringExtra(INTENT_MODE)!!
            )
        }*/
        if (intent.hasExtra(VAULT_CURRENT_ROOT_PARENT)) {
            currentRootParent = intent.getStringExtra(VAULT_CURRENT_ROOT_PARENT)
        }
        val mediaFileHandler = MediaFileHandler()
        val glideLoader = VaultFileUrlLoader(context.applicationContext, mediaFileHandler)
        glide = Glide.with(context).using(glideLoader)
        setupCameraView()
        setupCameraModeButton()
        setupImagePreview()
        setupShutterSound()
        checkLocationSettings(
            C.START_CAMERA_CAPTURE
        ) {}
    }

    override fun onResume() {
        super.onResume()
        //mOrientationEventListener!!.enable()
        startLocationMetadataListening()
        //cameraView!!.open()
        setVideoQuality()
        mSeekBar.progress = zoomLevel
        setCameraZoom()
        presenter!!.getLastMediaFile()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            maybeChangeTemporaryTimeout()
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onPause() {
        super.onPause()
        stopLocationMetadataListening()
//        mOrientationEventListener!!.disable()
        if (videoRecording) {
            captureButton.performClick()
        }
        //cameraView!!.close()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPresenter()
        hideProgressDialog()
        hideVideoResolutionDialog()
       // cameraView!!.destroy()
    }

    override fun onBackPressed() {
        if (maybeStopVideoRecording()) return
        super.onBackPressed()
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up)
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun initView() {
       // cameraView = binding.camera
        viewFinder = binding.viewFinder
        /*viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
                })*/

        gridButton = binding.gridButton
        switchButton = binding.switchButton
        flashButton = binding.flashButton
        captureButton = binding.captureButton
        durationView = binding.durationView
        mSeekBar = binding.cameraZoom
        videoLine = binding.videoLine
        photoLine = binding.photoLine
        previewView = binding.previewImage
        photoModeText = binding.photoText
        videoModeText = binding.videoText
        resolutionButton = binding.resolutionButton

        binding.close.setOnClickListener {
            onBackPressed()
        }

        binding.captureButton.setOnClickListener {
            if (Preferences.isShutterMute()) {
                val mgr = getSystemService(AUDIO_SERVICE) as AudioManager
                mgr.setStreamMute(AudioManager.STREAM_SYSTEM, true)
            }
            captureImage()
           /* if (cameraView.mode == Mode.PICTURE) {
                cameraView.takePicture()
            } else {
                gridButton.visibility = if (videoRecording) View.VISIBLE else View.GONE
                switchButton.visibility = if (videoRecording) View.VISIBLE else View.GONE
                resolutionButton.visibility = if (videoRecording) View.VISIBLE else View.GONE
                if (videoRecording) {
                    if (System.currentTimeMillis() - lastClickTime >= CLICK_DELAY) {
                        cameraView.stopVideo()
                        videoRecording = false
                        gridButton.visibility = View.VISIBLE
                        switchButton.visibility = View.VISIBLE
                        resolutionButton.visibility = View.VISIBLE
                    }
                } else {
                    setVideoQuality()
                    lastClickTime = System.currentTimeMillis()
                    cameraView.takeVideo(MediaFileHandler.getTempFile())
                    captureButton.displayStopVideo()
                    durationView.start()
                    videoRecording = true
                    gridButton.visibility = View.GONE
                    switchButton.visibility = View.GONE
                    resolutionButton.visibility = View.GONE
                }
            }*/
        }

        binding.photoMode.setOnClickListener { onPhotoClicked() }
        binding.videoMode.setOnClickListener { onVideoClicked() }
        binding.gridButton.setOnClickListener { onGridClicked() }
        binding.switchButton.setOnClickListener { onSwitchClicked() }
        binding.previewImage.setOnClickListener { onPreviewClicked() }
        binding.resolutionButton.setOnClickListener { chooseVideoResolution() }

    }

    fun onSwitchClicked() {
        /*if (cameraView.facing == Facing.BACK) {
            cameraView.facing = Facing.FRONT
            switchButton.displayFrontCamera()
        } else {
            cameraView.facing = Facing.BACK
            switchButton.displayBackCamera()
        }*/
    }

    fun onGridClicked() {
       /* if (cameraView.grid == Grid.DRAW_3X3) {
            cameraView.grid = Grid.OFF
            gridButton.displayGridOff()
        } else {
            cameraView.grid = Grid.DRAW_3X3
            gridButton.displayGridOn()
        }*/
    }

    fun onPreviewClicked() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.PHOTO_VIDEO_FILTER, "filter")
        startActivity(intent)
        finish()
    }

    fun chooseVideoResolution() {
        /*if (videoResolutionManager != null) {
            videoQualityDialog = DialogsUtil.showVideoResolutionDialog(
                this,
                { videoSize: SizeSelector -> setVideoSize(videoSize) }, videoResolutionManager
            )
        }*/
    }

    fun onPhotoClicked() {
        if (modeLocked) {
            return
        }
        if (System.currentTimeMillis() - lastClickTime < CLICK_MODE_DELAY) {
            return
        }
       /* if (cameraView.mode == Mode.PICTURE) {
            return
        }
        if (cameraView.flash == Flash.TORCH) {
            cameraView.flash = Flash.AUTO
        }*/
        setPhotoActive()
        captureButton.displayPhotoButton()
        //cameraView.mode = Mode.PICTURE
        mode = CameraMode.PHOTO
        resetZoom()
        lastClickTime = System.currentTimeMillis()
    }

    fun onVideoClicked() {
        if (modeLocked) {
            return
        }
        if (System.currentTimeMillis() - lastClickTime < CLICK_MODE_DELAY) {
            return
        }
       /* if (cameraView.mode == Mode.VIDEO) {
            return
        }
        cameraView.mode = Mode.VIDEO*/
        turnFlashDown()
        captureButton.displayVideoButton()
        setVideoActive()
        mode = CameraMode.VIDEO
        resetZoom()
        lastClickTime = System.currentTimeMillis()
    }

    override fun onAddingStart() {
        progressDialog = DialogsUtil.showLightProgressDialog(
            this,
            getString(R.string.gallery_dialog_expl_encrypting)
        )
        if (Preferences.isShutterMute()) {
            val mgr = getSystemService(AUDIO_SERVICE) as AudioManager
            mgr.setStreamMute(AudioManager.STREAM_SYSTEM, false)
        }
    }

    override fun onAddingEnd() {
        hideProgressDialog()
        showToast(R.string.gallery_toast_file_encrypted)
    }

    override fun onAddSuccess(bundle: VaultFile) {
        capturedMediaFile = bundle
        if (intentMode != IntentMode.COLLECT) {
            previewView.visibility = View.VISIBLE
            Glide.with(this).load(bundle.thumb).into(previewView)
        }
        if (!Preferences.isAnonymousMode()) {
            attachMediaFileMetadata(capturedMediaFile, metadataAttacher)
        } else {
            returnIntent(bundle)
        }
        MyApplication.bus().post(CaptureEvent())
    }

    override fun onAddError(error: Throwable) {
        showToast(R.string.gallery_toast_fail_saving_file)
    }

    override fun onMetadataAttached(vaultFile: VaultFile) {
        returnIntent(vaultFile)

        //scheduleFileUpload(capturedMediaFile);
    }

    private fun returnIntent(vaultFile: VaultFile) {
        val data = Intent()
        if (intentMode == IntentMode.ODK) {
            capturedMediaFile!!.metadata = vaultFile.metadata
            data.putExtra(MEDIA_FILE_KEY, capturedMediaFile)
            setResult(RESULT_OK, data)
            finish()
        } else if (intentMode == IntentMode.COLLECT) {
            capturedMediaFile!!.metadata = vaultFile.metadata
            val list: MutableList<String> = ArrayList()
            list.add(vaultFile.id)
            data.putExtra(VAULT_FILE_KEY, Gson().toJson(list))
            setResult(RESULT_OK, data)
            finish()
        } else {
            data.putExtra(C.CAPTURED_MEDIA_FILE_ID, vaultFile.metadata)
            setResult(RESULT_OK, data)
        }
    }

    override fun onMetadataAttachError(throwable: Throwable) {
        onAddError(throwable)
    }

    override fun rotateViews(rotation: Int) {
        gridButton.rotateView(rotation)
        switchButton.rotateView(rotation)
        flashButton.rotateView(rotation)
        durationView.rotateView(rotation)
        captureButton.rotateView(rotation)
        if (mode != CameraMode.PHOTO) {
            resolutionButton.rotateView(rotation)
        }
        if (intentMode != IntentMode.COLLECT) {
            previewView.animate().rotation(rotation.toFloat()).start()
        }
    }

    override fun onLastMediaFileSuccess(vaultFile: VaultFile) {
        if (intentMode != IntentMode.COLLECT) {
            previewView.visibility = View.VISIBLE
            glide!!.load(VaultFileLoaderModel(vaultFile, VaultFileLoaderModel.LoadType.THUMBNAIL))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(previewView)
        }
    }

    override fun onLastMediaFileError(throwable: Throwable) {
        if (intentMode != IntentMode.COLLECT || intentMode == IntentMode.ODK) {
            previewView.visibility = View.GONE
        }
    }

    override fun getContext(): Context {
        return this
    }

    override fun onMediaFilesUploadScheduled() {}
    override fun onMediaFilesUploadScheduleError(throwable: Throwable) {}
    override fun onGetMediaFilesSuccess(mediaFiles: List<VaultFile>) {}
    override fun onGetMediaFilesError(error: Throwable) {}

    private fun resetZoom() {
        zoomLevel = 0
        mSeekBar.progress = 0
        setCameraZoom()
    }


    private fun setCameraZoom() {
        //cameraView.zoom = zoomLevel.toFloat() / 100
    }

    private fun maybeStopVideoRecording(): Boolean {
        if (videoRecording) {
            captureButton.performClick()
            return true
        }
        return false
    }

    private fun stopPresenter() {
        if (presenter != null) {
            presenter!!.destroy()
            presenter = null
        }
    }

    private fun showConfirmVideoView(video: File) {
        captureButton.displayVideoButton()
        durationView.stop()
        presenter!!.addMp4Video(video, currentRootParent)
    }

    private fun setupCameraView() {
        if (mode == CameraMode.PHOTO) {
          //  cameraView.mode = Mode.PICTURE
            captureButton.displayPhotoButton()
        } else {
           // cameraView.mode = Mode.VIDEO
            captureButton.displayVideoButton()
        }

        //cameraView.setEnabled(PermissionUtil.checkPermission(this, Manifest.permission.CAMERA));
        /*cameraView.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS)
        setOrientationListener()
        cameraView.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                presenter!!.addJpegPhoto(result.data, currentRootParent)
            }

            override fun onVideoTaken(result: VideoResult) {
                showConfirmVideoView(result.file)
            }

            override fun onCameraError(exception: CameraException) {
                Timber.e(exception) //TODO Crahslytics removed
            }

            override fun onCameraOpened(options: CameraOptions) {
                if (options.supports(Grid.DRAW_3X3)) {
                    gridButton.visibility = View.VISIBLE
                    setUpCameraGridButton()
                } else {
                    gridButton.visibility = View.GONE
                }
                if (options.supportedFacing.size < 2) {
                    switchButton.visibility = View.GONE
                } else {
                    switchButton.visibility = View.VISIBLE
                    setupCameraSwitchButton()
                }
                if (options.supportedFlash.size < 2) {
                    flashButton.visibility = View.INVISIBLE
                } else {
                    flashButton.visibility = View.VISIBLE
                    setupCameraFlashButton(options.supportedFlash)
                }
                if (options.supportedVideoSizes.size > 0) {
                    videoResolutionManager = VideoResolutionManager(options.supportedVideoSizes)
                }
                // options object has info
                super.onCameraOpened(options)
            }
        })*/
        mSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                zoomLevel = i
                setCameraZoom()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        startCamera()
    }

    private fun setupCameraModeButton() {
       /* if (cameraView.mode == Mode.PICTURE) {
            setPhotoActive()
        } else {
            setVideoActive()
        }*/
    }

    private fun setUpCameraGridButton() {
       /* if (cameraView.grid == Grid.DRAW_3X3) {
            gridButton.displayGridOn()
        } else {
            gridButton.displayGridOff()
        }*/
    }

    private fun setupCameraSwitchButton() {
       /* if (cameraView.facing == Facing.FRONT) {
            switchButton.displayFrontCamera()
        } else {
            switchButton.displayBackCamera()
        }*/
    }

    private fun setupImagePreview() {
        if (intentMode == IntentMode.COLLECT || intentMode == IntentMode.ODK) {
            previewView.visibility = View.GONE
        }
    }

   /* private fun setupCameraFlashButton(supported: Collection<Flash>) {
        if (cameraView.flash == Flash.AUTO) {
            flashButton.displayFlashAuto()
        } else if (cameraView.flash == Flash.OFF) {
            flashButton.displayFlashOff()
        } else {
            flashButton.displayFlashOn()
        }
        flashButton.setOnClickListener { view: View? ->
            if (cameraView.mode == Mode.VIDEO) {
                if (cameraView.flash == Flash.OFF && supported.contains(
                        Flash.TORCH
                    )
                ) {
                    flashButton.displayFlashOn()
                    cameraView.flash = Flash.TORCH
                } else {
                    turnFlashDown()
                }
            } else {
                if (cameraView.flash == Flash.ON || cameraView.flash == Flash.TORCH) {
                    turnFlashDown()
                } else if (cameraView.flash == Flash.OFF && supported.contains(
                        Flash.AUTO
                    )
                ) {
                    flashButton.displayFlashAuto()
                    cameraView.flash = Flash.AUTO
                } else {
                    flashButton.displayFlashOn()
                    cameraView.flash = Flash.ON
                }
            }
        }
    }*/

    private fun turnFlashDown() {
      /*  flashButton.displayFlashOff()
        cameraView.flash = Flash.OFF*/
    }

    private fun hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
    }

    private fun setOrientationListener() {
        mOrientationEventListener = object : OrientationEventListener(
            this, SensorManager.SENSOR_DELAY_NORMAL
        ) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation != ORIENTATION_UNKNOWN) {
                    presenter!!.handleRotation(orientation)
                }
            }
        }
    }

    private fun setPhotoActive() {
        videoLine.visibility = View.GONE
        photoLine.visibility = View.VISIBLE
        photoModeText.alpha = 1f
        videoModeText.alpha = if (modeLocked) 0.1f else 0.5f
        resolutionButton.visibility = View.GONE
    }

    private fun setVideoActive() {
        videoLine.visibility = View.VISIBLE
        photoLine.visibility = View.GONE
        videoModeText.alpha = 1f
        photoModeText.alpha = if (modeLocked) 0.1f else 0.5f
        if (videoResolutionManager != null) {
            resolutionButton.visibility = View.VISIBLE
        }
    }

    private fun hideVideoResolutionDialog() {
        if (videoQualityDialog != null) {
            videoQualityDialog!!.dismiss()
            videoQualityDialog = null
        }
    }

    private fun setVideoQuality() {
      /*  if (cameraView != null && videoResolutionManager != null) {
            cameraView.setVideoSize(videoResolutionManager!!.videoSize)
        }*/
    }

    /*private fun setVideoSize(videoSize: SizeSelector) {
        if (cameraView != null) {
            cameraView.setVideoSize(videoSize)
            cameraView.close()
            cameraView.open()
        }
    }*/

    private fun setupShutterSound() {
       // cameraView.playSounds = !Preferences.isShutterMute()
    }

    enum class CameraMode {
        PHOTO, VIDEO
    }

    enum class IntentMode {
        COLLECT, RETURN, STAND, ODK
    }

    companion object {
        @kotlin.jvm.JvmField
        var INTENT_MODE: String = "im"

        @kotlin.jvm.JvmField
        var CAMERA_MODE = "cm"

        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }

    private fun startCamera() {
        // This is the CameraX PreviewView where the camera will be rendered

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: InterruptedException) {
                Toast.makeText(context, "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            } catch (e: ExecutionException) {
                Toast.makeText(context, "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            }

            // The display information
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            // The ratio for the output image and preview
            val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            // The display rotation
            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            // The Configuration of camera preview
            preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio) // set the camera aspect ratio
                .setTargetRotation(rotation) // set the camera rotation
                .build()

            // The Configuration of image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) // setting to have pictures with highest quality possible (may be slow)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF) // set capture flash
                .setTargetAspectRatio(aspectRatio) // set the capture aspect ratio
                .setTargetRotation(rotation) // set the capture rotation
                .build()

           // checkForHdrExtensionAvailability()

            // The Configuration of image analyzing
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio) // set the analyzer aspect ratio
                .setTargetRotation(rotation) // set the analyzer rotation
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // in our analysis, we care about the latest image
                .build()
                .also { setLuminosityAnalyzer(it) }

            // Unbind the use-cases before rebinding them
            localCameraProvider.unbindAll()
            // Bind all use cases to the camera with lifecycle
            bindToLifecycle(localCameraProvider, viewFinder)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun setLuminosityAnalyzer(imageAnalysis: ImageAnalysis) {
        // Use a worker thread for image analysis to prevent glitches
        val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
        imageAnalysis.setAnalyzer(
            ThreadExecutor(Handler(analyzerThread.looper)),
            LuminosityAnalyzer()
        )
    }

    private fun bindToLifecycle(localCameraProvider: ProcessCameraProvider, viewFinder: PreviewView) {
        try {
            localCameraProvider.bindToLifecycle(
                this, // current lifecycle owner
                hdrCameraSelector ?: lensFacing, // either front or back facing
                preview, // camera preview use case
                imageCapture, // image capture use case
                imageAnalyzer, // image analyzer use case
            ).run {
                // Init camera exposure control
                cameraInfo.exposureState.run {
                    val lower = exposureCompensationRange.lower
                    val upper = exposureCompensationRange.upper

                    /*binding.sliderExposure.run {
                        valueFrom = lower.toFloat()
                        valueTo = upper.toFloat()
                        stepSize = 1f
                        value = exposureCompensationIndex.toFloat()

                        addOnChangeListener { _, value, _ ->
                            cameraControl.setExposureCompensationIndex(value.toInt())
                        }
                    }*/
                }
            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Timber.e("Failed to bind use cases")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun captureImage() {
        val localImageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        // Setup image capture metadata
        val metadata = ImageCapture.Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val tempFile = MediaFileHandler.getTempFile()
        //cameraView.addCameraListener(object : CameraListener() {
          // override fun onPictureTaken(result: PictureResult) {
                //presenter!!.addJpegPhoto(result.data, currentRootParent)
        //    }
        // Options fot the output image file
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
           /* val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            val contentResolver = context.contentResolver

            // Create the output uri
            val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)*/

            ImageCapture.OutputFileOptions.Builder(tempFile)
        } else {
           /* File(outputDirectory).mkdirs()
            val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")*/

            ImageCapture.OutputFileOptions.Builder(tempFile)
        }.setMetadata(metadata).build()

        localImageCapture.takePicture(
            outputOptions, // the options needed for the final image
            context.mainExecutor, // the executor, on which the task will run
            object : ImageCapture.OnImageSavedCallback { // the callback, about the result of capture process
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // This function is called if capture is successfully completed
                    outputFileResults.savedUri
                        ?.let { uri ->
                            val iStream = getContentResolver().openInputStream(uri)
                            presenter!!.addJpegPhoto(MediaFileHandler.getBytes(iStream), currentRootParent)
                            //setGalleryThumbnail(tempFile.toURI())
                            Timber.d("Photo saved in $uri")
                        }
                        //?: presenter?.getLastMediaFile()
                }

                override fun onError(exception: ImageCaptureException) {
                    // This function is called if there is an errors during capture process
                    val msg = "Photo capture failed: ${exception.message}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Timber.e(msg)
                    exception.printStackTrace()
                }
            }
        )
    }
}