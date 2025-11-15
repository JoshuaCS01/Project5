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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference db;

    private TextInputEditText etEmail, etPassword, etDisplayName;
    private Button btnAuthAction;
    private TextView tvStatus;
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
        etDisplayName = findViewById(R.id.etDisplayName);
        btnAuthAction = findViewById(R.id.btnAuthAction);
        tvStatus = findViewById(R.id.tvStatus);
        authToggle = findViewById(R.id.authToggleGroup);
        rbLogin = findViewById(R.id.rbLogin);
        rbRegister = findViewById(R.id.rbRegister);

        authToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLogin) {
                etDisplayName.setVisibility(View.GONE);
                btnAuthAction.setText("Login");
            } else {
                etDisplayName.setVisibility(View.VISIBLE);
                btnAuthAction.setText("Register");
            }
        });

        rbLogin.setChecked(true);       // optional, ensure default
        updateAuthUiForMode();

        authToggle.setOnCheckedChangeListener((group, checkedId) -> updateAuthUiForMode());

        btnAuthAction.setOnClickListener(v -> {
            tvStatus.setText("");
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            boolean isRegister = rbRegister.isChecked();

            if (!isValidEmail(email)) {
                tvStatus.setText("Enter a valid email address.");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                tvStatus.setText("Password must be at least 6 characters.");
                return;
            }

            if (isRegister) {
                String displayName = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
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

    private boolean isValidEmail(String email) {
        return (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches());
    }

    private void registerUser(String email, String password, String displayName) {
        btnAuthAction.setEnabled(false);
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

    private void loginUser(String email, String password) {
        btnAuthAction.setEnabled(false);
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

    private void openMainActivity() {
        // Start MainActivity and finish this auth activity
        Intent i = new Intent(AuthActivity.this, MainActivity.class);
        startActivity(i);
        finish();
    }

    private void updateAuthUiForMode() {
        boolean isRegisterMode = rbRegister.isChecked();
        View displayNameLayout = findViewById(R.id.tilDisplayName);
        displayNameLayout.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        btnAuthAction.setText(isRegisterMode ? "Register" : "Login");
    }
}
