"""C01: chạy terminal, không cần Streamlit hoặc Internet."""
import argparse
from datetime import date
from pathlib import Path
from .engine import ChatEngine
from .storage import SQLiteStore
from .demo import seed_demo
from .ai import configured_interpreter


def main():
    parser = argparse.ArgumentParser(description="Chat quản lý chi tiêu — Đức Duy")
    parser.add_argument("--db", default=str(Path(__file__).resolve().parents[1] / "data" / "chat.db"))
    parser.add_argument("--today", type=date.fromisoformat, help="Ngày thử YYYY-MM-DD; bỏ qua để dùng ngày Việt Nam")
    parser.add_argument("--session", default="cli")
    parser.add_argument("--seed-demo", action="store_true")
    args = parser.parse_args()
    store = SQLiteStore(args.db)
    if args.seed_demo:
        seed_demo(store)
    interpreter = configured_interpreter()
    engine = ChatEngine(store, args.session, args.today, interpreter=interpreter)
    print("Smart Money Bot |", interpreter.name)
    print("Gõ: Ăn trưa 45k; xác nhận lưu; hủy; thử lại; thoát.")
    print("Chọn nhãn bằng: danh mục Ăn uống. Bản nháp được giữ qua lần mở lại.")
    try:
        while True:
            text = input("Bạn: ")
            if text.strip().lower() in {"thoát", "thoat", "exit"}:
                break
            print("Bot:", engine.send(text))
    except (EOFError, KeyboardInterrupt):
        print("\nĐã kết thúc phiên terminal.")


if __name__ == "__main__":
    main()
