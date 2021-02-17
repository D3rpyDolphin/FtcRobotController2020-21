package org.firstinspires.ftc.teamcode.vision.scorers;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;

import org.firstinspires.ftc.robotlib.vision.VisionScorer;
import org.firstinspires.ftc.teamcode.vision.RingData;

@Config
public class AreaScorer implements VisionScorer {
    private final FtcDashboard dashboard;
    public static double weight = 10;

    public AreaScorer() {
        dashboard = FtcDashboard.getInstance();
    }
    public AreaScorer(double weight) {
        dashboard = FtcDashboard.getInstance();
        this.weight = weight;
    }

    @Override
    public double score(RingData ringData) {
        double area = ringData.getNormalizedContourArea();
        dashboard.getTelemetry().addLine("area = " + area);
        return -area * weight;
    }

    @Override
    public double getWeight() {
        return weight;
    }
}