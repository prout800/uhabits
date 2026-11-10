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

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Score
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.screens.habits.show.views.ScoreCardPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.ScoreCardState
import org.isoron.uhabits.core.ui.views.HistoryChart
import org.isoron.uhabits.core.ui.views.Theme

data class DashboardOverviewState(
    val color: PaletteColor,
    val habitCount: Int,
    val scoreToday: Float,
    val scoreMonthDiff: Float,
    val scoreYearDiff: Float,
    val perfectDays: Long,
    val theme: Theme
)

data class DashboardHistoryState(
    val color: PaletteColor,
    val firstWeekday: DayOfWeek,
    val series: List<HistoryChart.Square>,
    val defaultSquare: HistoryChart.Square,
    val notesIndicators: List<Boolean>,
    val today: LocalDate,
    val theme: Theme
)

data class DashboardState(
    val color: PaletteColor,
    val overview: DashboardOverviewState,
    val scores: ScoreCardState,
    val history: DashboardHistoryState,
    val theme: Theme
)

class DashboardPresenter {
    companion object {
        private val DASHBOARD_COLOR = PaletteColor(1)

        fun buildState(
            habitList: HabitList,
            preferences: Preferences,
            theme: Theme
        ): DashboardState {
            val today = getToday()
            val habits = habitList.getFiltered(HabitMatcher()).toList()
            val oldestByHabit = habits.associateWith { habit ->
                habit.computedEntries.getKnown().lastOrNull()?.date ?: today
            }
            val oldest = oldestByHabit.values.minOrNull() ?: today

            fun startedHabitsOn(date: LocalDate): List<Habit> {
                return habits.filter { habit ->
                    !oldestByHabit.getValue(habit).isNewerThan(date)
                }
            }

            fun globalScoreOn(date: LocalDate): Double {
                if (habits.isEmpty()) return 0.0
                return habits.map { it.scores[date].value }.average()
            }

            val days = mutableListOf<LocalDate>()
            var current = today
            while (!current.isOlderThan(oldest)) {
                days.add(current)
                current = current.minus(1)
            }

            val perfectDays = days.count { date ->
                val started = startedHabitsOn(date)
                started.isNotEmpty() && started.all { it.isCompletedOn(date) }
            }.toLong()

            val overview = DashboardOverviewState(
                color = DASHBOARD_COLOR,
                habitCount = habits.size,
                scoreToday = globalScoreOn(today).toFloat(),
                scoreMonthDiff = (globalScoreOn(today) - globalScoreOn(today.minus(30))).toFloat(),
                scoreYearDiff = (globalScoreOn(today) - globalScoreOn(today.minus(365))).toFloat(),
                perfectDays = perfectDays,
                theme = theme
            )

            val spinnerPosition = preferences.scoreCardSpinnerPosition
            val bucketSize = ScoreCardPresenter.BUCKET_SIZES[spinnerPosition]
            val scores = days.groupBy { date ->
                ScoreCardPresenter.truncateDate(
                    ScoreCardPresenter.getTruncateField(bucketSize),
                    date,
                    preferences.firstWeekdayInt
                )
            }.map { (date, dates) ->
                Score(date, dates.map { globalScoreOn(it) }.average())
            }.sortedBy {
                it.date
            }.reversed()

            val history = DashboardHistoryState(
                color = DASHBOARD_COLOR,
                firstWeekday = preferences.firstWeekday,
                series = days.map { date ->
                    val started = startedHabitsOn(date)
                    if (started.isEmpty()) {
                        HistoryChart.Square.OFF
                    } else {
                        val completed = started.count { it.isCompletedOn(date) }
                        when {
                            completed == started.size -> HistoryChart.Square.ON
                            completed == 0 -> HistoryChart.Square.OFF
                            else -> HistoryChart.Square.DIMMED
                        }
                    }
                },
                defaultSquare = HistoryChart.Square.OFF,
                notesIndicators = days.map { date ->
                    habits.any { it.computedEntries.get(date).notes.isNotEmpty() }
                },
                today = today,
                theme = theme
            )

            return DashboardState(
                color = DASHBOARD_COLOR,
                overview = overview,
                scores = ScoreCardState(
                    scores = scores,
                    bucketSize = bucketSize,
                    spinnerPosition = spinnerPosition,
                    color = DASHBOARD_COLOR,
                    theme = theme
                ),
                history = history,
                theme = theme
            )
        }
    }
}
