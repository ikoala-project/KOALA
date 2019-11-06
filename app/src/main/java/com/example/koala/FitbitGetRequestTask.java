package com.example.koala;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Task for making HTTP GET requests to the Fitbit API.
 * Adapted from
 * https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
 * by HelmiB.
 */
public class FitbitGetRequestTask extends AsyncTask<String, Void, JSONObject[]> {
    public interface AsyncResponse {
        void getProcessFinish(JSONObject[] output);
    }

    private AsyncResponse delegate = null;

    protected FitbitGetRequestTask(AsyncResponse delegate){
        this.delegate = delegate;
    }

    /**
     * Requests the data from Fitbit in the background.
     * Adapted from https://stackoverflow.com/questions/12732422/adding-header-for-httpurlconnection
     * by Cyril Beschi and
     * https://alvinalexander.com/blog/post/java/how-open-url-read-contents-httpurl-connection-java
     * by Alvin Alexander.
     * @param params First parameter is access token and the following are request URLs
     * @return Data from Fitbit requests
     */
    protected JSONObject[] doInBackground(String... params) {
        String accessToken = params[0];

        // Every parameter except the first is a URL for a request for Fitbit data
        Scanner scanner = null;
        JSONObject[] jsonObjects = new JSONObject[params.length - 1];
        for(int i = 1; i < params.length; i++) { // for each request URL
            try {
                // Make request
                URL url = new URL(params[i]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                String bearerAuth = "Bearer " + accessToken;
                urlConnection.setRequestProperty("Authorization", bearerAuth);
                urlConnection.setRequestMethod("GET");

                urlConnection.setDoInput(true);
                urlConnection.setReadTimeout(5 * 1000);
                urlConnection.connect();

                // Read response
                InputStream response = urlConnection.getInputStream();

                scanner = new Scanner(response);
                String responseBody = scanner.useDelimiter("\\A").next();

                JSONObject jsonObject = new JSONObject(responseBody);
                jsonObjects[i-1] = jsonObject;

                urlConnection.disconnect();
            } catch (IOException | JSONException e) {
                jsonObjects = null;
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        }

        return jsonObjects;
    }

    /**
     * Executes the getProcessFinish method in the calling class.
     * @param result Data from Fitbit requests
     */
    @Override
    protected void onPostExecute(JSONObject[] result) { delegate.getProcessFinish(result); }
}
