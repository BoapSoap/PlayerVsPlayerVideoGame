package tankgame.menus;

import tankgame.Launcher;
import tankgame.game.AudioManager;
import tankgame.game.GameWorld;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class SettingsPanel extends JPanel {
    private final Launcher lf;
    private final GameWorld gameWorld;
    private BufferedImage backImg;

    public SettingsPanel(Launcher lf, GameWorld gameWorld) {
        this.lf = lf;
        this.gameWorld = gameWorld;
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Settings", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 48));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(title, BorderLayout.NORTH);

        // Center: checklist-style options
        JPanel center = new JPanel();
        center.setBackground(Color.WHITE);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));

        // Section label
        JLabel displayLabel = new JLabel("Display Options");
        displayLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        displayLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(displayLabel);
        center.add(Box.createRigidArea(new Dimension(0, 10)));

        // Fullscreen checkbox
        JCheckBox fullscreenBox = new JCheckBox("Fullscreen");
        fullscreenBox.setFont(new Font("SansSerif", Font.PLAIN, 18));
        fullscreenBox.setBackground(Color.WHITE);
        fullscreenBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Toggling fullscreen
        fullscreenBox.addItemListener(e -> {
            lf.toggleFullScreen();
        });
        center.add(fullscreenBox);

        // Mini-map toggle
        JCheckBox miniMapBox = new JCheckBox("Mini Map");
        miniMapBox.setFont(new Font("SansSerif", Font.PLAIN, 18));
        miniMapBox.setBackground(Color.WHITE);
        miniMapBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (gameWorld != null) {
            miniMapBox.setSelected(gameWorld.isMiniMapEnabled());
        }
        miniMapBox.addItemListener(e -> {
            if (gameWorld != null) {
                gameWorld.setMiniMapEnabled(miniMapBox.isSelected());
            }
        });
        center.add(miniMapBox);

        // Split/Zoom mode toggle
        JCheckBox splitBox = new JCheckBox("Split/Zoom Mode");
        splitBox.setFont(new Font("SansSerif", Font.PLAIN, 18));
        splitBox.setBackground(Color.WHITE);
        splitBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (gameWorld != null) {
            splitBox.setSelected(gameWorld.isSplitScreenEnabled());
        }
        splitBox.addItemListener(e -> {
            if (gameWorld != null) {
                gameWorld.setSplitScreenEnabled(splitBox.isSelected());
            }
        });
        center.add(splitBox);

        center.add(Box.createVerticalGlue());
        add(center, BorderLayout.CENTER);

        // Back button bottom-right
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JButton backBtn = new JButton();
        backBtn.setContentAreaFilled(false);
        backBtn.setBorderPainted(false);
        backBtn.setFocusPainted(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        try {
            backImg = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("backbutton.png")
            ));
            Image scaled = backImg.getScaledInstance(80, 80, Image.SCALE_SMOOTH);
            backBtn.setIcon(new ImageIcon(scaled));
        } catch (IOException | NullPointerException ex) {
            System.err.println("failed to load backbutton.png");
            backBtn.setText("Back");
        }
        backBtn.addActionListener(e -> {
            AudioManager.playEffect("gunshot.wav");
            lf.setFrame("start");
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
        right.add(backBtn);
        south.add(right, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
    }
}
