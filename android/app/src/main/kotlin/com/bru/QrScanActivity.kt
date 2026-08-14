package com.bru

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.InputType
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.abs

class QrScanActivity : Activity() {

    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var manualInput: EditText
    private val main = Handler(Looper.getMainLooper())
    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    private val decoder = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true,
            ),
        )
    }

    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation = 90
    private var started = false
    private var askedPermission = false
    private var done = false
    private var yBuf = ByteArray(0)
    private var previewPixels = IntArray(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }
        status = text("Point at the QR code shown by the client", 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val previewFrame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                status,
                FrameLayout.LayoutParams(MATCH, WRAP).apply { gravity = Gravity.BOTTOM },
            )
        }

        val manualBar = buildManualBar()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            addView(previewFrame, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(manualBar, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            ).bottom
            manualBar.updatePadding(bottom = dp(10) + bottom)
            insets
        }
        setContentView(root)
    }

    private fun buildManualBar(): View {
        manualInput = EditText(this).apply {
            hint = "or paste the pair link"
            setSingleLine()
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            typeface = MONO
            textSize = 13f
            setTextColor(FG)
            setHintTextColor(MUTED)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    pairManual()
                    true
                } else {
                    false
                }
            }
        }
        val pairButton = button("Pair", filled = true) { pairManual() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(manualInput, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(pairButton, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        }
    }

    private fun pairManual() {
        val text = manualInput.text.toString().trim()
        if (text.isEmpty()) return
        if (Pairing.parse(text) != null) finishWith(text) else status.text = "That's not a valid pairing link"
    }

    private fun finishWith(contents: String) {
        if (done) return
        done = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, contents))
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (!askedPermission) {
            askedPermission = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode != REQ_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            status.text = "Camera denied — paste the link below"
        }
    }

    private fun startCamera() {
        if (started) return
        started = true
        val thread = HandlerThread("bru-qr").also { it.start() }
        bgThread = thread
        bgHandler = Handler(thread.looper).apply { post { openCamera() } }
    }

    private fun stopCamera() {
        bgHandler?.post {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { imageReader?.close() }
            session = null
            device = null
            imageReader = null
        }
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
        started = false
    }

    private fun openCamera() {
        try {
            val cameraId = backCameraId() ?: return cameraUnavailable()
            val ch = cameraManager.getCameraCharacteristics(cameraId)
            sensorOrientation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val size = chooseSize(ch)
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
                .apply { setOnImageAvailableListener({ onFrame(it) }, bgHandler) }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return cameraUnavailable()
            }
            cameraManager.openCamera(cameraId, deviceCallback(), bgHandler)
        } catch (e: Exception) {
            cameraUnavailable()
        }
    }

    private fun deviceCallback() = object : CameraDevice.StateCallback() {
        @Suppress("DEPRECATION")
        override fun onOpened(camera: CameraDevice) {
            device = camera
            val surface = imageReader!!.surface
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                            )
                        }.build()
                        runCatching { s.setRepeatingRequest(req, null, bgHandler) }
                            .onFailure { cameraUnavailable() }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) = cameraUnavailable()
                },
                bgHandler,
            )
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            device = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            device = null
            cameraUnavailable()
        }
    }

    private fun onFrame(source: ImageReader) {
        val image = source.acquireLatestImage() ?: return
        try {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            if (yBuf.size != w * h) yBuf = ByteArray(w * h)
            val y = yBuf
            for (row in 0 until h) {
                buffer.position(row * rowStride)
                buffer.get(y, row * w, w)
            }
            val bitmap = grayscale(y, w, h)
            main.post { preview.setImageBitmap(bitmap) }

            val luminance = PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false)
            val result = try {
                decoder.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
            } catch (e: Exception) {
                null
            } finally {
                decoder.reset()
            }
            val text = result?.text
            if (text != null && Pairing.parse(text) != null) {
                main.post { finishWith(text) }
            }
        } catch (e: Exception) {
        } finally {
            image.close()
        }
    }

    private fun grayscale(src: ByteArray, w: Int, h: Int): Bitmap {
        val step = 4
        val turned = sensorOrientation == 90 || sensorOrientation == 270
        val pw = (if (turned) h else w) / step
        val ph = (if (turned) w else h) / step
        if (previewPixels.size != pw * ph) previewPixels = IntArray(pw * ph)
        var i = 0
        for (yy in 0 until ph) {
            for (xx in 0 until pw) {
                val sx: Int
                val sy: Int
                when (sensorOrientation) {
                    90 -> { sx = yy * step; sy = h - 1 - xx * step }
                    270 -> { sx = w - 1 - yy * step; sy = xx * step }
                    180 -> { sx = w - 1 - xx * step; sy = h - 1 - yy * step }
                    else -> { sx = xx * step; sy = yy * step }
                }
                val l = src[sy * w + sx].toInt() and 0xFF
                previewPixels[i++] = (0xFF shl 24) or (l shl 16) or (l shl 8) or l
            }
        }
        return Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            .apply { setPixels(previewPixels, 0, pw, 0, 0, pw, ph) }
    }

    private fun cameraUnavailable() {
        main.post { status.text = "Camera unavailable — paste the link below" }
    }

    private fun backCameraId(): String? = cameraManager.cameraIdList.firstOrNull {
        cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    } ?: cameraManager.cameraIdList.firstOrNull()

    private fun chooseSize(ch: CameraCharacteristics): Size {
        val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
        return sizes?.minByOrNull { abs(it.width * it.height - 1280 * 720) } ?: Size(1280, 720)
    }

    companion object {
        const val EXTRA_RESULT = "qr_result"
        private const val REQ_CAMERA = 2

        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
