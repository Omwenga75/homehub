package com.example.homehub.other

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.homehub.R
import com.google.firebase.auth.FirebaseAuth

object Extensions {
    private fun isValidContext(context: android.content.Context?): Boolean {
        if (context == null) return false
        if (context is android.app.Activity) {
            return !context.isFinishing && !context.isDestroyed
        }
        if (context is android.content.ContextWrapper) {
            return isValidContext(context.baseContext)
        }
        return true
    }

    fun ImageView.loadCircularImage(url: String?, signature: Any? = null) {
        if (url.isNullOrEmpty()) return
        
        val context = this.context
        if (!isValidContext(context)) return

        val request = Glide.with(context)
            .load(url)
            .apply(
                RequestOptions.circleCropTransform()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
            )
        
        if (signature != null) {
            request.signature(com.bumptech.glide.signature.ObjectKey(signature))
        }
        
        request.into(this)
    }

    fun ImageView.loadCircularImage(resourceId: Int) {
        val context = this.context
        if (!isValidContext(context)) return

        Glide.with(context)
            .load(resourceId)
            .apply(
                RequestOptions.circleCropTransform()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
            )
            .into(this)
    }

    /**
     * Load property image with proper error handling and placeholders
     */
    fun ImageView.loadPropertyImage(url: String?) {
        if (url.isNullOrEmpty()) {
            this.setImageResource(R.drawable.hs2)
            return
        }

        val context = this.context
        if (!isValidContext(context)) return

        try {
            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.hs2)
                .error(R.drawable.hs2)
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                .skipMemoryCache(false)
                .into(this)
        } catch (e: Exception) {
            this.setImageResource(R.drawable.hs2)
        }
    }

    /**
     * Premium profile image loader that handles local persistence, 
     * caching invalidation, and Firebase fallback automatically.
     * @param signature Optional force-refresh token (e.g. System.currentTimeMillis())
     */
    fun ImageView.loadProfileImage(userId: String?, backupUrl: String? = null, signature: Any? = null) {
        if (userId.isNullOrEmpty()) {
            this.loadCircularImage(R.drawable.ic_profile)
            return
        }

        // Check if the context is a valid activity before using Glide
        val context = this.context
        if (!isValidContext(context)) {
            return
        }

        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val isCurrentUser = userId == currentUserId
            val localFile = java.io.File(this.context.filesDir, "profile_images/$userId.jpg")
            
            val circleOptions = RequestOptions.circleCropTransform()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)

            if (isCurrentUser && localFile.exists()) {
                // Priority 1: High-quality local persistent JPEG for current user only
                val finalSignature = signature ?: localFile.lastModified()
                Glide.with(context)
                    .load(localFile)
                    .apply(circleOptions)
                    .signature(com.bumptech.glide.signature.ObjectKey(finalSignature))
                    .into(this)
            } else if (!backupUrl.isNullOrEmpty()) {
                // Priority 2: Firebase Storage URL fallback
                Glide.with(context)
                    .load(backupUrl)
                    .apply(circleOptions)
                    .into(this)
            } else {
                // Priority 3: Default Placeholder
                this.loadCircularImage(R.drawable.ic_profile)
            }
        } catch (e: Exception) {
            // Silently fail if Glide is unable to load
            this.loadCircularImage(R.drawable.ic_profile)
        }
    }
}
