package net.kdt.pojavlaunch.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModsFragment extends Fragment {

    public static final String TAG = "ModsFragment";

    private TextView mDetectedVersion;
    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingLayout;
    private EditText mSearchEdit;
    private ModsAdapter mAdapter;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private String mCurrentLoader = "all";
    private String mDetectedVersionStr = "";
    private final List<ModItem> mAllMods = new ArrayList<>();

    public ModsFragment() {
        super(R.layout.fragment_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mDetectedVersion = view.findViewById(R.id.mods_detected_version);
        mRecyclerView    = view.findViewById(R.id.mods_recycler);
        mLoadingLayout   = view.findViewById(R.id.mods_loading);
        mSearchEdit      = view.findViewById(R.id.mods_search);

        TextView title   = view.findViewById(R.id.mods_title);

        mAdapter = new ModsAdapter(new ArrayList<>());
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);

        // ── Fade-in animations ──
        animateFadeIn(title, 0);
        animateFadeIn(mDetectedVersion, 120);
        animateFadeIn(mSearchEdit, 200);

        // ── Detect Minecraft version from active profile ──
        mDetectedVersionStr = detectMinecraftVersion();
        mDetectedVersion.setText("MC " + (mDetectedVersionStr.isEmpty() ? "Not detected" : mDetectedVersionStr));

        // ── Filter chips ──
        setChipListeners(view);

        // ── Search ──
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filterAndShow(s.toString());
            }
        });

        // ── Load mods ──
        loadMods("");
    }

    // ─── Version detection ───────────────────────────────────────────────
    private String detectMinecraftVersion() {
        try {
            String profileKey = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (profileKey == null) return "";
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
            if (profile == null) return "";
            String ver = profile.lastVersionId;
            if (ver == null) return "";
            // Strip loader prefix e.g. "fabric-loader-1.20.1" → "1.20.1"
            if (ver.contains("fabric-loader-")) ver = ver.split("fabric-loader-")[1];
            if (ver.contains("forge-")) ver = ver.split("forge-")[1].split("-")[0];
            return ver;
        } catch (Exception e) {
            return "";
        }
    }

    // ─── Modrinth API fetch ───────────────────────────────────────────────
    private void loadMods(String query) {
        mLoadingLayout.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);

        mExecutor.execute(() -> {
            List<ModItem> result = new ArrayList<>();
            try {
                StringBuilder facets = new StringBuilder("[");
                facets.append("[\"project_type:mod\"]");
                if (!mDetectedVersionStr.isEmpty()) {
                    facets.append(",[\"versions:").append(mDetectedVersionStr).append("\"]");
                }
                if (!mCurrentLoader.equals("all")) {
                    facets.append(",[\"categories:").append(mCurrentLoader).append("\"]");
                }
                facets.append("]");

                String q = query.isEmpty() ? "minecraft" : query;
                String urlStr = "https://api.modrinth.com/v2/search?query="
                        + URLEncoder.encode(q, "UTF-8")
                        + "&limit=30&facets=" + URLEncoder.encode(facets.toString(), "UTF-8");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Luveria/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray hits = json.getJSONArray("hits");
                    for (int i = 0; i < hits.length(); i++) {
                        JSONObject hit = hits.getJSONObject(i);
                        ModItem item = new ModItem();
                        item.title        = hit.optString("title", "Unknown");
                        item.author       = hit.optString("author", "");
                        item.description  = hit.optString("description", "");
                        item.downloads    = hit.optInt("downloads", 0);
                        item.projectId    = hit.optString("project_id", "");
                        item.version      = mDetectedVersionStr;
                        // Pick first loader category
                        JSONArray cats = hit.optJSONArray("categories");
                        if (cats != null) {
                            for (int j = 0; j < cats.length(); j++) {
                                String c = cats.getString(j);
                                if (c.equals("fabric") || c.equals("forge") || c.equals("quilt") || c.equals("neoforge")) {
                                    item.loader = c;
                                    break;
                                }
                            }
                        }
                        if (item.loader == null) item.loader = "unknown";
                        result.add(item);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // silently fail
            }
            mAllMods.clear();
            mAllMods.addAll(result);
            mMainHandler.post(() -> {
                mLoadingLayout.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mAdapter.setMods(new ArrayList<>(mAllMods));
            });
        });
    }

    private void filterAndShow(String query) {
        if (query.isEmpty()) {
            loadMods("");
        } else {
            loadMods(query);
        }
    }

    // ─── Chip listeners ─────────────────────────────────────────────────
    private void setChipListeners(View root) {
        int[] chipIds   = {R.id.chip_all, R.id.chip_fabric, R.id.chip_forge, R.id.chip_quilt};
        String[] labels = {"all",         "fabric",         "forge",         "quilt"};

        for (int i = 0; i < chipIds.length; i++) {
            final String loader = labels[i];
            root.findViewById(chipIds[i]).setOnClickListener(v -> {
                mCurrentLoader = loader;
                updateChipState(root, chipIds, v.getId());
                loadMods(mSearchEdit.getText().toString());
            });
        }
    }

    private void updateChipState(View root, int[] ids, int activeId) {
        for (int id : ids) {
            View chip = root.findViewById(id);
            if (id == activeId) {
                chip.setBackgroundResource(R.drawable.chip_active_bg);
                ((TextView) chip).setTextColor(0xFF000000);
            } else {
                chip.setBackgroundResource(R.drawable.chip_inactive_bg);
                ((TextView) chip).setTextColor(0xFF888888);
            }
        }
    }

    // ─── Animation helper ────────────────────────────────────────────────
    private void animateFadeIn(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(16f);
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(350)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    // ─── Data model ──────────────────────────────────────────────────────
    public static class ModItem {
        public String title, author, description, projectId, version, loader;
        public int downloads;
    }

    // ─── Adapter ─────────────────────────────────────────────────────────
    public class ModsAdapter extends RecyclerView.Adapter<ModsAdapter.VH> {

        private List<ModItem> mItems;

        public ModsAdapter(List<ModItem> items) { this.mItems = items; }

        public void setMods(List<ModItem> items) {
            mItems = items;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mod_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ModItem item = mItems.get(position);
            h.name.setText(item.title);
            h.author.setText("by " + item.author);
            h.desc.setText(item.description);
            h.versionBadge.setText(item.version.isEmpty() ? "Any" : item.version);
            h.loaderBadge.setText(item.loader);
            h.iconLetter.setText(item.title.isEmpty() ? "?" :
                    String.valueOf(item.title.charAt(0)).toUpperCase());

            long dl = item.downloads;
            String dlStr = dl >= 1_000_000 ? (dl / 1_000_000) + "M" :
                           dl >= 1_000      ? (dl / 1_000) + "k" :
                           String.valueOf(dl);
            h.downloads.setText("↓ " + dlStr);

            // Item animation
            h.itemView.setAlpha(0f);
            h.itemView.setTranslationY(20f);
            h.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(position * 40L)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();

            h.installBtn.setOnClickListener(v -> {
                Toast.makeText(requireContext(),
                    "Installing " + item.title + " for MC " + item.version + "…",
                    Toast.LENGTH_SHORT).show();
                h.installBtn.setEnabled(false);
                h.installBtn.setText("✓");
                h.installBtn.setBackgroundResource(R.drawable.btn_installed_bg);
                ((TextView) h.installBtn).setTextColor(0xFF888888);
            });
        }

        @Override public int getItemCount() { return mItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, author, desc, versionBadge, loaderBadge, downloads, iconLetter;
            TextView installBtn;
            VH(@NonNull View v) {
                super(v);
                name         = v.findViewById(R.id.mod_name);
                author       = v.findViewById(R.id.mod_author);
                desc         = v.findViewById(R.id.mod_description);
                versionBadge = v.findViewById(R.id.mod_version_badge);
                loaderBadge  = v.findViewById(R.id.mod_loader_badge);
                downloads    = v.findViewById(R.id.mod_downloads);
                iconLetter   = v.findViewById(R.id.mod_icon_letter);
                installBtn   = v.findViewById(R.id.mod_install_btn);
            }
        }
    }
}
