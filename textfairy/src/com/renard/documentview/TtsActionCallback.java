package com.renard.documentview;

import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.renard.ocr.R;
import com.renard.ocr.cropimage.MonitoredActivity;
import com.renard.ocr.help.OCRLanguageAdapter;
import com.renard.util.ResourceUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Created by renard on 05/12/13.
 */
public class TtsActionCallback implements ActionMode.Callback, TextToSpeech.OnInitListener, MonitoredActivity.LifeCycleListener {

    private final static String LOG_TAG = TtsActionCallback.class.getSimpleName();


    private TextToSpeech mTts;
    private final DocumentActivity activity;
    private boolean mTtsReady = false;
    private ActionMode mActionMode;
    final Map<String, String> hashMapResource;

    TtsActionCallback(DocumentActivity activity) {
        hashMapResource = ResourceUtils.getHashMapResource(activity, R.xml.iso_639_mapping);
        this.activity = activity;
        this.activity.addLifeCycleListener(this);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mActionMode = actionMode;
        activity.getSupportMenuInflater().inflate(R.menu.tts_action_mode, menu);
        if (mTts == null) {
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            activity.startActivityForResult(checkIntent, DocumentActivity.REQUEST_CODE_TTS_CHECK);
        }
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        if (mTtsReady) {
            //show play and stop button
            menu.findItem(R.id.item_play).setVisible(true);
            menu.findItem(R.id.item_tts_settings).setVisible(true);
        } else {
            activity.setSupportProgressBarIndeterminateVisibility(true);
            menu.findItem(R.id.item_play).setVisible(false);
            menu.findItem(R.id.item_stop).setVisible(false);
            menu.findItem(R.id.item_tts_settings).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.item_play:
                int result = mTts.speak(activity.getPlainDocumentText(), TextToSpeech.QUEUE_FLUSH, null);
                actionMode.getMenu().findItem(R.id.item_play).setVisible(false);
                actionMode.getMenu().findItem(R.id.item_stop).setVisible(true);
                break;
            case R.id.item_stop:
                stopPlaying(actionMode);
                break;
            case R.id.item_tts_settings:
                stopPlaying(actionMode);
                askForLocale();
                break;
        }
        return false;
    }

    private void stopPlaying(ActionMode actionMode) {
        mTts.stop();
        actionMode.getMenu().findItem(R.id.item_play).setVisible(true);
        actionMode.getMenu().findItem(R.id.item_stop).setVisible(false);
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        if (mTtsReady && mTts != null) {
            mTts.stop();
        }
    }

    @Override
    public void onInit(int status) {
        activity.setSupportProgressBarIndeterminateVisibility(false);
        // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
        if (status == TextToSpeech.ERROR) {
            Log.e(LOG_TAG, "Could not initialize TextToSpeech.");
            Toast.makeText(activity, R.string.tts_init_error, Toast.LENGTH_LONG).show();
            mActionMode.finish();
        } else {
            mActionMode.getMenu().findItem(R.id.item_tts_settings).setVisible(true);
            String ocrLanguage = activity.getLanguageOfDocument();
            Locale documentLocale = mapTesseractLanguageToLocale(ocrLanguage);
            if (documentLocale==null){
                askForLocale(ocrLanguage, true);
            }else {
                if(isLanguageAvailable(new OCRLanguageAdapter.OCRLanguage(ocrLanguage, null,true,0))){
                    mTts.setLanguage(documentLocale);
                    mActionMode.getMenu().findItem(R.id.item_play).setVisible(true);
                    mTtsReady = true;
                } else {
                    askForLocale(ocrLanguage,true);
                }
            }
        }
    }


    private void askForLocale(final String documentLanguage, boolean languageSupported) {

        NoTtsLanguageDialog.newInstance(documentLanguage,languageSupported, activity).show(activity.getSupportFragmentManager(), NoTtsLanguageDialog.TAG);
    }

    private void askForLocale() {
        NoTtsLanguageDialog.newInstance(activity).show(activity.getSupportFragmentManager(), NoTtsLanguageDialog.TAG);
    }
    /**
     * user has picked a language for tts
     * @param lang
     */
    public void onTtsLanguageChosen(OCRLanguageAdapter.OCRLanguage lang) {
        Locale documentLocale = mapTesseractLanguageToLocale(lang.getValue());
        int result = mTts.setLanguage(documentLocale);
        switch (result){
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
            case TextToSpeech.LANG_AVAILABLE:
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                Log.i(LOG_TAG,"language ok");
                break;
            default:
                Log.i(LOG_TAG,"language not supported");
                break;
        }
        mActionMode.getMenu().findItem(R.id.item_play).setVisible(true);
        mActionMode.getMenu().findItem(R.id.item_stop).setVisible(false);
        mTtsReady = true;
    }

    public void onTtsCancelled() {
        if (mTts!=null){
            mTts.shutdown();
            mTts = null;
        }
        mTtsReady=false;
        mActionMode.finish();
    }

    public boolean isLanguageAvailable(OCRLanguageAdapter.OCRLanguage lang) {
        if (mTts!=null){
            final Locale locale = mapTesseractLanguageToLocale(lang.getValue());
            if (locale==null){
                return false;
            }
            Log.i(LOG_TAG,"Checking " + locale.toString());
            final int result = mTts.isLanguageAvailable(locale);
            switch(result){
                case TextToSpeech.LANG_NOT_SUPPORTED:
                    Log.i(LOG_TAG,"LANG_NOT_SUPPORTED");
                    return false;
                case TextToSpeech.LANG_MISSING_DATA:
                    Log.i(LOG_TAG,"LANG_MISSING_DATA");
                    return false;
                case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                    Log.w(LOG_TAG, "LANG_COUNTRY_AVAILABLE");
                    mTtsReady = true;
                    return true;
                case TextToSpeech.LANG_AVAILABLE:
                    Log.w(LOG_TAG, "LANG_AVAILABLE");
                    mTtsReady = true;
                    return true;
                default:
                    return true;
            }
        }
        return false;
    }


    private Locale mapTesseractLanguageToLocale(String ocrLanguage) {
        final String s = hashMapResource.get(ocrLanguage);
        if (s!=null){
            return new Locale(s);
        } else {
            return null;
        }
    }

    void onTtsCheck(int resultCode) {

        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // success, create the TTS instance
            mTts = new TextToSpeech(activity, this);
        } else {
            mActionMode.finish();
            // missing data, install it
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            activity.startActivity(installIntent);
        }

    }

    @Override
    public void onActivityCreated(MonitoredActivity activity) {

    }

    @Override
    public void onActivityDestroyed(MonitoredActivity activity) {
        if (mTtsReady == true) {
            mTts.shutdown();
            mTtsReady = false;
            mTts = null;
        }
    }

    @Override
    public void onActivityPaused(MonitoredActivity activity) {

    }

    @Override
    public void onActivityResumed(MonitoredActivity activity) {

    }

    @Override
    public void onActivityStarted(MonitoredActivity activity) {

    }

    @Override
    public void onActivityStopped(MonitoredActivity activity) {

    }
}
