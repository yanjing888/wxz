<template>
  <div class="task-center custom-scroll">
    <header class="task-heading">
      <div>
        <p>物小智 · 学生工作台</p>
        <h1>{{ reportsOnly ? "实验报告" : "我的实验任务" }}</h1>
        <span>{{
          reportsOnly
            ? "实操结束后可离开实验室，在这里继续写作和提交报告。"
            : "一次专注一个实验。已结束实验的报告可以稍后提交。"
        }}</span>
      </div>
      <button
        @click="load"
        :disabled="tasks.loading"
        style="white-space: nowrap; flex-shrink: 0"
      >
        {{ tasks.loading ? "正在刷新…" : "刷新" }}
      </button>
    </header>
    <p v-if="tasks.error" class="task-error" role="alert">{{ tasks.error }}</p>
    <div v-else-if="!tasks.loaded" class="task-empty">正在加载实验安排…</div>
    <template v-else>
      <section v-if="!reportsOnly" class="current-task">
        <template v-if="tasks.current"
          ><div class="current-label">
            当前实验
            <span>{{ tasks.current.className || "教学班待确认" }}</span>
          </div>
          <h2>{{ tasks.current.experimentName }}</h2>
          <p>{{ currentHint }}</p>
          <p v-if="tasks.current.instructions" class="teacher-note">
            <b>本班要求</b>{{ tasks.current.instructions }}
          </p>
          <div class="current-footer">
            <span>{{
              tasks.current.reportDueAt
                ? "报告截止：" + time(tasks.current.reportDueAt)
                : "教师未设置报告截止时间"
            }}</span
            ><router-link class="task-primary" :to="currentRoute">{{
              tasks.current.simulationRequired &&
              !tasks.current.simulationCompleted
                ? "开始仿真预习"
                : tasks.current.activeSessionId
                  ? "继续实验"
                  : "进入实验台"
            }}</router-link>
          </div></template
        >
        <template v-else
          ><h2>当前没有待进行的实验</h2>
          <p>
            {{
              tasks.pendingReports.length
                ? "实操已完成，下面还有待提交的实验报告。"
                : "教师分配实验后会显示在这里。"
            }}
          </p></template
        >
      </section>
      <section class="task-section">
        <div class="section-heading">
          <h2>
            {{ reportsOnly ? "报告列表" : "待提交报告" }}
            <small>{{ rows.length }}</small>
          </h2>
          <select
            v-if="reportsOnly"
            v-model="reportFilter"
            aria-label="报告状态"
          >
            <option value="pending">待提交</option>
            <option value="submitted">已提交 / 已批改</option>
            <option value="all">全部报告</option></select
          ><router-link v-else :to="{ name: 'student-reports' }"
            >查看全部报告 →</router-link
          >
        </div>
        <div v-if="!rows.length" class="task-empty">
          {{
            reportFilter === "submitted"
              ? "暂无已提交报告。"
              : "暂无待提交报告。实操结束后，报告会进入此列表。"
          }}
        </div>
        <div v-else class="task-table-scroll">
          <table>
            <thead>
              <tr>
                <th>实验</th>
                <th>所属教学班</th>
                <th>报告期限</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in rows" :key="item.experimentCode">
                <td>
                  <strong>{{ item.experimentName }}</strong
                  ><span v-if="item.gradingCompleted" class="grade-line"
                    >教师评分 {{ item.teacherScore }}/100<span
                      v-if="item.teacherComment"
                    >
                      · {{ item.teacherComment }}</span
                    ></span
                  >
                </td>
                <td>{{ item.className || "未分班" }}</td>
                <td :class="{ overdue: isOverdue(item) }">
                  {{ item.reportDueAt ? time(item.reportDueAt) : "未设置"
                  }}<small v-if="isOverdue(item)"> · 已逾期，仍可提交</small>
                </td>
                <td>
                  <span class="task-status">{{
                    item.gradingCompleted
                      ? "已批改"
                      : item.reportCompleted
                        ? "已提交，待批改"
                        : "待提交"
                  }}</span>
                </td>
                <td>
                  <router-link
                    :to="{
                      name: 'after-center',
                      params: { code: item.experimentCode },
                    }"
                    >{{
                      item.reportCompleted ? "查看报告" : "继续写报告"
                    }}</router-link
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section v-if="!reportsOnly && queued.length" class="task-section">
        <div class="section-heading">
          <h2>
            后续实验 <small>{{ queued.length }}</small>
          </h2>
          <span>完成当前实操后依次开放</span>
        </div>
        <ol class="queued-list">
          <li v-for="item in queued" :key="item.experimentCode">
            <span>{{ item.experimentName }}</span
            ><span>{{ item.className || "未分班" }} · 等待当前实验完成</span>
          </li>
        </ol>
      </section>
    </template>
  </div>
</template>
<script setup>
import { computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ref } from "vue";
import { useTaskStore } from "../stores/tasks";
const route = useRoute(),
  tasks = useTaskStore(),
  reportFilter = ref("pending");
