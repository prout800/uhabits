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

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.isoron.uhabits.R
import org.isoron.uhabits.core.ui.screens.dashboard.DashboardState
import org.isoron.uhabits.core.ui.screens.habits.show.views.ScoreCardPresenter
import org.isoron.uhabits.databinding.DashboardBinding
import org.isoron.uhabits.utils.applyBottomInset
import org.isoron.uhabits.utils.applyToolbarInsets
import org.isoron.uhabits.utils.setupToolbar

class DashboardView(context: Context) : FrameLayout(context) {
    private val binding = DashboardBinding.inflate(LayoutInflater.from(context))

    init {
        binding.toolbar.applyToolbarInsets()
        addView(binding.root)
    }

    fun setState(data: DashboardState) {
        setupToolbar(
            binding.toolbar,
            title = context.getString(R.string.dashboard),
            color = data.color,
            theme = data.theme
        )
        binding.toolbar.subtitle = context.resources.getQuantityString(
            R.plurals.dashboard_habits_count,
            data.overview.habitCount,
            data.overview.habitCount
        )
        binding.overviewCard.setState(data.overview)
        binding.scoreCard.setState(data.scores)
        binding.historyCard.setState(data.history)
        binding.linearLayout.applyBottomInset()
    }

    fun setListener(presenter: ScoreCardPresenter) {
        binding.scoreCard.setListener(presenter)
    }
}
