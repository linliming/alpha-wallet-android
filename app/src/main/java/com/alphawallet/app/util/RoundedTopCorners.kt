package com.alphawallet.app.util

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.util.Preconditions
import com.bumptech.glide.util.Util
import java.nio.ByteBuffer
import java.security.MessageDigest

class RoundedTopCorners(roundingRadius: Int) : BitmapTransformation() {
    private val roundingRadius: Int

    /**
     * @param roundingRadius the corner radius (in device-specific pixels).
     * @throws IllegalArgumentException if rounding radius is 0 or less.
     */
    init {
        Preconditions.checkArgument(roundingRadius > 0, "roundingRadius must be greater than 0.")
        this.roundingRadius = roundingRadius
    }

    override fun transform(
        pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int
    ): Bitmap {
        return TransformationUtils.roundedCorners(
            pool,
            toTransform,
            roundingRadius.toFloat(),
            roundingRadius.toFloat(),
            0f,
            0f
        )
    }

    override fun equals(o: Any?): Boolean {
        if (o is RoundedTopCorners) {
            return roundingRadius == o.roundingRadius
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(ID.hashCode(), Util.hashCode(roundingRadius))
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)

        val radiusData = ByteBuffer.allocate(4).putInt(roundingRadius).array()
        messageDigest.update(radiusData)
    }

    companion object {
        private const val ID = "com.bumptech.glide.load.resource.bitmap.RoundedCorners"
        private val ID_BYTES = ID.toByteArray(CHARSET)
    }
}

