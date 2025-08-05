package tankgame.game;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;

public class AudioManager {
    private Clip backgroundClip;
    private static float masterVolume = 1.0f;   // [0.0 – 1.0]

    public static void setMasterVolume(float volume) {
        masterVolume = Math.max(0f, Math.min(1f, volume));
        // if music is already playing, update its volume immediately
        // we do that in playBackground by re-applying gain each time we start
    }

    public static float getMasterVolume() {
        return masterVolume;
    }

    private static Clip loadClip(String resourcePath) {
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
        if (isBackgroundPlaying()) return;
        stopBackground();
        backgroundClip = loadClip(resourcePath);
        if (backgroundClip != null) {
            applyVolume(backgroundClip);
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
                applyVolume(clip);
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.start();
            }
        }, "SFX-" + resourcePath).start();
    }

    private static void applyVolume(Clip clip) {
        FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        // convert linear 0.0–1.0 to decibels:
        float dB = (float) (20f * Math.log10(masterVolume <= 0f ? 0.0001f : masterVolume));
        vol.setValue(dB);
    }
}
