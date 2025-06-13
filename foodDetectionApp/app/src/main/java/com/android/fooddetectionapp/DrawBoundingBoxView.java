package com.android.fooddetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class DrawBoundingBoxView extends View {
    private float startX, startY, endX, endY;
    private Paint paint;
    private RectF boundingBox;

    public DrawBoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (boundingBox != null) { // Keep the last drawn box visible
            canvas.drawRect(boundingBox, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Reset previous bounding box when starting a new one
                startX = event.getX();
                startY = event.getY();
                boundingBox = null;
                break;

            case MotionEvent.ACTION_MOVE:
                endX = event.getX();
                endY = event.getY();
                boundingBox = new RectF(startX, startY, endX, endY);
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                endX = event.getX();
                endY = event.getY();
                boundingBox = new RectF(startX, startY, endX, endY);
                invalidate();
                break;
        }
        return true;
    }

    public RectF getBoundingBox() {
        return boundingBox;
    }

    public void resetBoundingBox() {
        boundingBox = null;
        invalidate();
    }
}
