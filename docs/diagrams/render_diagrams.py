"""Render AppForge's editable SVG diagrams and PNG previews.

Requirements: Python 3, Pillow, svgwrite, Inkscape and Noto Sans CJK SC.
Run this script from any working directory. Outputs are placed beside it.
Diagram content describes the current implementation, including migration limits.
"""

from pathlib import Path
import subprocess

from PIL import Image, ImageFont
import svgwrite


OUT = Path(__file__).resolve().parent
FONT_FILE = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
BG, INK, MUTED, BORDER = "#FBF9F5", "#242532", "#6D707D", "#DDDCD8"
ORANGE, BLUE, PURPLE = "#E85B36", "#3979BF", "#7861B4"
GREEN, TEAL, GOLD = "#26836A", "#258D9B", "#AD8426"
PALE = {
    BLUE: "#EDF4FC", PURPLE: "#F2EEF9", GREEN: "#EDF6F0",
    TEAL: "#EAF6F7", ORANGE: "#FFF0E8", GOLD: "#FBF6E5",
}


class Diagram:
    def __init__(self, filename, w, h, title, subtitle, number):
        self.filename, self.w, self.h = filename, w, h
        self.d = svgwrite.Drawing(str(OUT / (filename + ".svg")),
                                 size=(w, h), viewBox=f"0 0 {w} {h}")
        self.d.set_desc(title=title, desc=subtitle)
        self.rect(0, 0, w, h, fill=BG, stroke="none", radius=0)
        self.text(70, 61, f"APPFORGE  /  {number}", 22, ORANGE, True)
        self.text(70, 125, title, 49, INK, True)
        self.text(70, 171, subtitle, 23, MUTED)
        self.line([(70, 198), (w - 70, 198)], BORDER, 2)
        self.line([(70, h - 82), (w - 70, h - 82)], BORDER, 2)
        self.text(70, h - 40, "APPFORGE · AI 网页生成平台", 20, MUTED)
        self.text(w - 70, h - 40, "依据当前源码 · 迁移边界见 README",
                  20, MUTED, anchor="end")

    def text(self, x, y, value, size=28, color=INK, bold=False,
             anchor="start", max_width=None):
        if max_width:
            font = ImageFont.truetype(FONT_BOLD if bold else FONT_FILE,
                                     size=size, index=2)
            assert font.getlength(value) <= max_width, (value, max_width)
        self.d.add(self.d.text(
            value, insert=(x, y), fill=color, text_anchor=anchor,
            font_family="Noto Sans CJK SC", font_size=size,
            font_weight=700 if bold else 400))

    def rect(self, x, y, w, h, fill="white", stroke=BORDER,
             radius=18, width=2):
        assert x >= 0 and y >= 0 and x+w <= self.w and y+h <= self.h
        self.d.add(self.d.rect(insert=(x, y), size=(w, h), rx=radius,
                               fill=fill, stroke=stroke, stroke_width=width))

    def line(self, points, color=MUTED, width=3, arrow=False, dash=False):
        line = self.d.polyline(points, fill="none", stroke=color,
                               stroke_width=width,
                               stroke_linejoin="round", stroke_linecap="round")
        if dash:
            line.dasharray([8, 7])
        if arrow:
            marker = self.d.marker(insert=(9, 5), size=(11, 10),
                                   orient="auto", markerUnits="userSpaceOnUse")
            marker.add(self.d.path(d="M 0 0 L 10 5 L 0 10 Z", fill=color))
            self.d.defs.add(marker)
            line["marker-end"] = marker.get_funciri()
        self.d.add(line)

    def pill(self, x, y, text, color=ORANGE, w=None):
        w = w or len(text)*22+32
        self.rect(x, y, w, 38, PALE[color], "none", radius=12)
        self.text(x+w/2, y+27, text, 20, color, True, anchor="middle",
                  max_width=w-12)

    def node(self, x, y, w, h, title, lines=(), color=BLUE,
             title_size=30, body_size=24):
        self.rect(x, y, w, h, PALE[color], color, radius=17)
        title_y = y + (h/2+11 if not lines else 43)
        self.text(x+w/2, title_y, title, title_size, INK, True,
                  anchor="middle", max_width=w-30)
        for i, t in enumerate(lines):
            self.text(x+w/2, y+82+i*34, t, body_size, MUTED,
                      anchor="middle", max_width=w-30)

    def diamond(self, cx, cy, w, h, lines):
        self.d.add(self.d.polygon(
            [(cx, cy-h/2), (cx+w/2, cy), (cx, cy+h/2), (cx-w/2, cy)],
            fill=PALE[GOLD], stroke=GOLD, stroke_width=3))
        top = cy-(len(lines)-1)*17+10
        for i, t in enumerate(lines):
            self.text(cx, top+i*34, t, 27, INK, True, anchor="middle")

    def save(self):
        self.d.save()
        subprocess.run([
            "inkscape", str(OUT/(self.filename+".svg")),
            "--export-type=png", "--export-width=2400",
            "--export-filename="+str(OUT/(self.filename+".png")),
        ], check=True, stdout=subprocess.DEVNULL)


