package com.cpjd.roblu.tba;

import android.os.StrictMode;
import android.util.Log;

import com.cpjd.main.TBA;
import com.cpjd.models.standard.Event;
import com.cpjd.models.standard.Match;
import com.cpjd.models.standard.Team;
import com.cpjd.roblu.utils.Constants;

/**
 * ImportEvent is used when the user has tapped a specific Event and more specific info
 * needs to be downloaded for that event (teams, matches, etc.), so let's do that!
 */
public class ImportEvent extends Thread {
    /**
     * The TBA key of the event to download specific data for
     */
    private String key;
    /**
     * The listener that will be notified when the event is successfully imported
     */
    private TBALoadEventsTask.LoadTBAEventsListener listener;

    public ImportEvent(TBALoadEventsTask.LoadTBAEventsListener listener, String key) {
        this.listener = listener;
        this.key = key;
    }

    @Override
    public void run() {
        /*
         * Make sure this thread has network permissions
         */
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);

        // Set auth token
        TBA.setAuthToken(Constants.PUBLIC_TBA_READ_KEY);

        // set what should be included in the event download
        //Settings.defaults();

        // notify the listener of the downloaded event
        Event e = new TBA().getEvent(this.key);
        Team[] teams = new TBA().getEventTeams(e.getKey());
        Match[] matches = new TBA().getMatches(e.getKey());

        if(e != null) listener.eventDownloaded(e, teams, matches);
        else listener.errorOccurred("No event found with key: "+this.key+".");

        try {
            join();
        } catch(InterruptedException error) {
            Log.d("RBS", "Failed to stop ImportEvent thread.");
        }
    }


}
