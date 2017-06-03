package com.gsy.facerecognition.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.FaceDetector;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

/**
 * 自定义的ImageView，实现图片的拖动，缩放，显示人脸识别的结果
 */
public class MyImageView extends ImageView {
    private static final int SINGLE_OPERATION = 0;                  // 单指触控
    private static final int COUPLE_OPERATION = 1;                  // 双指缩放
    private static final int NONE_OPERATION = -1;                   // 点击事件默认值
    private static final int BORDER_BACK_DURATION = 200;            // 边界回弹的时间
    public static final float FACE_VERTICAL = 2f;                   // 竖直方向上相对于眼睛的距离
    public static final float THE_MAX_SCALE = 3f;                   // 放大为view的最大倍数值
    private int mIntrinsicWidth, mIntrinsicHeight;                  // 图片原始的宽高
    private int mLastX, mLastY;                                     // 上一次触摸事件的位置
    private FaceDetector.Face[] mFaces;                             // 人脸识别结果
    private boolean[] mIsNeedDraws;                                 // 是否需要绘制
    private PointF mPoint;                                          // 眼睛所在位置
    private Paint mPaint;                                           // 画笔，用来绘制人脸识别的结果
    private int mTouchMode;                                         // 触摸的类型，单指或者双指
    private PointF mMidPoint = new PointF();                        // 双指的中点
    private Matrix mDrawMatrix = new Matrix();
    private Matrix mSavedMatrix = new Matrix();
    private Matrix mTempMatrix = new Matrix();
    private RectF mTempRectF;
    private float mResetScale;                                      // 当达到缩放极限时的矫正scale
    private boolean mIsVerticalFit;                                 // 图片是否为竖直填充
    private boolean mIsCheckRAndL;                                  // 标志是否需要检查左右方向居中状态
    private boolean mIsCheckBAndT;                                  // 标志是否需要检查上下方向居中状态
    private boolean mIsMove;                                        // 是否有位移
    private float mLastDist;                                         // 两个手刚按下时的距离
    private int mFaceCount;                                         // 识别出的人脸数量，不一定能正确识别
    // 矫正系数，当人脸识别使用的bitmap缩放大小和ImageView使用的bitmap大小不一致时，需要有矫正系数，此demo因
    // 为识别和使用的bitmap是同一个bitmap，因此矫正系数为 识别scale / imageView的scale = 1f,从Activity中传入
    private float mAdjustScale;


    public MyImageView(Context context) {
        this(context, null, 0);
    }

