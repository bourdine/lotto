package com.research.lottolotto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MiningService extends Service {

    private static final String TAG = "MiningService";
    private static final String CHANNEL_ID = "MiningChannel";
    private static final int NOTIFICATION_ID = 1;

    private ExecutorService miningExecutor;
    private PowerManager.WakeLock wakeLock;
    private boolean isMining = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private File dataFile;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        acquireWakeLock();
        createDataFile();
        Log.d(TAG, "Сервис создан");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String poolUrl = intent != null ? intent.getStringExtra("pool_url") : "pool.supportxmr.com:3333";
        startMining(poolUrl);
        return START_STICKY;
    }

    private void startMining(String poolUrl) {
        isMining = true;
        miningExecutor = Executors.newSingleThreadExecutor();

        miningExecutor.submit(() -> {
            Log.d(TAG, "Начало эксперимента. Пул: " + poolUrl);
            long iteration = 0;
            while (isMining) {
                try {
                    performMiningIteration(iteration);
                    iteration++;
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Майнинг прерван", e);
                    break;
                }
            }
        });
    }

    private void performMiningIteration(long iteration) {
        long startTime = System.nanoTime();
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sin(i) * Math.cos(i) * Math.tan(i % 90);
        }
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        logMetrics(duration, iteration);
        if (iteration % 50 == 0) updateNotification();
    }

    private void logMetrics(long duration, long iteration) {
        if (dataFile == null) return;
        try (FileWriter fw = new FileWriter(dataFile, true)) {
            String timestamp = dateFormat.format(new Date());
            double hashRate = 1000000000.0 / duration;
            fw.append(String.format(Locale.US, "%s,%d,%.2f\n", timestamp, iteration, hashRate));
            fw.flush();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи", e);
        }
    }

    private void createDataFile() {
        try {
            File dir = new File(getExternalFilesDir(null), "experiment_data");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "mining_data_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
            dataFile = new File(dir, fileName);
            try (FileWriter fw = new FileWriter(dataFile)) {
                fw.append("Timestamp,Iteration,HashRate\n");
            }
            Log.d(TAG, "Файл: " + dataFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Ошибка создания файла", e);
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lottolotto::MiningWakelock");
        wakeLock.acquire(10 * 60 * 1000L);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mining Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Канал для уведомлений");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("lottolotto")
                .setContentText("Эксперимент выполняется...")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isMining = false;
        if (miningExecutor != null) {
            miningExecutor.shutdown();
            try {
                if (!miningExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    miningExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                miningExecutor.shutdownNow();
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Log.d(TAG, "Сервис остановлен");
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}