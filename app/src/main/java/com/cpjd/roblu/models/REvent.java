package com.cpjd.roblu.models;

import android.support.annotation.NonNull;

import java.io.Serializable;

import lombok.Data;

/**
 * The model for an REvent, stores some general information about an event.
 *
 * @since 3.0.0
 * @author Will Davies
 */
@Data
public class REvent implements Serializable, Comparable<REvent> {

    /**
     * Changing this versionUID will render this class incompatible with older versions.
     */
    public static final long serialVersionUID = 1L;
    /**
     * The event's ID, this is what differentiates duplicate events and is
     * used for saving the event to the file system
     */
    private int ID;

    /**
     * The name of the REvent
     */
    private String name;

    /**
     * This is the TBA key of the event, used for automatically syncing stuff
     */
    private String key;
    /**
     * The last filter that was used to sort this team, so when the user relaunches
     * the app, they're still sorting in the most recently used sort scheme. This sorting
     * filter applies to the team list
     */
    private int lastFilter;
    /**
     * Whether this event is currently being managed by Roblu Cloud
     */
    private boolean cloudEnabled;

    /**
     * True if Bluetooth syncs should be directed to this event
     */
    private boolean bluetoothEnabled;

    /**
     * If non -1, this event is being synced with read only mode and therefore write access
     * should not be assumed
     */
    private int readOnlyTeamNumber;

    public REvent(int ID, String name) {
        this.ID = ID;
        this.name = name;
        this.readOnlyTeamNumber = -1;
    }

    @Override
    public int compareTo(@NonNull REvent event) {
        return ((Integer)ID).compareTo(event.getID());
    }
}
