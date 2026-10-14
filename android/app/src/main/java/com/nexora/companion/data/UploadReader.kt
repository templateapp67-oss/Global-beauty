package com.nexora.companion.data

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object UploadReader {
    suspend fun read(context: Context, uri: Uri, photo: Boolean): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val mime=context.contentResolver.getType(uri).orEmpty()
        require(UploadRules.allowed(mime,photo)) { "Choose a JPEG, PNG${if(photo) ", WebP photo" else " image or PDF document"}." }
        val raw=context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(UploadRules.MAX_BYTES) } ?: error("Could not open file.")
        if(!photo) {
            if(mime=="application/pdf") require(raw.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-") { "Invalid PDF file." }
            else { val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeByteArray(raw,0,raw.size,bounds); require(bounds.outWidth>0 && bounds.outHeight>0) { "Invalid image document." } }
            raw to mime
        } else {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=40_000_000) { "Choose a valid photo up to 40 megapixels." }
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600) sample*=2
            val bitmap=BitmapFactory.decodeByteArray(raw,0,raw.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("Photo could not be decoded.")
            val orientation=ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            val matrix=Matrix().apply {
                when(orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f,1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f,-1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f,1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f,1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            val out=ByteArrayOutputStream().use { stream -> check(rotated.compress(Bitmap.CompressFormat.JPEG,85,stream)); stream.toByteArray() }
            if(rotated!==bitmap) rotated.recycle()
            bitmap.recycle()
            out to "image/jpeg"
        }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out=ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=read(buffer); if(n<0)break; require(out.size()+n<=limit) { "File must be smaller than 5 MB." }; out.write(buffer,0,n) }
        return out.toByteArray().also { require(it.isNotEmpty()) { "File is empty." } }
    }
}
