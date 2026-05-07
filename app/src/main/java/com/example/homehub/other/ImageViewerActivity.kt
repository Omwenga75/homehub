package com.example.homehub.other

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.example.homehub.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var closeButton: FloatingActionButton

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null
    private var scaleFactor = 1.0f

    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        imageView = findViewById(R.id.fullScreenImageView)
        closeButton = findViewById(R.id.closeButton)

        // Get image URL from intent
        val imageUrl = intent.getStringExtra("image_url")
        val imageTitle = intent.getStringExtra("image_title")

        // Load image using Coil
        loadImage(imageUrl)

        closeButton.setOnClickListener {
            finish()
        }

        // Setup gesture detectors for zoom and pan
        setupGestureDetectors()

        // Enable full-screen immersive mode
        enableFullScreen()

        // Handle double tap to zoom
        setupDoubleTapZoom()
    }

    private fun loadImage(imageUrl: String?) {
        if (imageUrl != null) {
            imageView.load(imageUrl) {
                placeholder(R.drawable.ks4)
                error(R.drawable.ks4)
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        // Image loaded successfully
                    },
                    onError = { _, _ ->
                        // Fallback to default image on error
                        imageView.setImageResource(R.drawable.ks4)
                    }
                )
            }
        } else {
            // Fallback to default image
            imageView.setImageResource(R.drawable.ks4)
        }
    }

    private fun setupGestureDetectors() {
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        gestureDetector = GestureDetector(this, GestureListener())

        imageView.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            gestureDetector?.onTouchEvent(event)
            true
        }
    }

    private fun setupDoubleTapZoom() {
        imageView.setOnClickListener {
            // Toggle between zoomed and normal state
            if (scaleFactor > 1.0f) {
                // Reset zoom
                imageView.scaleX = 1.0f
                imageView.scaleY = 1.0f
                scaleFactor = 1.0f
            } else {
                // Zoom in
                imageView.scaleX = 2.0f
                imageView.scaleY = 2.0f
                scaleFactor = 2.0f
            }
        }
    }

    private fun enableFullScreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullScreen()
        }
    }

    // Scale gesture detector for pinch to zoom
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 5.0f) // Limit scale between 0.1x and 5x

            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            return true
        }
    }

    // Gesture listener for other gestures
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Double tap to reset zoom or zoom in
            if (scaleFactor > 1.0f) {
                scaleFactor = 1.0f
                imageView.scaleX = scaleFactor
                imageView.scaleY = scaleFactor
            } else {
                scaleFactor = 2.0f
                imageView.scaleX = scaleFactor
                imageView.scaleY = scaleFactor
            }
            return true
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        if (scaleFactor > 1.0f) {
            // Reset zoom first
            scaleFactor = 1.0f
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
        } else {
            super.onBackPressed()
        }
    }
}
