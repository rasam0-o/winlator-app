package com.winlator;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.win32.PEParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherFragment extends Fragment {
    public static final String APP_NAME = "GameBox";
    private static final String PREF_KEY = "game_library";
    private static final int GREEN = 0xFF3DDC84;

    private final ExecutorService iconExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 30000);
        }
    };

    private LinearLayout cardRow;
    private TextView statusView;
    private TextView labelView;

    public static class Game {
        public final String name;
        public final String path;

        public Game(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    // ---------- Game library (saved on the phone) ----------

    public static List<Game> loadGames(Context context) {
        List<Game> games = new ArrayList<>();
        try {
            String json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                games.add(new Game(obj.getString("name"), obj.getString("path")));
            }
        }
        catch (Exception e) {}
        return games;
    }

    private static void saveGames(Context context, List<Game> games) {
        try {
            JSONArray array = new JSONArray();
            for (Game game : games) {
                JSONObject obj = new JSONObject();
                obj.put("name", game.name);
                obj.put("path", game.path);
                array.put(obj);
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_KEY, array.toString()).apply();
        }
        catch (Exception e) {}
    }

    public static boolean addGame(Context context, String path) {
        List<Game> games = loadGames(context);
        for (Game game : games) {
            if (game.path.equals(path)) return false;
        }

        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        games.add(new Game(name, path));
        saveGames(context, games);
        return true;
    }

    public static void removeGame(Context context, String path) {
        List<Game> result = new ArrayList<>();
        for (Game game : loadGames(context)) {
            if (!game.path.equals(path)) result.add(game);
        }
        saveGames(context, result);
    }

    // ---------- Helpers ----------

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable topRounded(int color1, int color2) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color1, color2});
        float r = dp(24);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable cardBackground(boolean isImport, boolean focused) {
        int stroke = focused ? GREEN : (isImport ? 0xFF2A6B4C : 0xFF232B40);
        return rounded(0xFF141A2A, 24, stroke, focused ? 2 : 1);
    }

    private int[] cardSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float availableDp = dm.heightPixels / dm.density - 170f;
        int heightDp = (int)Math.max(200f, Math.min(300f, availableDp));
        int widthDp = (int)(heightDp * 0.74f);
        return new int[]{widthDp, heightDp};
    }

    // ---------- Screen ----------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        DisplayMetrics dm = getResources().getDisplayMetrics();

        FrameLayout root = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setGradientCenter(0.85f, 1.0f);
        background.setGradientRadius(Math.max(dm.widthPixels, dm.heightPixels) * 0.9f);
        background.setColors(new int[]{0xFF0F4A35, 0xFF070B14});
        root.setBackground(background);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(40), dp(18), dp(40), dp(14));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top bar
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText(APP_NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusView = new TextView(context);
        statusView.setTextColor(0xFFB8C2D9);
        statusView.setTextSize(15);
        topBar.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView settings = new TextView(context);
        settings.setText("\u2699");
        settings.setTextColor(0xFFB8C2D9);
        settings.setTextSize(28);
        settings.setPadding(dp(16), dp(4), dp(4), dp(4));
        settings.setOnClickListener((v) -> ((MainActivity)requireActivity()).openAdvancedMenu());
        topBar.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Section label
        labelView = new TextView(context);
        labelView.setTextColor(0xFF8A94A8);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(6);
        column.addView(labelView, labelParams);

        // Cards
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        cardRow = new LinearLayout(context);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);
        cardRow.setClipChildren(false);
        cardRow.setPadding(dp(8), 0, dp(24), 0);
        scroll.addView(cardRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Hint
        TextView hint = new TextView(context);
        hint.setText("Tap a game to play  \u2022  Hold a game for more options");
        hint.setTextColor(0xFF5E6A82);
        hint.setTextSize(12);
        column.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        handler.removeCallbacks(ticker);
        handler.post(ticker);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        iconExecutor.shutdownNow();
    }

    private void updateStatus() {
        if (statusView == null || getContext() == null) return;

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String battery = "";
        try {
            Intent batteryIntent = requireContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) battery = "    " + (level * 100 / scale) + "%";
            }
        }
        catch (Exception e) {}
        statusView.setText(time + battery);
    }

    public void refresh() {
        if (cardRow == null || getContext() == null) return;

        List<Game> games = loadGames(getContext());
        if (labelView != null) labelView.setText("MY GAMES  \u2022  " + games.size());

        cardRow.removeAllViews();
        cardRow.addView(createCard(null));
        for (Game game : games) cardRow.addView(createCard(game));
    }

    private View createCard(final Game game) {
        Context context = requireContext();
        final boolean isImport = game == null;

        int[] size = cardSize();
        int cardWidth = dp(size[0]);
        int cardHeight = dp(size[1]);
        int artHeight = (int)(cardHeight * 0.56f);

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground(isImport, false));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cardParams.setMargins(dp(4), dp(10), dp(16), dp(10));
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        // Art area
        FrameLayout art = new FrameLayout(context);
        if (isImport) {
            art.setBackground(topRounded(0xFF1A2236, 0xFF121A2C));
        }
        else {
            int hue = Math.abs(game.name.hashCode()) % 360;
            int color1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.55f});
            int color2 = Color.HSVToColor(new float[]{(hue + 40) % 360, 0.65f, 0.22f});
            art.setBackground(topRounded(color1, color2));
        }
        card.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, artHeight));

        final TextView letter = new TextView(context);
        letter.setGravity(Gravity.CENTER);
        letter.setTextSize(isImport ? 52 : 44);
        letter.setTextColor(isImport ? GREEN : 0xCCFFFFFF);
        letter.setText(isImport ? "+" : (game.name.isEmpty() ? "?" : game.name.substring(0, 1).toUpperCase()));
        art.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!isImport) {
            int iconSize = (int)(artHeight * 0.62f);
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            loadIcon(game.path, icon, letter);
        }

        // Info area
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), dp(8), dp(12), dp(12));
        card.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView name = new TextView(context);
        name.setText(isImport ? "Import PC games" : game.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView button = new TextView(context);
        button.setText(isImport ? "Import" : "\u25B6  Play");
        button.setTextColor(0xFF06210F);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(GREEN, 18, 0, 0));
        button.setPadding(0, dp(9), 0, dp(9));
        info.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            card.setBackground(cardBackground(isImport, hasFocus));
        });

        card.setOnClickListener((v) -> {
            MainActivity activity = (MainActivity)requireActivity();
            if (isImport) activity.startImportGame();
            else activity.launchGame(game.path);
        });

        if (!isImport) {
            card.setOnLongClickListener((v) -> {
                showGameMenu(game);
                return true;
            });
        }
        return card;
    }

    private void showGameMenu(final Game game) {
        String[] options = {"Play", "Remove from library"};
        new AlertDialog.Builder(requireContext())
            .setTitle(game.name)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    ((MainActivity)requireActivity()).launchGame(game.path);
                }
                else {
                    removeGame(requireContext(), game.path);
                    refresh();
                }
            })
            .show();
    }

    private void loadIcon(final String path, final ImageView icon, final TextView letter) {
        try {
            iconExecutor.execute(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = PEParser.extractIcon(new File(path));
                }
                catch (Throwable e) {}

                final Bitmap result = bitmap;
                if (result != null) {
                    icon.post(() -> {
                        icon.setImageBitmap(result);
                        letter.setVisibility(View.GONE);
                    });
                }
            });
        }
        catch (Exception e) {}
    }
}
