package com.example.plantclassificator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.InterpreterFactory;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    Uri photoURI;
    Button button, plantsList;
    String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA}, 0);

        button = (Button) findViewById(R.id.snap_button);
        button.setOnClickListener(MainActivity.this);

        plantsList =(Button) findViewById(R.id.availablePlantsButton);
        plantsList.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                startActivity(new Intent(MainActivity.this, AvailablePlantsActivity.class));
            }
        });
  }

    @Override
    public void onClick(View v) {
        Intent cameraIntent= new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {

            }
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                        BuildConfig.APPLICATION_ID + ".provider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(cameraIntent, 1888);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1888 && resultCode == RESULT_OK) {
            Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show();

            //Set ImageView with bitmap
            Bitmap bp = null;
            ImageView mImageView = findViewById(R.id.leafImageView);
            try {
                bp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);
                mImageView.setImageBitmap(bp);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //Create an imageprocessor to process the input bitmap image
            ImageProcessor imageProcessor =
                    new ImageProcessor.Builder()
                            .add(new ResizeOp(256, 256, ResizeOp.ResizeMethod.BILINEAR))
                            .build();

            //Process the image
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bp);
            tensorImage = imageProcessor.process(tensorImage);

            // Create Buffer di input
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 256, 256, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(tensorImage.getBuffer());

            // Create Buffer di output
            float[][] result = new float[1][10];

            // Model initialize
            try {
                MappedByteBuffer tfliteModel
                        = FileUtil.loadMappedFile(this,
                        "tf_lite_model.tflite");
                InterpreterApi tflite = new InterpreterFactory().create(
                        tfliteModel, new InterpreterApi.Options());

                // Run inference
                if (null != tflite) {
                    long startTime = System.currentTimeMillis();
                    tflite.run(inputFeature0.getBuffer(), result);
                    long endTime = System.currentTimeMillis();
                    long timeElapsed = endTime - startTime;
                    Log.d("MainActivityTempo", ""+timeElapsed);
                }
            } catch (IOException e) {
                Log.e("tfliteSupport", "Error reading model", e);
            }

            // Match probability output with labels
            final String ASSOCIATED_AXIS_LABELS = "labels.txt";
            List<String> associatedAxisLabels = null;
            try {
                associatedAxisLabels = FileUtil.loadLabels(this, ASSOCIATED_AXIS_LABELS);
            } catch (IOException e) {
                Log.e("tfliteSupport", "Error reading label file", e);
            }

            //Show ordered output in text view
            TextView resultsView = (TextView) findViewById(R.id.outputTextView);
            String results = "Classification results:\n";
            Map<String, Float> unsorted_map = new HashMap<String, Float>();
            ValueComparator bvc = new ValueComparator(unsorted_map);
            TreeMap<String, Float> sorted_map = new TreeMap<String, Float>(bvc);
            int i=0;
            for (Float value: result[0]) {
                unsorted_map.put(associatedAxisLabels.get(i), value*100);
                i++;
            }
            sorted_map.putAll(unsorted_map);

            int numberOfResultShown = 3;
            int count = 0;
            for(String key: sorted_map.keySet()){
                if(count < numberOfResultShown){
                    Float value = unsorted_map.get(key);
                    results = results + (count+1) +"-"+ key +" = "+ String.format("%.2f", value) + "%\n";
                }
                count++;
            }
            resultsView.setText(results);
        }
    }
}

class ValueComparator implements Comparator<String> {
    Map<String, Float> base;

    public ValueComparator(Map<String, Float> base) {
        this.base = base;
    }

    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        }
    }
}