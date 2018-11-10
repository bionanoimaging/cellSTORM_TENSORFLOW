package de.cellstorm.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import de.cellstorm.R;

import static org.opencv.core.Core.NORM_MINMAX;
import static org.opencv.core.Core.divide;
import static org.opencv.core.Core.normalize;
import static org.opencv.imgcodecs.Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.imgproc.Imgproc.cvtColor;

;

/**
 * Created by Bene on 26.09.2015.
 */

public class LocalizerActivity extends Activity {
    // Flag for debug - if true => save images to disk slows down!
    boolean debug = false;


    String TAG = "TFcellSTORM";
    //********************************************************************************************** 
    // TENSORFLOW STUFF 
    // ********************************************************************************************** 

    //private static final String MODEL_FILE = "file:///android_asset/expert-graph_2D_128px.pb";
    File extStore = Environment.getExternalStorageDirectory();
    //private final String MODEL_FILE = String.valueOf(extStore)+"/cellstorm/TF/expert-graph_2D_128px.pb"; //expert-graph_CN.pb
    // private final String MODEL_FILE = String.valueOf(extStore)+"/cellstorm/frozen_model.pb"; //
    // String MODEL_FILE = "/storage/emulated/0/cellstorm/frozen_model.pb";
    String MODEL_FILE = "file:///android_asset/frozen_model.pb";
    //String MODEL_FILE = "file:///android_asset/first-graph.pb"; // name of python protobuffer file 
    String INPUT_NODE = "Placeholder:0";                                // name of python input node  // prefix/
    String OUTPUT_NODE = "outputs:0";                              // name of python output node 
    long[] INPUT_SIZE = {1,256,256,1};                            // Size of input RGB image
    long[] OUTPUT_SIZE = {1,256,256,1};                            // Size of input RGB image


    // determine safe-path for frames
    String mypath = "/cellstorm/";
    String myvideofile = "stack.mp4";
    // Some GUI parameters
    TextView textViewIteration;
    TextView textViewNumFrames;
    TextView textViewFirstFrame;
    ImageView imageViewCellstormResult;
    CheckBox checkBoxDebug;

    int num_frames_iter = 1000;
    int num_frame_first = 0;

    // read one cellSTORM frame
    String image_path = Environment.getExternalStorageDirectory().getAbsolutePath()+mypath;

    // Final Result
    Bitmap cellSTORM_result = null;

    // initiialize video-reader
    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    MediaExtractor extractor = new MediaExtractor();

    // get video parameters
    long video_duration = -1;
    long video_width = -1;
    long video_heigth = -1;
    long video_framerate = 1;
    float video_frame_duration = -1;

    // center-coordinates of crop-region
    int center_w = -1;
    int center_h = -1;

    Bitmap global_bitmap = null;

    // create Tensorflow Object 
    TensorFlowInferenceInterface inferenceInterface;


    //**********************************************************************************************
    //  Method OnCreate
    //**********************************************************************************************


