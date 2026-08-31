package com.wuxiaozhi.service;

import com.wuxiaozhi.dto.MechanicalScaleRecognizeRequest;
import com.wuxiaozhi.dto.MechanicalScaleRecognizeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

@Service
public class MechanicalScaleReadingService {

    private static final int MAX_WORK_SIZE = 1400;

    private final FileStorageService fileStorageService;

    public MechanicalScaleReadingService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public MechanicalScaleRecognizeResponse recognize(MechanicalScaleRecognizeRequest request) {
        if (request == null || request.getImageUrl() == null || request.getImageUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先上传刻度图片");
        }
        BufferedImage original = readImage(request.getImageUrl());
        BufferedImage image = resizeForWork(original);
        Axis axis = resolveAxis(request.getScaleAxis(), request.getFieldKey());
        Analysis analysis = analyze(image, axis, request);

        MechanicalScaleRecognizeResponse resp = new MechanicalScaleRecognizeResponse();
        resp.setFieldKey(request.getFieldKey() != null ? request.getFieldKey() : "");
        resp.setFieldLabel(request.getFieldLabel() != null ? request.getFieldLabel() : "");
        resp.setUnit("mm");
        resp.setConfidence(round(analysis.confidence, 2));
        resp.setWarnings(analysis.warnings);
        resp.setSteps(analysis.steps);
        resp.setQuality(analysis.quality);
        resp.setOk(analysis.value != null && analysis.confidence >= 0.45);
        if (analysis.value != null) {
            double value = round(analysis.value, decimalsFor(request.getScaleUnitPerTick()));
            resp.setValue(value);
            resp.setDisplay(formatValue(value, decimalsFor(request.getScaleUnitPerTick())) + " mm");
        }
        resp.setDebugImageUrl(writeDebugImage(image, axis, analysis));
        return resp;
    }

