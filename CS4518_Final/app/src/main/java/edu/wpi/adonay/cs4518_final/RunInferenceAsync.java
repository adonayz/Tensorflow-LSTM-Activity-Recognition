package edu.wpi.adonay.cs4518_final;

import android.content.Context;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import com.google.gson.Gson;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class RunInferenceAsync extends AsyncTask<float[], Float, float[]>{
    private final static String TAG = "RunInferenceAsync";

    private String resultMessage = "Nothing happened";
    private OkHttpClient client;
    private ArrayList<WeakReference<TextView>> weakProbTextViews;
    private long startTime;
    private float[] results;
    private String[] labels = {"Downstairs", "Jogging", "Sitting", "Standing", "Upstairs", "Walking"};
    private boolean isRemote;
    private TensorFlowClassifier classifier;
    private OnInferenceCompleted inferenceCompletedListener;

    //  private static final String hosturl = "http://10.0.2.2:54321/predict"; // on local server w emulator
    //  private static final String hosturl = "http://192.168.1.4:54321/predict"; // on local server w usb phone
    private static final String hosturl = "http://35.243.255.62:54321/predict"; // on remote server

    RunInferenceAsync(MainActivity mainActivity, TextView[] probTextViewArray, long startTime, boolean isRemote){
        Context context = mainActivity.getApplicationContext();
        this.inferenceCompletedListener = mainActivity;
        this.weakProbTextViews = new ArrayList<>();
        for(TextView textView: probTextViewArray){
            this.weakProbTextViews.add(new WeakReference<>(textView));
        }
        this.startTime = startTime;
        this.isRemote = isRemote;
        if (!this.isRemote){
            classifier = new TensorFlowClassifier(context);
        }
    }

    protected void onPreExecute() {
        if(isRemote){
            final int timeout = 30;
            client = new OkHttpClient();
            client.setConnectTimeout(timeout, TimeUnit.SECONDS);
            client.setWriteTimeout(timeout,TimeUnit.SECONDS);
            client.setReadTimeout(timeout,TimeUnit.SECONDS);
        }
    }

    protected float[] doInBackground(float[]... sensorData) {
        if(!isRemote){
            results = classifier.predictProbabilities(sensorData[0]);
        }else{
            Gson gson = new Gson();
            String jsonSensorData = gson.toJson(sensorData);

            RequestBody requestBody = new MultipartBuilder()
                    .type(MultipartBuilder.FORM)
                    .addFormDataPart("x", jsonSensorData)
                    .build();

            Request request = new Request.Builder()
                    .url(hosturl)
                    .post(requestBody)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                resultMessage = response.body().string();
            } catch (IOException e) {
                Log.d(TAG, e.toString());
            }

            String stringProbabilities = "";

            try {
                JSONObject jsonObject = new JSONObject(resultMessage);
                stringProbabilities = jsonObject.getString("y");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            stringProbabilities = stringProbabilities.substring(2, stringProbabilities.length()-2);

            String[] splitMessage = stringProbabilities.split(",");
            results = new float[splitMessage.length];

            for(int i = 0; i < splitMessage.length; i++){
                results[i] = Float.parseFloat(splitMessage[i]);
            }
        }

        return results;
    }

    protected void onPostExecute(float[] results){
        long elapsedTime = SystemClock.uptimeMillis() - startTime;
        for(int i = 0; i < weakProbTextViews.size(); i++){
            TextView textView = weakProbTextViews.get(i).get();
            if(textView!=null){
                textView.setText(Float.toString(round(results[i], 2)));
            }
        }
        int highestIndex = getHighestIndex();

        inferenceCompletedListener.onInferenceCompleted(results, labels[highestIndex], results[highestIndex], elapsedTime);
    }

    private static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    private int getHighestIndex(){
        if (results == null || results.length == 0) {
            return -1;
        }
        float max = -1;
        int idx = -1;
        for (int i = 0; i < results.length; i++) {
            if (results[i] > max) {
                idx = i;
                max = results[i];
            }
        }

        return idx;
    }
}
