/*
 * ******************************************************
 *  * Copyright (C) 2016 Will Davies wdavies973@gmail.com
 *  *
 *  * This file is part of Roblu
 *  *
 *  * Roblu cannot be distributed for a price or to people outside of your local robotics team.
 *  ******************************************************
 */

package com.cpjd.roblu.ui.events;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import com.cpjd.http.Request;
import com.cpjd.requests.CloudTeamRequest;
import com.cpjd.roblu.BuildConfig;
import com.cpjd.roblu.R;
import com.cpjd.roblu.csv.ExportCSVTask;
import com.cpjd.roblu.io.IO;
import com.cpjd.roblu.models.RBackup;
import com.cpjd.roblu.models.REvent;
import com.cpjd.roblu.models.RForm;
import com.cpjd.roblu.models.RSettings;
import com.cpjd.roblu.models.RTeam;
import com.cpjd.roblu.models.RUI;
import com.cpjd.roblu.sync.cloud.InitPacker;
import com.cpjd.roblu.ui.UIHandler;
import com.cpjd.roblu.ui.dialogs.FastDialogBuilder;
import com.cpjd.roblu.ui.forms.FormViewer;
import com.cpjd.roblu.ui.settings.customPreferences.RUICheckPreference;
import com.cpjd.roblu.ui.teams.TeamsView;
import com.cpjd.roblu.utils.Constants;
import com.cpjd.roblu.utils.Utils;
import com.google.common.io.Files;

import java.io.File;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * EventSettings manages event specific settings
 *
 * @version 2
 * @since 3.0.0
 * @author Will Davies
 */
