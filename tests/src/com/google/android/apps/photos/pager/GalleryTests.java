package com.google.android.apps.photos.pager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** On-device framework tests; no test libraries or production runtime dependencies. */
public final class GalleryTests extends Instrumentation {
    private static final String FIXTURE = "org.pixelgalleryredirect.tests";
    private final AtomicReference<HostPhotoPagerActivity> active = new AtomicReference<>();
    private final List<Intent> launches = new ArrayList<>();
    private boolean rejectMedia;
    private boolean rejectAll;
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private AlertDialog picker(HostPhotoPagerActivity activity) {
        try {
            Field field = HostPhotoPagerActivity.class.getDeclaredField("picker");
            field.setAccessible(true);
            return (AlertDialog) field.get(activity);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private HostPhotoPagerActivity open() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(getTargetContext(), HostPhotoPagerActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        HostPhotoPagerActivity activity = (HostPhotoPagerActivity) startActivitySync(intent);
        waitForIdleSync();
        return activity;
    }
    private void select(HostPhotoPagerActivity activity) {
        List<HostPhotoPagerActivity.Gallery> entries = activity.discover();
        int position = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).app.packageName.equals(FIXTURE)) position = i;
        final int row = position < 0 ? -1 : position + (activity.isLauncher() ? 1 : 0);
        check(row >= 0, "Fixture discovered");
        runOnMainSync(() -> picker(activity).getListView().performItemClick(null, row, row));
        waitForIdleSync();
    }
    private Intent request(String type) {
        return new Intent("android.provider.action.REVIEW")
                .setDataAndType(Uri.parse("content://org.pixelgalleryredirect.tests/example"), type)
                .putExtra("camera-private-extra", "must not forward")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    private void route(HostPhotoPagerActivity activity, Intent request) {
        runOnMainSync(() -> {
            activity.setIntent(request);
            try {
                java.lang.reflect.Method method = HostPhotoPagerActivity.class.getDeclaredMethod("redirect", String.class);
                method.setAccessible(true);
                method.invoke(activity, FIXTURE);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        waitForIdleSync();
    }

    @Override public void onStart() {
        SharedPreferences prefs = getTargetContext().getSharedPreferences("gallery", 0);
        String previous = prefs.getString("package", null);
        boolean previousAsk = prefs.getBoolean("ask_every_time", false);
        Application app = (Application) getTargetContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity a) { if (a instanceof HostPhotoPagerActivity) active.set((HostPhotoPagerActivity) a); }
            public void onActivityCreated(Activity a, Bundle b) {}
            public void onActivityStarted(Activity a) {}
            public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {}
        });
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (intent.getComponent() != null && FIXTURE.equals(intent.getComponent().getPackageName())) {
                    launches.add(new Intent(intent));
                    if (rejectAll || (rejectMedia && Intent.ACTION_VIEW.equals(intent.getAction()))) throw new SecurityException("Test rejection");
                    return new ActivityResult(Activity.RESULT_OK, null);
                }
                return null;
            }
        };
        addMonitor(monitor);
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            check(!getTargetContext().getSystemService(android.app.KeyguardManager.class).isKeyguardLocked(), "Unlock test device first");
            prefs.edit().remove("package").putBoolean("ask_every_time", false).commit();
            HostPhotoPagerActivity first = open();
            check(first.selectedPackage() == null, "No implicit default for new/upgrade installs");
            List<HostPhotoPagerActivity.Gallery> entries = first.discover();
            int count = 0;
            java.util.Set<String> packages = new java.util.HashSet<>();
            java.text.Collator collator = java.text.Collator.getInstance();
            String last = null;
            for (HostPhotoPagerActivity.Gallery entry : entries) {
                check(packages.add(entry.app.packageName), "Unique package");
                check(!entry.app.packageName.equals(getTargetContext().getPackageName()), "Exclude redirect");
                check(last == null || collator.compare(last, entry.name) <= 0, "Sorted app names");
                last = entry.name;
                if (entry.app.packageName.equals(FIXTURE)) {
                    count++;
                    check(entry.images && entry.videos, "Merge separate photo/video activities");
                }
            }
            check(count == 1, "One fixture row");
            runOnMainSync(() -> picker(first).cancel());
            check(prefs.getString("package", null) == null, "Cancel preserves unset choice");
            Intent firstRequest = request("image/jpeg").setClassName(getTargetContext(), HostPhotoPagerActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            HostPhotoPagerActivity firstCamera = (HostPhotoPagerActivity) startActivitySync(firstRequest);
            waitForIdleSync();
            check(picker(firstCamera) != null && launches.isEmpty(), "First camera use asks before launching");
            runOnMainSync(() -> picker(firstCamera).cancel());
            check(prefs.getString("package", null) == null, "First camera cancellation preserves unset choice");
            HostPhotoPagerActivity choose = open();
            select(choose);
            check(FIXTURE.equals(prefs.getString("package", null)), "Launcher selection persisted");
            check(launches.isEmpty(), "Launcher does not open gallery");
            HostPhotoPagerActivity again = open();
            check(picker(again) != null, "Launcher always shows picker");
            runOnMainSync(() -> picker(again).cancel());
            check(FIXTURE.equals(prefs.getString("package", null)), "Cancel preserves previous choice");

            HostPhotoPagerActivity rotating = open();
            Intent pending = request("video/mp4");
            runOnMainSync(() -> { rotating.setIntent(pending); rotating.recreate(); });
            long deadline = System.currentTimeMillis() + 5000;
            while (active.get() == rotating && System.currentTimeMillis() < deadline) {
                android.os.SystemClock.sleep(50);
                waitForIdleSync();
            }
            HostPhotoPagerActivity recreated = active.get();
            check(recreated != rotating, "Activity recreated");
            check(picker(recreated) != null && picker(recreated).isShowing(), "Picker survives recreation");
            check(pending.getData().equals(recreated.getIntent().getData()), "Pending URI survives recreation");
            select(recreated);
            check(launches.size() == 1 && launches.get(0).getComponent().getClassName().endsWith(".Video"), "Selection resumes pending video exactly once");

            for (String type : new String[]{"image/jpeg", "video/mp4", "application/vnd.google.panorama360+jpg"}) {
                launches.clear();
                HostPhotoPagerActivity activity = open();
                route(activity, request(type));
                check(launches.size() == 1, "Single media launch");
                Intent sent = launches.get(0);
                check(sent.getData().equals(request(type).getData()), "Preserve URI");
                check(sent.getType().equals(type.startsWith("application/") ? "image/jpeg" : type), "Preserve/normalize MIME");
                check(sent.getFlags() == Intent.FLAG_GRANT_READ_URI_PERMISSION, "Only read grant forwarded");
                check(sent.getClipData().getItemAt(0).getUri().equals(sent.getData()), "ClipData read grant");
                check(sent.getExtras() == null, "No camera extras");
                check(sent.getComponent().getClassName().endsWith(type.startsWith("video") ? ".Video" : ".Photo"), "Resolve activity per type");
            }
            launches.clear();
            HostPhotoPagerActivity send = open();
            Intent incoming = new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://org.pixelgalleryredirect.tests/sent"));
            route(send, incoming);
            check(launches.get(0).getData().equals(incoming.getParcelableExtra(Intent.EXTRA_STREAM)), "SEND stream forwarded");
            for (Intent invalid : new Intent[]{new Intent("android.provider.action.REVIEW"),
                    new Intent().setDataAndType(Uri.parse("file:///invalid"), "image/jpeg"), request("text/plain"),
                    new Intent().setData(Uri.parse("content://missing.provider/example"))}) {
                launches.clear();
                route(open(), invalid);
                check(launches.size() == 1 && Intent.ACTION_MAIN.equals(launches.get(0).getAction()), "Invalid media falls back home");
            }
            launches.clear();
            rejectMedia = true;
            route(open(), request("image/jpeg"));
            check(launches.size() == 2 && Intent.ACTION_MAIN.equals(launches.get(1).getAction()), "Rejected VIEW falls back home");
            rejectAll = true;
            HostPhotoPagerActivity rejected = open();
            route(rejected, request("image/jpeg"));
            check(!rejected.isFinishing() && picker(rejected) != null, "Both rejected returns to picker");
            runOnMainSync(() -> picker(rejected).cancel());
            rejectAll = false;
            rejectMedia = false;
            prefs.edit().putString("package", "missing.gallery.package").commit();
            Intent media = request("image/jpeg").setClassName(getTargetContext(), HostPhotoPagerActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            HostPhotoPagerActivity missing = (HostPhotoPagerActivity) startActivitySync(media);
            waitForIdleSync();
            check(picker(missing) != null, "Missing saved package returns to picker");
            runOnMainSync(() -> picker(missing).cancel());
            check("missing.gallery.package".equals(prefs.getString("package", null)), "Failure cancel retains preference");
            HostPhotoPagerActivity ask = open();
            runOnMainSync(() -> picker(ask).getListView().performItemClick(null, 0, 0));
            waitForIdleSync();
            check(prefs.getBoolean("ask_every_time", false) && prefs.getString("package", null) == null, "Ask every time saved without a fixed package");
            for (int preview = 0; preview < 2; preview++) {
                launches.clear();
                HostPhotoPagerActivity asking = (HostPhotoPagerActivity) startActivitySync(media);
                waitForIdleSync();
                check(picker(asking) != null && launches.isEmpty(), "Each preview asks");
                select(asking);
                check(launches.size() == 1, "Ask-mode selection launches once");
                check(prefs.getBoolean("ask_every_time", false) && prefs.getString("package", null) == null, "One-time selection leaves ask mode intact");
            }
            HostPhotoPagerActivity cancelAsk = open();
            runOnMainSync(() -> picker(cancelAsk).cancel());
            check(prefs.getBoolean("ask_every_time", false), "Cancel preserves ask mode");
            select(open());
            check(!prefs.getBoolean("ask_every_time", true) && FIXTURE.equals(prefs.getString("package", null)), "Launcher gallery selection exits ask mode");
            result.putString("stream", "PASS: " + checks + " assertions\n");

        } catch (Throwable failure) {
            result.putString("stream", "FAIL after " + checks + " assertions: " + android.util.Log.getStackTraceString(failure));
            status = Activity.RESULT_CANCELED;
        } finally {
            prefs.edit().putBoolean("ask_every_time", previousAsk).commit();
            if (previous == null) prefs.edit().remove("package").commit();
            else prefs.edit().putString("package", previous).commit();
        }
        finish(status, result);
    }
}
