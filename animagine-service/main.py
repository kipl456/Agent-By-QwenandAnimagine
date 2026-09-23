import base64, io, uuid, torch
from fastapi import FastAPI
from pydantic import BaseModel
from diffusers import StableDiffusionXLPipeline

app = FastAPI(title="Animagine Image Service")

# ---- 1) 定义"请求长什么样":Java 会按这个格式把数据发过来 ----
class GenRequest(BaseModel):
    prompt: str                 # 英文提示词(必须)
    negative_prompt: str = ""   # 负面提示词(不要画什么),可空
    width: int = 832            # 画布宽
    height: int = 1216          # 画布高
    steps: int = 28             # 采样步数,越大越细越慢
    cfg: float = 7.0            # 提示词权重,太高会过饱和
    seed: int = -1              # 随机种子;-1 表示每次随机

# ---- 2) 启动时把模型加载进显存(只加载一次,别放接口里)----
#     优先用【本地已有的权重】:如果你本地/ComfyUI 里已经有 Animagine 的 .safetensors,
#     就用 from_single_file 直接读它,不必再从 HuggingFace 下 6~7GB。
#     注意:单文件 .safetensors 必须用 from_single_file;from_pretrained 只吃 diffusers 目录格式。
print("正在加载 Animagine XL 4.0 ...")
pipe = StableDiffusionXLPipeline.from_single_file(
    # 本地权重绝对路径;前面加 r 防止 Windows 反斜杠被当转义符
    r"D:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI\models\checkpoints\animagine-xl-4.0-opt.safetensors",
    torch_dtype=torch.float16,          # 用半精度,省显存
    use_safetensors=True,
)
# 【8GB 显存关键】把权重常驻在"内存",用到哪个组件才临时搬进显存。
# 效果:显存峰值从 ~6.5GB 降到 ~2~3GB;闲置时显存几乎全空,可以再开别的程序。
# 代价:出图稍慢一点点;但远好于"用完卸载、下次重读 6.5GB 硬盘"的做法。
# 注意:用了这行就【不要】再写 .to("cuda"),两者冲突。
pipe.enable_model_cpu_offload()
print("模型加载完成。")

# ---- 3) 对外提供"画一张图"的接口:POST /generate ----
@app.post("/generate")
def generate(r: GenRequest):
    # 若给定了 seed,则固定随机数生成器,这样可复现同一张图
    g = torch.Generator("cuda").manual_seed(r.seed) if r.seed >= 0 else None
    # 调用 diffusers 真正画图
    img = pipe(
        prompt=r.prompt,
        negative_prompt=r.negative_prompt,
        width=r.width,
        height=r.height,
        num_inference_steps=r.steps,
        guidance_scale=r.cfg,
        generator=g,
    ).images[0]
    # 把图片转成 base64 文本(方便 Java 存库/传输),再返回 JSON
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    b64 = base64.b64encode(buf.getvalue()).decode("utf-8")
    return {"image_b64": b64, "seed": r.seed}

# ---- 4) 健康检查,方便 Java/你确认它还活着 ----
@app.get("/health")
def health():
    return {"status": "ok", "model": "animagine-xl-4.0"}
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)

