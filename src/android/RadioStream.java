package com.inodes.radiostream;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mhapanowicz on 11/12/16.
 */

public class RadioStream {

    private static final String TAG = RadioStream.class.getName();

    // This singleton
    private static RadioStream singleton;

    // Statuses and constants
    public static final int PLAYER_STATUS_UNINITIALIZED = 0;
    public static final int PLAYER_STATUS_INITIALIZED = 1;
    public static final int PLAYER_STATUS_STOPPED = 2;
    public static final int PLAYER_STATUS_PLAYING = 3;
    public static final int PLAYER_STATUS_ERROR = 4;
    public static final int PLAYER_STATUS_NULL = 5;

    public static final String DEFAULT_STREAMING_URL = "http://cdn.instream.audio:9189/stream";

    // Internal attributes
    private MediaPlayer player;
    private String streamingUrl;
    private int playerStatus;
    private boolean autoStart;
    private String lastFMApiKey;
    private String centovaURL;
    private String centovaUser;
    private String centovaPass;
    private String defaultArtist;

    private Context mContext;
    private Class activity;

    private TrackInfo trackInfo;

    private List<StatusChangeListener> statusChangeListener;
    private List<TrackChangeListener> trackChangeListener;
    private List<RadioDestroyListener> radioDestroyListener;

    private MediaSessionCallback mMediaSessionCallback;
    private Thread infoUpdater;

    private long bufferingStartTime = 0;
    private long bufferingEndTime = 0;

    private RadioStream(Context context) {

        this.mContext = context;

        // Initialization Routines
        this.playerStatus = PLAYER_STATUS_NULL;
        this.streamingUrl = DEFAULT_STREAMING_URL;
        this.autoStart = false;
        this.trackInfo = new TrackInfo();
        this.defaultArtist = "";

        this.statusChangeListener = new ArrayList<>();
        this.trackChangeListener = new ArrayList<>();
        this.radioDestroyListener = new ArrayList<>();
        this.mMediaSessionCallback = new MediaSessionCallback();

    }

    /**
     * Gets the RadioStream singleton
     *
     * @return
     */
    public static RadioStream getInstance(Context context) {
        if (singleton == null)
            singleton = new RadioStream(context);
        return singleton;
    }

