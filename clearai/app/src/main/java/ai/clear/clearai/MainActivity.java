package ai.clear.clearai;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.api.services.vision.v1.model.Vertex;
import com.google.common.collect.Iterables;

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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.CYAN;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.MAGENTA;
import static android.graphics.Color.RED;

public class MainActivity extends AppCompatActivity {

    public static final boolean faked_for_video = false;

    protected Vision vision;

    // One global imageUri
    public Uri imageUri = null;

    public final static int IMG_REQUEST_CODE = 1;
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public Bitmap picture = null;

    private int system_state = 0;

    String findThisString = "mint";
    String findThisString_second = "salted";

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

    public void takePictureCall() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "New Picture");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra("android.intent.extra.quickCapture",true);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, IMG_REQUEST_CODE);
    }

    public void takePicture(View view) {
        takePictureCall();
    }

    public void record(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Which Product?");
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);

//        Button tiny = findViewById(R.id.recordButton);
//        if (system_state == 0) {
//            // Recording
//            tiny.setBackgroundColor(Color.parseColor("#ff99cc00"));
//            system_state = 1;
//        }
//        else {
//            // Ready to record
//            tiny.setBackgroundColor(Color.parseColor("#ff669900"));
//            system_state = 0;
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {

            if (data != null) {
                ArrayList matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                Log.i((String) matches.get(0), (String) matches.get(0) + " " + (String) matches.get(1));
                if (matches.size() > 1) {
                    findThisString = ((String) matches.get(0)).toLowerCase();
                } else {
                    findThisString = "scaf weij fnjkcnwdnc uv";
                }

                if (matches.size() > 2) {
                    findThisString_second = ((String) matches.get(1)).toLowerCase();
                } else {
                    findThisString_second = "scaf weij fnjkcnw dncuv";
                }
            }
            takePictureCall();
        }

        if (requestCode == IMG_REQUEST_CODE && resultCode == RESULT_OK) {

            try {
                picture = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            picture = Bitmap.createScaledBitmap(picture, 200, 275, true);

            // Set the bitmap as the source of the ImageView
            ((ImageView) findViewById(R.id.previewImage)).setImageBitmap(picture);

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            picture.compress(Bitmap.CompressFormat.PNG, 100, byteStream);

            // Google processing begins
            String base64Data = Base64.encodeToString(byteStream.toByteArray(), Base64.URL_SAFE);
            Image inputImage = new Image();
            inputImage.setContent(base64Data);

            Feature desiredFeature = new Feature();
            desiredFeature.setType("TEXT_DETECTION");

            AnnotateImageRequest request = new AnnotateImageRequest();
            request.setImage(inputImage);
            request.setFeatures(Arrays.asList(desiredFeature));

            final BatchAnnotateImagesRequest batchRequest = new BatchAnnotateImagesRequest();
            batchRequest.setRequests(Arrays.asList(request));

            // Create new thread
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    // Convert photo to byte array
                    BatchAnnotateImagesResponse batchResponse = null;
                    try {

                        if (faked_for_video) {
                            Thread.sleep(2000);
                            if (findThisString.toLowerCase().equals("hi")) {
                                runOnUIThingImageYES();
                            } else {
                                runOnUIThingImageNO();
                                runOnUIThing("The AI could not find the text you are searching for.");
                            }
                        }
                        else {
                            batchResponse = vision.images().annotate(batchRequest).execute();
                            final AnnotateImageResponse annotateImageResponse =
                                    batchResponse.getResponses().get(0);

                            if (annotateImageResponse.size() == 0) {
                                runOnUIThingImageNO();
                                runOnUIThing("The AI could not find the text you are searching for.");
                            } else {

                                // We need to skip the first one as
                                String found_text = null;

                                String text_annotation = annotateImageResponse.getFullTextAnnotation().getText().toLowerCase();
                                text_annotation = text_annotation.replace("\n", " ");
                                if (text_annotation.contains(findThisString)) {
                                    found_text = annotateImageResponse.getFullTextAnnotation().getText();
                                }
                                if (text_annotation.contains(findThisString_second)) {
                                    found_text = annotateImageResponse.getFullTextAnnotation().getText();
                                }

                                if (found_text == null) {
                                    runOnUIThingImageNO();
                                    runOnUIThing("The AI could not find the text you are searching for.");
                                } else {
                                    runOnUIThing(batchResponse.getResponses().get(0).getFullTextAnnotation().getText());
                                    runOnUIThingImageYES();
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Where's your god now?", "Annotation call failed", e);
                    }
                }
            });
        }
    }

    private void runOnUIThingImageNO() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((ImageView) findViewById(R.id.previewImage)).setImageResource(R.raw.no_2);
            }
        });
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
        final ImageView iv = findViewById(R.id.previewImage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                iv.setImageResource(R.raw.yes_2);

                Runnable r = new Runnable(){
                    public void run(){
                        // Set the bitmap as the source of the ImageView
                        iv.setImageBitmap(picture);
                    }
                };
                iv.postDelayed(r,3000);
            }
        });
    }

    // Not used presently
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
