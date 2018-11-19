package de.cellstorm.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v13.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nononsenseapps.filepicker.FilePickerActivity;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;

import de.cellstorm.R;
import de.cellstorm.utils.SimpleFileDialog;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    Button btnAcqActivity;
    Button btnChooseFile;
    TextView textView;
    String my_file_path = null;
    int FILE_CODE = 123;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request write permission
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.INTERNET},1);


        // specify the GUI interaction
        btnAcqActivity = (Button) findViewById(R.id.btnAcqActivity);
        btnChooseFile = (Button) findViewById(R.id.btnChooseFile);
        textView = (TextView) findViewById(R.id.textview);

        // set GUI
        textView.setText("Please select a Video-file! ");


        // Choose the Video-File
        btnChooseFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                //Create FileOpenDialog and register a callback
                Log.i(TAG, "Start the file-choose dialog!");
                // This always works
                Intent i = new Intent(MainActivity.this, FilePickerActivity.class);
                // This works if you defined the intent filter
                // Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                // Set these depending on your use case. These are the defaults.
                i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);

                // Configure initial directory by specifying a String.
                // You could specify a String like "/storage/emulated/0/", but that can
                // dangerous. Always use Android's API calls to get paths to the SD-card or
                // internal memory.
                i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

                startActivityForResult(i, FILE_CODE);


            }
        });





        btnAcqActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(my_file_path != null){
                    Intent intent = new Intent(v.getContext(), LocalizerActivity.class);
                    intent.putExtra("my_file_path", my_file_path);
                    startActivity(intent);
                }
            }
        });




    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();

                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                        }
                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra
                            (FilePickerActivity.EXTRA_PATHS);

                    if (paths != null) {
                        for (String path: paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                        }
                    }
                }

            } else {
                Uri selectedfile = data.getData();
                File myFile = new File(selectedfile.getPath());
                my_file_path = myFile.getAbsolutePath();
                Log.i(TAG, "The file location is: "+ my_file_path);
                textView.setText("The file location is: "+ my_file_path);
            }
        }
    }





}