    /**
     * Destroys this session
     */
    public void destroy() {
        reset();
        for( RadioDestroyListener listener : radioDestroyListener ) {
            try {
                listener.onRadioStreamDestroy();
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieve the main activity to start when called
     * @return
     */
    public Class getActivity() {
        return activity;
    }

    /**
     * Sets the main activity to start when called
     * @param activity
     */
    public void setActivity(Class activity) {
        this.activity = activity;
    }

    /**
     * Gets the current player status
     *
     * @return
     */
    public int getPlayerStatus() {
        return playerStatus;
    }

    /**
     * Privately sets a new player status
     * and executes all its listeners
     *
     * @param status
     */
    private void setPlayerStatus(int status) {
        this.playerStatus = status;
        for( StatusChangeListener listener : statusChangeListener ) {
            try {
                listener.onStatusChange(status);
            } catch( Exception e ) {}
        }
    }

    /**
     * Retrieves the default Radio Artist
     * @return
     */
    public String getDefaultArtist() {
        return defaultArtist;
    }

    /**
     * Sets a default Radio Artist
     * @param defaultArtist
     */
    public void setDefaultArtist(String defaultArtist) {
        this.defaultArtist = defaultArtist;
    }

    /**
     * Is this player configured to auto start?
     *
     * @return

     */
    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * Sets the auto start configuration for this player
     *
     * @param autoStart
     */
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    /**
     * Removes all status change listeners
     */
    public void clearStatusChangeListener() {
        statusChangeListener = new ArrayList<>();
    }

    /**
     * Adds a new status change listener
     *
     * @param listener
     */
    public void addStatusChangeListener(StatusChangeListener listener) {
        if (statusChangeListener == null)
            statusChangeListener = new ArrayList<>();

        statusChangeListener.add(listener);
    }

    /**
     * Replaces all other status change listeners
     *
     * @param listener
     */
    public void setStatusChangeListener(StatusChangeListener listener) {
        clearStatusChangeListener();
        addStatusChangeListener(listener);
    }

    /**
     * Removes all radio destroy listeners
     */
    public void clearRadioDestroyListener() {
        radioDestroyListener = new ArrayList<>();
    }

    /**
     * Adds a new radio destroy listener
     *
     * @param listener
     */
    public void addRadioDestroyListener(RadioDestroyListener listener) {
        if (radioDestroyListener== null)
            radioDestroyListener = new ArrayList<>();

        radioDestroyListener.add(listener);
    }

    /**
     * Replaces all other radio destroy listeners
     *
     * @param listener
     */
    public void setRadioDestroyListener(RadioDestroyListener listener) {
        clearRadioDestroyListener();
        addRadioDestroyListener(listener);
    }

    /**
     * Removes all track change listeners
     */
    public void clearTrackChangeListener() {
        trackChangeListener = new ArrayList<>();
    }

    /**
     * Adds a new track change listener
     *
     * @param listener
     */
    public void addTrackChangeListener(TrackChangeListener listener) {
        if (trackChangeListener == null)
            trackChangeListener = new ArrayList<>();

        trackChangeListener.add(listener);
    }

    /**
     * Replaces all other track change listeners
     *
     * @param listener
     */
    public void setTrackChangeListener(TrackChangeListener listener) {
        clearTrackChangeListener();
        addTrackChangeListener(listener);
    }

    /**
     * Gets the current streaming URL
     *
     * @return
     */
    public String getStreamingUrl() {
        return streamingUrl;
    }

    /**
     * Updates the current streaming URL
     *
     * @param streamingUrl
     */
    public void setStreamingUrl(String streamingUrl) {
        this.streamingUrl = streamingUrl;
    }

    /**
     * Retrieves LastFM API Key
     * @return
     */
    public String getLastFMApiKey() {
        return lastFMApiKey;
    }

    /**
     * Sets the LastFM API KEY
     * @param lastFMApiKey
     */
    public void setLastFMApiKey(String lastFMApiKey) {
        this.lastFMApiKey = lastFMApiKey;
    }

    /**
     * Retrieves Centova CAST URL
     * @return
     */
    public String getCentovaURL() {
        return centovaURL;
    }

    /**
     * Sets the Centova CAST URL
     * @param centovaURL
     */
    public void setCentovaURL(String centovaURL) {
        this.centovaURL = centovaURL;
    }

    /**
     * Retrieves Centova CAST User
     * @return
     */
    public String getCentovaUser() {
        return centovaUser;
    }

    /**
     * Sets the Centova CAST User
     * @param centovaUser
     */
    public void setCentovaUser(String centovaUser) {
        this.centovaUser = centovaUser;
    }

    /**
     * Retrieves Centova CAST Password
     * @return
     */
    public String getCentovaPass() {
        return centovaPass;
    }

    /**
     * Sets the Centova CAST Password
     * @param centovaPass
     */
    public void setCentovaPass(String centovaPass) {
        this.centovaPass = centovaPass;
    }

    /**
     * Retrieves current song info
     * @return
     */
    public TrackInfo getTrackInfo() {
        return trackInfo;
    }

    /**
     * Resets the plugin status
     */
    public void reset() {
        if( player != null ) {
            player.stop();
            player.reset();
            player = null;
            setPlayerStatus(PLAYER_STATUS_NULL);
        }
    }

    /**
     * Initialize the plugin
     */
    public void initialize() {
        if (null == player) {
            player = new MediaPlayer();
            setPlayerStatus(PLAYER_STATUS_UNINITIALIZED);
        }

        if (playerStatus == PLAYER_STATUS_ERROR || playerStatus == PLAYER_STATUS_UNINITIALIZED) {

            player.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {

                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    Log.i("Buffering", "" + percent);
                }
            });

            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    System.out.println("Error Found!!!!");
                    return false;
                }
            });

            try {
                bufferingStartTime = System.currentTimeMillis();
                player.reset();
                player.setDataSource(getStreamingUrl());
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);