    public MyImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setImageBitmap(Bitmap bm, FaceDetector.Face[] faces, float adjustScale) {
        mFaces = faces;
        mIsNeedDraws = new boolean[faces.length];
        mPoint = new PointF();
        mPaint = new Paint();
        mPaint.setStrokeWidth(2);
        mPaint.setColor(Color.BLUE);
        mPaint.setStyle(Paint.Style.STROKE);
        mAdjustScale = adjustScale;
        mFaceCount = faces.length;
        setImageBitmap(bm);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        mLastX = mLastY = 0;
        mIntrinsicWidth = drawable.getIntrinsicWidth();
        mIntrinsicHeight = drawable.getIntrinsicHeight();
        centerCropImage();
        mTempRectF = getMatrixRectF();
        // 判断图片的宽高view的宽高FitCenter状态是竖直fit还是水平fit，如果不能理解可以查看IamgeView源码中的CenterCrop
        // 和FitCenter这两个属性，其实这里是照搬源码
        mIsVerticalFit = mIntrinsicWidth * getLayoutParams().height < getLayoutParams().width * mIntrinsicHeight;
        setFaceAreaVisibility();
        super.setImageDrawable(drawable);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) return false;
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mTouchMode = SINGLE_OPERATION;
                mSavedMatrix.set(mDrawMatrix);
                mLastX = (int) ev.getX();
                mLastY = (int) ev.getY();
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                mTouchMode = COUPLE_OPERATION;
                mLastDist = spacing(ev);
                mSavedMatrix.set(mDrawMatrix);
                setMidPoint(ev);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mTouchMode == COUPLE_OPERATION) {
                    mTempMatrix.set(mSavedMatrix);
                    float newDist = spacing(ev);
                    float scale = newDist / mLastDist;
                    mTempMatrix.postScale(scale, scale, mMidPoint.x, mMidPoint.y);
                    mDrawMatrix.set(mTempMatrix);
                    this.setImageMatrix(mDrawMatrix);
                    // 缩放到极限时设置恢复scale
                    boolean isOut = mIsVerticalFit
                            ? getMatrixRectF().width() / getLayoutParams().width > THE_MAX_SCALE
                            : getMatrixRectF().height() / getLayoutParams().height > THE_MAX_SCALE;
                    if (scale >= 1 && isOut) {
                        if (mIsVerticalFit) {
                            // 竖直fit取宽
                            mResetScale = THE_MAX_SCALE * getLayoutParams().width / getMatrixRectF().width();
                        } else {
                            // 水平fit取高
                            mResetScale = THE_MAX_SCALE * getLayoutParams().height / getMatrixRectF().height();
                        }
                    } else if (scale <= 1 && (int) getMatrixRectF().width() <= getLayoutParams().width
                            && (int) getMatrixRectF().height() <= getLayoutParams().height) {
                        // 保证当图片全部缩小在显示范围内便不能再缩小
                        if (mIsVerticalFit) {
                            // 竖直取高
                            mResetScale = getLayoutParams().height / getMatrixRectF().height();
                        } else {
                            // 水平取宽
                            mResetScale = getLayoutParams().width / getMatrixRectF().width();
                        }
                    } else {
                        mResetScale = 0;
                    }
                } else if (mTouchMode == SINGLE_OPERATION) {
                    mTempMatrix.set(mSavedMatrix);
                    float tx = ev.getX() - mLastX;
                    float ty = ev.getY() - mLastY;
                    mIsMove = true;
                    RectF rectF = getMatrixRectF();
                    mIsCheckRAndL = (int) rectF.width() <= getLayoutParams().width;
                    mIsCheckBAndT = (int) rectF.height() <= getLayoutParams().height;
                    mTempMatrix.postTranslate(tx, ty);
                    mDrawMatrix.set(mTempMatrix);
                    this.setImageMatrix(mDrawMatrix);
                }
                setFaceAreaVisibility();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (mTouchMode == COUPLE_OPERATION) {
                    if (mResetScale != 0) {
                        mTempMatrix.postScale(mResetScale, mResetScale, mMidPoint.x, mMidPoint.y);
                        mResetScale = 0;
                    }
                    mDrawMatrix.set(mTempMatrix);
                    center(true, true);
                    this.setImageMatrix(mDrawMatrix);
                    setFaceAreaVisibility();
                    invalidate();
                    if (getMatrixRectF().left > 0 || getMatrixRectF().top > 0) {
                        mTempRectF = getMatrixRectF();
                    }
                } else if (mTouchMode == SINGLE_OPERATION && mIsMove) {
                    float[] dxDyBounds = checkDxDyBounds();
                    if (dxDyBounds == null) {
                        mTempMatrix.postTranslate(0, 0);
                        mDrawMatrix.set(mTempMatrix);
                        this.setImageMatrix(mDrawMatrix);
                        setFaceAreaVisibility();
                    } else {
                        setAnimation(dxDyBounds);
                    }
                }
                mIsMove = false;
                mTouchMode = NONE_OPERATION;
                break;
        }
        return true;
    }


    /**
     * 设置人脸未显示全的提示
     *
     * @return 该图片是否有脸部未显示全
     */
    public boolean setFaceAreaVisibility() {
        checkFace();
        boolean needTip = false;
        for (int i = 0; i < mFaceCount; i++) {
            if (mIsNeedDraws[i]) {
                needTip = true;
                break;
            }
        }
        return needTip;
    }

    /**
     * 根据当前图片的Matrix获得图片的范围
     * 这里获取的是当前显示的图片的大小。
     * 图片放大后，获取的就是图片放大后的图片的大小。
     * 图片缩小后，获取的就是图片缩小后的图片的大小。
     *
     * @return 缩放后的图片RectF
     */
    private RectF getMatrixRectF() {
        Matrix m = mDrawMatrix;
        RectF rect = new RectF();
        rect.set(0, 0, mIntrinsicWidth, mIntrinsicHeight);
        m.mapRect(rect);
        return rect;
    }

    /**
     * 计算两个点击点之间的距离
     *
     * @return 两个点之间的距离
     */
    private float spacing(MotionEvent ev) {
        float x = ev.getX(0) - ev.getX(1);
        float y = ev.getY(0) - ev.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * 给两个点击点之间中点设值
     */
    public void setMidPoint(MotionEvent ev) {
        float x = ev.getX(0) + ev.getX(1);
        float y = ev.getY(0) + ev.getY(1);
        mMidPoint.set(x / 2, y / 2);
    }


    /**
     * 横向、纵向 图片居中
     */
    protected void center(boolean horizontal, boolean vertical) {
        RectF rect = getMatrixRectF();
        float deltaX = 0, deltaY = 0;
        float height = rect.height();
        float width = rect.width();
        if (vertical) {
            // 图片小于屏幕大小，则居中显示，大于屏幕，如果图片上方留空则往上移，图片下方留空则往下移
            int viewHeight = getLayoutParams().height;
            if (height < viewHeight) {
                deltaY = (viewHeight - height) / 2 - rect.top;
            } else if (rect.top > 0) {
                deltaY = -rect.top;
            } else if (rect.bottom < viewHeight) {
                deltaY = this.getHeight() - rect.bottom;
            }
        }
        if (horizontal) {
            int viewWidth = getLayoutParams().width;
            if (width < viewWidth) {
                deltaX = (viewWidth - width) / 2 - rect.left;
            } else if (rect.left > 0) {
                deltaX = -rect.left;
            } else if (rect.right < viewWidth) {
                deltaX = viewWidth - rect.right;
            }
        }
        mDrawMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 检测图片偏离屏幕两边的距离
     * 然后平移，是图片边缘在屏幕边，
     * 使图片周围没有空白
     */
    private float[] checkDxDyBounds() {
        RectF rectF = getMatrixRectF();
        float dx = 0.0f, dy = 0.0f;
        /*
         * 如果图片的左侧大于零，说明图片左侧向右
         * 偏离了左侧屏幕，则左移偏离的距离.
         * rectF.left的值，是基于左侧坐标计算的。
         * 图片正常情况下，该值为0.
         * 当图片向右侧拖动以后，该值大于0.
         * 当图片向左侧拖动以后，该值小于0.
         */
        if (rectF.left > 0) {
            dx = -rectF.left;
            if (mIsCheckRAndL) {
                dx = dx + mTempRectF.left;
            }
        }
        /*
         * 如果图片的右侧偏离屏幕的右侧，则
         * 图片右移图片的宽度与图片显示的宽度的差.
         * rectF.right的值，是基于左侧计算的，图片没有缩放旋转情况下，
         * 该值==touchImageView.getWidth()图片的宽度。
         * 当拖动图片以后，该值变化，等于显示的图片的宽度
         */
        if (rectF.right < this.getWidth()) {
            dx = this.getWidth() - rectF.right;
            if (mIsCheckRAndL) {
                dx = dx - mTempRectF.left;
            }
        }
        /*
         * 当图片顶部大于0，说明图片向下偏离屏幕顶部，
         * 则图片向上回弹偏离的距离。
         * rectF.top的值基于顶部坐标，
         * 图片正常情况下，该值=0.
         */
        if (rectF.top > 0) {
            dy = -rectF.top;
            if (mIsCheckBAndT) {
                dy = dy + mTempRectF.top;
            }
        }
        /*
         * 当图片底部小于图片高度时，图片偏离屏幕底部
         * 则图片回弹图片的高度与显示的图片的高度之差。
         * rectF.bottom的值，基于顶部坐标。
         * 图片正常情况下，该值=图片的高度。
         */
        if (rectF.bottom < this.getHeight()) {
            dy = this.getHeight() - rectF.bottom;
            if (mIsCheckBAndT) {
                dy = dy - mTempRectF.top;
            }
        }
        if (dx != 0 || dy != 0) {
            return new float[]{dx, dy};
        }
        return null;
    }

    /**
     * 设置边界回弹的动画
     *
     * @param bounds 需要回弹偏移量
     */
    private void setAnimation(float[] bounds) {
        setEnabled(false);
        final float floatDx = bounds[0];
        final float floatDy = bounds[1];
        ValueAnimator animator = new ValueAnimator();
        animator.setInterpolator(new DecelerateInterpolator());
        animator.setFloatValues(0, 1);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setEnabled(true);
                setFaceAreaVisibility();
            }
        });
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            float lastDx = 0;
            float lastDy = 0;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float offsetX = floatDx * animation.getAnimatedFraction() - lastDx;
                float offsetY = floatDy * animation.getAnimatedFraction() - lastDy;
                lastDx += offsetX;
                lastDy += offsetY;
                mTempMatrix.postTranslate(offsetX, offsetY);
                mDrawMatrix.set(mTempMatrix);
                MyImageView.this.setImageMatrix(mDrawMatrix);
            }
        });
        animator.setDuration(BORDER_BACK_DURATION).start();
    }

    /**
     * 检查是否有人脸超出显示范围
     */
    private void checkFace() {
        for (int i = 0; i < mFaceCount; i++) {
            if (mFaces[i] == null) continue;
            mFaces[i].getMidPoint(mPoint);
            RectF rectF = new RectF(0, 0, mPoint.x * mAdjustScale, mPoint.y * mAdjustScale);
            mDrawMatrix.mapRect(rectF);
            float eyesDistance = mFaces[i].eyesDistance() * mAdjustScale * getMatrixRectF().width() / mIntrinsicWidth;
            float distanceH = rectF.left + rectF.width();
            float distanceV = rectF.top + rectF.height();
            mIsNeedDraws[i] = distanceH > getLayoutParams().width - eyesDistance                    // 右边超出
                    || distanceH < eyesDistance                                                     // 左边超出
                    || distanceV > getLayoutParams().height - eyesDistance * FACE_VERTICAL          // 下边超出
                    || distanceV < eyesDistance * FACE_VERTICAL;                                    // 上边超出
        }
    }

    /**
     * CenterCrop，照搬源码
     */
    private void centerCropImage() {
        int dwidth = mIntrinsicWidth;
        int dheight = mIntrinsicHeight;
        int vwidth = getLayoutParams().width;
        int vheight = getLayoutParams().height;
        float dx = 0, dy = 0, scaleFactor;
        if (dwidth * vheight > vwidth * dheight) {
            scaleFactor = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scaleFactor) * 0.5f;
        } else {
            scaleFactor = (float) vwidth / (float) dwidth;
            dy = (vheight - dheight * scaleFactor) * 0.5f;
        }
        mDrawMatrix.setScale(scaleFactor, scaleFactor);
        mDrawMatrix.postTranslate(Math.round(dx), Math.round(dy));
    }

    /**
     * 绘制超过显示区域的人脸矩形
     */
    private void drawFace(Canvas canvas) {
        for (int i = 0; i < mFaceCount; i++) {
            if (mFaces[i] != null && mIsNeedDraws[i]) {
                mFaces[i].getMidPoint(mPoint);
                float distance = mFaces[i].eyesDistance() * mAdjustScale;
                RectF rectF = new RectF(mPoint.x * mAdjustScale - distance
                        , mPoint.y * mAdjustScale - FACE_VERTICAL * distance
                        , mPoint.x * mAdjustScale + distance
                        , mPoint.y * mAdjustScale + FACE_VERTICAL * distance);
                canvas.drawRect(rectF, mPaint);
            }
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        final Drawable drawable = getDrawable();
        if (drawable != null) {
            canvas.save();                                          // 保存画布，接下来的操作在新的图层绘制
            canvas.translate(getPaddingLeft(), getPaddingTop());
            canvas.concat(mDrawMatrix);
            drawable.draw(canvas);
            drawFace(canvas);
            canvas.restore();                                       // 合并图层
        }
    }

}