    public LocalizerActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localize);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // get result from mypath
        Intent intent = getIntent();
        String my_file_path = intent.getExtras().getString("my_file_path");

        // Set GUI handles
        textViewIteration = (TextView)findViewById(R.id.textViewIteration);
        imageViewCellstormResult = (ImageView) findViewById(R.id.imageViewCellstormResult);
        textViewNumFrames = (TextView)findViewById(R.id.editTextNumFrames);
        textViewFirstFrame = (TextView)findViewById(R.id.editTextFirstFrame);
        checkBoxDebug = (CheckBox)findViewById(R.id.checkBoxDebug);
        checkBoxDebug.setOnClickListener(checkboxClickListener);
        // Load native libraries
        //System.loadLibrary("native_microscope");
        System.loadLibrary("tensorflow_inference");
        System.loadLibrary("opencv_java3");

        // initialize Tensorflow object
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);


        // Create dir if not existent
        File myfolder = new File(image_path);
        boolean success = false;
        if(!myfolder.exists()){
            success = myfolder.mkdir();
        }
        if (!success){
            Log.d(TAG,"Folder not created.");
        }
        else{
            Log.d(TAG,"Folder created!");
        }

        // VIDEO-STUFF -------
        // read video and video-information
        mediaMetadataRetriever.setDataSource(my_file_path);

        // get video parameters
        video_duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        video_width = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        video_heigth = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));


        // get framerate
        video_framerate = 24; //may be default
        try {
            //Adjust data source as per the requirement if file, URI, etc.
            extractor.setDataSource(my_file_path);
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; ++i) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        video_framerate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            //Release stuff
            extractor.release();
        }

        video_frame_duration = (float) ((1./(float)video_framerate)*10000000.);

        // read the first frame of the video and set it to the image viewer
        Bitmap current_frame = mediaMetadataRetriever.getFrameAtTime(0,  MediaMetadataRetriever.OPTION_CLOSEST_SYNC); //unit in microsecond
        global_bitmap = current_frame;

        // global bitmap for displaying purposes
        imageViewCellstormResult.setImageBitmap(global_bitmap);

        // Interact with the user
        textViewIteration.setText("Please select the ROI of the image (64x64)");

        // select ROI for STORM analysis
        imageViewCellstormResult.setOnTouchListener(new View.OnTouchListener() {
            Matrix inverse = new Matrix();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){

                    if(center_h==-1){

                        // get the number of frames which was set by the user
                        num_frames_iter = Integer.valueOf(textViewNumFrames.getText().toString());
                        num_frame_first = Integer.valueOf(textViewFirstFrame.getText().toString());
                        
                        imageViewCellstormResult.getImageMatrix().invert(inverse);
                        float[] pts = {event.getX(), event.getY()};
                        inverse.mapPoints(pts);

                        // might be confusing, but width and height are exchanged
                        float[] coords_xy = getPointerCoords(imageViewCellstormResult, event);
                        center_w = (int)Math.floor(pts[0]);
                        center_h = (int)Math.floor(pts[1]);

                        textViewIteration.setText("Touch coordinates : " +String.valueOf(event.getX()) + "x" + String.valueOf(event.getY()));
                        Log.i(TAG, "onTouch x: " + Math.floor(pts[0]) + ", y: " + Math.floor(pts[1]));
                        new runProcess().execute();


                    }


                }
                return true;
            }
        });





    }


    //**********************************************************************************************
    //  Method OnPause
    //**********************************************************************************************
    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");
        

    }


    //**********************************************************************************************
    //  Method OnDestroy
    //**********************************************************************************************
    public void onDestroy() {
        super.onDestroy();
    }


    //**********************************************************************************************
    //  Method OnStart
    //**********************************************************************************************
    @Override
    public void onStart() {
        super.onStart();
    }


    //**********************************************************************************************
    //  Method OnStop
    //**********************************************************************************************
    @Override
    public void onStop() {
        super.onStop();
    }


    private class runProcess extends AsyncTask<Void, Void, Void> {



        int iter = 0;
        


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Void... params) {
            textViewIteration.setText("Processing frame: "+String.valueOf(iter+1)+"/"+String.valueOf(num_frames_iter-num_frame_first)+" @ "+"Touch coordinates (width/height): " +String.valueOf(center_w) + "x" + String.valueOf(center_h));
            imageViewCellstormResult.setImageBitmap(global_bitmap);
            //imageViewCellstormResult.setAdjustViewBounds(scaleType.centerInside);
        }

        @Override
            protected Void doInBackground(Void... params) {

            // set parameters
            Size dst_size = new Size(256, 256);
            Size src_size = new Size(64, 64);

            // allocate memory/adress for MAT
            Mat cellstorm_frame_raw = new Mat();
            Mat cellstorm_frame = new Mat();
            Mat cellstorm_frame_result = new Mat();
            Mat cellstorm_frame_iter = Mat.zeros(dst_size, CvType.CV_8UC1);

            Mat cellstorm_sum_result = new Mat();
            Mat cellstorm_sum_result_brightfield = new Mat();
            cellstorm_sum_result = Mat.zeros(dst_size, CvType.CV_32FC1);
            cellstorm_sum_result_brightfield = Mat.zeros(dst_size, CvType.CV_32FC1);

            // define ouput Data to store result
            float[] TF_output;
            float[] TF_input = new float[(int)(OUTPUT_SIZE[0]*OUTPUT_SIZE[1]*OUTPUT_SIZE[2]*OUTPUT_SIZE[3])];

            // Need to convert TestMat to float array to feed into TF
            MatOfFloat TF_input_f = new MatOfFloat(CvType.CV_32F);
            Mat TF_output_f = new Mat(dst_size, CvType.CV_32F);

            // create the Bitmap for intermediate results
            cellSTORM_result = Bitmap.createBitmap(cellstorm_sum_result.cols(), cellstorm_sum_result.rows(), Bitmap.Config.ARGB_8888);

            // print the properties for debugging
            Log.i(TAG, "VIDEO_Width/Height: "+String.valueOf(video_width)+"/"+String.valueOf(video_heigth)+", VIDEO_FRAMERATE: "+String.valueOf(video_framerate)+", VIDEO_DURATION (us): "+String.valueOf(video_duration*1000)+", VIDEO_FRAME_DURATION (us): "+String.valueOf(video_frame_duration));

            Log.i(TAG, "Start");
            for(iter = num_frame_first; iter< num_frames_iter+num_frame_first; iter++) {

                long video_time_position = (long)((float)iter*video_frame_duration);
                Log.i(TAG, "Videoframe-position: "+String.valueOf(iter)+", Time: (us)"+String.valueOf(video_time_position));

                // get first frame of the video
                Bitmap current_frame = mediaMetadataRetriever.getFrameAtTime(video_time_position,  MediaMetadataRetriever.OPTION_CLOSEST_SYNC); //unit in microsecond

                // Bitmap current_frame = mmr.getFrameAtTime(video_time_position, FFmpegMediaMetadataRetriever.OPTION_CLOSEST); // frame at XX museconds

                if(debug) imwriteNorm(current_frame, image_path+"image_orig_bitmap"+String.valueOf(iter)+".png");

                // crop the image around the center with 64x64 in dimension
                //current_frame = ThumbnailUtils.extractThumbnail(current_frame, (int)src_size.width, (int)src_size.height);

                if(true){
                    Utils.bitmapToMat(current_frame, cellstorm_frame_raw);
                    cvtColor(cellstorm_frame_raw , cellstorm_frame_raw , Imgproc.COLOR_RGB2GRAY);
                    Rect roi = new Rect(center_w, center_h, (int)src_size.width, (int)src_size.height);
                    cellstorm_frame = new Mat(cellstorm_frame_raw, roi);
                }
                else{ // only for debugging purposes
                    cellstorm_frame_raw = imread(image_path+"image.png", CV_LOAD_IMAGE_GRAYSCALE);
                    Log.i(TAG, String.valueOf(cellstorm_frame_raw));
                }
                // resize the image to designated size (256x256)
                if(debug) imwriteNorm(cellstorm_frame, image_path+"image_orig"+String.valueOf(iter)+".png");
                Imgproc.resize(cellstorm_frame, cellstorm_frame, dst_size);//, 0., 0., Imgproc.INTER_NEAREST);



                // accumulate the result of all frames
                cellstorm_frame.convertTo(cellstorm_frame, CvType.CV_32FC1);
                Core.add(cellstorm_sum_result_brightfield, cellstorm_frame, cellstorm_sum_result_brightfield);
                if(debug) imwriteNorm(cellstorm_frame, image_path+"image_orig_resize"+String.valueOf(iter)+".png");


                // preprocess the frame
                cellstorm_frame = preprocess(cellstorm_frame);

                // convert MAT to MatOfFloat
                cellstorm_frame.convertTo(TF_input_f,CvType.CV_32F);

                // get the frame/image and allocate it in the MOF object
                TF_input_f.get(0, 0, TF_input);

                // define ouput Data to store result
                TF_output = new float[(int) (OUTPUT_SIZE[0]*OUTPUT_SIZE[1]*OUTPUT_SIZE[2]*OUTPUT_SIZE[3])];

                // feed the data to the input node
                inferenceInterface.feed(INPUT_NODE, TF_input, INPUT_SIZE);
                // run inference model in native code
                inferenceInterface.run(new String[] {OUTPUT_NODE});
                // fetch the result from the node
                inferenceInterface.fetch(OUTPUT_NODE,TF_output);

                if(debug) Log.i(TAG, " I processed the " + String.valueOf(iter) + "-th frame!");

                // convert array-buffer back to MAT
                TF_output_f.put(0, 0, TF_output);

                // convert MAT to MatOfFloat
                TF_output_f.convertTo(cellstorm_frame_result,CvType.CV_32F);
                if(debug) Log.i(TAG, String.valueOf(cellstorm_frame_result));
                if(debug) Log.i(TAG, String.valueOf(TF_output_f));
                if(debug) Log.i(TAG, "Arraysize: "+ String.valueOf(TF_output.length));

                // deprocess the MAT and save it
                cellstorm_frame_result = deprocess(cellstorm_frame_result);
                if(true) imwriteNorm(cellstorm_frame_result, image_path+"image_result"+String.valueOf(iter)+".png");

                // accumulate the result of all frames
                Core.add(cellstorm_sum_result, cellstorm_frame_result, cellstorm_sum_result);

                // display result
                if(debug){
                    if ((iter%1) == 2) cellstorm_frame_result.copyTo(cellstorm_frame_iter);
                    else cellstorm_frame.copyTo(cellstorm_frame_iter);

                    normalize(cellstorm_frame_iter, cellstorm_frame_iter, 0, 255, NORM_MINMAX);
                    cellstorm_frame_iter.convertTo(cellstorm_frame_iter, CvType.CV_8UC1);
                    Log.i(TAG, String.valueOf(cellstorm_frame_iter));
                    Utils.matToBitmap(cellstorm_frame_iter, cellSTORM_result);
                    global_bitmap = cellSTORM_result;
                    //cellstorm_frame_iter.release();
                }
                else{
                    // display the summed resut every 10th iteration
                        if ((iter%10) == 1) {
                            cellstorm_sum_result.copyTo(cellstorm_frame_iter);
                            normalize(cellstorm_frame_iter, cellstorm_frame_iter, 0, 255, NORM_MINMAX);
                            cellstorm_frame_iter.convertTo(cellstorm_frame_iter, CvType.CV_8UC1);
                            Utils.matToBitmap(cellstorm_frame_iter, cellSTORM_result);
                            global_bitmap = cellSTORM_result;
                        }

                        // push the iteration result to the textview

                    }
                publishProgress();
            }

            // write out the results of the summed frames (loc and BF)
            imwriteNorm(cellstorm_sum_result_brightfield, image_path+"cellstorm_BF_result.png");
            imwriteNorm(cellstorm_sum_result, image_path+"cellstorm_result.png");
            Log.i(TAG, "Finish");

            // convert image back to BMP for displaying
            normalize(cellstorm_sum_result, cellstorm_sum_result, 0, 255, NORM_MINMAX);
            cellstorm_sum_result.convertTo(cellstorm_sum_result, CvType.CV_8UC1);
            Utils.matToBitmap(cellstorm_sum_result, cellSTORM_result);


            return null;


        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            imageViewCellstormResult.setImageBitmap(cellSTORM_result);


            //mmr.release();

        }


        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public static Mat preprocess(Mat Mat_input){
        // tune the data to -1..1
        // read one cellSTORM frame
        //normalize(Mat_input, Mat_input, 0, 1, NORM_MINMAX);
        Core.divide(Mat_input, new Scalar(255), Mat_input);
        Core.multiply(Mat_input, new Scalar(2), Mat_input);
        Core.subtract(Mat_input, new Scalar(1), Mat_input);

        return Mat_input;
    }

    public static Mat deprocess(Mat Mat_input){
        // tune the data to 0.1
        // read one cellSTORM frame
        Core.add(Mat_input, new Scalar(1), Mat_input);
        Core.divide(Mat_input, new Scalar(2), Mat_input);
        return Mat_input;
    }



    public static void imwriteNorm(Bitmap Bitmap_input, String filename){
        // Save image from light-source
        Mat Mat_norm = new Mat ();
        Utils.bitmapToMat(Bitmap_input, Mat_norm);
        normalize(Mat_norm, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);

    }

    public static void imwriteNorm(Mat Mat_input, String filename){
        // save image
        Mat Mat_norm = new Mat();
        normalize(Mat_input, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);

    }


    private List<File> getListFiles(File parentDir) {
        List<File> inFiles = new ArrayList<>();
        Queue<File> files = new LinkedList<>();
        files.addAll(Arrays.asList(parentDir.listFiles()));
        while (!files.isEmpty()) {
            File file = files.remove();
            if (file.isDirectory()) {
                files.addAll(Arrays.asList(file.listFiles()));
            } else if (file.getName().endsWith(".csv")) {
                inFiles.add(file);
            }
        }
        return inFiles;
    }


    final float[] getPointerCoords(ImageView view, MotionEvent e)
    {
        final int index = e.getActionIndex();
        final float[] coords = new float[] { e.getX(index), e.getY(index) };
        Matrix matrix = new Matrix();
        view.getImageMatrix().invert(matrix);
        matrix.postTranslate(view.getScrollX(), view.getScrollY());
        matrix.mapPoints(coords);
        return coords;
    }

    // See if debug is activated or not
    View.OnClickListener checkboxClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean checked = ((CheckBox) view).isChecked();
            if (checked) {
                switch (view.getId()) {
                    case R.id.checkBoxDebug:
                        Toast.makeText(LocalizerActivity.this, "Debug == True!", Toast.LENGTH_LONG).show();
                        debug = true;
                        break;
                }
            }
            else{
                switch (view.getId()) {
                    case R.id.checkBoxDebug:
                        Toast.makeText(LocalizerActivity.this,"Debug == False!", Toast.LENGTH_LONG).show();
                        debug = false;
                        break;
                }

            }
        }};
}
