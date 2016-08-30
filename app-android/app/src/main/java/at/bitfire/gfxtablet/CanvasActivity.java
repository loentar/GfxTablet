package at.bitfire.gfxtablet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class CanvasActivity extends AppCompatActivity implements View.OnSystemUiVisibilityChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int RESULT_LOAD_IMAGE = 1;
    private static final String TAG = "GfxTablet.Canvas";

    private final static Uri homepageUri = Uri.parse("https://gfxtablet.bitfire.at");

    private NetworkClient netClient;

    private SharedPreferences preferences;
    private boolean fullScreen = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        setContentView(R.layout.activity_canvas);

        // create network client in a separate thread
        netClient = new NetworkClient();
        if (!netClient.create()) {
            Toast.makeText(this, "Failed to create client", Toast.LENGTH_LONG).show();
        } else {
            updateNetworkConfig();
        }

        // notify CanvasView of the network client
        CanvasView canvas = (CanvasView)findViewById(R.id.canvas);
        if (canvas != null)
            canvas.setNetworkClient(netClient);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (preferences.getBoolean(SettingsActivity.KEY_KEEP_DISPLAY_ACTIVE, true))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        showTemplateImage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        netClient.destroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_canvas, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (fullScreen)
            switchFullScreen(null);
        else
            super.onBackPressed();
    }

    public void showAbout(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, homepageUri));
    }

    public void showDonate(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, homepageUri.buildUpon().appendPath("donate").build()));
    }

    public void showSettings(MenuItem item) {
        startActivityForResult(new Intent(this, SettingsActivity.class), 0);
    }


    // preferences were changed

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case SettingsActivity.KEY_PREF_HOST:
                Log.i(TAG, "Recipient host changed, reconfiguring network client");
                updateNetworkConfig();
                break;
        }
    }


    // full-screen methods

    public void switchFullScreen(MenuItem item) {
        final View decorView = getWindow().getDecorView();
        int uiFlags = decorView.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= 16) {
            uiFlags ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        } else if (Build.VERSION.SDK_INT >= 14) {
            uiFlags ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        if (Build.VERSION.SDK_INT >= 19)
            uiFlags ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        decorView.setOnSystemUiVisibilityChangeListener(this);
        decorView.setSystemUiVisibility(uiFlags);
        fullScreen = !fullScreen;
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        Log.i("GfxTablet", "System UI changed " + visibility);

        // show/hide action bar according to full-screen mode
        ActionBar actionBar = CanvasActivity.this.getSupportActionBar();
        if (actionBar != null) {
            if (fullScreen) {
                actionBar.hide();
                Toast.makeText(CanvasActivity.this, "Press Back button to leave full-screen mode.", Toast.LENGTH_LONG).show();
            } else {
                actionBar.show();
            }
        }
    }


    // template image logic

    private String getTemplateImagePath() {
        return preferences.getString(SettingsActivity.KEY_TEMPLATE_IMAGE, null);
    }

    public void setTemplateImage(MenuItem item) {
        if (getTemplateImagePath() == null)
            selectTemplateImage(item);
        else {
            // template image already set, show popup
            PopupMenu popup = new PopupMenu(this, findViewById(R.id.menu_set_template_image));
            popup.getMenuInflater().inflate(R.menu.set_template_image, popup.getMenu());
            popup.show();
        }
    }

    public void selectTemplateImage(MenuItem item) {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, RESULT_LOAD_IMAGE);
    }

    public void clearTemplateImage(MenuItem item) {
        preferences.edit().remove(SettingsActivity.KEY_TEMPLATE_IMAGE).apply();
        showTemplateImage();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            if (cursor != null) {
                try {
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String picturePath = cursor.getString(columnIndex);

                    preferences.edit().putString(SettingsActivity.KEY_TEMPLATE_IMAGE, picturePath).apply();
                    showTemplateImage();
                } finally {
                    cursor.close();
                }
            }
        }
    }

    public void showTemplateImage() {
        ImageView template = (ImageView)findViewById(R.id.canvas_template);
        if (template == null)
            return;

        template.setImageDrawable(null);

        if (template.getVisibility() == View.VISIBLE) {
            String picturePath = preferences.getString(SettingsActivity.KEY_TEMPLATE_IMAGE, null);
            if (picturePath != null)
                try {
                    // TODO load bitmap efficiently, for intended view size and display resolution
                    // https://developer.android.com/training/displaying-bitmaps/load-bitmap.html
                    final Drawable drawable = new BitmapDrawable(getResources(), picturePath);
                    template.setImageDrawable(drawable);
                } catch (Exception e) {
                    Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }
        }
    }

    private void updateNetworkConfig() {
        String host = preferences.getString(SettingsActivity.KEY_PREF_HOST, "unknown.invalid");

        boolean success = netClient.setNetworkConfig(host);
        if (success)
            Toast.makeText(CanvasActivity.this, "Touch events will be sent to " + host
                    + ":" + NetworkClient.GFXTABLET_PORT, Toast.LENGTH_LONG).show();

        int[] viewIds = {R.id.canvas, R.id.canvas_template, R.id.canvas_message};
        for (int id :viewIds) {
            View view = findViewById(id);
            if (view != null)
                view.setVisibility(success ? View.VISIBLE : View.GONE);
        }
    }

}