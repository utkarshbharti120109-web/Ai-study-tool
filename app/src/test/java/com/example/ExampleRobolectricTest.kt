package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.StudyRepository
import com.example.viewmodel.StudyViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StudyBuddy", appName)
  }

  @Test
  fun testViewModelInitialization() = runTest {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repository = StudyRepository(db)
    val viewModel = StudyViewModel(context, repository)
    
    assertNotNull(viewModel)
    // Run the default badges populating directly to check for any Room/Coroutine exceptions
    repository.populateDefaultBadges()
    
    val badges = db.achievementBadgeDao().getAllBadgesDirect()
    assertEquals(6, badges.size)
    db.close()
  }
}