def workflow():
    d = Diagram("appforge-workflow", 1700, 1930,
                "从想法到网页交付",
                "需求输入 → 生成与校验 → 预览修改 → 版本、发布与源码交付", "01")
    # Connectors are placed first so labels and nodes remain unobstructed.
    for y1, y2 in [(322, 360), (470, 510), (620, 660), (798, 844)]:
        d.line([(690, y1), (690, y2)], arrow=True)
    d.line([(865, 920), (1130, 920)], arrow=True, color=ORANGE)
    d.text(987, 899, "否", 25, ORANGE, anchor="middle")
    d.line([(1335, 845), (1335, 412), (1000, 412)],
           color=ORANGE, arrow=True, dash=True)
    d.pill(1157, 482, "调整需求后重试", ORANGE, 260)
    d.line([(690, 996), (690, 1060)], arrow=True, color=GREEN)
    d.text(720, 1038, "是", 25, GREEN)
    d.line([(690, 1178), (690, 1220)], arrow=True)
    d.line([(515, 1298), (305, 1298), (305, 1470)], arrow=True)
    d.text(326, 1400, "需要修改", 25, BLUE)
    d.line([(865, 1298), (1280, 1298), (1280, 1470)], arrow=True)
    d.text(1095, 1276, "满意", 25, GREEN)
    d.line([(65, 1544), (35, 1544), (35, 729), (380, 729)],
           arrow=True, color=PURPLE)
    d.line([(1280, 1618), (1280, 1685)], arrow=True, color=GREEN)
    d.line([(1530, 1738), (1610, 1738), (1610, 1119), (1000, 1119)],
           arrow=True, color=TEAL)
    d.pill(1273, 1206, "恢复历史与成功版本", TEAL, 300)

    d.node(380, 230, 620, 92, "注册 / 登录", color=BLUE)
    d.node(380, 360, 620, 110, "输入需求，创建应用",
           ["也可进入已有作品继续创作"], BLUE)
    d.node(380, 510, 620, 110, "确定代码生成模式",
           ["用户指定优先 · HTML / 多文件 / Vue"], PURPLE)
    d.node(380, 660, 620, 138, "模型生成与受限文件操作",
           ["SSE 推送文本、工具事件和文件进度",
            "Vue 工程额外执行 Docker 构建"], PURPLE)
    d.diamond(690, 920, 350, 152, ["生成与校验", "是否成功？"])
    d.node(1130, 845, 410, 148, "失败或停止",
           ["保留上一成功版本",
            "首次失败时暂无成功预览"], ORANGE, title_size=29)
    d.node(380, 1060, 620, 118, "保存成功版本，预览网站",
           ["支持源码查看、文件对比与设备预览"], BLUE)
    d.diamond(690, 1298, 350, 156, ["是否需要", "继续修改？"])
    d.node(65, 1470, 480, 148, "可视化选区 / 继续对话",
           ["提交新的修改要求",
            "重新生成、构建并校验"], PURPLE, title_size=29)
    d.node(1030, 1470, 500, 148, "发布作品 / 下载源码",
           ["发布绑定指定成功版本",
            "源码 ZIP 可独立下载"], GREEN)
    d.node(1030, 1685, 500, 106, "重新进入应用",
           ["加载对话历史，继续创作"], TEAL)
    d.node(95, 1690, 690, 106, "可选能力：自动封面 / 自定义封面",
           ["封面按版本异步生成，不是下载前置步骤"], GREEN,
           title_size=27, body_size=22)
    d.save()


