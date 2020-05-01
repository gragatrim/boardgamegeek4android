package com.boardgamegeek.ui.dialog

import android.util.Pair
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.boardgamegeek.extensions.showAndSurvive
import com.boardgamegeek.ui.viewmodel.NewPlayViewModel
import com.boardgamegeek.util.ColorUtils
import com.boardgamegeek.util.fabric.PlayerColorsManipulationEvent

class NewPlayPlayerColorPickerDialogFragment : ColorPickerDialogFragment() {
    private val viewModel by activityViewModels<NewPlayViewModel>()

    override fun onColorClicked(item: Pair<String, Int>?, requestCode: Int) {
        item?.let {
            PlayerColorsManipulationEvent.log("Add", it.first)
            viewModel.addColorToPlayer(requestCode, it.first)
        }
    }

    companion object {
        fun launch(activity: FragmentActivity, playerDescription: String, featuredColors: List<String>, selectedColor: String?, disabledColors: List<String>, playerIndex: Int) {
            val df = NewPlayPlayerColorPickerDialogFragment().apply {
                arguments = createBundle(
                        playerDescription,
                        ColorUtils.getColorList(),
                        ArrayList(featuredColors),
                        selectedColor,
                        ArrayList(disabledColors),
                        requestCode = playerIndex)
            }
            activity.showAndSurvive(df)
        }
    }
}
