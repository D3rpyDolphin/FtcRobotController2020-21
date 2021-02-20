package org.firstinspires.ftc.teamcode.vision.operators;

import com.acmerobotics.dashboard.config.Config;

import org.firstinspires.ftc.robotlib.vision.MatOperator;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

@Config
public class YCrCbRangeFilter implements MatOperator {
    public static int lowerThresholdY = 3;
    public static int lowerThresholdCr = 100;
    public static int lowerThresholdCb = 120;
    public static int upperThresholdY = 20;
    public static int upperThresholdCr = 255;
    public static int upperThresholdCb = 255;
    public static Size blur = new Size(5,5);
    public static int blurSigma = 1;

    public YCrCbRangeFilter() {

    }
    public YCrCbRangeFilter(Size blur) {
        YCrCbRangeFilter.blur = blur;
    }

    Mat mask = new Mat();
    @Override
    public Mat process(Mat img) {
        Scalar lowerThreshold = new Scalar(lowerThresholdY, lowerThresholdCr, lowerThresholdCb);
        Scalar upperThreshold = new Scalar(upperThresholdY, upperThresholdCr, upperThresholdCb);

        Imgproc.cvtColor(img, mask, Imgproc.COLOR_RGB2YCrCb);

        Imgproc.GaussianBlur(mask, mask, blur, blurSigma);
        Core.inRange(mask, lowerThreshold, upperThreshold, mask);
        return mask;
    }
}
