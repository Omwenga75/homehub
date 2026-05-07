package com.example.homehub.utils

import android.app.Dialog
import android.content.Context
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
import com.example.homehub.utils.ProfilePictureUtils
import com.example.homehub.databinding.DialogUserVerificationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UserVerificationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogUserVerificationBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: com.example.homehub.auth.SessionManager

    private var idCardFrontBitmap: Bitmap? = null
    private var idCardBackBitmap: Bitmap? = null
    
    private var idFrontPath: String? = null
    private var idBackPath: String? = null
    
    private var isCameraStarted = false

    private var currentCaptureMode: String? = null // "ID_FRONT", "ID_BACK"
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val TAG = "UserVerificationBottomSheet"

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
        _binding = DialogUserVerificationBinding.inflate(inflater, container, false)
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
        sessionManager = com.example.homehub.auth.SessionManager(requireContext())

        setupCaptureListeners()
        setupBioStep()
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId).get().addOnSuccessListener { doc ->
                val role = doc.getString("userType")?.uppercase() ?: "STUDENT"
                if (role == "STUDENT") {
                    binding.idNumberLayout.hint = "Registration Number"
                    binding.idFrontPlaceholder.findViewById<android.widget.TextView>(com.example.homehub.R.id.idFrontPlaceholderText)?.text = "CAPTURE REGISTRATION CARD FRONT"
                    binding.idBackPlaceholder.findViewById<android.widget.TextView>(com.example.homehub.R.id.idBackPlaceholderText)?.text = "CAPTURE REGISTRATION CARD BACK"
                } else {
                    binding.idNumberLayout.hint = "National ID Number"
                    binding.idFrontPlaceholder.findViewById<android.widget.TextView>(com.example.homehub.R.id.idFrontPlaceholderText)?.text = "CAPTURE NATIONAL ID FRONT"
                    binding.idBackPlaceholder.findViewById<android.widget.TextView>(com.example.homehub.R.id.idBackPlaceholderText)?.text = "CAPTURE NATIONAL ID BACK"
                }
            }
        }
        
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
    }

    private fun setupBioStep() {
        // Date of birth initialization removed as requested
    }

    private fun validateAll(): Boolean {
        val name = binding.fullNameInput.text.toString().trim()
        val id = binding.idNumberInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()
        val location = binding.locationInput.text.toString().trim()

        if (name.isEmpty() || id.isEmpty() || phone.isEmpty() || location.isEmpty()) {
            Toast.makeText(context, "Please complete all identity fields, including your location", Toast.LENGTH_SHORT).show()
            return false
        }

        val nameValidation = ValidationUtils.isValidName(name)
        if (!nameValidation.isValid) {
            binding.fullNameInput.error = nameValidation.errorMessage
            Toast.makeText(context, nameValidation.errorMessage, Toast.LENGTH_SHORT).show()
            return false
        }

        // Fetch current role for ID validation
        val role = sessionManager.getUserRole() ?: "STUDENT" // Safe fallback
        val idValidation = ValidationUtils.isValidId(id, role)
        if (!idValidation.isValid) {
            binding.idNumberInput.error = idValidation.errorMessage
            Toast.makeText(context, idValidation.errorMessage, Toast.LENGTH_SHORT).show()
            return false
        }

        if (idCardFrontBitmap == null || idCardBackBitmap == null) {
            Toast.makeText(context, "Capture both Front and Back of ID", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun handleCaptureClick(mode: String) {
        currentCaptureMode = mode
        val bitmap = when (mode) {
            "ID_FRONT" -> idCardFrontBitmap
            "ID_BACK" -> idCardBackBitmap
            else -> null
        }
        
        if (!isCameraStarted && bitmap == null) {
            checkCameraPermissionAndStart()
        } else if (isCameraStarted) {
            takePhoto()
        } else {
            // Retake
            when (mode) {
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
                "ID_FRONT" -> binding.idFrontViewFinder
                "ID_BACK" -> binding.idBackViewFinder
                else -> binding.idFrontViewFinder
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                
                viewFinder.visibility = View.VISIBLE
                when (mode) {
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
                        bitmap = ImageWatermarkUtils.addWatermark(bitmap, 0.0, 0.0)

                        when (mode) {
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
                "ID_FRONT" -> idFrontPath = persistentFile.absolutePath
                "ID_BACK" -> idBackPath = persistentFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap locally: ${e.message}")
        }
    }

    private fun submitApplication() {
        if (!validateAll()) return

        val name = binding.fullNameInput.text.toString().trim()
        val idNo = binding.idNumberInput.text.toString().trim()
        val phoneNo = binding.phoneInput.text.toString().trim()
        val location = binding.locationInput.text.toString().trim()

        if (idFrontPath == null || idBackPath == null) {
            Toast.makeText(context, "Please capture all required photos", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        
        val kycData = androidx.work.workDataOf(
            KYCUploadWorker.KEY_FULL_NAME to name,
            KYCUploadWorker.KEY_EMAIL to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
            KYCUploadWorker.KEY_ID_NUMBER to idNo,
            "phone" to phoneNo,
            KYCUploadWorker.KEY_ADDRESS to location,
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

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("verificationStatus", "PENDING")
        }

        setLoading(false)
        
        // Update local cache immediately so dashboard reflects name
        sessionManager.saveCachedUserProfile(name, ProfilePictureUtils.getInitials(name), null)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verification Submitted")
            .setMessage("Your identity verification has been submitted and is being processed. You will be notified once the review is complete.")
            .setPositiveButton("Done") { _, _ ->
                VerificationManager.onVerificationSubmitted()
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
