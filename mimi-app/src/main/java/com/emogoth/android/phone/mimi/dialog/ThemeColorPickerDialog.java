/* Licensed under the Apache License, Version 2.0. */
package com.emogoth.android.phone.mimi.dialog;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.emogoth.android.phone.mimi.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rarepebble.colorpicker.ColorPickerView;

/** A recoverable draft: only explicit OK publishes a result to the current activity. */
public class ThemeColorPickerDialog extends DialogFragment {
    public static final String TAG = "theme-color-picker";
    public static final String RESULT = "theme-color-result";
    public static final String SLOT = "slot";
    public static final String COLOR = "color";
    private ColorPickerView picker;

    public static ThemeColorPickerDialog newInstance(int slot, int color) {
        if (slot < 0 || slot > 3) throw new IllegalArgumentException("Unknown color slot");
        ThemeColorPickerDialog dialog = new ThemeColorPickerDialog();
        Bundle args = new Bundle();
        args.putInt(SLOT, slot);
        args.putInt(COLOR, color);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        int slot = requireArguments().getInt(SLOT);
        picker = new ColorPickerView(requireContext());
        picker.showAlpha(false);
        picker.setColor(savedInstanceState == null ? requireArguments().getInt(COLOR)
                : savedInstanceState.getInt(COLOR, requireArguments().getInt(COLOR)));
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getResources().getStringArray(R.array.color_types_list)[slot])
                .setView(picker)
                .setPositiveButton(R.string.ok, (ignored, which) -> {
                    Bundle result = new Bundle();
                    result.putInt(SLOT, slot);
                    result.putInt(COLOR, picker.getColor());
                    getParentFragmentManager().setFragmentResult(RESULT, result);
                })
                .setNegativeButton(R.string.cancel, null).create();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        if (picker != null) state.putInt(COLOR, picker.getColor());
        super.onSaveInstanceState(state);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        picker = null;
    }
}
