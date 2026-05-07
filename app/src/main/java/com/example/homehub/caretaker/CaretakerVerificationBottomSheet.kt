package com.example.homehub.caretaker

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.homehub.R
import com.example.homehub.utils.ValidationUtils
import com.example.homehub.utils.ImageWatermarkUtils
import com.example.homehub.utils.KYCUploadWorker
import com.example.homehub.utils.VerificationManager
import com.example.homehub.databinding.DialogCaretakerVerificationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UserVerificationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogCaretakerVerificationBinding? = null
    private val binding get() = _binding!!

    private var selfieBitmap: Bitmap? = null
    private var idCardFrontBitmap: Bitmap? = null
    private var idCardBackBitmap: Bitmap? = null
    
    private var selfiePath: String? = null
    private var idFrontPath: String? = null
    private var idBackPath: String? = null
    
    private var isCameraStarted = false
    private var isGpsRetrieved = false

    private var currentCaptureMode: String? = null // "SELFIE", "ID_FRONT", "ID_BACK"
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val TAG = "UserVerificationBottomSheet"

    private val requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fetchLocation()
        } else {
            binding.gpsStatusText.text = "Location access denied. Please enable for verification."
            binding.gpsProgressBar.visibility = View.GONE
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            currentCaptureMode?.let { startCamera(it) }
        } else {
            showErrorDialog("Permission Denied", "Camera permission is required for verification.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCaretakerVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isHideable = false
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupCaptureListeners()
        setupBioStep()
        
        // GPS implementation
        checkLocationPermission()

        binding.submitButton.setOnClickListener {
            submitApplication()
        }
    }

    private fun setupCaptureListeners() {
        binding.idCardFrontCard.setOnClickListener {
            handleCaptureClick("ID_FRONT")
        }

        binding.idCardBackCard.setOnClickListener {
            handleCaptureClick("ID_BACK")
        }

        binding.selfieCard.setOnClickListener {
            handleCaptureClick("SELFIE")
        }

        binding.captureSelfieBtn.setOnClickListener {
            handleCaptureClick("SELFIE")
        }
    }

    private fun setupBioStep() {
        // Date of birth initialization removed as requested
    }

    private fun validateAll(): Boolean {
        val name = binding.fullNameInput.text.toString().trim()
        val id = binding.idNumberInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()

        if (name.isEmpty() || id.isEmpty() || phone.isEmpty()) {
            Toast.makeText(context, "Please complete all identity fields", Toast.LENGTH_SHORT).show()
            return false
        }

        val nameValidation = ValidationUtils.isValidName(name)
        if (!nameValidation.isValid) {
            binding.fullNameInput.error = nameValidation.errorMessage
            Toast.makeText(context, nameValidation.errorMessage, Toast.LENGTH_SHORT).show()
            return false
        }

        if (idCardFrontBitmap == null || idCardBackBitmap == null) {
            Toast.makeText(context, "Capture both Front and Back of ID", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selfieBitmap == null) {
            Toast.makeText(context, "Please take a selfie for biometric audit", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }


    private fun handleCaptureClick(mode: String) {
        currentCaptureMode = mode
        val bitmap = when (mode) {
            "SELFIE" -> selfieBitmap
            "ID_FRONT" -> idCardFrontBitmap
            "ID_BACK" -> idCardBackBitmap
            else -> null
        }
        
        if (!isCameraStarted && bitmap == null) {
            checkCameraPermissionAndStart()
        } else if (isCameraStarted) {
            // Simply take photo, no liveness required
            takePhoto()
        } else {
            // Already have a photo, tap to retake
            when (mode) {
                "SELFIE" -> {
                    selfieBitmap = null
                    binding.selfieImage.visibility = View.GONE
                    binding.viewFinder.visibility = View.VISIBLE
                }
                "ID_FRONT" -> {
                    idCardFrontBitmap = null
                    binding.idCardFrontImage.visibility = View.GONE
                    binding.idFrontPlaceholder.visibility = View.VISIBLE
                }
                "ID_BACK" -> {
                    idCardBackBitmap = null
                    binding.idCardBackImage.visibility = View.GONE
                    binding.idBackPlaceholder.visibility = View.VISIBLE
                }
            }
            checkCameraPermissionAndStart()
        }
    }

    private fun checkLocationPermission() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            fetchLocation()
        } else {
            binding.gpsProgressBar.visibility = View.VISIBLE
            requestLocationPermission.launch(permissions)
        }
    }

    private fun fetchLocation() {
        if (context == null) return
        binding.gpsProgressBar.visibility = View.VISIBLE
        binding.gpsStatusText.text = "fetching location..."

        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    isGpsRetrieved = true
                    
                    // Geocode for real address
                    try {
                        val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val city = addresses[0].locality ?: addresses[0].subAdminArea ?: "Current Location"
                            binding.gpsStatusText.text = "Location Tagged: $city"
                        } else {
                            binding.gpsStatusText.text = "Location tagged successfully"
                        }
                    } catch (e: Exception) {
                        binding.gpsStatusText.text = "Location tagged successfully"
                    }
                    
                    binding.gpsProgressBar.visibility = View.GONE
                } else {
                    binding.gpsStatusText.text = "Could not get GPS. Please enable Location Services."
                    binding.gpsProgressBar.visibility = View.GONE
                }
            }.addOnFailureListener {
                binding.gpsStatusText.text = "GPS Error: ${it.message}"
                binding.gpsProgressBar.visibility = View.GONE
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    private fun checkCameraPermissionAndStart() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            currentCaptureMode?.let { startCamera(it) }
        } else {
            requestCameraPermission.launch(permission)
        }
    }

    private fun startCamera(mode: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val viewFinder = when (mode) {
                "SELFIE" -> binding.viewFinder
                "ID_FRONT" -> binding.idFrontViewFinder
                "ID_BACK" -> binding.idBackViewFinder
                else -> binding.viewFinder
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = if (mode == "SELFIE") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                
                viewFinder.visibility = View.VISIBLE
                when (mode) {
                    "SELFIE" -> binding.selfieImage.visibility = View.GONE
                    "ID_FRONT" -> {
                        binding.idCardFrontImage.visibility = View.GONE
                        binding.idFrontPlaceholder.visibility = View.GONE
                    }
                    "ID_BACK" -> {
                        binding.idCardBackImage.visibility = View.GONE
                        binding.idBackPlaceholder.visibility = View.GONE
                    }
                }
                isCameraStarted = true
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showErrorDialog("Camera Error", "Failed to start camera: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val mode = currentCaptureMode ?: return

        val photoFile = File(requireContext().cacheDir, "${mode.lowercase()}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    showErrorDialog("Capture Error", "Failed to capture ${mode.lowercase()}: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (bitmap != null) {
                        // Apply Watermark
                        bitmap = ImageWatermarkUtils.addWatermark(bitmap, latitude, longitude)

                        when (mode) {
                            "SELFIE" -> {
                                selfieBitmap = bitmap
                                binding.selfieImage.setImageBitmap(bitmap)
                                binding.selfieImage.visibility = View.VISIBLE
                                binding.viewFinder.visibility = View.GONE
                            }
                            "ID_FRONT" -> {
                                idCardFrontBitmap = bitmap
                                binding.idCardFrontImage.setImageBitmap(bitmap)
                                binding.idCardFrontImage.visibility = View.VISIBLE
                                binding.idFrontViewFinder.visibility = View.GONE
                                binding.idFrontPlaceholder.visibility = View.GONE
                            }
                            "ID_BACK" -> {
                                idCardBackBitmap = bitmap
                                binding.idCardBackImage.setImageBitmap(bitmap)
                                binding.idCardBackImage.visibility = View.VISIBLE
                                binding.idBackViewFinder.visibility = View.GONE
                                binding.idBackPlaceholder.visibility = View.GONE
                            }
                        }

                        // Save watermarked bitmap to persistent storage for hybrid upload
                        saveBitmapLocally(bitmap, mode)

                        isCameraStarted = false
                        stopCamera()
                    }
                }
            }
        )
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind camera", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun saveBitmapLocally(bitmap: Bitmap, mode: String) {
        val kycFolder = File(requireContext().filesDir, "kyc_assets")
        if (!kycFolder.exists()) kycFolder.mkdirs()
        
        val fileName = "${mode.lowercase()}_${UUID.randomUUID().toString().take(8)}.jpg"
        val persistentFile = File(kycFolder, fileName)
        
        try {
            java.io.FileOutputStream(persistentFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            when (mode) {
                "SELFIE" -> selfiePath = persistentFile.absolutePath
                "ID_FRONT" -> idFrontPath = persistentFile.absolutePath
                "ID_BACK" -> idBackPath = persistentFile.absolutePath
            }
            Log.d(TAG, "Saved $mode locally: ${persistentFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap locally: ${e.message}")
        }
    }

    private fun submitApplication() {
        if (!validateAll()) return

        val name = binding.fullNameInput.text.toString().trim()
        val idNo = binding.idNumberInput.text.toString().trim()
        val phoneNo = binding.phoneInput.text.toString().trim()

        if (selfiePath == null || idFrontPath == null || idBackPath == null) {
            Toast.makeText(context, "Please capture all required photos", Toast.LENGTH_SHORT).show()
            return
        }

        if (latitude == null || longitude == null) {
            Toast.makeText(context, "GPS is required for security. Synchronizing location...", Toast.LENGTH_SHORT).show()
            fetchLocation()
            return
        }

        // Show loading state
        setLoading(true)
        
        // Enqueue WorkManager for Hybrid Upload
        val kycData = androidx.work.workDataOf(
            KYCUploadWorker.KEY_FULL_NAME to name,
            KYCUploadWorker.KEY_EMAIL to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            KYCUploadWorker.KEY_ID_NUMBER to idNo,
            "phone" to phoneNo,
            KYCUploadWorker.KEY_LATITUDE to (latitude ?: 0.0),
            KYCUploadWorker.KEY_LONGITUDE to (longitude ?: 0.0),
            KYCUploadWorker.KEY_SELFIE_PATH to selfiePath,
            KYCUploadWorker.KEY_ID_FRONT_PATH to idFrontPath,
            KYCUploadWorker.KEY_ID_BACK_PATH to idBackPath
        )

        val uploadRequest = androidx.work.OneTimeWorkRequestBuilder<KYCUploadWorker>()
            .setInputData(kycData)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()

        androidx.work.WorkManager.getInstance(requireContext())
            .enqueueUniqueWork("kyc_upload_${FirebaseAuth.getInstance().currentUser?.uid}", 
                androidx.work.ExistingWorkPolicy.REPLACE, uploadRequest)

        // Update status immediately so UI shows "PENDING"
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("verificationStatus", "PENDING")
        }

        // Inform user and dismiss
        setLoading(false)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verification Submitted")
            .setMessage("Your identity verification has been submitted and is being processed. You will be notified once the review is complete.")
            .setPositiveButton("Done") { _, _ ->
                dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkInfo = connectivityManager?.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }


    private fun showSuccessDialog() {
        if (context == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verification Submitted")
            .setMessage("Your details have been sent for review. Please wait for up to 24 hours while an admin verifies your identity. You will be notified once approved.")
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
                dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {
        if (context == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonContainer.alpha = if (isLoading) 0.5f else 1.0f
        binding.submitButton.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        const val TAG = "UserVerificationBottomSheet"
    }
}