const reportsOnly = computed(() => route.name === "student-reports");
const rows = computed(() =>
  reportsOnly.value
    ? tasks.reports.filter(
        (x) =>
          reportFilter.value === "all" ||
          (reportFilter.value === "submitted"
            ? x.reportCompleted
            : !x.reportCompleted),
      )
    : tasks.pendingReports,
);
const queued = computed(() =>
  tasks.items.filter(
    (x) =>
      !x.labCompleted && x.experimentCode !== tasks.current?.experimentCode,
  ),
);
const currentRoute = computed(() =>
  tasks.current?.simulationRequired && !tasks.current.simulationCompleted
    ? {
        name: "prep-simulation",
        params: { code: tasks.current.experimentCode },
      }
    : { name: "lab", query: { exp: tasks.current?.experimentCode } },
);
const currentHint = computed(() =>
  tasks.current?.simulationRequired && !tasks.current.simulationCompleted
    ? "先完成仿真预习，再进入真实实验。"
    : tasks.current?.activeSessionId
      ? "你已经开始本次实操，可继续完成并保存数据。"
      : "按内置实验步骤完成实操并保存数据，结束后再提交报告。",
);
const time = (value) =>
  new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
const isOverdue = (item) =>
  !item.reportCompleted &&
  item.reportDueAt &&
  Date.parse(item.reportDueAt) < Date.now();
async function load() {
  try {
    await tasks.load();
  } catch {}
}
onMounted(load);
</script>
<style scoped>
.task-center {
  overflow: auto;
  background: #f5f7f9;
  padding: 32px max(24px, calc((100vw - 1200px) / 2));
  color: #243346;
  display: block !important;
}
.task-heading,
.section-heading,
.current-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.task-heading {
  margin-bottom: 26px;
}
.task-heading p {
  font-size: 12px;
  color: #738092;
  margin-bottom: 6px;
}
.task-heading h1 {
  font-size: 25px;
  font-weight: 650;
  margin-bottom: 8px;
}
.task-heading span,
.current-task p,
.section-heading > span,
.current-footer > span {
  font-size: 13px;
  color: #667588;
  line-height: 1.8;
}
.task-heading button,
select {
  background: white;
  border: 1px solid #d6dfe6;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
}
.current-task,
.task-section {
  background: white;
  border: 1px solid #dce3e8;
  border-radius: 9px;
  margin-bottom: 24px;
}
.current-task {
  padding: 24px;
  border-top: 3px solid #297f79;
}
.current-label {
  font-size: 12px;
  color: #2a7872;
  display: flex;
  gap: 14px;
}
.current-label span {
  color: #738092;
}
.current-task h2 {
  font-size: 22px;
  font-weight: 650;
  margin: 12px 0;
}
.current-footer {
  border-top: 1px solid #edf0f3;
  margin-top: 20px;
  padding-top: 18px;
}
.task-primary {
  background: #246e69;
  color: white;
  padding: 10px 18px;
  border-radius: 6px;
  font-size: 13px;
}
.teacher-note {
  white-space: pre-wrap;
  background: #f7f9fa;
  padding: 12px 14px;
  margin-top: 14px;
}
.teacher-note b {
  display: block;
  color: #314c57;
  margin-bottom: 3px;
}
.section-heading {
  padding: 18px 20px;
  border-bottom: 1px solid #e6ebef;
}
.section-heading h2 {
  font-size: 16px;
  font-weight: 650;
}
.section-heading small {
  font-size: 12px;
  color: #85919d;
  margin-left: 8px;
}
a {
  color: #276d74;
  font-size: 13px;
}
.task-table-scroll {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  text-align: left;
}
th {
  font-weight: 500;
  color: #6b7989;
  background: #fafbfd;
}
td,
th {
  padding: 15px 20px;
  border-bottom: 1px solid #edf0f3;
}
td strong {
  font-weight: 550;
}
.grade-line {
  display: block;
  font-size: 12px;
  color: #657889;
  max-width: 360px;
  margin-top: 6px;
}
.task-status {
  font-size: 12px;
  color: #576c7a;
  background: #f0f4f6;
  padding: 4px 8px;
  border-radius: 4px;
}
.overdue {
  color: #a05c20;
}
.task-empty {
  padding: 34px 22px;
  color: #81909e;
  font-size: 14px;
}
.queued-list {
  padding: 0 20px;
}
.queued-list li {
  display: flex;
  justify-content: space-between;
  padding: 15px 0;
  border-bottom: 1px solid #edf0f3;
  font-size: 13px;
}
.queued-list li span + span {
  color: #80909d;
}
.task-error {
  padding: 14px;
  background: #fff2ef;
  color: #a34435;
}
@media (max-width: 650px) {
  .task-center {
    padding: 18px 12px;
  }
  .task-heading,
  .current-footer {
    align-items: flex-start;
  }
  .task-heading h1 {
    font-size: 22px;
  }
  .current-footer {
    flex-direction: column;
  }
  .queued-list li {
    flex-direction: column;
    gap: 6px;
  }
  td,
  th {
    padding: 12px;
    white-space: nowrap;
  }
}
</style>
