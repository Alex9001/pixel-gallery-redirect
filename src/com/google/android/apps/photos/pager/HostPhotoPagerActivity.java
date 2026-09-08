package com.google.android.apps.photos.pager;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** Bridges Pixel Camera's Google Photos contract to GrapheneOS Gallery. */
public final class HostPhotoPagerActivity extends Activity {
    private static final String GALLERY = "com.android.gallery3d";
    private static final String TAG = "PixelGalleryRedirect";
    private boolean dispatched;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
    }

    @Override public void onResume() {
        super.onResume();
        if (dispatched) return;
        // Never expose the user's full gallery while the device is locked.
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardLocked()) {
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { redirect(); }
                @Override public void onDismissCancelled() { finish(); }
                @Override public void onDismissError() { finish(); }
            });
        } else {
            redirect();
        }
    }

    private void redirect() {
        if (dispatched) return;
        dispatched = true;
        Intent incoming = getIntent();
        Uri uri = incoming.getData();
        if (uri == null && Intent.ACTION_SEND.equals(incoming.getAction())) {
            uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        String type = incoming.getType();
        if (uri != null && "content".equals(uri.getScheme())) {
            if (type == null) {
                try { type = getContentResolver().getType(uri); }
                catch (SecurityException ignored) { /* Gallery fallback below. */ }
            }
            if ("application/vnd.google.panorama360+jpg".equals(type)) type = "image/jpeg";
            if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) {
                // Construct a fresh intent: no camera-specific extras, callbacks or task flags.
                Intent view = new Intent(Intent.ACTION_VIEW).setPackage(GALLERY)
                    .setDataAndType(uri, type);
                view.setClipData(ClipData.newRawUri("Camera photo", uri));
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (launch(view, "Opened selected media in Gallery")) return;
            }
        }
        Intent gallery = getPackageManager().getLaunchIntentForPackage(GALLERY);
        if (gallery != null && launch(gallery, "Opened Gallery home")) return;
        Toast.makeText(this, "Enable the GrapheneOS Gallery app to view photos.",
                Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean launch(Intent intent, String message) {
        try {
            startActivity(intent);
            Log.i(TAG, message);
            finish();
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            // Do not log photo URIs or camera extras.
            Log.w(TAG, "Gallery launch unavailable: " + e.getClass().getSimpleName());
            return false;
        }
    }
}
