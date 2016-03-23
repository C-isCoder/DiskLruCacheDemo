package com.example.iscoder.disklrucachedemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.iscoder.libcore.io.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private ImageView imageView;
    private TextView cache_text;
    private Button clean_button;
    private Context context;
    private DiskLruCache mDiskLruCache = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("SMZQ", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = MainActivity.this;
        imageView = (ImageView) findViewById(R.id.image_view);
        cache_text = (TextView) findViewById(R.id.text_view);
        clean_button = (Button) findViewById(R.id.clean_button);
        clean_button.setOnClickListener(this);
        initDiskLruCache();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.clean_button: {
                if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
                    try {
                        mDiskLruCache.delete();
                        //调用delete方法内部会调用close方法这样，下面请求缓存大小的方法会失效，所以下面直接把缓存搞成0了。
                        cache_text.setText("缓存大小：" + getFormatSize(0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }
    }

    private void initDiskLruCache() {
        if (mDiskLruCache == null || mDiskLruCache.isClosed()) {
            File cacheDir = getDiskCacheDir(context, "bitmap");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            try {
                mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(context), 1, 10 * 1024 * 1024);
            } catch (IOException e) {
                e.printStackTrace();
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String imageUrl = "https://mmbiz.qlogo.cn/mmbiz/7MhCtyXq28XRfxj6HOdtQuDpg01cNd4K6eFX5YWSAJ1CFBzicUohLps6CfY57Cia4mEeicF3ZOn2VvRvDicc6ZtxrA/0?wx_fmt=jpeg";
                        String key = hashKeyForDisk(imageUrl);
                        DiskLruCache.Editor deitoer = mDiskLruCache.edit(key);
                        if (deitoer != null) {
                            OutputStream outputStream = deitoer.newOutputStream(0);
                            if (downloadUrlToStream(imageUrl, outputStream)) {
                                deitoer.commit();
                                handler.sendEmptyMessage(UPDATE_UI);
                            } else {
                                deitoer.abort();
                            }
                        }
                        mDiskLruCache.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } else if (!mDiskLruCache.isClosed() && mDiskLruCache != null) {
            loadCache();
        }

    }

    private final int UPDATE_UI = 1;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_UI: {
                    loadCache();
                }
            }
        }
    };

    /**
     * 读取缓存
     */
    private void loadCache() {
        try {
            String imageUrl = "https://mmbiz.qlogo.cn/mmbiz/7MhCtyXq28XRfxj6HOdtQuDpg01cNd4K6eFX5YWSAJ1CFBzicUohLps6CfY57Cia4mEeicF3ZOn2VvRvDicc6ZtxrA/0?wx_fmt=jpeg";
            String key = hashKeyForDisk(imageUrl);
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if (snapshot != null) {
                cache_text.setText("缓存大小：" + getFormatSize(mDiskLruCache.size()));
                InputStream is = snapshot.getInputStream(0);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageDrawable(this.getResources().getDrawable(R.mipmap.ic_launcher, null));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 下载图片
     *
     * @param urlString
     * @param outputStream
     * @return
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * 获取存缓存文件的路径。
     * 当SD卡存在或者SD卡不可被移除的时候，就调用getExternalCacheDir()方法来获取缓存路径，
     * 否则就调用getCacheDir()方法来获取缓存路径。
     * 前者获取到的就是 /sdcard/Android/data/<application package>/cache 这个路径，
     * 而后者获取到的是 /data/data/<application package>/cache 这个路径。
     * 接着又将获取到的路径和一个uniqueName进行拼接，作为最终的缓存路径返回。那
     * 么这个uniqueName又是什么呢？其实这就是为了对不同类型的数据进行区分而设定的一个唯一值，
     * 比如说在网易新闻缓存路径下看到的bitmap、object等文件夹。
     *
     * @param context
     * @param uniqueName
     * @return
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 获取版本号
     *
     * @param context
     * @return
     */
    public int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 将图片的url传入根据MD5加密获取唯一的key
     *
     * @param key
     * @return
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        if (mDiskLruCache != null && !mDiskLruCache.isClosed()) {
            try {
                mDiskLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("SMZQ", "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        //initDiskLruCache();
        Log.d("SMZQ", "onStart");
        super.onStart();
    }

    @Override
    protected void onPause() {
        try {
            if (!mDiskLruCache.isClosed()) {
                mDiskLruCache.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("SMZQ", "onPause");
        super.onPause();
    }

    @Override
    protected void onResume() {
        initDiskLruCache();
        Log.d("SMZQ", "onResume");
        super.onResume();
    }

    /**
     * 格式化单位
     *
     * @param size
     * @return
     */
    public static String getFormatSize(double size) {
        double kiloByte = size / 1024;
        if (kiloByte < 1) {
            //return size + "Byte";
            return "0K";
        }
        double megaByte = kiloByte / 1024;
        if (megaByte < 1) {
            BigDecimal result1 = new BigDecimal(Double.toString(kiloByte));
            return result1.setScale(2, BigDecimal.ROUND_HALF_UP)
                    .toPlainString() + "KB";
        }
        double gigaByte = megaByte / 1024;
        if (gigaByte < 1) {
            BigDecimal result2 = new BigDecimal(Double.toString(megaByte));
            return result2.setScale(2, BigDecimal.ROUND_HALF_UP)
                    .toPlainString() + "MB";
        }
        double teraBytes = gigaByte / 1024;
        if (teraBytes < 1) {
            BigDecimal result3 = new BigDecimal(Double.toString(gigaByte));
            return result3.setScale(2, BigDecimal.ROUND_HALF_UP)
                    .toPlainString() + "GB";
        }
        BigDecimal result4 = new BigDecimal(teraBytes);
        return result4.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString()
                + "TB";
    }

}
