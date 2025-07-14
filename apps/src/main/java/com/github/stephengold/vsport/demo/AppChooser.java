/*
 Copyright (c) 2022-2025 Stephen Gold

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.vsport.demo;

import com.github.stephengold.vsport.BaseApplication;
import com.github.stephengold.vsport.test.AssimpTest;
import com.github.stephengold.vsport.test.CheckerboardTest;
import com.github.stephengold.vsport.test.HelloVSport;
import com.github.stephengold.vsport.test.MouseTest;
import com.github.stephengold.vsport.test.MouseTest2;
import com.github.stephengold.vsport.test.OctasphereTest;
import com.github.stephengold.vsport.test.SpriteTest;
import com.github.stephengold.vsport.test.TextureTest;
import com.github.stephengold.vsport.tutorial.HelloCcd;
import com.github.stephengold.vsport.tutorial.HelloCharacter;
import com.github.stephengold.vsport.tutorial.HelloContactResponse;
import com.github.stephengold.vsport.tutorial.HelloCustomShape;
import com.github.stephengold.vsport.tutorial.HelloDamping;
import com.github.stephengold.vsport.tutorial.HelloDeactivation;
import com.github.stephengold.vsport.tutorial.HelloDoor;
import com.github.stephengold.vsport.tutorial.HelloDoubleEnded;
import com.github.stephengold.vsport.tutorial.HelloGhost;
import com.github.stephengold.vsport.tutorial.HelloJoint;
import com.github.stephengold.vsport.tutorial.HelloKinematics;
import com.github.stephengold.vsport.tutorial.HelloLimit;
import com.github.stephengold.vsport.tutorial.HelloMadMallet;
import com.github.stephengold.vsport.tutorial.HelloMassDistribution;
import com.github.stephengold.vsport.tutorial.HelloMinkowski;
import com.github.stephengold.vsport.tutorial.HelloMotor;
import com.github.stephengold.vsport.tutorial.HelloNewHinge;
import com.github.stephengold.vsport.tutorial.HelloNonUniformGravity;
import com.github.stephengold.vsport.tutorial.HelloRigidBody;
import com.github.stephengold.vsport.tutorial.HelloServo;
import com.github.stephengold.vsport.tutorial.HelloSport;
import com.github.stephengold.vsport.tutorial.HelloSpring;
import com.github.stephengold.vsport.tutorial.HelloStaticBody;
import com.github.stephengold.vsport.tutorial.HelloVehicle;
import com.github.stephengold.vsport.tutorial.HelloWalk;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * Choose a V-Sport application to run.
 */
final class AppChooser extends JFrame {
    /**
     * Main entry point for the AppChooser application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        Logger.getLogger("").setLevel(Level.WARNING);
        List<BaseApplication> apps = new ArrayList<>(40);

        apps.add(new AssimpTest());
        apps.add(new CheckerboardTest());
        apps.add(new ConveyorDemo());
        apps.add(new HelloCcd());
        apps.add(new HelloCharacter());

        apps.add(new HelloContactResponse());
        apps.add(new HelloCustomShape());
        apps.add(new HelloDamping());
        apps.add(new HelloDeactivation());
        apps.add(new HelloDoor());

        apps.add(new HelloDoubleEnded());
        apps.add(new HelloGhost());
        apps.add(new HelloJoint());
        apps.add(new HelloKinematics());
        apps.add(new HelloLimit());

        apps.add(new HelloMadMallet());
        apps.add(new HelloMassDistribution());
        apps.add(new HelloMinkowski());
        apps.add(new HelloMotor());
        apps.add(new HelloNewHinge());

        apps.add(new HelloNonUniformGravity());
        apps.add(new HelloRigidBody());
        apps.add(new HelloServo());
        apps.add(new HelloSport());
        apps.add(new HelloSpring());

        apps.add(new HelloStaticBody());
        apps.add(new HelloVehicle());
        apps.add(new HelloVSport());
        apps.add(new HelloWalk());
        apps.add(new MouseTest());

        apps.add(new MouseTest2());
        apps.add(new NewtonsCradle());
        apps.add(new OctasphereTest());
        apps.add(new Pachinko());
        apps.add(new SplitDemo());

        apps.add(new SpriteTest());
        apps.add(new TestGearJoint());
        apps.add(new TextureTest());
        apps.add(new ThousandCubes());
        apps.add(new Windlass());

        new AppChooser(apps);
    }
    // *************************************************************************
    // private methods

    /**
     * Select and run one V-Sport app from the specified list.
     *
     * @param apps the list of apps to choose from (not null, unaffected)
     */
    private AppChooser(List<? extends BaseApplication> apps) {
        setTitle("V-Sport AppChooser");
        setSize(500, 100);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Container contentPane = getContentPane();

        // Add a ComboBox to select one app.
        JComboBox<String> comboBox = new JComboBox<>();
        for (BaseApplication app : apps) {
            String appName = app.getClass().getSimpleName();
            comboBox.addItem(appName);
        }
        contentPane.add(BorderLayout.CENTER, comboBox);

        // Add a JButton to start the selected app.
        JButton startButton = new JButton("Start the selected app");
        startButton.addActionListener(actionEvent -> {
            setVisible(false);
            int selectedIndex = comboBox.getSelectedIndex();
            apps.get(selectedIndex).start();
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });
        contentPane.add(BorderLayout.EAST, startButton);

        setVisible(true);
    }
}
