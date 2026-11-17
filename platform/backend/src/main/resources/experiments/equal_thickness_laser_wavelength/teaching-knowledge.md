---
kb_type: teaching
experiment_code: equal_thickness_laser_wavelength
experiment_name: 等厚干涉测量激光波长
category: optics
doc: equal_thickness_laser_wavelength/teaching-knowledge.md
sectionKey: equal_thickness_laser_wavelength
version: v1.0
---

# 等厚干涉测量激光波长教学知识

## 实验原理

方法参考：[基于迈克尔逊干涉仪的波长计设计与实现](https://www.sy.uestc.edu.cn/cn/article/doi/10.12179/1672-4550.20200476)。本文平台步骤暂采用近轴条纹计数方法，实际装置和传动标定需与配套讲义核对。

本版本暂按迈克尔逊干涉仪等厚条纹移动计数法组织：轻微倾斜等效反射面产生近似平行直条纹，在固定参考线处计数，反射镜沿光轴移动 Δd 时光程差近似改变 2Δd，近轴条件下 λ≈2|Δd|/N。必须使用镜面实际位移；若读数装置带传动倍率，应先按仪器标定换算。本方案不等同于空气劈尖测厚；实际装置不同需更换对应方法。

## equal_thickness_laser_wavelength.step.1.teaching

光路与方法确认

- 确认采用迈克尔逊干涉仪及反射镜位移标定关系
- 调整两束光重合，并按设备规程获得可观察条纹

## equal_thickness_laser_wavelength.step.2.teaching

等厚条纹与计数基准

- 微调反射镜倾角，得到稳定的近似平行直条纹
- 设置固定计数参考线，沿单一方向预进微调机构以消除回程间隙

## equal_thickness_laser_wavelength.step.3.teaching

条纹移动与位移记录

- 缓慢单向移动反射镜，在固定参考线处累计条纹通过数
- 记录读数起点、终点及条纹数，重复多组并保留原始记录

## equal_thickness_laser_wavelength.step.4.teaching

波长计算与误差分析

- 将机构读数差换算为镜面实际位移 Δd
- 按 λ≈2
- Δd
- /N 求波长并换算为 nm，分析漏计、回程间隙和倾角误差

## equal_thickness_laser_wavelength.step.5.teaching

实验结束与资料整理

- 按现场规程停机或关闭光源、电源，将调节机构恢复安全位置
- 整理原始记录、数据表格和过程照片，检查报告所需信息并清理台面
