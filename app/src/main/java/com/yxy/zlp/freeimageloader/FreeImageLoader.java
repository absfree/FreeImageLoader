package com.yxy.zlp.freeimageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator on 2016/4/9.
 */
public class FreeImageLoader {
    public static final String TAG = FreeImageLoader.class.getSimpleName();
    public static final int MESSAGE_SEND_RESULT = 1;
    private static final int IMG_URL = R.id.iv_url;

    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = 2 * CPU_COUNT + 1;
    private static final long KEEP_ALIVE = 5L;

    private static final int DISK_CACHE_SIZE = 30 * 1024 * 1024;

    public static final Executor threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,
            MAX_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private Context mContext;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private static FreeImageLoader sImageLoader = null;

    private Handler mMainHandler = new MainHandler(Looper.getMainLooper());

    private FreeImageLoader(Context context) {
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int memoryCacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(memoryCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        File diskCacheDir = getAppCacheDir(mContext, "images");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (diskCacheDir.getUsableSpace() > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static FreeImageLoader getInstance(Context context) {
        if (sImageLoader == null) {
            synchronized (FreeImageLoader.class) {
                if (sImageLoader == null) {
                    sImageLoader = new FreeImageLoader(context);
                }
            }
        }
        return sImageLoader;
    }

    private void addToMemoryCache(String key, Bitmap bitmap) {
        if (getFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    public void displayImage(final String url, final ImageView imageView, final int dstWidth, final int dstHeight) {
        imageView.setTag(IMG_URL, url);
        Bitmap bitmap = loadFromMemory(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, dstWidth, dstHeight);
                if (bitmap != null) {
                    Result result = new Result(imageView,bitmap, url);
                    Message msg = mMainHandler.obtainMessage(MESSAGE_SEND_RESULT, result);
                    msg.sendToTarget();
                }
            }
        };
        threadPoolExecutor.execute(loadBitmapTask);
    }

    private Bitmap loadBitmap(String url, int dstWidth, int dstHeight) {
        Bitmap bitmap = loadFromMemory(url);
        if (bitmap != null) {
            return bitmap;
        }
        try {
            bitmap = loadFromDisk(url, dstWidth, dstHeight);
            if (bitmap != null) {
                return bitmap;
            }
            bitmap = loadFromNet(url, dstWidth, dstHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    private Bitmap loadFromMemory (String url) {
        String key = getKeyFromUrl(url);
        Bitmap bitmap = getFromMemoryCache(key);
        return bitmap;
    }

    private Bitmap loadFromDisk(String url, int dstWidth, int dstHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "should not Bitmap in main thread");
        }

        if (mDiskLruCache == null) {
            return null;
        }

        Bitmap bitmap = null;
        String key = getKeyFromUrl(url);
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if (snapshot != null) {
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(0);
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            bitmap = decodeSampledBitmapFromFD(fileDescriptor, dstWidth, dstHeight);
            if (bitmap != null) {
                addToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    private Bitmap loadFromNet(String url, int dstWidth, int dstHeight) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("Do not load Bitmap in main thread.");
        }

        if (mDiskLruCache == null) {
            return null;
        }

        String key = getKeyFromUrl(url);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null) {
            OutputStream outputStream = editor.newOutputStream(0);
            if (getStreamFromUrl(url, outputStream)) {
                editor.commit();
            } else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadFromDisk(url, dstWidth, dstHeight);
    }

    public void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getKeyFromUrl(String url) {
        String key;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(url.getBytes());
            byte[] m = messageDigest.digest();
            return getString(m);
        } catch (NoSuchAlgorithmException e) {
            key = String.valueOf(url.hashCode());
        }
        return key;
    }
    private static String getString(byte[] b){
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < b.length; i ++){
            sb.append(b[i]);
        }
        return sb.toString();
    }

    public static File getAppCacheDir(Context context, String dirName) {
        String cacheDirString;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            cacheDirString = context.getExternalCacheDir().getPath();
        } else {
            cacheDirString = context.getCacheDir().getPath();
        }
        return new File(cacheDirString + File.separator + dirName);
    }

    private Bitmap decodeSampledBitmapFromFD(FileDescriptor fd, int dstWidth, int dstHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inSampleSize = calInSampleSize(options, dstWidth, dstHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    private int calInSampleSize(BitmapFactory.Options options, int dstWidth, int dstHeight) {
        int rawWidth = options.outWidth;
        int rawHeight = options.outHeight;
        int inSampleSize = 1;
        if (rawWidth > dstWidth || rawHeight > dstHeight) {
            float ratioWidth = (float) rawWidth / dstHeight;
            float ratioHeight = (float) rawHeight / dstHeight;
            inSampleSize = (int) Math.min(ratioWidth, ratioHeight);
        }
        return inSampleSize;
    }

    public boolean getStreamFromUrl(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            bis = new BufferedInputStream(urlConnection.getInputStream());
            bos = new BufferedOutputStream(outputStream);

            int byteRead;
            while ((byteRead = bis.read()) != -1) {
                bos.write(byteRead);
            }
            return true;
        }catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            close(bis);
            close(bos);
        }
        return false;
    }

    private static class MainHandler extends Handler {
        MainHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_SEND_RESULT) {
                Result result = (Result) msg.obj;
                ImageView imageView = result.imageView;
                String url = (String) imageView.getTag(IMG_URL);
                if (url.equals(result.url)) {
                    imageView.setImageBitmap(result.bitmap);
                } else {
                    Log.w(TAG, "The url associated with imageView has changed");
                }
                MainActivity.afterLoad();
            }
        }
    }

    private static class Result {
        public ImageView imageView;
        public Bitmap bitmap;
        public String url;

        public Result(ImageView imageView, Bitmap bitmap, String url) {
            this.imageView = imageView;
            this.bitmap = bitmap;
            this.url = url;
        }
    }

}
