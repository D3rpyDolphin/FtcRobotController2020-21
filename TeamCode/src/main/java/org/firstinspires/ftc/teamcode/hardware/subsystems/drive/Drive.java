package org.firstinspires.ftc.teamcode.hardware.subsystems.drive;

import androidx.annotation.NonNull;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.control.PIDCoefficients;
import com.acmerobotics.roadrunner.control.PIDFController;
import com.acmerobotics.roadrunner.drive.DriveSignal;
import com.acmerobotics.roadrunner.followers.HolonomicPIDVAFollower;
import com.acmerobotics.roadrunner.followers.TrajectoryFollower;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.profile.MotionProfile;
import com.acmerobotics.roadrunner.profile.MotionProfileGenerator;
import com.acmerobotics.roadrunner.profile.MotionState;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.acmerobotics.roadrunner.trajectory.TrajectoryBuilder;
import com.acmerobotics.roadrunner.trajectory.constraints.DriveConstraints;
import com.acmerobotics.roadrunner.trajectory.constraints.MecanumConstraints;
import com.acmerobotics.roadrunner.util.NanoClock;
import com.google.common.flogger.FluentLogger;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;

import org.firstinspires.ftc.teamcode.hardware.subsystems.Localizer;
import org.firstinspires.ftc.teamcode.robotlib.hardware.roadrunner.MecanumDrive;
import org.firstinspires.ftc.teamcode.robotlib.util.DashboardUtil;
import org.firstinspires.ftc.teamcode.robotlib.util.LynxModuleUtil;
import org.firstinspires.ftc.teamcode.util.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.firstinspires.ftc.teamcode.hardware.subsystems.drive.DriveConstants.BASE_CONSTRAINTS;
import static org.firstinspires.ftc.teamcode.hardware.subsystems.drive.DriveConstants.MOTOR_VELO_PID;
import static org.firstinspires.ftc.teamcode.hardware.subsystems.drive.DriveConstants.RUN_USING_ENCODER;
import static org.firstinspires.ftc.teamcode.hardware.subsystems.drive.DriveConstants.TRACK_WIDTH;
import static org.firstinspires.ftc.teamcode.hardware.subsystems.drive.DriveConstants.encoderTicksToInches;
import static org.firstinspires.ftc.teamcode.robotlib.util.MathUtil.angleWrapRadians;

@Config
public class Drive extends MecanumDrive {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final String LEFT_FRONT_NAME = "leftFront";
    private static final String LEFT_REAR_NAME = "leftRear";
    private static final String RIGHT_REAR_NAME = "rightRear";
    private static final String RIGHT_FRONT_NAME = "rightFront";

    //public static double DRIVER_TARGET_ANGLE_MIN_ERROR = 1; // In degrees

    public static PIDCoefficients TRANSLATIONAL_PID = new PIDCoefficients(0, 0, 0);
    public static PIDCoefficients HEADING_PID = new PIDCoefficients(0, 0, 0);

    public static double HEADING_MIN_ERROR = .1;

    public static double LATERAL_MULTIPLIER = 1;

    public static double VX_WEIGHT = 1;
    public static double VY_WEIGHT = 1;
    public static double OMEGA_WEIGHT = 1;

    public static int POSE_HISTORY_LIMIT = 100;

    public enum Mode {
        IDLE,
        TURN,
        FOLLOW_TRAJECTORY
    }

    private final NanoClock clock;

    private Mode mode;

    private final PIDFController turnController;
    private MotionProfile turnProfile;
    private double turnStart;

    private final DriveConstraints constraints;
    private final TrajectoryFollower follower;

    private final LinkedList<Pose2d> poseHistory;

    private final DcMotorEx leftFront;
    private final DcMotorEx leftRear;
    private final DcMotorEx rightRear;
    private final DcMotorEx rightFront;
    private final List<DcMotorEx> motors;

    private final VoltageSensor batteryVoltageSensor;

