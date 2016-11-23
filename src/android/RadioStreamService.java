package com.inodes.radiostream;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by mhapanowicz on 11/14/16.
 */

public class RadioStreamService extends MediaBrowserServiceCompat
        implements RadioStream.StatusChangeListener,RadioStream.TrackChangeListener,
        RadioStream.RadioDestroyListener {

    private static final String TAG = RadioStreamService.class.getName();

    public static int TYPE_WIFI = 1;
    public static int TYPE_MOBILE = 2;
    public static int TYPE_NOT_CONNECTED = 0;
    public static final String MEDIA_ID_ROOT = "__ROOT__";

    // Delay stopSelf by using a handler.
    private static final int STOP_DELAY = 30000;

    private MediaSessionCompat mSession;
    private MediaNotificationManager mMediaNotificationManager;
    private NetworkBroadcastReceiver mNetworkBroadcastReceiver;
    private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);

    private RadioStream mRadioStream;

/*
 * (non-Javadoc)
 * @see android.app.Service#onCreate()
 */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mRadioStream = RadioStream.getInstance(this);

        // Start a new MediaSession
        mSession = new MediaSessionCompat(this, "MusicService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(mRadioStream.getMediaSessionCallback());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Do I need to set a SessionActivity on a MusicSession?
        Context context = getApplicationContext();
        Intent intent = new Intent(context, mRadioStream.getActivity());
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mSession.setSessionActivity(pi);

        try {
            mMediaNotificationManager = new MediaNotificationManager(this);
        } catch (RemoteException e) {
            throw new IllegalStateException("Could not create a MediaNotificationManager", e);
        }

        mRadioStream.addStatusChangeListener(this);
        mRadioStream.addTrackChangeListener(this);
        mRadioStream.addRadioDestroyListener(this);

        registerNetworkStateReceiver();

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY);
        mSession.setPlaybackState(stateBuilder.build());

    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
            MediaButtonReceiver.handleIntent(mSession, startIntent);
        }

        onStatusChange(mRadioStream.getPlayerStatus());

        // Reset the delay handler to enqueue a message to stop the service if
        // nothing is playing.
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
        return START_STICKY;
    }

    /**
     * Gets the activity to start when called
     * @return
     */
    public Class getActivity() {
        return mRadioStream.getActivity();
    }

    /**
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Service is being killed, so make sure we release our resources
        unregisterNetworkStateReceiver();
        mRadioStream.stopPlaying();
        mRadioStream.reset();
        mMediaNotificationManager.stopNotification();

        mSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                 Bundle rootHints) {
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentMediaId,
                               @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
    }

    public void stopRadio() {
        mRadioStream.stopPlaying();
    }

    public void playRadio() {
        mRadioStream.startPlaying();
    }

    public int getPlayerStatus() {
        return mRadioStream.getPlayerStatus();
    }

    @Override
    public void onStatusChange(int status) {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        if( status == RadioStream.PLAYER_STATUS_PLAYING ) {
            stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE);
        } else {
            stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY);

            // Reset the delay handler to enqueue a message to stop the service if
            // nothing is playing.
            mDelayedStopHandler.removeCallbacksAndMessages(null);
            mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);

        }
        mSession.setPlaybackState(stateBuilder.build());

        if( status == RadioStream.PLAYER_STATUS_PLAYING
                || status == RadioStream.PLAYER_STATUS_STOPPED )
            onTrackChange(mRadioStream.getTrackInfo());
    }

    @Override
    public void onRadioStreamDestroy() {
        onDestroy();
    }

    @Override
    public void onTrackChange(RadioStream.TrackInfo trackInfo) {
        String myArtist = new String(trackInfo.getArtist());
        if( myArtist.equalsIgnoreCase("Unknown")) myArtist = mRadioStream.getDefaultArtist();
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, trackInfo.getTrackId())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackInfo.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, myArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, trackInfo.getDuration() / 1000)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, trackInfo.getCover())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackInfo.getTitle())
                .build();

        mSession.setMetadata(metadata);

        // Try to start the notification system
        if( null != mMediaNotificationManager ) {
            mMediaNotificationManager.startNotification();
        }
    }

    /**
     * Registers the network state receiver
     */
    public void registerNetworkStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");

        mNetworkBroadcastReceiver = new NetworkBroadcastReceiver(mRadioStream);
        registerReceiver(mNetworkBroadcastReceiver, filter);

    }

    /**
     * Unregisters the network state receiver
     */
    public void unregisterNetworkStateReceiver() {
        if( mNetworkBroadcastReceiver != null ) {
            unregisterReceiver(mNetworkBroadcastReceiver);
            mNetworkBroadcastReceiver = null;
        }
    }

    /**
     * A simple broadcast receiver for changing network states
     */
    public class NetworkBroadcastReceiver extends BroadcastReceiver {
        private RadioStream mRadioStream;
        private int lastConnectionType = TYPE_NOT_CONNECTED;

        public NetworkBroadcastReceiver(RadioStream mRadioStream) {
            this.mRadioStream = mRadioStream;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            int connectionType = TYPE_NOT_CONNECTED;
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (null != activeNetwork) {
                if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                    connectionType = TYPE_WIFI;

                if(activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                    connectionType = TYPE_MOBILE;
            }

            if( lastConnectionType != TYPE_NOT_CONNECTED && connectionType != lastConnectionType )
                mRadioStream.reset();

            lastConnectionType = connectionType;
        }
    }
    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private static class DelayedStopHandler extends Handler {
        private final WeakReference<RadioStreamService> mWeakReference;

        private DelayedStopHandler(RadioStreamService service) {
            mWeakReference = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            RadioStreamService service = mWeakReference.get();
            if (service != null ) {
                if (service.getPlayerStatus() == RadioStream.PLAYER_STATUS_STOPPED
                        || service.getPlayerStatus() == RadioStream.PLAYER_STATUS_ERROR
                        || service.getPlayerStatus() == RadioStream.PLAYER_STATUS_NULL) {
                    Log.d(TAG, "Stopping service with delay handler.");
                    service.stopSelf();
                } else {
                    Log.d(TAG, "Ignoring delayed stop since the media player is in use.");
                    return;
                }
            }
        }
    }

}
