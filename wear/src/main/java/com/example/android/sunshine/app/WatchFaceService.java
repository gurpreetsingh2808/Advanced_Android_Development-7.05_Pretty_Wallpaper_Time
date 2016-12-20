package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.wear.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
    private class Engine extends CanvasWatchFaceService.Engine
    {

        private static final String DEFAULT_MAX = "1000";
        private static final String DEFAULT_MIN = "-273";
        BroadcastReceiver tempReceiver;
        BroadcastReceiver imageReceiver;
        Bitmap bitmap;
        Bitmap ambientBitmap;


        static final String COLON_STRING = ":";

        static final int MSG_UPDATE_TIME = 0;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;

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
        Bitmap mIcon;
        String minTemp;
        String maxTemp;
        Rect rect = new Rect();

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

        // service methods (see other sections)

        private void setTemp(String min, String max) {
            PreferenceManager.getDefaultSharedPreferences(WatchFaceService.this).edit()
                    .putString("MIN", min)
                    .putString("MAX", max)
                    .apply();
        }

        private void loadTemp() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(WatchFaceService.this);
            minTemp = preferences.getString("MIN", DEFAULT_MIN);
            maxTemp = preferences.getString("MAX", DEFAULT_MAX);
        }

        private void loadBitmap() {
            File cacheDir = getBaseContext().getCacheDir();
            File f = new File(cacheDir, "image.jpg");
            FileInputStream fis;
            try {
                fis = new FileInputStream(f);
                bitmap = BitmapFactory.decodeStream(fis);
                ambientBitmap = Util.toGrayscale(bitmap);
                invalidate();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            tempReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    minTemp = intent.getStringExtra("MIN");
                    maxTemp = intent.getStringExtra("MAX");
                    setTemp(minTemp, maxTemp);
                    invalidate();
                }
            };
            IntentFilter tempFilter = new IntentFilter(WeatherListenerService.ACTION_DATA);
            LocalBroadcastManager.getInstance(WatchFaceService.this).registerReceiver(tempReceiver, tempFilter);
            imageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    loadBitmap();
                }
            };
            loadBitmap();
            IntentFilter imageFilter = new IntentFilter(WeatherListenerService.ACTION_IMAGE);
            LocalBroadcastManager.getInstance(WatchFaceService.this).registerReceiver(imageReceiver, imageFilter);


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
            loadTemp();

            // create graphic styles
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor);
            mColonPaint = createTextPaint(mInteractiveHourDigitsColor);
            mIconPaint = new Paint();

            mMaxPaint = createTextPaint(mInteractiveHourDigitsColor);
            mMinPaint = createTextPaint(mInteractiveHourDigitsColor);

///            ...

            // allocate a Calendar to calculate local time using the UTC time and time zone
            mCalendar = Calendar.getInstance();
            mDate = new Date();
//////            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (imageReceiver != null)
                LocalBroadcastManager.getInstance(WatchFaceService.this).unregisterReceiver(imageReceiver);
            if (tempReceiver != null)
                LocalBroadcastManager.getInstance(WatchFaceService.this).unregisterReceiver(tempReceiver);
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
            Log.d(TAG, "onDraw: time set");

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            float x = mXOffset;
            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);
            canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            //x += mColonWidth;

            //  Draw date and month
            float y = mYOffset;
            y += mHourPaint.measureText(hourString);
            //x += mMinutePaint.measureText(minuteString);
            String dateText = String.format(Locale.getDefault(), "%1$tb %1$te", mCalendar);
            canvas.drawText(dateText, mXOffset, y, mDatePaint);

            //  Draw day
            y -= mDatePaint.measureText(dateText) / 3;
            String dayText = String.format(Locale.getDefault(), "%1$ta", mCalendar);
            canvas.drawText(dayText, mXOffset, y, mDatePaint);

            // draw weather text at x
//Draw Icon and Temperatures
            if (maxTemp != null && minTemp != null) {
                Log.d(TAG, "onDraw: max min temp not null");

                float tempYOffset = mYOffset + getResources().getDimension(R.dimen.digital_date_text_margin_bottom);
                //Icon
                if (mIcon != null && !mLowBitAmbient)
                    canvas.drawBitmap(mIcon, mXOffset - mIcon.getWidth() - mIcon.getWidth() / 4, tempYOffset - mIcon.getHeight() / 2, mIconPaint);
                ////// canvas.drawBitmap(mIcon, centerX - mIcon.getWidth() - mIcon.getWidth() / 4, tempYOffset - mIcon.getHeight() / 2, mIconPaint);
                //High temp
                canvas.drawText(maxTemp, x, tempYOffset, mMaxPaint);
                //Low temp
                float highTempSize = mMaxPaint.measureText(maxTemp);
                ///////float highTempRightMargin = getResources().getDimension(R.dimen.digital_temp_text_margin_right);
                ////////canvas.drawText(minTemp, centerX + highTempSize + highTempRightMargin, tempYOffset, mMinPaint);
                //////////canvas.drawText(minTemp, centerX + highTempSize + 0.0f, tempYOffset, mMinPaint);
                canvas.drawText(minTemp, mXOffset + highTempSize + 0.0f, tempYOffset, mMinPaint);

            }
            else {
                Log.d(TAG, "onDraw: max min temp null");
            }
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
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode, so we may need to start or stop the timer
            updateTimer();
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

    }

}
