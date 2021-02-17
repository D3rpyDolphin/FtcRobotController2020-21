package org.firstinspires.ftc.teamcode.vision.operators;

import com.acmerobotics.dashboard.config.Config;

import org.firstinspires.ftc.robotlib.vision.MatOperator;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

@Config
public class HSVRangeFilter implements MatOperator {
    public static int lowerThresholdH = 3;
    public static int lowerThresholdS = 100;
    public static int lowerThresholdV = 120;
    public static int upperThresholdH = 20;
    public static int upperThresholdS = 255;
    public static int upperThresholdV = 255;
    public static Size blur = new Size(5,5);
    public static int blurSigma = 1;

    public HSVRangeFilter() {

    }
    public HSVRangeFilter(Size blur) {
        HSVRangeFilter.blur = blur;
    }

    Mat mask = new Mat();
    @Override
    public Mat process(Mat img) {
        Scalar lowerThreshold = new Scalar(lowerThresholdH, lowerThresholdS, lowerThresholdV);
        Scalar upperThreshold = new Scalar(upperThresholdH, upperThresholdS, upperThresholdV);

        Imgproc.cvtColor(img, mask, Imgproc.COLOR_RGB2HSV);

        Imgproc.GaussianBlur(mask, mask, blur, blurSigma);
        Core.inRange(mask, lowerThreshold, upperThreshold, mask);
        return mask;
    }
}
