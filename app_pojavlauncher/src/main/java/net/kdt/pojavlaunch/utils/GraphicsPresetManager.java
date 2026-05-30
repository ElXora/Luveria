package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Luveria Graphics Preset Manager
 * Applies MC options.txt settings per preset and auto-installs performance mods.
 *
 * HIGH  → Everything on  (fancy graphics, all particles, clouds on, render dist 12)
 * MID   → Balanced       (fast graphics, some particles, clouds off, render dist 8)  [DEFAULT]
 * LOW   → Max FPS        (fast graphics, no particles, no clouds, render dist 6, fog off)
 */
public class GraphicsPresetManager {

    public static final String PRESET_HIGH = "high";
    public static final String PRESET_MID  = "mid";
    public static final String PRESET_LOW  = "low";

    private static final String TAG = "LuveriaGraphics";

    /** Minecraft options.txt keys */
    private static final String KEY_GRAPHICS            = "graphicsMode";   // 0=fast 1=fancy 2=fabulous
    private static final String KEY_RENDER_DISTANCE     = "renderDistance";
    private static final String KEY_PARTICLES           = "particles";      // 0=all 1=decreased 2=minimal
    private static final String KEY_CLOUDS              = "renderClouds";   // true/false/"fast"
    private static final String KEY_SMOOTH_LIGHTING     = "ao";             // 0=off 1=min 2=max
    private static final String KEY_FOG                 = "fog";            // true/false (1.18+)
    private static final String KEY_ENTITY_SHADOWS      = "entityShadows";  // true/false
    private static final String KEY_ENTITY_DIST         = "entityDistanceMult"; // float
    private static final String KEY_MIPMAP              = "mipmapLevels";   // 0-4
    private static final String KEY_ATTACK_INDICATOR    = "attackIndicator";// 0/1/2
    private static final String KEY_MAX_FPS             = "maxFps";

    /**
     * Performance mods to auto-install per loader.
     * Modrinth project slugs — one per loader, best-effort.
     */
    private static final String[][] PERF_MODS_FABRIC = {
        // {modrinth project id, friendly name}
        {"AANobbMI", "Sodium"},
        {"gvQqBUqZ", "Lithium"},
        {"YL57xq9U", "Ok Zoomer"},   // tiny, QOL
        {"hEOCdOgW", "FerriteCore"},
        {"uXXizFIs", "ThreadTweak"},
    };
    private static final String[][] PERF_MODS_QUILT = {
        {"AANobbMI", "Sodium"},
        {"gvQqBUqZ", "Lithium"},
        {"hEOCdOgW", "FerriteCore"},
    };
    // Forge performance mods (different project IDs)
    private static final String[][] PERF_MODS_FORGE = {
        {"JuksLGBQ", "Rubidium"},        // Sodium fork for Forge
        {"nmDcB62a", "Oculus"},          // Iris fork for Forge
        {"5ZwThSOp", "Canary"},          // Lithium fork for Forge
    };

    /** Apply preset: writes options.txt and saves to SharedPreferences */
    public static void applyPreset(Context ctx, String preset) {
        // Save preference
        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        editor.putString("graphics_preset", preset);
        editor.apply();
        LauncherPreferences.PREF_GRAPHICS_PRESET = preset;

        // Write MC options
        MCOptionUtils.load();
        applyOptionsForPreset(preset);
        MCOptionUtils.save();

        Log.i(TAG, "Applied preset: " + preset);
    }

    private static void applyOptionsForPreset(String preset) {
        switch (preset) {
            case PRESET_HIGH:
                MCOptionUtils.set(KEY_GRAPHICS,         "1");   // fancy
                MCOptionUtils.set(KEY_RENDER_DISTANCE,  "12");
                MCOptionUtils.set(KEY_PARTICLES,        "0");   // all
                MCOptionUtils.set(KEY_CLOUDS,           "true");
                MCOptionUtils.set(KEY_SMOOTH_LIGHTING,  "2");
                MCOptionUtils.set(KEY_FOG,              "true");
                MCOptionUtils.set(KEY_ENTITY_SHADOWS,   "true");
                MCOptionUtils.set(KEY_ENTITY_DIST,      "1.0");
                MCOptionUtils.set(KEY_MIPMAP,           "4");
                MCOptionUtils.set(KEY_MAX_FPS,          "60");
                break;

            case PRESET_MID:
            default:
                MCOptionUtils.set(KEY_GRAPHICS,         "0");   // fast
                MCOptionUtils.set(KEY_RENDER_DISTANCE,  "8");
                MCOptionUtils.set(KEY_PARTICLES,        "1");   // decreased
                MCOptionUtils.set(KEY_CLOUDS,           "false");
                MCOptionUtils.set(KEY_SMOOTH_LIGHTING,  "1");
                MCOptionUtils.set(KEY_FOG,              "true");
                MCOptionUtils.set(KEY_ENTITY_SHADOWS,   "true");
                MCOptionUtils.set(KEY_ENTITY_DIST,      "0.9");
                MCOptionUtils.set(KEY_MIPMAP,           "2");
                MCOptionUtils.set(KEY_MAX_FPS,          "60");
                break;

            case PRESET_LOW:
                MCOptionUtils.set(KEY_GRAPHICS,         "0");   // fast
                MCOptionUtils.set(KEY_RENDER_DISTANCE,  "6");
                MCOptionUtils.set(KEY_PARTICLES,        "2");   // minimal
                MCOptionUtils.set(KEY_CLOUDS,           "false");
                MCOptionUtils.set(KEY_SMOOTH_LIGHTING,  "0");
                MCOptionUtils.set(KEY_FOG,              "false");
                MCOptionUtils.set(KEY_ENTITY_SHADOWS,   "false");
                MCOptionUtils.set(KEY_ENTITY_DIST,      "0.5");
                MCOptionUtils.set(KEY_MIPMAP,           "0");
                MCOptionUtils.set(KEY_MAX_FPS,          "120");
                break;
        }
    }

