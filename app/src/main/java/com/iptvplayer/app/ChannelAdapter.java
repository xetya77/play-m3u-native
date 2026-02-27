package com.iptvplayer.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface OnChannelClickListener {
        void onChannelClick(int position);
    }

    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> filteredChannels = new ArrayList<>();
    private int activeIndex = -1;
    private OnChannelClickListener listener;
    private String filter = "";

    public ChannelAdapter(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setChannels(List<Channel> channels) {
        this.allChannels = new ArrayList<>(channels);
        applyFilter(filter);
    }

    public void setActiveIndex(int idx) {
        this.activeIndex = idx;
        notifyDataSetChanged();
    }

    public void applyFilter(String query) {
        this.filter = query == null ? "" : query.toLowerCase().trim();
        filteredChannels.clear();
        for (Channel ch : allChannels) {
            if (filter.isEmpty() ||
                    ch.name.toLowerCase().contains(filter) ||
                    ch.group.toLowerCase().contains(filter)) {
                filteredChannels.add(ch);
            }
        }
        notifyDataSetChanged();
    }

    /** Returns the real index in allChannels for a filtered position */
    public int getRealIndex(int filteredPos) {
        if (filteredPos < 0 || filteredPos >= filteredChannels.size()) return -1;
        Channel target = filteredChannels.get(filteredPos);
        return allChannels.indexOf(target);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ch_panel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Channel ch = filteredChannels.get(pos);
        int realIdx = getRealIndex(pos);

        h.tvNum.setText(String.valueOf(realIdx + 1));
        h.tvName.setText(ch.name);
        h.tvGroup.setText(ch.group);

        // Logo
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            h.ivLogo.setVisibility(View.VISIBLE);
            h.tvFallback.setVisibility(View.GONE);
            Glide.with(h.ivLogo.getContext())
                    .load(ch.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_ch_logo)
                    .error((android.graphics.drawable.Drawable) null)
                    .into(h.ivLogo);
        } else {
            h.ivLogo.setVisibility(View.GONE);
            h.tvFallback.setVisibility(View.VISIBLE);
            String initials = ch.name.isEmpty() ? "?" :
                    ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase();
            h.tvFallback.setText(initials);
        }

        // Active channel highlight
        boolean isActive = (realIdx == activeIndex);
        if (isActive) {
            h.itemView.setBackgroundResource(R.drawable.bg_ch_active);
        } else {
            h.itemView.setBackgroundResource(R.drawable.selector_ch_item);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChannelClick(realIdx);
        });
    }

    @Override
    public int getItemCount() {
        return filteredChannels.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNum, tvName, tvGroup, tvFallback;
        ImageView ivLogo;

        VH(View v) {
            super(v);
            tvNum = v.findViewById(R.id.tv_num);
            tvName = v.findViewById(R.id.tv_ch_name);
            tvGroup = v.findViewById(R.id.tv_ch_group);
            ivLogo = v.findViewById(R.id.iv_logo);
            tvFallback = v.findViewById(R.id.tv_logo_fallback);
        }
    }
}
