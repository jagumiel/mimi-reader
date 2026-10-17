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

/**
 * Created by Eli Connelly on 2/25/2015.
 */

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.util.Typefaces;

public class IconTextView extends AppCompatTextView {

    public IconTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    public IconTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);

    }

    public IconTextView(Context context) {
        super(context);
        init(null);
    }

    private void init(AttributeSet attrs) {
        Typeface typeface = Typefaces.get(getContext(), getContext().getString(R.string.font_path));
        setTypeface(typeface);
    }

}
