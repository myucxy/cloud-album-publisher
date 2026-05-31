package com.cloudalbum.publisher.focalpoint.provider;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Slf4j
@Component
public class OpenCvFocalPointProvider implements FocalPointProvider {

    private static final String FACE_CASCADE_PATH = "opencv/haarcascade_frontalface_alt.xml";
    private static final int MAX_PROCESS_DIMENSION = 1600;
    private CascadeClassifier faceCascade;
    private volatile boolean cascadeLoadAttempted = false;

    @Override
    public String getProviderType() {
        return "OPENCV";
    }

    @Override
    public List<FocalPointResult> detect(InputStream imageInputStream, Map<String, Object> extraConfig) {
        try {
            BufferedImage originalImage = ImageIO.read(imageInputStream);
            if (originalImage == null) {
                return Collections.emptyList();
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            BufferedImage bufferedImage = originalImage;

            // Downscale large images to reduce memory usage
            if (originalWidth > MAX_PROCESS_DIMENSION || originalHeight > MAX_PROCESS_DIMENSION) {
                double scale = (double) MAX_PROCESS_DIMENSION / Math.max(originalWidth, originalHeight);
                int scaledWidth = (int) (originalWidth * scale);
                int scaledHeight = (int) (originalHeight * scale);
                bufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = bufferedImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
                g.dispose();
                originalImage.flush();
                log.debug("Downscaled image from {}x{} to {}x{}", originalWidth, originalHeight, scaledWidth, scaledHeight);
            }

            Mat mat = bufferedImageToMat(bufferedImage);
            if (mat == null || mat.empty()) {
                return Collections.emptyList();
            }

            try {
                List<FocalPointResult> results = new ArrayList<>();
                int width = bufferedImage.getWidth();
                int height = bufferedImage.getHeight();

                // Try face detection first
                List<FocalPointResult> faces = detectFaces(mat, width, height);
                if (!faces.isEmpty()) {
                    results.addAll(faces);
                }

                // If no faces found, try saliency detection
                if (results.isEmpty()) {
                    FocalPointResult saliency = detectSaliency(mat, width, height);
                    if (saliency != null) {
                        results.add(saliency);
                    }
                }

                // Fallback to center if nothing detected
                if (results.isEmpty()) {
                    results.add(new FocalPointResult(0.5, 0.5, 0.1, "CENTER", 0.0, 0.0));
                }

                return results;
            } finally {
                mat.close();
            }
        } catch (Exception e) {
            log.warn("OpenCV focal point detection failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FocalPointResult> detectFaces(Mat mat, int width, int height) {
        try {
            CascadeClassifier cascade = getFaceCascade();
            if (cascade == null || cascade.empty()) {
                return Collections.emptyList();
            }

            Mat gray = new Mat();
            cvtColor(mat, gray, COLOR_BGR2GRAY);
            equalizeHist(gray, gray);

            RectVector faces = new RectVector();
            cascade.detectMultiScale(gray, faces, 1.1, 3, 0,
                    new Size(30, 30), new Size());

            List<FocalPointResult> results = new ArrayList<>();
            long numFaces = faces.size();
            for (long i = 0; i < numFaces; i++) {
                Rect face = faces.get(i);
                double x = (face.x() + face.width() / 2.0) / width;
                double y = (face.y() + face.height() / 2.0) / height;
                double regionWidth = (double) face.width() / width;
                double regionHeight = (double) face.height() / height;
                double confidence = 0.9; // OpenCV Haar cascade doesn't return confidence
                results.add(new FocalPointResult(x, y, confidence, "FACE", regionWidth, regionHeight));
            }

            gray.close();
            faces.close();
            return results;
        } catch (Exception e) {
            log.warn("Face detection failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private FocalPointResult detectSaliency(Mat mat, int width, int height) {
        try {
            // Simple saliency detection using gradient magnitude
            Mat gray = new Mat();
            cvtColor(mat, gray, COLOR_BGR2GRAY);

            Mat gradX = new Mat();
            Mat gradY = new Mat();
            Sobel(gray, gradX, CV_32F, 1, 0);
            Sobel(gray, gradY, CV_32F, 0, 1);

            Mat magnitude = new Mat();
            magnitude(gradX, gradY, magnitude);

            // Find the region with highest gradient magnitude
            double[] minVal = new double[1];
            double[] maxVal = new double[1];
            Point minLoc = new Point();
            Point maxLoc = new Point();
            minMaxLoc(magnitude, minVal, maxVal, minLoc, maxLoc, null);

            double x = (double) maxLoc.x() / width;
            double y = (double) maxLoc.y() / height;

            gray.close();
            gradX.close();
            gradY.close();
            magnitude.close();
            minLoc.close();
            maxLoc.close();

            return new FocalPointResult(x, y, 0.5, "SALIENCY", 0.0, 0.0);
        } catch (Exception e) {
            log.warn("Saliency detection failed: {}", e.getMessage());
            return null;
        }
    }

    private synchronized CascadeClassifier getFaceCascade() {
        if (cascadeLoadAttempted) {
            return faceCascade;
        }
        cascadeLoadAttempted = true;
        try {
            ClassPathResource resource = new ClassPathResource(FACE_CASCADE_PATH);
            if (!resource.exists()) {
                log.warn("Face cascade file not found: {}", FACE_CASCADE_PATH);
                return null;
            }
            // Extract to temp file since CascadeClassifier requires a filesystem path
            java.io.File tempFile = java.io.File.createTempFile("haarcascade_", ".xml");
            tempFile.deleteOnExit();
            try (java.io.InputStream is = resource.getInputStream();
                 java.io.OutputStream os = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }
            faceCascade = new CascadeClassifier(tempFile.getAbsolutePath());
            if (faceCascade.empty()) {
                log.warn("Failed to load face cascade from extracted file");
                faceCascade = null;
            }
        } catch (Exception e) {
            log.warn("Failed to load face cascade: {}", e.getMessage());
        }
        return faceCascade;
    }

    private Mat bufferedImageToMat(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Mat mat = new Mat(height, width, CV_8UC3);
        byte[] data = new byte[width * height * 3];
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            data[i * 3] = (byte) (pixel & 0xFF);         // B
            data[i * 3 + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            data[i * 3 + 2] = (byte) ((pixel >> 16) & 0xFF); // R
        }
        mat.data().put(data, 0, data.length);
        return mat;
    }
}
