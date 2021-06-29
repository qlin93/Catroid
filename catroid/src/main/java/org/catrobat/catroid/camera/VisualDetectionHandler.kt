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

import android.graphics.Point
import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.text.Text
import com.huawei.hms.mlsdk.face.MLFace
import com.huawei.hms.mlsdk.skeleton.MLJoint
import com.huawei.hms.mlsdk.skeleton.MLSkeleton
import org.catrobat.catroid.camera.VisualDetectionUtils.translateToStageCoordinates
import org.catrobat.catroid.camera.VisualDetectionUtils.writeFloatToSensor
import org.catrobat.catroid.camera.VisualDetectionUtils.writeStringToSensor
import org.catrobat.catroid.formulaeditor.SensorCustomEventListener
import org.catrobat.catroid.formulaeditor.Sensors
import org.catrobat.catroid.utils.TextBlockUtil
import kotlin.math.roundToInt

class VisualDetectionHandlerFace(val id: Int, val boundingBox: Rect)

object VisualDetectionHandler {
    private const val MAX_FACE_SIZE = 100
    private const val FACE_SENSORS = 2
    private val sensorListeners = mutableSetOf<SensorCustomEventListener>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var facesForSensors: Array<VisualDetectionHandlerFace?> = Array(FACE_SENSORS) { _ -> null }
    private var faceIds: IntArray = IntArray(FACE_SENSORS) { _ -> -1 }

    @JvmStatic
    fun addListener(listener: SensorCustomEventListener) {
        sensorListeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: SensorCustomEventListener) {
        sensorListeners.remove(listener)
    }

    fun translateGoogleFaceToVisualDetectionFace(faceList: List<Face>): List<VisualDetectionHandlerFace> {
        val newFacesList = mutableListOf<VisualDetectionHandlerFace>()
        for(face in faceList) {
            newFacesList.add(VisualDetectionHandlerFace(face.trackingId, face.boundingBox))
        }
        return newFacesList
    }

