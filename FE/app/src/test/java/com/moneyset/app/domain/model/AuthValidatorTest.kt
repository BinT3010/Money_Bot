package com.moneyset.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `valid inputs have no errors`() {
        assertNull(AuthValidator.nameError("Phúc"))
        assertNull(AuthValidator.emailError("phuc@moneyset.vn"))
        assertNull(AuthValidator.emailError("  a.b+c@mail.com  "))
        assertNull(AuthValidator.loginPasswordError("x"))
        assertNull(AuthValidator.newPasswordError("Demo1234"))
        assertNull(AuthValidator.confirmPasswordError("Demo1234", "Demo1234"))
        assertNull(AuthValidator.otpError("123456"))
    }

    @Test
    fun `name is required and not too short`() {
        assertEquals("Vui lòng nhập họ tên", AuthValidator.nameError("  "))
        assertEquals("Họ tên quá ngắn", AuthValidator.nameError("A"))
    }

    @Test
    fun `malformed emails are rejected`() {
        assertEquals("Vui lòng nhập email", AuthValidator.emailError("   "))
        listOf("phuc", "phuc@", "phuc@mail", "@mail.com", "ph uc@mail.com").forEach {
            assertEquals("Email không hợp lệ ($it)", "Email không hợp lệ", AuthValidator.emailError(it))
        }
    }

    @Test
    fun `login only requires a password`() {
        assertEquals("Vui lòng nhập mật khẩu", AuthValidator.loginPasswordError(""))
    }

    @Test
    fun `new password needs 8 chars with letters and digits`() {
        assertEquals("Vui lòng nhập mật khẩu", AuthValidator.newPasswordError(""))
        assertEquals("Mật khẩu tối thiểu 8 ký tự", AuthValidator.newPasswordError("abc123"))
        assertEquals("Mật khẩu cần có cả chữ và số", AuthValidator.newPasswordError("abcdefgh"))
        assertEquals("Mật khẩu cần có cả chữ và số", AuthValidator.newPasswordError("12345678"))
    }

    @Test
    fun `confirm password must match`() {
        assertEquals("Vui lòng nhập lại mật khẩu", AuthValidator.confirmPasswordError("Demo1234", ""))
        assertEquals("Mật khẩu nhập lại không khớp", AuthValidator.confirmPasswordError("Demo1234", "Demo12345"))
    }

    @Test
    fun `otp must be exactly 6 digits`() {
        listOf("", "12345", "1234567", "12a456").forEach {
            assertEquals("Mã xác nhận gồm 6 chữ số", AuthValidator.otpError(it))
        }
    }

    @Test
    fun `password strength levels`() {
        assertEquals(PasswordStrength.EMPTY, AuthValidator.strength(""))
        assertEquals(PasswordStrength.WEAK, AuthValidator.strength("abc"))
        assertEquals(PasswordStrength.MEDIUM, AuthValidator.strength("Demo1234"))
        assertEquals(PasswordStrength.STRONG, AuthValidator.strength("Abcdef12345!xyz"))
    }
}
