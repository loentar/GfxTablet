package at.bitfire.gfxtablet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

@SuppressLint("ViewConstructor")
public class CanvasView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "GfxTablet.CanvasView";

	private enum InRangeStatus {
		OutOfRange,
		InRange,
		FakeInRange
	}

    final SharedPreferences settings;
    NetworkClient netClient;
	boolean acceptStylusOnly;
	InRangeStatus inRangeStatus;


    // setup

    public CanvasView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        // view is disabled until a network client is set
        setEnabled(false);

        settings = PreferenceManager.getDefaultSharedPreferences(context);
        settings.registerOnSharedPreferenceChangeListener(this);
        setBackground();
        setInputMethods();
		inRangeStatus = InRangeStatus.OutOfRange;
    }

    public void setNetworkClient(NetworkClient networkClient) {
        netClient = networkClient;
        setEnabled(true);
    }


    // settings

    protected void setBackground() {
        if (settings.getBoolean(SettingsActivity.KEY_DARK_CANVAS, false))
            setBackgroundColor(Color.BLACK);
        else
            setBackgroundResource(R.drawable.bg_grid_pattern);
    }

    protected void setInputMethods() {
        acceptStylusOnly = settings.getBoolean(SettingsActivity.KEY_PREF_STYLUS_ONLY, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case SettingsActivity.KEY_PREF_STYLUS_ONLY:
                setInputMethods();
                break;
            case SettingsActivity.KEY_DARK_CANVAS:
                setBackground();
                break;
        }
    }


    // drawing

    @Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.i(TAG, "Canvas size changed: " + w + "x" + h + " (before: " + oldw + "x" + oldh + ")");
		netClient.setAreaSize(w, h);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (isEnabled()) {
			for (int ptr = 0, ptrCount = event.getPointerCount(); ptr < ptrCount; ptr++)
				if (!acceptStylusOnly || (event.getToolType(ptr) == MotionEvent.TOOL_TYPE_STYLUS)) {
					float x = event.getX(ptr);
					float y = event.getY(ptr);
					float pressure = event.getPressure(ptr);
//					Log.v(TAG, String.format("Generic motion event logged: %f|%f, pressure %f", event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
					switch (event.getActionMasked()) {
					case MotionEvent.ACTION_HOVER_MOVE:
						netClient.sendEvent(NetworkClient.TYPE_MOTION, x, y, pressure);
						break;
					case MotionEvent.ACTION_HOVER_ENTER:
						inRangeStatus = InRangeStatus.InRange;
						netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, pressure, -1, true);
						break;
					case MotionEvent.ACTION_HOVER_EXIT:
						inRangeStatus = InRangeStatus.OutOfRange;
						netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, pressure, -1, false);
						break;
					}
				}
			return true;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (isEnabled()) {
			for (int ptr = 0, ptrCount = event.getPointerCount(); ptr < ptrCount; ptr++)
				if (!acceptStylusOnly || (event.getToolType(ptr) == MotionEvent.TOOL_TYPE_STYLUS)) {
					float x = event.getX(ptr);
					float y = event.getY(ptr);
					float pressure = event.getPressure(ptr);
//					Log.v(TAG, String.format("Touch event logged: action %d @ %f|%f (pressure %f)", event.getActionMasked(), event.getX(ptr), event.getY(ptr), event.getPressure(ptr)));
					switch (event.getActionMasked()) {
					case MotionEvent.ACTION_MOVE:
						netClient.sendEvent(NetworkClient.TYPE_MOTION, x, y, pressure);
						break;
					case MotionEvent.ACTION_DOWN:
						if (inRangeStatus == InRangeStatus.OutOfRange) {
							inRangeStatus = InRangeStatus.FakeInRange;
							netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, (short)0, -1, true);
						}
						netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, pressure, 0, true);
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, pressure, 0, false);
						if (inRangeStatus == InRangeStatus.FakeInRange) {
							inRangeStatus = InRangeStatus.OutOfRange;
							netClient.sendEvent(NetworkClient.TYPE_BUTTON, x, y, (short)0, -1, false);
						}
						break;
					}

				}
			return true;
		}
		return false;
	}
}