def layer(d, y, h, label, color):
    d.rect(60, y, 180, h, PALE[color], color, radius=20)
    d.text(150, y+h/2+11, label, 31, INK, True, anchor="middle",
           max_width=160)
    d.rect(265, y, 1470, h, "#FFFFFF", BORDER, radius=20)


def card(d, x, y, w, h, title, rows, color, tag=None):
    d.rect(x, y, w, h, PALE[color], color, radius=17)
    d.text(x+26, y+45, title, 29, INK, True, max_width=w-50)
    d.line([(x+25, y+65), (x+w-25, y+65)], color, 1.5)
    for i, row in enumerate(rows):
        d.text(x+26, y+104+i*40, row, 24, MUTED, max_width=w-48)
    if tag:
        d.pill(x+20, y+h-53, tag, color, w-40)


def architecture():
    d = Diagram("appforge-architecture", 1800, 1910,
                "系统架构与模块边界",
                "前后端分离 · 保留单体基线 · 微服务迁移中的实际分层", "02")
    layer(d, 235, 150, "客户端", BLUE)
    d.node(300, 262, 680, 98, "Vue 创作工作台",
           ["需求输入 · 对话 · 预览 · 源码与版本"], BLUE,
           title_size=30, body_size=23)
    d.node(1010, 262, 690, 98, "管理入口",
           ["用户与应用管理 · 精选审核 · 重置码"], BLUE,
           title_size=30, body_size=23)
    d.line([(1000, 385), (1000, 425)], arrow=True)

    layer(d, 425, 160, "接入方式", GOLD)
    d.node(300, 453, 680, 104, "单体：Spring MVC / SSE · 8123",
           ["前端开发代理直连后端"], GOLD, title_size=28, body_size=23)
    d.node(1010, 453, 690, 104, "微服务：Higress HTTP · 18080",
           ["转发到 user 8124 / app 8125"], GOLD,
           title_size=28, body_size=23)
    d.line([(1000, 585), (1000, 625)], arrow=True)

    layer(d, 625, 650, "业务与执行", GREEN)
    card(d, 300, 655, 410, 350, "user 用户服务",
         ["注册 / 登录 / 退出", "昵称与密码修改",
          "角色权限校验", "Redis 共享登录态"],
         BLUE, "HTTP 8124 · Triple 50051")
    card(d, 735, 655, 520, 350, "app 应用服务",
         ["应用 / 对话 / 附件", "多模式生成 / 可视化修改",
          "版本 / 发布 / 源码下载", "封面任务 / 密码找回"],
         GREEN, "HTTP 8125 · Triple 50053")
    card(d, 1280, 655, 420, 350, "screenshot 截图服务",
         ["独立启动入口", "Selenium 网页渲染",
          "截图上传 COS", "提供内部 Dubbo 接口"],
         TEAL, "HTTP 8127 · Triple 50052")
    d.line([(995, 1005), (995, 1040), (650, 1040), (650, 1070)],
           arrow=True, color=PURPLE)
    d.line([(995, 1040), (1360, 1040), (1360, 1070)],
           arrow=True, color=GREEN)
    d.node(300, 1070, 700, 170, "ai 依赖模块 · 由 app 进程加载",
           ["LangChain4j / 模型配置 / 文件工具",
            "应用记忆 / 输入护栏 / 模型事件"], PURPLE,
           title_size=28, body_size=24)
    d.node(1030, 1070, 670, 170, "app 进程内执行能力",
           ["Docker 固定构建 / 超时与取消",
            "本地封面队列 / 渲染与有限重试"], GREEN,
           title_size=28, body_size=24)
    d.line([(1000, 1275), (1000, 1315)], arrow=True)

    layer(d, 1315, 180, "治理与观测", PURPLE)
    d.node(300, 1345, 700, 122, "Dubbo / Nacos",
           ["内部服务契约与注册 · 当前仍有本地调用"], PURPLE,
           title_size=29, body_size=23)
    d.node(1030, 1345, 670, 122, "Redisson / Micrometer",
           ["接口限流 · 请求、错误、Token 与耗时"], PURPLE,
           title_size=29, body_size=23)
    d.line([(1000, 1495), (1000, 1535)], arrow=True)

    layer(d, 1535, 170, "存储层", ORANGE)
    for x, title, body, color in [
        (300, "MySQL", "业务 / 版本 / 对话 / 任务", BLUE),
        (655, "Redis", "Session / 记忆 / 缓存", ORANGE),
        (1010, "本地文件", "源码 / 构建产物 / 附件", TEAL),
        (1365, "腾讯云 COS", "封面 / 附件对象存储", GREEN),
    ]:
        d.node(x, 1566, 335, 108, title, [body], color,
               title_size=29, body_size=19)

    d.text(75, 1757, "说明：分层图不是独立部署清单；单体与微服务接入二选一。", 23, MUTED)
    d.text(75, 1795, "AI 尚未独立成服务；app 封面链路仍使用本地渲染，尚未切换到截图服务。", 23, MUTED)
    d.save()


