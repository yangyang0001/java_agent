import json
import requests
from openai import OpenAI

# ===========================
# 连接 Ollama
# ===========================

client = OpenAI(
    api_key="ollama",
    base_url="http://localhost:11434/v1"
)

MODEL = "qwen2.5:3b"

# ===========================
# Tool
# ===========================

def get_weather(city: str):

    url = f"https://wttr.in/{city}?format=j1"

    try:

        r = requests.get(url, timeout=10)

        data = r.json()

        current = data["current_condition"][0]

        return {
            "city": city,
            "weather": current["weatherDesc"][0]["value"],
            "temp": current["temp_C"]
        }

    except Exception as e:

        return {
            "error": str(e)
        }


# ===========================
# Tool Schema
# ===========================

tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市天气",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市"
                    }
                },
                "required": ["city"]
            }
        }
    }
]


# ===========================
# User
# ===========================

messages = [

    {
        "role": "system",
        "content": "你是一个旅游助手，如果需要查询天气，请调用工具。"
    },

    {
        "role": "user",
        "content": "北京今天天气怎么样？"
    }

]

# ===========================
# 第一次调用 LLM
# ===========================

response = client.chat.completions.create(

    model=MODEL,

    messages=messages,

    tools=tools,

    tool_choice="auto"

)

msg = response.choices[0].message

messages.append(msg)

# ===========================
# Tool Calling
# ===========================

if msg.tool_calls:

    for tool_call in msg.tool_calls:

        print("模型调用工具：", tool_call.function.name)

        args = json.loads(tool_call.function.arguments)

        result = get_weather(args["city"])

        print("工具返回：")

        print(result)

        messages.append({

            "role": "tool",

            "tool_call_id": tool_call.id,

            "content": json.dumps(result, ensure_ascii=False)

        })

# ===========================
# 第二次调用 LLM
# ===========================

response2 = client.chat.completions.create(

    model=MODEL,

    messages=messages

)

print("\n最终回答：\n")

print(response2.choices[0].message.content)