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
import android.graphics.Color;
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
    private class Engine extends CanvasWatchFaceService.Engine {

        private static final int DEFAULT_MAX = 0;
        private static final int DEFAULT_MIN = 0;

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
        int minTemp;
        int maxTemp;

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

        private void setTemp(int min, int max) {
            PreferenceManager.getDefaultSharedPreferences(WatchFaceService.this).edit()
                    .putInt("MIN", min)
                    .putInt("MAX", max)
                    .apply();
        }

        private void loadTemp() {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(WatchFaceService.this);
            minTemp = preferences.getInt("MIN", DEFAULT_MIN);
            maxTemp = preferences.getInt("MAX", DEFAULT_MAX);
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

            Log.d(TAG, "onCreate: min temp before "+minTemp);

            tempReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    minTemp = intent.getIntExtra("MIN", DEFAULT_MIN);
                    maxTemp = intent.getIntExtra("MAX", DEFAULT_MAX);
                    setTemp(minTemp, maxTemp);
                    invalidate();
                }
            };

            Log.d(TAG, "onCreate: min temp after "+minTemp);
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

            mMaxPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinPaint = createTextPaint(resources.getColor(R.color.digital_date));

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
            Log.d(TAG, "onDraw: time set");

            String dateText = String.format(Locale.getDefault(), "%1$tb %1$te, %1$ta", mCalendar);
            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            String timeString = hourString + ":" + minuteString;

            String min = "";
            String max = "";
            int iconWidth = 0;
            //if (minTemp != DEFAULT_MIN)
                min = minTemp + "°";
            //if (maxTemp != DEFAULT_MAX)
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
            Log.d(TAG, "onDraw: max min temp not null");

            y = bounds.centerY() + (mHourPaint.measureText(dateText) / 4);
            //Icon
            // draw weather icon at x
            if (bitmap != null && !mLowBitAmbient) {
                canvas.drawBitmap(bitmap, x, y - bitmap.getHeight() / 2, mIconPaint);
                Log.d(TAG, "onDraw: draw bitmap");
            }

            Log.d(TAG, "onDraw: maxtemp " + maxTemp);
            // draw weather icon at x
            float highTempSize = mDatePaint.measureText(Integer.toString(maxTemp));

            iconWidth = bitmap.getWidth();
            //High temp
            canvas.drawText("   " + max, x + iconWidth, y, mDatePaint);

            //Low temp
            canvas.drawText("  " + min, x + iconWidth + mDatePaint.measureText("   "), y + highTempSize, mMinPaint);

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
