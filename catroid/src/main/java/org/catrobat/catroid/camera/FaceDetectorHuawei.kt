/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.camera

import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.huawei.hiai.vision.common.ConnectionCallback
import com.huawei.hiai.vision.common.VisionBase
import com.huawei.hiai.vision.face.FaceDetector
import com.huawei.hiai.vision.visionkit.common.Frame
import com.huawei.hiai.vision.visionkit.face.Face
import org.catrobat.catroid.formulaeditor.SensorCustomEventListener
import org.catrobat.catroid.stage.StageActivity
import org.json.JSONObject
import java.nio.ByteBuffer

object FaceDetectorHuawei: ImageAnalysis.Analyzer {
    private const val MAX_FACE_SIZE = 100
    private const val FACE_SENSORS = 2
    private val sensorListeners = mutableSetOf<SensorCustomEventListener>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var facesForSensors: Array<Face?> = Array(FACE_SENSORS) { _ -> null }
    private var faceIds: IntArray = IntArray(FACE_SENSORS) { _ -> -1 }
    private var faceDetected = false

    var stageActivity: StageActivity? = null



    @JvmStatic
    fun addListener(listener: SensorCustomEventListener) {
        sensorListeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: SensorCustomEventListener) {
        sensorListeners.remove(listener)
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image?.planes?.get(0)?.getBuffer() ?: return

        val buffer: ByteBuffer = image
        val bytes = ByteArray(buffer.capacity())
        buffer[bytes]
        val bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)

        val mFaceDetector = FaceDetector(
            stageActivity?.context
        );

        val frame = Frame()
        frame.bitmap = bitmapImage
        val jsonObject: JSONObject? = mFaceDetector.detect(frame, null)

        jsonObject ?: return

        val faces: List<Face> = mFaceDetector.convertResult(jsonObject)

        /*for(face in faces) {
            Log.d("HUAWEI", face.toString())
        }*/
    }

    fun connectToVisionBase(){
        VisionBase.init(stageActivity, object : ConnectionCallback {
            override fun onServiceConnect() {
                Log.i("HUAWEI", "onServiceConnect")
            }

            override fun onServiceDisconnect() {
                Log.i("HUAWEI", "onServiceDisconnect")
            }
        })
    }

}
