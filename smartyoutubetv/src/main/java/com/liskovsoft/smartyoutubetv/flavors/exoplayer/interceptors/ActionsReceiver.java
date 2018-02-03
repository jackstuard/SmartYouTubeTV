package com.liskovsoft.smartyoutubetv.flavors.exoplayer.interceptors;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.liskovsoft.browser.Browser;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.commands.GoogleConstants;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.PlayerActivity;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parser.injectors.GenericEventResourceInjector;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parser.injectors.GenericEventResourceInjector.GenericStringResultEventWithId;
import com.squareup.otto.Subscribe;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Gets real button states from the WebView
 */
public class ActionsReceiver implements Runnable {
    private static final String TAG = ActionsReceiver.class.getSimpleName();
    private static final long RESPONSE_CHECK_DELAY_MS = 3000;
    private GenericStringResultReceiver mReceiver;
    private final Context mContext;
    private final Intent mIntent;
    private final Runnable mOnDone;
    /**
     * for details see {@link com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parser.injectors.GenericEventResourceInjector}
     */
    private final String GET_STATE_COMMAND = "JSON.stringify(exoutils.getButtonStates())";
    private final String GET_DATE_COMMAND = "document.querySelector('.uploaded-date').innerHTML";
    private final int mStatesId = new Random().nextInt();
    private final int mDateId = new Random().nextInt();
    private boolean mRunOnce;

    public ActionsReceiver(Context context, Intent intent, Runnable onDone) {
        mContext = context;
        mIntent = intent;
        mOnDone = onDone;
    }

    private class GenericStringResultReceiver {
        private int mRunCount;

        public GenericStringResultReceiver() {
            Browser.getBus().register(this);
        }

        @Subscribe
        public void onGenericStringResult(GenericStringResultEventWithId event) {
            String result = event.getResult();

            if (event.getId() == mStatesId) {
                ActionsReceiver.this.processJSON(result);
                mRunCount++;
            }

            if (event.getId() == mDateId) {
                ActionsReceiver.this.processDate(result);
                mRunCount++;
            }

            if (mRunCount == 2) {
                Browser.getBus().unregister(this);
                if (checkIntent())
                    mOnDone.run();
            }
        }
    }

    /**
     * Check that user didn't tapped back key before actual playback
     * @return true if user didn't tapped back key
     */
    private boolean checkIntent() {
        if (runOnce()) {
            return false;
        }

        if (mIntent.hasExtra(PlayerActivity.BUTTON_SUBSCRIBE))
            return true;

        Log.w(TAG, "Action is cancelled. User tapped back key. Disable subsequent start of the player activity...");
        // Uncomment next section to debug
        // Toast.makeText(mContext, "Action is cancelled. Do nothing...", Toast.LENGTH_LONG).show();
        return false;
    }

    private void processDate(String result) {
        mIntent.putExtra(PlayerActivity.VIDEO_DATE, result);
    }

    /**
     * Button states in JSON format
     * @param result
     */
    private void processJSON(String result) {
        Map<String, Boolean> states = convertToObj(result);
        syncWithIntent(states);
    }

    private void syncWithIntent(Map<String, Boolean> states) {
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            mIntent.putExtra(entry.getKey(), entry.getValue());
        }
    }

    // "{'.btn-selector': true, '.btn-selector2': false}"
    private Map<String, Boolean> convertToObj(String result) {
        Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
        Gson gson = new Gson();
        return gson.fromJson(result, type);
    }

    private Map<String, String> initSelectorNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put(GoogleConstants.BUTTON_SUBSCRIBE, PlayerActivity.BUTTON_SUBSCRIBE);
        return map;
    }

    @Override
    public void run() {
        mReceiver = new GenericStringResultReceiver();
        passToBrowser(GET_STATE_COMMAND, mStatesId);
        passToBrowser(GET_DATE_COMMAND, mDateId);

        startResponseCheck();
    }

    protected void passToBrowser(String hugeFunction, int id) {
        Browser.getBus().post(new GenericEventResourceInjector.JSStringResultEvent(hugeFunction, id));
    }

    /**
     * Cast from phone fix (unable to interrupt video).
     * Force do callback if there are no response from the WebView.
     */
    private void startResponseCheck() {
        new Handler(mContext.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!runOnce())
                    mOnDone.run();
            }
        }, RESPONSE_CHECK_DELAY_MS);
    }

    private boolean runOnce() {
        boolean result = mRunOnce;
        mRunOnce = true;
        return result;
    }
}