public class EventSettings extends AppCompatActivity {
    /**
     * The REvent reference whose settings are being modified
     */
    private static REvent event;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_settings);

        /*
         * Receive parameters
         */
        event = (REvent) getIntent().getSerializableExtra("event");

        /*
         * Setup UI
         */
        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(event.getName());
            getSupportActionBar().setSubtitle("Event settings");
        }

        // Bind event to the UI
        getFragmentManager().beginTransaction()
                .replace(R.id.blankFragment, new SettingsFragment())
                .commit();

        // Bind general user UI preferences
        new UIHandler(this, toolbar).update();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            Intent intent = new Intent();
            intent.putExtra("event", event);
            setResult(Constants.EVENT_SETTINGS_CHANGED, intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra("event", event);
        setResult(Constants.EVENT_SETTINGS_CHANGED, intent);
        finish();
    }

    public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener, ExportCSVTask.ExportCSVListener {

        /**
         * Store an RUI reference here to give all dialogs below access to user color preferences
         */
        private RUI rui;

        /**
         * Helper variable for the Backup task
         */
        private File backupFile;

        private RUICheckPreference cloud;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            rui = new IO(getActivity()).loadSettings().getRui();

            /*
             * Setup UI
             */
            addPreferencesFromResource(R.xml.event_preferences);

            /*
             * Set appropriate preference listeners to the preferences
             */
            findPreference("edit_form").setOnPreferenceClickListener(this);
            findPreference("export_csv").setOnPreferenceClickListener(this);
            findPreference("backup").setOnPreferenceClickListener(this);
            findPreference("duplicate").setOnPreferenceClickListener(this);
            findPreference("edit_event").setOnPreferenceClickListener(this);
            findPreference("delete_teams").setOnPreferenceClickListener(this);
            findPreference("delete_event").setOnPreferenceClickListener(this);

            /*
             * Obtain explicit preference references where an attribute of the preference
             * needs to be programmatically set
             */
            Preference teams = findPreference("delete_teams");
            Preference deleteEvent = findPreference("delete_event");
            cloud = (RUICheckPreference) findPreference("sync");
            cloud.setChecked(event.isCloudEnabled());
            cloud.setOnPreferenceChangeListener(this);

            /*
             * Set the info to the UI that needs to be
             */

            teams.setSummary("Delete "+new IO(getActivity()).getNumberTeams(event.getID())+" teams");
            deleteEvent.setSummary("Delete ["+event.getName()+"] event");

        }

        /**
         * The user tapped on one of the available preferences
         * @param preference the preference that was tapped
         * @return true if the event was consumed
         */
        @Override
        public boolean onPreferenceClick(final Preference preference) {
            /*
             * User clicked on the "Edit form" preference.
             * Keep in mind, we'll need to listen to the FormViewer activity's return
             */
            if(preference.getKey().equals("edit_form")) {
                Intent intent = new Intent(getActivity(), FormViewer.class);
                intent.putExtra("form", new IO(getActivity()).loadForm(event.getID()));
                intent.putExtra("editing", true); // editing will be true
                startActivityForResult(intent, Constants.GENERAL);
            }
            /*
             * User clicked the "Edit event info" preference.
             * Keep in mind, we'll need to listen to the EventEditor for changes
             */
            else if(preference.getKey().equals("edit_event")) {
                Intent intent = new Intent(getActivity(), EventEditor.class);
                intent.putExtra("editing", true);
                intent.putExtra("name", event.getName());
                intent.putExtra("key", event.getKey());
                startActivityForResult(intent, Constants.GENERAL);
            }
            /*
             * User clicked on "Duplicate event"
             */
            else if(preference.getKey().equals("duplicate")) {
                new FastDialogBuilder()
                        .setTitle("Keep scouting data?")
                        .setMessage("Would you like to copy scouting data to the duplicate event?")
                        .setPositiveButtonText("Yes")
                        .setNegativeButtonText("No")
                        .setNeutralButtonText("Cancel")
                        .setFastDialogListener(new FastDialogBuilder.FastDialogListener() {
                            @Override
                            public void accepted() {
                                Intent intent = new Intent();
                                intent.putExtra("eventID", new IO(getActivity()).duplicateEvent(event, true).getID());
                                getActivity().setResult(Constants.NEW_EVENT_CREATED);
                                Toast.makeText(getActivity(), "Duplicate event created", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void denied() {
                                Intent intent = new Intent();
                                intent.putExtra("eventID", new IO(getActivity()).duplicateEvent(event, false).getID());
                                getActivity().setResult(Constants.NEW_EVENT_CREATED);
                                Toast.makeText(getActivity(), "Duplicate event created", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void neutral() {

                            }
                        }).build(getActivity());
            }
            /*
             * User clicked on "Backup event"
             */
            else if(preference.getKey().equals("backup")){
                if (EasyPermissions.hasPermissions(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // Star the task
                    new BackupEventTask(new IO(getActivity()), new BackupEventTask.BackupEventTaskListener() {
                        @Override
                        public void eventBackupComplete(File backup) {
                            backupFile = backup;

                            // We have an internal file now, let's ask the user for where they want to put it in a location they can see it
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                    intent.setType("application/*");
                                    intent.putExtra(Intent.EXTRA_TITLE, backupFile.getName());
                                    startActivityForResult(intent, Constants.FILE_CHOOSER);
                                } else Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Your phone doesn't support backup exporting", true, 0);
                            } catch(Exception e) {
                                Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "No file manager found", true, 0);
                            }
                        }
                    }).execute();
                } else {
                    Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Storage permission is disabled. Please enable it.", true, rui.getPrimaryColor());
                }
            }
            /*
             * User clicked on "Delete teams"
             */
            else if (preference.getKey().equals("delete_teams")) {
                new FastDialogBuilder()
                        .setTitle("Delete all teams?")
                        .setMessage("Are you sure you want to delete ALL team profiles within this event? Cannot be undone!")
                        .setPositiveButtonText("Delete")
                        .setNegativeButtonText("Cancel")
                        .setFastDialogListener(new FastDialogBuilder.FastDialogListener() {
                            @Override
                            public void accepted() {
                                new IO(getActivity()).deleteAllTeams(event.getID());
                                preference.setSummary("Delete 0 teams");
                                Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Teams successfully deleted", false, rui.getPrimaryColor());
                            }

                            @Override
                            public void denied() {}

                            @Override
                            public void neutral() {}
                        }).build(getActivity());
            }
            /*
             * User clicked on "Delete event"
             */
            else if (preference.getKey().equals("delete_event")) {
                new FastDialogBuilder()
                        .setTitle("Warning! Delete event?")
                        .setMessage("Are you sure you want to delete the event? This CANNOT be undone!!")
                        .setPositiveButtonText("Delete")
                        .setNegativeButtonText("Cancel")
                        .setFastDialogListener(new FastDialogBuilder.FastDialogListener() {
                            @Override
                            public void accepted() {
                                IO io = new IO(getActivity());
                                //TODO make sure syncing is force quit here
                                io.clearCheckouts();
                                io.deleteEvent(event.getID());

                                if(event.isCloudEnabled()) {
                                    RSettings settings = new IO(getActivity()).loadSettings();
                                    settings.setPurgeRequested(true);
                                    new IO(getActivity()).saveSettings(settings);
                                    new IO(getActivity()).clearCheckouts();
                                }

                                startActivity(new Intent(getActivity(), TeamsView.class));
                                Toast.makeText(getActivity(), "Event successfully deleted", Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void denied() {}

                            @Override
                            public void neutral() {}
                        }).build(getActivity());
            }
            /*
             * User clicked on "Export to .csv"
             */
            else if (preference.getKey().equals("export_csv")) {
                ProgressDialog d = ProgressDialog.show(getActivity(), "Freeze!", "Roblu is generating an spreadsheet file...", true);
                d.setCancelable(false);
                d.show();

                new ExportCSVTask(getActivity(), this, d, event).execute();

            }
            return true;
        }

        /**
         * This method is called whenever a preference is changed and has a value that needs to be saved
         * @param preference the preference whose value was changed
         * @param o the new value that the user just set
         * @return true if the event was consumed
         */
        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            /*
             * User selected the cloud sync button
             */
            if(preference.getKey().equals("sync")) {
                if((boolean)o) {
                    /*
                     * Check if an error message should be shown
                     */
                    RSettings settings = new IO(getActivity()).loadSettings();
                    Request r = new Request(settings.getServerIP());
                    CloudTeamRequest ctr = new CloudTeamRequest(r, settings.getCode());
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
                    StrictMode.setThreadPolicy(policy);

                    if(ctr.isActive()) {
                        new FastDialogBuilder()
                                .setTitle("Warning")
                                .setMessage("It looks like you already have some scouting data on the server. Do you want to overwrite this data? If you still have" +
                                        " a local copy of scouting data on a Roblu Master app, you can safely overwrite cloud data.")
                                .setPositiveButtonText("Overwrite")
                                .setNegativeButtonText("Cancel")
                                .setFastDialogListener(new FastDialogBuilder.FastDialogListener() {
                                    @Override
                                    public void accepted() {
                                        uploadEvent();
                                    }

                                    @Override
                                    public void denied() {

                                    }

                                    @Override
                                    public void neutral() {

                                    }
                                }).build(getActivity());

                    } else uploadEvent();
                } else {
                    event.setCloudEnabled(false);
                    new IO(getActivity()).saveEvent(event);
                    RSettings settings = new IO(getActivity()).loadSettings();
                    settings.setPurgeRequested(true);
                    new IO(getActivity()).saveSettings(settings);
                    new IO(getActivity()).clearCheckouts();
                }
            }
            return true;
        }

        /**
         * Uploads the active event to the server
         */
        private void uploadEvent() {
            ProgressDialog pd = ProgressDialog.show(getActivity(), "Fasten your seatbelt!", "Launching packets into Roblu Cloud orbit...", false);
            pd.setCancelable(false);
            pd.show();
            InitPacker ip = new InitPacker(pd, cloud, new IO(getActivity()), event.getID());
            ip.setListener(new InitPacker.StatusListener() {
                @Override
                public void statusUpdate(String message) {
                    Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), message, false, rui.getPrimaryColor());
                }
            });
            ip.execute();
        }

        /**
         * Receives various data from any activities started from this class
         * @param requestCode the request code the child activities were started with
         * @param resultCode the result code form child activites
         * @param data any data included with the result
         */
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            /*
             * Received after the user selected a backup file location and data needs to be
             * copied to it
             */
            if(requestCode == Constants.FILE_CHOOSER) {
                try {
                    /*
                     * This will copy the internal backup file to the external location the user selected
                     * (internal is app data, external is a location the user can see, but still on internal storage for the device)
                     */
                    OutputStream os = getActivity().getContentResolver().openOutputStream(data.getData());
                    if(os != null) {
                        Files.copy(backupFile, os);
                        os.flush();
                        os.close();
                        Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Successfully created backup file", false, rui.getPrimaryColor());
                    } else Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Error occurred while creating backup", true, 0);
                } catch(Exception e) {
                    Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Error occurred while creating backup", true, 0);
                }
            }
            /*
             * Called when event info was edited
             */
            else if(resultCode == Constants.EVENT_INFO_EDITED) {
                event.setName(data.getStringExtra("name"));
                event.setKey(data.getStringExtra("key"));
                new IO(getActivity()).saveEvent(event);
                if(((AppCompatActivity)getActivity()).getSupportActionBar() != null) {
                    ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(event.getName());
                }
            }
            /*
             * Called when the user made changes to the form
             */
            else if(resultCode == Constants.FORM_CONFIRMED) {
                Bundle bundle = data.getExtras();
                RForm form = (RForm)bundle.getSerializable("form");
                form.setUploadRequired(true);
                new IO(getActivity()).saveForm(event.getID(), form);
            }
        }

        @Override
        public void errorOccurred(String message) {
            Utils.showSnackbar(getActivity().findViewById(R.id.event_settings), getActivity(), "Error occurred while generating .CSV file: "+message, true, 0);
        }

        @Override
        public void csvFileGenerated(File file) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            Uri uri = FileProvider.getUriForFile(getActivity(), BuildConfig.APPLICATION_ID, file);
            intent.setType("application/vnd.ms-excel");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            PackageManager pm = getActivity().getPackageManager();
            if(intent.resolveActivity(pm) != null) {
                getActivity().startActivity(Intent.createChooser(intent, "Export spreadsheet to..."));
            }
        }


        /**
         * BackupEvent backups up an REvent object into a internal file
         */
        public static class BackupEventTask extends AsyncTask<Void, Void, File> {

            private WeakReference<IO> ioWeakReference;

            interface BackupEventTaskListener {
                void eventBackupComplete(File backupFile);
            }

            /**
             * Notified with a backupFile object when the backup is complete
             */
            private BackupEventTaskListener listener;

            BackupEventTask(IO io, BackupEventTaskListener listener) {
                this.listener = listener;
                this.ioWeakReference = new WeakReference<>(io);
            }

            protected File doInBackground(Void... params) {
                RTeam[] teams = ioWeakReference.get().loadTeams(event.getID());
                RForm form = ioWeakReference.get().loadForm(event.getID());
                return ioWeakReference.get().saveBackup(new RBackup(event, teams, form));
            }

            protected void onPostExecute(File file) {
                listener.eventBackupComplete(file);
            }
        }
    }
}
