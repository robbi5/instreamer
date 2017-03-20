package com.robbi5.instreamer;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.realtek.hardware.RtkHDMIRxManager;
import com.realtek.server.HDMIRxParameters;
import com.realtek.server.HDMIRxStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import com.robbi5.instreamer.layout.MainFragment;

import static com.robbi5.instreamer.layout.MainFragment.STREAM_ACTIVITY_INTENT;

public class StreamService extends Service {

  SharedPreferences settings;
  RtkHDMIRxManager hdmiRxManager;
  Process ffmpegProcess;
  Thread readerThread;
  Thread logThread;

  public StreamService() {
  }

  private void sendIntent(String key, String value) {
    Intent intent = new Intent(STREAM_ACTIVITY_INTENT);
    intent.putExtra(key, value);
    sendBroadcast(intent);
  }

  public void copyFile(File src, File dst) throws IOException {
    try (FileInputStream in = new FileInputStream(src);
         FileOutputStream out = new FileOutputStream(dst)) {
      FileChannel inChannel = in.getChannel();
      FileChannel outChannel = out.getChannel();
      inChannel.transferTo(0, inChannel.size(), outChannel);
    }
  }

  boolean isProcessRunning(Process process) {
    try {
      process.exitValue();
    } catch (IllegalThreadStateException e) {
      return true;
    }
    return false;
  }

  private String getFfmpegPath() {
    String myPath = this.getApplicationContext().getFilesDir().getAbsolutePath();
    return myPath + "/ffmpeg";
  }

  private void copyFfmpeg() throws FileNotFoundException {
    File ffmpegSource = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "ffmpeg");
    File ffmpegBin = new File(getFfmpegPath());
    if (!ffmpegSource.exists() && !ffmpegBin.exists()) {
      throw new FileNotFoundException();
    }

