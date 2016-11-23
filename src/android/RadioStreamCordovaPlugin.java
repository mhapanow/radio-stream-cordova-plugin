package com.inodes.radiostream;

import java.util.HashMap;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;

public class RadioStreamCordovaPlugin extends CordovaPlugin {

    private static final String DEFAULT_ARTIST = "Monkey Radio";
    private static final String STREAMING_URL = "http://cdn.instream.audio:9189/stream";
    private static final String CENTOVACAST_URL = "http://cdn.instream.audio:2199";
    private static final String CENTOVACAST_USER = "monkeyra";
    private static final String CENTOVACAST_PASS = "vlW13jF6u6";
    private static final String LASTFM_APIKEY = "812117bad533a130518319f6d6614edf";
    private static final boolean AUTO_PLAYBACK = true;

    private RadioStream rs;

    /**
     * Constructor.
     */
    public RadioStreamCordovaPlugin() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

	/**
	 * Gets the application context from cordova's main activity.
	 * @return the application context
	 */
	private Context getApplicationContext() {
		return this.cordova.getActivity().getApplicationContext();
	}

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getInfo".equals(action)) {
            JSONObject r = new JSONObject();
            r.put("result", "all good for now");
            callbackContext.success(r);
        } else if ("initialize".equals(action)) {
            JSONObject r = new JSONObject();
            JSONObject json = new JSONObject(args.getString(0));
            

            rs = RadioStream.getInstance(getApplicationContext());
            rs.setActivity(this.cordova.getActivity());

            rs.setAutoStart(json.has('autoStart') ? json.getBoolean('autoStart') : AUTO_PLAYBACK);
            rs.setStreamingUrl(json.has('streamingURL') ? json.getString('streamingURL') : STREAMING_URL);
            rs.setCentovaUser(json.has('centovacastUser') ? json.getString('centovacastUser') : CENTOVACAST_USER);
            rs.setCentovaPass(json.has('centovacastPass') ? json.getString('centovacastPass') : CENTOVACAST_PASS);
            rs.setCentovaURL(json.has('centovacastURL') ? json.getString('centovacastURL') : CENTOVACAST_URL);
            rs.setLastFMApiKey(json.has('lastfmApiKey') ? json.getString('lastfmApiKey') : LASTFM_APIKEY);
            rs.setDefaultArtist(json.has('defaultArtist') ? json.getString('defaultArtist') : DEFAULT_ARTIST);
            rs.addStatusChangeListener(new RadioStream.StatusChangeListener() {
                @Override
                public void onStatusChange(int status) {
                    // do nothing right now
                }
            });
            rs.addTrackChangeListener(new RadioStream.TrackChangeListener() {
                @Override
                public void onTrackChange(final RadioStream.TrackInfo trackInfo) {
                    // do nothing right now
                }
            });

            rs.initialize();
            
            r.put("result", "ok");
            callbackContext.success(r);

        } else if ("play".equals(action)) {
            JSONObject r = new JSONObject();
            rs.startPlaying();
            r.put("result", "ok");
            callbackContext.success(r);

        } else if ("stop".equals(action)) {
            JSONObject r = new JSONObject();
            rs.stopPlaying();
            r.put("result", "ok");
            callbackContext.success(r);

        } else if ("getPlayerStatus".equals(action)) {
            JSONObject r = new JSONObject();
            int status = rs.getPlayerStatus();
            r.put("status", status);
            r.put("result", "ok");
            callbackContext.success(r);

        } else if ("getPlayerStatus".equals(action)) {
            JSONObject r = new JSONObject();
            RadioStream.TrackInfo info = rs.getTrackInfo();
            r.put("trackId", info.getTrackId());
            r.put("title", info.getTitle());
            r.put("album", info.getAlbum());
            r.put("artist", info.getArtist());
            r.put("cover", info.getCover());
            r.put("duration", info.getStringDuration());
            r.put("result", "ok");
            callbackContext.success(r);
        }

        else {
            return false;
        }
        return true;
    }

}