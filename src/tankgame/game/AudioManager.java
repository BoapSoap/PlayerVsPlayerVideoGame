package tankgame.game;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;

public class AudioManager {
    private Clip backgroundClip;

    public static Clip loadClip(String resourcePath) {
        try {
            URL url = AudioManager.class.getClassLoader().getResource(resourcePath);
            if (url == null) {
                System.err.println("Audio resource not found: " + resourcePath);
                return null;
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(url);
            AudioFormat baseFormat = ais.getFormat();
            AudioFormat decoded = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            AudioInputStream dais = AudioSystem.getAudioInputStream(decoded, ais);
            Clip clip = AudioSystem.getClip();
            clip.open(dais);
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void playBackground(String resourcePath) {
        if (isBackgroundPlaying()) return; // don't restart if already playing
        stopBackground();
        backgroundClip = loadClip(resourcePath);
        if (backgroundClip != null) {
            backgroundClip.loop(Clip.LOOP_CONTINUOUSLY);
            backgroundClip.start();
        }
    }

    public boolean isBackgroundPlaying() {
        return backgroundClip != null && backgroundClip.isRunning();
    }

    public void stopBackground() {
        if (backgroundClip != null) {
            backgroundClip.stop();
            backgroundClip.close();
            backgroundClip = null;
        }
    }

    public static void playEffect(String resourcePath) {
        new Thread(() -> {
            Clip clip = loadClip(resourcePath);
            if (clip != null) {
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.start();
            }
        }, "SFX-" + resourcePath).start();
    }
}
