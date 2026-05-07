package com.example.homehub

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderLayoutTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(AdminDashboardActivity::class.java)

    @Test
    fun testAdminHeaderElementsVisible() {
        onView(withId(R.id.profileIconCard)).check(matches(isDisplayed()))
        onView(withId(R.id.greetingText)).check(matches(isDisplayed()))
        onView(withId(R.id.userNameText)).check(matches(isDisplayed()))
        onView(withId(R.id.btnNotificationHeader)).check(matches(isDisplayed()))
    }
}
