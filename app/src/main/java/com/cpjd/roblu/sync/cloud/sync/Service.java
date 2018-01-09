package com.cpjd.roblu.sync.cloud.sync;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.cpjd.roblu.sync.cloud.api.CloudRequest;
import com.cpjd.roblu.models.RCheckout;
import com.cpjd.roblu.models.REvent;
import com.cpjd.roblu.models.RForm;
import com.cpjd.roblu.models.RSettings;
import com.cpjd.roblu.models.RTeam;
import com.cpjd.roblu.models.RUI;
import com.cpjd.roblu.notifications.Notify;
import com.cpjd.roblu.utils.Utils;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the background service for the Roblu Cloud API.
 *
 * It performs the following:
 *
 * -Checking for RCheckouts in the InCheckouts database
 * -Pushing any changes to the form
 * -Pushing checkouts after they've been merged (either automatically or explicity)
 *
 * Requests are added to a stack, this service will continually attempt to upload them
 * until it receives a successful response.
 * -Items that are explicitly merged will be flagged with 'requestedExplicitMerge', this service will detect that and attempt to upload it
 * from (/checkoutconflicts)
 * -When we pull items, we'll instantly re-upload them if they auto merge. When we re-upload them, THATS when we delete them
 *
 * Service timing:
 * -Service will start on device startup
 * -Service will run through the stack once every 15 seconds when unopened, once every 5 seconds when open
 *
 * Service can receive new jobs from the main app thread.
 *
 * Service should check for a connection before attempting to upload something
 *
 * @since 3.6.1
 * @author Will Davies
 *
 */

public class Service extends android.app.Service {

