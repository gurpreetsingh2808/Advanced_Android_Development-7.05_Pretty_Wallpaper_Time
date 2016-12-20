package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Gurpreet on 12/5/2016.
 */

public class WeatherListenerService extends WearableListenerService {
    public static final String ACTION_DATA = "ActionData";
    public static final String ACTION_IMAGE = "ActionImage";
    public static final String DATA_ITEM_RECEIVED_PATH = "WEATHER";
    private static final String TAG = WeatherListenerService.class.getSimpleName();

    public static Bitmap loadBitmapFromAsset(Asset asset, GoogleApiClient mGoogleApiClient) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult result =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDataChanged: " + dataEventBuffer);
        }
        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEventBuffer);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/weather")) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Intent intent = new Intent(ACTION_DATA);
                intent.putExtra("MIN", dataMapItem.getDataMap().getInt("MIN"));
                intent.putExtra("MAX", dataMapItem.getDataMap().getInt("MAX"));
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            } else if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/image")) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset profileAsset = dataMapItem.getDataMap().getAsset("weatherImage");
                Log.d(TAG, "onDataChanged: received image");
                Bitmap bitmap = loadBitmapFromAsset(profileAsset, googleApiClient);
                if (bitmap != null) {
                    File cacheDir = getCacheDir();
                    File f = new File(cacheDir, "image.jpg");
                    FileOutputStream fos;
                    try {
                        fos = new FileOutputStream(f);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                        Intent intent = new Intent(ACTION_IMAGE);
                        LocalBroadcastManager.getInstance(WeatherListenerService.this).sendBroadcast(intent);
                        Log.d(TAG, "onDataChanged: Image Saved to cache!");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
}