    fun translateHuaweiFaceToVisualDetectionFace(faceList: List<MLFace>):
        List<VisualDetectionHandlerFace> {
        val newFacesList = mutableListOf<VisualDetectionHandlerFace>()
        for(face in faceList) {
            newFacesList.add(VisualDetectionHandlerFace(face.tracingIdentity, face.border))
        }
        return newFacesList
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateTextSensorValues(text: Text, imageWidth: Int, imageHeight: Int) {
        if (text.textBlocks.isEmpty()) return

        TextBlockUtil.setTextBlocks(text.textBlocks, imageWidth, imageHeight)

        sensorListeners.forEach {
            writeStringToSensor(it, Sensors.TEXT_FROM_CAMERA, text.text)
            writeFloatToSensor(it, Sensors.TEXT_BLOCKS_NUMBER, text.textBlocks.size.toFloat())
        }
    }

    fun updateAllFaceSensorValues(imageWidth: Int, imageHeight: Int) {
        updateFaceDetectionStatusSensorValues()

        facesForSensors.forEachIndexed { index, face ->
            face?.let {
                val faceBounds = face.boundingBox

                val facePosition = translateToStageCoordinates(
                    faceBounds.exactCenterX(), faceBounds.exactCenterY(),
                    imageWidth, imageHeight
                )
                val relativeFaceSize =
                    (faceBounds.height().toFloat() / imageHeight).coerceAtMost(1f)
                val faceSize = (MAX_FACE_SIZE * relativeFaceSize).roundToInt()

                updateFaceSensorValues(facePosition, faceSize, index)
            }
        }
        facesForSensors.fill(null)
    }

    fun handleAlreadyExistingFaces(faces: List<VisualDetectionHandlerFace>) {
        for (face in faces) {
            when (face.id) {
                faceIds[0] -> facesForSensors[0] = face
                faceIds[1] -> facesForSensors[1] = face
            }
        }
    }

    fun handleNewFaces(faces: List<VisualDetectionHandlerFace>) {
        facesForSensors.forEachIndexed { index, face ->
            if (face == null) {
                attachNewFaceIfExisting(faces, index)
            }
        }
    }

    private fun attachNewFaceIfExisting(faces: List<VisualDetectionHandlerFace>, index: Int) {
        for (face in faces) {
            if (!face.id?.let { faceIds.contains(it) }) {
                faceIds[index] = face.id ?: -1
                facesForSensors[index] = face
                break
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateFaceDetectionStatusSensorValues() {
        val firstSensorValue = if (facesForSensors[0] != null) 1f else 0f
        val secondSensorValue = if (facesForSensors[1] != null) 1f else 0f
        sensorListeners.forEach {
            writeFloatToSensor(it, Sensors.FACE_DETECTED, firstSensorValue)
            writeFloatToSensor(it, Sensors.SECOND_FACE_DETECTED, secondSensorValue)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun updateFaceSensorValues(facePosition: Point, faceSize: Int, faceNumber: Int) {
        sensorListeners.forEach {
            when (faceNumber) {
                0 -> {
                    writeFloatToSensor(it, Sensors.FACE_X_POSITION, facePosition.x.toFloat())
                    writeFloatToSensor(it, Sensors.FACE_Y_POSITION, facePosition.y.toFloat())
                    writeFloatToSensor(it, Sensors.FACE_SIZE, faceSize.toFloat())
                }
                1 -> {
                    writeFloatToSensor(it, Sensors.SECOND_FACE_X_POSITION, facePosition.x.toFloat())
                    writeFloatToSensor(it, Sensors.SECOND_FACE_Y_POSITION, facePosition.y.toFloat())
                    writeFloatToSensor(it, Sensors.SECOND_FACE_SIZE, faceSize.toFloat())
                }
            }
        }
    }

    fun updateAllPoseSensorValues(pose: Pose?, imageWidth: Int, imageHeight: Int) {
        val allPoseLandmarks = pose?.allPoseLandmarks

        if (allPoseLandmarks.isNullOrEmpty()) return

        allPoseLandmarks.forEach { poseLandmark ->
            poseLandmark?.let {
                val poseLandmarkPositionTranslated = translateToStageCoordinates(
                    poseLandmark.position.x,
                    poseLandmark.position.y,
                    imageWidth,
                    imageHeight
                )

                updatePoseSensorValues(poseLandmark, poseLandmarkPositionTranslated)
            }
        }
    }

    private fun updatePoseSensorValues(poseLandmark: PoseLandmark, position: Point) {
        sensorListeners.forEach {
            when (poseLandmark.landmarkType) {
                PoseLandmark.NOSE -> {
                    writeFloatToSensor(it, Sensors.NOSE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.NOSE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_EYE_INNER -> {
                    writeFloatToSensor(it, Sensors.LEFT_EYE_INNER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_EYE_INNER_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_EYE -> {
                    writeFloatToSensor(it, Sensors.LEFT_EYE_CENTER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_EYE_CENTER_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_EYE_OUTER -> {
                    writeFloatToSensor(it, Sensors.LEFT_EYE_OUTER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_EYE_OUTER_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_EYE_INNER -> {
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_INNER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_INNER_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_EYE -> {
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_CENTER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_CENTER_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_EYE_OUTER -> {
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_OUTER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_EYE_OUTER_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_EAR -> {
                    writeFloatToSensor(it, Sensors.LEFT_EAR_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_EAR_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_EAR -> {
                    writeFloatToSensor(it, Sensors.RIGHT_EAR_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_EAR_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_MOUTH -> {
                    writeFloatToSensor(it, Sensors.MOUTH_LEFT_CORNER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.MOUTH_LEFT_CORNER_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_MOUTH -> {
                    writeFloatToSensor(it, Sensors.MOUTH_RIGHT_CORNER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.MOUTH_RIGHT_CORNER_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_SHOULDER -> {
                    writeFloatToSensor(it, Sensors.LEFT_SHOULDER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_SHOULDER_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_SHOULDER -> {
                    writeFloatToSensor(it, Sensors.RIGHT_SHOULDER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_SHOULDER_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_ELBOW -> {
                    writeFloatToSensor(it, Sensors.LEFT_ELBOW_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_ELBOW_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_ELBOW -> {
                    writeFloatToSensor(it, Sensors.RIGHT_ELBOW_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_ELBOW_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_WRIST -> {
                    writeFloatToSensor(it, Sensors.LEFT_WRIST_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_WRIST_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_WRIST -> {
                    writeFloatToSensor(it, Sensors.RIGHT_WRIST_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_WRIST_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_PINKY -> {
                    writeFloatToSensor(it, Sensors.LEFT_PINKY_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_PINKY_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_PINKY -> {
                    writeFloatToSensor(it, Sensors.RIGHT_PINKY_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_PINKY_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_INDEX -> {
                    writeFloatToSensor(it, Sensors.LEFT_INDEX_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_INDEX_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_INDEX -> {
                    writeFloatToSensor(it, Sensors.RIGHT_INDEX_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_INDEX_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_THUMB -> {
                    writeFloatToSensor(it, Sensors.LEFT_THUMB_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_THUMB_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_THUMB -> {
                    writeFloatToSensor(it, Sensors.RIGHT_THUMB_KNUCKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_THUMB_KNUCKLE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_HIP -> {
                    writeFloatToSensor(it, Sensors.LEFT_HIP_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_HIP_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_HIP -> {
                    writeFloatToSensor(it, Sensors.RIGHT_HIP_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_HIP_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_KNEE -> {
                    writeFloatToSensor(it, Sensors.LEFT_KNEE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_KNEE_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_KNEE -> {
                    writeFloatToSensor(it, Sensors.RIGHT_KNEE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_KNEE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_ANKLE -> {
                    writeFloatToSensor(it, Sensors.LEFT_ANKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_ANKLE_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_ANKLE -> {
                    writeFloatToSensor(it, Sensors.RIGHT_ANKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_ANKLE_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_HEEL -> {
                    writeFloatToSensor(it, Sensors.LEFT_HEEL_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_HEEL_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_HEEL -> {
                    writeFloatToSensor(it, Sensors.RIGHT_HEEL_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_HEEL_Y, position.y.toFloat())
                }
                PoseLandmark.LEFT_FOOT_INDEX -> {
                    writeFloatToSensor(it, Sensors.LEFT_FOOT_INDEX_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_FOOT_INDEX_Y, position.y.toFloat())
                }
                PoseLandmark.RIGHT_FOOT_INDEX -> {
                    writeFloatToSensor(it, Sensors.RIGHT_FOOT_INDEX_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_FOOT_INDEX_Y, position.y.toFloat())
                }
            }
        }
    }

    fun updateAllPoseSensorValuesHuawei(skeletonList: List<MLSkeleton>, imageWidth: Int,
        imageHeight: Int) {
        if (skeletonList.isNullOrEmpty()) return

        skeletonList[0].joints.forEach { joint ->
            joint?.let {
                val jointPositionTranslated = translateToStageCoordinates(
                    joint.pointX,
                    joint.pointY,
                    imageWidth,
                    imageHeight)

                updatePoseSensorValuesHuawei(joint.type, jointPositionTranslated)
            }
        }
    }

    private fun updatePoseSensorValuesHuawei(jointType: Int, position: Point) {
        sensorListeners.forEach {
            when (jointType) {
                MLJoint.TYPE_HEAD_TOP -> {
                    writeFloatToSensor(it, Sensors.HEAD_TOP_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.HEAD_TOP_Y, position.y.toFloat())
                }
                MLJoint.TYPE_NECK -> {
                    writeFloatToSensor(it, Sensors.NECK_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.NECK_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_SHOULDER -> {
                    writeFloatToSensor(it, Sensors.LEFT_SHOULDER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_SHOULDER_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_SHOULDER -> {
                    writeFloatToSensor(it, Sensors.RIGHT_SHOULDER_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_SHOULDER_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_ELBOW -> {
                    writeFloatToSensor(it, Sensors.LEFT_ELBOW_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_ELBOW_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_ELBOW -> {
                    writeFloatToSensor(it, Sensors.RIGHT_ELBOW_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_ELBOW_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_WRIST -> {
                    writeFloatToSensor(it, Sensors.LEFT_WRIST_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_WRIST_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_WRIST -> {
                    writeFloatToSensor(it, Sensors.RIGHT_WRIST_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_WRIST_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_HIP -> {
                    writeFloatToSensor(it, Sensors.LEFT_HIP_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_HIP_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_HIP -> {
                    writeFloatToSensor(it, Sensors.RIGHT_HIP_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_HIP_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_KNEE -> {
                    writeFloatToSensor(it, Sensors.LEFT_KNEE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_KNEE_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_KNEE -> {
                    writeFloatToSensor(it, Sensors.RIGHT_KNEE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_KNEE_Y, position.y.toFloat())
                }
                MLJoint.TYPE_LEFT_ANKLE -> {
                    writeFloatToSensor(it, Sensors.LEFT_ANKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.LEFT_ANKLE_Y, position.y.toFloat())
                }
                MLJoint.TYPE_RIGHT_ANKLE -> {
                    writeFloatToSensor(it, Sensors.RIGHT_ANKLE_X, position.x.toFloat())
                    writeFloatToSensor(it, Sensors.RIGHT_ANKLE_Y, position.y.toFloat())
                }
            }
        }
    }
}