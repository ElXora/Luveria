package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.GraphicsPresetManager;

/**
 * A 3-segment choice bar rendered inside a Preference row.
 * HIGH ─── MID ─── LOW
 */
public class GraphicsPresetPreference extends Preference {

    public GraphicsPresetPreference(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setLayoutResource(R.layout.pref_graphics_preset);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false); // clicks handled by children

        View btnHigh = holder.itemView.findViewById(R.id.preset_btn_high);
        View btnMid  = holder.itemView.findViewById(R.id.preset_btn_mid);
        View btnLow  = holder.itemView.findViewById(R.id.preset_btn_low);

        String current = LauncherPreferences.DEFAULT_PREF
                .getString("graphics_preset", GraphicsPresetManager.PRESET_MID);

        updateSelection(holder.itemView, current);

        btnHigh.setOnClickListener(v -> select(holder.itemView, GraphicsPresetManager.PRESET_HIGH));
        btnMid.setOnClickListener( v -> select(holder.itemView, GraphicsPresetManager.PRESET_MID));
        btnLow.setOnClickListener( v -> select(holder.itemView, GraphicsPresetManager.PRESET_LOW));
    }

    private void select(View root, String preset) {
        updateSelection(root, preset);
        GraphicsPresetManager.applyPreset(getContext(), preset);

        if (GraphicsPresetManager.PRESET_LOW.equals(preset)) {
            Toast.makeText(getContext(),
                    "Low preset selected — installing performance mods…",
                    Toast.LENGTH_SHORT).show();
            GraphicsPresetManager.autoInstallPerfMods(getContext(), () -> {});
        } else {
            String label = GraphicsPresetManager.PRESET_HIGH.equals(preset) ? "High" : "Mid";
            Toast.makeText(getContext(),
                    label + " preset applied. Changes take effect next launch.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSelection(View root, String preset) {
        View btnHigh = root.findViewById(R.id.preset_btn_high);
        View btnMid  = root.findViewById(R.id.preset_btn_mid);
        View btnLow  = root.findViewById(R.id.preset_btn_low);

        setActive(btnHigh, GraphicsPresetManager.PRESET_HIGH.equals(preset));
        setActive(btnMid,  GraphicsPresetManager.PRESET_MID.equals(preset));
        setActive(btnLow,  GraphicsPresetManager.PRESET_LOW.equals(preset));
    }

    private void setActive(View btn, boolean active) {
        if (btn == null) return;
        btn.setSelected(active);
        if (btn instanceof LinearLayout) {
            // Tint the label
            for (int i = 0; i < ((LinearLayout) btn).getChildCount(); i++) {
                View child = ((LinearLayout) btn).getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(active ? 0xFF000000 : 0xFF888888);
                }
            }
        }
        btn.setBackgroundResource(active
                ? R.drawable.preset_btn_active_bg
                : R.drawable.preset_btn_inactive_bg);
    }
}
