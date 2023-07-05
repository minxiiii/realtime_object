package com.example.realtime_object

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale

class ObjectDetection : AppCompatActivity() {

    lateinit var labels:List<String>
    var colors = listOf(
        Color.BLUE, Color.BLACK,)
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap:Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var tts: TextToSpeech
    private var isDetectionComplete = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar()?.hide();
        setContentView(R.layout.activity_main)
        get_permission()




        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val classes = outputs.classesAsTensorBuffer.floatArray
                val maxScoreIndex = outputs.scoresAsTensorBuffer.floatArray.indices.maxByOrNull { outputs.scoresAsTensorBuffer.floatArray[it] } ?: -1
                val scores = floatArrayOf(outputs.scoresAsTensorBuffer.floatArray[maxScoreIndex])


                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)


                val objectName = findViewById<TextView>(R.id.objectName)
                if (!isDetectionComplete && scores.isNotEmpty() && scores[0] > 0.5) { //score higher than 0.5
                    val index = maxScoreIndex //take the object that has the highest scoring detection
                    // Draw bounding boxes and labels
                    objectName.text = labels[classes[index].toInt()]
                    objectName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
                   objectName.setTextColor(Color.BLUE)


                    tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
                        if (status != TextToSpeech.ERROR) {
                            // Choose language
                            tts.language = Locale.ENGLISH
                            tts.speak(labels.get(classes.get(index).toInt()),TextToSpeech.QUEUE_ADD, null)
                            showDialog()
                            isDetectionComplete = true
                        }

                    })




                    //extract text from labels based on class index

                }

                imageView.setImageBitmap(mutable)
                //storing captured frames


            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

    }

    private fun showDialog() {
        val dialog = Dialog(this, R.style.DialogTheme)
        dialog.setContentView(R.layout.popup)

        val dialogTextView = dialog.findViewById<TextView>(R.id.dialogTextView)


        dialogTextView.text = "Swipe Up for text detection"
        dialogTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        dialogTextView.setTextColor(Color.RED)


        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // main screen not dim when dialog pops up
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setGravity(Gravity.CENTER)
        }
        dialog.show()

    }


    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }



    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }
        }, handler)
    }

    //request for permission to start camera
    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }


}