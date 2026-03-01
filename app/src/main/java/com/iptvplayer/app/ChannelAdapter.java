package com.iptvplayer.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private String textFilter = "";
    private String groupFilter = "ALL"; // ALL, TV, RADIO, FILM

    public ChannelAdapter(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setChannels(List<Channel> channels) {
        this.allChannels = new ArrayList<>(channels);
        reapplyFilters();
    }

    public void setActiveIndex(int idx) {
        this.activeIndex = idx;
        notifyDataSetChanged();
    }

    /** Filter berdasarkan kategori group */
    public void applyGroupFilter(String category) {
        this.groupFilter = category;
        reapplyFilters();
    }

    /** Filter berdasarkan teks pencarian */
    public void applyFilter(String query) {
        this.textFilter = query == null ? "" : query.toLowerCase().trim();
        reapplyFilters();
    }

    private void reapplyFilters() {
        filteredChannels.clear();
        for (Channel ch : allChannels) {
            if (!matchesGroupFilter(ch)) continue;
            if (!textFilter.isEmpty()) {
                if (!ch.name.toLowerCase().contains(textFilter) &&
                    !ch.group.toLowerCase().contains(textFilter)) continue;
            }
            filteredChannels.add(ch);
        }
        notifyDataSetChanged();
    }

    private boolean matchesGroupFilter(Channel ch) {
        if ("ALL".equals(groupFilter)) return true;
        String g = ch.group != null ? ch.group.toLowerCase() : "";
        switch (groupFilter) {
            case "TV":    return g.contains("tv") || g.contains("nasional") || g.contains("berita") || g.contains("siaran");
            case "RADIO": return g.contains("radio") || g.contains("fm");
            case "FILM":  return g.contains("film") || g.contains("movie") || g.contains("video");
            default:      return true;
        }
    }

    public int getRealIndex(int filteredPos) {
        if (filteredPos < 0 || filteredPos >= filteredChannels.size()) return -1;
        Channel target = filteredChannels.get(filteredPos);
        return allChannels.indexOf(target);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ch_panel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Channel ch = filteredChannels.get(pos);
        int realIdx = getRealIndex(pos);
        boolean isActive = (realIdx == activeIndex);

        // Nomor + nama
        h.tvNum.setText(realIdx >= 0 ? String.valueOf(realIdx + 1) : "");
        h.tvNum.setTextColor(isActive ? 0xFF000000 : 0x80FFFFFF);
        h.tvName.setText(ch.name);
        h.tvName.setTextColor(isActive ? 0xFF000000 : 0xFFFFFFFF);
        h.tvEpg.setText("Tidak ada informasi");
        h.tvEpg.setTextColor(isActive ? 0x80000000 : 0x80FFFFFF);

        // Logo
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            h.ivLogo.setVisibility(View.VISIBLE);
            h.tvFallback.setVisibility(View.GONE);
            Glide.with(h.ivLogo.getContext())
                    .load(ch.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error((android.graphics.drawable.Drawable) null)
                    .into(h.ivLogo);
        } else {
            h.ivLogo.setVisibility(View.GONE);
            h.tvFallback.setVisibility(View.VISIBLE);
            String initials = ch.name.isEmpty() ? "?" : ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase();
            h.tvFallback.setText(initials);
            h.tvFallback.setTextColor(isActive ? 0xFF000000 : 0x66FFFFFF);
        }

        // Background: putih saat active (dipilih), transparan lainnya
        if (isActive) {
            h.itemBg.setVisibility(View.VISIBLE);
            // Tampilkan waveform indikator, play arrow tersembunyi
            h.tvWaveform.setVisibility(View.VISIBLE);
            h.ivPlayArrow.setVisibility(View.GONE);
        } else {
            h.itemBg.setVisibility(View.INVISIBLE);
            h.tvWaveform.setVisibility(View.GONE);
            h.ivPlayArrow.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChannelClick(realIdx);
        });
    }

    @Override
    public int getItemCount() { return filteredChannels.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View itemBg;
        TextView tvNum, tvName, tvEpg, tvFallback, tvWaveform;
        ImageView ivLogo, ivPlayArrow;

        VH(View v) {
            super(v);
            itemBg      = v.findViewById(R.id.item_bg);
            tvNum       = v.findViewById(R.id.tv_num);
            tvName      = v.findViewById(R.id.tv_ch_name);
            tvEpg       = v.findViewById(R.id.tv_ch_epg);
            ivLogo      = v.findViewById(R.id.iv_logo);
            tvFallback  = v.findViewById(R.id.tv_logo_fallback);
            ivPlayArrow = v.findViewById(R.id.iv_play_arrow);
            tvWaveform  = v.findViewById(R.id.tv_waveform);
        }
    }
}
