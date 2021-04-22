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

package org.catrobat.catroid.test.content.actions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.common.LookData
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.content.Scene
import org.catrobat.catroid.content.Sprite
import org.catrobat.catroid.io.XstreamSerializer
import org.catrobat.catroid.io.asynctask.ProjectLoadTask
import org.catrobat.catroid.test.utils.TestUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeleteLookActionFileCleanupTest {
    private lateinit var sprite: Sprite
    private lateinit var project: Project
    private lateinit var scene: Scene
    private var projectName = "testProject"

    @Before
    fun setUp() {
        this.createTestProject()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        TestUtils.deleteProjects(projectName)
    }

    @Test
    fun testDeleteLook() {
        val lookFileNamesList = mutableListOf("LookFile1", "LookFile2", "LookFile3")
        addLookDataFromListToSprite(lookFileNamesList)

        deleteNumberOfLooksAndAssert(1, lookFileNamesList)

        ProjectLoadTask.task(project.directory, ApplicationProvider.getApplicationContext())
        assertImageDirectoryOnlyContainsUsedFiles(lookFileNamesList)
    }

    @Test
    fun testDeleteMultipleLooks() {
        val lookFileNamesList = mutableListOf("LookFile1", "LookFile2", "LookFile3")
        addLookDataFromListToSprite(lookFileNamesList)

        deleteNumberOfLooksAndAssert(3, lookFileNamesList)
        ProjectLoadTask.task(project.directory, ApplicationProvider.getApplicationContext())
        assertImageDirectoryOnlyContainsUsedFiles(lookFileNamesList)
    }

    @Test
    fun testNoFilesDeletedAfterLoadWithoutDelete() {
        val lookFileNamesList = mutableListOf("LookFile1", "LookFile2", "LookFile3")
        addLookDataFromListToSprite(lookFileNamesList)

        deleteNumberOfLooksAndAssert(0, lookFileNamesList)

        ProjectLoadTask.task(project.directory, ApplicationProvider.getApplicationContext())
        ProjectLoadTask.task(project.directory, ApplicationProvider.getApplicationContext())
        assertImageDirectoryOnlyContainsUsedFiles(lookFileNamesList)
    }

    private fun deleteNumberOfLooksAndAssert(
        numberOfLooksToDelete: Int,
        lookNameList: MutableList<String>
    ) {
        assertEquals(lookNameList.size, sprite.lookList.size)
        assertImageDirectoryOnlyContainsUsedFiles(lookNameList)
        for (numberOfDeletedLooks in 1..numberOfLooksToDelete) {
            sprite.actionFactory.createDeleteLookAction(sprite).act(1f)
            assertEquals(lookNameList.size - numberOfDeletedLooks, sprite.lookList.size)
            assertImageDirectoryOnlyContainsUsedFiles(lookNameList)
        }
        repeat(numberOfLooksToDelete) { lookNameList.remove(lookNameList.first()) }
    }

    private fun assertImageDirectoryOnlyContainsUsedFiles(lookFileNamesList: List<String>) {
        val imageDirectory = File(scene.directory, Constants.IMAGE_DIRECTORY_NAME)
        val nomediaOffset = 1
        assertEquals(lookFileNamesList.size + nomediaOffset, imageDirectory.listFiles().size)
        lookFileNamesList.forEach { name ->
            val file = File(imageDirectory, name)
            assertTrue(file.exists())
        }
    }

    private fun createTestProject() {
        project = Project(ApplicationProvider.getApplicationContext(), projectName)
        scene = project.defaultScene
        sprite = Sprite("testSprite")
        scene.addSprite(sprite)

        XstreamSerializer.getInstance().saveProject(project)
        ProjectManager.getInstance().currentProject = project
    }

    private fun addLookDataFromListToSprite(lookFileNamesList: List<String>) {
        lookFileNamesList.forEach { name ->
            addLookDataToSprite(scene, name)
        }
        XstreamSerializer.getInstance().saveProject(project)
    }

    private fun addLookDataToSprite(currentScene: Scene?, name: String?) {
        val imageDirectory = File(currentScene?.directory, Constants.IMAGE_DIRECTORY_NAME)
        val lookDataFile = File(imageDirectory, name)
        lookDataFile.createNewFile()
        val lookData = LookData(name, lookDataFile)
        sprite.lookList?.add(lookData)
        sprite.look.lookData = sprite.lookList.first()
    }
}