def overview():
    d = Diagram("appforge-capability-map", 2200, 1680,
                "功能与技术全景",
                "围绕网页生成任务组织产品能力、工程实现与验证边界", "03")
    left_x, right_x, cw, ch = 65, 1515, 620, 360
    ys = [245, 705, 1165]
    hub = (875, 704, 450, 324)
    specs = [
        (left_x, ys[0], BLUE, "产品与交付",
         ["三种生成模式与多轮修改", "预览、源码与设备视图",
          "成功版本保存与历史恢复", "源码 ZIP、发布与下线",
          "公开作品与精选审核"], None),
        (left_x, ys[1], PURPLE, "AI 生成能力",
         ["LangChain4j 声明式服务", "受限文件工具与模式选择",
          "SSE 文本、工具与进度事件", "按应用管理记忆与摘要",
          "输入护栏与异常反馈"], None),
        (left_x, ys[2], GREEN, "工程与可靠性",
         ["Docker 固定命令构建", "资源限制、超时与任务停止",
          "失败保留上一成功版本", "预览版本与发布版本独立",
          "封面队列与有限重试"], None),
        (right_x, ys[0], TEAL, "服务拆分与治理",
         ["Maven 七模块组织", "user / app / screenshot",
          "Dubbo 内部接口与 Nacos 注册", "Higress 统一 HTTP 路由"],
         "迁移中：AI 仍是进程内依赖"),
        (right_x, ys[1], ORANGE, "数据、会话与安全",
         ["MySQL 业务与版本持久化", "Redis Session 与对话记忆",
          "Caffeine 缓存 AI Service", "Redisson 用户 / IP / 接口限流",
          "文件路径与作品归属校验"], None),
        (right_x, ys[2], GOLD, "测试与运行观测",
         ["JUnit / Vitest / GitHub Actions",
          "请求、错误、Token 与耗时指标",
          "Prometheus / Grafana 按环境配置",
          "真实模型与 Docker 专项验证"],
         "微服务全链路仍需运行验收"),
    ]
    # One trunk per category: six branches, three on each side.
    for x, y, color, title, rows, tag in specs:
        if x == left_x:
            d.line([(hub[0], 866), (790, 866),
                    (790, y+ch/2), (x+cw, y+ch/2)], color, 4)
        else:
            d.line([(hub[0]+hub[2], 866), (1410, 866),
                    (1410, y+ch/2), (x, y+ch/2)], color, 4)
    for x, y, color, title, rows, tag in specs:
        card(d, x, y, cw, ch, title, rows, color, tag)
    d.rect(*hub, "white", ORANGE, radius=36, width=4)
    d.text(1100, 786, "APPFORGE", 42, ORANGE, True, anchor="middle")
    d.text(1100, 852, "AI 网页生成平台", 36, INK, True, anchor="middle")
    d.text(1100, 916, "生成 · 修改 · 版本 · 交付", 23, MUTED,
           anchor="middle", max_width=410)
    d.save()


def main():
    for draw in [workflow, architecture, overview]:
        draw()
    thumbs = []
    for filename in ["appforge-workflow", "appforge-architecture",
                     "appforge-capability-map"]:
        image = Image.open(OUT/(filename+".png")).convert("RGB")
        image.thumbnail((620, 700))
        thumbs.append(image)
    sheet = Image.new("RGB", (1900, 750), "#E9E6DF")
    for i, image in enumerate(thumbs):
        sheet.paste(image, (i*630+(630-image.width)//2,
                            (750-image.height)//2))
    sheet.save(OUT/"contact-sheet.png")
    for path in OUT.glob("appforge-*"):
        print(path.name, path.stat().st_size)


if __name__ == "__main__":
    main()
