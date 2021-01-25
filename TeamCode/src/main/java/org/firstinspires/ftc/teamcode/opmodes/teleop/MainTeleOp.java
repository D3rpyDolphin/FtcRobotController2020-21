package org.firstinspires.ftc.teamcode.opmodes.teleop;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.hardware.Robot;
import org.firstinspires.ftc.teamcode.opmodes.DataWriterUtil;

@TeleOp(name="Main TeleOp", group="TeleOp")
@Disabled
public class MainTeleOp extends OpMode {
    Robot robot;
    OperatorInterface operatorInterface = new OperatorInterface(robot, gamepad1, gamepad2);

    @Override
    public void init() {
        robot = new Robot(this);

        robot.setAlliance(DataWriterUtil.getAlliance());
        robot.setPoseEstimate(DataWriterUtil.getLastPose());

        robot.init();
    }

    @Override
    public void start() {
        robot.start();
    }

    @Override
    public void loop() {
        operatorInterface.update();
        robot.update();
    }

    @Override
    public void stop() {
        robot.stop();
    }
}
