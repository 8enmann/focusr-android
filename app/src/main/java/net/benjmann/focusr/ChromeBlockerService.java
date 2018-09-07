package net.benjmann.focusr;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChromeBlockerService extends AccessibilityService {

    // TODO: serialize this to save across phone restarts.
    private static long timeMillis;
    private static long lastStart;
    // Used to track 24 hours. Make this configurable.
    private static long firstStart;
    private static boolean isRunning;
    private static Set<String> apps;
    private static long appsLastUpdated;
    // Lazy load settings updates every 30 seconds.
    private static final int REFRESH_INTERVAL_MILLIS = 30 * 1000;
    private static HashMap<String, Long> lastPreferenceCheck = new HashMap<>();
    private static HashMap<String, Float> lastPreferenceValue = new HashMap<>();

    private float getPreferenceLazy(String key, float def) {
        Long lastCheck = lastPreferenceCheck.get(key);
        if (lastCheck == null) {
            lastCheck = 0L;
        }
        float value = def;
        if (System.currentTimeMillis() - lastCheck > REFRESH_INTERVAL_MILLIS) {
            lastPreferenceCheck.put(key, System.currentTimeMillis());

            try {
                value = Float.parseFloat(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(key, Float.toString(def)));
            } catch (Exception e) {
                e.printStackTrace();
            }
            lastPreferenceValue.put(key, value);
        } else {
            return lastPreferenceValue.get(key);
        }
        return value;
    }

    long getTimeSpentMillis() {
        return timeMillis;
    }

    @Override
    public void onInterrupt() {
        Log.i(getPackageName(), "Interrupt");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(getPackageName(), "Connected");
    }

    private static String eventToString(AccessibilityEvent event) {
        CharSequence[] fields = {
                event.getPackageName(),
                event.getContentDescription(),
                event.getClassName(),
                AccessibilityEvent.eventTypeToString(event.getEventType()),
                Integer.valueOf(event.getEventType()).toString(),
        };
        return TextUtils.join(" ", fields);
    }


    synchronized private Set<String> getPackagesToTrack() {
        if (System.currentTimeMillis() - appsLastUpdated > REFRESH_INTERVAL_MILLIS) {
            appsLastUpdated = System.currentTimeMillis();
            apps = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getStringSet("apps", new HashSet<String>());
        }
        return apps;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        String eventText = null;
        CharSequence packageName = event.getPackageName();
        if (packageName == null || packageName.toString().isEmpty()) {
            Log.i(getPackageName(), event.getClassName() + " " + AccessibilityEvent.eventTypeToString(event.getEventType()));
            // Ignore these events since they don't seem to trigger on anything important.
            return;
        }

        if (getPackagesToTrack().contains(packageName.toString())) {
            startTimer(event.getEventTime());
            return;
        }
        if (!packageName.equals("com.android.chrome")) {
            stopTimer(event.getEventTime(), packageName.toString());
        }

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            return;
        }
        // TODO: support other browsers?
        List<AccessibilityNodeInfo> nodes = source.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar");
        for (AccessibilityNodeInfo node : nodes) {
            String original = node.getText().toString();
            String domain = getDomainName(original);
            Log.i(getPackageName(), domain);
            if (domain.endsWith("facebook.com")) {
                startTimer(event.getEventTime());
            } else if (domain.trim().isEmpty()) {
                Log.i(getPackageName(), "Skipping " + original);
            } else {
                stopTimer(event.getEventTime(), domain);
            }
            node.recycle();
        }
    }

    private void startTimer(long eventTime) {
        Log.i(getPackageName(), "start " + isRunning);
        // Reset every 24 hours
        if ((eventTime - firstStart) > 24 * 60 * 60 * 1000) {
            Log.i(getPackageName(), "init");
            firstStart = eventTime;
            timeMillis = 0;
        }
        if (isRunning) {
            // If it's been more than a minute since the last start, toast anyway
            if (eventTime - lastStart > 1000 * 60 * getPreferenceLazy("progress_interval", 1)) {
                timeMillis += eventTime - lastStart;
                lastStart = eventTime;
                showTime(timeMillis);
            }
            return;
        }
        isRunning = true;
        lastStart = eventTime;
        showTime(timeMillis);

    }

    public static String getDomainName(String url)  {
        // URI barfs if there's no scheme.
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return "";
        }
        String domain = uri.getHost();
        if (domain == null) {
            Log.w(ChromeBlockerService.class.getPackage().toString(), url);
            return "";
        }
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private void stopTimer(long eventTime, String reason) {
        if (reason.trim().isEmpty()) {
            reason = "empty";
        }
        Log.i(getPackageName(), "stop " + isRunning + " " + reason);
        if (!isRunning) {
            return;
        }
        isRunning = false;
        timeMillis += eventTime - lastStart;
        showTime(timeMillis);
    }

    private void showTime(long millis) {
        Log.i(getPackageName(), millis + " " + isRunning + " " + lastStart);
        double mins = getPreferenceLazy("total_time_per_day", 10) - (millis / 1000.0 / 60);

        String message = String.format("%.1f mins remaining", mins);
        if (mins < 0) {
            message = "Time's up!";
            // TODO: consider kicking them back to home screen.
            // this.performGlobalAction(GLOBAL_ACTION_HOME);
        }
        Toast.makeText(
                this.getApplicationContext(),
                message,
                Toast.LENGTH_SHORT).show();
    }
}