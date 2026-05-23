package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.data.db.AppDatabase
import com.example.data.repository.StudyRepository
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BadgesScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.StudyGuideScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.StudyViewModel
import com.example.viewmodel.StudyViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Database & Repository
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = StudyRepository(database)

    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val app = context.applicationContext as Application

        // Instantiate ViewModel
        val viewModel: StudyViewModel = remember {
          StudyViewModelFactory(app, repository).create(StudyViewModel::class.java)
        }

        var currentTab by remember { mutableIntStateOf(0) }

        Scaffold(
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
          bottomBar = {
            NavigationBar(
              modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
              val items = listOf(
                NavigationItem(
                  title = "Study",
                  selectedIcon = Icons.Default.MenuBook,
                  unselectedIcon = Icons.Outlined.MenuBook,
                  tag = "nav_tab_study"
                ),
                NavigationItem(
                  title = "Growth",
                  selectedIcon = Icons.Default.Analytics,
                  unselectedIcon = Icons.Outlined.Analytics,
                  tag = "nav_tab_analytics"
                ),
                NavigationItem(
                  title = "Badges",
                  selectedIcon = Icons.Default.EmojiEvents,
                  unselectedIcon = Icons.Outlined.EmojiEvents,
                  tag = "nav_tab_badges"
                ),
                NavigationItem(
                  title = "Reminders",
                  selectedIcon = Icons.Default.NotificationsActive,
                  unselectedIcon = Icons.Outlined.NotificationsActive,
                  tag = "nav_tab_reminders"
                )
              )

              items.forEachIndexed { index, item ->
                NavigationBarItem(
                  selected = currentTab == index,
                  onClick = { currentTab = index },
                  icon = {
                    Icon(
                      imageVector = if (currentTab == index) item.selectedIcon else item.unselectedIcon,
                      contentDescription = item.title
                    )
                  },
                  label = { Text(item.title) },
                  modifier = Modifier.testTag(item.tag)
                )
              }
            }
          }
        ) { innerPadding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            when (currentTab) {
              0 -> StudyGuideScreen(viewModel = viewModel)
              1 -> AnalyticsScreen(viewModel = viewModel)
              2 -> BadgesScreen(viewModel = viewModel)
              3 -> RemindersScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }
}

data class NavigationItem(
  val title: String,
  val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
  val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
  val tag: String
)
