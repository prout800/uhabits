/*
 * Copyright (C) 2016-2025 Álison Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.ui.screens.dashboard

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.views.HistoryChart
import org.isoron.uhabits.core.ui.views.LightTheme
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardPresenterTest : BaseUnitTest() {
    private val theme = LightTheme()
    private val preferences: Preferences = mock()

    @BeforeTest
    override fun setUp() {
        super.setUp()
        every { preferences.scoreCardSpinnerPosition } returns 0
        every { preferences.firstWeekdayInt } returns 2
        every { preferences.firstWeekday } returns DayOfWeek.MONDAY
    }

    private fun addHabit(
        name: String,
        archived: Boolean = false,
        completedOffsets: IntArray = intArrayOf()
    ): Habit {
        val habit = fixtures.createEmptyHabit(name = name)
        habit.isArchived = archived
        val today = getToday()
        for (offset in completedOffsets) {
            habit.originalEntries.add(Entry(today.minus(offset), Entry.YES_MANUAL))
        }
        habit.recompute()
        habitList.add(habit)
        return habit
    }

    @Test
    fun testGlobalScoreIsAverageOfHabitScores() {
        val a = addHabit("A", completedOffsets = intArrayOf(0))
        val b = addHabit("B", completedOffsets = intArrayOf(0))
        val state = DashboardPresenter.buildState(habitList, preferences, theme)
        val today = getToday()
        val expected = (a.scores[today].value + b.scores[today].value) / 2.0
        assertEquals(expected.toFloat(), state.overview.scoreToday, 1e-6)
        assertEquals(2, state.overview.habitCount)
    }

    @Test
    fun testPerfectDaysCountedOnlyWhenAllHabitsCompleted() {
        addHabit("A", completedOffsets = intArrayOf(0))
        addHabit("B", completedOffsets = intArrayOf(0))
        var state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(1L, state.overview.perfectDays)

        addHabit("C")
        state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(0L, state.overview.perfectDays)
    }

    @Test
    fun testArchivedHabitsAreExcluded() {
        addHabit("A", archived = true, completedOffsets = intArrayOf(0))
        addHabit("B", completedOffsets = intArrayOf(0))
        val state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(1, state.overview.habitCount)
    }

    @Test
    fun testHistoryMarksDayOnOnlyWhenAllCompleted() {
        addHabit("A", completedOffsets = intArrayOf(0))
        addHabit("B", completedOffsets = intArrayOf(0))
        var state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(listOf(HistoryChart.Square.ON), state.history.series)

        addHabit("C")
        state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(listOf(HistoryChart.Square.OFF), state.history.series)
    }

    @Test
    fun testScoreSeriesContainsOnePointPerDay() {
        addHabit("A", completedOffsets = intArrayOf(0, 1, 2))
        val state = DashboardPresenter.buildState(habitList, preferences, theme)
        assertEquals(3, state.scores.scores.size)
        assertEquals(getToday(), state.scores.scores.first().date)
    }
}