    /**
     * Auto-install the best performance mods for the active profile's loader + MC version.
     * Only runs for LOW preset. Runs on a background thread.
     */
    public static void autoInstallPerfMods(Context ctx, Runnable onComplete) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // Detect active profile
                String profileKey = LauncherPreferences.DEFAULT_PREF
                        .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                if (profileKey == null) { mainHandler.post(onComplete); return; }

                LauncherProfiles.load();
                MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
                if (profile == null || profile.lastVersionId == null) {
                    mainHandler.post(onComplete); return;
                }

                String verId     = profile.lastVersionId;
                String mcVersion = parseMcVersion(verId);
                String loader    = detectLoader(verId);

                String[][] mods = getMods(loader);
                File modsDir     = new File(Tools.getGameDirPath(profile), "mods");
                if (!modsDir.exists()) modsDir.mkdirs();

                int installed = 0;
                for (String[] mod : mods) {
                    String projectId = mod[0];
                    String modName   = mod[1];
                    try {
                        String dlUrl = resolveDownloadUrl(projectId, mcVersion, loader);
                        if (dlUrl == null) { Log.w(TAG, "No URL for " + modName); continue; }
                        String fname  = modName.toLowerCase().replace(" ", "-") + ".jar";
                        File   target = new File(modsDir, fname);
                        if (target.exists()) { Log.i(TAG, modName + " already present"); continue; }

                        downloadFile(dlUrl, target);
                        installed++;
                        final String name = modName;
                        mainHandler.post(() ->
                            Toast.makeText(ctx, "✓ Installed " + name, Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to install " + modName + ": " + e.getMessage());
                    }
                }

                final int count = installed;
                mainHandler.post(() -> {
                    if (count > 0)
                        Toast.makeText(ctx, count + " performance mods installed!", Toast.LENGTH_LONG).show();
                    onComplete.run();
                });
            } catch (Exception e) {
                Log.e(TAG, "autoInstallPerfMods error", e);
                mainHandler.post(onComplete);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Parse base MC version from loader-prefixed string e.g. fabric-loader-0.15.1-1.20.4 → 1.20.4 */
    private static String parseMcVersion(String verId) {
        if (verId == null) return "";
        // fabric-loader-X-MC  or  MC  or  MC-forge-X
        String[] parts = verId.split("-");
        // Last numeric segment that looks like a version
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+\\.\\d+.*")) return parts[i];
        }
        return verId;
    }

    private static String detectLoader(String verId) {
        if (verId == null) return "fabric";
        if (verId.contains("fabric")) return "fabric";
        if (verId.contains("quilt"))  return "quilt";
        if (verId.contains("forge"))  return "forge";
        return "fabric"; // Default to fabric
    }

    private static String[][] getMods(String loader) {
        switch (loader) {
            case "forge":  return PERF_MODS_FORGE;
            case "quilt":  return PERF_MODS_QUILT;
            default:       return PERF_MODS_FABRIC;
        }
    }

    /**
     * Query Modrinth API to get the best download URL for project + MC version + loader.
     */
    private static String resolveDownloadUrl(String projectId, String mcVersion, String loader)
            throws Exception {
        // Modrinth versions endpoint
        String apiUrl = "https://api.modrinth.com/v2/project/" + projectId
                + "/version?loaders=[%22" + loader + "%22]&game_versions=[%22" + mcVersion + "%22]";

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Luveria/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        if (conn.getResponseCode() != 200) return null;

        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        JSONArray versions = new JSONArray(sb.toString());
        if (versions.length() == 0) return null;

        // Pick latest (first) version
        JSONObject latest = versions.getJSONObject(0);
        JSONArray files   = latest.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            if (file.optBoolean("primary", false)) {
                return file.getString("url");
            }
        }
        // Fallback: first file
        return files.getJSONObject(0).getString("url");
    }

    private static void downloadFile(String urlStr, File target) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Luveria/1.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
        } finally {
            conn.disconnect();
        }
    }
}
