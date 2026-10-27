package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.AboutDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final boolean DEBUG_MODE = false; // FIXME change to false
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    public static final byte IMPORT_GAME_REQUEST_CODE = 5;
    public static final byte IMPORT_ZIP_REQUEST_CODE = 6;
    public static final byte IMPORT_FOLDER_REQUEST_CODE = 7;
    private static final int MENU_IMPORT_GAMES = 9001;
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.getMenu().add(Menu.NONE, MENU_IMPORT_GAMES, 0, "Import PC games");

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menu_item_input_controls));
            navigationView.setCheckedItem(R.id.menu_item_input_controls);
        }
        else {
            boolean showShortcutsFirst = preferences.getBoolean("show_shortcuts_first", false);
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : (showShortcutsFirst ? R.id.menu_item_shortcuts : R.id.menu_item_containers);

            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);
            if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);

            int containerId = intent.getIntExtra("container_id", 0);
            String startPath = intent.getStringExtra("start_path");
            if (containerId > 0 && startPath != null) {
                showFragment(new ContainerFileManagerFragment(containerId, startPath));
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
        else if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == IMPORT_GAME_REQUEST_CODE) importGame(data.getData());
            else if (requestCode == IMPORT_ZIP_REQUEST_CODE) importZip(data.getData());
            else if (requestCode == IMPORT_FOLDER_REQUEST_CODE) importFolder(data.getData());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
            else if (currentFragment instanceof ContainersFragment) {
                finish();
            }
        }

        showFragment(new ContainersFragment());
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_add ||
            itemId == R.id.menu_item_home ||
            itemId == R.id.menu_item_view_style ||
            itemId == R.id.menu_item_new_folder) {
            return super.onOptionsItemSelected(menuItem);
        }
        else {
            if (editInputControls) {
                setResult(RESULT_OK);
                finish();
            }
            else {
                if (currentFragment instanceof BaseFileManagerFragment) {
                    BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                    if (fileManagerFragment.onOptionsMenuClicked()) return true;
                }
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == MENU_IMPORT_GAMES) {
            drawerLayout.closeDrawer(GravityCompat.START);
            startImportGame();
            return true;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.menu_item_shortcuts:
                preferences.edit().putBoolean("show_shortcuts_first", true).apply();
                showFragment(new ShortcutsFragment());
                break;
            case R.id.menu_item_containers:
                preferences.edit().putBoolean("show_shortcuts_first", false).apply();
                showFragment(new ContainersFragment());
                break;
            case R.id.menu_item_input_controls:
                showFragment(new InputControlsFragment(selectedProfileId));
                break;
            case R.id.menu_item_settings:
                showFragment(new SettingsFragment());
                break;
            case R.id.menu_item_about:
                (new AboutDialog(this)).show();
                break;
        }
        return true;
    }

    public void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();

        drawerLayout.closeDrawer(GravityCompat.START);
        currentFragment = fragment;
    }

    // ---------- Import PC games ----------

    private void startImportGame() {
        String[] options = {"Game folder", "ZIP file (auto extract)", "EXE file"};
        new AlertDialog.Builder(this)
            .setTitle("Import PC games")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), IMPORT_FOLDER_REQUEST_CODE);
                }
                else {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, which == 1 ? IMPORT_ZIP_REQUEST_CODE : IMPORT_GAME_REQUEST_CODE);
                }
            })
            .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String docIdToPath(String docId) {
        if (docId == null) return null;
        if (docId.startsWith("raw:")) return docId.substring(4);
        String[] parts = docId.split(":", 2);
        if (parts.length == 2 && parts[0].equalsIgnoreCase("primary")) {
            return Environment.getExternalStorageDirectory().getPath() + "/" + parts[1];
        }
        return null;
    }

    private String uriToPath(Uri uri, boolean isTree) {
        try {
            if (isTree) return docIdToPath(DocumentsContract.getTreeDocumentId(uri));
            if (!DocumentsContract.isDocumentUri(this, uri)) return null;
            return docIdToPath(DocumentsContract.getDocumentId(uri));
        }
        catch (Exception e) {
            return null;
        }
    }

    private void importFolder(Uri uri) {
        String path = uriToPath(uri, true);
        if (path == null) {
            toast("Could not read this folder. Pick a folder from 'Internal storage'.");
            return;
        }

        File dir = new File(path);
        if (!dir.isDirectory()) {
            toast("Folder not found");
            return;
        }
        chooseExecutable(dir);
    }

    private void importZip(Uri uri) {
        String zipPath = uriToPath(uri, false);
        String name = zipPath != null ? new File(zipPath).getName() : "game";
        String folderName = name.replaceAll("(?i)\\.zip$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        final File targetDir = new File(Environment.getExternalStorageDirectory(), "WinlatorGames/" + folderName);

        toast("Extracting, please wait...");
        new Thread(() -> {
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(in)) {
                targetDir.mkdirs();
                String basePath = targetDir.getCanonicalPath() + File.separator;
                byte[] buffer = new byte[65536];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File out = new File(targetDir, entry.getName());
                    if (!out.getCanonicalPath().startsWith(basePath)) continue;
                    if (entry.isDirectory()) {
                        out.mkdirs();
                        continue;
                    }
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int count;
                        while ((count = zis.read(buffer)) > 0) fos.write(buffer, 0, count);
                    }
                }
            }
            catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }

            final String finalError = error;
            runOnUiThread(() -> {
                if (finalError != null) toast("Extract failed: " + finalError);
                else chooseExecutable(targetDir);
            });
        }).start();
    }

    private void findExecutables(File dir, List<File> result, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 4) return;
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isDirectory()) {
                if (!name.contains("redist") && !name.equals("directx")) findExecutables(file, result, depth + 1);
            }
            else if (name.endsWith(".exe") && !name.contains("unins") && !name.contains("setup") &&
                !name.contains("vcredist") && !name.contains("crash")) {
                result.add(file);
            }
        }
    }

    private void chooseExecutable(File gameDir) {
        final List<File> executables = new ArrayList<>();
        findExecutables(gameDir, executables, 0);

        if (executables.isEmpty()) {
            toast("No game .exe file found here");
        }
        else if (executables.size() == 1) {
            launchGame(executables.get(0).getPath());
        }
        else {
            String[] names = new String[executables.size()];
            int prefixLength = gameDir.getPath().length() + 1;
            for (int i = 0; i < names.length; i++) {
                String fullPath = executables.get(i).getPath();
                names[i] = fullPath.length() > prefixLength ? fullPath.substring(prefixLength) : fullPath;
            }

            new AlertDialog.Builder(this)
                .setTitle("Choose the game's .exe")
                .setItems(names, (dialog, which) -> launchGame(executables.get(which).getPath()))
                .show();
        }
    }

    private void importGame(Uri uri) {
        String path = uriToPath(uri, false);
        if (path == null) {
            toast("Could not read this file location. Pick it from 'Internal storage'.");
            return;
        }

        String lowerPath = path.toLowerCase();
        if (!lowerPath.endsWith(".exe") && !lowerPath.endsWith(".bat")) {
            toast("Please choose the game's .exe file");
            return;
        }
        launchGame(path);
    }

    private void launchGame(String path) {
        Container container = null;
        for (Container c : (new ContainerManager(this)).getContainers()) {
            container = c;
            break;
        }
        if (container == null) {
            toast("Create a container first (Containers tab, + button)");
            return;
        }

        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", path);
        startActivity(intent);
    }
}
