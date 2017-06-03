package com.gsy.facerecognition.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.support.annotation.Nullable;

/**
 * 图片的Utils类，提供从SD卡加载图片的功能，注意，需要权限，不然返回的bitmap会为null
 */

public class BitmapUtils {

    /**
     * 从sd卡中加载图片
     *
     * @param picPath     图片路径
     * @param minSide     默认为-1，根据maxPixels生成的scale需要为偶数，如果计算的到scale=3，当minSide为-1是，scale取2，否则取4
     * @param maxPixels   输出的bitma的最大像素值
     * @param sampleScale 加载图片时的Scale，当Scale>0时直接按scale加载
     * @return bitmap
     */
    private static Bitmap decodeSDCardPic(String picPath, int minSide, int maxPixels, int sampleScale) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 仅读取图片的大小
        options.inJustDecodeBounds = true;
        Bitmap resBitmap;
        options.inSampleSize = sampleScale > 0 ? sampleScale : computeScaleSize(options, minSide, maxPixels);
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap tmpBitmap = BitmapFactory.decodeFile(picPath, options);
        int orientation = getPicOrientation(picPath);
        resBitmap = orientation != 1 ? decodeBitmapByOrientation(tmpBitmap, orientation, true) : tmpBitmap;
        return resBitmap;
    }

    /**
     * 根据filePath图片的的宽高计算scale，结果为2的次幂
     *
     * @param options   BitmapFactory的Options
     * @param minSide   最小显示区，默认为 -1
     * @param maxPixels 最大的像素数量
     * @return 缩小倍数
     */
    public static int computeScaleSize(BitmapFactory.Options options, int minSide, int maxPixels) {
        int initSize = computeInitScaleSize(options, minSide, maxPixels);
        int roundSize;
        if (initSize <= 8) {
            roundSize = 1;
            while (roundSize < initSize) {
                roundSize <<= 1;
            }
        } else {
            roundSize = (initSize + 7) / 8 * 8;
        }
        return roundSize;
    }

    /**
     * 计算缩小倍数
     *
     * @param minSide   最小显示区，默认为 -1
     * @param maxPixels 像素数量最大值
     * @return 缩小倍数
     */
    private static int computeInitScaleSize(BitmapFactory.Options options, int minSide, int maxPixels) {
        int upperLimit = (minSide == -1) ? 128 : (int) Math.min(Math.floor(options.outWidth / minSide)
                , Math.floor(options.outHeight / minSide));
        int lowerLimit = (maxPixels == -1) ? 1 : (int) Math.ceil(Math.sqrt(options.outWidth
                * options.outHeight / maxPixels));
        if (upperLimit < lowerLimit) {
            return lowerLimit;
        }
        if ((maxPixels == -1) && (minSide == -1)) {
            return 1;
        } else if (minSide == -1) {
            return lowerLimit;
        } else {
            return upperLimit;
        }
    }

    /**
     * 读取图片的Orientation信息
     *
     * @param filePath 图片路径
     * @return 图片的方向
     */
    public static int getPicOrientation(final String filePath) {
        ExifInterface exifInterface;
        int orientation = 1;
        try {
            exifInterface = new ExifInterface(filePath);
            orientation = Integer.parseInt(exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION));
            orientation = orientation == 0 ? 1 : orientation;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orientation;
    }

    /**
     * 根据orientation生成bitmap
     *
     * @param srcBitmap     原来不带方向信息的bitmap
     * @param orientation   方向信息
     * @param isNeedRelease 是否需要释放源bitmap
     * @return bitmap对象
     */
    public static Bitmap decodeBitmapByOrientation(Bitmap srcBitmap, int orientation, boolean isNeedRelease) {
        Bitmap resBitmap;
        if (orientation != 1) {
            Matrix matrix = new Matrix();
            switch (orientation) {
                case 2:
                    matrix.preScale(-1, 1);
                    break;
                case 3:
                    matrix.preRotate(180);
                    break;
                case 4:
                    matrix.preScale(1, -1);
                    break;
                case 5:
                    matrix.preRotate(90);
                    matrix.preScale(-1, 1);
                    break;
                case 6:
                    matrix.preRotate(90);
                    break;
                case 7:
                    matrix.preScale(-1, 1);
                    matrix.preRotate(90);
                    break;
                case 8:
                    matrix.preRotate(270);
                    break;
                default:
                    break;
            }
            if (matrix.isIdentity()) {
                resBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth()
                        , srcBitmap.getHeight(), null, false);
            } else {
                resBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth()
                        , srcBitmap.getHeight(), matrix, true);
            }
            if (isNeedRelease && resBitmap != srcBitmap) {
                srcBitmap.recycle();
            }
        } else {
            return srcBitmap;
        }
        return resBitmap;
    }

    /**
     * 获取图片
     *
     * @param filePath          路径
     * @param maxWidth          最大宽
     * @param maxHeight         最大高
     * @param isFaceRecognition 系统人脸识别只认565格式
     * @return 图片
     */
    public static Bitmap loadBitmap(String filePath, int maxWidth, int maxHeight, boolean isFaceRecognition) {
        if (maxWidth == 0 || maxHeight == 0) {
            return BitmapUtils.decodeSDCardPic(filePath, -1, -1, -1);
        }
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);
        opts.inJustDecodeBounds = false;
        opts.inPreferQualityOverSpeed = true;
        opts.inSampleSize = (int) Math.max(Math.ceil(opts.outWidth * 1f / maxWidth)
                , Math.ceil(opts.outHeight * 1f / maxHeight));
        if (isFaceRecognition) {
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, opts);
        return adjustOrientation(filePath, bitmap);
    }

    /**
     * 调整方向
     *
     * @param filePath 图片路径
     * @param bitmap   图片
     * @return 调整后的bitmap
     */
    @Nullable
    private static Bitmap adjustOrientation(String filePath, Bitmap bitmap) {
        int orientation = getPicOrientation(filePath);
        if (orientation != 1) {
            bitmap = decodeBitmapByOrientation(bitmap, orientation, false);
        }
        return bitmap;
    }
}
