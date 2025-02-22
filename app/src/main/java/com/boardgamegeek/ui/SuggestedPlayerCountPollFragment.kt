package com.boardgamegeek.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.boardgamegeek.R
import com.boardgamegeek.extensions.fadeIn
import com.boardgamegeek.extensions.setViewBackground
import com.boardgamegeek.extensions.showAndSurvive
import com.boardgamegeek.ui.viewmodel.GameViewModel
import com.boardgamegeek.ui.widget.PlayerNumberRow
import kotlinx.android.synthetic.main.fragment_poll_suggested_player_count.*

class SuggestedPlayerCountPollFragment : DialogFragment(R.layout.fragment_poll_suggested_player_count) {
    val viewModel by activityViewModels<GameViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.setTitle(R.string.suggested_numplayers)

        addKeyRow(R.color.best, R.string.best)
        addKeyRow(R.color.recommended, R.string.recommended)
        addKeyRow(R.color.not_recommended, R.string.not_recommended)

        viewModel.playerPoll.observe(viewLifecycleOwner, { entity ->
            val totalVoteCount = entity?.totalVotes ?: 0
            totalVoteView?.text = resources.getQuantityString(R.plurals.votes_suffix, totalVoteCount, totalVoteCount)

            pollList?.visibility = if (totalVoteCount == 0) View.GONE else View.VISIBLE
            keyContainer?.visibility = if (totalVoteCount == 0) View.GONE else View.VISIBLE
            noVotesSwitch?.visibility = if (totalVoteCount == 0) View.GONE else View.VISIBLE
            if (totalVoteCount > 0) {
                pollList?.removeAllViews()
                for ((_, playerCount, bestVoteCount, recommendedVoteCount, notRecommendedVoteCount) in entity!!.results) {
                    val row = PlayerNumberRow(requireContext())
                    row.setText(playerCount)
                    row.setVotes(bestVoteCount, recommendedVoteCount, notRecommendedVoteCount, totalVoteCount)
                    row.setOnClickListener { view ->
                        pollList.children.forEach {
                            (it as? PlayerNumberRow)?.clearHighlight()
                        }
                        (view as? PlayerNumberRow)?.let { playerNumberRow ->
                            playerNumberRow.setHighlight()

                            val voteCount = playerNumberRow.votes
                            keyContainer.children.forEachIndexed { index, view ->
                                view.findViewById<TextView>(R.id.infoView).text = voteCount[index].toString()
                            }
                        }
                    }
                    pollList.addView(row)
                }

                noVotesSwitch?.setOnClickListener {
                    pollList.children.forEach { row ->
                        (row as? PlayerNumberRow)?.showNoVotes(noVotesSwitch.isChecked)
                    }
                }
            }

            progressView?.hide()
            scrollView.fadeIn()
        })
    }

    private fun addKeyRow(@ColorRes colorResId: Int, @StringRes textResId: Int) {
        val v = LayoutInflater.from(context).inflate(R.layout.row_poll_key, keyContainer, false) as ViewGroup
        v.findViewById<TextView>(R.id.textView).setText(textResId)
        v.findViewById<View>(R.id.colorView).setViewBackground(ContextCompat.getColor(requireContext(), colorResId))
        keyContainer?.addView(v)
    }

    companion object {
        fun launch(host: Fragment) {
            val dialog = SuggestedPlayerCountPollFragment()
            dialog.setStyle(STYLE_NORMAL, R.style.Theme_bgglight_Dialog)
            host.showAndSurvive(dialog)
        }
    }
}
