package com.pavelsikun.seekbarpreference;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Created by Pavel Sikun on 21.05.16.
 */

public class SeekBarPreferenceView extends FrameLayout implements View.OnClickListener {

    private PreferenceControllerDelegate controllerDelegate;

    public SeekBarPreferenceView(final Context context) {
        super(context);
        init(null);
    }

    public SeekBarPreferenceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SeekBarPreferenceView(final Context context, final AttributeSet attrs,
                                 final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public SeekBarPreferenceView(final Context context, final AttributeSet attrs,
                                 final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(@Nullable final AttributeSet attrs) {
        controllerDelegate = new PreferenceControllerDelegate(getContext(), true);
        controllerDelegate.loadValuesFromXml(attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        controllerDelegate.onBind(inflate(getContext(), R.layout.msbp_seekbar_view_layout, this));
    }

    @Override
    protected void onDetachedFromWindow() {
        controllerDelegate.onDetached();
        super.onDetachedFromWindow();
    }

    @Override
    public void onClick(final View v) {
        controllerDelegate.onClick(v);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        controllerDelegate.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return controllerDelegate.isEnabled();
    }

    public int getMaxValue() {
        return controllerDelegate.getMaxValue();
    }

    public void setMaxValue(final int maxValue) {
        controllerDelegate.setMaxValue(maxValue);
    }

    public String getTitle() {
        return controllerDelegate.getTitle();
    }

    public void setTitle(final String title) {
        controllerDelegate.setTitle(title);
    }

    public String getSummary() {
        return controllerDelegate.getSummary();
    }

    public void setSummary(final String summary) {
        controllerDelegate.setSummary(summary);
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
    }

    public String getMeasurementUnit() {
        return controllerDelegate.getUnit();
    }

    public void setMeasurementUnit(final String measurementUnit) {
        controllerDelegate.setUnit(measurementUnit);
    }

    public void setOnValueSelectedListener(final PersistValueListener persistValueListener) {
        controllerDelegate.setPersistValueListener(persistValueListener);
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
