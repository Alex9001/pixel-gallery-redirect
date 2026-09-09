package com.google.android.apps.photos.pager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bridges Pixel Camera's Google Photos contract to a user-selected media viewer. */
public final class HostPhotoPagerActivity extends Activity {
    private static final String TAG = "PixelGalleryRedirect";
    private boolean dispatched;
    private boolean choosing;
    private boolean unlocking;
    private boolean resumed;
    private String explanation;
    private String pendingPackage;
    private AlertDialog picker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            dispatched = state.getBoolean("dispatched");
            choosing = state.getBoolean("choosing");
            explanation = state.getString("explanation");
            pendingPackage = state.getString("pendingPackage");
        }
        if (dispatched) finish();
    }

    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("dispatched", dispatched);
        state.putBoolean("choosing", choosing);
        state.putString("explanation", explanation);
        state.putString("pendingPackage", pendingPackage);
        super.onSaveInstanceState(state);
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        proceed();
    }

    @Override public void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override public void onDestroy() {
        if (picker != null) picker.dismiss();
        super.onDestroy();
    }

    private void proceed() {
        if (!resumed || dispatched || isFinishing() || isDestroyed() || unlocking) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardLocked()) {
            unlocking = true;
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() {
                    unlocking = false;
                    // onResume also retries if the callback arrives while paused.
                    proceed();
                }
                @Override public void onDismissCancelled() { unlocking = false; finish(); }
                @Override public void onDismissError() { unlocking = false; finish(); }
            });
            return;
        }
        if (pendingPackage != null) redirect(pendingPackage);
        else if (choosing || isLauncher() || asksEveryTime() || selectedPackage() == null) showPicker(explanation);
        else redirect(selectedPackage());
    }

    boolean isLauncher() {
        return Intent.ACTION_MAIN.equals(getIntent().getAction())
                && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    SharedPreferences preferences() { return getSharedPreferences("gallery", MODE_PRIVATE); }
    String selectedPackage() { return preferences().getString("package", null); }
    boolean asksEveryTime() { return preferences().getBoolean("ask_every_time", false); }

    static final class Gallery {
        final ApplicationInfo app;
        final String name;
        boolean images;
        boolean videos;
        Gallery(ApplicationInfo app, PackageManager pm) {
            this.app = app;
            name = app.loadLabel(pm).toString();
        }
    }

    List<Gallery> discover() {
        PackageManager pm = getPackageManager();
        Map<String, Gallery> packages = new LinkedHashMap<>();
        String[] types = {"image/*", "video/*"};
        for (String type : types) {
            Intent query = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("content://media/external/"), type);
            for (ResolveInfo result : pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (result.activityInfo == null || !result.activityInfo.exported
                        || !result.activityInfo.enabled || !result.activityInfo.applicationInfo.enabled
                        || getPackageName().equals(result.activityInfo.packageName)) continue;
                ApplicationInfo app = result.activityInfo.applicationInfo;
                Gallery gallery = packages.get(app.packageName);
                if (gallery == null) {
                    gallery = new Gallery(app, pm);
                    packages.put(app.packageName, gallery);
                }
                if (type.startsWith("image")) gallery.images = true;
                else gallery.videos = true;
            }
        }
        List<Gallery> galleries = new ArrayList<>(packages.values());
        Collator collator = Collator.getInstance();
        galleries.sort((a, b) -> {
            int byName = collator.compare(a.name, b.name);
            return byName != 0 ? byName : a.app.packageName.compareTo(b.app.packageName);
        });
        return galleries;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void showPicker(String message) {
        choosing = true;
        explanation = message;
        if (picker != null) return;
        List<Gallery> galleries = discover();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Choose your gallery")
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish());
        final int offset = isLauncher() ? 1 : 0;
        if (galleries.isEmpty() && offset == 0) {
            builder.setMessage("Install or enable a gallery that can view photos or videos, then try again.");
        } else {
            if (galleries.isEmpty()) message = "Install or enable a gallery that can view photos or videos, then try again.";
            // A separate heading allows both an explanation and the native dialog list.
            if (message != null) {
                TextView heading = new TextView(this);
                heading.setText(message);
                heading.setPadding(dp(24), dp(12), dp(24), dp(12));
                builder.setView(heading);
            }
            String selected = selectedPackage();
            builder.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return galleries.size() + offset; }
                @Override public Object getItem(int position) { return position < offset ? null : galleries.get(position - offset); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    Gallery gallery = position < offset ? null : galleries.get(position - offset);
                    LinearLayout row = new LinearLayout(HostPhotoPagerActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(24), dp(12), dp(24), dp(12));
                    ImageView icon = new ImageView(HostPhotoPagerActivity.this);
                    if (gallery == null) icon.setImageResource(android.R.drawable.ic_menu_help);
                    else icon.setImageDrawable(gallery.app.loadIcon(getPackageManager()));
                    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    TextView label = new TextView(HostPhotoPagerActivity.this);
                    if (gallery == null) {
                        label.setText("Ask every time\nChoose for each camera preview"
                                + (asksEveryTime() ? " · Current choice" : ""));
                    } else {
                        String media = gallery.images && gallery.videos ? "Photos and videos"
                                : gallery.images ? "Photos" : "Videos";
                        label.setText(gallery.name + "\n" + media
                                + (!asksEveryTime() && gallery.app.packageName.equals(selected) ? " · Current choice" : ""));
                    }
                    label.setTextSize(16);
                    label.setPadding(dp(16), 0, 0, 0);
                    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    return row;
                }
            }, (dialog, which) -> {
                picker = null;
                if (which < offset) {
                    if (!preferences().edit().remove("package").putBoolean("ask_every_time", true).commit()) {
                        showPicker("Could not save your choice. Please try again.");
                        return;
                    }
                    Toast.makeText(this, "Will ask for each camera preview", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                Gallery gallery = galleries.get(which - offset);
                // In ask mode, a camera selection applies only to this pending request.
                if (isLauncher() || !asksEveryTime()) {
                    // Persist the small preference write before handing off to another process.
                    if (!preferences().edit().putString("package", gallery.app.packageName)
                            .putBoolean("ask_every_time", false).commit()) {
                        showPicker("Could not save your choice. Please try again.");
                        return;
                    }
                }
                choosing = false;
                explanation = null;
                if (isLauncher()) {
                    Toast.makeText(this, "Selected " + gallery.name, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    pendingPackage = gallery.app.packageName;
                    proceed();
                }
            });
        }
        picker = builder.create();
        picker.show();
    }

    Intent mediaIntent(String packageName) {
        Intent incoming = getIntent();
        Uri uri = incoming.getData();
        if (uri == null && Intent.ACTION_SEND.equals(incoming.getAction())) {
            Object stream = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) uri = (Uri) stream;
        }
        String type = incoming.getType();
        if (uri == null || !"content".equals(uri.getScheme())) return null;
        if (type == null) {
            try { type = getContentResolver().getType(uri); }
            catch (RuntimeException ignored) { /* Missing or inaccessible provider: use home. */ }
        }
        if ("application/vnd.google.panorama360+jpg".equals(type)) type = "image/jpeg";
        if (type == null || !(type.startsWith("image/") || type.startsWith("video/"))) return null;
        Intent view = new Intent(Intent.ACTION_VIEW).setPackage(packageName).setDataAndType(uri, type);
        view.setClipData(ClipData.newRawUri("Camera media", uri));
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return view;
    }

    private void redirect(String packageName) {
        Intent view = mediaIntent(packageName);
        if (view != null) {
            // Resolve for this exact URI/type: photo and video may use different activities.
            List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo handler : handlers) {
                if (handler.activityInfo == null || !handler.activityInfo.exported
                        || !handler.activityInfo.enabled || !handler.activityInfo.applicationInfo.enabled
                        || !packageName.equals(handler.activityInfo.packageName)) continue;
                Intent explicit = new Intent(view).setClassName(packageName, handler.activityInfo.name);
                if (launch(explicit)) return;
            }
        }
        Intent home = getPackageManager().getLaunchIntentForPackage(packageName);
        if (home != null && launch(home)) return;
        pendingPackage = null;
        showPicker("Your chosen gallery is unavailable or could not open this request. Choose a gallery to continue.");
    }

    private boolean launch(Intent intent) {
        try {
            startActivity(intent);
            dispatched = true;
            Log.i(TAG, "Opened chosen gallery");
            finish();
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            // Do not log photo URIs or camera extras.
            Log.w(TAG, "Gallery launch unavailable: " + e.getClass().getSimpleName());
            return false;
        }
    }
}
