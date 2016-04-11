package com.yxy.zlp.freeimageloader;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by Administrator on 2016/4/11.
 */
public class SquareImageView extends ImageView{
    public SquareImageView(Context context) {
        super(context, null, 0);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }


}
