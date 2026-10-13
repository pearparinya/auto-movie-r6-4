package com.pearparinya.automovie

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EpisodeExportTest {
    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun imageIntroAndTwoClipsProducePortraitMp4WithSilentIntroAndAudio() {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val context=instrumentation.targetContext
        val folder=File(context.cacheDir,"test_episode").apply { deleteRecursively(); mkdirs() }
        fun image(name: String,color: Int): File {
            val file=File(folder,name); val b=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888); b.eraseColor(color)
            file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle(); return file
        }
        val cover=image("cover.png",Color.YELLOW); val red=image("red.png",Color.RED); val blue=image("blue.png",Color.BLUE)
        fun export(items: List<EditedMediaItem>, path: File) {
            val latch=CountDownLatch(1); var failure: Throwable?=null; var transformer: Transformer?=null
            instrumentation.runOnMainSync {
                val encoder=DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(24000000).build()).setEnableFallback(false).build()
                transformer=Transformer.Builder(context).setPortraitEncodingEnabled(true).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition,result: ExportResult) { latch.countDown() }
                    override fun onError(composition: Composition,result: ExportResult,exception: ExportException) { failure=exception; latch.countDown() }
                }).build()
                transformer!!.start(Composition.Builder(EditedMediaItemSequence.Builder(items).build()).experimentalSetForceAudioTrack(true).build(),path.path)
            }
            if(!latch.await(180,TimeUnit.SECONDS)) { instrumentation.runOnMainSync { transformer?.cancel() }; fail("export timed out") }
            failure?.let { throw AssertionError("real encoder export failed",it) }
        }
        fun still(file: File,ms: Long)=EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(file)).setImageDurationMs(ms).build()).setFrameRate(30).setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(1080,1920,Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        val first=File(folder,"clip1.mp4"); val second=File(folder,"clip2.mp4")
        export(listOf(still(red,1000)),first); export(listOf(still(blue,1000)),second)
        val before=listOf(EpisodeMedia.digest(first),EpisodeMedia.digest(second))
        val output=File(folder,"EP01.mp4")
        export(listOf(still(cover,3000),EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(first))).setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(1080,1920,Presentation.LAYOUT_SCALE_TO_FIT)))).build(),EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(second))).setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(1080,1920,Presentation.LAYOUT_SCALE_TO_FIT)))).build()),output)
        val info=EpisodeMedia.inspect(output); assertEquals(1080,info.width); assertEquals(1920,info.height); assertEquals(0,info.rotation)
        assertTrue(kotlin.math.abs(info.durationMs-5000)<300); assertNotNull(info.audioMime)
        val reader=android.media.MediaMetadataRetriever(); reader.setDataSource(output.path)
        try {
            val frame=reader.getFrameAtTime(500000,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)!!
            val color=frame.getPixel(frame.width/2,frame.height/2); assertTrue(Color.red(color)>150 && Color.green(color)>150 && Color.blue(color)<100); frame.recycle()
        } finally { reader.release() }
        assertEquals(before,listOf(EpisodeMedia.digest(first),EpisodeMedia.digest(second)))
        // Strict packet-copy either verifies compatibility or rejects it; it never encodes silently.
        val copy=File(folder,"copy.mp4")
        try { EpisodeMedia.remux(listOf(first,second),copy,{false},{})
            assertEquals(1080,EpisodeMedia.inspect(copy).width); assertTrue(kotlin.math.abs(EpisodeMedia.inspect(copy).durationMs-2000)<300)
        } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("คุณภาพสูง")) }
        folder.deleteRecursively()
    }
}
