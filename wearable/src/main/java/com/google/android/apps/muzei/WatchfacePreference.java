package com.google.android.apps.muzei;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import net.nurik.roman.muzei.R;

import java.io.IOException;
import java.io.InputStream;

/**
 * A Preference which shows a preview of the watchface
 */
public class WatchfacePreference extends Preference {
    public WatchfacePreference(Context context) {
        this(context, null);
    }

    public WatchfacePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WatchfacePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WatchfacePreference(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.watchface_preview);
    }

    @Override
    protected View onCreateView(final ViewGroup parent) {
        View view = super.onCreateView(parent);
        ImageView background = view.findViewById(R.id.background);
        try (InputStream image = getContext().getAssets().open("starrynight.jpg")) {
            background.setImageBitmap(BitmapFactory.decodeStream(image));
        } catch (IOException e) {
            // Don't you fail on me
        }
        return view;
    }
}
