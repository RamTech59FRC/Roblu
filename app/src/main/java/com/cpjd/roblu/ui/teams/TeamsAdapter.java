package com.cpjd.roblu.ui.teams;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cpjd.roblu.R;
import com.cpjd.roblu.models.RTeam;
import com.cpjd.roblu.models.RUI;
import com.cpjd.roblu.utils.Utils;

import java.util.LinkedList;

class TeamsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final Context mContext;
    private LinkedList<RTeam> teams;
    private final TeamsItemClickListener listener;
    private RUI rui;

    public TeamsAdapter(Context context, TeamsItemClickListener listener){
        this.mContext = context; this.listener = listener;
        try {
            rui = new Loader(context).loadSettings().getRui();
        } catch(Exception e) {
            rui = null;
        }
    }

    public void reAdd(RTeam t) {
        for(int i = 0; i < teams.size(); i++) {
            if(teams.get(i) == null) continue;

            if(teams.get(i).getID() == t.getID()) {
                teams.remove(i);
                teams.add(i, t);
                break;
            }
        }
        notifyDataSetChanged();
    }

    void removeAll() {
        if(teams == null) return;
        this.teams.clear();
        notifyDataSetChanged();
    }

    public RTeam getTeam(int position) {
        return teams.get(position);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.teams_item, parent, false);
        if(rui != null) view.setBackgroundColor(rui.getCardColor());
        final MyViewHolder holder = new MyViewHolder(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(listener != null) listener.onItemClick(v);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        MyViewHolder myHolder = (MyViewHolder)holder;
        myHolder.bindMovie(teams.get(position));
    }

    public void setElements(LinkedList<RTeam> teams) {
        if(this.teams == null) this.teams = new LinkedList<>();
        if(teams == null || teams.size() == 0) return;
        this.teams.addAll(teams);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if(teams == null) teams = new LinkedList<>();
        return teams.size();
    }

    public void remove(final int position) {
        RTeam team = teams.remove(position);
        notifyItemRemoved(position);
        listener.deleteTeam(team);
    }

    public Context getContext() {
        return mContext;
    }

    private class MyViewHolder extends RecyclerView.ViewHolder{
        public final TextView title;
        public final TextView number;
        public final TextView subtitle;

        private MyViewHolder(View view){
            super(view);

            title = (TextView) view.findViewById(R.id.title);
            subtitle = (TextView) view.findViewById(R.id.subtitle);
            number = (TextView) view.findViewById(R.id.number);
        }

        private void bindMovie(RTeam team){
            this.title.setText(team.getName());
            if(rui != null) this.title.setTextColor(rui.getText());
            this.title.setMaxWidth((int)(Utils.getWidth()* 0.85));
            String text = "#"+String.valueOf(team.getNumber());
            this.number.setText(text);
            if(rui != null) this.number.setTextColor(rui.getText());
            String subtitle = "In "+team.getNumMatches()+" matches\nLast edited: "+ Utils.convertTime(team.getLastEdit());
            if(team.getSortTip() != null && !team.getSortTip().equals("")) subtitle +="\n\n"+team.getSortTip();
            if(team.getSearchTip() != null && !team.getSearchTip().equals("")) subtitle +="\n\n"+team.getSearchTip();

            this.subtitle.setText(subtitle);
            if(rui != null) this.subtitle.setTextColor(rui.getText());
        }
    }

}