    private Pose2d lastPoseOnTurn;

    private final Localizer localizer;

    public static double LAUNCH_X = Field.LAUNCH_LINE_X - .75 * Field.TILE_WIDTH;
    public static double BEHIND_LINE_ERROR = .5; // inches

    public Drive(HardwareMap hardwareMap) {
        super("Drive", DriveConstants.kV, DriveConstants.kA, DriveConstants.kStatic, TRACK_WIDTH, TRACK_WIDTH, LATERAL_MULTIPLIER);

        clock = NanoClock.system();

        mode = Mode.IDLE;

        turnController = new PIDFController(HEADING_PID);
        turnController.setInputBounds(0, 2 * Math.PI);

        constraints = new MecanumConstraints(BASE_CONSTRAINTS, TRACK_WIDTH);
        follower = new HolonomicPIDVAFollower(TRANSLATIONAL_PID, TRANSLATIONAL_PID, HEADING_PID,
                new Pose2d(0.5, 0.5, Math.toRadians(5.0)), 0.5);

        poseHistory = new LinkedList<>();

        LynxModuleUtil.ensureMinimumFirmwareVersion(hardwareMap);

        batteryVoltageSensor = hardwareMap.voltageSensor.iterator().next();

        for (LynxModule module : hardwareMap.getAll(LynxModule.class)) {
            module.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        leftFront = hardwareMap.get(DcMotorEx.class, LEFT_FRONT_NAME);
        leftRear = hardwareMap.get(DcMotorEx.class, LEFT_REAR_NAME);
        rightRear = hardwareMap.get(DcMotorEx.class, RIGHT_FRONT_NAME);
        rightFront = hardwareMap.get(DcMotorEx.class, RIGHT_REAR_NAME);

        motors = Arrays.asList(leftFront, leftRear, rightRear, rightFront);

        for (DcMotorEx motor : motors) {
            MotorConfigurationType motorConfigurationType = motor.getMotorType().clone();
            motorConfigurationType.setAchieveableMaxRPMFraction(1.0);
            motor.setMotorType(motorConfigurationType);
        }

        if (RUN_USING_ENCODER) {
            setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }

        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        if (RUN_USING_ENCODER && MOTOR_VELO_PID != null) {
            setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, MOTOR_VELO_PID);
        }

        leftFront.setDirection(DcMotorSimple.Direction.REVERSE);
        leftRear.setDirection(DcMotorSimple.Direction.REVERSE);

        localizer = new Localizer(hardwareMap);
        setLocalizer(localizer);
    }

    public Drive(HardwareMap hardwareMap, Localizer localizer) {
        super("Drive", DriveConstants.kV, DriveConstants.kA, DriveConstants.kStatic, TRACK_WIDTH, TRACK_WIDTH, LATERAL_MULTIPLIER);

        clock = NanoClock.system();

        mode = Mode.IDLE;

        turnController = new PIDFController(HEADING_PID);
        turnController.setInputBounds(0, 2 * Math.PI);

        constraints = new MecanumConstraints(BASE_CONSTRAINTS, TRACK_WIDTH);
        follower = new HolonomicPIDVAFollower(TRANSLATIONAL_PID, TRANSLATIONAL_PID, HEADING_PID,
                new Pose2d(0.5, 0.5, Math.toRadians(5.0)), 0.5);

        poseHistory = new LinkedList<>();

        LynxModuleUtil.ensureMinimumFirmwareVersion(hardwareMap);

        batteryVoltageSensor = hardwareMap.voltageSensor.iterator().next();

        for (LynxModule module : hardwareMap.getAll(LynxModule.class)) {
            module.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
        }

        leftFront = hardwareMap.get(DcMotorEx.class, LEFT_FRONT_NAME);
        leftRear = hardwareMap.get(DcMotorEx.class, LEFT_REAR_NAME);
        rightRear = hardwareMap.get(DcMotorEx.class, RIGHT_FRONT_NAME);
        rightFront = hardwareMap.get(DcMotorEx.class, RIGHT_REAR_NAME);

        motors = Arrays.asList(leftFront, leftRear, rightRear, rightFront);

        for (DcMotorEx motor : motors) {
            MotorConfigurationType motorConfigurationType = motor.getMotorType().clone();
            motorConfigurationType.setAchieveableMaxRPMFraction(1.0);
            motor.setMotorType(motorConfigurationType);
        }

        if (RUN_USING_ENCODER) {
            setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }

        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        if (RUN_USING_ENCODER && MOTOR_VELO_PID != null) {
            setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER, MOTOR_VELO_PID);
        }

