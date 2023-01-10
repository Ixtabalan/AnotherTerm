package com.pavelsikun.seekbarpreference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Created by Pavel Sikun on 21.05.16.
 */

public class SeekBarPreference extends Preference implements View.OnClickListener,
        PreferenceControllerDelegate.ViewStateListener, PersistValueListener, ChangeValueListener {

    private PreferenceControllerDelegate controllerDelegate;

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public SeekBarPreference(final Context context, final AttributeSet attrs,
                             final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    public SeekBarPreference(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public SeekBarPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SeekBarPreference(final Context context) {
        super(context);
        init(null);
    }

    private void init(@Nullable final AttributeSet attrs) {
        setLayoutResource(R.layout.msbp_seekbar_view_layout);
        controllerDelegate = new PreferenceControllerDelegate(getContext(), false);

        controllerDelegate.setViewStateListener(this);
        controllerDelegate.setPersistValueListener(this);
        controllerDelegate.setChangeValueListener(this);

        controllerDelegate.loadValuesFromXml(attrs);
    }

    @Override
    protected void onBindView(final View view) {
        super.onBindView(view);
        controllerDelegate.onBind(view);
        controllerDelegate.setTitle(getTitle() != null ? getTitle().toString() : null);
    }

    @Override
    protected Object onGetDefaultValue(final TypedArray a, final int index) {
        return a.getInt(index, PreferenceControllerDelegate.DEFAULT_CURRENT_VALUE);
    }

    @Override
    protected void onSetInitialValue(final boolean restorePersistedValue,
                                     final Object defaultValue) {
        super.onSetInitialValue(restorePersistedValue, defaultValue);
        controllerDelegate.setCurrentValue(restorePersistedValue ?
                getPersistedInt(PreferenceControllerDelegate.DEFAULT_CURRENT_VALUE) :
                (int) defaultValue);
    }

    @Override
    public boolean persistInt(final int value) {
        return super.persistInt(value);
    }

    @Override
    public boolean onChange(final int value) {
        return callChangeListener(value);
    }

    @Override
    public void onClick(final View v) {
        controllerDelegate.onClick(v);
    }

    public int getMaxValue() {
        return controllerDelegate.getMaxValue();
    }

    public void setMaxValue(final int maxValue) {
        controllerDelegate.setMaxValue(maxValue);
    }

    public int getMinValue() {
        return controllerDelegate.getMinValue();
    }

    public void setMinValue(final int minValue) {
        controllerDelegate.setMinValue(minValue);
    }

    public int getInterval() {
        return controllerDelegate.getInterval();
    }

    public void setInterval(final int interval) {
        controllerDelegate.setInterval(interval);
    }

    public int getCurrentValue() {
        return controllerDelegate.getCurrentValue();
    }

    public void setCurrentValue(final int currentValue) {
        controllerDelegate.setCurrentValue(currentValue);
        persistInt(controllerDelegate.getCurrentValue());
    }

    public String getMeasurementUnit() {
        return controllerDelegate.getUnit();
    }

    public void setMeasurementUnit(final String measurementUnit) {
        controllerDelegate.setUnit(measurementUnit);
    }

    @Override
    public void setTitle(final CharSequence title) {
        super.setTitle(title);
        controllerDelegate.setTitle(title != null ? title.toString() : null);
    }

    public boolean isDialogEnabled() {
        return controllerDelegate.isDialogEnabled();
    }

    public void setDialogEnabled(final boolean dialogEnabled) {
        controllerDelegate.setDialogEnabled(dialogEnabled);
    }

    public void setDialogStyle(final int dialogStyle) {
        controllerDelegate.setDialogStyle(dialogStyle);
    }
}
