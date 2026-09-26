package com.example.smartgardenplanner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.StatFs
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream

/**
 * [NEW] T2-FUN-010 / HLR-SEN-010/020/090/100/110/160: the entire camera-capture subsystem was
 * previously absent from the codebase (no CameraX dependency, no capture UI). This class
 * implements the documented pipeline:
 *   - CameraX ProcessCameraProvider lifecycle binding (HLR-SEN-010)
 *   - Async capture via ImageCapture.OnImageSavedCallback-equivalent (HLR-SEN-020)
 *   - Storage floor check before allowing capture: block at <=5% free space (HLR-SEN-160 / T2-ERR-020)
 *   - Downscale to 1080px major axis + JPEG quality 85 (HLR-SEN-090/100)
 *   - OutOfMemoryError-safe fallback to the raw un-scaled image (HLR-SEN-110 / T2-ERR-030)
 *
 * Requires the module to declare the CameraX dependencies (see build.gradle.kts changes in the
 * accompanying patch notes) — this class alone does not add the Gradle dependency.
 */
class CameraCaptureManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    interface CaptureListener {
        fun onImageSaved(outputFile: File)
        fun onCaptureError(message: String)
        fun onLowLight(luxLevel: Float)
        fun onStorageBlocked()
    }

    /** HLR-SEN-160 / T2-ERR-020: hard floor at 5% free space — mirrors LIM-STOR-06 in the ICD. */
    fun isStorageAvailable(): Boolean {
        val stat = StatFs(context.filesDir.path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        if (totalBytes <= 0L) return true
        val freeRatio = availableBytes.toDouble() / totalBytes.toDouble()
        return freeRatio > 0.05
    }

    fun startPreview(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        listener: CaptureListener
    ) {
        if (!isStorageAvailable()) {
            listener.onStorageBlocked()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
            } catch (e: Exception) {
                listener.onCaptureError("ERR-CAM-001: Failed to bind camera lifecycle: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun capturePlotImage(plotId: Long, listener: CaptureListener) {
        val capture = imageCapture ?: run {
            listener.onCaptureError("ERR-CAM-002: Camera not initialized.")
            return
        }
        if (!isStorageAvailable()) {
            listener.onStorageBlocked()
            return
        }

        val tempFile = File(context.cacheDir, "sgp_capture_raw_${plotId}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val finalFile = processAndStore(tempFile, plotId)
                    if (finalFile != null) {
                        listener.onImageSaved(finalFile)
                    } else {
                        listener.onCaptureError("ERR-CAM-003: Post-processing failed.")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    listener.onCaptureError("ERR-CAM-004: ${exception.message}")
                }
            }
        )
    }

    /**
     * HLR-SEN-090/100: downscale to a 1080px major axis, JPEG quality 85.
     * HLR-SEN-110 / T2-ERR-030: on OutOfMemoryError, fall back to storing the raw captured file
     * unmodified rather than crashing — matches ERR-MEM-005 in the ICD message dictionary.
     */
    private fun processAndStore(rawFile: File, plotId: Long): File? {
        val outputFile = File(context.filesDir, "sgp_plot_${plotId}_${System.currentTimeMillis()}.jpg")
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(rawFile.path, options)

            val majorAxis = maxOf(options.outWidth, options.outHeight)
            val scaleFactor = if (majorAxis > 1080) (majorAxis / 1080) else 1
            val sampleSize = Integer.highestOneBit(maxOf(1, scaleFactor))

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(rawFile.path, decodeOptions)
                ?: return rawFile.also { rawFile.copyTo(outputFile, overwrite = true) }

            val finalBitmap = downscaleToMajorAxis(bitmap, 1080)
            FileOutputStream(outputFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            rawFile.delete()
            outputFile
        } catch (oom: OutOfMemoryError) {
            // [HLR-SEN-110] Safe fallback: preserve the raw, un-overlayed capture instead of crashing.
            System.gc()
            try {
                rawFile.copyTo(outputFile, overwrite = true)
                rawFile.delete()
                outputFile
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downscaleToMajorAxis(bitmap: Bitmap, targetMajorAxis: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val majorAxis = maxOf(width, height)
        if (majorAxis <= targetMajorAxis) return bitmap

        val scale = targetMajorAxis.toFloat() / majorAxis.toFloat()
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun stopPreview() {
        cameraProvider?.unbindAll()
    }
}

/**
 * [NEW] T2-ENV-030 / HLR-SEN-030/040: ambient light monitoring, independent of the capture
 * pipeline above so it can run as soon as the preview is active.
 */
class AmbientLightMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var listener: ((Float, Boolean) -> Unit)? = null

    fun isAvailable(): Boolean = lightSensor != null

    fun start(onLuxReading: (luxLevel: Float, isLowLight: Boolean) -> Unit) {
        listener = onLuxReading
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            listener?.invoke(lux, lux < 10f) // ERR-SENS-003 threshold
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