    private ServiceHandler handler;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("Roblu Service", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper serviceLooper = thread.getLooper();
        handler = new ServiceHandler(serviceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("RBS", "Service started at "+ Utils.convertTime(System.currentTimeMillis()));
        try {
            Message message = handler.obtainMessage();
            message.arg1 = startId;
            handler.sendMessage(message);
        } catch(Exception e) {
            Log.d("RBS", "Failed to start service.");
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class ServiceHandler extends Handler {

        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg == null) return;
            ObjectMapper mapper = new ObjectMapper().configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Loader l = new Loader(getApplicationContext());
            CloudRequest cr;

            while(true) {
                // sleep a bit
                try {
                    if(isAppOnForeground(getApplicationContext())) { // detect if app is in foreground, if yes, sleep for a little less
                        Log.d("RBS", "Sleeping for 5 seconds...");
                        Thread.sleep(5000);
                    }
                    else {
                       Log.d("RBS", "Sleeping for 15 seconds...");
                        Thread.sleep(15000);
                    }
                } catch(Exception e) { /* Ignore */}

                // first, check if we have an internet connection, if we don't, the background service is useless
                if(!Utils.hasInternetConnection(getApplicationContext())) {
                    continue;
                }
                // create the object we use to access the server
                cr = new CloudRequest(l.loadSettings().getAuth(), l.loadSettings().getTeamCode(), Utils.getDeviceID(getApplicationContext()));

                // check if the UI needs to be uploaded
                RSettings settings = l.loadSettings();

                /*
                 * -DE SYNC-
                 * Check if we need to tell the server to stop syncing
                 * all the scouters because the master either deleted a synced event,
                 * or tapped stop syncing
                 *
                 */
                if(settings.isClearActiveRequested()) {
                    try {
                        Log.d("RBS", cr.clearActiveEvent().toString());
                    } catch(Exception e) { /* Ignore */ }
                    settings.setClearActiveRequested(false);
                    l.saveSettings(settings);
                }

                /*
                 *-UI-
                 * Check if the UI was modified and needs to be uploaded to all the scouters
                 */
                RUI rui = settings.getRui();
                if(rui != null && rui.isModified()) {
                    try {
                       // Log.d("RBS", "UI was modified, pushing changes...");
                        cr.pushUI(mapper.writeValueAsString(rui));
                        rui.setModified(false);
                        l.saveSettings(settings);
                        Log.d("RBS", "Form synced");
                    } catch(Exception e) {
                        Log.d("RBS", "Error pushing UI to the server.");
                    }
                }

                /*
                 * Find the active event from local events and obtain a reference to it
                 */
                REvent[] events = l.getEvents();
                REvent activeEvent = null;
                for(int i = 0; events != null && events.length > 0 && i < events.length; i++) {
                    if(events[i].isCloudEnabled()) {
                        activeEvent = events[i];
                        break;
                    }
                }
                if(activeEvent == null) continue;
                /*
                 * -FORM-
                 * Check if the form was modified and needs to be uploaded to the scouters
                 */
                RForm form = l.loadForm(activeEvent.getID());
                if(form != null  && form.isModified()) {
                    try {
                        Log.d("RBS", "Form was modified, pushing changes...");
                        cr.pushForm(mapper.writeValueAsString(form));
                        form.setModified(false);
                        l.saveForm(form, activeEvent.getID());
                        Log.d("RBS", "Form synced.");
                    } catch(Exception e) {
                        Log.d("RBS", "Error pushing form to the server.");
                    }
                }

                /*
                 * -ReceivedCheckouts-
                 * Check to see if any scouters completed scouting data and
                 * uploaded it to the server, if so, download it and either merge
                 * automatically or save to merge conflicts
                 */
                try {
                    int auto = 0;
                    int conflicts = 0;

                    Log.d("RBS", "Checking for ReceivedCheckouts...");
                    JSONArray checkouts = (JSONArray) ((JSONObject)cr.pullCheckouts()).get("data");
                    for(int i = 0; i < checkouts.size(); i++) {
                        JSONObject object = (JSONObject) checkouts.get(i);
                        RCheckout checkout = mapper.readValue(object.get("content").toString(), RCheckout.class);
                        checkout.setStatus(checkout.getStatus().replace("\nWaiting to upload", ""));
                        checkout.setSyncRequired(false);

                        /*
                         * We need to check for conflicts (does a team already exist that's been edited, or does the team not exist)
                         */
                        RTeam temp = l.loadTeam(activeEvent.getID(), checkout.getTeam().getID());
                        if(temp == null) { // if we don't have a local team matching the downloaded team, set conflict type not-found locally
                            checkout.setConflictType("not-found");
                            l.saveCheckoutConflict(checkout);
                            conflicts++;
                        }
                        else if(temp.getLastEdit() > 0) { // if the local team is already edited, set conflict type to edited locally already
                            checkout.setConflictType("edited");
                            l.saveCheckoutConflict(checkout);
                            conflicts++;
                        }

                        // If there are no conflicts, merge the checkout automatically with the local master copy
                        if(checkout.getConflictType() == null || checkout.getConflictType().equals("")) {
                            if(temp != null) {
                                for(int j = 0; j < temp.getTabs().size(); j++) {
                                    if(temp.getTabs().get(j).getTitle().equals(checkout.getTeam().getTabs().get(0).getTitle())) {
                                        for(int k = 0; k < checkout.getTeam().getTabs().size(); k++) { // set the correct tabs to the received data, set editors and edit times
                                            temp.getTabs().set(j + k, checkout.getTeam().getTabs().get(k));
                                            if(temp.getTabs().get(j + k).getEditors() == null) {
                                                temp.getTabs().get(j + k).setEditors(new ArrayList<String>());
                                                temp.getTabs().get(j + k).setEditTimes(new ArrayList<Long>());
                                            }
                                            temp.getTabs().get(j + k).getEditors().add(checkout.getStatus().replace("Completed by", ""));
                                            Log.d("RBS", "Received checkout with compleltion time: "+checkout.getCompletedTime());
                                            temp.getTabs().get(j + k).getEditTimes().add(checkout.getCompletedTime());

                                        }
                                        temp.updateEdit();
                                        l.saveTeam(temp, activeEvent.getID());
                                        // save the checkout in the merge history
                                        checkout.setMergedTime(System.currentTimeMillis());
                                        checkout.setSyncRequired(true); // update the master checkouts repo
                                        checkout.setHistoryID(new Loader(getApplicationContext()).getNewCheckoutID());
                                        new Loader(getApplicationContext()).saveCheckout(checkout);
                                        auto++;
                                        break;
                                    } else if(j == temp.getTabs().size() - 1) { // we didn't find the tab, add a new one
                                        temp.addTab(checkout.getTeam().getTabs().get(0));
                                        if(temp.getTabs().get(j).getEditors() == null) {
                                            temp.getTabs().get(j).setEditors(new ArrayList<String>());
                                            temp.getTabs().get(j).setEditTimes(new ArrayList<Long>());
                                        }
                                        temp.getTabs().get(j).getEditors().add(checkout.getStatus().replace("Completed by", ""));
                                        temp.getTabs().get(j).getEditTimes().add(checkout.getCompletedTime());
                                    }

                                }
                            }
                        }
                    }
                    // notify the user here
                    if(auto > 0) {
                        Notify.notify(getApplicationContext(), "Merged "+auto+" checkouts.", checkouts.size()+" checkout(s) were automatically merged at "+ Utils.convertTimeOnly(System.currentTimeMillis()));
                        Intent broadcast = new Intent();
                        broadcast.setAction("com.cpjd.roblu.broadcast");
                        broadcast.putExtra("mode", 1);
                        sendBroadcast(broadcast);

                        // Updates the main list
                        Intent broadcast2 = new Intent();
                        broadcast2.setAction("com.cpjd.roblu.broadcast.main");
                        sendBroadcast(broadcast2);
                    }

                    if(conflicts > 0) {
                        Notify.notify(getApplicationContext(), conflicts+" checkouts could not be merged", conflicts+" conflicts could not be merged due to merge conflicts. Resolve them in Mailbox.");
                        Intent broadcast = new Intent();
                        broadcast.setAction("com.cpjd.roblu.broadcast");
                        broadcast.putExtra("mode", 0);
                        sendBroadcast(broadcast);
                    }
                } catch(Exception e) {
                    Log.d("RBS", "Error checking for completed checkouts. Error: "+e.getMessage());
                }

                // Check if there are any checkouts (explicitly or automatically merged) that need to be uploaded
                Log.d("RBS", "Checking for any checkouts to upload...");
                try {
                    ArrayList<RCheckout> toUpload = new ArrayList<>();
                    RCheckout[] checkouts = l.loadCheckouts();
                    for(RCheckout checkout : checkouts) {
                        if(checkout.isSyncRequired()) {
                            toUpload.add(checkout);
                        }

                    }
                    if(toUpload.size() > 0) {
                        String json = mapper.writeValueAsString(toUpload);
                        JSONObject object = (JSONObject) cr.pushCheckouts(json);
                        if(object.get("status").equals("success")) {
                            for(RCheckout checkout : toUpload) {
                                checkout.setSyncRequired(false);
                                l.saveCheckout(checkout);
                            }
                        }
                        Log.d("RBS", "Uploaded "+toUpload.size()+" checkouts.");
                    }
                } catch(Exception e) {
                    Log.d("RBS", "Failed to upload checkouts. "+e.getMessage());
                }
            }
        }

        // returns true if the user has the app open (not just in the recent apps drawer, but currently being drawed to the screen)
        private boolean isAppOnForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null) {
                return false;
            }
            final String packageName = context.getPackageName();
            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }

    }
}