        leftFront.setDirection(DcMotorSimple.Direction.REVERSE);
        leftRear.setDirection(DcMotorSimple.Direction.REVERSE);

        this.localizer = localizer;
        setLocalizer(localizer);
    }

    @Override
    public void init() {
        setMotorPowers(0,0,0,0);
    }

    @Override
    public void update() {
        updatePoseEstimate();

        Pose2d currentPose = getPoseEstimate();

        poseHistory.add(currentPose);

        if (POSE_HISTORY_LIMIT > -1 && poseHistory.size() > POSE_HISTORY_LIMIT) {
            poseHistory.removeFirst();
        }

        switch (mode) {
            case IDLE:
                // do nothing
                break;
            case TURN: {
                double t = clock.seconds() - turnStart;

                MotionState targetState = turnProfile.get(t);

                turnController.setTargetPosition(targetState.getX());

                double correction = turnController.update(currentPose.getHeading());

                double targetOmega = targetState.getV();
                double targetAlpha = targetState.getA();
                setDriveSignal(new DriveSignal(new Pose2d(
                        0, 0, targetOmega + correction
                ), new Pose2d(
                        0, 0, targetAlpha
                )));

                if (t >= turnProfile.duration()) {
                    mode = Mode.IDLE;
                    setDriveSignal(new DriveSignal());
                }
                break;
            }
            case FOLLOW_TRAJECTORY: {
                setDriveSignal(follower.update(currentPose));

                if (!follower.isFollowing()) {
                    mode = Mode.IDLE;
                    setDriveSignal(new DriveSignal());
                }

                break;
            }
        }
    }

    @Override
    public void stop() {
        setMotorPowers(0,0,0,0);
    }

    @Override
    public void updateTelemetry() {
        Pose2d currentPose = getPoseEstimate();

        telemetry.put("mode", mode);

        telemetry.put("Motor velocities: ", getWheelVelocities().toString());

        telemetry.put("x", currentPose.getX());
        telemetry.put("y", currentPose.getY());
        telemetry.put("heading", currentPose.getHeading());

        TelemetryPacket packet = new TelemetryPacket();
        Canvas fieldOverlay = packet.fieldOverlay();

        switch (mode) {
            case IDLE:
                // do nothing
                break;
            case TURN: {
                double t = clock.seconds() - turnStart;

                MotionState targetState = turnProfile.get(t);

                Pose2d newPose = lastPoseOnTurn.copy(lastPoseOnTurn.getX(), lastPoseOnTurn.getY(), targetState.getX());

                fieldOverlay.setStroke("#4CAF50");
                DashboardUtil.drawRobot(fieldOverlay, newPose);
                break;
            }
            case FOLLOW_TRAJECTORY: {
                Trajectory trajectory = follower.getTrajectory();

                fieldOverlay.setStrokeWidth(1);
                fieldOverlay.setStroke("#4CAF50");
                DashboardUtil.drawSampledPath(fieldOverlay, trajectory.getPath());
                double t = follower.elapsedTime();
                DashboardUtil.drawRobot(fieldOverlay, trajectory.get(t));

                fieldOverlay.setStroke("#3F51B5");
                DashboardUtil.drawPoseHistory(fieldOverlay, poseHistory);
                break;
            }
        }

        fieldOverlay.setStroke("#3F51B5");
        DashboardUtil.drawRobot(fieldOverlay, currentPose);

        Field.drawRings(fieldOverlay);
        Field.drawWobbleGoal(fieldOverlay, localizer.getAlliance());

        FtcDashboard.getInstance().sendTelemetryPacket(packet);
    }

    @Override
    public void updateLogging() {
        Pose2d currentPose = getPoseEstimate();
        Pose2d lastError = getLastError();

        logger.atFine().log("mode", mode);

        logger.atFine().log("x", currentPose.getX());
        logger.atFine().log("y", currentPose.getY());
        logger.atFine().log("heading", currentPose.getHeading());

        logger.atFine().log("xError", lastError.getX());
        logger.atFine().log("yError", lastError.getY());
        logger.atFine().log("headingError", lastError.getHeading());
    }

    /*
     * Input is the forwardAmt, strafeAmt, rotation
     */
    public void teleopControl(Pose2d rawControllerInput, boolean fieldCentric, boolean pointAtTarget) {
        Vector2d input = new Vector2d(rawControllerInput.getX(), rawControllerInput.getY());

        double turn = -rawControllerInput.getHeading();

        if (fieldCentric) {
            input = input.rotated(-getPoseEstimate().getHeading());
        }

        if (pointAtTarget) {
            pointAtTargetAsync();
            turn = 0; // may not work, must test
        }

        setWeightedDrivePower(
                new Pose2d(input.getX(), input.getY(), turn)
        );
    }

    public boolean readyToShoot() {
        return Math.abs(getPoseEstimate().getHeading() - localizer.getTargetHeading()) < HEADING_MIN_ERROR;
    }
    public void pointAtTargetAsync() {
        turnToAsync(localizer.getTargetHeading());
    }
    public void pointAtTarget() {
        turnTo(localizer.getTargetHeading());
    }

    // TODO: Check this is global not local
    public void strafeToPointAsync(Pose2d target) {
        Pose2d pose = getPoseEstimate();
        Trajectory trajectory = trajectoryBuilder(pose)
                .lineToSplineHeading(target)
                .build();
        followTrajectoryAsync(trajectory);
    }
    public void strafeToPoint(Pose2d target) {
        Pose2d pose = getPoseEstimate();
        Trajectory trajectory = trajectoryBuilder(pose)
                .lineToSplineHeading(target)
                .build();
        followTrajectory(trajectory);
    }

    public void goBehindLineAsync() {
        // TODO: change heading to target shooting
        double angle = 0;
        strafeToPointAsync(new Pose2d(LAUNCH_X, localizer.getPoseEstimate().getY(), angle));
    }
    public boolean isBehindLine() {
        return Math.abs(getPoseEstimate().getX() - LAUNCH_X) < BEHIND_LINE_ERROR;
    }

    public TrajectoryBuilder trajectoryBuilder(Pose2d startPose) {
        return new TrajectoryBuilder(startPose, constraints);
    }
    public TrajectoryBuilder trajectoryBuilder(Pose2d startPose, boolean reversed) {
        return new TrajectoryBuilder(startPose, reversed, constraints);
    }
    public TrajectoryBuilder trajectoryBuilder(Pose2d startPose, double startHeading) {
        return new TrajectoryBuilder(startPose, startHeading, constraints);
    }

    public void turnAsync(double angle) {
        double heading = getPoseEstimate().getHeading();

        lastPoseOnTurn = getPoseEstimate();

        turnProfile = MotionProfileGenerator.generateSimpleMotionProfile(
                new MotionState(heading, 0, 0, 0),
                new MotionState(heading + angle, 0, 0, 0),
                constraints.maxAngVel,
                constraints.maxAngAccel,
                constraints.maxAngJerk
        );

        turnStart = clock.seconds();
        mode = Mode.TURN;
    }
    public void turn(double angle) {
        turnAsync(angle);
        waitForIdle();
    }

    public void turnToAsync(double angle) {
        turnAsync(angleWrapRadians(angle - getPoseEstimate().getHeading()));
    }
    public void turnTo(double angle) {
        turnToAsync(angle);
        waitForIdle();
    }

    public void followTrajectoryAsync(Trajectory trajectory) {
        follower.followTrajectory(trajectory);
        mode = Mode.FOLLOW_TRAJECTORY;
    }
    public void followTrajectory(Trajectory trajectory) {
        followTrajectoryAsync(trajectory);
        waitForIdle();
    }

    public void cancelFollowing() {
        mode = Mode.IDLE;
    }

    public Pose2d getLastError() {
        switch (mode) {
            case FOLLOW_TRAJECTORY:
                return follower.getLastError();
            case TURN:
                return new Pose2d(0, 0, turnController.getLastError());
            case IDLE:
                return new Pose2d();
        }
        throw new AssertionError();
    }

    public void waitForIdle() {
        while (!Thread.currentThread().isInterrupted() && isBusy()) {
            update();
        }
    }

    public boolean isBusy() {
        return mode != Mode.IDLE;
    }

    public void setMode(DcMotor.RunMode runMode) {
        for (DcMotorEx motor : motors) {
            motor.setMode(runMode);
        }
    }

    public void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior zeroPowerBehavior) {
        for (DcMotorEx motor : motors) {
            motor.setZeroPowerBehavior(zeroPowerBehavior);
        }
    }

    public void setPIDFCoefficients(DcMotor.RunMode runMode, PIDFCoefficients coefficients) {
        PIDFCoefficients compensatedCoefficients = new PIDFCoefficients(
                coefficients.p, coefficients.i, coefficients.d,
                coefficients.f * 12 / batteryVoltageSensor.getVoltage()
        );
        for (DcMotorEx motor : motors) {
            motor.setPIDFCoefficients(runMode, compensatedCoefficients);
        }
    }

    public void setWeightedDrivePower(Pose2d drivePower) {
        Pose2d vel = drivePower;

        if (Math.abs(drivePower.getX()) + Math.abs(drivePower.getY())
                + Math.abs(drivePower.getHeading()) > 1) {
            // re-normalize the powers according to the weights
            double denom = VX_WEIGHT * Math.abs(drivePower.getX())
                    + VY_WEIGHT * Math.abs(drivePower.getY())
                    + OMEGA_WEIGHT * Math.abs(drivePower.getHeading());

            vel = new Pose2d(
                    VX_WEIGHT * drivePower.getX(),
                    VY_WEIGHT * drivePower.getY(),
                    OMEGA_WEIGHT * drivePower.getHeading()
            ).div(denom);
        }

        setDrivePower(vel);
    }

    @NonNull
    @Override
    public List<Double> getWheelPositions() {
        List<Double> wheelPositions = new ArrayList<>();
        for (DcMotorEx motor : motors) {
            wheelPositions.add(encoderTicksToInches(motor.getCurrentPosition()));
        }
        return wheelPositions;
    }

    @Override
    public List<Double> getWheelVelocities() {
        List<Double> wheelVelocities = new ArrayList<>();
        for (DcMotorEx motor : motors) {
            wheelVelocities.add(encoderTicksToInches(motor.getVelocity()));
        }
        return wheelVelocities;
    }

    @Override
    public void setMotorPowers(double v, double v1, double v2, double v3) {
        leftFront.setPower(v);
        leftRear.setPower(v1);
        rightRear.setPower(v2);
        rightFront.setPower(v3);
    }

    @Override
    public double getRawExternalHeading() {
        return 0;
    }

    double getBatteryVoltage(HardwareMap hardwareMap) {
        double result = Double.POSITIVE_INFINITY;
        for (VoltageSensor sensor : hardwareMap.voltageSensor) {
            double voltage = sensor.getVoltage();
            if (voltage > 0) {
                result = Math.min(result, voltage);
            }
        }
        return result;
    }
}
