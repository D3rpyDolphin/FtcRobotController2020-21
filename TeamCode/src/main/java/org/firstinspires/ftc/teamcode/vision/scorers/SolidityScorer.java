package org.firstinspires.ftc.teamcode.vision.scorers;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;

import org.firstinspires.ftc.teamcode.robotlib.vision.VisionScorer;
import org.firstinspires.ftc.teamcode.vision.RingData;

import static org.firstinspires.ftc.teamcode.robotlib.util.MathUtil.squareError;

@Config
public class SolidityScorer extends VisionScorer {
    private final FtcDashboard dashboard;

    public static double optimalRatio = .8;
    public static double weight = 1;

    public SolidityScorer() {
        dashboard = FtcDashboard.getInstance();
    }
    public SolidityScorer(double optimalRatio, double weight) {
        dashboard = FtcDashboard.getInstance();
        SolidityScorer.optimalRatio = optimalRatio;
        SolidityScorer.weight = weight;
    }

    @Override
    public double score(RingData ringData) {
        double ratio = ringData.contourArea / ringData.convexHullArea;
        dashboard.getTelemetry().addLine("solidity ratio = " + ratio);
        return squareError(ratio, optimalRatio) * weight;
    }
}