package com.cpjd.roblu.ui.forms;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.cpjd.roblu.R;
import com.cpjd.roblu.io.IO;
import com.cpjd.roblu.models.RForm;
import com.cpjd.roblu.models.metrics.RBoolean;
import com.cpjd.roblu.models.metrics.RCheckbox;
import com.cpjd.roblu.models.metrics.RChooser;
import com.cpjd.roblu.models.metrics.RCounter;
import com.cpjd.roblu.models.metrics.RDivider;
import com.cpjd.roblu.models.metrics.RGallery;
import com.cpjd.roblu.models.metrics.RMetric;
import com.cpjd.roblu.models.metrics.RSlider;
import com.cpjd.roblu.models.metrics.RStopwatch;
import com.cpjd.roblu.models.metrics.RTextfield;
import com.cpjd.roblu.ui.UIHandler;
import com.cpjd.roblu.ui.team.TeamViewer;
import com.cpjd.roblu.utils.Constants;
import com.cpjd.roblu.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Allows the user to selected a predefined form that they want to use.
 *
 * Predefined forms are loaded from /assets/predefinedForms.
 * Here's the file import requirements (this is what should be typed on each line, ALWAYS SPECIFY ALL PARAMETERS, INCLUDED DEFAULTS):
 *
 * -boolean,title,defaultValue
 * -counter,title,increment,defaultValue,
 * -slider,title,min,max,increment,defaultValue
 * -chooser,title,option1:option2:option3,defaultSelectedIndex
 * -checkbox,title,option1:option2:option3
 * -stopwatch,title,defaultValue
 * -textfield,title,defaultValue
 * -gallery,title
 * -DEFAULT (add team name and team number).
 *
 * Separate the file like this:
 * PIT
 * DEFAULTS
 * metric1...
 * metric2...
 * MATCH
 * metric1...
 * metric2...
 *
 * @version 2
 * @since 3.0.0
 * @author Will Davies
 */
public class PredefinedFormSelector extends AppCompatActivity implements OnItemClickListener {

	private String items[];

	private String sub_items[];

	private ArrayList<RForm> forms;

