package com.yxy.zlp.freeimageloader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;

/**
 * Created by Administrator on 2016/4/11.
 */
public class GridAdapter extends BaseAdapter {
    private static int counts = 0;
    private ArrayList<String> mImgUrlList;
    private Context mContext;
    private FreeImageLoader mFreeImageLoader;

    private ImageLoader mImageLoader;
    private DisplayImageOptions options;

    private LayoutInflater mLayoutInflater;

    public GridAdapter(Context context, ArrayList<String> imgUrlList) {
        mContext = context;
        mImgUrlList = imgUrlList;
        mFreeImageLoader = FreeImageLoader.getInstance(mContext);
        mLayoutInflater = LayoutInflater.from(mContext);

        //mImageLoader = ImageLoader.getInstance();
        //options = new DisplayImageOptions.Builder()
        //        .cacheInMemory(true)
        //        .cacheOnDisk(true)
        //        .build();

    }

    @Override
    public int getCount() {
        return mImgUrlList.size();
    }

    @Override
    public Object getItem(int position) {
        return mImgUrlList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder = null;
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.grid_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.image);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        ImageView imageView = viewHolder.imageView;
        final String url_tag = (String) imageView.getTag();
        final String url = (String) getItem(position);
        if (!url.equals(url_tag)) {
            imageView.setImageResource(R.drawable.loading);
        }

        if (MainActivity.isGridIdle) {
            imageView.setTag(url);
            mFreeImageLoader.displayImage(url, imageView, 160, 160);
//            mImageLoader.displayImage(url, imageView, options, new ImageLoadingListener() {
//                @Override
//                public void onLoadingStarted(String s, View view) {

//                }

//                @Override
//                public void onLoadingFailed(String s, View view, FailReason failReason) {

//                }

//                @Override
//                public void onLoadingComplete(String s, View view, Bitmap bitmap) {
//                    counts++;
//                    if (counts == 30) {
//                        long afterTime = System.currentTimeMillis();
//                        float averTime = (float) ((afterTime - MainActivity.beforeTime) / 30);
//                        MainActivity.textView.setText("Average image load time: " + averTime);
//                    }
//                }

//                @Override
//                public void onLoadingCancelled(String s, View view) {

//                }
//            });
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView imageView;
    }
}
