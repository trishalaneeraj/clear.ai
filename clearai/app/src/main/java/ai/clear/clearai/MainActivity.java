package ai.clear.clearai;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.services.vision.v1.model.Vertex;
import com.google.common.collect.Iterables;

import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Handler;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;
import com.github.kittinunf.fuel.util.Base64;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.TextAnnotation;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.security.spec.ECField;
import java.util.ArrayList;
import java.util.Arrays;

import kotlin.Pair;

public class MainActivity extends AppCompatActivity {

    protected Vision vision;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Vision.Builder visionBuilder = new Vision.Builder(
                new NetHttpTransport(),
                new AndroidJsonFactory(),
                null);

        visionBuilder.setVisionRequestInitializer(
                new VisionRequestInitializer(getResources().getString(R.string.mykey)));

        vision = visionBuilder.build();
    }

    public final static int MY_REQUEST_CODE = 1;

    public Uri imageUri = null;

    public void takePicture(View view) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "New Picture");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, MY_REQUEST_CODE);
    }

    // TODO - How do we link different buttons in this thing?
    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {

        final String findThisString = "mint";
        if (requestCode == MY_REQUEST_CODE && resultCode == RESULT_OK) {

            Bitmap picture = null;
            try {
                picture = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            picture = Bitmap.createScaledBitmap(picture, 768, 1024, true);

            // Set the bitmap as the source of the ImageView
            ((ImageView) findViewById(R.id.previewImage)).setImageBitmap(picture);

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            picture.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            String base64Data = Base64.encodeToString(byteStream.toByteArray(), Base64.URL_SAFE);
            Image inputImage = new Image();
            inputImage.setContent(base64Data);

            Feature desiredFeature = new Feature();
            desiredFeature.setType("TEXT_DETECTION");

            AnnotateImageRequest request = new AnnotateImageRequest();
            request.setImage(inputImage);
            request.setFeatures(Arrays.asList(desiredFeature));

            final BatchAnnotateImagesRequest batchRequest =
                    new BatchAnnotateImagesRequest();

            batchRequest.setRequests(Arrays.asList(request));

            // Create new thread
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    // Convert photo to byte array
                    BatchAnnotateImagesResponse batchResponse = null;
                    try {
                        batchResponse = vision.images().annotate(batchRequest).execute();

                        final AnnotateImageResponse annotateImageResponse =
                                batchResponse.getResponses().get(0);

                        if (annotateImageResponse.size() == 0) {
                            runOnUIThingImageNO();
                            runOnUIThing("Try again - GCP could not find the text you are searching for - no response");
                        } else {

                            // We need to skip the first one as
                            ArrayList<Vertex> vertices = null;
                            for (EntityAnnotation entityAnnotation : Iterables.skip(
                                    annotateImageResponse.getTextAnnotations(), 1)) {
                                entityAnnotation.getBoundingPoly();
                                if (findThisString.equals(entityAnnotation.getDescription())) {
                                    vertices = (ArrayList<Vertex>) entityAnnotation.getBoundingPoly().getVertices();
                                    break;
                                }
                            }
                            if (vertices == null) {
                                runOnUIThingImageNO();
                                runOnUIThing("Try again - GCP could not find the text you are searching for");
                            } else {
                                runOnUIThing(batchResponse.getResponses().get(0).getFullTextAnnotation().getText());
                                runOnUIThingImageYES();
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Where's your god now?", "Annotation call failed", e);
                    }
                }
            });
        }
    }

    private void runOnUIThing(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void runOnUIThingImageYES() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ImageView) findViewById(R.id.previewImage)).setImageResource(R.raw.yes);
                }
            });
    }

    private void runOnUIThingImageNO() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((ImageView) findViewById(R.id.previewImage)).setImageResource(R.raw.no);
            }
        });
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode,
//                                    Intent data) {
//        if(requestCode == MY_REQUEST_CODE && resultCode == RESULT_OK) {
//
//            // Convert image data to bitmap
//            Bitmap picture = (Bitmap)data.getExtras().get("data");
//
//            // Set the bitmap as the source of the ImageView
//            ((ImageView)findViewById(R.id.previewImage)).setImageBitmap(picture);
//
//            // More code goes here
//            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
//            picture.compress(Bitmap.CompressFormat.JPEG, 90, byteStream);
//            String base64Data = Base64.encodeToString(byteStream.toByteArray(),
//                    Base64.URL_SAFE);
//
//            String requestURL =
//                    "https://vision.googleapis.com/v1/images:annotate?key=" +
//                            getResources().getString(R.string.mykey);
//
//            String body = "";
//            try {
//                body = getJSONString(base64Data);
//            } catch (JSONException js){
//                body = "";
//            }
//
//            Fuel.post(requestURL)
//                    .header(
//                            new Pair<String, Object>("content-length", body.length()),
//                            new Pair<String, Object>("content-type", "application/json")
//                    )
//                    .body(body.getBytes())
//                    .responseString(new Handler<String>() {
//                        @Override
//                        public void success(@NotNull Request request,
//                                            @NotNull Response response,
//                                            String data) {
//                            // Access the labelAnnotations arrays
//                            JSONArray labels = new JSONArray();
//                            try {
//                                labels = new JSONObject(data)
//                                        .getJSONArray("responses")
//                                        .getJSONObject(0)
//                                        .getJSONArray("labelAnnotations");
//                            } catch (Exception e) {
//                                Log.e("sdsa", "Explanation of what was being attempted", e);
//                            }
//
//                            String results = "";
//
//                            // Loop through the array and extract the
//                            // description key for each item
//                            for(int i=0;i<labels.length();i++) {
//                                try {
//                                    results = results +
//                                            labels.getJSONObject(i).getString("description") +
//                                            "\n";
//                                } catch (JSONException e) {
//                                    Log.e("sdsa", "Explanation of what was being attempted", e);
//                                }
//                            }
//
//                            // Display the annotations inside the TextView
//                            ((TextView)findViewById(R.id.resultsText)).setText(results);
//                        }
//
//                        @Override
//                        public void failure(@NotNull Request request,
//                                            @NotNull Response response,
//                                            @NotNull FuelError fuelError) {
//
//                            Log.e("sdsa", "Explanation of what was being attempted", fuelError);
//
//                            // Display the annotations inside the TextView
//                            ((TextView)findViewById(R.id.resultsText)).setText("You done fucked up");
//                        }
//                    });
//        }
//    }

    private String getJSONString(String base64Data) throws JSONException {
        // Create an array containing
        // the LABEL_DETECTION feature
        JSONArray features = new JSONArray();
        JSONObject feature = new JSONObject();
        feature.put("type", "LABEL_DETECTION");
        features.put(feature);

        // Create an object containing
        // the Base64-encoded image data
        JSONObject imageContent = new JSONObject();
        imageContent.put("content", base64Data);

        // Put the array and object into a single request
        // and then put the request into an array of requests
        JSONArray requests = new JSONArray();
        JSONObject request = new JSONObject();
        request.put("image", imageContent);
        request.put("features", features);
        requests.put(request);
        JSONObject postData = new JSONObject();
        postData.put("requests", requests);

        // Convert the JSON into a
        // string
        return postData.toString();
    }
}
