package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Card buttons (new layout uses LinearLayout containers)
        View modsButton          = view.findViewById(R.id.mods_button);
        View newsButton          = view.findViewById(R.id.news_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View discordButton       = view.findViewById(R.id.discord_button);
        View openFilesButton     = view.findViewById(R.id.open_files_button);
        View installJarButton    = view.findViewById(R.id.install_jar_button);
        View shareLogsButton     = view.findViewById(R.id.share_logs_button);

        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);
        View playButton               = view.findViewById(R.id.play_button);
        mVersionSpinner               = view.findViewById(R.id.mc_version_spinner);

        // ── Staggered entrance animations ──
        View[] cards = {modsButton, newsButton, customControlButton,
                        discordButton, openFilesButton, installJarButton, shareLogsButton};
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null) animateCard(cards[i], i * 55L);
        }
        if (playButton != null) animateCard(playButton, 420L);

        // ── Click listeners ──
        if (modsButton != null)
            modsButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ModsFragment.class, ModsFragment.TAG, null));

        if (newsButton != null)
            newsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));

        if (customControlButton != null)
            customControlButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        if (discordButton != null)
            discordButton.setOnClickListener(v ->
                Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        if (openFilesButton != null)
            openFilesButton.setOnClickListener(v ->
                openPath(v.getContext(), getCurrentProfileDirectory(), false));

        if (installJarButton != null) {
            installJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            installJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        }

        if (shareLogsButton != null)
            shareLogsButton.setOnClickListener(v -> shareLog(requireContext()));

        if (editProfileButton != null)
            editProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        if (playButton != null)
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        // Long-press on news → gamepad mapper
        if (newsButton != null)
            newsButton.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
    }

    private void animateCard(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(24f);
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(320)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
