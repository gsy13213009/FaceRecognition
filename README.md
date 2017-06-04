# FaceRecognition
##基于Android API的人脸识别
### 图片的缩放，平移这些需求还是挺常见的，我通过自定义ImageView实现缩放和平移，结合系统提供API实现人脸的识别
- 代码稍微有点多，首先上演示效果：

![图片的缩放，平移，人脸识别效果.gif](http://github.com/gsy13213009/FaceRecognition/blob/master/图片的缩放，平移，人脸识别效果.gif)

- 缩放和平移其实也就是调用ImageView的setImageMatrix方法便可完成，通过计算移动的距离(tx，ty)设置Matrix.postTranslate(tx，ty)，两个手指新距离和按下距离的比值scale以按下时两指的中点设置Matrix.postScale(scale，x，y)，再将Matrix设置为ImageView即可
- 人脸识别通过系统提供的FaceDetector便可实现，虽然不是很准确，但是目前也只有能达到这种结果，这个类使用的bitmap有两个要求，第一个是必须使用Bitmap.Config.RGB_565格式加载，第二个是bitmap的宽必须为偶数
-------------
### 下面来看具体代码
##### 首先是人脸识别，识别的结果为一个Face数组，当然所有信息例如眼睛的位置，距离等均包含在Face类中，具体可以查看源码

``` java
public class MainActivity extends Activity {

    private MyImageView mMyImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initPhoto();
    }

    private void initView() {
        mMyImageView = (MyImageView) findViewById(R.id.main_image);
    }

    private void initPhoto() {
        String picPath = "/storage/emulated/0/Tencent/QQ_Images/-663c6adb1540c36f.jpg";
        Bitmap srcBitmap = BitmapUtils.loadBitmap(picPath, 1000, 1000, true);
        if (srcBitmap == null) {
            Toast.makeText(getApplicationContext(), "处理图片失败", Toast.LENGTH_SHORT).show();
        } else {
            // 图片的宽必须为偶数，不然系统无法进行人脸识别
            if (srcBitmap.getWidth() % 2 != 0) {
                srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth() - 1
                        , srcBitmap.getHeight());
            }
            // 最多的人脸数
            int maxCount = 50;
            FaceDetector.Face[] faces = new FaceDetector.Face[maxCount];
            FaceDetector faceDetector = new FaceDetector(srcBitmap.getWidth(), srcBitmap.getHeight(), maxCount);
            // 这一步比较耗时间，大概一秒左右，跟bitmap的大小有关(1000左右最佳，识别结果准确并且时间较少)，建议使用EventBus异步处理
            int faceCount = faceDetector.findFaces(srcBitmap, faces);
            PointF pointF = new PointF();
            // 过滤原本就不完整的脸
            for (int i = 0; i < faceCount; i++) {
                float eyesDistance = faces[i].eyesDistance();
                faces[i].getMidPoint(pointF);
                if (pointF.x < eyesDistance                                              // 左边超出
                        || pointF.y < eyesDistance * 2f                                // 上边超出
                        || srcBitmap.getWidth() - pointF.x < eyesDistance                // 右边超出
                        || srcBitmap.getHeight() - pointF.y < eyesDistance * 2f) {     // 下边超出
                    faces[i] = null;
                }
            }
            // 必须设置LayoutParams，这样在自定义ImageView中使用getLayoutParams才能得到正确的params
            FrameLayout.LayoutParams photoParams = new FrameLayout.LayoutParams(mMyImageView.getLayoutParams());
            photoParams.gravity = Gravity.CENTER;
            photoParams.width = 1000;
            photoParams.height = 1000;
            mMyImageView.setLayoutParams(photoParams);
            mMyImageView.setImageBitmap(srcBitmap, faces, 1f);
        }
    }

}
```
- 其中的BitmapUtils类为提供根据图片本地路径picPath加载bitmap的方法，具体实现可以通过GitHub上的项目查看
- 这里脸部范围的定义是：以两只眼睛的中点为重心，宽为两倍的眼睛之间的距离，高为4倍的眼睛之间的距离
------------------
##### 接下来是自定义的ImageView，也是整个demo的重点
- 这里着重解释一些mIsVerticalFit这个变量，这是根据图片的宽高和ImageView的宽高做FitCenter得到的值，FitCenter的意思就是将图片刚好完整的居中显示在ImageView中，这里边涉及到水平Fit和竖直Fit，这个值便是判断是否为竖直Fit的变量，详细的FitCenter和CenterCrop信息可以自行百度~

``` java
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
        checkFace();
        super.setImageDrawable(drawable);
    }
```
- 我们主要来看一下单指和双指移动过程中的代码，这里设置放大倍数为View宽高的3倍，如果是竖直Fit就以宽来计算，否则以高来计算
- 如果是放大，并且放大倍数已经超过3倍了，便设置mResetScale以供手指放开时恢复放大的极限，缩小同理，最多能缩小到FitCenter，具体该使用宽还是高做判断取决于mIsVerticalFit

``` java
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
                checkFace();
                break;
            }
```
- 当手指抬起来时同时进行边界回弹检查和缩放倍数检查，如果超过缩放极限便根据mResetScale将图片缩放至极限大小，具体代码如下：

``` java
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
                    checkFace();
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
                        checkFace();
                    } else {
                        setAnimation(dxDyBounds);
                    }
                }
                mIsMove = false;
                mTouchMode = NONE_OPERATION;
                break;
```
- 其中由于系统自带的回弹动画太快，Matrix.postTranslate()方法不可以传递动作时间，因此使用ValueAnimator将移动的操作拆分按传入时间完成，以达到动画效果

``` java
/**
     * 设置边界回弹的动画
     *
     * @param bounds 需要回弹的偏移量
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
                checkFace();
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
```
##### 检测移动和缩放过程中是否有人脸超出显示范围，将超出范围的人脸用蓝色方框标注出来
- 根据人脸范围(眼睛中点为重心，宽为两倍眼睛之间的距离，高为四倍)到自定义ImageView边界的距离判断是否超过显示范围，这里的坐标均为相对坐标，即ImageView的左上角即为原点(绝对坐标指的是屏幕左上角为原点)

``` java
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
```
- 绘制每个需要绘制的矩形，根据mIsNeedDraws这个数组判断，如果mIsNeedDraws[i]为true证明这个矩形需要绘制，否则continue

``` java
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
```
##### 至此，整个图片的缩放，平移和人脸识别基本结束，关于检查边界并计算回弹距离，以及BitmapUtils加载本地图片相关的代码大家可以查看完整的demo，地址：https://github.com/gsy13213009/FaceRecognition.git
- 有关缩放平移的代码，我也是参考了这个哥们的分享完成的，因此边界回弹等相关代码基本照搬，只是多了一些比如人脸识别，缩放的极限设置以及图片的填充方向等工作，该博客的地址为：额，找不到了，以后遇到再填吧

### 欢迎大家交流和指正哈~
