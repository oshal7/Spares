package com.spares.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.spares.app.R;

public class GoalSetupActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private TextInputEditText etTitle;
    private TextInputEditText etAmount;
    private Button btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_setup);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        etTitle  = findViewById(R.id.et_goal_title);
        etAmount = findViewById(R.id.et_goal_amount);
        btnCreate = findViewById(R.id.btn_create_goal);

        // Enable button only when both fields are filled
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etTitle.addTextChangedListener(watcher);
        etAmount.addTextChangedListener(watcher);

        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String amtStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";

            if (title.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amtStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount < 100.0) {
                Toast.makeText(this, "Minimum goal amount is ₹100", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.createGoal(title, amount);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void updateButtonState() {
        String title  = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String amount = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
        btnCreate.setEnabled(!title.isEmpty() && !amount.isEmpty());
    }
}