    // copy ffmpeg from sdcard to data folder
    // because sdcard has noexec mount flag
    if (!ffmpegBin.exists()) {
      try {
        copyFile(ffmpegSource, ffmpegBin);
        ffmpegBin.setExecutable(true);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    settings = PreferenceManager.getDefaultSharedPreferences(this);

    try {
      copyFfmpeg();
    } catch (FileNotFoundException e) {
      Toast.makeText(this, R.string.ffmpeg_not_found, Toast.LENGTH_LONG).show();
      return Service.START_NOT_STICKY;
    }

    // try cleanup first
    stopFFMPEG();
    releaseHdmiRxManager();

    // create Pipe for hdmi -> ffmpeg
    ParcelFileDescriptor[] fdPair;
    try {
      fdPair = ParcelFileDescriptor.createPipe();
    } catch (IOException e) {
      e.printStackTrace();
      return Service.START_NOT_STICKY;
    }
    ParcelFileDescriptor readFD = fdPair[0];
    ParcelFileDescriptor writeFD = fdPair[1];

    // Start ffmpeg process
    String cmd = settings.getString("ffmpeg_cmd", "");
    cmd = cmd
      .replaceFirst("/mnt/sdcard/", "") // migration of old settings
      .replaceFirst("ffmpeg", getFfmpegPath());

    Log.d("starting ffmpeg", cmd);

    try {
      ffmpegProcess = Runtime.getRuntime().exec(cmd);
      final BufferedReader in = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()));
      final Process thisFFMPEG = ffmpegProcess;

      // create logger thread
      logThread = new Thread() {
        @Override
        public void run() {
          String log = null;
          try {
            while (isProcessRunning(thisFFMPEG)) {
              log = in.readLine();

              if ((log != null) && (log.length() > 0)) {
                Log.d("ffmpeg", log);
                sendIntent("log", log);
                sleep(10);
              } else {
                sleep(100);
              }
            }
          } catch (InterruptedException e) {
            // meh.
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      };
      logThread.start();

    } catch (IOException e) {
      e.printStackTrace();
      return Service.START_NOT_STICKY;
    } catch (RuntimeException e) {
      e.printStackTrace();
      Toast.makeText(this, R.string.edit_settings, Toast.LENGTH_LONG).show();
      return Service.START_NOT_STICKY;
    }

    // Initialize recording hardware
    hdmiRxManager = new RtkHDMIRxManager();
    HDMIRxStatus rxStatus = hdmiRxManager.getHDMIRxStatus();
    if (rxStatus == null || rxStatus.status != 1) {
      hdmiRxManager.release();
      hdmiRxManager = null;
      return Service.START_NOT_STICKY;
    }
    if (hdmiRxManager.open() != 0) {
      hdmiRxManager.release();
      hdmiRxManager = null;
      return Service.START_NOT_STICKY;
    }

    // Initialize preview
    // Needed, else streaming doesn't work!
    Surface s = intent.getParcelableExtra(MainFragment.EXTRA_SURFACE);
    try {
      hdmiRxManager.setPreviewDisplay2(s);
    } catch (IOException e) {
      hdmiRxManager.release();
      hdmiRxManager = null;
      return Service.START_NOT_STICKY;
    }

    hdmiRxManager.setParameters(buildHDMIRxParametersFromSettings(settings));
    RtkHDMIRxManager.VideoConfig videoConfig = buildVideoConfigFromSettings(settings);
    RtkHDMIRxManager.AudioConfig audioConfig = buildAudioConfigFromSettings(settings);

    hdmiRxManager.configureTargetFormat(videoConfig, audioConfig);

    // create proxy thread to read from hdmi and write to ffmpeg stdin
    final ParcelFileDescriptor finalReadFD = readFD;

    readerThread = new Thread() {
      @Override
      public void run() {
        byte[] buffer = new byte[8192];
        int read;

        OutputStream ffmpegInput = ffmpegProcess.getOutputStream();
        final FileInputStream reader = new FileInputStream(finalReadFD.getFileDescriptor());

        try {
          while (true) {
            if (reader.available() > 0) {
              read = reader.read(buffer);
              ffmpegInput.write(buffer, 0, read);
            } else {
              sleep(1);
            }
          }
        } catch (InterruptedException e) {
          // meh.
        } catch (IOException e) {
          sendIntent("error", "Streaming failed: " + e.getMessage());
          teardown();
          sendIntent("failure", "");
        }
      }
    };

    // Make sure this thread has higher priority
    readerThread.setPriority(Thread.NORM_PRIORITY + 1);
    readerThread.start();

    try {
      hdmiRxManager.setTargetFd(writeFD, RtkHDMIRxManager.HDMIRX_FILE_FORMAT_TS);
      hdmiRxManager.play();
      hdmiRxManager.setTranscode(true);
      Toast.makeText(this, "Streaming started", Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      sendIntent("error", "Failed to start stream: " + e.getMessage());
      teardown();
      sendIntent("failure", "");
    }

    return Service.START_NOT_STICKY;
  }

  public static HDMIRxParameters buildHDMIRxParametersFromSettings(SharedPreferences settings) {
    int videoFramerate = Integer.parseInt(settings.getString("videoFramerate", "30"));
    int[] videoSize = buildVideoWidthHeightFromSettings(settings);

    HDMIRxParameters hdmiRxParameters = new HDMIRxParameters();
    hdmiRxParameters.setPreviewSize(videoSize[0], videoSize[1]);
    hdmiRxParameters.setPreviewFrameRate(videoFramerate);

    return hdmiRxParameters;
  }

  public static int[] buildVideoWidthHeightFromSettings(SharedPreferences settings) {
    int videoSize = Integer.parseInt(settings.getString("videoSize", "0"));
    switch (videoSize) {
      default:
      case 0:
        return new int[]{1920,1080};
      case 1:
        return new int[]{1280,720};
      case 2:
        return new int[]{720,576};
      case 3:
        return new int[]{720,480};
      case 4:
        return new int[]{640,368};
    }
  }

  public static RtkHDMIRxManager.AudioConfig buildAudioConfigFromSettings(SharedPreferences settings) {
    int sampleRate = Integer.parseInt(settings.getString("audioBitrate", "44100"));
    int channels = Integer.parseInt(settings.getString("audioChannels", "2"));
    int scale = 441;
    if (sampleRate % 8000 == 0) {
      scale = 480;
    }
    int audioBitrate = ((channels * 640) * sampleRate) / scale;
    return new RtkHDMIRxManager.AudioConfig(channels, sampleRate, audioBitrate);
  }

  public static RtkHDMIRxManager.VideoConfig buildVideoConfigFromSettings(SharedPreferences settings) {
    int[] videoSize = buildVideoWidthHeightFromSettings(settings);
    int videoBitrate = Integer.parseInt(settings.getString("videoBitrate", "10000000"));
    return new RtkHDMIRxManager.VideoConfig(videoSize[0], videoSize[1], videoBitrate);
  }

  private void teardown() {
    stopFFMPEG();
    releaseHdmiRxManager();
  }

  public void onDestroy() {
    teardown();
  }

  private void releaseHdmiRxManager() {
    if (hdmiRxManager != null) {
      hdmiRxManager.setTranscode(false);
      hdmiRxManager.release();
      hdmiRxManager = null;
    }
  }

  private void stopFFMPEG() {
    if (ffmpegProcess != null) {
      ffmpegProcess.destroy();
      ffmpegProcess = null;
    }
    if (readerThread != null) {
      readerThread.interrupt();
      readerThread = null;
    }
    if (logThread != null) {
      logThread.interrupt();
      logThread = null;
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
