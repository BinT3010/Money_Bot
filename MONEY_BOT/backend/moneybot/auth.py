"""Tài khoản thật lưu SQLite; PBKDF2, token ngẫu nhiên chỉ lưu bản băm, hết hạn 7 ngày."""
import hashlib
import hmac
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from uuid import uuid4


def password_hash(password, salt):
    return hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 600_000).hex()


class Auth:
    def __init__(self, path):
        self.path = str(path)
        with self.db() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE,salt TEXT,password TEXT);
                CREATE TABLE IF NOT EXISTS tokens(hash TEXT PRIMARY KEY,user_id TEXT,expires INTEGER);
                CREATE TABLE IF NOT EXISTS failures(email TEXT PRIMARY KEY,count INTEGER,until INTEGER);
            """)

    @contextmanager
    def db(self):
        db = sqlite3.connect(self.path, timeout=15)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def register(self, email, password):
        email = email.strip().lower()
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email) or len(email) > 254:
            raise ValueError("Email không hợp lệ.")
        self.check_password(password)
        salt, uid = secrets.token_hex(16), uuid4().hex
        try:
            with self.db() as db:
                db.execute("INSERT INTO users VALUES (?,?,?,?)", (uid, email, salt, password_hash(password, salt)))
        except sqlite3.IntegrityError:
            raise ValueError("Email đã được đăng ký.") from None
        return self.issue(uid, email)

    @staticmethod
    def check_password(password):
        if not 8 <= len(password) <= 128 or not any(c.isalpha() for c in password) or not any(c.isdigit() for c in password):
            raise ValueError("Mật khẩu dài 8–128 ký tự, có chữ và số.")

    def login(self, email, password):
        email = email.strip().lower()
        now = int(time.time())
        with self.db() as db:
            failed = db.execute("SELECT * FROM failures WHERE email=?", (email,)).fetchone()
            if failed and failed["count"] >= 5 and failed["until"] > now:
                raise ValueError("Thử sai quá nhiều; vui lòng đợi 5 phút.")
            user = db.execute("SELECT * FROM users WHERE email=?", (email,)).fetchone()
        salt = user["salt"] if user else "00" * 16
        valid = hmac.compare_digest(password_hash(password, salt), user["password"] if user else "0" * 64)
        if not user or not valid:
            with self.db() as db:
                count = failed["count"] + 1 if failed and failed["until"] > now else 1
                db.execute("INSERT OR REPLACE INTO failures VALUES (?,?,?)", (email, count, now + 300))
            raise ValueError("Email hoặc mật khẩu không đúng.")
        with self.db() as db:
            db.execute("DELETE FROM failures WHERE email=?", (email,))
        return self.issue(user["id"], email)

    def issue(self, uid, email):
        token = secrets.token_urlsafe(32)
        with self.db() as db:
            db.execute("DELETE FROM tokens WHERE expires<?", (int(time.time()),))
            db.execute("INSERT INTO tokens VALUES (?,?,?)", (hashlib.sha256(token.encode()).hexdigest(), uid, int(time.time()) + 7 * 86400))
        return {"token": token, "email": email, "user_id": uid}

    def user(self, token):
        with self.db() as db:
            row = db.execute("SELECT user_id FROM tokens WHERE hash=? AND expires>?", (hashlib.sha256(token.encode()).hexdigest(), int(time.time()))).fetchone()
        return row[0] if row else None

    def logout(self, token):
        with self.db() as db:
            db.execute("DELETE FROM tokens WHERE hash=?", (hashlib.sha256(token.encode()).hexdigest(),))

    def change(self, uid, old, new):
        self.check_password(new)
        with self.db() as db:
            u = db.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
            if not hmac.compare_digest(password_hash(old, u["salt"]), u["password"]):
                raise ValueError("Mật khẩu hiện tại không đúng.")
            salt = secrets.token_hex(16)
            db.execute("UPDATE users SET salt=?,password=? WHERE id=?", (salt, password_hash(new, salt), uid))
            db.execute("DELETE FROM tokens WHERE user_id=?", (uid,))
