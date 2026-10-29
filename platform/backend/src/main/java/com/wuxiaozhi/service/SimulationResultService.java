package com.wuxiaozhi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuxiaozhi.entity.*;
import com.wuxiaozhi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SimulationResultService {
    private final StudentExperimentProgressRepository progress;
    private final UserRepository users;
    private final StudentExperimentAccessService access;
    private final ClassRosterService roster;
    private final ObjectMapper mapper;
    private static final List<String> IDS = List.of("orientation", "beams", "focus", "exposure", "illumination", "drum", "dark", "operation");
    private static final List<Set<Integer>> KEYS = List.of(Set.of(1), Set.of(0), Set.of(0,1,2), Set.of(1), Set.of(0,1,2), Set.of(1), Set.of(2), Set.of(0,1,2));
    private static final Set<String> UNITS = Set.of("sample", "objective", "mirror", "lamp", "eyepiece", "camera");

    @Transactional
    public ObjectNode save(Long userId, String code, JsonNode input) {
        if (!"newton_rings".equals(code)) throw bad("该实验暂不支持仿真结果同步");
        users.lockById(userId).orElseThrow(() -> bad("用户不存在"));
        access.requireAssignedIfStudent(userId, code);
        ObjectNode result = normalize(input);
        var p = progress.findByUserIdAndExperimentCode(userId, code).orElseGet(() -> {
            var value = new StudentExperimentProgress(); value.setUserId(userId); value.setExperimentCode(code); return value;
        });
        JsonNode previous = read(p);
        // Late retries must not replace a newer submitted result.
        if (previous != null && previous.path("revision").asLong() >= result.path("revision").asLong()) return (ObjectNode) previous;
        result.put("studentClass", access.assignmentClass(userId, code));
        result.put("syncedAt", LocalDateTime.now().toString());
        p.setSimulationResultJson(result.toString());
        if (result.path("passed").asBoolean()) p.setSimulationCompleted(true);
        p.setUpdatedAt(LocalDateTime.now());
        progress.save(p);
        return result;
    }

    public List<ObjectNode> list(User teacher, String code, String className) {
        String scope = roster.scope(teacher, className);
        var students = roster.students(teacher, scope);
        if (students.isEmpty()) return List.of();
        List<ObjectNode> result = new ArrayList<>();
        for (var p : progress.findByExperimentCodeAndUserIdIn(code, students.stream().map(User::getId).toList())) {
            JsonNode saved = read(p);
            String cls = saved == null ? access.assignmentClass(p.getUserId(), code) : saved.path("studentClass").asText();
            if (!scope.isBlank() && !scope.equals(cls)) continue;
            var row = mapper.createObjectNode();
            row.put("userId", p.getUserId()); row.put("studentClass", cls);
            row.put("completed", p.isSimulationCompleted());
            if (saved != null) {
                for (String key : List.of("knowledgeScore", "challengeScore", "totalScore", "passed", "quizDone", "syncedAt")) row.set(key, saved.get(key));
                row.put("hasResult", true);
            } else row.put("hasResult", false);
            result.add(row);
        }
        return result;
    }

    public JsonNode detail(User teacher, String code, Long userId) {
        // Use the same roster and assignment-class checks as the list, including multi-class students.
        if (list(teacher, code, null).stream().noneMatch(r -> r.path("userId").asLong() == userId && r.path("hasResult").asBoolean()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可查看的预习结果");
        return read(progress.findByUserIdAndExperimentCode(userId, code).orElseThrow());
    }

    private JsonNode read(StudentExperimentProgress p) {
        if (p.getSimulationResultJson() == null) return null;
        try { return mapper.readTree(p.getSimulationResultJson()); }
        catch (Exception e) { throw new IllegalStateException("仿真结果无法读取", e); }
    }

    ObjectNode normalize(JsonNode input) {
        if (input == null || !input.isObject() || input.toString().length() > 200_000 || input.path("version").asInt() != 3) throw bad("仿真结果格式不正确");
        if (!input.path("quizConfirmed").asBoolean()) throw bad("请先最终确认预习题成绩");
        long revision = input.path("revision").asLong();
        if (revision <= 0 || revision > System.currentTimeMillis() + 300_000) throw bad("仿真提交时间不正确");
        var result = mapper.createObjectNode();
        result.put("version", 3); result.put("revision", revision);
        result.put("startedAt", input.path("startedAt").asLong());
        result.put("guidePassed", input.path("guidePassed").asBoolean());
        Set<String> installed = new LinkedHashSet<>();
        input.path("installed").forEach(n -> { if (UNITS.contains(n.asText())) installed.add(n.asText()); });
        result.set("installed", mapper.valueToTree(installed));
        JsonNode answers = input.path("answers");
        if (!answers.isArray() || answers.size() > 8) throw bad("预习题数量不正确");
        var normalized = result.putArray("answers");
        Set<String> seen = new HashSet<>(); int knowledge = 0;
        for (JsonNode answer : answers) {
            String id = answer.path("id").asText(); int index = IDS.indexOf(id);
            if (index < 0 || !seen.add(id) || !answer.path("selected").isArray()) throw bad("预习题答案不正确");
            Set<Integer> selected = new TreeSet<>();
            for (JsonNode choice : answer.path("selected")) {
                int max = Set.of("exposure", "drum").contains(id) ? 1 : 3;
                if (!choice.isIntegralNumber() || choice.asInt() < 0 || choice.asInt() > max || !selected.add(choice.asInt())) throw bad("选项不正确");
            }
            boolean correct = selected.equals(KEYS.get(index));
            if (correct) knowledge += 5;
            var item = normalized.addObject(); item.put("id", id); item.set("selected", mapper.valueToTree(selected)); item.put("correct", correct);
        }
        boolean quizDone = input.path("quizDone").asBoolean() && seen.size() == 8;
        if (!quizDone) throw bad("请完成全部预习题后再确认");
        result.put("quizConfirmed", true);
        result.put("reportReady", input.path("challenge").isObject());
        result.put("quizDone", quizDone); result.put("knowledgeScore", knowledge);
        JsonNode challenge = input.path("challenge");
        int score = 0; boolean challengePassed = false;
        if (!challenge.isMissingNode() && !challenge.isNull()) {
            if (!quizDone) throw bad("请先完成预习题");
            JsonNode o = challenge.path("optics");
            for (String key : List.of("focus", "reticle", "cameraFocus", "focusTarget", "reticleTarget", "cameraFocusTarget", "aperture", "mirrorAngle", "lampPosition", "lampPower", "exposure", "reading", "ringOrigin", "center")) {
                if (!o.path(key).isNumber() || !Double.isFinite(o.path(key).asDouble()) || Math.abs(o.path(key).asDouble()) > 10000) throw bad("成像参数不完整");
            }
            boolean assembled = installed.size() == 6;
            double aperture = n(o,"aperture"), focus = Math.abs(n(o,"focus")-n(o,"focusTarget")), camera = Math.abs(n(o,"cameraFocus")-n(o,"cameraFocusTarget"));
            double throughput = (o.path("lightOn").asBoolean() && assembled ? 1 : 0) * n(o,"lampPower")/100 * Math.exp(-Math.pow((n(o,"mirrorAngle")-45)/6,2)-Math.pow(n(o,"lampPosition")/19,2)) * Math.pow(aperture/70,2) * n(o,"exposure")/100;
            double stray = o.path("baseMirrorFolded").asBoolean() ? .015 : .22;
            double sharpness = Math.min(1, Math.exp(-(focus*.11*(.6+aperture/100)+camera*.09+Math.max(0,25-aperture)*.1)*.48));
            double contrast = Math.min(1, (.92/(1+stray*5)*sharpness)/(1+Math.max(0,throughput-1.1)*.9));
            boolean[] ok = {
                assembled && o.path("lightOn").asBoolean() && o.path("cameraOn").asBoolean() && o.path("videoConnected").asBoolean() && o.path("monitorOn").asBoolean(),
                assembled && o.path("lightOn").asBoolean() && Math.abs(n(o,"lampPosition"))<=5 && Math.abs(n(o,"mirrorAngle")-45)<=2,
                Math.abs(n(o,"reticle")-n(o,"reticleTarget"))<=3, focus<=3, o.path("baseMirrorFolded").asBoolean(),
                camera<=3 && sharpness>=.65 && contrast>=.45 && throughput*(.5+stray)>=.2 && throughput<=1.35 && aperture>=30,
                Math.abs(n(o,"reading")-n(o,"ringOrigin")-n(o,"center")*.02)<=.04
            };
            String[] labels = {"CCD 供电、接线和屏幕", "光源与半反镜照明", "十字丝清晰", "物镜高度与环纹焦点", "底座反光镜收起", "CCD 图像质量", "暗斑居中"};
            int[] points = {10,10,10,10,5,5,10};
            var out = result.putObject("challenge"); var rubric = out.putArray("rubric");
            for (int i=0;i<ok.length;i++) { var item=rubric.addObject(); item.put("label",labels[i]); item.put("points",points[i]); item.put("ok",ok[i]); if(ok[i]) score+=points[i]; }
            out.set("optics", o.deepCopy()); out.put("durationSeconds", Math.max(0, challenge.path("durationSeconds").asLong()));
            out.put("edits", Math.max(0, challenge.path("edits").asInt()));
            challengePassed = score==60;
        } else result.putNull("challenge");
        result.put("challengeScore", challenge.isObject() ? score : null);
        result.put("totalScore", challenge.isObject() ? knowledge+score : null);
        result.put("passed", installed.size()==6 && result.path("guidePassed").asBoolean() && quizDone && challengePassed && knowledge+score>=80);
        return result;
    }
    private static double n(JsonNode node, String key) { return node.path(key).asDouble(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
