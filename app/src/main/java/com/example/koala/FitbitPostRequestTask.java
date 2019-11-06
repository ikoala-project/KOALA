package com.example.koala;

import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Task for making an HTTP POST request to the Fitbit API.
 * Adapted from
 * https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
 * by HelmiB.
 */
public class FitbitPostRequestTask extends AsyncTask<String, Void, JSONObject> {
    public interface AsyncResponse {
        void postProcessFinish(JSONObject output);
    }

    private FitbitPostRequestTask.AsyncResponse delegate = null;

    protected FitbitPostRequestTask(FitbitPostRequestTask.AsyncResponse delegate){
        this.delegate = delegate;
    }

    /**
     * Makes the request to Fitbit in the background.
     * Adapted from https://stackoverflow.com/questions/12732422/adding-header-for-httpurlconnection
     * by Cyril Beschi and
     * https://alvinalexander.com/blog/post/java/how-open-url-read-contents-httpurl-connection-java
     * by Alvin Alexander.
     * @param params First is access token, second is request URL, subsequent parameters are
     *               request parameters
     * @return Data from Fitbit request
     */
    protected JSONObject doInBackground(String... params) {
        String accessToken = params[0];
        String requestUrl = params[1];

        // Format request parameters into a string
        String requestParams = "";
        for(int i = 2; i < params.length; i++) {
            requestParams += params[i];
            if(i < params.length - 1) {
                requestParams += "&";
            }
        }

        Scanner scanner = null;
        JSONObject jsonObject = null;
        try {
            // Make request
            URL url = new URL(requestUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            String bearerAuth = "Bearer " + accessToken;
            urlConnection.setRequestProperty("Authorization", bearerAuth);
            urlConnection.setRequestMethod("POST");

            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setReadTimeout(5 * 1000);
            urlConnection.connect();

            // Write data
            // Adapted from
            // https://stackoverflow.com/questions/4205980/java-sending-http-parameters-via-post-method-easily
            // by Alan Geleynse
            byte[] postData = requestParams.getBytes( StandardCharsets.UTF_8 );
            DataOutputStream wr = new DataOutputStream( urlConnection.getOutputStream());
            wr.write(postData);

            // Read response
            InputStream response = urlConnection.getInputStream();

            scanner = new Scanner(response);
            String responseBody = scanner.useDelimiter("\\A").next();

            jsonObject = new JSONObject(responseBody);

            urlConnection.disconnect();
        } catch (IOException | JSONException e) {
            jsonObject = null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        return jsonObject;
    }

    /**
     * Executes the postProcessFinish method in the calling class.
     * @param result Data from Fitbit request
     */
    @Override
    protected void onPostExecute(JSONObject result) {
        delegate.postProcessFinish(result);
    }
}
