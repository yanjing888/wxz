import { defineStore } from "pinia";
import { studentExperimentApi } from "../api";
let pending = null;
export const useTaskStore = defineStore("tasks", {
  state: () => ({
    items: [],
    loaded: false,
    loading: false,
    error: "",
    owner: "",
  }),
  getters: {
    current: (s) =>
      s.items.find((x) => x.currentTask) ||
      (s.items.some((x) => "currentTask" in x)
        ? null
        : s.items.find((x) => !x.labCompleted)),
    pendingReports: (s) =>
      s.items.filter((x) => x.labCompleted && !x.reportCompleted),
    reports: (s) => s.items.filter((x) => x.labCompleted),
    queued: (s) => s.items.filter((x) => !x.labCompleted && !x.currentTask),
  },
  actions: {
    async load() {
      const owner = localStorage.getItem("wxz_userId") || "guest",
        token = localStorage.getItem("wxz_token");
      if (this.owner !== owner) {
        this.$reset();
        this.owner = owner;
      }
      if (pending?.owner === owner) return pending.promise;
      this.loading = true;
      this.error = "";
      const promise = studentExperimentApi
        .listProgress()
        .then(({ data }) => {
          if (token === localStorage.getItem("wxz_token")) {
            this.items = data || [];
            this.loaded = true;
          }
        })
        .catch((e) => {
          if (this.owner === owner)
            this.error = e.response?.data?.message || "任务加载失败，请重试";
          throw e;
        })
        .finally(() => {
          if (pending?.promise === promise) pending = null;
          if (this.owner === owner) this.loading = false;
        });
      pending = { owner, promise };
      return promise;
    },
  },
});