    private BufferedImage readImage(String imageUrl) {
        try {
            Path path = fileStorageService.resolve(imageUrl);
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取刻度图片");
            }
            return img;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "刻度图片不存在或格式不支持");
        }
    }

    private BufferedImage resizeForWork(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_WORK_SIZE) {
            return toRgb(source);
        }
        double scale = MAX_WORK_SIZE / (double) max;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }

    private Analysis analyze(BufferedImage image, Axis axis, MechanicalScaleRecognizeRequest request) {
        int w = image.getWidth();
        int h = image.getHeight();
        double[][] gray = toGray(image);
        double contrast = std(gray);
        double sharpness = sharpness(gray);
        double darkThreshold = clamp(mean(gray) - contrast * 0.45, 45, 185);

        List<Peak> peaks = detectPeaks(gray, axis, darkThreshold);
        Double pitch = estimatePitch(peaks, axis == Axis.VERTICAL ? h : w);
        Analysis a = new Analysis();
        a.axis = axis;
        a.reference = axis == Axis.VERTICAL ? h / 2.0 : w / 2.0;
        a.peaks = peaks;
        a.pitch = pitch;
        a.quality.put("axis", axis == Axis.VERTICAL ? "vertical" : "horizontal");
        a.quality.put("tickCount", peaks.size());
        a.quality.put("contrast", round(contrast, 1));
        a.quality.put("sharpness", round(sharpness, 1));
        if (pitch != null) {
            a.quality.put("tickPitchPx", round(pitch, 2));
        }
        a.quality.put("referencePx", round(a.reference, 1));

        if (contrast < 18) {
            a.warnings.add("图片对比度偏低，刻度线可能不够清楚");
        }
        if (sharpness < 22) {
            a.warnings.add("图片略模糊，建议贴近刻度并保持手机稳定后重拍");
        }
        if (peaks.size() < 8) {
            a.warnings.add("检测到的刻度线太少，请让刻度尺占满画面并包含 0 刻度");
        }
        if (pitch == null) {
            a.warnings.add("未能稳定识别等间距小刻度，请重拍并让刻度方向与准线垂直");
            a.confidence = 0.2;
            return a;
        }

        Double zero = findZeroPeak(peaks, pitch);
        if (zero == null) {
            a.warnings.add("没有找到清晰的 0 刻度起点，请把 0 刻度拍进画面");
            a.confidence = 0.28;
            return a;
        }

        a.zero = zero;
        a.quality.put("zeroPx", round(zero, 1));
        double unitPerTick = request.getScaleUnitPerTick() != null && request.getScaleUnitPerTick() > 0
                ? request.getScaleUnitPerTick()
                : 1.0;
        double zeroValue = request.getScaleZeroValue() != null ? request.getScaleZeroValue() : 0.0;
        double signedPixels = signedPixelsFromZero(a.reference, zero, axis, request.getScaleDirection());
        double value = zeroValue + signedPixels / pitch * unitPerTick;
        a.value = value;
        if (value < -0.5 || value > 70) {
            a.warnings.add("换算值超出常见刻度范围，请确认 0 刻度和绿色准线位置");
        }

        double stability = pitchStability(peaks, pitch);
        double tickScore = clamp(peaks.size() / 24.0, 0, 1);
        double contrastScore = clamp(contrast / 45.0, 0, 1);
        double sharpScore = clamp(sharpness / 55.0, 0, 1);
        a.confidence = clamp(0.18 + tickScore * 0.25 + stability * 0.32 + contrastScore * 0.12 + sharpScore * 0.13, 0, 0.98);
        if (!a.warnings.isEmpty()) {
            a.confidence = Math.max(0.15, a.confidence - Math.min(0.28, a.warnings.size() * 0.08));
        }

        a.steps.add("已按" + (axis == Axis.VERTICAL ? "竖直" : "水平") + "刻度提取等间距刻度线");
        a.steps.add("估算小格间距约 " + formatValue(pitch, 2) + " px / 格");
        a.steps.add("以画面绿色准线作为当前读数基准线，按刻度方向换算数值");
        if (a.confidence < 0.65) {
            a.warnings.add("当前识别置信度不高，建议学生核对标注图后再填入");
        }
        return a;
    }

    private double signedPixelsFromZero(double reference, double zero, Axis axis, String direction) {
        String dir = direction != null ? direction.trim().toLowerCase(Locale.ROOT) : "";
        if (dir.isBlank()) {
            dir = axis == Axis.VERTICAL ? "down" : "right";
        }
        return switch (dir) {
            case "up", "left" -> zero - reference;
            default -> reference - zero;
        };
    }

    private Axis resolveAxis(String explicitAxis, String fieldKey) {
        String axis = explicitAxis != null ? explicitAxis.trim().toLowerCase(Locale.ROOT) : "";
        if ("horizontal".equals(axis)) return Axis.HORIZONTAL;
        if ("vertical".equals(axis)) return Axis.VERTICAL;
        String key = fieldKey != null ? fieldKey.toLowerCase(Locale.ROOT) : "";
        if (key.contains("lateral") || key.contains("stage")) return Axis.HORIZONTAL;
        return Axis.VERTICAL;
    }

    private List<Peak> detectPeaks(double[][] gray, Axis axis, double darkThreshold) {
        int h = gray.length;
        int w = gray[0].length;
        int axisLen = axis == Axis.VERTICAL ? h : w;
        int crossLen = axis == Axis.VERTICAL ? w : h;
        double[] scores = new double[axisLen];
        int minRun = Math.max(4, crossLen / 220);
        int maxRun = Math.max(28, crossLen / 4);

        for (int pos = 1; pos < axisLen - 1; pos++) {
            int run = 0;
            double score = 0;
            for (int cross = 1; cross < crossLen - 1; cross++) {
                double v = axis == Axis.VERTICAL ? gray[pos][cross] : gray[cross][pos];
                boolean dark = v < darkThreshold;
                if (dark) {
                    run++;
                } else {
                    score += scoreRun(run, minRun, maxRun);
                    run = 0;
                }
            }
            score += scoreRun(run, minRun, maxRun);
            scores[pos] = score;
        }

        double[] smooth = smooth(scores, 2);
        double max = Arrays.stream(smooth).max().orElse(0);
        double threshold = Math.max(percentile(smooth, 0.82), max * 0.16);
        List<Peak> raw = new ArrayList<>();
        for (int i = 2; i < smooth.length - 2; i++) {
            if (smooth[i] < threshold) continue;
            if (smooth[i] >= smooth[i - 1] && smooth[i] >= smooth[i + 1]
                    && smooth[i] >= smooth[i - 2] && smooth[i] >= smooth[i + 2]) {
                raw.add(new Peak(i, smooth[i]));
            }
        }
        return mergeClosePeaks(raw, Math.max(2, axisLen / 420));
    }

    private double scoreRun(int run, int minRun, int maxRun) {
        if (run < minRun || run > maxRun) {
            return 0;
        }
        return Math.min(run, maxRun * 0.55);
    }

    private List<Peak> mergeClosePeaks(List<Peak> raw, int minGap) {
        if (raw.isEmpty()) return raw;
        List<Peak> merged = new ArrayList<>();
        Peak best = raw.get(0);
        for (int i = 1; i < raw.size(); i++) {
            Peak p = raw.get(i);
            if (p.pos - best.pos <= minGap) {
                if (p.score > best.score) {
                    best = p;
                }
            } else {
                merged.add(best);
                best = p;
            }
        }
        merged.add(best);
        return merged;
    }

    private Double estimatePitch(List<Peak> peaks, int axisLen) {
        if (peaks.size() < 8) return null;
        int min = Math.max(3, axisLen / 500);
        int max = Math.max(min + 1, axisLen / 16);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 1; i < peaks.size(); i++) {
            int d = peaks.get(i).pos - peaks.get(i - 1).pos;
            if (d >= min && d <= max) {
                counts.merge(d, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) return null;
        int mode = counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingInt(e -> -e.getKey()))
                .map(Map.Entry::getKey)
                .orElse(0);
        if (mode <= 0 || counts.getOrDefault(mode, 0) < 3) {
            return null;
        }
        List<Integer> near = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            int d = peaks.get(i).pos - peaks.get(i - 1).pos;
            if (Math.abs(d - mode) <= Math.max(2, mode * 0.28)) {
                near.add(d);
            }
        }
        if (near.size() < 3) return null;
        Collections.sort(near);
        return near.get(near.size() / 2).doubleValue();
    }

    private Double findZeroPeak(List<Peak> peaks, double pitch) {
        int bestStart = -1;
        int bestRun = 0;
        for (int i = 0; i < peaks.size(); i++) {
            int run = 1;
            int last = peaks.get(i).pos;
            for (int j = i + 1; j < peaks.size(); j++) {
                int d = peaks.get(j).pos - last;
                if (Math.abs(d - pitch) <= Math.max(2.0, pitch * 0.35)) {
                    run++;
                    last = peaks.get(j).pos;
                } else if (d > pitch * 1.8) {
                    break;
                }
            }
            if (run > bestRun) {
                bestRun = run;
                bestStart = i;
            }
        }
        if (bestStart < 0 || bestRun < 5) {
            return peaks.get(0).pos * 1.0;
        }
        return peaks.get(bestStart).pos * 1.0;
    }

    private double pitchStability(List<Peak> peaks, double pitch) {
        List<Double> errors = new ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            int d = peaks.get(i).pos - peaks.get(i - 1).pos;
            if (d <= 0 || d > pitch * 1.8) continue;
            errors.add(Math.abs(d - pitch) / pitch);
        }
        if (errors.size() < 3) return 0.35;
        Collections.sort(errors);
        double median = errors.get(errors.size() / 2);
        return clamp(1.0 - median * 2.2, 0, 1);
    }

    private String writeDebugImage(BufferedImage image, Axis axis, Analysis a) {
        try {
            BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(Math.max(2f, Math.max(image.getWidth(), image.getHeight()) / 360f)));
            drawLine(g, axis, a.reference, image.getWidth(), image.getHeight(), new Color(22, 163, 74), "读数准线");
            if (a.zero != null) {
                drawLine(g, axis, a.zero, image.getWidth(), image.getHeight(), new Color(239, 68, 68), "0刻度");
            }
            g.setColor(new Color(250, 204, 21, 170));
            for (Peak p : a.peaks) {
                if (axis == Axis.VERTICAL) {
                    g.drawLine(image.getWidth() - 36, p.pos, image.getWidth() - 10, p.pos);
                } else {
                    g.drawLine(p.pos, image.getHeight() - 36, p.pos, image.getHeight() - 10);
                }
            }
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return fileStorageService.storeImageBytes(baos.toByteArray(), ".png");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void drawLine(Graphics2D g, Axis axis, double pos, int w, int h, Color color, String label) {
        g.setColor(color);
        int p = (int) Math.round(pos);
        if (axis == Axis.VERTICAL) {
            g.drawLine(0, p, w, p);
            g.drawString(label, 10, Math.max(16, p - 8));
        } else {
            g.drawLine(p, 0, p, h);
            g.drawString(label, Math.min(w - 70, p + 8), 18);
        }
    }

    private double[][] toGray(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        double[][] gray = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = r * 0.299 + g * 0.587 + b * 0.114;
            }
        }
        return gray;
    }

    private double mean(double[][] values) {
        double sum = 0;
        int count = 0;
        for (double[] row : values) {
            for (double v : row) {
                sum += v;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private double std(double[][] values) {
        double mean = mean(values);
        double sum = 0;
        int count = 0;
        for (double[] row : values) {
            for (double v : row) {
                double d = v - mean;
                sum += d * d;
                count++;
            }
        }
        return count == 0 ? 0 : Math.sqrt(sum / count);
    }

    private double sharpness(double[][] gray) {
        int h = gray.length;
        int w = gray[0].length;
        if (w < 3 || h < 3) return 0;
        double sum = 0;
        int count = 0;
        for (int y = 1; y < h - 1; y += 2) {
            for (int x = 1; x < w - 1; x += 2) {
                double gx = gray[y][x + 1] - gray[y][x - 1];
                double gy = gray[y + 1][x] - gray[y - 1][x];
                sum += Math.sqrt(gx * gx + gy * gy);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private double[] smooth(double[] source, int radius) {
        double[] out = new double[source.length];
        for (int i = 0; i < source.length; i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(source.length - 1, i + radius); j++) {
                sum += source[j];
                count++;
            }
            out[i] = count == 0 ? source[i] : sum / count;
        }
        return out;
    }

    private double percentile(double[] values, double p) {
        if (values.length == 0) return 0;
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int idx = (int) Math.max(0, Math.min(copy.length - 1, Math.round((copy.length - 1) * p)));
        return copy[idx];
    }

    private int decimalsFor(Double unitPerTick) {
        double unit = unitPerTick != null && unitPerTick > 0 ? unitPerTick : 1.0;
        if (unit < 0.01) return 3;
        if (unit < 0.1) return 2;
        return 1;
    }

    private String formatValue(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private double round(double value, int decimals) {
        double p = Math.pow(10, decimals);
        return Math.round(value * p) / p;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Axis {
        VERTICAL,
        HORIZONTAL
    }

    private record Peak(int pos, double score) { }

    private static final class Analysis {
        Axis axis = Axis.VERTICAL;
        List<Peak> peaks = List.of();
        Double pitch;
        Double zero;
        Double value;
        double reference;
        double confidence;
        List<String> warnings = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        Map<String, Object> quality = new LinkedHashMap<>();
    }
}
