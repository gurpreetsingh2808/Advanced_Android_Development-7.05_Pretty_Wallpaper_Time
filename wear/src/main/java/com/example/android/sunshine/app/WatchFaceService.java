package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class WatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = CanvasWatchFaceService.class.getSimpleName();

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /*
        * Update rate in milliseconds for interactive mode. We update once a second to advance the
        * second hand.
        */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);


    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }


    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        Bitmap weatherIcon;
        Double maxTemp = 0d;
        Double minTemp = 0d;
        String desc = "";
        int weatherId = 0;

        static final String COLON_STRING = ":";
        static final int MSG_UPDATE_TIME = 0;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;

        GoogleApiClient googleApiClient;

        // device features
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        // graphic objects
        private Paint mBackgroundPaint;
        private Paint mDatePaint;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        Paint mMinPaint;
        Paint mMaxPaint;
        Paint mColonPaint;
        Paint mIconPaint;

        float mColonWidth;
        float mXOffset;
        float mYOffset;

        Date mDate;

        int mInteractiveBackgroundColor =
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND;
        int mInteractiveHourDigitsColor =
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS;
        int mInteractiveMinuteDigitsColor =
                DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS;


        // handler to update the time once a second in interactive mode
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler
                                    .sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };


        // receiver to update the time zone
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Log.d(TAG, "onCreate: min temp before "+minTemp);

            /* initialize your watch face */
            // configure the system UI
            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle
                            .BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Log.d(TAG, "onCreate: wattch face");

            // load the background image
            Resources resources = WatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            //loadTemp();

            // create graphic styles
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor);
            mColonPaint = createTextPaint(mInteractiveHourDigitsColor);
            mIconPaint = new Paint();

            mMaxPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinPaint = createTextPaint(resources.getColor(R.color.digital_date));

///            ...

            // allocate a Calendar to calculate local time using the UTC time and time zone
            mCalendar = Calendar.getInstance();
            mDate = new Date();
//////            initFormats();
            googleApiClient = new GoogleApiClient.Builder(WatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }


        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFaceService.this.getResources();
            boolean isRound = insets.isRound();

            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mMinPaint.setTextSize(resources.getDimension(R.dimen.small_text_size));
            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION,
                    false);
            mHourPaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            /* the time changed */
            invalidate();
        }


        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            adjustPaintColorToCurrentMode(mBackgroundPaint, mInteractiveBackgroundColor,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            adjustPaintColorToCurrentMode(mColonPaint, mInteractiveMinuteDigitsColor,
                    DigitalWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            /* the wearable switched between modes */
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
            }
            invalidate();
            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* draw your watch face */
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            String dateText = String.format(Locale.getDefault(), "%1$tb %1$te, %1$ta", mCalendar);
            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            String timeString = hourString + ":" + minuteString;

            String min = "";
            String max = "";
            int iconWidth = 0;
                min = minTemp + "°";
                max = maxTemp + "°";

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float x = bounds.centerX() - (mDatePaint.measureText(dateText) / 2);
            float y = bounds.centerY() - (mHourPaint.measureText(dateText) / 4);

            //  Draw the date
            canvas.drawText(dateText, x, y, mDatePaint);

            // Draw the time.
            x = bounds.centerX() - (mHourPaint.measureText(timeString) / 2);
            canvas.drawText(timeString, x, bounds.centerY(), mHourPaint);


            //Draw Icon and Temperatures

            y = bounds.centerY() + (mHourPaint.measureText(dateText) / 4);
            //Icon
            // draw weather icon at x
            if (weatherIcon != null && !mLowBitAmbient) {
                canvas.drawBitmap(weatherIcon, x-mDatePaint.measureText("   "), y - weatherIcon.getHeight() / 2, mIconPaint);
                Log.d(TAG, "onDraw: draw weatherIcon");
            }

            Log.d(TAG, "onDraw: maxtemp " + maxTemp);
            // draw weather icon at x
            float highTempSize = mDatePaint.measureText(Double.toString(maxTemp));

            if(weatherIcon != null) {
                iconWidth = weatherIcon.getWidth();
            }
            //High temp
            canvas.drawText(max, x + iconWidth, y, mDatePaint);

            //Low temp
            canvas.drawText(min, x + iconWidth + mDatePaint.measureText(" "), y + (highTempSize/2), mMinPaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */
            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(googleApiClient, this.onDataChangedListener);
                    googleApiClient.disconnect();
                }
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode, so we may need to start or stop the timer
            updateTimer();
        }

        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        processConfigurationFor(item);
                    }
                }

                dataEvents.release();
                invalidate();
            }
        };

        private void processConfigurationFor(DataItem item) {
            if ("/weather_data".equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey("HIGH_TEMP")) {
                    maxTemp = dataMap.getDouble("HIGH_TEMP");
                    Log.e("HighTemp", maxTemp.toString());
                }

                if (dataMap.containsKey("LOW_TEMP")) {
                    minTemp = dataMap.getDouble("LOW_TEMP");
                    Log.e("LowTemp", minTemp.toString());
                }

                if (dataMap.containsKey("DESC")) {
                    desc = dataMap.getString("DESC");
                }

                if (dataMap.containsKey("ICON")) {
                    weatherId = dataMap.getInt("ICON");
                    updateWeatherIcon();
                    Log.e("Icon", String.valueOf(weatherId));
                }
                invalidate();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.e(TAG, "Connected");
            Wearable.DataApi.addListener(googleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);
            invalidate();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        private void updateWeatherIcon() {
            Resources resources = WatchFaceService.this.getResources();
            Drawable weatherBitmap;

            if (weatherId >= 200 && weatherId <= 232) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_storm, null);
            } else if (weatherId >= 300 && weatherId <= 321) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_light_rain, null);
            } else if (weatherId >= 500 && weatherId <= 504) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_rain, null);
            } else if (weatherId == 511) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_snow, null);
            } else if (weatherId >= 520 && weatherId <= 531) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_rain, null);
            } else if (weatherId >= 600 && weatherId <= 622) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_snow, null);
            } else if (weatherId >= 701 && weatherId <= 761) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_fog, null);
            } else if (weatherId == 761 || weatherId == 781) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_storm, null);
            } else if (weatherId == 800) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_clear, null);
            } else if (weatherId == 801) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_light_clouds, null);
            } else if (weatherId >= 802 && weatherId <= 804) {
                weatherBitmap = resources.getDrawable(R.drawable.ic_cloudy, null);
            } else { //default
                weatherBitmap = resources.getDrawable(R.drawable.ic_clear, null);
            }

            weatherIcon = ((BitmapDrawable) weatherBitmap).getBitmap();
        }
        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    processConfigurationFor(item);
                }

                dataItems.release();
                invalidate();
            }
        };

    }
}
