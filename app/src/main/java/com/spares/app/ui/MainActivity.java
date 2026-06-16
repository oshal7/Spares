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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spares.app.R;
import com.spares.app.db.Goal;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final double TRANSFER_THRESHOLD = 50.0;
    private static final String PREFS_NAME = "spares_prefs";
    private static final String PREF_VPA   = "savings_vpa";

    private MainViewModel viewModel;
    private TransactionAdapter adapter;
    private SharedPreferences prefs;

    private TextView tvGoalTitle, tvAccumulatedAmount, tvTargetAmount;
    private TextView tvProgressLabel, tvUnsettledAmount, tvThresholdHint;
    private TextView tvLastRefresh, tvRefreshStatus, tvTxCount;
    private ProgressBar progressBar, progressSpinner;
    private Button btnTransfer;
    private AppCompatImageButton btnRefresh, btnChangeGoal;
    private LinearLayout layoutEmpty;

    private Goal currentGoal;
    private double unsettledTotal = 0.0;

    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean ok = Boolean.TRUE.equals(result.get(Manifest.permission.RECEIVE_SMS))
                      && Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));
            if (ok) {
                requestBatteryOptimizationExemption();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("SMS Permission Required")
                    .setMessage("Spares needs SMS access to detect bank transactions.")
                    .setPositiveButton("Grant", (d, w) -> requestSmsPermissions())
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
        requestSmsPermissions();
    }

    private void bindViews() {
        tvGoalTitle         = findViewById(R.id.tv_goal_title);
        tvAccumulatedAmount = findViewById(R.id.tv_accumulated_amount);
        tvTargetAmount      = findViewById(R.id.tv_target_amount);
        progressBar         = findViewById(R.id.progress_bar);
        tvProgressLabel     = findViewById(R.id.tv_progress_label);
        tvUnsettledAmount   = findViewById(R.id.tv_unsettled_amount);
        tvThresholdHint     = findViewById(R.id.tv_threshold_hint);
        tvLastRefresh       = findViewById(R.id.tv_last_refresh);
        tvRefreshStatus     = findViewById(R.id.tv_refresh_status);
        tvTxCount           = findViewById(R.id.tv_tx_count);
        progressSpinner     = findViewById(R.id.progress_spinner);
        btnTransfer         = findViewById(R.id.btn_transfer);
        btnRefresh          = findViewById(R.id.btn_refresh);
        btnChangeGoal       = findViewById(R.id.btn_change_goal);
        layoutEmpty         = findViewById(R.id.layout_empty);

        btnTransfer.setOnClickListener(v -> launchUpiTransfer());

        btnRefresh.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.isRefreshing.getValue())) return;
            viewModel.refreshFromSmsInbox();
        });

        btnChangeGoal.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Change Goal?")
                .setMessage("This will deactivate the current goal and create a new one.")
                .setPositiveButton("Yes, change", (d, w) -> {
                    startActivity(new Intent(this, GoalSetupActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show()
        );
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        RecyclerView rv = findViewById(R.id.rv_transactions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void observeData() {
        viewModel.activeGoal.observe(this, goal -> {
            currentGoal = goal;
            if (goal == null) {
                startActivity(new Intent(this, GoalSetupActivity.class));
                finish();
                return;
            }
            updateGoalUI(goal);
        });

        viewModel.recentTransactions.observe(this, txs -> {
            adapter.submitList(txs);
            int count = txs != null ? txs.size() : 0;
            layoutEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            tvTxCount.setText(count > 0 ? count + " total" : "");
        });

        viewModel.unsettledTotal.observe(this, total -> {
            unsettledTotal = total != null ? total : 0.0;
            tvUnsettledAmount.setText(
                String.format(Locale.getDefault(), "₹%.2f", unsettledTotal));
            updateTransferButton();
        });

        // Refresh spinner
        viewModel.isRefreshing.observe(this, refreshing -> {
            progressSpinner.setVisibility(refreshing ? View.VISIBLE : View.GONE);
            btnRefresh.setEnabled(!refreshing);
            btnRefresh.setAlpha(refreshing ? 0.4f : 1.0f);
            if (refreshing) {
                tvLastRefresh.setText("Scanning inbox…");
                tvRefreshStatus.setVisibility(View.GONE);
            }
        });

        // Refresh result message
        viewModel.lastRefreshStatus.observe(this, status -> {
            if (status == null || status.isEmpty()) return;
            tvLastRefresh.setText("Last scan: just now");
            tvRefreshStatus.setText(status);
            tvRefreshStatus.setVisibility(View.VISIBLE);
            // Auto-hide after 4 seconds
            tvRefreshStatus.postDelayed(() -> tvRefreshStatus.setVisibility(View.GONE), 4000);
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
        tvProgressLabel.setText(percent + "% saved");
        updateTransferButton();
    }

    private void updateTransferButton() {
        boolean canTransfer = unsettledTotal >= TRANSFER_THRESHOLD;
        btnTransfer.setEnabled(canTransfer);
        if (canTransfer) {
            btnTransfer.setText(String.format(Locale.getDefault(),
                "Transfer ₹%.2f to Goal →", unsettledTotal));
            tvThresholdHint.setVisibility(View.GONE);
        } else {
            btnTransfer.setText("Transfer to Savings");
            tvThresholdHint.setText(String.format(Locale.getDefault(),
                "Need ₹%.0f more", Math.max(0, TRANSFER_THRESHOLD - unsettledTotal)));
            tvThresholdHint.setVisibility(View.VISIBLE);
        }
    }

    // ── UPI Transfer ─────────────────────────────────────────────────────────

    private void launchUpiTransfer() {
        if (currentGoal == null) return;
        String stored = prefs.getString(PREF_VPA, "").trim();
        if (TextUtils.isEmpty(stored)) {
            promptForVpa(this::fireUpiIntent);
        } else {
            confirmAndFireUpi(stored);
        }
    }

    private void confirmAndFireUpi(String vpa) {
        new AlertDialog.Builder(this)
            .setTitle("Confirm Transfer")
            .setMessage(String.format(Locale.getDefault(),
                "Transfer ₹%.2f to\n%s\n\nfor \"%s\"",
                unsettledTotal, vpa, currentGoal.title))
            .setPositiveButton("Transfer Now", (d, w) -> fireUpiIntent(vpa))
            .setNeutralButton("Change UPI ID", (d, w) -> promptForVpa(this::fireUpiIntent))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void promptForVpa(VpaCallback callback) {
        EditText et = new EditText(this);
        et.setHint("yourname@okicici");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String existing = prefs.getString(PREF_VPA, "");
        if (!existing.isEmpty()) et.setText(existing);

        new AlertDialog.Builder(this)
            .setTitle("Savings UPI ID")
            .setMessage("Where should round-ups be transferred?")
            .setView(et)
            .setPositiveButton("Save & Transfer", (d, w) -> {
                String vpa = et.getText().toString().trim();
                if (TextUtils.isEmpty(vpa) || !vpa.contains("@")) {
                    Toast.makeText(this, "Enter a valid UPI ID (eg: name@bank)", Toast.LENGTH_SHORT).show();
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
        String uri = String.format(Locale.getDefault(),
            "upi://pay?pa=%s&pn=SparesGoal&am=%.2f&cu=INR&tn=Spares+Round-Up",
            vpa, unsettledTotal);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            viewModel.settleAll(() -> runOnUiThread(() ->
                Toast.makeText(this, "✓ Transfer initiated!", Toast.LENGTH_LONG).show()));
        } else {
            Toast.makeText(this, "No UPI app found (install GPay or PhonePe)", Toast.LENGTH_LONG).show();
        }
    }

    interface VpaCallback { void onVpa(String vpa); }

    // ── Permissions ───────────────────────────────────────────────────────────

    private void requestSmsPermissions() {
        boolean ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                     == PackageManager.PERMISSION_GRANTED
                  && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                     == PackageManager.PERMISSION_GRANTED;
        if (ok) { requestBatteryOptimizationExemption(); return; }

        new AlertDialog.Builder(this)
            .setTitle("SMS Access Required")
            .setMessage("Spares reads bank SMS locally on your device to detect transactions. " +
                        "Nothing leaves your phone.")
            .setPositiveButton("Continue", (d, w) -> permissionLauncher.launch(new String[]{
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS
            }))
            .setCancelable(false)
            .show();
    }

    @SuppressWarnings("BatteryLife")
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }
    }
}
