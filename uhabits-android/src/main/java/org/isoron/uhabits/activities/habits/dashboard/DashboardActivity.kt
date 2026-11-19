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
package org.isoron.uhabits.activities.habits.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.activities.AndroidThemeSwitcher
import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.screens.dashboard.DashboardPresenter
import org.isoron.uhabits.core.ui.screens.habits.show.views.ScoreCardPresenter
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.widgets.WidgetUpdater

class DashboardActivity : AppCompatActivity(), CommandRunner.Listener {

    private lateinit var commandRunner: CommandRunner
    private lateinit var habitList: HabitList
    private lateinit var preferences: Preferences
    private lateinit var themeSwitcher: AndroidThemeSwitcher
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var view: DashboardView
    private lateinit var scoreCardPresenter: ScoreCardPresenter

    private val scope = CoroutineScope(Dispatchers.Main)
    private val screen = Screen()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appComponent = (applicationContext as HabitsApplication).component
        habitList = appComponent.habitList
        preferences = appComponent.preferences
        commandRunner = appComponent.commandRunner
        widgetUpdater = appComponent.widgetUpdater

        themeSwitcher = AndroidThemeSwitcher(this, preferences)
        themeSwitcher.apply()

        scoreCardPresenter = ScoreCardPresenter(preferences, screen)
        view = DashboardView(this)
        view.setListener(scoreCardPresenter)
        view.applyRootViewInsets()
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        commandRunner.addListener(this)
        screen.refresh()
    }

    override fun onPause() {
        commandRunner.removeListener(this)
        super.onPause()
    }

    override fun onCommandFinished(command: Command) {
        screen.refresh()
    }

    inner class Screen : ScoreCardPresenter.Screen {
        override fun updateWidgets() {
            widgetUpdater.updateWidgets()
        }

        override fun refresh() {
            scope.launch {
                view.setState(
                    DashboardPresenter.buildState(
                        habitList = habitList,
                        preferences = preferences,
                        theme = themeSwitcher.currentTheme
                    )
                )
            }
        }
    }
}
