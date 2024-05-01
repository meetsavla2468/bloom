package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private Button camera_button;
    private Button ocr_button;
    private Button currency_button;
    private Button sos_button;
    private Button speak_button;
    private TextToSpeech textToSpeech;

    private static int VOICE_RECOGNITION_CODE = 1234;
    //@SuppressLint("MissingInflatedId")

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        camera_button=findViewById(R.id.cameraButton);
        ocr_button=findViewById(R.id.OCR);
        currency_button=findViewById(R.id.Currency);
        sos_button = findViewById(R.id.sos);
        speak_button = findViewById(R.id.speak);

        speak_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                        "Speech recognition demo");
                startActivityForResult(intent, VOICE_RECOGNITION_CODE);
            }
        });


        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i != TextToSpeech.ERROR){
                    textToSpeech.setLanguage(Locale.US);
                }
            }
        });

        camera_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textToSpeech.speak("Object Detection mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,DetectorActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });
        ocr_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textToSpeech.speak("OCR mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,OCRActivity.class));
            }
        });
        currency_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textToSpeech.speak("Currency Detection mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,ClassifierActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });
        sos_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //textToSpeech.speak("SOS", TextToSpeech.QUEUE_ADD, null);

                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                intent.setData(Uri.parse("tel:" + "9319773751"));
                startActivity(intent);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_CODE && resultCode == RESULT_OK) {
            ArrayList matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches.contains("read")) {
                textToSpeech.speak("OCR mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,OCRActivity.class));
            }
            else if (matches.contains("camera")) {
                textToSpeech.speak("Object Detection mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,DetectorActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
            else if (matches.contains("currency")) {
                textToSpeech.speak("Currency Detection mode", TextToSpeech.QUEUE_ADD, null);
                startActivity(new Intent(HomeActivity.this,ClassifierActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
            else if (matches.contains("emergency")) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                intent.setData(Uri.parse("tel:" + "9319773751"));
                startActivity(intent);
            }
        }
    }
}