    /**
     * If true, any selected form will be launched into preview mode
     */
	private static boolean previewModeEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_predefined);

        /*
         * Setup UI
         */
        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Template forms");
            getSupportActionBar().setSubtitle("You can still edit predefined forms later");
        }

		/*
		 * Load and process predefined forms
		 */
		try {
		    forms = new ArrayList<>();
		    String[] files = getAssets().list("predefinedForms");
		    items = new String[files.length];
		    sub_items = new String[files.length];
		    for(int i = 0; i < files.length; i++) {
		        forms.add(processForm(i, files[i]));
            }

        } catch(IOException e) {
		    Log.d("RBS", "Unable to process predefined forms.");
        }

        // Bind predefined forms to the list
        ListView sharingView = findViewById(R.id.listView1);
        List<Map<String, String>> data = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            Map<String, String> datum = new HashMap<>(2);
            datum.put("item", items[i]);
            datum.put("description", sub_items[i]);
            data.add(datum);
        }
        SimpleAdapter adapter = new SimpleAdapter(this, data, android.R.layout.simple_list_item_2, new String[] { "item", "description" },
                new int[] { android.R.id.text1, android.R.id.text2 });
        sharingView.setAdapter(adapter);
        sharingView.setOnItemClickListener(this);


        // Sync UI with user settings
        new UIHandler(this, toolbar).update();
	}

    /**
     * Converst the form into an RForm reference
     * @param name the file name
     * @return an RForm instance
     */
	private RForm processForm(int index, String name) {
	    RForm form = new RForm(null, null);
        ArrayList<RMetric> metrics = new ArrayList<>();
	    try {
            AssetManager am = getAssets();
            InputStream is = am.open("predefinedForms"+ File.separator+name);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            int ID = 0;
            while((line = br.readLine()) != null) {
                if(line.startsWith("Title")) {
                    items[index] = line.split(":")[1];
                    continue;
                } else if(line.startsWith("Description")) {
                    sub_items[index] = line.split(":")[1];
                    continue;
                }

                switch(line) {
                    case "PIT":
                        continue;
                    case "MATCH":
                        form.setPit(new ArrayList<>(metrics));
                        metrics.clear();
                        continue;
                    case "DEFAULTS":
                        metrics.add(new RTextfield(0, "Team name", false, true, ""));
                        metrics.add(new RTextfield(1, "Team number", true, true, ""));
                        ID = 2;
                        break;
                }

                /*
                 * Process file
                 */
                String regex = "(?<!\\\\)" + Pattern.quote(",");
                String[] tokens = line.split(regex);
                for(int i = 0; i < tokens.length; i++) {
                    tokens[i] = tokens[i].replaceAll("\\\\,", ",");
                }

                switch(tokens[0]) {
                    case "counter":
                        metrics.add(new RCounter(ID, tokens[1], Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3])));
                        ID++;
                        break;
                    case "divider":
                        metrics.add(new RDivider(ID, tokens[1]));
                        ID++;
                        break;
                    case "chooser":
                        metrics.add(new RChooser(ID, tokens[1], tokens[2].split(":"), Integer.parseInt(tokens[3])));
                        ID++;
                        break;
                    case "slider":
                        metrics.add(new RSlider(ID, tokens[1], Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[4])));
                        ID++;
                        break;
                    case "checkbox":
                        LinkedHashMap<String, Boolean> temp = new LinkedHashMap<>();
                        for(String s : tokens[2].split(":")) temp.put(s, false);
                        metrics.add(new RCheckbox(ID, tokens[1], temp));
                        ID++;
                        break;
                    case "textfield":
                        metrics.add(new RTextfield(ID, tokens[1], ""));
                        ID++;
                        break;
                    case "stopwatch":
                        metrics.add(new RStopwatch(ID, tokens[1], Double.parseDouble(tokens[2])));
                        ID++;
                        break;
                    case "boolean":
                        metrics.add(new RBoolean(ID, tokens[1], Boolean.parseBoolean(tokens[2])));
                        ID++;
                        break;
                    case "gallery":
                        metrics.add(new RGallery(ID, tokens[1]));
                        ID++;
                        break;
                }
            }
            form.setMatch(metrics);
            Log.d("RBS", "Form created successfully with "+form.getPit().size()+" pit metrics and "+form.getMatch().size()+" match metrics");
            return form;

        } catch(IOException e) {
	        Log.d("RBS", "Failed to process form: "+e.getMessage());
            return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            setResult(Constants.CANCELLED);
            finish();
            return true;
        }
        if(item.getItemId() == R.id.preview) {
            previewModeEnabled = !previewModeEnabled;
            if(previewModeEnabled) Utils.showSnackbar(findViewById(R.id.activity_predefined_layout), getApplicationContext(), "Preview mode enabled", false, new IO(getApplicationContext()).loadSettings().getRui().getPrimaryColor());
            else Utils.showSnackbar(findViewById(R.id.activity_predefined_layout), getApplicationContext(), "Preview mode disabled", false, new IO(getApplicationContext()).loadSettings().getRui().getPrimaryColor());
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        setResult(Constants.CANCELLED);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.predefined, menu);
        new UIHandler(this, menu).updateMenu();
        return true;
    }

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        RForm form = forms.get(position);
        if(form == null) return;

        if(previewModeEnabled) {
            new IO(getApplicationContext()).createPreview(form);
            Intent intent = new Intent(this, TeamViewer.class);
            intent.putExtra("teamID", -1);
            intent.putExtra("eventID", -1);
            intent.putExtra("editable", false);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent();
        intent.putExtra("form", form);
        setResult(Constants.PREDEFINED_FORM_SELECTED, intent);
        finish();
	}
}
