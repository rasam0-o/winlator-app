package com.winlator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherFragment extends Fragment {
    public static final String APP_NAME = "GameBox";
    private static final String PREF_KEY = "game_library";
    private static final int GREEN = 0xFF3DDC84;

    private final ExecutorService iconExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout cardRow;

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

    // ---------- Screen ----------

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();

        FrameLayout root = new FrameLayout(context);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF070B14, 0xFF0B2A22}));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(32), dp(20), dp(32), dp(20));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText(APP_NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = new TextView(context);
        settings.setText("\u2699");
        settings.setTextColor(0xFFB8C2D9);
        settings.setTextSize(28);
        settings.setPadding(dp(12), dp(4), dp(12), dp(4));
        settings.setOnClickListener((v) -> ((MainActivity)requireActivity()).openAdvancedMenu());
        topBar.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText("MY GAMES");
        label.setTextColor(0xFF8A94A8);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(10);
        column.addView(label, labelParams);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        cardRow = new LinearLayout(context);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(cardRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        iconExecutor.shutdownNow();
    }

    public void refresh() {
        if (cardRow == null || getContext() == null) return;
        cardRow.removeAllViews();
        cardRow.addView(createCard(null));
        for (Game game : loadGames(getContext())) cardRow.addView(createCard(game));
    }

    private View createCard(final Game game) {
        Context context = requireContext();
        final boolean isImport = game == null;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(14), dp(16), dp(14), dp(14));
        card.setBackground(rounded(0xFF151B2B, 22, isImport ? GREEN : 0xFF232B40));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(150), dp(220));
        cardParams.setMargins(0, 0, dp(16), 0);
        card.setLayoutParams(cardParams);
        card.setClickable(true);
        card.setFocusable(true);

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(rounded(0xFF1E2740, 18, 0));
        card.addView(iconBox, new LinearLayout.LayoutParams(dp(88), dp(88)));

        final TextView letter = new TextView(context);
        letter.setGravity(Gravity.CENTER);
        letter.setTextSize(isImport ? 40 : 34);
        letter.setTextColor(isImport ? GREEN : 0xFFB8C2D9);
        letter.setText(isImport ? "+" : (game.name.isEmpty() ? "?" : game.name.substring(0, 1).toUpperCase()));
        iconBox.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!isImport) {
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setPadding(dp(10), dp(10), dp(10), dp(10));
            iconBox.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            loadIcon(game.path, icon, letter);
        }

        TextView name = new TextView(context);
        name.setText(isImport ? "Import PC games" : game.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(14);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        nameParams.topMargin = dp(12);
        card.addView(name, nameParams);

        TextView button = new TextView(context);
        button.setText(isImport ? "Import" : "\u25B6  Play");
        button.setTextColor(0xFF06210F);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(GREEN, 18, 0));
        button.setPadding(0, dp(9), 0, dp(9));
        card.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
