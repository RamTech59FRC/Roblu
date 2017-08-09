package com.cpjd.roblu.models;

import com.cpjd.roblu.forms.elements.Element;
import com.cpjd.roblu.utils.Text;

import java.io.Serializable;
import java.util.ArrayList;

import lombok.Data;

/**
 * This class stores information about either PIT, Prediction, or Match data.
 *
 * @since 3.5.0
 * @author Will Davies
 */
@Data
public class RTab implements Serializable, Comparable<RTab> {

    /**
     * RTabs are identified by their title, no duplicate titles!
     */
    private String title;
    private ArrayList<Element> elements;
    private ArrayList<Long> editTimes;
    private boolean redAlliance;
    private boolean won;
    private long time;

    private transient ArrayList<RTeam> teammates;
    private transient ArrayList<RTeam> opponents;

    private ArrayList<String> editors;

    /**
     * This boolean will be flagged to true if this tab
     * was modified and needs to be pushed to the server
     */
    private boolean modified;

    // jackson requires an empty constructor
    public RTab() {}

    public RTab(ArrayList<Element> elements, String title, boolean redAlliance, boolean won, long time) {
        this.elements = elements;
        this.title = title;
        this.redAlliance = redAlliance;
        this.won = won;
        this.time = time;
    }

    public RTab duplicate() {
        return new RTab(Text.createNew(elements), title, redAlliance, won, time);
    }

    @Override
    public int compareTo(RTab tab) {
        return ((Long)Text.getMatchScore(title)).compareTo(Text.getMatchScore(tab.getTitle()));
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }
}
