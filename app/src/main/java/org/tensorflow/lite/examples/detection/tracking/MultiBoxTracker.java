/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tracking;

import static java.lang.Thread.sleep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.Classifier.Recognition;
import org.tensorflow.lite.examples.detection.DetectorActivity;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker {
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static String lastStr = null;
  private static Queue<String> queue = new LinkedList<>();

  private static final int[] COLORS = {
          Color.BLUE,
          Color.RED,
          Color.GREEN,
          Color.YELLOW,
          Color.CYAN,
          Color.MAGENTA,
          Color.WHITE,
          Color.parseColor("#55FF55"),
          Color.parseColor("#FFA500"),
          Color.parseColor("#FF8888"),
          Color.parseColor("#AAAAFF"),
          Color.parseColor("#FFFFAA"),
          Color.parseColor("#55AAAA"),
          Color.parseColor("#AA33AA"),
          Color.parseColor("#0D0068")
  };
  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<Integer>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;
  private Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;
 private TextToSpeech textToSpeech;
  public MultiBoxTracker(final Context context) {
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textToSpeech = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int i) {
        if(i != TextToSpeech.ERROR){
          textToSpeech.setLanguage(Locale.US);
        }
      }
    });

    textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  public synchronized void setFrameConfiguration(
          final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
  }

  public synchronized void trackResults(final List<Pair<Classifier.Recognition, Double>> results, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
            Math.min(
                    canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                    canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
            ImageUtils.getTransformationMatrix(
                    frameWidth,
                    frameHeight,
                    (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                    (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                    sensorOrientation,
                    false);
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
              !TextUtils.isEmpty(recognition.title)
                      ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                      : String.format("%.2f", (100 * recognition.detectionConfidence));
      //            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
      // labelString);
      borderedText.drawText(
              canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
    }
  }

  private void processResults(final List<Pair<Classifier.Recognition, Double>> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Pair<Classifier.Recognition, Double> result : results) {
      if (result.first.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.first.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
              "Result! Frame: " + result.first.getLocation() + " mapped to screen:" + detectionScreenRect);

      if(!queue.contains(result.first.getTitle())){
          if(queue.size()>=2){
              queue.poll();
          }
          queue.add(result.first.getTitle());
        textToSpeech.speak(result.first.getTitle() + " is detected at a distance of " + result.second + "meters!" , TextToSpeech.QUEUE_ADD, null);
      }

//      if(lastStr == null || lastStr != result.getTitle()){
//        lastStr = result.getTitle();
//        textToSpeech.speak(result.getTitle() + " is detected !", TextToSpeech.QUEUE_ADD, null);
//        textToSpeech.playSilentUtterance(1000, TextToSpeech.QUEUE_ADD,null);
//      }

      screenRects.add(new Pair<Float, RectF>(result.first.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.first.getConfidence(), result.first));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
//      trackedRecognition.color = COLORS[trackedObjects.size() % COLORS.length];
      trackedRecognition.color = COLORS[potential.second.getDetectedClass() % COLORS.length];
      trackedObjects.add(trackedRecognition);

//      if (trackedObjects.size() >= COLORS.length) {
//        break;
//      }
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
}