                player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                    public void onPrepared(MediaPlayer mp) {
                        bufferingEndTime = System.currentTimeMillis();
                        setPlayerStatus(PLAYER_STATUS_STOPPED);
                        player.setVolume(0, 0);
                        player.start();
                        if (isAutoStart())
                            startPlaying();
                        Log.d(TAG, "Buffering offset was " + getBufferOffset() + "ms");
                    }
                });

                setPlayerStatus(PLAYER_STATUS_INITIALIZED);
                player.prepareAsync();

                // Starts the updater thread
                if( infoUpdater == null ) {
                    infoUpdater = new Thread(new RadioInfoUpdater());
                    infoUpdater.start();
                }

            } catch (IllegalArgumentException | SecurityException
                    | IllegalStateException | IOException e) {
                e.printStackTrace();
                setPlayerStatus(PLAYER_STATUS_ERROR);
            }
        }
    }

    /**
     * Returns the buffering offset in millis
     * @return
     */
    public long getBufferOffset() {
        if( bufferingEndTime == 0 || bufferingStartTime == 0 )
            return 0;
        else
            return bufferingEndTime - bufferingStartTime;
    }

    /**
     * Starts playing audio
     */
    public void startPlaying() {
        if (playerStatus == PLAYER_STATUS_NULL) {
            initialize();
        }
        if (playerStatus == PLAYER_STATUS_STOPPED) {
            Intent i = new Intent(mContext, RadioStreamService.class);
            mContext.startService(i);

            player.setVolume(1, 1);
            setPlayerStatus(PLAYER_STATUS_PLAYING);
        }
    }

    /**
     * Stops playing audio
     */
    public void stopPlaying() {
        if (playerStatus == PLAYER_STATUS_PLAYING) {
            player.setVolume(0, 0);
            setPlayerStatus(PLAYER_STATUS_STOPPED);
        }
    }

    /**
     * Switch playing status. If this player is stopped,
     * starts playing. If its playing, stops playing
     */
    public void switchPlaying() {
        if (playerStatus == PLAYER_STATUS_PLAYING)
            stopPlaying();
        else if (playerStatus == PLAYER_STATUS_STOPPED
                || playerStatus == PLAYER_STATUS_ERROR
                || playerStatus == PLAYER_STATUS_NULL)
            startPlaying();
    }

    /**
     * Interface to listen for status changes
     */
    public interface StatusChangeListener {
        void onStatusChange(int status);
    }

    /**
     * Interface to listen for destroy
     */
    public interface RadioDestroyListener {
        void onRadioStreamDestroy();
    }

    public MediaSessionCompat.Callback getMediaSessionCallback() {
        return mMediaSessionCallback;
    }

    /**
     * Interface to listen for track changes
     */
    public interface TrackChangeListener {
        void onTrackChange(TrackInfo trackInfo);
    }

    public class TrackInfo {

        private String trackId;
        private String title;
        private String album;
        private String artist;
        private String cover;
        private long time;
        private long duration;

        public String getStringDuration() {
            int minutes = (int)(getDuration() / 60000);
            int seconds = (int)(getDuration() % 60000) / 1000;
            StringBuffer sb = new StringBuffer();
            if(minutes < 10 ) sb.append(0);
            sb.append(minutes);
            sb.append(":");
            if(seconds < 10 ) sb.append(0);
            sb.append(seconds);
            return sb.toString();
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public String getTrackId() {
            return trackId;
        }

        public void setTrackId(String trackId) {
            this.trackId = trackId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getCover() {
            return cover;
        }

        public void setCover(String cover) {
            this.cover = cover;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        @Override
        public String toString() {
            return "TrackInfo{" +
                    "trackId='" + trackId + '\'' +
                    ", title='" + title + '\'' +
                    ", album='" + album + '\'' +
                    ", artist='" + artist + '\'' +
                    ", cover='" + cover + '\'' +
                    ", time=" + time +
                    '}';
        }
    }

    /**
     * Daemon class that constantly updates information about
     * the current playing song
     */
    public class RadioInfoUpdater implements Runnable {

        private HttpClient httpClient = new DefaultHttpClient();
        private String songsUrl;

        public RadioInfoUpdater() {
             songsUrl = getCentovaURL()
                     + "/api.php?xm=server.getsongs&f=json&a[username]="
                     + getCentovaUser() + "&a[password]=" + getCentovaPass();
        }

        @Override
        public void run() {
            while( true ) {

                try {
                    updateSongInformation();
                } catch (Exception e ) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(10000);
                } catch( Throwable t ) {}
            }

        }

        /**
         * Calls Centova CAST and LastFM Web Services to get
         * information about the current song
         */
        public void updateSongInformation() throws IOException, JSONException {
            String songInfoStr = getAPISongsInformation();
            JSONObject songInfoJson = new JSONObject(songInfoStr);
            JSONArray jsonArr = songInfoJson.getJSONObject("response")
                    .getJSONObject("data").getJSONArray("songs");
            JSONObject songJson = jsonArr.getJSONObject(0);

            // Checks timing for buffering correction
            long virtualNow = (System.currentTimeMillis() + getBufferOffset()) / 1000;
            long trackTime = songJson.getLong("time");
            Log.d(TAG, "----------------- trackTime " + trackTime + " and virtual now "
                    + virtualNow + " dif: " +(trackTime - virtualNow));
            if( trackTime > virtualNow && jsonArr.length() > 1 )
                songJson = jsonArr.getJSONObject(1);

            JSONObject trackJson = songJson.getJSONObject("track");

            String trackId = String.valueOf(trackJson.getLong("royaltytrackid"));
            if (null != trackId && !trackId.equals(getTrackInfo().getTrackId())) {
                getTrackInfo().setTrackId(trackId);
                getTrackInfo().setAlbum(trackJson.getString("album"));
                getTrackInfo().setArtist(trackJson.getString("artist"));
                getTrackInfo().setTitle(trackJson.getString("title"));
                getTrackInfo().setTime(songJson.getLong("time"));
                getTrackInfo().setDuration(0);
                getTrackInfo().setCover(null);

                String coverStr = null;
                try {
                    coverStr = getAPISongCover(getTrackInfo());
                    JSONObject coverJson = new JSONObject(coverStr).getJSONObject("track");
                    if( coverJson.has("album")) {
                        JSONObject albumJson = coverJson.getJSONObject("album");

                        getTrackInfo().setDuration(Long.parseLong(coverJson.getString("duration")));
                        if (getTrackInfo().getAlbum().equals(""))
                            getTrackInfo().setAlbum(albumJson.getString("title"));

                        JSONArray images = albumJson.getJSONArray("image");
                        String cover = null;
                        for (int i = 0; i < images.length(); i++) {
                            JSONObject image = images.getJSONObject(i);
                            if (cover == null || image.getString("size").equals("extralarge")) {
                                cover = image.getString("#text");
                            }
                        }

                        if (cover != null) cover = cover.replaceAll("https:", "http:");
                        getTrackInfo().setCover(cover);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "coverStr was: " + coverStr, e);
                }

                for( TrackChangeListener listener : trackChangeListener ) {
                    try {
                        listener.onTrackChange(getTrackInfo());
                    } catch( Exception e ) {
                        e.printStackTrace();
                    }
                }
            }

        }

        /**
         * Gets information about the cover of the current song
         *
         * @param info
         * @return
         * @throws IOException
         */
        private String getAPISongCover(TrackInfo info) throws IOException {
            String apiUrl = "http://ws.audioscrobbler.com/2.0/?method=track.getinfo&api_key="
                    + getLastFMApiKey() + "&artist=" + URLEncoder.encode(info.getArtist(), "UTF-8")
                    + "&track=" + URLEncoder.encode(info.getTitle(), "UTF-8") + "&format=json";
            HttpGet httpGet = new HttpGet(apiUrl);
            httpGet.setHeader("Accept", "application/json");
            // Execute HTTP Post Request
            HttpResponse res = httpClient.execute(httpGet);
            if( res.getStatusLine().getStatusCode() == 200 ) {

                // Get hold of the response entity
                HttpEntity entity = res.getEntity();
                // If the response does not enclose an entity, there is no need
                // to worry about connection release

                if (entity != null) {

                    // A Simple JSON Response Read
                    InputStream instream = entity.getContent();
                    String result= convertStreamToString(instream);
                    // now you have the string representation of the HTML request
                    try {
                        instream.close();
                    } catch( IOException e ) {
                        // Ignore it for now!
                    }

                    return result;
                }

            }

            return null;
        }

        /**
         * Call the centova API to get informaton about the current song
         *
         * @return
         * @throws IOException
         */
        private String getAPISongsInformation() throws IOException {
            HttpGet httpGet = new HttpGet(songsUrl);
            httpGet.setHeader("Accept", "application/json");
            // Execute HTTP Post Request
            HttpResponse res = httpClient.execute(httpGet);
            if( res.getStatusLine().getStatusCode() == 200 ) {

                // Get hold of the response entity
                HttpEntity entity = res.getEntity();
                // If the response does not enclose an entity, there is no need
                // to worry about connection release

                if (entity != null) {

                    // A Simple JSON Response Read
                    InputStream instream = entity.getContent();
                    String result= convertStreamToString(instream);
                    // now you have the string representation of the HTML request
                    try {
                        instream.close();
                    } catch( IOException e ) {
                        // Ignore it for now!
                    }

                    return result;
                }
            }

            return null;
        }

        /**
         * Converts an input stream to a Simple String
         *
         * @param is
         * @return
         */
        private String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the BufferedReader.readLine()
		 * method. We iterate until the BufferedReader return null which means
		 * there's no more data to read. Each line will appended to a StringBuilder
		 * and returned as String.
		 */
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();

            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return sb.toString();
        }

    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
        }

        @Override
        public void onSkipToQueueItem(long queueId) {
        }

        @Override
        public void onSeekTo(long position) {
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onStop() {
        }

        @Override
        public void onSkipToNext() {
        }

        @Override
        public void onSkipToPrevious() {
        }

        @Override
        public void onCustomAction(@NonNull String action, Bundle extras) {
        }

        /**
         * Handle free and contextual searches.
         * <p/>
         * All voice searches on Android Auto are sent to this method through a connected
         * {@link android.support.v4.media.session.MediaControllerCompat}.
         * <p/>
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         * <p/>
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an {@link AsyncTask} as we do here).
         **/
        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
        }
    }

}
