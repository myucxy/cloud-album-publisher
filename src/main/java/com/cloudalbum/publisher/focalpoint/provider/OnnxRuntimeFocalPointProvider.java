package com.cloudalbum.publisher.focalpoint.provider;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OnnxRuntimeFocalPointProvider implements FocalPointProvider {

    private static final int INPUT_SIZE = 224;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Map<String, OrtSession> sessionCache = new ConcurrentHashMap<>();

    @Override
    public String getProviderType() {
        return "ONNX";
    }

    @Override
    public List<FocalPointResult> detect(InputStream imageInputStream, Map<String, Object> extraConfig) {
        String modelPath = (String) extraConfig.get("modelPath");
        if (modelPath == null || modelPath.isEmpty()) {
            log.warn("ONNX model path not configured");
            return Collections.emptyList();
        }

        try {
            BufferedImage image = ImageIO.read(imageInputStream);
            if (image == null) {
                return Collections.emptyList();
            }

            float[] inputTensor = preprocessImage(image);
            OrtSession session = getOrCreateSession(modelPath);
            if (session == null) {
                return Collections.emptyList();
            }

            long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor), shape);
                 OrtSession.Result result = session.run(Collections.singletonMap("input", tensor))) {

                float[][][] output = (float[][][]) result.get(0).getValue();
                List<FocalPointResult> results = new ArrayList<>();

                for (float[] detection : output[0]) {
                    if (detection.length >= 4) {
                        float x = detection[0];
                        float y = detection[1];
                        float confidence = detection[2];
                        String regionType = "SALIENCY";
                        double regionWidth = detection.length > 3 ? detection[3] : 0.0;
                        double regionHeight = detection.length > 4 ? detection[4] : 0.0;

                        if (confidence > 0.3f) {
                            results.add(new FocalPointResult(x, y, confidence, regionType, regionWidth, regionHeight));
                        }
                    }
                }

                return results;
            }
        } catch (Exception e) {
            log.warn("ONNX Runtime focal point detection failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private synchronized OrtSession getOrCreateSession(String modelPath) {
        return sessionCache.computeIfAbsent(modelPath, path -> {
            try {
                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                return env.createSession(path, opts);
            } catch (Exception e) {
                log.warn("Failed to create ONNX session for {}: {}", path, e.getMessage());
                return null;
            }
        });
    }

    private float[] preprocessImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage resized = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(
                image.getScaledInstance(INPUT_SIZE, INPUT_SIZE, java.awt.Image.SCALE_SMOOTH),
                0, 0, null);

        float[] tensor = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int[] pixels = resized.getRGB(0, 0, INPUT_SIZE, INPUT_SIZE, null, 0, INPUT_SIZE);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;

            r = (r - MEAN[0]) / STD[0];
            g = (g - MEAN[1]) / STD[1];
            b = (b - MEAN[2]) / STD[2];

            tensor[i] = r;
            tensor[INPUT_SIZE * INPUT_SIZE + i] = g;
            tensor[2 * INPUT_SIZE * INPUT_SIZE + i] = b;
        }

        return tensor;
    }
}
