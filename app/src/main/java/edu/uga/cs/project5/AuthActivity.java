package edu.uga.cs.project5;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference db;

    private TextInputEditText etEmail, etPassword, etPasswordConfirm, etDisplayName;
    private Button btnAuthAction;
    private TextView tvStatus, tvForgotPassword;
    private RadioGroup authToggle;
    private RadioButton rbLogin, rbRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);
        etDisplayName = findViewById(R.id.etDisplayName);
        btnAuthAction = findViewById(R.id.btnAuthAction);
        tvStatus = findViewById(R.id.tvStatus);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        authToggle = findViewById(R.id.authToggleGroup);
        rbLogin = findViewById(R.id.rbLogin);
        rbRegister = findViewById(R.id.rbRegister);

        // Default: login mode
        rbLogin.setChecked(true);
        updateAuthUiForMode();

        // Switch between Login / Register
        authToggle.setOnCheckedChangeListener((group, checkedId) -> updateAuthUiForMode());

        // Forgot password (login only)
        tvForgotPassword.setOnClickListener(v -> sendPasswordResetEmail());

        // Main button click
        btnAuthAction.setOnClickListener(v -> {
            tvStatus.setText("");
            boolean isRegister = rbRegister.isChecked();

            String email = safeText(etEmail);
            String password = safeText(etPassword);

            if (!isValidEmail(email)) {
                tvStatus.setText("Enter a valid email address.");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                tvStatus.setText("Password must be at least 6 characters.");
                return;
            }

            if (isRegister) {
                String confirm = safeText(etPasswordConfirm);
                String displayName = safeText(etDisplayName);

                if (TextUtils.isEmpty(confirm)) {
                    tvStatus.setText("Please confirm your password.");
                    return;
                }
                if (!password.equals(confirm)) {
                    tvStatus.setText("Passwords do not match.");
                    return;
                }
                if (TextUtils.isEmpty(displayName)) {
                    tvStatus.setText("Enter a display name.");
                    return;
                }

                registerUser(email, password, displayName);
            } else {
                loginUser(email, password);
            }
        });
    }

    private String safeText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private boolean isValidEmail(String email) {
        return (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches());
    }

    private void updateAuthUiForMode() {
        boolean isRegisterMode = rbRegister.isChecked();

        View displayNameLayout = findViewById(R.id.tilDisplayName);
        View confirmLayout = findViewById(R.id.tilPasswordConfirm);

        if (displayNameLayout != null) {
            displayNameLayout.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        }
        if (confirmLayout != null) {
            confirmLayout.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        }

        // Forgot password only in login mode
        tvForgotPassword.setVisibility(isRegisterMode ? View.GONE : View.VISIBLE);

        btnAuthAction.setText(isRegisterMode ? "Register" : "Login");
    }

    // ------------ Register ------------
    private void registerUser(String email, String password, String displayName) {
        btnAuthAction.setEnabled(false);
        tvStatus.setText("Registering...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnAuthAction.setEnabled(true);
                    if (task.isSuccessful()) {
                        String uid = task.getResult().getUser().getUid();
                        Map<String, Object> profile = new HashMap<>();
                        profile.put("displayName", displayName);
                        profile.put("email", email);
                        profile.put("createdAt", ServerValue.TIMESTAMP);
                        db.child("users").child(uid).setValue(profile)
                                .addOnCompleteListener(profileTask -> {
                                    if (profileTask.isSuccessful()) {
                                        openMainActivity();
                                    } else {
                                        tvStatus.setText("Registration succeeded but failed to write profile.");
                                    }
                                });
                    } else {
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            tvStatus.setText("This email is already registered.");
                        } else {
                            tvStatus.setText("Registration failed: " + (e != null ? e.getMessage() : "unknown error"));
                        }
                    }
                });
    }

    // ------------ Login ------------
    private void loginUser(String email, String password) {
        btnAuthAction.setEnabled(false);
        tvStatus.setText("Logging in...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    btnAuthAction.setEnabled(true);
                    if (task.isSuccessful()) {
                        openMainActivity();
                    } else {
                        tvStatus.setText("Login failed: unknown email/password.");
                    }
                });
    }

    // ------------ Forgot password ------------
    private void sendPasswordResetEmail() {
        String email = safeText(etEmail);
        if (!isValidEmail(email)) {
            tvStatus.setText("Enter a valid email to reset password.");
            return;
        }

        tvStatus.setText("Sending password reset email...");

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        tvStatus.setText("Password reset email sent.");
                    } else {
                        tvStatus.setText("Failed to send reset email.");
                    }
                });
    }

    private void openMainActivity() {
        Intent i = new Intent(AuthActivity.this, MainActivity.class);
        startActivity(i);
        finish();
    }
}
