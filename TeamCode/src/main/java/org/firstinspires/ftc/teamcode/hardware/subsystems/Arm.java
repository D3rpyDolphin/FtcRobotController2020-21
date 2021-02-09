package org.firstinspires.ftc.teamcode.hardware.subsystems;

import com.acmerobotics.dashboard.config.Config;
import com.google.common.flogger.FluentLogger;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotlib.hardware.Subsystem;

@Config
public class Arm extends Subsystem {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    public static final String CLAW_SERVO_NAME = "claw";
    public static final String ARM_MOTOR_NAME = "arm";

    // Position from 0 to 1
    public static double CLAW_OPEN_POSITION = 1;
    public static double CLAW_CLOSE_POSITION = 0;

    public static double ARM_LIFT_ANGLE = 1;
    public static double ARM_DEFAULT_ANGLE = .5;
    public static double ARM_LOWER_ANGLE = 0;

    public static double GEAR_RATIO = 1; // output revs / input revs

    public static PIDFCoefficients POS_PIDF = new PIDFCoefficients(0,0,0,0);

    public static double TICKS_PER_REV = 0;

    private final DcMotorEx armMotor;
    private final Servo clawServo;
    private double armAngle, clawPosition;

    public Arm(HardwareMap hardwareMap) {
        super("Arm");

        clawServo = hardwareMap.get(Servo.class, CLAW_SERVO_NAME);

        armMotor = hardwareMap.get(DcMotorEx.class, ARM_MOTOR_NAME);

        armMotor.setMode(DcMotorEx.RunMode.STOP_AND_RESET_ENCODER);
        armMotor.setPIDFCoefficients(DcMotorEx.RunMode.RUN_TO_POSITION, POS_PIDF);
        armMotor.setMode(DcMotorEx.RunMode.RUN_TO_POSITION);
    }

    @Override
    public void start() {
        closeClaw(); // At the beginning of the round, the claw is closed with the wobble
        defaultPos();
    }

    @Override
    public void update() {

    }

    @Override
    public void stop() {
        closeClaw();
        defaultPos();
    }

    @Override
    public void updateMotorAndServoValues() {
        clawServo.setPosition(clawPosition);
        updateArmAngle(armAngle);
    }

    @Override
    public void updateTelemetry() {
        telemetry.put("claw position: ", clawServo.getPosition());
        telemetry.put("arm angle: ", getArmAngle());
    }

    @Override
    public void updateLogging() {
        logger.atFine().log("claw position: ", clawServo.getPosition());
        logger.atFine().log("arm angle: ", getArmAngle());
        logger.atFiner().log("target - claw position: ", clawPosition - clawServo.getPosition());
        logger.atFiner().log("target - arm angle: ", armAngle - getArmAngle());
        logger.atFinest().log("claw target: ", clawPosition);
        logger.atFinest().log("arm target: ", armAngle);
    }

    // Commands //
    public void openClaw() {
        setClawPosition(CLAW_OPEN_POSITION);
    }
    public void closeClaw() {
        setClawPosition(CLAW_CLOSE_POSITION);
    }

    public void lift() {
        setArmAngle(ARM_LIFT_ANGLE);
    }
    public void defaultPos() {
        setArmAngle(ARM_DEFAULT_ANGLE);
    }
    public void lower() {
        setArmAngle(ARM_LOWER_ANGLE);
    }

    // Setters //
    public void setArmAngle(double angle) {
        armAngle = angle;
    }
    public void updateArmAngle(double angle) {
        angle -= ARM_DEFAULT_ANGLE;
        int ticks = (int) (angle / (2 * Math.PI * GEAR_RATIO) * TICKS_PER_REV);
        armMotor.setTargetPosition(ticks);
    }
    public void setClawPosition(double position) {
        this.clawPosition = position;
    }

    // Getters //
    public double getArmAngle() {
        return 2 * Math.PI * GEAR_RATIO * armMotor.getCurrentPosition() / TICKS_PER_REV + ARM_DEFAULT_ANGLE;
    }
}
