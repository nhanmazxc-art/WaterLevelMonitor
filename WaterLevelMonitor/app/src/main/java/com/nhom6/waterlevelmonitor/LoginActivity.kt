package com.nhom6.waterlevelmonitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider

import com.nhom6.waterlevelmonitor.data.FirebaseManager


class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tvError: TextView
    private lateinit var btnLogin: MaterialButton
    private lateinit var progress: ProgressBar

    // Google
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginActivity"
        private const val GOOGLE_SIGN_IN_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        // Nếu đã đăng nhập thì vào thẳng MainActivity
        if (FirebaseManager.auth.currentUser != null) {
            openMain()
            return
        }

        setContentView(R.layout.activity_login)

        // =========================
        // ÁNH XẠ VIEW
        // =========================

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvError = findViewById(R.id.tvLoginError)
        btnLogin = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progressLogin)


        // =========================
        // CẤU HÌNH GOOGLE
        // =========================

        setupGoogleLogin()


        // =========================
        // ĐĂNG NHẬP EMAIL
        // =========================

        btnLogin.setOnClickListener {
            attemptLogin()
        }


        // =========================
        // ĐĂNG NHẬP GOOGLE
        // =========================

        findViewById<View>(R.id.btnGoogle).setOnClickListener {

            hideError()

            try {

                setLoading(true)

                val signInIntent =
                    googleSignInClient.signInIntent

                startActivityForResult(
                    signInIntent,
                    GOOGLE_SIGN_IN_REQUEST
                )

            } catch (e: Exception) {

                setLoading(false)

                Log.e(
                    TAG,
                    "Không thể mở Google Login",
                    e
                )

                showError(
                    "Không thể mở đăng nhập Google."
                )
            }
        }


        // =========================
        // ĐĂNG KÝ
        // =========================

        findViewById<View>(R.id.tvRegister).setOnClickListener {
            showRegisterDialog()
        }


        // =========================
        // QUÊN MẬT KHẨU
        // =========================

        findViewById<View>(R.id.tvForgotPassword).setOnClickListener {
            sendPasswordReset()
        }
    }


    // =====================================================
    // GOOGLE LOGIN
    // =====================================================

    private fun setupGoogleLogin() {

        val gso =
            GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(R.string.default_web_client_id)
                )
                .requestEmail()
                .build()

        googleSignInClient =
            GoogleSignIn.getClient(
                this,
                gso
            )
    }


    private fun handleGoogleResult(data: Intent?) {

        val task =
            GoogleSignIn.getSignedInAccountFromIntent(data)

        try {

            val account =
                task.getResult(
                    ApiException::class.java
                )

            val idToken =
                account.idToken

            if (idToken == null) {

                setLoading(false)

                showError(
                    "Không lấy được Google ID Token."
                )

                return
            }

            // Tạo Firebase Credential
            val credential =
                GoogleAuthProvider.getCredential(
                    idToken,
                    null
                )

            // Đăng nhập Firebase
            FirebaseManager.auth
                .signInWithCredential(credential)
                .addOnCompleteListener(this) { result ->

                    setLoading(false)

                    if (result.isSuccessful) {

                        Log.d(
                            TAG,
                            "Đăng nhập Google thành công"
                        )

                        openMain()

                    } else {

                        Log.e(
                            TAG,
                            "Google Firebase Login Error",
                            result.exception
                        )

                        showError(
                            friendlyError(
                                result.exception
                            )
                        )
                    }
                }

        } catch (e: ApiException) {

            setLoading(false)

            Log.e(
                TAG,
                "Google Login Failed",
                e
            )

            showError(
                "Đăng nhập Google thất bại."
            )
        }
    }


    // =====================================================
    // GOOGLE RESULT
    // =====================================================

    @Deprecated("Deprecated API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
            handleGoogleResult(data)
        }
    }


    // =====================================================
    // EMAIL + PASSWORD LOGIN
    // =====================================================

    private fun attemptLogin() {

        val email =
            etEmail.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val password =
            etPassword.text
                ?.toString()
                .orEmpty()

        if (email.isEmpty() || password.isEmpty()) {

            showError(
                "Vui lòng nhập email và mật khẩu."
            )

            return
        }

        setLoading(true)

        FirebaseManager.auth
            .signInWithEmailAndPassword(
                email,
                password
            )
            .addOnCompleteListener(this) { task ->

                setLoading(false)

                if (task.isSuccessful) {

                    Log.d(
                        TAG,
                        "Đăng nhập Email thành công"
                    )

                    openMain()

                } else {

                    Log.e(
                        TAG,
                        "Đăng nhập thất bại",
                        task.exception
                    )

                    showError(
                        friendlyError(
                            task.exception
                        )
                    )
                }
            }
    }


    // =====================================================
    // ĐĂNG KÝ
    // =====================================================

    private fun showRegisterDialog() {

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(24),
                    0,
                    dp(24),
                    0
                )
            }

        val email =
            dialogField(
                "Email",
                false
            )

        val password =
            dialogField(
                "Mật khẩu (tối thiểu 6 ký tự)",
                true
            )

        content.addView(email)

        content.addView(
            password,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Tạo tài khoản")
                .setView(content)
                .setNegativeButton(
                    "Hủy",
                    null
                )
                .setPositiveButton(
                    "Đăng ký",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog
                .getButton(
                    AlertDialog.BUTTON_POSITIVE
                )
                .setOnClickListener {

                    val address =
                        email.text
                            .toString()
                            .trim()

                    val secret =
                        password.text
                            .toString()

                    if (
                        !Patterns.EMAIL_ADDRESS
                            .matcher(address)
                            .matches()
                    ) {

                        email.error =
                            "Email không hợp lệ"

                        return@setOnClickListener
                    }

                    if (secret.length < 6) {

                        password.error =
                            "Mật khẩu cần ít nhất 6 ký tự"

                        return@setOnClickListener
                    }

                    FirebaseManager.auth
                        .createUserWithEmailAndPassword(
                            address,
                            secret
                        )
                        .addOnCompleteListener(this) { task ->

                            if (task.isSuccessful) {

                                openMain()

                            } else {

                                showError(
                                    registerError(
                                        task.exception
                                    )
                                )
                            }

                            dialog.dismiss()
                        }
                }
        }

        dialog.show()
    }


    // =====================================================
    // QUÊN MẬT KHẨU
    // =====================================================

    private fun sendPasswordReset() {

        val address =
            etEmail.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(address)
                .matches()
        ) {

            etEmail.error =
                "Nhập email hợp lệ trước khi đặt lại mật khẩu"

            etEmail.requestFocus()

            return
        }

        setLoading(true)

        FirebaseManager.auth
            .sendPasswordResetEmail(address)
            .addOnCompleteListener(this) { task ->

                setLoading(false)

                if (task.isSuccessful) {

                    showMessage(
                        "Đã gửi email đặt lại mật khẩu đến $address.",
                        R.color.green
                    )

                } else {

                    showError(
                        friendlyError(
                            task.exception
                        )
                    )
                }
            }
    }


    // =====================================================
    // DIALOG FIELD
    // =====================================================

    private fun dialogField(
        hint: String,
        password: Boolean
    ): EditText {

        return EditText(this).apply {

            this.hint = hint

            inputType =
                if (password) {

                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

                } else {

                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                }

            setSingleLine(true)
        }
    }


    // =====================================================
    // ERROR
    // =====================================================

    private fun registerError(
        e: Exception?
    ): String {

        return when (e) {

            is FirebaseAuthUserCollisionException ->
                "Email này đã được đăng ký."

            is FirebaseAuthWeakPasswordException ->
                "Mật khẩu chưa đủ mạnh."

            else ->
                friendlyError(e)
        }
    }


    private fun friendlyError(
        e: Exception?
    ): String {

        return when (e) {

            is FirebaseAuthInvalidUserException,
            is FirebaseAuthInvalidCredentialsException ->
                "Email hoặc mật khẩu không đúng."

            is FirebaseNetworkException ->
                "Không có kết nối mạng. Hãy kiểm tra Internet."

            is FirebaseTooManyRequestsException ->
                "Thử quá nhiều lần. Vui lòng đợi một lúc rồi thử lại."

            else ->
                "Đăng nhập thất bại: ${
                    e?.message ?: "lỗi không xác định"
                }"
        }
    }


    // =====================================================
    // UI
    // =====================================================

    private fun showError(
        message: String
    ) {

        showMessage(
            message,
            R.color.red
        )
    }


    private fun showMessage(
        message: String,
        colorRes: Int
    ) {

        tvError.text = message

        tvError.setTextColor(
            ContextCompat.getColor(
                this,
                colorRes
            )
        )

        tvError.visibility =
            View.VISIBLE
    }


    private fun hideError() {

        tvError.visibility =
            View.GONE
    }


    private fun setLoading(
        loading: Boolean
    ) {

        btnLogin.isEnabled =
            !loading

        progress.visibility =
            if (loading)
                View.VISIBLE
            else
                View.GONE

        if (loading) {
            tvError.visibility =
                View.GONE
        }
    }


    // =====================================================
    // MỞ MAIN
    // =====================================================

    private fun openMain() {

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()
    }


    // =====================================================
    // DP
    // =====================================================

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }
}