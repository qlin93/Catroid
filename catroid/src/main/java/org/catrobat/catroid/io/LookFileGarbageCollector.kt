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

package org.catrobat.catroid.io

import android.util.Log
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.content.Scene
import org.catrobat.catroid.io.asynctask.ProjectLoadTask
import java.io.File
import java.io.IOException
import java.util.ArrayList

object LookFileGarbageCollector {
    fun cleanUpUnusedLookFiles(project: Project) {
        for (scene in project.sceneList) {
            deleteUnusedLookFiles(scene, getAllFileNamesToKeep(scene))
        }
    }

    private fun getAllFileNamesToKeep(scene: Scene): ArrayList<String> {
        val allFileNamesToKeep = ArrayList<String>()
        allFileNamesToKeep.addAll(getLookDataFileNames(scene))
        allFileNamesToKeep.addAll(getOtherFileNamesToKeep())
        return allFileNamesToKeep
    }

    private fun getLookDataFileNames(scene: Scene): ArrayList<String> {
        val lookDataFileNames = ArrayList<String>()
        for (sprite in scene.spriteList) {
            for (lookData in sprite.lookList) {
                lookDataFileNames.add(lookData.file.name)
            }
        }
        return lookDataFileNames
    }

    private fun getOtherFileNamesToKeep(): ArrayList<String> = arrayListOf(".nomedia")

    private fun deleteUnusedLookFiles(scene: Scene, fileNamesToKeep: ArrayList<String>) {
        val imageDirectory = File(scene.directory, Constants.IMAGE_DIRECTORY_NAME)
        val imageDirectoryFileList = imageDirectory.listFiles() ?: return
        for (file in imageDirectoryFileList) {
            if (!fileNamesToKeep.contains(file.name)) {
                tryDeleteLookFile(file)
            }
        }
    }

    private fun tryDeleteLookFile(file: File) {
        try {
            StorageOperations.deleteFile(file)
        } catch (e: IOException) {
            Log.e(
                ProjectLoadTask.TAG, "Error while deleting unused LookData files"
            )
        }
    }
}
