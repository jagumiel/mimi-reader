/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;

import com.emogoth.android.phone.mimi.R;

/**
 * Created by ferranribell on 19/08/15.
 */
public class ColorImageView extends AppCompatImageView {

    private int mColor;
    private int mBorderColor;
    private int mBorderColorSelected;
    private Drawable fill;
    private Drawable border;

    public ColorImageView(Context context) {
        super(context);
        init();
    }

    public ColorImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ColorPickerImageView,
                0, 0);
        try {
            setBorderColor(a.getColor(R.styleable.ColorPickerImageView_imageBorderColor, mBorderColor));
            setBorderColorSelected(a.getColor(R.styleable.ColorPickerImageView_imageBorderColorSelected, mBorderColorSelected));
            setBackgroundColor(a.getColor(R.styleable.ColorPickerImageView_imageBackgroundColor, mColor));
        } finally {
            a.recycle();
        }
    }

    private void init() {
        mBorderColor = ResourcesCompat.getColor(getResources(), R.color.border, getContext().getTheme());
        mBorderColorSelected = ResourcesCompat.getColor(getResources(), R.color.border_selected, getContext().getTheme());
        mColor = ResourcesCompat.getColor(getResources(), R.color.circle, getContext().getTheme());
        fill = DrawableCompat.wrap(AppCompatResources.getDrawable(getContext(), R.drawable.circle)).mutate();
        border = DrawableCompat.wrap(AppCompatResources.getDrawable(getContext(), R.drawable.circle_border)).mutate();
        setImageDrawable(fill);
        setBackground(border);
        updateColors();
    }

    public void setBackgroundColor(int color) {
        mColor = color;
        updateColors();
    }

    public int getBackgroundColor() {
        return mColor;
    }

    public void setBorderColor(int color) {
        mBorderColor = color;
        updateColors();
    }

    public void setBorderColorSelected(int color) {
        mBorderColorSelected = color;
        updateColors();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        updateColors();
    }

    private void updateColors() {
        // View's constructor can invoke overridden setters before initialization.
        if (fill != null) fill.setColorFilter(mColor, PorterDuff.Mode.SRC_IN);
        if (border != null) border.setColorFilter(isSelected() ? mBorderColorSelected : mBorderColor, PorterDuff.Mode.SRC_IN);
    }
}
