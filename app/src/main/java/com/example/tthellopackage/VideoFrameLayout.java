package com.example.tthellopackage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

public class VideoFrameLayout extends FrameLayout {

    private static final float SCROLL_THRESHOLD = 10;

    private IVideoFrameListener mListener;

    private float mDownX;
    private float mDownY;

    private boolean isOnClick;

    public VideoFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void Register(IVideoFrameListener listener) {
        mListener = listener;
    }

    // When this view is tapped, need to notify the the MainActivity. Need to
    // differentiate between a tap versus other touch events.
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                isOnClick = true;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (isOnClick) {
                    // call back to the MainActivity
                    mListener.onVideoFrameClicked();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isOnClick && (Math.abs(mDownX - ev.getX()) > SCROLL_THRESHOLD || Math.abs(mDownY - ev.getY()) > SCROLL_THRESHOLD)) {
                    isOnClick = false;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}
