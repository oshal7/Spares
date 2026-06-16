package com.spares.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spares.app.R;
import com.spares.app.db.Goal;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final double TRANSFER_THRESHOLD = 50.0; // PRD §5
    private static final String PREFS_NAME = "spares_prefs";
    private static final String PREF_VPA   = "savings_vpa";

    private MainViewModel viewModel;
    private TransactionAdapter adapter;
    private SharedPreferences prefs;

    // UI references
    private TextView tvGoalTitle;
    private TextView tvAccumulatedAmount;
    private TextView tvTargetAmount;
    private ProgressBar progressBar;
    private TextView tvProgressLabel;
    private Button btnTransfer;
    private TextView tvUnsettledAmount;

    // Current state
    private Goal currentGoal;
    private double unsettledTotal = 0.0;

    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean receiveSms = Boolean.TRUE.equals(result.get(Manifest.permission.RECEIVE_SMS));
            boolean readSms    = Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));
            if (receiveSms && readSms) {
                requestBatteryOptimizationExemption();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("SMS Permission Required")
                    .setMessage("Spares needs to read incoming bank SMS to detect transactions. " +
                                "Without this, round-up savings cannot be tracked.")
                    .setPositiveButton("Grant Permission", (d, w) -> requestSmsPermissions())
                    .setNegativeButton("Exit", (d, w) -> finish())
                    .show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        bindViews();
        setupRecyclerView();
        observeData();
        requestSmsPermissions(); // PRD §4.1 Assertion Tier 1
    }

    private void bindViews() {
        tvGoalTitle         = findViewById(R.id.tv_goal_title);
        tvAccumulatedAmount = findViewById(R.id.tv_accumulated_amount);
        tvTargetAmount      = findViewById(R.id.tv_target_amount);
        progressBar         = findViewById(R.id.progress_bar);
        tvProgressLabel     = findViewById(R.id.tv_progress_label);
        btnTransfer         = findViewById(R.id.btn_transfer);
        tvUnsettledAmount   = findViewById(R.id.tv_unsettled_amount);

        btnTransfer.setOnClickListener(v -> launchUpiTransfer());
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        RecyclerView rv = findViewById(R.id.rv_transactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void observeData() {
        // Active goal
        viewModel.activeGoal.observe(this, goal -> {
            currentGoal = goal;
            if (goal == null) {
                startActivity(new Intent(this, GoalSetupActivity.class));
                finish();
                return;
            }
            updateGoalUI(goal);
        });

        // Transaction ledger
        viewModel.recentTransactions.observe(this, transactions ->
            adapter.submitList(transactions));

        // Unsettled savings total
        viewModel.unsettledTotal.observe(this, total -> {
            unsettledTotal = total != null ? total : 0.0;
            tvUnsettledAmount.setText(
                String.format(Locale.getDefault(), "₹%.2f pending transfer", unsettledTotal));
            updateTransferButton();
        });
    }

    private void updateGoalUI(Goal goal) {
        tvGoalTitle.setText(goal.title);
        tvAccumulatedAmount.setText(
            String.format(Locale.getDefault(), "₹%.2f", goal.currentAccumulated));
        tvTargetAmount.setText(
            String.format(Locale.getDefault(), "of ₹%.2f", goal.targetAmount));

        int percent = (goal.targetAmount > 0)
            ? (int) Math.min(100, (goal.currentAccumulated / goal.targetAmount) * 100)
            : 0;
        progressBar.setProgress(percent);
        tvProgressLabel.setText(percent + "% reached");

        updateTransferButton();
    }

    private void updateTransferButton() {
        boolean canTransfer = unsettledTotal >= TRANSFER_THRESHOLD;
        btnTransfer.setEnabled(canTransfer);
        btnTransfer.setText(String.format(Locale.getDefault(),
            canTransfer
                ? "Transfer ₹%.2f to Savings Goal"
                : "₹%.2f accumulated — need ₹50 min",
            unsettledTotal));
    }

    // ── UPI Transfer (PRD §4.3) ───────────────────────────────────────────────

    /**
     * If a VPA is already stored, fire the UPI intent immediately.
     * If not (first time), prompt for it, persist it, then fire.
     */
    private void launchUpiTransfer() {
        if (currentGoal == null) return;

        String storedVpa = prefs.getString(PREF_VPA, "").trim();

        if (TextUtils.isEmpty(storedVpa)) {
            // First time — ask user for their UPI ID
            promptForVpa(vpa -> fireUpiIntent(vpa));
        } else {
            // Confirm amount + VPA, allow editing
            confirmAndFireUpi(storedVpa);
        }
    }

    private void confirmAndFireUpi(String existingVpa) {
        new AlertDialog.Builder(this)
            .setTitle("Transfer to Savings")
            .setMessage(String.format(Locale.getDefault(),
                "Transfer ₹%.2f for \"%s\"\nto UPI ID: %s\n\nChange UPI ID?",
                unsettledTotal, currentGoal.title, existingVpa))
            .setPositiveButton("Transfer Now", (d, w) -> fireUpiIntent(existingVpa))
            .setNeutralButton("Change UPI ID", (d, w) -> promptForVpa(this::fireUpiIntent))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void promptForVpa(VpaCallback callback) {
        EditText etVpa = new EditText(this);
        etVpa.setHint("yourname@okicici");
        etVpa.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String existing = prefs.getString(PREF_VPA, "");
        if (!existing.isEmpty()) etVpa.setText(existing);

        new AlertDialog.Builder(this)
            .setTitle("Your Savings UPI ID")
            .setMessage("Enter the UPI VPA where round-up savings will be transferred:")
            .setView(etVpa)
            .setPositiveButton("Save & Transfer", (d, w) -> {
                String vpa = etVpa.getText().toString().trim();
                if (TextUtils.isEmpty(vpa) || !vpa.contains("@")) {
                    Toast.makeText(this, "Invalid UPI ID (must contain @)", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(PREF_VPA, vpa).apply();
                callback.onVpa(vpa);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void fireUpiIntent(String vpa) {
        if (currentGoal == null || TextUtils.isEmpty(vpa)) return;

        String goalNameEncoded = currentGoal.title.replace(" ", "+");
        String upiUri = String.format(Locale.getDefault(),
            "upi://pay?pa=%s&pn=SparesGoal&am=%.2f&cu=INR&tn=Spares+Round-Up+%s",
            vpa, unsettledTotal, goalNameEncoded);

        Intent upiIntent = new Intent(Intent.ACTION_VIEW);
        upiIntent.setData(Uri.parse(upiUri));

        if (upiIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(upiIntent);
            // Mark unsettled transactions as settled
            viewModel.settleAll(() -> runOnUiThread(() ->
                Toast.makeText(this, "Transfer launched! Ledger cleared.", Toast.LENGTH_LONG).show()
            ));
        } else {
            Toast.makeText(this,
                "No UPI app found. Please install PhonePe or GPay.",
                Toast.LENGTH_LONG).show();
        }
    }

    interface VpaCallback {
        void onVpa(String vpa);
    }

    // ── Permission flows (PRD §4.1) ──────────────────────────────────────────

    private void requestSmsPermissions() {
        boolean hasReceive = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasRead = ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;

        if (hasReceive && hasRead) {
            requestBatteryOptimizationExemption();
            return;
        }

        // Educational backdrop before system dialog (PRD §4.1 Tier 1)
        new AlertDialog.Builder(this)
            .setTitle("SMS Access Required")
            .setMessage("Spares reads your bank SMS messages locally on this device to detect " +
                        "transaction amounts. No data leaves your phone — everything is stored " +
                        "only in local sandboxed storage.\n\nThis is required to automatically " +
                        "calculate your round-up savings.")
            .setPositiveButton("Continue", (d, w) -> permissionLauncher.launch(new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            }))
            .setCancelable(false)
            .show();
    }

    /**
     * PRD §4.1 Assertion Tier 2 — battery optimization exemption.
     * Only prompt if not already exempted (avoids repeated system dialogs).
     */
    @SuppressWarnings("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}
